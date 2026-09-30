import { CallMask } from '../CallMask';
import type {
  CallEndReason,
  CallEvent,
  NativeSubscription,
} from '../types';
import type {
  CallActionContext,
  CallIntegrationError,
  CallIntegrationStage,
  CallLifecycleCoordinatorOptions,
  RemoteTerminationReason,
} from './types';

interface CallControl {
  addEventListener(listener: (event: CallEvent) => void): NativeSubscription;
  consumePendingEvents(): Promise<CallEvent[]>;
  markActive(callId: string): Promise<void>;
  end(callId: string, reason?: CallEndReason): Promise<void>;
}

interface CallRuntimeState {
  generation: number;
  terminalReason?: CallEndReason;
}

const REMOTE_REASONS = new Set<CallEndReason>([
  'remote',
  'cancelled',
  'busy',
  'missed',
]);

export class CallLifecycleCoordinator {
  private readonly control: CallControl;
  private readonly options: Required<
    Pick<CallLifecycleCoordinatorOptions, 'maxProcessedEvents'>
  > &
    Omit<CallLifecycleCoordinatorOptions, 'maxProcessedEvents'>;

  private subscription: NativeSubscription | null = null;
  private readonly processedEventIds = new Set<string>();
  private readonly processedEventOrder: string[] = [];
  private readonly callQueues = new Map<string, Promise<void>>();
  private readonly runtime = new Map<string, CallRuntimeState>();

  constructor(
    options: CallLifecycleCoordinatorOptions,
    control: CallControl = CallMask,
  ) {
    this.options = {
      ...options,
      maxProcessedEvents: Math.max(32, options.maxProcessedEvents ?? 512),
    };
    this.control = control;
  }

  async start(): Promise<void> {
    if (this.subscription) return;

    this.subscription = this.control.addEventListener(event => {
      void this.handleNativeEvent(event).catch(error => {
        this.report(event.callId, 'native.end', error, event);
      });
    });

    const pending = await this.control.consumePendingEvents();
    for (const event of pending) {
      await this.handleNativeEvent(event);
    }
  }

  stop(): void {
    this.subscription?.remove();
    this.subscription = null;
  }

  handleNativeEvent(event: CallEvent): Promise<void> {
    if (!this.rememberEvent(event.eventId)) {
      return Promise.resolve();
    }

    const generation = this.state(event.callId).generation;
    return this.enqueue(event.callId, async () => {
      switch (event.type) {
        case 'answer':
          await this.handleAnswer(event, generation);
          return;
        case 'decline':
          await this.handleDecline(event);
          return;
        case 'end':
          await this.handleEnd(event);
          return;
        default:
          return;
      }
    });
  }

  handleRemoteTermination(
    callId: string,
    reason: RemoteTerminationReason = 'remote',
  ): Promise<void> {
    this.invalidate(callId, reason);

    const syntheticEvent: CallEvent = {
      eventId: `remote:${callId}:${Date.now()}:${reason}`,
      callId,
      type: 'end',
      timestamp: Date.now(),
      state: 'ended',
      endReason: reason,
    };

    return this.enqueue(callId, async () => {
      await this.safeMediaDisconnect(syntheticEvent, reason);

      try {
        await this.control.end(callId, reason);
      } catch (error) {
        this.report(callId, 'native.end', error, syntheticEvent);
        throw error;
      }
    });
  }

  private async handleAnswer(
    event: CallEvent,
    generation: number,
  ): Promise<void> {
    if (!this.isCurrent(event.callId, generation)) return;

    const context: CallActionContext = {
      callId: event.callId,
      event,
    };

    try {
      await this.options.signaling.accept(context);
    } catch (error) {
      this.report(event.callId, 'signaling.accept', error, event);
      this.invalidate(event.callId, 'failed');
      await this.safeNativeEnd(event, 'failed');
      return;
    }

    if (!this.isCurrent(event.callId, generation)) return;

    try {
      await this.options.media.connect(context);
    } catch (error) {
      this.report(event.callId, 'media.connect', error, event);
      this.invalidate(event.callId, 'failed');
      await this.safeSignalingEnd(event, 'failed');
      await this.safeNativeEnd(event, 'failed');
      return;
    }

    if (!this.isCurrent(event.callId, generation)) {
      await this.safeMediaDisconnect(event, this.state(event.callId).terminalReason ?? 'remote');
      return;
    }

    try {
      await this.control.markActive(event.callId);
    } catch (error) {
      this.report(event.callId, 'native.markActive', error, event);
      this.invalidate(event.callId, 'failed');
      await this.safeMediaDisconnect(event, 'failed');
      await this.safeSignalingEnd(event, 'failed');
      await this.safeNativeEnd(event, 'failed');
    }
  }

  private async handleDecline(event: CallEvent): Promise<void> {
    this.invalidate(event.callId, 'declined');

    try {
      await this.options.signaling.decline({
        callId: event.callId,
        event,
      });
    } catch (error) {
      this.report(event.callId, 'signaling.decline', error, event);
    }

    await this.safeMediaDisconnect(event, 'declined');
  }

  private async handleEnd(event: CallEvent): Promise<void> {
    const reason = event.endReason ?? 'local';
    this.invalidate(event.callId, reason);

    if (!REMOTE_REASONS.has(reason) && reason !== 'declined') {
      await this.safeSignalingEnd(event, reason);
    }

    await this.safeMediaDisconnect(event, reason);
  }

  private async safeSignalingEnd(
    event: CallEvent,
    reason: CallEndReason,
  ): Promise<void> {
    try {
      await this.options.signaling.end({
        callId: event.callId,
        event,
        reason,
      });
    } catch (error) {
      this.report(event.callId, 'signaling.end', error, event);
    }
  }

  private async safeMediaDisconnect(
    event: CallEvent,
    reason: CallEndReason,
  ): Promise<void> {
    try {
      await this.options.media.disconnect({
        callId: event.callId,
        event,
        reason,
      });
    } catch (error) {
      this.report(event.callId, 'media.disconnect', error, event);
    }
  }

  private async safeNativeEnd(
    event: CallEvent,
    reason: CallEndReason,
  ): Promise<void> {
    try {
      await this.control.end(event.callId, reason);
    } catch (error) {
      this.report(event.callId, 'native.end', error, event);
    }
  }

  private state(callId: string): CallRuntimeState {
    let state = this.runtime.get(callId);
    if (!state) {
      state = { generation: 0 };
      this.runtime.set(callId, state);
    }
    return state;
  }

  private invalidate(callId: string, reason: CallEndReason): void {
    const current = this.state(callId);
    current.generation += 1;
    current.terminalReason = reason;
  }

  private isCurrent(callId: string, generation: number): boolean {
    const current = this.state(callId);
    return current.generation === generation && current.terminalReason === undefined;
  }

  private enqueue(callId: string, work: () => Promise<void>): Promise<void> {
    const previous = this.callQueues.get(callId) ?? Promise.resolve();
    const next = previous.catch(() => undefined).then(work);
    this.callQueues.set(callId, next);

    void next.finally(() => {
      if (this.callQueues.get(callId) === next) {
        this.callQueues.delete(callId);
      }
    });

    return next;
  }

  private rememberEvent(eventId: string): boolean {
    if (this.processedEventIds.has(eventId)) return false;

    this.processedEventIds.add(eventId);
    this.processedEventOrder.push(eventId);

    while (this.processedEventOrder.length > this.options.maxProcessedEvents) {
      const oldest = this.processedEventOrder.shift();
      if (oldest) this.processedEventIds.delete(oldest);
    }

    return true;
  }

  private report(
    callId: string,
    stage: CallIntegrationStage,
    error: unknown,
    event?: CallEvent,
  ): void {
    const payload: CallIntegrationError = {
      callId,
      stage,
      error,
      ...(event ? { event } : {}),
    };
    this.options.onError?.(payload);
  }
}
