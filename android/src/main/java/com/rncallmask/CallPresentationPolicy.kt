package com.rncallmask

internal object CallPresentationPolicy {
    fun mayOwnFullScreen(callId: String, sessions: List<CallSession>): Boolean {
        val target = sessions.firstOrNull { it.callId == callId } ?: return false
        if (target.state != CallState.RINGING && target.state != CallState.INCOMING) return false

        val answeredCallExists = sessions.any { session ->
            session.callId != callId &&
                (session.state == CallState.CONNECTING ||
                    session.state == CallState.ACTIVE ||
                    session.state == CallState.HELD)
        }
        if (answeredCallExists) return false

        val newestRinging = sessions
            .asSequence()
            .filter { session ->
                session.state == CallState.RINGING || session.state == CallState.INCOMING
            }
            .maxWithOrNull(compareBy<CallSession> { it.createdAt }.thenBy { it.callId })
            ?: return false

        return newestRinging.callId == callId
    }
}
