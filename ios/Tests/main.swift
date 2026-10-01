import Darwin
import Foundation

func expect(_ condition: @autoclosure () -> Bool, _ message: String) {
    if !condition() {
        fputs("FAIL: " + message + "\n", stderr)
        exit(1)
    }
}

let suiteName = "rn-call-mask-ios-tests-" + UUID().uuidString
guard let defaults = UserDefaults(suiteName: suiteName) else {
    fatalError("Unable to create isolated UserDefaults")
}
defer {
    defaults.removePersistentDomain(forName: suiteName)
}

let registry = CallMaskIOSRegistry(defaults: defaults)

let firstA = try registry.registerIncoming(
    callId: "A",
    callerId: "caller-A",
    callerName: "Caller A",
    handle: nil,
    media: "audio"
)
let duplicateA = try registry.registerIncoming(
    callId: "A",
    callerId: "caller-A-new",
    callerName: "Caller A duplicate",
    handle: nil,
    media: "video"
)
let callB = try registry.registerIncoming(
    callId: "B",
    callerId: "caller-B",
    callerName: "Caller B",
    handle: "user-b",
    media: "video"
)

expect(firstA.uuid == duplicateA.uuid, "duplicate callId must retain stable UUID")
expect(registry.allSessions().count == 2, "A and B must remain independent sessions")
expect(callB.callId == "B", "Call B identity must remain isolated")

let connectingA = try registry.transition(callId: "A", to: .connecting)
expect(connectingA.state == .connecting, "A should transition to connecting")
expect(registry.session(callId: "B")?.state == .ringing, "A transition must not mutate B")

let activeA = try registry.transition(callId: "A", to: .active)
expect(activeA.state == .active, "A should transition to active")
_ = try registry.transition(callId: "A", to: .ending)
let endedA = try registry.transition(callId: "A", to: .ended, endReason: "local")
expect(endedA.endReason == "local", "terminal reason must persist")

do {
    _ = try registry.transition(callId: "A", to: .ringing)
    fputs("FAIL: ended call must not return to ringing\n", stderr)
    exit(1)
} catch CallMaskIOSError.invalidState {
    // Expected.
}

let eventStore = CallMaskIOSEventStore(defaults: defaults)
let now = Date()
eventStore.append(
    CallMaskIOSEvent(
        callId: "B",
        type: "answer",
        timestamp: now.addingTimeInterval(2),
        state: "connecting"
    )
)
eventStore.append(
    CallMaskIOSEvent(
        callId: "A",
        type: "end",
        timestamp: now.addingTimeInterval(1),
        state: "ended",
        endReason: "local"
    )
)

let consumed = eventStore.consume()
expect(consumed.count == 2, "pending event store should return all queued events")
expect(consumed[0].callId == "A", "pending events must replay in timestamp order")
expect(consumed[1].callId == "B", "pending events must replay in timestamp order")
expect(eventStore.consume().isEmpty, "consumed events must not replay twice")

print("iOS core registry/event tests passed.")
