package com.bmwe60coderpro.controller

import com.bmwe60coderpro.protocol.BmwTargets
import com.bmwe60coderpro.protocol.KdcanSession

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
        val activeTargetSessions: Set<Int> = emptySet(),
        val lastKeepAlive: Map<Int, Long> = emptyMap()
    )

    private var sessionState = SessionState()

    /** Start extended diagnostic sessions (0x10 0x03) on target modules. */
    suspend fun initSessions(session: KdcanSession): String {
        val targets = listOf(BmwTargets.DME, BmwTargets.DSC, BmwTargets.KOMBI, BmwTargets.CCC)
        val activeSessions = mutableSetOf<Int>()
        val keepAlives = mutableMapOf<Int, Long>()
        val resultsSummary = mutableMapOf<String, Boolean>()

        // ── Wake up attempt ──
        // Send a broadcast TesterPresent or a dummy frame to CAS to wake the bus if KL15 is off.
        runCatching { session.sendRawFrame(0x40, 0x3E, listOf(0x80)) }
        kotlinx.coroutines.delay(200)

        targets.forEach { target ->
            // Clear any existing session with 0x10 0x01 then jump to 0x10 0x03
            runCatching { session.sendRawFrame(target.targetAddress, 0x10, listOf(0x01)) }
            kotlinx.coroutines.delay(50)

            val result = runCatching {
                session.sendRawFrame(target.targetAddress, 0x10, listOf(0x03))
            }.getOrDefault("")

            // Optional: Request security seed (0x27 0x01) to keep the module in a permissive state
            if (target.targetAddress == BmwTargets.DME.targetAddress || target.targetAddress == BmwTargets.CAS.targetAddress) {
                runCatching { session.sendRawFrame(target.targetAddress, 0x27, listOf(0x01)) }
            }

            val ok = result.contains("50 03") || result.contains("50")
            if (ok) {
                activeSessions.add(target.targetAddress)
                keepAlives[target.targetAddress] = System.currentTimeMillis()
            }
            resultsSummary[target.name] = ok
        }

        sessionState = SessionState(
            dmeSessionActive = resultsSummary[BmwTargets.DME.name] ?: false,
            dscSessionActive = resultsSummary[BmwTargets.DSC.name] ?: false,
            activeTargetSessions = activeSessions,
            lastKeepAlive = keepAlives
        )

        return buildString {
            append("Sessions: ")
            resultsSummary.forEach { (name, ok) ->
                append("$name:${if (ok) "OK" else "FAIL"} ")
            }
        }
    }

    /** Send tester present (0x3E) to keep sessions alive. Call every ~2 s. */
    suspend fun keepAlive(session: KdcanSession): String {
        val now = System.currentTimeMillis()
        val results = mutableListOf<String>()
        val updatedKeepAlives = sessionState.lastKeepAlive.toMutableMap()

        sessionState.activeTargetSessions.forEach { addr ->
            val last = sessionState.lastKeepAlive[addr] ?: 0L
            if (now - last > 2000) {
                val r = runCatching {
                    session.sendRawFrame(addr, 0x3E, listOf(0x00))
                }.getOrDefault("ERR")
                updatedKeepAlives[addr] = now
                if (r.isNotEmpty()) results.add("0x%02X: $r".format(addr))
            }
        }

        sessionState = sessionState.copy(lastKeepAlive = updatedKeepAlives)

        return if (results.isEmpty()) "No keep-alive needed"
        else "Keep-alive: ${results.joinToString(", ")}"
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

        // Send extra payloads (e.g. indicators, horn simulation)
        commands.extraPayloads.forEach { (targetAddr, payload) ->
            sendControl(session, targetAddr, payload)
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
