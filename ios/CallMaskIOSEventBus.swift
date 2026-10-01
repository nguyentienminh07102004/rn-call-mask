import Foundation

public final class CallMaskIOSEventBus {
    public static let shared = CallMaskIOSEventBus()

    private let lock = NSLock()
    private let store: CallMaskIOSEventStore
    private var listener: ((CallMaskIOSEvent) -> Void)?

    public init(store: CallMaskIOSEventStore = .shared) {
        self.store = store
    }

    public func setListener(_ listener: ((CallMaskIOSEvent) -> Void)?) {
        lock.lock()
        self.listener = listener
        lock.unlock()
    }

    public func dispatch(_ event: CallMaskIOSEvent) {
        lock.lock()
        let currentListener = listener
        lock.unlock()

        guard let currentListener else {
            store.append(event)
            return
        }

        if Thread.isMainThread {
            currentListener(event)
        } else {
            DispatchQueue.main.async {
                currentListener(event)
            }
        }
    }
}
