package com.rncallmask

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class NativeCallEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val callId: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val state: String? = null,
    val endReason: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("eventId", eventId)
        put("callId", callId)
        put("type", type)
        put("timestamp", timestamp)
        state?.let { put("state", it) }
        endReason?.let { put("endReason", it) }
    }

    fun toWritableMap(): WritableMap = Arguments.createMap().apply {
        putString("eventId", eventId)
        putString("callId", callId)
        putString("type", type)
        putDouble("timestamp", timestamp.toDouble())
        state?.let { putString("state", it) }
        endReason?.let { putString("endReason", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): NativeCallEvent? {
            val eventId = json.optString("eventId")
            val callId = json.optString("callId")
            val type = json.optString("type")
            if (eventId.isBlank() || callId.isBlank() || type.isBlank()) return null
            return NativeCallEvent(
                eventId = eventId,
                callId = callId,
                type = type,
                timestamp = json.optLong("timestamp"),
                state = json.optString("state").takeIf { it.isNotBlank() },
                endReason = json.optString("endReason").takeIf { it.isNotBlank() },
            )
        }
    }
}

internal class CallEventStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun append(event: NativeCallEvent) {
        val events = read().toMutableList()
        events.add(event)
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val trimmed = events.filter { it.timestamp >= cutoff }.takeLast(MAX_EVENTS)
        write(trimmed)
    }

    @Synchronized
    fun consume(): WritableArray {
        val events = read()
        preferences.edit().remove(KEY_EVENTS).commit()
        return Arguments.createArray().apply {
            events.forEach { pushMap(it.toWritableMap()) }
        }
    }

    private fun read(): List<NativeCallEvent> {
        val raw = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    NativeCallEvent.fromJson(array.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(events: List<NativeCallEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "rn_call_mask_events"
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 100
        private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
