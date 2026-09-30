# RN Call Mask — Implementation Roadmap

This roadmap assumes AI-assisted implementation with a developer continuously running the code on real devices and returning logs/behavior for fixes.

## Delivery strategy

Build vertical slices that are testable on device. Avoid writing the whole abstraction before proving native presentation.

Recommended order:

```text
contract/state
   ↓
Android notification MVP
   ↓
Android cold-start + multi-call
   ↓
Core-Telecom
   ↓
iOS CallKit + PushKit
   ↓
hardening + release
```

## Phase 0 — Repository bootstrap

Estimated effort with AI: 0.5 day.

Deliverables:

- React Native library/app module scaffold;
- TypeScript strict mode;
- lint + formatting + test baseline;
- `AGENTS.md` and local agent skills;
- GitHub Actions validation;
- example application or host-app integration target;
- Android and iOS native module placeholders.

Exit criteria:

- `lint`, `typecheck`, and `test` scripts exist;
- CI is green;
- example/host app can import a no-op native module.

## Phase 1 — Domain contract and CallRegistry

Estimated effort with AI: 0.5–1 day.

Implement:

- `CallSession` and normalized call states;
- call end reasons;
- state transition validator;
- native `CallRegistry` interface;
- duplicate `callId` policy;
- JS API interfaces;
- normalized error codes;
- event envelope with `eventId`, `callId`, timestamp.

Tests:

- valid state transitions;
- invalid transitions;
- duplicate incoming call registration;
- isolation of Call A and Call B;
- idempotent end/reject behavior.

Exit criteria:

- call domain tests are deterministic and platform-independent;
- no presentation code owns authoritative call state.

## Phase 2 — Android incoming-call notification MVP

Estimated effort with AI: 1–2 days.

Implement:

- dedicated incoming-call notification channel;
- `NotificationCompat.CallStyle` incoming notification;
- Answer and Decline PendingIntents;
- per-`callId` notification tag/id strategy;
- `CallActionReceiver`;
- custom full-screen `IncomingCallActivity`;
- `USE_FULL_SCREEN_INTENT` manifest configuration;
- `canUseFullScreen()` and settings deep link;
- heads-up fallback;
- `silence(callId)`;
- `dismissIncomingUI(callId)`;
- `end(callId)` cleanup.

Manual tests:

- foreground;
- background;
- lock screen;
- full-screen permission on/off;
- notification permission on/off.

Exit criteria:

- one incoming call can be shown, answered, declined, silenced, and cancelled without relying on JS listener availability.

## Phase 3 — Multi-call and cold-start correctness

Estimated effort with AI: 1–2 days.

Implement:

- multiple independent ringing sessions;
- unique PendingIntent identities;
- full-screen owner policy;
- `onNewIntent()` routing;
- native pending-event queue;
- event replay to RN;
- stale action rejection;
- remote caller cancellation;
- process recreation cleanup.

Required scenarios:

```text
A arrives
B arrives
Answer B
Reject A
```

and:

```text
A arrives
process starts cold
user answers before RN listeners attach
RN starts
answer event delivered exactly once
```

Exit criteria:

- multi-call isolation tests pass;
- killed/cold-start path works on at least one Google device/emulator and one OEM device if available.

## Phase 4 — Android Telecom integration

Estimated effort with AI: 1–2 days.

Implement:

- AndroidX Core-Telecom dependency;
- `CallsManager` registration;
- native answer/disconnect callbacks;
- audio endpoint state exposure if needed;
- ongoing call notification after answer;
- foreground execution compliance;
- end reason mapping between Telecom and internal model.

Do not expose Core-Telecom classes in public JS API.

Exit criteria:

- incoming notification state and Telecom state stay synchronized;
- answering from OS surface updates the same CallSession;
- active call cleanup leaves no orphan notification/service.

Reference:

https://developer.android.com/develop/connectivity/telecom/voip-app/telecom

## Phase 5 — Host signaling/media integration

Estimated effort: 0.5–2 days depending on existing app infrastructure.

Integrate normalized call events with the host app:

```text
answer event
   ↓
host signaling accept
   ↓
WebRTC/SIP connect
   ↓
mark active
```

and:

```text
remote cancel
   ↓
end(callId, cancelled)
   ↓
close native presentation
```

Exit criteria:

- native presentation and backend call state converge reliably;
- network failure maps to a terminal end reason;
- no WebRTC implementation leaks into the notification module.

## Phase 6 — iOS CallKit + PushKit

Estimated effort with AI: 2–4 days, plus provisioning/APNs setup time.

Implement:

- `CXProvider` configuration;
- `CXProviderDelegate` answer/end handlers;
- `callId ↔ UUID` mapping;
- PushKit token registration integration hook;
- native VoIP push handling;
- immediate `reportNewIncomingCall` path;
- remote cancellation/end reason mapping;
- pending event queue parity with Android;
- multiple call handling according to configured CallKit limits.

Important platform rule:

For real VoIP pushes on modern iOS, report the incoming call through CallKit from the native PushKit callback. Do not wait for React Native to boot.

References:

- https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit
- https://developer.apple.com/documentation/callkit/cxprovider

Exit criteria:

- foreground/background/wake-by-VoIP-push scenarios work on a physical iPhone;
- Answer/End events reach the same normalized JS event surface.

## Phase 7 — Hardening

Estimated effort with AI: 2–4 days of implementation plus device testing.

Focus areas:

- Samsung/Xiaomi/Oppo/Vivo behavior where available;
- Android 13/14/15+ permission permutations;
- app update/reinstall/channel migration;
- duplicate push delivery;
- out-of-order remote cancel vs answer;
- process death during action handling;
- notification tap after call already ended;
- rapid call bursts;
- memory/thread safety;
- bounded event persistence;
- PII-safe logs.

Add instrumentation/debug APIs only behind development flags.

Exit criteria:

- `docs/TEST_MATRIX.md` or release checklist records real-device results;
- no known P0/P1 lifecycle bugs.

## Phase 8 — Release readiness

Estimated effort with AI: 0.5–1 day.

Deliverables:

- README usage guide;
- API reference;
- Android manifest/permission integration guide;
- iOS entitlement/PushKit/CallKit integration guide;
- changelog;
- semantic versioning policy;
- example app flows;
- release GitHub Action if publishing as a package.

Exit criteria:

- clean install integration is reproducible;
- CI passes from a fresh checkout;
- package contents are audited before publish.

## Milestones

### M0 — Android visual MVP

Scope:

- one call;
- heads-up;
- full-screen request;
- Answer/Decline;
- cancel.

Target: 1–2 focused days with AI assistance.

### M1 — Android usable beta

Scope:

- multiple calls;
- cold-start events;
- remote cancellation;
- Core-Telecom;
- ongoing call notification.

Target cumulative: roughly 3–6 focused days.

### M2 — Cross-platform beta

Scope:

- iOS CallKit;
- PushKit;
- normalized API parity;
- real signaling/media integration.

Target cumulative: roughly 1–2 weeks, strongly dependent on device/provisioning/debug time.

### M3 — Production candidate

Scope:

- OEM/device matrix;
- race-condition hardening;
- observability;
- CI/release automation;
- documentation.

Target cumulative: roughly 1.5–3 weeks for one developer with strong AI assistance, assuming signaling/WebRTC already exists.

## Work breakdown for AI coding agents

Agents should receive one vertical task at a time. Preferred task size:

- one state transition feature;
- one Android notification behavior;
- one CallKit callback path;
- one race-condition regression test.

Avoid prompts such as “implement the whole calling library”. Prefer:

```text
Implement Android incoming notification for one CallSession.
Constraints:
- state comes from CallRegistry;
- unique callId action routing;
- no JS dependency;
- tests for duplicate callId and PendingIntent isolation.
```

After each native slice, run it on a real device before expanding scope.
