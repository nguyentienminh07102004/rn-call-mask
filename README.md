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

## Validation

```bash
npm run validate
npm run ci:android
```

Read `AGENTS.md`, `docs/REQUIREMENTS.md`, and `docs/ROADMAP.md` before changing lifecycle behavior.
