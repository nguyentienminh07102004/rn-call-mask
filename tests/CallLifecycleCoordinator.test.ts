import { describe, expect, it, vi } from 'vitest';

import { CallLifecycleCoordinator } from '../src/integration/CallLifecycleCoordinator';
import type {
  CallMediaAdapter,
  CallSignalingAdapter,
} from '../src/integration/types';
import type { CallEvent, NativeSubscription } from '../src/types';

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

  const addEventListener = vi.fn(
    (next: (value: CallEvent) => void): NativeSubscription => {
      listener = next;
      return { remove: vi.fn() };
    },
  );
  const consumePendingEvents = vi.fn(() =>
    Promise.resolve([] as CallEvent[]),
  );
  const markActive = vi.fn(() => Promise.resolve());
  const nativeEnd = vi.fn(() => Promise.resolve());

  const control = {
    addEventListener,
    consumePendingEvents,
    markActive,
    end: nativeEnd,
  };

  const accept = vi.fn<CallSignalingAdapter['accept']>(() => Promise.resolve());
  const decline = vi.fn<CallSignalingAdapter['decline']>(() => Promise.resolve());
  const signalingEnd = vi.fn<CallSignalingAdapter['end']>(() => Promise.resolve());

  const signaling: CallSignalingAdapter = {
    accept,
    decline,
    end: signalingEnd,
  };

  const connect = vi.fn<CallMediaAdapter['connect']>(() => Promise.resolve());
  const disconnect = vi.fn<CallMediaAdapter['disconnect']>(() => Promise.resolve());

  const media: CallMediaAdapter = {
    connect,
    disconnect,
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
    coordinator,
    errors,
    spies: {
      accept,
      decline,
      signalingEnd,
      connect,
      disconnect,
      markActive,
      nativeEnd,
      consumePendingEvents,
    },
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

    expect(h.spies.accept).toHaveBeenCalledOnce();
    expect(h.spies.connect).toHaveBeenCalledOnce();
    expect(h.spies.markActive).toHaveBeenCalledWith('A');
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

    expect(h.spies.accept).toHaveBeenCalledOnce();
    expect(h.spies.connect).toHaveBeenCalledOnce();
  });

  it('prevents media activation when remote cancellation wins an answer race', async () => {
    const h = harness();
    const accept = deferred();
    h.spies.accept.mockReturnValueOnce(accept.promise);

    const answerPromise = h.coordinator.handleNativeEvent(
      event({
        eventId: 'answer-race',
        callId: 'A',
        type: 'answer',
        state: 'connecting',
      }),
    );

    await vi.waitFor(() => {
      expect(h.spies.accept).toHaveBeenCalledOnce();
    });

    const remotePromise = h.coordinator.handleRemoteTermination('A', 'cancelled');
    accept.resolve();

    await Promise.all([answerPromise, remotePromise]);

    expect(h.spies.connect).not.toHaveBeenCalled();
    expect(h.spies.markActive).not.toHaveBeenCalled();
    expect(h.spies.nativeEnd).toHaveBeenCalledWith('A', 'cancelled');
    expect(h.spies.disconnect).toHaveBeenCalled();
  });

  it('ends native state as failed when signaling accept fails', async () => {
    const h = harness();
    h.spies.accept.mockRejectedValueOnce(new Error('offline'));

    await h.coordinator.handleNativeEvent(
      event({
        eventId: 'answer-failed',
        callId: 'A',
        type: 'answer',
      }),
    );

    expect(h.spies.connect).not.toHaveBeenCalled();
    expect(h.spies.nativeEnd).toHaveBeenCalledWith('A', 'failed');
    expect(h.errors).toHaveLength(1);
  });

  it('converges backend and native state when media connect fails', async () => {
    const h = harness();
    h.spies.connect.mockRejectedValueOnce(new Error('ice failed'));

    await h.coordinator.handleNativeEvent(
      event({
        eventId: 'media-failed',
        callId: 'A',
        type: 'answer',
        state: 'connecting',
      }),
    );

    expect(h.spies.accept).toHaveBeenCalledOnce();
    expect(h.spies.signalingEnd).toHaveBeenCalledWith(
      expect.objectContaining({ callId: 'A', reason: 'failed' }),
    );
    expect(h.spies.nativeEnd).toHaveBeenCalledWith('A', 'failed');
    expect(h.spies.markActive).not.toHaveBeenCalled();
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

    expect(h.spies.signalingEnd).toHaveBeenCalledTimes(1);
    expect(h.spies.signalingEnd).toHaveBeenCalledWith(
      expect.objectContaining({
        callId: 'A',
        reason: 'local',
      }),
    );
    expect(h.spies.disconnect).toHaveBeenCalledTimes(2);
  });

  it('subscribes before replaying pending events and still processes an event once', async () => {
    const h = harness();
    const pending = event({
      eventId: 'pending-answer',
      callId: 'A',
      type: 'answer',
    });

    h.spies.consumePendingEvents.mockImplementationOnce(() => {
      h.emit(pending);
      return Promise.resolve([pending]);
    });

    await h.coordinator.start();
    await vi.waitFor(() => {
      expect(h.spies.accept).toHaveBeenCalledOnce();
    });

    expect(h.spies.connect).toHaveBeenCalledOnce();
    h.coordinator.stop();
  });
});
