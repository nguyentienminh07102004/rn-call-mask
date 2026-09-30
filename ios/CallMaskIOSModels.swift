import Foundation

public enum CallMaskIOSState: String, Codable {
    case incoming
    case ringing
    case connecting
    case active
    case held
    case ending
    case ended

    func canTransition(to next: CallMaskIOSState) -> Bool {
        if self == next { return true }
        switch self {
        case .incoming:
            return next == .ringing || next == .ended
        case .ringing:
            return next == .connecting || next == .ended
        case .connecting:
            return next == .active || next == .ended
        case .active:
            return next == .held || next == .ending || next == .ended
        case .held:
            return next == .active || next == .ending || next == .ended
        case .ending:
            return next == .ended
        case .ended:
            return false
        }
    }
}

public struct CallMaskIOSSession: Codable, Equatable {
    public let callId: String
    public let uuid: UUID
    public let callerId: String
    public let callerName: String
    public let handle: String?
    public let media: String
    public var state: CallMaskIOSState
    public let createdAt: Date
    public var answeredAt: Date?
    public var endedAt: Date?
    public var endReason: String?

    public init(
        callId: String,
        uuid: UUID = UUID(),
        callerId: String,
        callerName: String,
        handle: String?,
        media: String,
        state: CallMaskIOSState = .ringing,
        createdAt: Date = Date(),
        answeredAt: Date? = nil,
        endedAt: Date? = nil,
        endReason: String? = nil
    ) {
        self.callId = callId
        self.uuid = uuid
        self.callerId = callerId
        self.callerName = callerName
        self.handle = handle
        self.media = media
        self.state = state
        self.createdAt = createdAt
        self.answeredAt = answeredAt
        self.endedAt = endedAt
        self.endReason = endReason
    }
}

public struct CallMaskIOSEvent: Codable, Equatable {
    public let eventId: UUID
    public let callId: String
    public let type: String
    public let timestamp: Date
    public let state: String?
    public let endReason: String?

    public init(
        eventId: UUID = UUID(),
        callId: String,
        type: String,
        timestamp: Date = Date(),
        state: String? = nil,
        endReason: String? = nil
    ) {
        self.eventId = eventId
        self.callId = callId
        self.type = type
        self.timestamp = timestamp
        self.state = state
        self.endReason = endReason
    }
}
