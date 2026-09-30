package com.rncallmask

import android.content.Context
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

class CallRegistry private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val calls = ConcurrentHashMap<String, CallSession>()

    init {
        restore()
    }

    @Synchronized
    fun registerIncoming(session: CallSession): CallSession {
        val existing = calls[session.callId]
        if (existing != null && existing.state != CallState.ENDED) {
            return existing
        }

        calls[session.callId] = session
        persist()
        return session
    }

    fun get(callId: String): CallSession? = calls[callId]

    fun all(): List<CallSession> = calls.values.sortedBy { it.createdAt }

    @Synchronized
    fun transition(
        callId: String,
        nextState: CallState,
        endReason: String? = null,
    ): CallSession? {
        val current = calls[callId] ?: return null
        if (current.state == nextState) return null
        if (!current.state.canTransitionTo(nextState)) return null

        val now = System.currentTimeMillis()
        val updated = current.copy(
            state = nextState,
            answeredAt = if (nextState == CallState.CONNECTING && current.answeredAt == null) now else current.answeredAt,
            endedAt = if (nextState == CallState.ENDED) now else current.endedAt,
            endReason = if (nextState == CallState.ENDED) endReason ?: current.endReason else current.endReason,
        )
        calls[callId] = updated
        persist()
        return updated
    }

    @Synchronized
    fun setSilenced(callId: String, silenced: Boolean): CallSession? {
        val current = calls[callId] ?: return null
        if (current.silenced == silenced) return current
        val updated = current.copy(silenced = silenced)
        calls[callId] = updated
        persist()
        return updated
    }

    @Synchronized
    fun removeEnded(callId: String) {
        if (calls[callId]?.state == CallState.ENDED) {
            calls.remove(callId)
            persist()
        }
    }

    private fun restore() {
        val raw = preferences.getString(KEY_CALLS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                CallSession.fromJson(array.getJSONObject(index))?.let { session ->
                    if (session.callId.isNotBlank()) calls[session.callId] = session
                }
            }
        }
    }

    private fun persist() {
        val array = JSONArray()
        calls.values.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_CALLS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "rn_call_mask_registry"
        private const val KEY_CALLS = "calls"

        @Volatile
        private var instance: CallRegistry? = null

        fun get(context: Context): CallRegistry = instance ?: synchronized(this) {
            instance ?: CallRegistry(context).also { instance = it }
        }
    }
}
