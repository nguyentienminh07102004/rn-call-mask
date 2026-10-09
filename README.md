# rn-call-mask

Native-first incoming-call presentation and lifecycle bridge for React Native VoIP apps.

## Status

Current package version: `0.1.0-beta.1`.

Supported package range:

- React Native `>=0.78 <0.79` (validated against 0.78.3);
- Node.js `>=20.19.4`;
- Android minSdk 26;
- iOS 15.1+;
- Android Core-Telecom integration;
- iOS CallKit + PushKit integration;
- host-owned signaling and WebRTC/SIP media.

This package owns native call presentation and normalized lifecycle events. It does **not** own your backend signaling protocol, WebRTC/SIP stack, TURN credentials, authentication, or call business rules.

## Install

For the beta release:

```bash
npm install rn-call-mask@beta
```

During repository development:

```bash
npm install
```

### iOS

Run CocoaPods after installation:

```bash
cd ios
pod install
```

Your host application is responsible for Apple signing/provisioning required for real VoIP pushes and PushKit. Start PushKit from native application startup so VoIP delivery never depends on the React Native runtime:

```swift
import RNCallMask

CallMaskPushKitManager.shared.onTokenUpdated = { token in
    // Send the VoIP token to your backend.
}

CallMaskPushKitManager.shared.start()
```

The singleton keeps the `PKPushRegistry` alive and reports valid incoming VoIP pushes to CallKit natively.

### Android

The package manifest contributes the call-related permissions and components required by the library. On Android 13+, the host app still needs to request notification permission at runtime before incoming-call notifications can appear.

On Android 14+, full-screen incoming-call presentation is OS-controlled. Check capability before relying on it:

```ts
const capabilities = await CallMask.getCapabilities();

if (!capabilities.canUseFullScreen) {
  await CallMask.openFullScreenSettings();
}
```

A denied full-screen capability is not a call failure; heads-up/normal notification presentation remains the fallback.

## Basic incoming call

```ts
import { CallMask } from 'rn-call-mask';

await CallMask.showIncomingCall({
  callId: 'call-123',
  media: 'audio',
  caller: {
    id: 'user-42',
    name: 'Nguyen Van A',
  },
});
```

Every operation is scoped by `callId`. Multiple simultaneous calls are stored independently.

## Native call actions

```ts
await CallMask.answer('call-123');
await CallMask.decline('call-123');
await CallMask.end('call-123', 'local');
await CallMask.silence('call-123');
await CallMask.dismissIncomingUI('call-123');
```

On iOS, CallKit owns incoming-call system UI. `silence()` and arbitrary UI dismissal are therefore not equivalent to Android behavior and may reject when the platform does not permit those operations.

## Events and cold start

Subscribe to live native actions:

```ts
const subscription = CallMask.addEventListener(event => {
  console.log(event.callId, event.type);
});

subscription.remove();
```

Actions that happen before JavaScript listeners exist are persisted natively. Consume them during app startup:

```ts
const pending = await CallMask.consumePendingEvents();
```

Pending events are bounded and consumed once.

## Signaling and media integration

For real calls, prefer the lifecycle coordinator instead of wiring Answer/End handlers manually:

```ts
import {
  createCallLifecycleCoordinator,
  type CallMediaAdapter,
  type CallSignalingAdapter,
} from 'rn-call-mask';

const signaling: CallSignalingAdapter = {
  async accept({ callId }) {
    await api.acceptCall(callId);
  },

  async decline({ callId }) {
    await api.declineCall(callId);
  },

  async end({ callId, reason }) {
    await api.endCall(callId, reason);
  },
};

const media: CallMediaAdapter = {
  async connect({ callId }) {
    await webRtc.connect(callId);
  },

  async disconnect({ callId }) {
    await webRtc.disconnect(callId);
  },
};

const calls = createCallLifecycleCoordinator({
  signaling,
  media,
  onError(error) {
    console.warn('call lifecycle error', {
      callId: error.callId,
      stage: error.stage,
    });
  },
});

await calls.start();
```

Answer convergence is:

```text
native Answer
→ signaling.accept(callId)
→ media.connect(callId)
→ CallMask.markActive(callId)
```

If your backend reports that a call ended while Answer is still pending:

```ts
await calls.handleRemoteTermination(callId, 'cancelled');
```

The coordinator invalidates the in-flight Answer before a late backend response can activate media.

See `docs/HOST_INTEGRATION.md` for the full adapter contract.

## Native host integration

Android push/service code can display a real incoming call without starting React Native:

```kotlin
CallMaskNative.showIncomingCall(
    context = context,
    callId = payload.callId,
    callerName = payload.callerName,
    media = "audio",
)
```

On iOS, `CallMaskPushKitManager` receives VoIP pushes and reports valid incoming calls to CallKit natively.

## Release validation

Before publishing:

```bash
npm install
npm run validate
npm run ci:pack
npm run ci:android
npm run ci:example:android
npm run ci:ios
```

`ci:pack` verifies the actual npm tarball contains compiled JavaScript/types/native sources and excludes native test sources.

The Android example CI installs the generated `.tgz` as a dependency and builds through React Native autolinking instead of linking a repository-local AAR.

## Publish beta

After all CI checks are green:

```bash
npm publish
```

The package currently publishes with the npm `beta` dist-tag, so consumers install it with:

```bash
npm install rn-call-mask@beta
```

Do not promote the package to `latest` until physical-device validation is complete for the Android/OEM and iOS PushKit scenarios in `docs/TEST_MATRIX.md`.

## Development references

Read these before changing lifecycle behavior:

- `AGENTS.md`
- `docs/REQUIREMENTS.md`
- `docs/ROADMAP.md`
- `docs/TEST_MATRIX.md`
- `docs/HOST_INTEGRATION.md`
