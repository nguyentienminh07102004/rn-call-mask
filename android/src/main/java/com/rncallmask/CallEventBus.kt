package com.rncallmask

import android.content.Context
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

internal object CallEventBus {
    private var reactContextRef: WeakReference<ReactApplicationContext>? = null
    private val listenerCount = AtomicInteger(0)

    fun attach(context: ReactApplicationContext) {
        reactContextRef = WeakReference(context)
    }

    fun detach(context: ReactApplicationContext) {
        if (reactContextRef?.get() === context) reactContextRef = null
        listenerCount.set(0)
    }

    fun addListener() {
        listenerCount.incrementAndGet()
    }

    fun removeListeners(count: Int) {
        listenerCount.updateAndGet { current -> (current - count).coerceAtLeast(0) }
    }

    fun dispatch(context: Context, event: NativeCallEvent) {
        val reactContext = reactContextRef?.get()
        if (reactContext != null && listenerCount.get() > 0) {
            val delivered = runCatching {
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(EVENT_NAME, event.toWritableMap())
            }.isSuccess
            if (delivered) return
        }

        CallEventStore(context).append(event)
    }

    const val EVENT_NAME = "RNCallMaskEvent"
}
