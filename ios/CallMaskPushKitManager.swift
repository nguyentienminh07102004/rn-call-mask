import Foundation
import PushKit

public final class CallMaskPushKitManager: NSObject, PKPushRegistryDelegate {
    public typealias TokenHandler = (String) -> Void
    public typealias IncomingPayloadHandler = ([AnyHashable: Any]) -> Void

    private let queue: DispatchQueue
    private let callKit: CallMaskCallKitManager
    private var registry: PKPushRegistry?

    public var onTokenUpdated: TokenHandler?
    public var onTokenInvalidated: (() -> Void)?
    public var onIncomingPayload: IncomingPayloadHandler?

    public init(
        queue: DispatchQueue = .main,
        callKit: CallMaskCallKitManager = .shared
    ) {
        self.queue = queue
        self.callKit = callKit
        super.init()
    }

    public func start() {
        let registry = PKPushRegistry(queue: queue)
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
        self.registry = registry
    }

    public func pushRegistry(
        _ registry: PKPushRegistry,
        didUpdate pushCredentials: PKPushCredentials,
        for type: PKPushType
    ) {
        guard type == .voIP else { return }
        let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        onTokenUpdated?(token)
    }

    public func pushRegistry(
        _ registry: PKPushRegistry,
        didInvalidatePushTokenFor type: PKPushType
    ) {
        guard type == .voIP else { return }
        onTokenInvalidated?()
    }

    public func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP else {
            completion()
            return
        }

        let dictionary = payload.dictionaryPayload
        onIncomingPayload?(dictionary)

        guard
            let callId = stringValue(dictionary["callId"]),
            let callerName = stringValue(dictionary["callerName"])
        else {
            completion()
            return
        }

        let callerId = stringValue(dictionary["callerId"]) ?? callId
        let handle = stringValue(dictionary["handle"])
        let media = stringValue(dictionary["media"]) ?? "audio"

        callKit.reportIncomingCall(
            callId: callId,
            callerId: callerId,
            callerName: callerName,
            handle: handle,
            media: media
        ) { _ in
            completion()
        }
    }

    private func stringValue(_ value: Any?) -> String? {
        guard let raw = value as? String else { return nil }
        let normalized = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return normalized.isEmpty ? nil : normalized
    }
}
