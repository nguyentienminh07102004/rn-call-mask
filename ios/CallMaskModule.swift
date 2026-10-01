import Foundation
import React

@objc(RNCallMask)
public final class CallMaskModule: RCTEventEmitter {
    private let callKit = CallMaskCallKitManager.shared
    private let registry = CallMaskIOSRegistry.shared
    private let pendingEvents = CallMaskIOSEventStore.shared
    private let eventBus = CallMaskIOSEventBus.shared

    public override static func requiresMainQueueSetup() -> Bool {
        true
    }

    public override func supportedEvents() -> [String]! {
        ["RNCallMaskEvent"]
    }

    public override func startObserving() {
        super.startObserving()
        eventBus.setListener { [weak self] event in
            self?.sendEvent(withName: "RNCallMaskEvent", body: event.dictionary)
        }
    }

    public override func stopObserving() {
        eventBus.setListener(nil)
        super.stopObserving()
    }

    @objc(showIncomingCall:resolver:rejecter:)
    public func showIncomingCall(
        _ input: NSDictionary,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard
            let callId = normalizedString(input["callId"]),
            let caller = input["caller"] as? NSDictionary,
            let callerName = normalizedString(caller["name"])
        else {
            reject("E_INVALID_ARGUMENT", "callId and caller.name are required", nil)
            return
        }

        let media = normalizedString(input["media"]) ?? "audio"
        let callerId = normalizedString(caller["id"]) ?? callId
        let handle = normalizedString(caller["handle"])

        callKit.reportIncomingCall(
            callId: callId,
            callerId: callerId,
            callerName: callerName,
            handle: handle,
            media: media
        ) { result in
            DispatchQueue.main.async {
                switch result {
                case .success(let session):
                    resolve(session.dictionary)
                case .failure(let error):
                    reject("E_PRESENTATION_FAILED", error.localizedDescription, error)
                }
            }
        }
    }

    @objc(answer:resolver:rejecter:)
    public func answer(
        _ callId: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        callKit.requestAnswer(callId: callId) { error in
            DispatchQueue.main.async {
                if let error {
                    reject("E_INVALID_STATE", error.localizedDescription, error)
                } else {
                    resolve(nil)
                }
            }
        }
    }

    @objc(decline:resolver:rejecter:)
    public func decline(
        _ callId: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        callKit.requestDecline(callId: callId) { error in
            DispatchQueue.main.async {
                if let error {
                    reject("E_INVALID_STATE", error.localizedDescription, error)
                } else {
                    resolve(nil)
                }
            }
        }
    }

    @objc(end:reason:resolver:rejecter:)
    public func end(
        _ callId: String,
        reason: String?,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let normalizedReason = normalizedString(reason) ?? "local"
        if normalizedReason == "local" {
            callKit.requestLocalEnd(callId: callId) { error in
                DispatchQueue.main.async {
                    if let error {
                        reject("E_INVALID_STATE", error.localizedDescription, error)
                    } else {
                        resolve(nil)
                    }
                }
            }
            return
        }

        do {
            try callKit.end(callId: callId, reason: normalizedReason)
            resolve(nil)
        } catch {
            reject("E_INVALID_STATE", error.localizedDescription, error)
        }
    }

    @objc(markActive:resolver:rejecter:)
    public func markActive(
        _ callId: String,
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        do {
            try callKit.markActive(callId: callId)
            resolve(nil)
        } catch {
            reject("E_INVALID_STATE", error.localizedDescription, error)
        }
    }

    @objc(silence:resolver:rejecter:)
    public func silence(
        _ callId: String,
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        guard registry.session(callId: callId) != nil else {
            reject("E_UNKNOWN_CALL", "Unknown callId: " + callId, nil)
            return
        }
        reject(
            "E_PRESENTATION_FAILED",
            "CallKit owns incoming-call ringtone and cannot be silenced independently.",
            nil
        )
    }

    @objc(dismissIncomingUI:resolver:rejecter:)
    public func dismissIncomingUI(
        _ callId: String,
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        guard registry.session(callId: callId) != nil else {
            reject("E_UNKNOWN_CALL", "Unknown callId: " + callId, nil)
            return
        }
        reject(
            "E_PRESENTATION_FAILED",
            "CallKit system incoming-call UI cannot be arbitrarily dismissed without ending the call.",
            nil
        )
    }

    @objc(getCalls:rejecter:)
    public func getCalls(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        resolve(registry.allSessions().map(\.dictionary))
    }

    @objc(consumePendingEvents:rejecter:)
    public func consumePendingEvents(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        resolve(pendingEvents.consume().map(\.dictionary))
    }

    @objc(getCapabilities:rejecter:)
    public func getCapabilities(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        resolve([
            "notificationsEnabled": true,
            "canUseFullScreen": false
        ])
    }

    @objc(openFullScreenSettings:rejecter:)
    public func openFullScreenSettings(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
    ) {
        resolve(false)
    }

    private func normalizedString(_ value: Any?) -> String? {
        guard let value = value as? String else { return nil }
        let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return normalized.isEmpty ? nil : normalized
    }
}

private extension CallMaskIOSSession {
    var dictionary: [String: Any] {
        var caller: [String: Any] = [
            "id": callerId,
            "name": callerName
        ]
        if let handle {
            caller["handle"] = handle
        }

        var result: [String: Any] = [
            "callId": callId,
            "media": media,
            "state": state.rawValue,
            "createdAt": createdAt.timeIntervalSince1970 * 1000,
            "silenced": false,
            "caller": caller
        ]
        if let answeredAt {
            result["answeredAt"] = answeredAt.timeIntervalSince1970 * 1000
        }
        if let endedAt {
            result["endedAt"] = endedAt.timeIntervalSince1970 * 1000
        }
        if let endReason {
            result["endReason"] = endReason
        }
        return result
    }
}

private extension CallMaskIOSEvent {
    var dictionary: [String: Any] {
        var result: [String: Any] = [
            "eventId": eventId.uuidString,
            "callId": callId,
            "type": type,
            "timestamp": timestamp.timeIntervalSince1970 * 1000
        ]
        if let state {
            result["state"] = state
        }
        if let endReason {
            result["endReason"] = endReason
        }
        return result
    }
}
