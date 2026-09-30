package com.rncallmask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID)?.takeIf { it.isNotBlank() }
            ?: return
        val action = intent.action ?: return
        CallActionHandler.handle(context, action, callId)
    }

    companion object {
        const val ACTION_ANSWER = "com.rncallmask.action.ANSWER"
        const val ACTION_DECLINE = "com.rncallmask.action.DECLINE"
        const val ACTION_END = "com.rncallmask.action.END"
    }
}
