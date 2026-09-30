# rn-call-mask

Native-first incoming-call presentation for React Native VoIP apps.

Current bootstrap scope:

- Android heads-up incoming call notification;
- Android full-screen incoming call intent when allowed by the OS;
- independent notifications per `callId`;
- native Answer / Decline / End actions;
- persisted native call registry and pending event queue;
- React Native TypeScript facade;
- CI for TypeScript and Android compilation.

> This repository is intentionally Android-first. iOS CallKit/PushKit work is tracked in `docs/ROADMAP.md`.

## Install during development

```bash
npm install
```

## Public API

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

const subscription = CallMask.addEventListener((event) => {
  if (event.type === 'answer') {
    // Host app owns signaling / WebRTC connection.
  }
});

await CallMask.silence('call-123');
await CallMask.dismissIncomingUI('call-123');

subscription.remove();
```

On app startup, consume native actions that happened before JavaScript listeners existed:

```ts
const pending = await CallMask.consumePendingEvents();
```

## Example device harness

Build the Android library and example app:

```bash
npm install
npm run ci:android
npm run ci:example:android
```

The example provides Call A/B controls, capabilities, event replay, and a native debug receiver for cold-start testing. Follow `docs/TEST_MATRIX.md` for the exact ADB commands and device scenarios.

## Native host integration

Android push/service code can show a real incoming call without booting React Native:

```kotlin
CallMaskNative.showIncomingCall(
    context = context,
    callId = payload.callId,
    callerName = payload.callerName,
    media = "audio",
)
```

The host app still owns signaling and WebRTC/SIP media.

## Validation

```bash
npm run validate
npm run ci:android
npm run ci:example:android
```

Read `AGENTS.md`, `docs/REQUIREMENTS.md`, `docs/ROADMAP.md`, and `docs/TEST_MATRIX.md` before changing lifecycle behavior.
