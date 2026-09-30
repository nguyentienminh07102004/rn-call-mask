package com.rncallmask.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.rncallmask.CallSession
import com.rncallmask.CallState

internal class CallNotificationManager(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun showIncoming(call: CallSession) {
        createChannel()
        post(call.callId, incomingBuilder(call).build())
    }

    fun showOngoing(call: CallSession) {
        createChannel()
        val person = person(call)
        val hangup = actionPendingIntent(call, CallActionReceiver.ACTION_END)
        val builder = baseBuilder(call)
            .setContentText(if (call.state == CallState.CONNECTING) "Connecting…" else "Call in progress")
            .setOngoing(true)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangup))
            .setSilent(true)
            .setOnlyAlertOnce(true)

        post(call.callId, builder.build())
    }

    fun silence(call: CallSession) {
        if (call.state != CallState.RINGING && call.state != CallState.INCOMING) return
        post(call.callId, incomingBuilder(call.copy(silenced = true)).build())
    }

    fun cancel(callId: String) {
        notificationManager.cancel(tag(callId), NOTIFICATION_ID)
    }

    fun notificationsEnabled(): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    private fun post(callId: String, notification: Notification) {
        if (!notificationsEnabled()) return
        notificationManager.notify(tag(callId), NOTIFICATION_ID, notification)
    }

    fun canUseFullScreen(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.canUseFullScreenIntent()
    }

    private fun incomingBuilder(call: CallSession): NotificationCompat.Builder {
        val decline = actionPendingIntent(call, CallActionReceiver.ACTION_DECLINE)
        val answer = actionPendingIntent(call, CallActionReceiver.ACTION_ANSWER)
        val fullscreen = fullScreenPendingIntent(call)

        return baseBuilder(call)
            .setContentText("Incoming ${call.media} call")
            .setOngoing(true)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person(call), decline, answer))
            .setSilent(call.silenced)
            .apply {
                if (canUseFullScreen()) setFullScreenIntent(fullscreen, true)
            }
    }

    private fun baseBuilder(call: CallSession): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(call.callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent(call))

    private fun person(call: CallSession): Person =
        Person.Builder()
            .setName(call.callerName)
            .setKey(call.callerId.ifBlank { call.callId })
            .setImportant(true)
            .build()

    private fun actionPendingIntent(call: CallSession, action: String): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("rncallmask://call/${Uri.encode(call.callId)}/${Uri.encode(action)}")
            putExtra(EXTRA_CALL_ID, call.callId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(call.callId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun fullScreenPendingIntent(call: CallSession): PendingIntent {
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("rncallmask://call/${Uri.encode(call.callId)}/fullscreen")
            putExtra(EXTRA_CALL_ID, call.callId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(call.callId, "fullscreen"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming and ongoing VoIP calls"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_CALL_ID = "rn_call_mask_call_id"
        private const val CHANNEL_ID = "rn_call_mask_incoming_calls"
        private const val NOTIFICATION_ID = 7301

        private fun tag(callId: String) = "rn-call-mask:$callId"
        private fun requestCode(callId: String, action: String) = ("$callId:$action").hashCode()
    }
}
