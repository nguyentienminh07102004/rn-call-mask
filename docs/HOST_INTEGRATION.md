# Host signaling and media integration

`rn-call-mask` owns native call presentation and normalized lifecycle events. It intentionally does **not** own your signaling protocol, WebRTC/SIP session, TURN credentials, authentication, or backend business rules.

The optional `CallLifecycleCoordinator` connects those two layers without coupling the package to a specific backend.

## Adapter contract

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
    logger.warn('call integration failure', {
      callId: error.callId,
      stage: error.stage,
    });
  },
});

await calls.start();
```

The coordinator subscribes to live native events **before** consuming the durable pending-event queue. Native `eventId` values are deduplicated, so an event observed through both paths is processed once.

## Answer flow

```text
native Answer
    ↓
answer event
    ↓
signaling.accept(callId)
    ↓
media.connect(callId)
    ↓
CallMask.markActive(callId)
```

If signaling acceptance fails, the coordinator ends native state with `failed`.

If signaling succeeds but media connection fails, it notifies signaling with a failed terminal reason and ends native state.

## Remote cancellation

When your socket/backend reports that a call ended before or during answer:

```ts
await calls.handleRemoteTermination(callId, 'cancelled');
```

The coordinator invalidates the in-flight generation **immediately**, before waiting for queued work. Therefore this race:

```text
user taps Answer
signaling.accept() is pending
remote cancel arrives
signaling.accept() resolves late
```

does not proceed to media connection or `markActive()`.

The media adapter's `disconnect()` should be idempotent because terminal paths may converge from more than one source.

## End-event echo prevention

Native terminal events whose reason is `remote`, `cancelled`, `busy`, or `missed` are not sent back through `signaling.end()`; this prevents echoing a backend-originated termination back to the backend.

A local active-call end is sent through `signaling.end()`.

A decline uses `signaling.decline()`.

## Integration boundary

The package should know:

- `callId`;
- normalized native action;
- normalized terminal reason;
- whether media became active.

The package should not know:

- socket room names;
- backend auth tokens;
- SDP;
- ICE/TURN credentials;
- application-specific call permissions;
- billing/business rules.

Keep those inside the host adapters.
