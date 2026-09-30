package com.rncallmask.notification

import android.content.Context
import com.rncallmask.CallEventBus
import com.rncallmask.CoreTelecomCoordinator
import com.rncallmask.CallRegistry
import com.rncallmask.CallState
import com.rncallmask.NativeCallEvent

internal object CallActionHandler {
    fun handle(context: Context, action: String, callId: String) {
        val registry = CallRegistry.get(context)
        val notifications = CallNotificationManager(context)

        when (action) {
            CallActionReceiver.ACTION_ANSWER -> {
                val call = registry.transition(callId, CallState.CONNECTING) ?: return
                notifications.showOngoing(call)
                CoreTelecomCoordinator.syncAnswer(context, callId)
                CallEventBus.dispatch(
                    context,
                    NativeCallEvent(callId = callId, type = "answer", state = call.state.wireValue),
                )
            }

            CallActionReceiver.ACTION_DECLINE -> {
                val call = registry.transition(callId, CallState.ENDED, "declined") ?: return
                notifications.cancel(callId)
                CoreTelecomCoordinator.syncDecline(context, callId)
                CallEventBus.dispatch(
                    context,
                    NativeCallEvent(
                        callId = callId,
                        type = "decline",
                        state = call.state.wireValue,
                        endReason = call.endReason,
                    ),
                )
            }

            CallActionReceiver.ACTION_END -> {
                val current = registry.get(callId) ?: return
                if (current.state == CallState.ENDED) return
                val call = when (current.state) {
                    CallState.ACTIVE, CallState.HELD -> {
                        registry.transition(callId, CallState.ENDING)
                        registry.transition(callId, CallState.ENDED, "local")
                    }
                    else -> registry.transition(callId, CallState.ENDED, "local")
                } ?: return
                notifications.cancel(callId)
                CoreTelecomCoordinator.syncEnd(context, callId)
                CallEventBus.dispatch(
                    context,
                    NativeCallEvent(
                        callId = callId,
                        type = "end",
                        state = call.state.wireValue,
                        endReason = call.endReason,
                    ),
                )
            }
        }
    }
}
