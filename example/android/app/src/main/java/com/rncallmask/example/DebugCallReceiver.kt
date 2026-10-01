package com.rncallmask.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rncallmask.CallMaskNative

class DebugCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("operation") ?: "show") {
            "show" -> {
                val callId = intent.getStringExtra("callId") ?: "native-" + System.currentTimeMillis()
                val callerName = intent.getStringExtra("callerName") ?: "Native caller"
                val media = intent.getStringExtra("media") ?: "audio"
                CallMaskNative.showIncomingCall(
                    context = context,
                    callId = callId,
                    callerName = callerName,
                    media = media,
                    data = mapOf("source" to "adb-native"),
                )
            }
            "end" -> {
                val callId = intent.getStringExtra("callId") ?: return
                CallMaskNative.end(context, callId, intent.getStringExtra("reason") ?: "remote")
            }
        }
    }
}
