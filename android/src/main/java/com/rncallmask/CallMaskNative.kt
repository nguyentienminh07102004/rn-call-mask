package com.rncallmask

import android.content.Context
import com.rncallmask.notification.CallNotificationManager

/**
 * Native host entry point for push/services that must not depend on the React Native runtime.
 * The host app still owns signaling and media.
 */
object CallMaskNative {
    @JvmStatic
    fun showIncomingCall(
        context: Context,
        callId: String,
        callerName: String,
        callerId: String = callId,
        media: String = "audio",
        handle: String? = null,
        avatar: String? = null,
        createdAt: Long = System.currentTimeMillis(),
        data: Map<String, String> = emptyMap(),
    ): CallSession {
        require(callId.isNotBlank()) { "callId must be non-empty" }
        require(callerName.isNotBlank()) { "callerName must be non-empty" }
        require(media == "audio" || media == "video") { "media must be audio or video" }

        val registry = CallRegistry.get(context)
        val existing = registry.get(callId)
        if (existing != null) {
            if (existing.state == CallState.RINGING || existing.state == CallState.INCOMING) {
                CallNotificationManager(context).showIncoming(existing)
            }
            return existing
        }

        val session = registry.registerIncoming(
            CallSession(
                callId = callId,
                callerId = callerId.ifBlank { callId },
                callerName = callerName.trim(),
                handle = handle,
                avatar = avatar,
                media = media,
                state = CallState.RINGING,
                createdAt = createdAt,
                data = data,
            ),
        )
        CallNotificationManager(context).showIncoming(session)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(callId = callId, type = "incoming", state = session.state.wireValue),
        )
        return session
    }

    @JvmStatic
    fun end(context: Context, callId: String, reason: String = "remote"): CallSession? {
        val registry = CallRegistry.get(context)
        val current = registry.get(callId) ?: return null
        if (current.state == CallState.ENDED) return current

        if (current.state == CallState.ACTIVE || current.state == CallState.HELD) {
            registry.transition(callId, CallState.ENDING)
        }
        val ended = registry.transition(callId, CallState.ENDED, reason) ?: return null
        CallNotificationManager(context).cancel(callId)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(
                callId = callId,
                type = "end",
                state = ended.state.wireValue,
                endReason = ended.endReason,
            ),
        )
        return ended
    }
}
