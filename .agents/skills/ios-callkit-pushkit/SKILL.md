# Skill: iOS CallKit and PushKit

Use this skill for iOS incoming VoIP calls, PushKit, CallKit, UUID mapping, CXProviderDelegate actions, or cold-start behavior.

## Critical rule

For real VoIP PushKit incoming notifications on modern iOS, report the incoming call through CallKit from native code. Do not wait for React Native JS to start.

References:

- https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit
- https://developer.apple.com/documentation/callkit/cxprovider

## Incoming call workflow

```text
PushKit callback
   ↓
validate minimum payload
   ↓
resolve/create callId ↔ UUID mapping
   ↓
insert/update native CallSession
   ↓
CXProvider.reportNewIncomingCall
   ↓
finish PushKit callback appropriately
   ↓
connect signaling in parallel as host architecture requires
```

## CXProviderDelegate workflow

For Answer/End:

1. resolve UUID to `callId`;
2. validate current CallSession state;
3. atomically update CallRegistry;
4. fulfill/fail CallKit action appropriately;
5. queue normalized event for React Native;
6. never emit duplicate logical Answer/End events.

## Multiple-call rules

- no single global call UUID;
- mapping is per live session;
- ending UUID B must not remove UUID A mapping;
- configuration limits must be explicit, not accidental.

## Remote cancellation

When backend/native signaling reports remote cancellation:

- resolve `callId`;
- update CallRegistry terminal state;
- report end to CXProvider with the mapped reason;
- queue normalized JS end/cancel event if needed;
- ignore stale later Answer callback safely.

## Data minimization

VoIP push payload should contain enough data to identify/report the call but should avoid unnecessary secrets. Never log the full VoIP token or sensitive payload by default.

## Physical-device validation

Simulator coverage is insufficient for final PushKit behavior. Verify on a physical iPhone:

- foreground;
- background;
- wake by VoIP push;
- Answer from system UI;
- End from system UI;
- remote caller cancellation;
- multiple call IDs if product supports them.
