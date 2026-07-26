package com.bmwe60coderpro.controller

import com.bmwe60coderpro.protocol.BmwJob
import com.bmwe60coderpro.protocol.BmwTargets
import com.bmwe60coderpro.protocol.JobCategory
import com.bmwe60coderpro.protocol.JobStep
import com.bmwe60coderpro.protocol.KdcanSession

/**
 * Sends [ControllerVehicleCommands] to the vehicle via KWP 0x30 output-control frames.
 *
 * Each controller tick produces up to three frames:
 * 1. DME 0x30 0xA2 throttleByte — pedal position override
 * 2. DSC 0x30 0xA1 steeringByte — steering torque advisory
 * 3. DSC 0x30 0xA3 brakeByte — brake pressure hint
 *
 * The caller decides which are sent (e.g. skip steering if EPS not fitted).
 *
 * Dry-run mode builds all frames and returns their hex representations
 * without touching the transport — useful for verifying axis mapping
 * before connecting to the car.
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

    /**
     * Send one controller tick to the vehicle.
     *
     * @param session Active [KdcanSession].
     * @param commands Axis values already converted to KWP payloads by [XboxControllerManager.buildCommands].
     * @param sendThrottle Whether to send the DME throttle frame this tick.
     * @param sendSteering Whether to send the DSC steering frame this tick.
     * @param sendBrake Whether to send the DSC brake frame this tick.
     * @param dryRun Build but do not transmit.
     */
    suspend fun tick(
        session: KdcanSession,
        commands: ControllerVehicleCommands,
        sendThrottle: Boolean = true,
        sendSteering: Boolean = false, // off by default — only useful with EPS
        sendBrake: Boolean = false, // off by default — advisory only
        dryRun: Boolean = true,
    ): TickResult {
        var throttleHex = ""
        var steeringHex = ""
        var brakeHex = ""
        var tOk = false
        var sOk = false
        var bOk = false

        if (sendThrottle) {
            val r = runControl(session, BmwTargets.DME, commands.throttlePayload, dryRun)
            throttleHex = r.first
            tOk = r.second
        }

        if (sendSteering) {
            val r = runControl(session, BmwTargets.DSC, commands.steeringPayload, dryRun)
            steeringHex = r.first
            sOk = r.second
        }

        if (sendBrake) {
            val r = runControl(session, BmwTargets.DSC, commands.brakePayload, dryRun)
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

    private suspend fun runControl(
        session: KdcanSession,
        target: com.bmwe60coderpro.protocol.EcuTarget,
        payload: List<Int>,
        dryRun: Boolean,
    ): Pair<String, Boolean> {
        session.setTarget(target)

        val job = BmwJob(
            id = "ctrl_inject_${target.name}_${payload.firstOrNull()?.toString(16)}",
            label = "Controller inject → ${target.name}",
            category = JobCategory.CONTROL,
            steps = listOf(JobStep(serviceId = 0x30, payload = payload, label = "0x30 ctrl")),
            description = "Controller axis output control to ${target.name}",
            readOnly = false,
            supportedTargets = setOf(target.name),
        )

        return if (dryRun) {
            val payloadHex = payload.joinToString(" ") { "0x%02X".format(it) }
            "30 $payloadHex [dry]" to true
        } else {
            val result = runCatching { session.execute(job) }.getOrNull()
            val hex = result?.requestHex ?: "error"
            hex to (result?.success == true)
        }
    }
}
