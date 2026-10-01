# Device Test Matrix

Use this checklist after CI is green. Device-only behavior is a release gate for incoming-call presentation.

## Build and install

```bash
npm install
npm run ci:android
npm run ci:example:android
adb install -r example/android/app/build/outputs/apk/debug/app-debug.apk
```

Start Metro in another terminal:

```bash
cd example
npx react-native start
```

Launch the example app and grant notification permission from the harness on Android 13+.

## Native cold-start trigger

The example contains a debug-only exported receiver so a call can be created without starting React Native JavaScript.

Force-stop the app first:

```bash
adb shell am force-stop com.rncallmask.example
```

Show Call A through the native entry point:

```bash
adb shell am broadcast \
  -n com.rncallmask.example/.DebugCallReceiver \
  -a com.rncallmask.example.DEBUG_CALL \
  --es operation show \
  --es callId native-A \
  --es callerName "Native Caller A" \
  --es media audio
```

Show Call B independently:

```bash
adb shell am broadcast \
  -n com.rncallmask.example/.DebugCallReceiver \
  -a com.rncallmask.example.DEBUG_CALL \
  --es operation show \
  --es callId native-B \
  --es callerName "Native Caller B" \
  --es media video
```

Remote-cancel only Call A:

```bash
adb shell am broadcast \
  -n com.rncallmask.example/.DebugCallReceiver \
  -a com.rncallmask.example.DEBUG_CALL \
  --es operation end \
  --es callId native-A \
  --es reason cancelled
```

After interacting with the native notification/full-screen actions, open the example app. The pending event list should contain the native action exactly once.

## Required Android scenarios

| Scenario | Expected result | Result |
| --- | --- | --- |
| Foreground incoming call | Actionable CallStyle notification appears | ⬜ |
| Background incoming call | Actionable notification remains available | ⬜ |
| Locked screen | Full-screen UI appears when OS allows it; notification fallback remains otherwise | ⬜ |
| Screen off | Incoming presentation follows OS policy without crashing | ⬜ |
| Notification permission denied | No notification is posted; capability reports disabled | ⬜ |
| Android 14+ full-screen disabled | Call remains actionable as notification; no call state failure | ⬜ |
| Silence A | A remains ringing while sound/vibration presentation becomes silent | ⬜ |
| Dismiss UI A | A call state remains live while presentation is removed where permitted | ⬜ |
| A then B | Two independent notifications exist | ⬜ |
| Answer B, decline A | B becomes connecting/active and A ends; B is unaffected | ⬜ |
| Active A then incoming B | B must not replace A as full-screen owner | ⬜ |
| Native call while JS stopped | Native presentation appears without React Native startup | ⬜ |
| Answer native call before JS starts | Pending answer event is replayed once after app starts | ⬜ |
| Remote cancel then stale Answer | No answer event; no crash | ⬜ |
| Process recreation | Registry restores live sessions without duplicating calls | ⬜ |

## Device coverage

Record exact OS build and device model.

| Device | Android | OEM | Result | Notes |
| --- | --- | --- | --- | --- |
| Emulator / Pixel |  | Google | ⬜ | |
| Physical Android |  |  | ⬜ | |
| OEM device |  | Samsung/Xiaomi/Oppo/Vivo/etc. | ⬜ | |

## Evidence to attach to PR/release

- Android version and device model;
- whether notification permission is enabled;
- whether full-screen intent access is enabled;
- screenshots or screen recording for lock-screen behavior;
- redacted event log showing Call A/B isolation;
- redacted logcat only when diagnosing a failure.

Do not attach phone numbers, push tokens, auth tokens, TURN credentials, or full signaling payloads.
