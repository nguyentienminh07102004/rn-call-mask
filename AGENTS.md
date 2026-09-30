# AGENTS.md — rn-call-mask

This file is the operating contract for AI coding agents working in this repository.

## Mission

Build a reliable React Native integration for real VoIP call presentation and lifecycle handling across Android and iOS.

Primary product requirements live in:

- `docs/REQUIREMENTS.md`
- `docs/ROADMAP.md`

Read both before changing call lifecycle behavior.

## Non-negotiable invariants

1. **Native-first incoming call handling.** Never require React Native JS to boot before showing or acting on an incoming call.
2. **Every operation is scoped by `callId`.** Do not introduce a global `currentCall` as authoritative state.
3. **CallRegistry is authoritative native state.** Notification, full-screen UI, Telecom, CallKit, and JS bridge must resolve through it.
4. **Presentation is not call termination.** `silence`, `dismissIncomingUI`, `reject`, and `end` have different semantics.
5. **Actions must be idempotent.** Duplicate push/action delivery must not duplicate calls or commands.
6. **Multiple calls are a first-class requirement.** All notification/PendingIntent/UUID logic must be tested with Call A and Call B.
7. **Do not expose platform implementation types to JS.** No `CXProvider`, `ConnectionService`, `CallsManager`, `PendingIntent`, or Android Activity types in public TypeScript API.
8. **Full-screen Android presentation is best-effort.** Never claim or encode an assumption that the OS must show full-screen UI.
9. **iOS real VoIP calls use system CallKit presentation.** Do not build a fake Android-style lock-screen call UI on iOS.
10. **Do not log secrets or raw PII.** No auth tokens, full push tokens, TURN credentials, raw phone numbers, or full signaling payloads in default logs.

## Architecture target

```text
React Native public API
        │
        ▼
NativeCallModule
        │
        ▼
CallManager
├── CallRegistry
├── NativeEventQueue
├── AndroidCallNotificationManager
├── AndroidTelecomAdapter
└── IOSCallKitAdapter

Host application owns:
├── signaling
└── WebRTC/SIP media
```

## Expected repository structure

The exact scaffold may evolve, but keep responsibilities close to this layout:

```text
src/
├── index.ts
├── types.ts
├── errors.ts
├── NativeCallMask.ts
└── CallMask.ts

android/src/main/java/.../callmask/
├── CallMaskModule.kt
├── CallManager.kt
├── CallRegistry.kt
├── CallSession.kt
├── NativeEventQueue.kt
├── notification/
│   ├── CallNotificationManager.kt
│   ├── CallActionReceiver.kt
│   └── IncomingCallActivity.kt
└── telecom/
    └── TelecomAdapter.kt

ios/
├── CallMaskModule.swift
├── CallManager.swift
├── CallRegistry.swift
├── NativeEventQueue.swift
└── CallKitAdapter.swift

example/
└── ...
```

## Skills

Use the relevant repository skill before implementing a related area:

- `.agents/skills/call-state-machine/SKILL.md`
- `.agents/skills/android-call-notifications/SKILL.md`
- `.agents/skills/ios-callkit-pushkit/SKILL.md`
- `.agents/skills/ci-validation/SKILL.md`

## Coding rules

### TypeScript

- strict typing;
- no `any` in public API;
- discriminated unions for event/state types;
- public async failures use stable error codes;
- keep public API platform-neutral.

### Kotlin

- prefer immutable domain objects;
- use structured concurrency where coroutines are used;
- do not use Activity/Service singletons as state storage;
- explicit Intent targets;
- correct PendingIntent mutability flags;
- unique action identity per `callId`;
- thread-safe registry mutations.

### Swift

- CallKit callbacks must update native state before notifying JS;
- `callId ↔ UUID` mapping must be deterministic for a live session;
- do not wait for the React Native bridge in PushKit incoming-call handling;
- complete PushKit callbacks correctly after reporting/handling the call.

## State-machine rules

Do not mutate state ad hoc.

Core transitions:

```text
incoming → ringing
ringing → connecting | ended
connecting → active | ended
active → held | ending | ended
held → active | ending | ended
ending → ended
```

When adding a transition:

1. update shared/domain documentation;
2. add transition tests;
3. define duplicate/idempotent behavior;
4. define native presentation side effects;
5. define event emitted to JS.

## Multiple-call checklist

Every feature touching call actions must be tested with at least two calls.

Minimum regression sequence:

```text
show A
show B
answer B
assert A is still ringing
reject A
assert B remains active/connecting
```

For Android action code, verify the `callId` used by PendingIntent/Intent cannot be overwritten or reused by another call.

## Native event queue rules

Events emitted before JS listener registration must be queued.

Queue requirements:

- unique `eventId`;
- timestamp order;
- bounded count and age;
- consume-once semantics;
- schema versioning if persisted;
- no sensitive payload persistence unless explicitly required.

## Android presentation rules

Incoming Android call presentation should use:

- dedicated high-importance channel;
- `NotificationCompat.CallStyle` where appropriate;
- Answer/Decline native actions;
- optional full-screen intent;
- heads-up/notification fallback;
- Core-Telecom direction for Telecom integration.

Do not fail a call just because `canUseFullScreenIntent()` is false.

## iOS presentation rules

For real VoIP incoming calls:

```text
PushKit callback
   ↓
validate payload
   ↓
register native CallSession
   ↓
CXProvider.reportNewIncomingCall
   ↓
queue/emit normalized events from CXProviderDelegate actions
```

Do not wait for JavaScript before reporting the call.

## Tests required per change

A change is not complete without the relevant automated tests.

At minimum consider:

- happy path;
- duplicate action;
- stale call action;
- unknown `callId`;
- Call A / Call B isolation;
- JS unavailable / late listener path for lifecycle-sensitive work.

Native UI/device-specific behavior also needs manual validation. Unit tests do not replace device testing.

## CI contract

Once `package.json` exists, it must provide:

```json
{
  "scripts": {
    "lint": "...",
    "typecheck": "...",
    "test": "..."
  }
}
```

When Android source is added, add a deterministic `ci:android` package script.  
When iOS source is added, add a deterministic `ci:ios` package script.

GitHub Actions must call repository scripts rather than duplicating complex build commands in YAML.

## Definition of done for agent tasks

Before marking a task complete:

1. implementation matches `docs/REQUIREMENTS.md`;
2. tests cover behavior, not only line execution;
3. multiple-call isolation is considered;
4. no JS dependency was introduced into critical incoming call handling;
5. CI commands relevant to the change pass;
6. docs/API types are updated if behavior changed;
7. device-only validation steps are explicitly listed in the PR description.

## Do not do these without an explicit requirement

- replace Core-Telecom with a public legacy ConnectionService API;
- make Notifee a required core dependency;
- make `react-native-callkeep` the public architecture boundary;
- implement signaling/WebRTC inside notification classes;
- silently map dismiss to reject;
- force a single-call model;
- create a custom iOS lock-screen replacement for CallKit;
- disable failing tests to make CI green.
