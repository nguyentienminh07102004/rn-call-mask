package com.rncallmask

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPresentationPolicyTest {
    @Test
    fun `newest ringing call owns fullscreen when no answered call exists`() {
        val sessions = listOf(
            session("A", CallState.RINGING, createdAt = 1L),
            session("B", CallState.RINGING, createdAt = 2L),
        )

        assertFalse(CallPresentationPolicy.mayOwnFullScreen("A", sessions))
        assertTrue(CallPresentationPolicy.mayOwnFullScreen("B", sessions))
    }

    @Test
    fun `ringing call cannot replace an answered call fullscreen`() {
        val sessions = listOf(
            session("A", CallState.ACTIVE, createdAt = 1L),
            session("B", CallState.RINGING, createdAt = 2L),
        )

        assertFalse(CallPresentationPolicy.mayOwnFullScreen("B", sessions))
    }

    @Test
    fun `non ringing target cannot own fullscreen`() {
        val sessions = listOf(session("A", CallState.ACTIVE, createdAt = 1L))

        assertFalse(CallPresentationPolicy.mayOwnFullScreen("A", sessions))
    }

    @Test
    fun `unknown target cannot own fullscreen`() {
        val sessions = listOf(session("A", CallState.RINGING, createdAt = 1L))

        assertFalse(CallPresentationPolicy.mayOwnFullScreen("B", sessions))
    }

    @Test
    fun `timestamp ties are deterministic by call id`() {
        val sessions = listOf(
            session("A", CallState.RINGING, createdAt = 10L),
            session("B", CallState.RINGING, createdAt = 10L),
        )

        assertFalse(CallPresentationPolicy.mayOwnFullScreen("A", sessions))
        assertTrue(CallPresentationPolicy.mayOwnFullScreen("B", sessions))
    }

    private fun session(
        callId: String,
        state: CallState,
        createdAt: Long,
    ) = CallSession(
        callId = callId,
        callerId = callId,
        callerName = callId,
        handle = null,
        avatar = null,
        media = "audio",
        state = state,
        createdAt = createdAt,
    )
}
