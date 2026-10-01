import Foundation

public final class CallMaskIOSEventStore {
    public static let shared = CallMaskIOSEventStore()

    private let lock = NSLock()
    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let key = "rn_call_mask_ios_events"
    private let maxEvents = 100
    private let maxAge: TimeInterval = 24 * 60 * 60

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func append(_ event: CallMaskIOSEvent) {
        lock.lock()
        defer { lock.unlock() }

        var events = readLocked()
        events.append(event)
        let cutoff = Date().addingTimeInterval(-maxAge)
        events = events.filter { $0.timestamp >= cutoff }
        if events.count > maxEvents {
            events = Array(events.suffix(maxEvents))
        }
        writeLocked(events)
    }

    public func consume() -> [CallMaskIOSEvent] {
        lock.lock()
        defer { lock.unlock() }

        let events = readLocked().sorted(by: { $0.timestamp < $1.timestamp })
        defaults.removeObject(forKey: key)
        return events
    }

    private func readLocked() -> [CallMaskIOSEvent] {
        guard
            let data = defaults.data(forKey: key),
            let events = try? decoder.decode([CallMaskIOSEvent].self, from: data)
        else {
            return []
        }
        return events
    }

    private func writeLocked(_ events: [CallMaskIOSEvent]) {
        guard let data = try? encoder.encode(events) else { return }
        defaults.set(data, forKey: key)
    }
}
