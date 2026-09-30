import { describe, expect, it, vi } from 'vitest';

import { CallLifecycleCoordinator } from '../src/integration/CallLifecycleCoordinator';
import type {
  CallMediaAdapter,
  CallSignalingAdapter,
} from '../src/integration/types';
import type {
  CallEndReason,
  CallEvent,
  NativeSubscription,
} from '../src/types';

function event(
  overrides: Partial<CallEvent> & Pick<CallEvent, 'eventId' | 'callId' | 'type'>,
): CallEvent {
  return {
    timestamp: Date.now(),
    ...overrides,
  };
}

function deferred(): {
  promise: Promise<void>;
  resolve: () => void;
  reject: (error: unknown) => void;
} {
  let resolve!: () => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<void>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, resolve, reject };
}

function harness() {
  let listener: ((value: CallEvent) => void) | undefined;
  const control = {
    addEventListener: vi.fn(
      (next: (value: CallEvent) => void): NativeSubscription => {
        listener = next;
        return { remove: vi.fn() };
      },
    ),
    consumePendingEvents: vi.fn(async (): Promise<CallEvent[]> => []),
    markActive: vi.fn(async (_callId: string): Promise<void> => undefined),
    end: vi.fn(
      async (_callId: string, _reason?: CallEndReason): Promise<void> => undefined,
    ),
  };

  const signaling: CallSignalingAdapter = {
    accept: vi.fn(async () => undefined),
    decline: vi.fn(async () => undefined),
    end: vi.fn(async () => undefined),
  };

  const media: CallMediaAdapter = {
    connect: vi.fn(async () => undefined),
    disconnect: vi.fn(async () => undefined),
  };

  const errors: unknown[] = [];
  const coordinator = new CallLifecycleCoordinator(
    {
      signaling,
      media,
      onError: value => errors.push(value),
    },
    control,
  );

  return {
    control,
    signaling,
    media,
    coordinator,
    errors,
    emit(value: CallEvent) {
      listener?.(value);
    },
  };
}

describe('CallLifecycleCoordinator', () => {
  it('accepts signaling, connects media, then marks the call active', async () => {
    const h = harness();
    const answer = event({
      eventId: 'answer-1',
      callId: 'A',
      type: 'answer',
      state: 'connecting',
    });

    await h.coordinator.handleNativeEvent(answer);

    expect(h.signaling.accept).toHaveBeenCalledOnce();
    expect(h.media.connect).toHaveBeenCalledOnce();
    expect(h.control.markActive).toHaveBeenCalledWith('A');
    expect(h.errors).toEqual([]);
  });

  it('deduplicates the same native event id', async () => {
    const h = harness();
    const answer = event({
      eventId: 'same-event',
      callId: 'A',
      type: 'answer',
      state: 'connecting',
    });

    await Promise.all([
      h.coordinator.handleNativeEvent(answer),
      h.coordinator.handleNativeEvent(answer),
    ]);

    expect(h.signaling.accept).toHaveBeenCalledOnce();
    expect(h.media.connect).toHaveBeenCalledOnce();
  });

  it('prevents media activation when remote cancellation wins an answer race', async () => {
    const h = harness();
    const accept = deferred();
    vi.mocked(h.signaling.accept).mockReturnValueOnce(accept.promise);

    const answerPromise = h.coordinator.handleNativeEvent(
      event({
        eventId: 'answer-race',
        callId: 'A',
        type: 'answer',
        state: 'connecting',
      }),
    );

    await vi.waitFor(() => {
      expect(h.signaling.accept).toHaveBeenCalledOnce();
    });

    const remotePromise = h.coordinator.handleRemoteTermination('A', 'cancelled');
    accept.resolve();

    await Promise.all([answerPromise, remotePromise]);

    expect(h.media.connect).not.toHaveBeenCalled();
    expect(h.control.markActive).not.toHaveBeenCalled();
    expect(h.control.end).toHaveBeenCalledWith('A', 'cancelled');
    expect(h.media.disconnect).toHaveBeenCalled();
  });

  it('ends native state as failed when signaling accept fails', async () => {
    const h = harness();
    vi.mocked(h.signaling.accept).mockRejectedValueOnce(new Error('offline'));

    await h.coordinator.handleNativeEvent(
      event({
        eventId: 'answer-failed',
        callId: 'A',
        type: 'answer',
      }),
    );

    expect(h.media.connect).not.toHaveBeenCalled();
    expect(h.control.end).toHaveBeenCalledWith('A', 'failed');
    expect(h.errors).toHaveLength(1);
  });

  it('converges backend and native state when media connect fails', async () => {
    const h = harness();
    vi.mocked(h.media.connect).mockRejectedValueOnce(new Error('ice failed'));

    await h.coordinator.handleNativeEvent(
      event({
        eventId: 'media-failed',
        callId: 'A',
        type: 'answer',
        state: 'connecting',
      }),
    );

    expect(h.signaling.accept).toHaveBeenCalledOnce();
    expect(h.signaling.end).toHaveBeenCalledWith(
      expect.objectContaining({ callId: 'A', reason: 'failed' }),
    );
    expect(h.control.end).toHaveBeenCalledWith('A', 'failed');
    expect(h.control.markActive).not.toHaveBeenCalled();
  });

  it('notifies backend for local end but does not echo remote end', async () => {
    const h = harness();

    await h.coordinator.handleNativeEvent(
      event({
        eventId: 'local-end',
        callId: 'A',
        type: 'end',
        endReason: 'local',
        state: 'ended',
      }),
    );

    await h.coordinator.handleNativeEvent(
      event({
        eventId: 'remote-end',
        callId: 'B',
        type: 'end',
        endReason: 'remote',
        state: 'ended',
      }),
    );

    expect(h.signaling.end).toHaveBeenCalledTimes(1);
    expect(h.signaling.end).toHaveBeenCalledWith(
      expect.objectContaining({
        callId: 'A',
        reason: 'local',
      }),
    );
    expect(h.media.disconnect).toHaveBeenCalledTimes(2);
  });

  it('subscribes before replaying pending events and still processes an event once', async () => {
    const h = harness();
    const pending = event({
      eventId: 'pending-answer',
      callId: 'A',
      type: 'answer',
    });

    vi.mocked(h.control.consumePendingEvents).mockImplementationOnce(async () => {
      h.emit(pending);
      return [pending];
    });

    await h.coordinator.start();
    await vi.waitFor(() => {
      expect(h.signaling.accept).toHaveBeenCalledOnce();
    });

    expect(h.media.connect).toHaveBeenCalledOnce();
    h.coordinator.stop();
  });
});
