import type { CallEndReason, CallEvent } from '../types';

export type RemoteTerminationReason =
  | 'remote'
  | 'cancelled'
  | 'busy'
  | 'failed'
  | 'missed';

export interface CallActionContext {
  callId: string;
  event: CallEvent;
}

export interface CallEndContext extends CallActionContext {
  reason: CallEndReason;
}

export interface CallSignalingAdapter {
  accept(context: CallActionContext): Promise<void>;
  decline(context: CallActionContext): Promise<void>;
  end(context: CallEndContext): Promise<void>;
}

export interface CallMediaAdapter {
  connect(context: CallActionContext): Promise<void>;
  disconnect(context: CallEndContext): Promise<void>;
}

export type CallIntegrationStage =
  | 'signaling.accept'
  | 'signaling.decline'
  | 'signaling.end'
  | 'media.connect'
  | 'media.disconnect'
  | 'native.markActive'
  | 'native.end';

export interface CallIntegrationError {
  callId: string;
  stage: CallIntegrationStage;
  error: unknown;
  event?: CallEvent;
}

export interface CallLifecycleCoordinatorOptions {
  signaling: CallSignalingAdapter;
  media: CallMediaAdapter;
  onError?: (error: CallIntegrationError) => void;
  maxProcessedEvents?: number;
}
