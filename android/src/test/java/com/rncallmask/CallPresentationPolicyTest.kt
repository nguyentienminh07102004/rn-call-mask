package com.rncallmask

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPresentationPolicyTest {
    @Test
    fun `ringing call can own fullscreen when no answered call exists`() {
        assertTrue(CallPresentationPolicy.mayOwnFullScreen("B", listOf(session("A", CallState.RINGING), session("B", CallState.RINGING))))
    }

    @Test
    fun `ringing call cannot replace an active call fullscreen`() {
        assertFalse(CallPresentationPolicy.mayOwnFullScreen("B", listOf(session("A", CallState.ACTIVE), session("B", CallState.RINGING))))
    }

    private fun session(callId: String, state: CallState) = CallSession(
        callId = callId,
        callerId = callId,
        callerName = callId,
        handle = null,
        avatar = null,
        media = "audio",
        state = state,
        createdAt = 1L,
    )
}
