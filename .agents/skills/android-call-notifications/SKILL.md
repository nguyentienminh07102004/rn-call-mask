# Skill: Android Call Notifications and Full-Screen Presentation

Use this skill for Android incoming-call notification, CallStyle, full-screen intent, action receivers, foreground/ongoing call presentation, or permission work.

## Platform principles

- Full-screen intent is best-effort and controlled by Android/user policy.
- Android 14+ calling apps should check `NotificationManager.canUseFullScreenIntent()` before assuming full-screen eligibility.
- A denied full-screen capability must fall back to an actionable notification where notification permission allows it.
- Multiple incoming calls require isolated notification and PendingIntent identities.

Reference:

https://developer.android.com/about/versions/14/behavior-changes-14

## Implementation workflow

1. Validate `callId` and resolve CallSession from CallRegistry.
2. Ensure the dedicated notification channel exists.
3. Build caller `Person` metadata.
4. Build Answer and Decline PendingIntents with target `callId`.
5. Ensure request/action identity cannot collide with another call.
6. Build `NotificationCompat.CallStyle.forIncomingCall(...)` where compatible.
7. Attach explicit content/full-screen intent as required.
8. Post using a stable per-call notification tag/id.
9. Record presentation result/status without changing call state incorrectly.
10. Add cleanup path for reject/end/remote cancel.

## Multiple-call invariant

Never use one constant notification identity for all incoming calls.

Valid strategies include a stable tag derived from `callId` plus a fixed type id, or a collision-resistant stable id mapping.

## Action receiver rules

The receiver must:

- be explicit/internal where possible;
- validate action string;
- read `callId`;
- resolve current call state;
- reject stale actions;
- update CallManager/CallRegistry before JS notification;
- never boot JS merely to decide whether an action is valid.

## Full-screen Activity rules

The Activity must:

- receive `callId`;
- query CallRegistry;
- finish if call is no longer ringing;
- handle `onNewIntent()` if reused;
- render presentation only;
- never become the authoritative call owner.

## Foreground/ongoing call rule

After answer, if foreground execution is required, transition from incoming CallStyle to an ongoing compliant call notification instead of blindly cancelling every notification.

## Required regression tests

```text
A notification shown
B notification shown
Answer B action pressed
→ B changes state
→ A remains visible/ringing
```

```text
A remote-cancelled
stale Answer A PendingIntent fires
→ no answer event
→ no crash
```

## Device validation

At minimum manually verify:

- foreground;
- background;
- locked screen;
- screen off;
- notification permission disabled;
- full-screen permission disabled;
- Android 14+;
- one non-Pixel OEM when possible.
