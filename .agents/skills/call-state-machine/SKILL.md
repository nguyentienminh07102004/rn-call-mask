# Skill: Call State Machine and Registry

Use this skill when changing call states, multi-call behavior, event delivery, or the native CallRegistry.

## Goal

Keep call lifecycle deterministic across Android, iOS, and React Native while supporting duplicate/out-of-order events.

## Required workflow

1. Identify the target `callId`.
2. Read the current `CallSession` from CallRegistry.
3. Validate the requested transition against the explicit transition table.
4. Apply one atomic state mutation.
5. Trigger platform side effects after the state mutation is accepted.
6. Create at most one normalized event for a logical action.
7. Queue the event if JS is unavailable.
8. Add tests for duplicate and stale actions.
9. Add a two-call isolation test.

## Transition baseline

```text
incoming → ringing
ringing → connecting | ended
connecting → active | ended
active → held | ending | ended
held → active | ending | ended
ending → ended
```

## Idempotency guidance

Preferred behavior:

- duplicate `showIncomingCall(callId)` → update/no-op, never duplicate;
- duplicate `end(callId)` after already ended → safe no-op/idempotent success;
- duplicate Answer after first Answer → do not issue signaling twice;
- stale Answer after remote cancellation → reject as invalid state;
- duplicate native callback → dedupe by state/event identity.

## Concurrency guidance

Registry mutation must be serialized or otherwise thread safe.

Never use:

- Activity fields as the source of truth;
- one global mutable `currentCall`;
- notification ID as the only domain identity.

## Test template

```text
Given A and B are ringing
When B is answered
Then B becomes connecting/active
And A remains ringing
And exactly one answer event for B exists
And no event for A is emitted
```

## Done checklist

- transition documented;
- invalid transition behavior defined;
- duplicate behavior defined;
- event semantics defined;
- A/B isolation test added;
- no platform type leaked into public TS contract.
