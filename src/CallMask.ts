import { nativeModule, subscribeToNativeEvents } from './NativeCallMask';
import type {
  CallCapabilities,
  CallEndReason,
  CallEvent,
  CallSession,
  IncomingCall,
  NativeSubscription,
} from './types';

function requireCallId(callId: string): void {
  if (callId.trim().length === 0) {
    throw new TypeError('callId must be a non-empty string');
  }
}

function normalizeIncomingCall(call: IncomingCall): IncomingCall {
  requireCallId(call.callId);
  if (call.caller.name.trim().length === 0) {
    throw new TypeError('caller.name must be a non-empty string');
  }

  return {
    ...call,
    callId: call.callId.trim(),
    caller: {
      ...call.caller,
      id: call.caller.id?.trim() || call.callId.trim(),
      name: call.caller.name.trim(),
    },
    createdAt: call.createdAt ?? Date.now(),
  };
}

export const CallMask = {
  showIncomingCall(call: IncomingCall): Promise<CallSession> {
    return nativeModule().showIncomingCall(normalizeIncomingCall(call));
  },

  answer(callId: string): Promise<void> {
    requireCallId(callId);
    return nativeModule().answer(callId);
  },

  decline(callId: string): Promise<void> {
    requireCallId(callId);
    return nativeModule().decline(callId);
  },

  end(callId: string, reason: CallEndReason = 'local'): Promise<void> {
    requireCallId(callId);
    return nativeModule().end(callId, reason);
  },

  markActive(callId: string): Promise<void> {
    requireCallId(callId);
    return nativeModule().markActive(callId);
  },

  silence(callId: string): Promise<void> {
    requireCallId(callId);
    return nativeModule().silence(callId);
  },

  dismissIncomingUI(callId: string): Promise<void> {
    requireCallId(callId);
    return nativeModule().dismissIncomingUI(callId);
  },

  getCalls(): Promise<CallSession[]> {
    return nativeModule().getCalls();
  },

  consumePendingEvents(): Promise<CallEvent[]> {
    return nativeModule().consumePendingEvents();
  },

  getCapabilities(): Promise<CallCapabilities> {
    return nativeModule().getCapabilities();
  },

  openFullScreenSettings(): Promise<boolean> {
    return nativeModule().openFullScreenSettings();
  },

  addEventListener(listener: (event: CallEvent) => void): NativeSubscription {
    return subscribeToNativeEvents(listener);
  },
};
