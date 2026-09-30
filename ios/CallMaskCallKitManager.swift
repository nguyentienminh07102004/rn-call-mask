import AVFAudio
import CallKit
import Foundation

public final class CallMaskCallKitManager: NSObject, CXProviderDelegate {
    public static let shared = CallMaskCallKitManager()

    private let registry: CallMaskIOSRegistry
    private let events: CallMaskIOSEventStore
    private let provider: CXProvider

    public init(
        registry: CallMaskIOSRegistry = .shared,
        events: CallMaskIOSEventStore = .shared,
        localizedName: String = "Call"
    ) {
        self.registry = registry
        self.events = events

        let configuration = CXProviderConfiguration(localizedName: localizedName)
        configuration.supportsVideo = true
        configuration.maximumCallGroups = 2
        configuration.maximumCallsPerCallGroup = 1
        configuration.supportedHandleTypes = [.generic, .phoneNumber]

        self.provider = CXProvider(configuration: configuration)
        super.init()
        self.provider.setDelegate(self, queue: nil)
    }

    public func reportIncomingCall(
        callId: String,
        callerId: String? = nil,
        callerName: String,
        handle: String? = nil,
        media: String = "audio",
        createdAt: Date = Date(),
        completion: @escaping (Result<CallMaskIOSSession, Error>) -> Void
    ) {
        do {
            let session = try registry.registerIncoming(
                callId: callId,
                callerId: callerId ?? callId,
                callerName: callerName,
                handle: handle,
                media: media,
                createdAt: createdAt
            )

            let update = CXCallUpdate()
            let handleValue = handle?.isEmpty == false ? handle! : session.callerId
            update.remoteHandle = CXHandle(type: .generic, value: handleValue)
            update.localizedCallerName = session.callerName
            update.hasVideo = session.media == "video"

            provider.reportNewIncomingCall(with: session.uuid, update: update) { [weak self] error in
                if let error {
                    completion(.failure(CallMaskIOSError.callKit(error.localizedDescription)))
                    return
                }

                self?.events.append(
                    CallMaskIOSEvent(
                        callId: session.callId,
                        type: "incoming",
                        state: session.state.rawValue
                    )
                )
                completion(.success(session))
            }
        } catch {
            completion(.failure(error))
        }
    }

    public func markActive(callId: String) throws {
        let current = registry.session(callId: callId)
        guard let current else { throw CallMaskIOSError.unknownCall(callId) }
        if current.state == .active { return }
        _ = try registry.transition(callId: callId, to: .active)
    }

    public func end(callId: String, reason: String = "remote") throws {
        guard let current = registry.session(callId: callId) else {
            throw CallMaskIOSError.unknownCall(callId)
        }
        if current.state == .ended { return }

        if current.state == .active || current.state == .held {
            _ = try registry.transition(callId: callId, to: .ending)
        }
        let ended = try registry.transition(callId: callId, to: .ended, endReason: reason)
        provider.reportCall(
            with: ended.uuid,
            endedAt: ended.endedAt,
            reason: callKitEndReason(reason)
        )
        events.append(
            CallMaskIOSEvent(
                callId: ended.callId,
                type: "end",
                state: ended.state.rawValue,
                endReason: ended.endReason
            )
        )
    }

    public func providerDidReset(_ provider: CXProvider) {
        for session in registry.allSessions() where session.state != .ended {
            try? end(callId: session.callId, reason: "failed")
        }
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        guard let session = registry.session(uuid: action.callUUID) else {
            action.fail()
            return
        }

        do {
            let connecting = try registry.transition(callId: session.callId, to: .connecting)
            events.append(
                CallMaskIOSEvent(
                    callId: connecting.callId,
                    type: "answer",
                    state: connecting.state.rawValue
                )
            )
            action.fulfill()
        } catch {
            action.fail()
        }
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        guard let session = registry.session(uuid: action.callUUID) else {
            action.fail()
            return
        }

        if session.state == .ended {
            action.fulfill()
            return
        }

        let wasRinging = session.state == .ringing || session.state == .incoming
        let reason = wasRinging ? "declined" : "local"
        let eventType = wasRinging ? "decline" : "end"

        do {
            if session.state == .active || session.state == .held {
                _ = try registry.transition(callId: session.callId, to: .ending)
            }
            let ended = try registry.transition(
                callId: session.callId,
                to: .ended,
                endReason: reason
            )
            events.append(
                CallMaskIOSEvent(
                    callId: ended.callId,
                    type: eventType,
                    state: ended.state.rawValue,
                    endReason: ended.endReason
                )
            )
            action.fulfill()
        } catch {
            action.fail()
        }
    }

    public func provider(
        _ provider: CXProvider,
        didActivate audioSession: AVAudioSession
    ) {
        // The host media layer can begin/continue media after receiving the answer event.
    }

    public func provider(
        _ provider: CXProvider,
        didDeactivate audioSession: AVAudioSession
    ) {
        // The host media layer owns WebRTC/SIP audio lifecycle.
    }

    private func callKitEndReason(_ reason: String) -> CXCallEndedReason {
        switch reason {
        case "remote", "cancelled":
            return .remoteEnded
        case "missed":
            return .unanswered
        case "declined":
            return .declinedElsewhere
        case "failed":
            return .failed
        default:
            return .remoteEnded
        }
    }
}
