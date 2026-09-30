package com.rncallmask

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import com.rncallmask.notification.CallNotificationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Mirrors app-owned calls into Android Core-Telecom.
 *
 * CallRegistry remains authoritative. Telecom is an integration surface for system call
 * arbitration and remote controls such as Android Auto, watches and Bluetooth devices.
 */
internal object CoreTelecomCoordinator {
    private enum class PendingCommand {
        ANSWER,
        DECLINE,
        END,
        SET_ACTIVE,
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val setupGate = Semaphore(1)
    private val controls = ConcurrentHashMap<String, CallControlScope>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val pendingCommands =
        ConcurrentHashMap<String, ConcurrentLinkedQueue<PendingCommand>>()
    private val registered = AtomicBoolean(false)

    fun addIncoming(context: Context, session: CallSession) {
        val applicationContext = context.applicationContext
        val existing = jobs[session.callId]
        if (existing?.isActive == true) return

        val job = appScope.launch(start = CoroutineStart.LAZY) {
            addIncomingInternal(applicationContext, session)
        }

        val previous = jobs.putIfAbsent(session.callId, job)
        if (previous == null || !previous.isActive) {
            if (previous != null) jobs[session.callId] = job
            job.start()
        }
    }

    fun syncAnswer(context: Context, callId: String) {
        enqueueOrRun(context, callId, PendingCommand.ANSWER)
    }

    fun syncDecline(context: Context, callId: String) {
        enqueueOrRun(context, callId, PendingCommand.DECLINE)
    }

    fun syncEnd(context: Context, callId: String) {
        enqueueOrRun(context, callId, PendingCommand.END)
    }

    fun syncSetActive(context: Context, callId: String) {
        enqueueOrRun(context, callId, PendingCommand.SET_ACTIVE)
    }

    @SuppressLint("MissingPermission")
    private suspend fun addIncomingInternal(context: Context, session: CallSession) {
        setupGate.acquire()
        var setupReleased = false

        fun releaseSetupGate() {
            if (!setupReleased) {
                setupReleased = true
                setupGate.release()
            }
        }

        try {
            val callsManager = CallsManager(context)
            ensureRegistered(callsManager)

            callsManager.addCall(
                callAttributes(session),
                onAnswer = {
                    handleTelecomAnswer(context, session.callId)
                },
                onDisconnect = { cause ->
                    handleTelecomDisconnect(context, session.callId, cause)
                },
                onSetActive = {
                    handleTelecomSetActive(context, session.callId)
                },
                onSetInactive = {
                    handleTelecomSetInactive(context, session.callId)
                },
            ) {
                controls[session.callId] = this
                releaseSetupGate()
                flushPendingCommands(context, session.callId, this)
            }
        } catch (_: Throwable) {
            // Presentation and app call state remain valid even when Telecom cannot add the call.
        } finally {
            releaseSetupGate()
            controls.remove(session.callId)
            jobs.remove(session.callId)
            pendingCommands.remove(session.callId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureRegistered(callsManager: CallsManager) {
        if (registered.get()) return

        synchronized(registered) {
            if (registered.get()) return
            callsManager.registerAppWithTelecom(
                CallsManager.CAPABILITY_BASELINE or
                    CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING,
            )
            registered.set(true)
        }
    }

    private fun callAttributes(session: CallSession): CallAttributesCompat {
        val rawAddress = session.handle?.takeIf { it.isNotBlank() } ?: session.callerId
        val address = Uri.parse("rncallmask:" + Uri.encode(rawAddress))
        val callType =
            if (session.media == "video") {
                CallAttributesCompat.CALL_TYPE_VIDEO_CALL
            } else {
                CallAttributesCompat.CALL_TYPE_AUDIO_CALL
            }

        return CallAttributesCompat(
            displayName = session.callerName,
            address = address,
            direction = CallAttributesCompat.DIRECTION_INCOMING,
            callType = callType,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
    }

    private fun enqueueOrRun(context: Context, callId: String, command: PendingCommand) {
        val control = controls[callId]
        if (control != null) {
            control.launch {
                runCommand(context.applicationContext, callId, control, command)
            }
            return
        }

        pendingCommands
            .getOrPut(callId) { ConcurrentLinkedQueue() }
            .offer(command)
    }

    private fun flushPendingCommands(
        context: Context,
        callId: String,
        control: CallControlScope,
    ) {
        val queue = pendingCommands.remove(callId) ?: return
        control.launch {
            while (true) {
                val command = queue.poll() ?: break
                runCommand(context.applicationContext, callId, control, command)
            }
        }
    }

    private suspend fun runCommand(
        context: Context,
        callId: String,
        control: CallControlScope,
        command: PendingCommand,
    ) {
        val result = when (command) {
            PendingCommand.ANSWER -> {
                val session = CallRegistry.get(context).get(callId)
                val callType =
                    if (session?.media == "video") {
                        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                    } else {
                        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                    }
                control.answer(callType)
            }
            PendingCommand.DECLINE ->
                control.disconnect(DisconnectCause(DisconnectCause.REJECTED))
            PendingCommand.END ->
                control.disconnect(DisconnectCause(DisconnectCause.LOCAL))
            PendingCommand.SET_ACTIVE ->
                control.setActive()
        }

        if (result is CallControlResult.Error && command == PendingCommand.ANSWER) {
            failConnectingCall(context, callId)
        }
    }

    private fun failConnectingCall(context: Context, callId: String) {
        val registry = CallRegistry.get(context)
        val current = registry.get(callId) ?: return
        if (current.state != CallState.CONNECTING) return

        val ended = registry.transition(callId, CallState.ENDED, "failed") ?: return
        CallNotificationManager(context).cancel(callId)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(
                callId = callId,
                type = "end",
                state = ended.state.wireValue,
                endReason = ended.endReason,
            ),
        )
    }

    private fun handleTelecomAnswer(context: Context, callId: String) {
        val registry = CallRegistry.get(context)
        val current = registry.get(callId) ?: return
        if (current.state != CallState.RINGING && current.state != CallState.INCOMING) return

        val connecting = registry.transition(callId, CallState.CONNECTING) ?: return
        CallNotificationManager(context).showOngoing(connecting)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(
                callId = callId,
                type = "answer",
                state = connecting.state.wireValue,
            ),
        )
    }

    private fun handleTelecomDisconnect(
        context: Context,
        callId: String,
        cause: DisconnectCause,
    ) {
        val registry = CallRegistry.get(context)
        val current = registry.get(callId) ?: return
        if (current.state == CallState.ENDED) return

        if (current.state == CallState.ACTIVE || current.state == CallState.HELD) {
            registry.transition(callId, CallState.ENDING)
        }

        val reason = endReason(cause)
        val ended = registry.transition(callId, CallState.ENDED, reason) ?: return
        CallNotificationManager(context).cancel(callId)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(
                callId = callId,
                type = if (cause.code == DisconnectCause.REJECTED) "decline" else "end",
                state = ended.state.wireValue,
                endReason = ended.endReason,
            ),
        )
    }

    private fun handleTelecomSetActive(context: Context, callId: String) {
        val registry = CallRegistry.get(context)
        val current = registry.get(callId) ?: return
        if (current.state != CallState.CONNECTING && current.state != CallState.HELD) return

        val active = registry.transition(callId, CallState.ACTIVE) ?: return
        CallNotificationManager(context).showOngoing(active)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(
                callId = callId,
                type = "stateChanged",
                state = active.state.wireValue,
            ),
        )
    }

    private fun handleTelecomSetInactive(context: Context, callId: String) {
        val registry = CallRegistry.get(context)
        val current = registry.get(callId) ?: return
        if (current.state != CallState.ACTIVE) return

        val held = registry.transition(callId, CallState.HELD) ?: return
        CallNotificationManager(context).showOngoing(held)
        CallEventBus.dispatch(
            context,
            NativeCallEvent(
                callId = callId,
                type = "stateChanged",
                state = held.state.wireValue,
            ),
        )
    }

    internal fun endReason(cause: DisconnectCause): String = when (cause.code) {
        DisconnectCause.REJECTED -> "declined"
        DisconnectCause.MISSED -> "missed"
        DisconnectCause.REMOTE -> "remote"
        else -> "local"
    }
}
