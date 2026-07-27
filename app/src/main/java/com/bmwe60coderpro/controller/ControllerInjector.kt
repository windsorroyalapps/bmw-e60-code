package com.bmwe60coderpro.controller

import com.bmwe60coderpro.protocol.BmwTargets
import com.bmwe60coderpro.protocol.KdcanSession
import kotlinx.coroutines.delay

/**
 * Xbox wired USB controller → BMW E60 vehicle bus injection.
 *
 * Sends KWP2000 IOCBLI (0x30) frames directly to DME and DSC without
 * session target switching. Requires extended diagnostic sessions (0x10 0x03)
 * to be active on each target before control frames will be accepted.
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
    )

    data class SessionState(
        val dmeSessionActive: Boolean = false,
        val dscSessionActive: Boolean = false,
        val lastDmeKeepAlive: Long = 0L,
        val lastDscKeepAlive: Long = 0L,
    )

    private var sessionState = SessionState()

    /** Start extended diagnostic sessions (0x10 0x03) on DME and DSC. */
    suspend fun initSessions(session: KdcanSession): String {
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
    suspend fun keepAlive(session: KdcanSession): String {
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
    ): TickResult {
        var throttleHex = ""
        var steeringHex = ""
        var brakeHex = ""
        var tOk = false
        var sOk = false
        var bOk = false

        if (sendThrottle && commands.throttlePayload.isNotEmpty()) {
            val r = sendControl(session, BmwTargets.DME.targetAddress, commands.throttlePayload)
            throttleHex = r.first
            tOk = r.second
        }

        if (sendSteering && commands.steeringPayload.isNotEmpty()) {
            val r = sendControl(session, BmwTargets.DSC.targetAddress, commands.steeringPayload)
            steeringHex = r.first
            sOk = r.second
        }

        if (sendBrake && commands.brakePayload.isNotEmpty()) {
            val r = sendControl(session, BmwTargets.DSC.targetAddress, commands.brakePayload)
            brakeHex = r.first
            bOk = r.second
        }

        val summary = buildString {
            append(commands.summary)
            if (sendThrottle) append(" THR=${if (tOk) "OK" else "FAIL"}")
            if (sendSteering) append(" STR=${if (sOk) "OK" else "FAIL"}")
            if (sendBrake) append(" BRK=${if (bOk) "OK" else "FAIL"}")
        }

        return TickResult(
            throttleHex = throttleHex,
            steeringHex = steeringHex,
            brakeHex = brakeHex,
            throttleOk = tOk,
            steeringOk = sOk,
            brakeOk = bOk,
            summary = summary,
        )
    }

    /** Emergency stop: zero throttle, full brake hint. */
    suspend fun emergencyStop(session: KdcanSession): TickResult {
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
        )
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Send a KWP 0x30 IOCBLI frame and validate the response.
     * Positive response: 0x70 + localId
     * Negative response: 0x7F + service + NRC
     */
    private suspend fun sendControl(
        session: KdcanSession,
        targetAddress: Int,
        payload: List<Int>,
    ): Pair<String, Boolean> {
        val result = runCatching {
            session.sendRawFrame(targetAddress, 0x30, payload)
        }.getOrDefault("")

        if (result.isEmpty()) return "NO RESPONSE" to false

        // Parse response hex string
        val bytes = result.trim().split(" ").mapNotNull { it.toIntOrNull(16) }

        // Check for positive response: 0x70 (0x30 + 0x40)
        val hasPositive = bytes.any { it == 0x70 }
        // Check for negative response: 0x7F
        val hasNegative = bytes.any { it == 0x7F }

        return when {
            hasPositive && !hasNegative -> result to true
            hasNegative -> "$result (NRC)" to false
            else -> "$result (unexpected)" to false
        }
    }
}
