import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

import { CallMaskError } from './errors';
import type {
  CallCapabilities,
  CallEndReason,
  CallEvent,
  CallSession,
  IncomingCall,
  NativeSubscription,
} from './types';

interface NativeCallMaskModule {
  showIncomingCall(call: IncomingCall): Promise<CallSession>;
  answer(callId: string): Promise<void>;
  decline(callId: string): Promise<void>;
  end(callId: string, reason?: CallEndReason): Promise<void>;
  markActive(callId: string): Promise<void>;
  silence(callId: string): Promise<void>;
  dismissIncomingUI(callId: string): Promise<void>;
  getCalls(): Promise<CallSession[]>;
  consumePendingEvents(): Promise<CallEvent[]>;
  getCapabilities(): Promise<CallCapabilities>;
  openFullScreenSettings(): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

function getNativeModule(): NativeCallMaskModule {
  const nativeModule = NativeModules.RNCallMask as NativeCallMaskModule | undefined;

  if (!nativeModule) {
    throw new CallMaskError(
      'E_NATIVE_MODULE_UNAVAILABLE',
      `RNCallMask native module is unavailable on ${Platform.OS}.`,
    );
  }

  return nativeModule;
}

export function nativeModule(): NativeCallMaskModule {
  return getNativeModule();
}

export function subscribeToNativeEvents(
  listener: (event: CallEvent) => void,
): NativeSubscription {
  getNativeModule();
  const emitterModule = NativeModules.RNCallMask as NativeCallMaskModule;
  return new NativeEventEmitter(emitterModule).addListener(
    'RNCallMaskEvent',
    (...args: readonly unknown[]) => {
      listener(args[0] as CallEvent);
    },
  );
}
