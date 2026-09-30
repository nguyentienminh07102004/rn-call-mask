package com.rncallmask.notification

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rncallmask.CallRegistry
import com.rncallmask.CallState

class IncomingCallActivity : Activity() {
    private var callId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        renderIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderIntent(intent)
    }

    private fun renderIntent(intent: Intent) {
        val nextCallId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID)
            ?.takeIf { it.isNotBlank() }
            ?: run {
                finish()
                return
            }
        val call = CallRegistry.get(this).get(nextCallId)
        if (call == null || (call.state != CallState.RINGING && call.state != CallState.INCOMING)) {
            finish()
            return
        }
        callId = nextCallId

        val density = resources.displayMetrics.density
        val padding = (32 * density).toInt()
        val spacing = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val title = TextView(this).apply {
            text = call.callerName
            textSize = 28f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Incoming ${call.media} call"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val decline = Button(this).apply {
            text = "Decline"
            setOnClickListener { performAction(CallActionReceiver.ACTION_DECLINE) }
        }
        val answer = Button(this).apply {
            text = "Answer"
            setOnClickListener { performAction(CallActionReceiver.ACTION_ANSWER) }
        }

        root.addView(title)
        root.addView(subtitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = spacing })
        actions.addView(decline)
        actions.addView(answer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = spacing })
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = spacing * 2 })

        setContentView(root)
    }

    private fun performAction(action: String) {
        val id = callId ?: return
        CallActionHandler.handle(this, action, id)
        finish()
    }
}
