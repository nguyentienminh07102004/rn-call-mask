package com.rncallmask

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import org.json.JSONObject

enum class CallState(val wireValue: String) {
    INCOMING("incoming"),
    RINGING("ringing"),
    CONNECTING("connecting"),
    ACTIVE("active"),
    HELD("held"),
    ENDING("ending"),
    ENDED("ended");

    fun canTransitionTo(next: CallState): Boolean {
        if (this == next) return true
        return when (this) {
            INCOMING -> next == RINGING || next == ENDED
            RINGING -> next == CONNECTING || next == ENDED
            CONNECTING -> next == ACTIVE || next == ENDED
            ACTIVE -> next == HELD || next == ENDING || next == ENDED
            HELD -> next == ACTIVE || next == ENDING || next == ENDED
            ENDING -> next == ENDED
            ENDED -> false
        }
    }

    companion object {
        fun fromWire(value: String): CallState? = entries.firstOrNull { it.wireValue == value }
    }
}

data class CallSession(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val handle: String?,
    val avatar: String?,
    val media: String,
    val state: CallState,
    val createdAt: Long,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val endReason: String? = null,
    val silenced: Boolean = false,
    val data: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("callId", callId)
        put("callerId", callerId)
        put("callerName", callerName)
        putNullable("handle", handle)
        putNullable("avatar", avatar)
        put("media", media)
        put("state", state.wireValue)
        put("createdAt", createdAt)
        putNullable("answeredAt", answeredAt)
        putNullable("endedAt", endedAt)
        putNullable("endReason", endReason)
        put("silenced", silenced)
        put("data", JSONObject(data))
    }

    fun toWritableMap(): WritableMap = Arguments.createMap().apply {
        putString("callId", callId)
        putString("media", media)
        putString("state", state.wireValue)
        putDouble("createdAt", createdAt.toDouble())
        putBoolean("silenced", silenced)
        answeredAt?.let { putDouble("answeredAt", it.toDouble()) }
        endedAt?.let { putDouble("endedAt", it.toDouble()) }
        endReason?.let { putString("endReason", it) }
        if (data.isNotEmpty()) {
            putMap("data", Arguments.createMap().apply {
                data.forEach { (key, value) -> putString(key, value) }
            })
        }

        putMap("caller", Arguments.createMap().apply {
            putString("id", callerId)
            putString("name", callerName)
            handle?.let { putString("handle", it) }
            avatar?.let { putString("avatar", it) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): CallSession? {
            val state = CallState.fromWire(json.optString("state")) ?: return null
            return CallSession(
                callId = json.optString("callId"),
                callerId = json.optString("callerId"),
                callerName = json.optString("callerName"),
                handle = json.optNullableString("handle"),
                avatar = json.optNullableString("avatar"),
                media = json.optString("media", "audio"),
                state = state,
                createdAt = json.optLong("createdAt"),
                answeredAt = json.optNullableLong("answeredAt"),
                endedAt = json.optNullableLong("endedAt"),
                endReason = json.optNullableString("endReason"),
                silenced = json.optBoolean("silenced", false),
                data = json.optJSONObject("data")?.let { objectJson ->
                    buildMap {
                        val keys = objectJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, objectJson.optString(key))
                        }
                    }
                } ?: emptyMap(),
            )
        }
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)
