# Signaling and media integration skill

Use this skill when changing host signaling/media orchestration for rn-call-mask.

## Invariants

1. rn-call-mask must not implement or depend on a concrete WebRTC, SIP, WebSocket, GraphQL, or REST backend.
2. Native call actions remain authoritative for system presentation state.
3. Backend/media adapters are injected at the TypeScript host boundary.
4. Process an `eventId` at most once per coordinator lifecycle.
5. Serialize asynchronous integration work per `callId`; unrelated calls may progress independently.
6. A remote terminal event must invalidate an in-flight answer immediately, before queued async work completes.
7. Never call `markActive` until signaling acceptance and media connection have both succeeded.
8. If media connect fails after backend acceptance, converge both backend and native state to a terminal failure.
9. Do not echo backend-originated remote/cancelled/busy/missed endings back to the backend.
10. Media disconnect must be treated as idempotent.

## Required regression tests

When touching lifecycle orchestration, keep tests for:

- answer -> accept -> media connect -> active;
- duplicate event IDs;
- remote cancel racing an in-flight answer;
- signaling accept failure;
- media connect failure;
- local end vs remote-end echo behavior;
- live event + pending replay duplication.

Do not weaken these tests to make a race pass.
