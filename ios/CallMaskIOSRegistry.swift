import Foundation

public final class CallMaskIOSRegistry {
    public static let shared = CallMaskIOSRegistry()

    private let lock = NSLock()
    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private var sessions: [String: CallMaskIOSSession]

    private let sessionsKey = "rn_call_mask_ios_sessions"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if
            let data = defaults.data(forKey: sessionsKey),
            let restored = try? decoder.decode([String: CallMaskIOSSession].self, from: data)
        {
            self.sessions = restored
        } else {
            self.sessions = [:]
        }
    }

    public func registerIncoming(
        callId: String,
        callerId: String,
        callerName: String,
        handle: String?,
        media: String,
        createdAt: Date = Date()
    ) throws -> CallMaskIOSSession {
        let normalizedCallId = callId.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedName = callerName.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !normalizedCallId.isEmpty else {
            throw CallMaskIOSError.invalidArgument("callId must be non-empty")
        }
        guard !normalizedName.isEmpty else {
            throw CallMaskIOSError.invalidArgument("callerName must be non-empty")
        }
        guard media == "audio" || media == "video" else {
            throw CallMaskIOSError.invalidArgument("media must be audio or video")
        }

        lock.lock()
        defer { lock.unlock() }

        if let existing = sessions[normalizedCallId], existing.state != .ended {
            return existing
        }

        let session = CallMaskIOSSession(
            callId: normalizedCallId,
            callerId: callerId.isEmpty ? normalizedCallId : callerId,
            callerName: normalizedName,
            handle: handle,
            media: media,
            state: .ringing,
            createdAt: createdAt
        )
        sessions[normalizedCallId] = session
        persistLocked()
        return session
    }

    public func session(callId: String) -> CallMaskIOSSession? {
        lock.lock()
        defer { lock.unlock() }
        return sessions[callId]
    }

    public func session(uuid: UUID) -> CallMaskIOSSession? {
        lock.lock()
        defer { lock.unlock() }
        return sessions.values.first(where: { $0.uuid == uuid })
    }

    public func allSessions() -> [CallMaskIOSSession] {
        lock.lock()
        defer { lock.unlock() }
        return sessions.values.sorted(by: { $0.createdAt < $1.createdAt })
    }

    @discardableResult
    public func transition(
        callId: String,
        to next: CallMaskIOSState,
        endReason: String? = nil
    ) throws -> CallMaskIOSSession {
        lock.lock()
        defer { lock.unlock() }

        guard var current = sessions[callId] else {
            throw CallMaskIOSError.unknownCall(callId)
        }
        guard current.state.canTransition(to: next) else {
            throw CallMaskIOSError.invalidState("\(current.state.rawValue) -> \(next.rawValue)")
        }

        if current.state == next {
            return current
        }

        current.state = next
        if next == .connecting && current.answeredAt == nil {
            current.answeredAt = Date()
        }
        if next == .ended {
            current.endedAt = Date()
            current.endReason = endReason ?? current.endReason
        }

        sessions[callId] = current
        persistLocked()
        return current
    }

    private func persistLocked() {
        guard let data = try? encoder.encode(sessions) else { return }
        defaults.set(data, forKey: sessionsKey)
    }
}

public enum CallMaskIOSError: Error, LocalizedError, Equatable {
    case invalidArgument(String)
    case unknownCall(String)
    case invalidState(String)
    case callKit(String)

    public var errorDescription: String? {
        switch self {
        case .invalidArgument(let value): return value
        case .unknownCall(let callId): return "Unknown callId: \(callId)"
        case .invalidState(let value): return "Invalid call state: \(value)"
        case .callKit(let value): return value
        }
    }
}
