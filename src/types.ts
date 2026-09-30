export type CallMedia = 'audio' | 'video';

export type CallState =
  | 'incoming'
  | 'ringing'
  | 'connecting'
  | 'active'
  | 'held'
  | 'ending'
  | 'ended';

export type CallEndReason =
  | 'local'
  | 'remote'
  | 'declined'
  | 'missed'
  | 'busy'
  | 'failed'
  | 'cancelled';

export interface CallParticipant {
  id: string;
  name: string;
  handle?: string;
  avatar?: string;
}

export interface IncomingCall {
  callId: string;
  media: CallMedia;
  caller: {
    id?: string;
    name: string;
    handle?: string;
    avatar?: string;
  };
  createdAt?: number;
  data?: Record<string, string>;
}

export interface CallSession {
  callId: string;
  media: CallMedia;
  caller: CallParticipant;
  state: CallState;
  createdAt: number;
  answeredAt?: number;
  endedAt?: number;
  endReason?: CallEndReason;
  silenced: boolean;
  data?: Record<string, string>;
}

export type CallEventType =
  | 'incoming'
  | 'answer'
  | 'decline'
  | 'end'
  | 'silenced'
  | 'stateChanged'
  | 'presentationChanged';

export interface CallEvent {
  eventId: string;
  callId: string;
  type: CallEventType;
  timestamp: number;
  state?: CallState;
  endReason?: CallEndReason;
}

export interface CallCapabilities {
  notificationsEnabled: boolean;
  canUseFullScreen: boolean;
  androidApiLevel?: number;
}

export interface NativeSubscription {
  remove(): void;
}
