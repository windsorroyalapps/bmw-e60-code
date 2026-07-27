package com.bmwe60coderpro.controller

import com.bmwe60coderpro.protocol.BmwTargets
import com.bmwe60coderpro.protocol.KdcanSession
import kotlinx.coroutines.delay

/**
 * Xbox wired USB controller → BMW E60 vehicle bus injection.
 *
 * IMPROVEMENTS:
 * 1. Uses KdcanSession.sendRawFrame() — no session target switching, no job overhead.
 * 2. Starts extended diagnostic sessions (0x10 0x03) on each target before injection.
 * 3. Keeps sessions alive with periodic 0x3E tester present.
 * 4. Throttled to ~10 Hz — K-line cannot sustain 50 Hz real-time control.
 */

object ControllerInjector {

    data class TickResult(
        val throttleHex: String,
        val steeringHex: String,
        val brakeHex: String,
        val throttleOk: Boolean,
        val steeringOk: Boolean,
        val brakeOk: Boolean,
        val summary: String,
        val dryRun: Boolean,
    )

    data class SessionState(
        val dmeSessionActive: Boolean = false,
        val dscSessionActive: Boolean = false,
        val lastDmeKeepAlive: Long = 0L,
        val lastDscKeepAlive: Long = 0L,
    )

    private var sessionState = SessionState()

    /** Start extended diagnostic sessions on DME and DSC. Call once at bridge start. */
    suspend fun initSessions(session: KdcanSession, dryRun: Boolean = true): String {
        if (dryRun) {
            sessionState = SessionState(dmeSessionActive = true, dscSessionActive = true)
            return "[DRY] Sessions simulated active"
        }

        val dmeResult = runCatching {
            session.sendRawFrame(BmwTargets.DME.targetAddress, 0x10, listOf(0x03))
        }.getOrDefault("")
        val dmeOk = dmeResult.contains("50 03") || dmeResult.contains("50")

        val dscResult = runCatching {
            session.sendRawFrame(BmwTargets.DSC.targetAddress, 0x10, listOf(0x03))
        }.getOrDefault("")
        val dscOk = dscResult.contains("50 03") || dscResult.contains("50")

        sessionState = SessionState(
            dmeSessionActive = dmeOk,
            dscSessionActive = dscOk,
            lastDmeKeepAlive = System.currentTimeMillis(),
            lastDscKeepAlive = System.currentTimeMillis(),
        )

        return buildString {
            append("DME session: ")
            append(if (dmeOk) "OK" else "FAIL ($dmeResult)")
            append(" | DSC session: ")
            append(if (dscOk) "OK" else "FAIL ($dscResult)")
        }
    }

    /** Send tester present (0x3E) to keep sessions alive. Call every ~2 s. */
    suspend fun keepAlive(session: KdcanSession, dryRun: Boolean = true): String {
        if (dryRun) return "[DRY] Keep-alive simulated"

        val now = System.currentTimeMillis()
        var dmeResult = ""
        var dscResult = ""

        if (sessionState.dmeSessionActive && now - sessionState.lastDmeKeepAlive > 2000) {
            dmeResult = runCatching {
                session.sendRawFrame(BmwTargets.DME.targetAddress, 0x3E, listOf(0x00))
            }.getOrDefault("")
            sessionState = sessionState.copy(lastDmeKeepAlive = now)
        }

        if (sessionState.dscSessionActive && now - sessionState.lastDscKeepAlive > 2000) {
            dscResult = runCatching {
                session.sendRawFrame(BmwTargets.DSC.targetAddress, 0x3E, listOf(0x00))
            }.getOrDefault("")
            sessionState = sessionState.copy(lastDscKeepAlive = now)
        }

        return buildString {
            if (dmeResult.isNotEmpty()) append("DME 0x3E: $dmeResult ")
            if (dscResult.isNotEmpty()) append("DSC 0x3E: $dscResult")
            if (isEmpty()) append("No keep-alive needed")
        }
    }

    /** Send one controller tick to the vehicle. */
    suspend fun tick(
        session: KdcanSession,
        commands: ControllerVehicleCommands,
        sendThrottle: Boolean = true,
        sendSteering: Boolean = false,
        sendBrake: Boolean = false,
        dryRun: Boolean = true,
    ): TickResult {
        var throttleHex = ""
        var steeringHex = ""
        var brakeHex = ""
        var tOk = false
        var sOk = false
        var bOk = false

        if (sendThrottle && commands.throttlePayload.isNotEmpty()) {
            val r = sendControl(session, BmwTargets.DME.targetAddress, commands.throttlePayload, dryRun)
            throttleHex = r.first
            tOk = r.second
        }

        if (sendSteering && commands.steeringPayload.isNotEmpty()) {
            val r = sendControl(session, BmwTargets.DSC.targetAddress, commands.steeringPayload, dryRun)
            steeringHex = r.first
            sOk = r.second
        }

        if (sendBrake && commands.brakePayload.isNotEmpty()) {
            val r = sendControl(session, BmwTargets.DSC.targetAddress, commands.brakePayload, dryRun)
            brakeHex = r.first
            bOk = r.second
        }

        val summary = buildString {
            if (dryRun) append("[DRY] ")
            append(commands.summary)
            if (sendThrottle) append(" THR=${if (tOk || dryRun) "OK" else "FAIL"}")
            if (sendSteering) append(" STR=${if (sOk || dryRun) "OK" else "FAIL"}")
            if (sendBrake) append(" BRK=${if (bOk || dryRun) "OK" else "FAIL"}")
        }

        return TickResult(
            throttleHex = throttleHex,
            steeringHex = steeringHex,
            brakeHex = brakeHex,
            throttleOk = tOk,
            steeringOk = sOk,
            brakeOk = bOk,
            summary = summary,
            dryRun = dryRun,
        )
    }

    /** Emergency stop: zero throttle, full brake hint, both sent immediately. */
    suspend fun emergencyStop(session: KdcanSession, dryRun: Boolean = true): TickResult {
        val stopCommands = ControllerVehicleCommands(
            throttlePayload = listOf(XboxControllerManager.DME_LOCAL_ID_THROTTLE, 0x03, 0x00),
            steeringPayload = listOf(XboxControllerManager.DSC_LOCAL_ID_STEERING, 0x03, 127),
            brakePayload = listOf(XboxControllerManager.DSC_LOCAL_ID_BRAKE, 0x03, 0xFF),
            hasActiveCommands = true,
            summary = "EMERGENCY STOP",
        )
        return tick(
            session = session,
            commands = stopCommands,
            sendThrottle = true,
            sendSteering = false,
            sendBrake = true,
            dryRun = dryRun,
        )
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun sendControl(
        session: KdcanSession,
        targetAddress: Int,
        payload: List<Int>,
        dryRun: Boolean,
    ): Pair<String, Boolean> {
        return if (dryRun) {
            val payloadHex = payload.joinToString(" ") { "0x%02X".format(it) }
            "30 $payloadHex [dry]" to true
        } else {
            val result = runCatching {
                session.sendRawFrame(targetAddress, 0x30, payload)
            }.getOrDefault("")
            val ok = result.startsWith("7") || result.contains("70") || result.contains("50") || result.isNotEmpty()
            result to ok
        }
    }
}
