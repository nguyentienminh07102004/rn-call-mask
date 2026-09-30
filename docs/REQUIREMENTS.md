# RN Call Mask — Product & Engineering Requirements

Status: Draft v0.1  
Target: React Native calling feature with native incoming-call presentation  
Primary use case: real VoIP calls, not generic marketing notifications

## 1. Goal

Build a React Native call-integration layer that can reliably present and manage real incoming calls when the app is foregrounded, backgrounded, cold-started, or killed by the user/system where the platform permits it.

The feature must support:

- heads-up / popup incoming-call notification on Android;
- full-screen incoming-call presentation when Android allows it;
- standard CallKit incoming-call UI on iOS;
- answer, reject, end, silence, and dismiss operations;
- multiple simultaneous incoming call sessions identified by `callId`;
- independent notification lifecycle per call;
- native-first handling so Answer/Reject does not depend on the React Native JS runtime being alive;
- event replay to JavaScript after cold start;
- future integration with WebRTC/SIP/signaling without coupling media transport into the notification layer.

## 2. Proposed platform baseline

These are project defaults, not permanent API guarantees. Re-evaluate before the first public release.

| Platform | Proposed baseline | Notes |
| --- | --- | --- |
| React Native | Current app-supported version | Prefer New Architecture-compatible bridge surface. Do not require Fabric for call lifecycle. |
| Android | API 26+ | Matches modern VoIP integration direction using AndroidX Core-Telecom. |
| Android targetSdk | Latest stable supported by the host app | Full-screen intent behavior must be tested on Android 14+. |
| iOS | iOS 15+ unless host app requires lower | Use CallKit and PushKit for real VoIP incoming calls. |
| Node | Controlled by `.nvmrc` once scaffolded | CI must use the same version. |

## 3. Architecture constraints

### 3.1 Native-first lifecycle

The following path is required for an incoming call:

```text
Push / native trigger
      ↓
Native CallManager
      ↓
CallRegistry
      ↓
OS call UI / notification
      ↓
Persist native event
      ↓
React Native runtime becomes available
      ↓
Replay normalized event to JS
```

Forbidden dependency:

```text
Push → boot React Native JS → JS decides whether to show the call
```

The JS runtime may not exist when the call arrives.

### 3.2 Single source of truth

Native `CallRegistry` is authoritative for call presentation state while the app may be suspended or killed. JavaScript mirrors that state when available.

Do not maintain unrelated copies of call state in:

- notification manager;
- full-screen activity/controller;
- React Native store;
- Telecom/CallKit adapter.

They must resolve state by `callId` / UUID from the common call model.

### 3.3 Separation of responsibilities

```text
CallManager
├── CallRegistry             # authoritative native state
├── CallNotificationManager # Android presentation
├── AndroidTelecomAdapter   # Core-Telecom integration
├── IOSCallKitAdapter       # CallKit integration
├── NativeEventQueue        # durable/pending events for RN
└── RN bridge               # normalized public API

Application layer
├── Signaling               # backend/socket/API
└── Media                   # WebRTC/SIP/audio/video
```

The library must not own signaling credentials, WebRTC rooms, TURN credentials, or application business rules.

## 4. Core domain model

### 4.1 CallSession

```ts
export type CallState =
  | 'incoming'
  | 'ringing'
  | 'connecting'
  | 'active'
  | 'held'
  | 'ending'
  | 'ended';

export type CallEndReason =
  | 'local'
  | 'remote'
  | 'declined'
  | 'missed'
  | 'busy'
  | 'failed'
  | 'cancelled';

export interface CallSession {
  callId: string;
  direction: 'incoming' | 'outgoing';
  media: 'audio' | 'video';
  state: CallState;

  caller: {
    id?: string;
    name: string;
    handle?: string;
    avatar?: string;
  };

  createdAt: number;
  answeredAt?: number;
  endedAt?: number;
  endReason?: CallEndReason;

  data?: Record<string, string>;
}
```

### 4.2 Event envelope

Every native-to-JS action event must be idempotent and traceable.

```ts
export interface CallEvent {
  eventId: string;
  callId: string;
  type:
    | 'incoming'
    | 'answer'
    | 'decline'
    | 'end'
    | 'timeout'
    | 'silenced'
    | 'presentationChanged';
  timestamp: number;
  payload?: Record<string, unknown>;
}
```

An event must never be processed twice merely because React Native reattached listeners.

## 5. Functional requirements

### CALL-001 — Unique call identity

Every call is identified by a non-empty application-provided `callId`.

Acceptance criteria:

- adding the same `callId` twice must not create duplicate notifications;
- repeated `showIncomingCall()` calls must be idempotent or update the existing session;
- Android notification identity must be stable per `callId`;
- iOS must map `callId` to a stable CallKit UUID for that session.

### CALL-002 — Incoming call registration

`showIncomingCall(call)` registers the call in native state before attempting presentation.

Acceptance criteria:

- a presentation failure must not corrupt another call;
- invalid payloads fail with a normalized error;
- caller name and media type are required or explicitly defaulted.

### CALL-003 — Answer

Answer can originate from:

- Android notification action;
- Android full-screen activity;
- iOS CallKit;
- React Native API.

Acceptance criteria:

- transition is idempotent;
- only valid ringing/incoming calls can be answered;
- answer event is queued even if JS is unavailable;
- incoming ringtone/vibration stops immediately;
- Android incoming notification becomes ongoing call UI when appropriate;
- application signaling/media connection is triggered by the host application after receiving the normalized event unless a native adapter explicitly owns that action.

### CALL-004 — Reject

Reject can originate from native UI or JS.

Acceptance criteria:

- ringing presentation is stopped;
- notification for that `callId` is removed/updated;
- call transitions to `ended` with reason `declined`;
- unrelated call notifications remain visible.

### CALL-005 — End active call

Ending a call is distinct from dismissing notification UI.

Acceptance criteria:

- call state transitions through `ending` to `ended`;
- Telecom/CallKit is notified;
- ongoing notification/foreground presentation is cleaned up;
- event includes end reason.

### CALL-006 — Silence incoming call UI

Expose `silence(callId)`.

Semantics:

- stop library-controlled ringtone/vibration for the target call;
- do not implicitly reject or end the call;
- notification may remain visible;
- state remains `ringing` unless platform integration requires a presentation-state update.

### CALL-007 — Dismiss incoming presentation

Expose `dismissIncomingUI(callId)`.

Semantics:

- removes/hides presentation where platform permits;
- does not implicitly terminate the backend call;
- host application decides whether dismissal also means decline.

On iOS, system CallKit behavior takes precedence; arbitrary hiding of the system incoming-call UI is not guaranteed.

### CALL-008 — Multiple simultaneous incoming calls

The system must support more than one `ringing` session.

Acceptance criteria:

- each Android call has an independent notification;
- Answer/Reject action targets the correct `callId`;
- PendingIntents must not accidentally reuse extras from another call;
- only one custom full-screen Android presentation is foreground owner at a time;
- ending Call B must not remove Call A notification;
- the API can enumerate calls by state.

### CALL-009 — Full-screen owner selection

Android must have deterministic logic for which call is allowed to own the custom full-screen activity.

Default policy:

1. newest ringing call becomes current full-screen candidate;
2. existing active answered call is never replaced by a full-screen ringing call without an explicit product decision;
3. if the current full-screen call ends, the next ringing call remains visible as a notification; promotion to full-screen again is optional and must be configurable later.

### CALL-010 — Cold-start event replay

Native actions that occur before JS listeners are registered must be retained temporarily.

Acceptance criteria:

- queued events are replayed in original timestamp order;
- consuming an event marks it delivered;
- process restart does not create duplicate answer/reject commands;
- a bounded retention policy prevents unbounded storage growth.

### CALL-011 — Remote cancellation

The host app/native entry point must be able to cancel a ringing call when the caller hangs up before answer.

Acceptance criteria:

- `end(callId, 'remote' | 'cancelled')` closes only the target presentation;
- stale Answer action after cancellation is rejected as invalid state;
- full-screen activity self-closes if its call no longer exists/rings.

## 6. Android requirements

### AND-001 — Call notification channel

Create a dedicated incoming-call notification channel with appropriate high importance. Channel creation must be idempotent.

Do not mutate user-selected channel importance after creation.

### AND-002 — CallStyle

Use `NotificationCompat.CallStyle` where supported by AndroidX.

Incoming notification must include:

- caller identity;
- Answer action;
- Decline action;
- call category;
- stable notification identity tied to `callId`;
- content/full-screen intents with immutable/update flags selected correctly.

### AND-003 — Heads-up fallback

If full-screen presentation is not allowed, incoming call must still remain actionable through a normal/heads-up notification when notification permissions and OS policy allow it.

Full-screen denial is not equivalent to call failure.

### AND-004 — Full-screen intent

For Android 14+, check `NotificationManager.canUseFullScreenIntent()`.

Expose:

```ts
canUseFullScreen(): Promise<boolean>;
openFullScreenSettings(): Promise<void>;
```

The library must not promise that full-screen UI always appears. The OS owns final presentation.

Reference: https://developer.android.com/about/versions/14/behavior-changes-14

### AND-005 — Notification permission

On Android versions requiring runtime notification permission, expose capability/status to the host app. The library must provide a clear error/status when notifications are disabled.

### AND-006 — Core-Telecom direction

Prefer AndroidX `core-telecom` / `CallsManager` for production integration rather than exposing legacy `ConnectionService` directly through the public JS API.

Reference: https://developer.android.com/develop/connectivity/telecom/voip-app/telecom

### AND-007 — Foreground execution

When platform rules require foreground execution for an active call, maintain a compliant ongoing call notification. `dismissIncomingUI()` must not incorrectly remove a legally/technically required foreground-service notification.

### AND-008 — PendingIntent isolation

Every action must resolve its call using a unique request identity and explicit `callId`.

Test explicitly:

- Call A arrives;
- Call B arrives;
- pressing Answer on B must never answer A;
- pressing Decline on A must never affect B.

### AND-009 — Full-screen activity safety

The Activity must:

- read `callId` from Intent;
- query current state from CallRegistry;
- finish immediately if the call is absent or not ringing;
- handle repeated intents through `onNewIntent()` when launch mode causes reuse;
- not own authoritative call state.

## 7. iOS requirements

### IOS-001 — Real VoIP call path

For real VoIP incoming calls use PushKit + CallKit.

For iOS 13+ VoIP PushKit delivery, the app must report the incoming call through CallKit as required by Apple platform behavior.

References:

- https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit
- https://developer.apple.com/documentation/callkit/cxprovider

### IOS-002 — System presentation

Do not attempt to reproduce Android heads-up/full-screen notification behavior on iOS. CallKit owns the standard incoming-call system UI.

### IOS-003 — UUID mapping

Maintain deterministic per-session mapping between application `callId` and CallKit `UUID` for the lifetime of the session.

### IOS-004 — Answer/end callbacks

Implement `CXProviderDelegate` handlers so user actions update native CallRegistry first, then enqueue normalized JS events.

### IOS-005 — Multiple calls

Support multiple sessions within CallKit configuration limits chosen by the host app. Do not hardcode a single global current call.

## 8. Public React Native API — target shape

```ts
export interface IncomingCallInput {
  callId: string;
  caller: {
    id?: string;
    name: string;
    handle?: string;
    avatar?: string;
  };
  media?: 'audio' | 'video';
  data?: Record<string, string>;
  android?: {
    requestFullScreen?: boolean;
  };
}

export interface RNCallMask {
  setup(options: SetupOptions): Promise<void>;

  showIncomingCall(call: IncomingCallInput): Promise<void>;
  answer(callId: string): Promise<void>;
  reject(callId: string): Promise<void>;
  end(callId: string, reason?: CallEndReason): Promise<void>;

  silence(callId: string): Promise<void>;
  dismissIncomingUI(callId: string): Promise<void>;

  getCall(callId: string): Promise<CallSession | null>;
  getCalls(): Promise<CallSession[]>;

  canUseFullScreen(): Promise<boolean>;
  openFullScreenSettings(): Promise<void>;

  addEventListener(
    event: CallEvent['type'],
    listener: (event: CallEvent) => void,
  ): { remove(): void };
}
```

The exact TurboModule spec can evolve, but semantics in this document are stable requirements.

## 9. State transition rules

Allowed core transitions:

```text
incoming → ringing
ringing  → connecting | ended
connecting → active | ended
active → held | ending | ended
held → active | ending | ended
ending → ended
```

Invalid transitions must return a normalized error and must not silently mutate unrelated state.

Examples:

- `answer(endedCall)` → reject operation;
- `reject(activeCall)` → reject operation or map to `end()` only if product explicitly chooses that behavior;
- duplicate `end()` → idempotent success is preferred.

## 10. Persistence requirements

Persist only data necessary to restore call presentation/event delivery.

Never persist:

- TURN credentials;
- auth tokens in plain text;
- raw SDP unless another subsystem explicitly requires it;
- unnecessary personal caller data.

Pending event persistence must have:

- maximum count;
- maximum age;
- schema version;
- safe cleanup on startup.

## 11. Observability

Every log line related to a call should include a redacted call correlation value.

Required debug events:

- incoming received;
- registry insert/update/remove;
- notification posted/updated/cancelled;
- full-screen eligibility result;
- native action received;
- event queued/replayed;
- invalid state transition;
- Telecom/CallKit callback;
- terminal end reason.

Do not log auth headers, push tokens, full phone numbers, or sensitive payloads by default.

## 12. Test matrix

### Android lifecycle

| Scenario | Expected result |
| --- | --- |
| Foreground + incoming | heads-up/actionable call presentation |
| Background + incoming | actionable notification; full-screen if OS allows |
| Process cold/killed + incoming native trigger | native UI appears without waiting for RN |
| Screen locked | full-screen/system call presentation when OS permits |
| Notifications denied | capability/error reported; no crash |
| Full-screen permission denied | notification fallback; call still tracked |
| Caller cancels before answer | target UI closes; stale action ignored |
| A then B incoming | two independent notifications |
| Answer B | only B changes state |
| Reject A | B remains intact |
| JS attaches late | pending event replayed once |
| Process recreation | call/event state does not duplicate actions |

### iOS lifecycle

| Scenario | Expected result |
| --- | --- |
| VoIP push with app foreground | CallKit incoming call reported |
| VoIP push with app background | CallKit incoming call reported |
| VoIP push wakes app | report call without waiting for RN |
| Answer from CallKit | normalized answer event queued/emitted |
| End from CallKit | normalized end event queued/emitted |
| Remote cancellation | CallKit call ends with mapped reason |
| Multiple calls | each UUID maps to correct `callId` |

## 13. Non-functional requirements

### Reliability

- No JS dependency for initial native call presentation.
- All action handlers must be idempotent.
- Per-call operations must not mutate other calls.
- Unknown/stale call actions must fail safely.

### Performance

- No network request is allowed on the critical path before displaying the native incoming-call UI when payload already contains enough caller data.
- Avoid loading React Native bundle solely to render Android incoming UI.

### Security

- Validate all push/native input fields before use.
- Treat caller-provided display text as untrusted input.
- Use explicit Intent targets for internal Android components.
- Prefer non-exported components unless external invocation is intentionally required.
- Follow current Android PendingIntent mutability rules.

### Compatibility

- Public JS API must avoid exposing Core-Telecom, `ConnectionService`, `CXProvider`, or `PendingIntent` implementation details.
- Platform adapters may change without breaking application code.

## 14. Explicit non-goals for v1

- implementing WebRTC itself;
- implementing SIP itself;
- backend signaling protocol;
- call recording;
- PSTN/carrier integration;
- arbitrary custom iOS lock-screen call UI;
- conference call UI;
- transfer/DTMF/advanced hold unless added in later roadmap phase;
- forcing Android full-screen UI against OS/user policy.

## 15. Definition of done for v1

v1 is ready only when:

1. public TypeScript API and native contracts are documented;
2. Android supports heads-up, full-screen eligibility/fallback, Answer/Decline, per-call cancel, and multiple simultaneous notifications;
3. Android cold-start action replay passes device tests;
4. Android Core-Telecom integration does not leak platform types into JS API;
5. iOS incoming VoIP call is reported through CallKit from native PushKit handling;
6. multiple call IDs remain isolated in native state and actions;
7. automated lint/typecheck/unit/native validation is green;
8. manual lifecycle matrix is completed on real devices before release.
