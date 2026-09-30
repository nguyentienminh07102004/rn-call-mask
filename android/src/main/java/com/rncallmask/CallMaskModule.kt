package com.rncallmask

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.rncallmask.notification.CallActionHandler
import com.rncallmask.notification.CallActionReceiver
import com.rncallmask.notification.CallNotificationManager

class CallMaskModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    private val registry = CallRegistry.get(reactContext)
    private val notifications = CallNotificationManager(reactContext)

    init {
        CallEventBus.attach(reactContext)
    }

    override fun getName(): String = NAME

    override fun invalidate() {
        CallEventBus.detach(reactContext)
        super.invalidate()
    }

    @ReactMethod
    fun showIncomingCall(call: ReadableMap, promise: Promise) {
        runCatching {
            val callId = call.requiredString("callId")
            val caller = call.getMap("caller") ?: throw IllegalArgumentException("caller is required")
            val callerName = caller.requiredString("name")
            val createdAt = if (call.hasKey("createdAt") && !call.isNull("createdAt")) {
                call.getDouble("createdAt").toLong()
            } else {
                System.currentTimeMillis()
            }
            val session = CallMaskNative.showIncomingCall(
                context = reactContext,
                callId = callId,
                callerName = callerName,
                callerId = caller.optionalString("id") ?: callId,
                media = call.optionalString("media") ?: "audio",
                handle = caller.optionalString("handle"),
                avatar = caller.optionalString("avatar"),
                createdAt = createdAt,
                data = call.optionalStringMap("data"),
            )
            promise.resolve(session.toWritableMap())
        }.onFailure { error ->
            promise.reject("E_INVALID_ARGUMENT", error.message ?: "Invalid incoming call payload", error)
        }
    }

    @ReactMethod
    fun answer(callId: String, promise: Promise) {
        if (!ensureKnown(callId, promise)) return
        CallActionHandler.handle(reactContext, CallActionReceiver.ACTION_ANSWER, callId)
        promise.resolve(null)
    }

    @ReactMethod
    fun decline(callId: String, promise: Promise) {
        if (!ensureKnown(callId, promise)) return
        CallActionHandler.handle(reactContext, CallActionReceiver.ACTION_DECLINE, callId)
        promise.resolve(null)
    }

    @ReactMethod
    fun end(callId: String, reason: String?, promise: Promise) {
        val current = registry.get(callId)
        if (current == null) {
            promise.reject("E_UNKNOWN_CALL", "Unknown callId: $callId")
            return
        }
        if (current.state == CallState.ENDED) {
            promise.resolve(null)
            return
        }
        val ended = CallMaskNative.end(reactContext, callId, reason ?: "local")
        if (ended == null) {
            promise.reject("E_INVALID_STATE", "Cannot end call $callId from ${current.state.wireValue}")
            return
        }
        promise.resolve(null)
    }

    @ReactMethod
    fun markActive(callId: String, promise: Promise) {
        val current = registry.get(callId)
        if (current == null) {
            promise.reject("E_UNKNOWN_CALL", "Unknown callId: $callId")
            return
        }
        if (current.state == CallState.ACTIVE) {
            notifications.showOngoing(current)
            promise.resolve(null)
            return
        }
        val active = registry.transition(callId, CallState.ACTIVE)
        if (active == null) {
            promise.reject("E_INVALID_STATE", "Cannot mark call $callId active from ${current.state.wireValue}")
            return
        }
        notifications.showOngoing(active)
        CoreTelecomCoordinator.syncSetActive(reactContext, callId)
        CallEventBus.dispatch(
            reactContext,
            NativeCallEvent(callId = callId, type = "stateChanged", state = active.state.wireValue),
        )
        promise.resolve(null)
    }

    @ReactMethod
    fun silence(callId: String, promise: Promise) {
        val current = registry.get(callId)
        if (current == null) {
            promise.reject("E_UNKNOWN_CALL", "Unknown callId: $callId")
            return
        }
        if (current.state != CallState.RINGING && current.state != CallState.INCOMING) {
            promise.reject("E_INVALID_STATE", "Only ringing calls can be silenced")
            return
        }
        val updated = registry.setSilenced(callId, true) ?: current
        notifications.silence(updated)
        CallEventBus.dispatch(
            reactContext,
            NativeCallEvent(callId = callId, type = "silenced", state = updated.state.wireValue),
        )
        promise.resolve(null)
    }

    @ReactMethod
    fun dismissIncomingUI(callId: String, promise: Promise) {
        if (!ensureKnown(callId, promise)) return
        notifications.cancel(callId)
        val state = registry.get(callId)?.state?.wireValue
        CallEventBus.dispatch(
            reactContext,
            NativeCallEvent(callId = callId, type = "presentationChanged", state = state),
        )
        promise.resolve(null)
    }

    @ReactMethod
    fun getCalls(promise: Promise) {
        val result = Arguments.createArray()
        registry.all().forEach { result.pushMap(it.toWritableMap()) }
        promise.resolve(result)
    }

    @ReactMethod
    fun consumePendingEvents(promise: Promise) {
        promise.resolve(CallEventStore(reactContext).consume())
    }

    @ReactMethod
    fun getCapabilities(promise: Promise) {
        val result = Arguments.createMap().apply {
            putBoolean("notificationsEnabled", notifications.notificationsEnabled())
            putBoolean("canUseFullScreen", notifications.canUseFullScreen())
            putInt("androidApiLevel", Build.VERSION.SDK_INT)
        }
        promise.resolve(result)
    }

    @ReactMethod
    fun openFullScreenSettings(promise: Promise) {
        if (Build.VERSION.SDK_INT < 34) {
            promise.resolve(false)
            return
        }

        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${reactContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(intent)
            true
        }.onSuccess(promise::resolve)
            .onFailure { promise.reject("E_PRESENTATION_FAILED", "Unable to open full-screen intent settings", it) }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        if (eventName == CallEventBus.EVENT_NAME) CallEventBus.addListener()
    }

    @ReactMethod
    fun removeListeners(count: Double) {
        CallEventBus.removeListeners(count.toInt())
    }

    private fun ensureKnown(callId: String, promise: Promise): Boolean {
        if (callId.isBlank()) {
            promise.reject("E_INVALID_ARGUMENT", "callId must be non-empty")
            return false
        }
        if (registry.get(callId) == null) {
            promise.reject("E_UNKNOWN_CALL", "Unknown callId: $callId")
            return false
        }
        return true
    }

    companion object {
        const val NAME = "RNCallMask"
    }
}

private fun ReadableMap.requiredString(key: String): String =
    optionalString(key)?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("$key must be a non-empty string")

private fun ReadableMap.optionalString(key: String): String? =
    if (!hasKey(key) || isNull(key)) null else getString(key)

private fun ReadableMap.optionalStringMap(key: String): Map<String, String> {
    if (!hasKey(key) || isNull(key)) return emptyMap()
    val map = getMap(key) ?: return emptyMap()
    return map.toHashMap().mapNotNull { (entryKey, value) ->
        (value as? String)?.let { entryKey to it }
    }.toMap()
}
