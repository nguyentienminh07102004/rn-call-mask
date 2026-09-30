package com.rncallmask

internal object CallPresentationPolicy {
    fun mayOwnFullScreen(callId: String, sessions: List<CallSession>): Boolean =
        sessions.none { session ->
            session.callId != callId &&
                (session.state == CallState.CONNECTING ||
                    session.state == CallState.ACTIVE ||
                    session.state == CallState.HELD)
        }
}
