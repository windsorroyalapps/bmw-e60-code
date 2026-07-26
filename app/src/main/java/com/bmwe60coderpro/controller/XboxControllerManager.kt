package com.bmwe60coderpro.controller

import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Xbox wired USB controller → BMW E60 vehicle input bridge.
 *
 * AXIS MAPPING
 * ─────────────────────────────────────────────────────────────────────────────
 * Left stick X  (AXIS_X)          →  Steering angle command  (-1.0 full left … +1.0 full right)
 * Right trigger (AXIS_RTRIGGER)   →  Throttle demand          (0.0 … 1.0)
 * Left trigger  (AXIS_LTRIGGER)   →  Brake demand             (0.0 … 1.0)
 * Left stick Y  (AXIS_Y)          →  Ignored (no longitudinal position control)
 *
 * BUTTON MAPPING
 * ─────────────────────────────────────────────────────────────────────────────
 * A             → Arm / disarm controller bridge
 * B             → Emergency stop (zero throttle + brake request)
 * RB            → Upshift request (EGS paddle up)
 * LB            → Downshift request (EGS paddle down)
 * START         → Enable sport mode
 * BACK/SELECT   → Disable sport mode / return to normal
 *
 * KWP INJECTION TARGETS
 * ─────────────────────────────────────────────────────────────────────────────
 * Steering  →  DSC  (0x56)  local ID 0xA1  (steering-angle torque overlay)
 *              The DSC exposes a KWP 0x30 input for target steering torque used
 *              in EPS-equipped cars; on E60 this acts as an advisory to the EPS
 *              module. Without active EPS this is logged only.
 *
 * Throttle  →  DME  (0x12)  local ID 0xA2  (throttle pedal position override)
 *              KWP 0x30 0x03 with a 0-255 position byte. The DME will honour
 *              this only if it is in diagnostic mode with the session kept alive.
 *              Default safety ceiling is 30 % pedal travel (≈ 77/255).
 *
 * Brake     →  DSC  (0x56)  local ID 0xA3  (brake pressure request hint)
 *              Advisory only on a standard E60; does not actuate calipers.
 *
 * ⚠ SAFETY NOTES
 *   - All injection is disabled by default. The user must explicitly arm via
 *     the UI toggle (and repeat arm with controller A button when live).
 *   - A hard throttle ceiling (DEFAULT_THROTTLE_CEILING = 0.30) caps pedal
 *     override at 30 % unless the user raises it in the UI.
 *   - Emergency-stop (B button or UI stop button) instantly zeros throttle and
 *     requests maximum brake hint.
 *   - The KWP diagnostic session will time out if the app is backgrounded,
 *     automatically reverting DME/DSC to normal input processing.
 *   - This is an experimental research tool. Never use while the vehicle is
 *     in motion without a co-pilot monitoring the diagnostic channel.
 */

const val DEFAULT_THROTTLE_CEILING = 0.30f   // 30 % pedal travel max
const val AXIS_DEADZONE = 0.08f               // ignore stick noise below 8 %

data class ControllerAxes(
    /** -1.0 = full left, 0.0 = centre, +1.0 = full right */
    val steeringNorm: Float = 0f,
    /** 0.0 = released, 1.0 = fully pressed */
    val throttleNorm: Float = 0f,
    /** 0.0 = released, 1.0 = fully pressed */
    val brakeNorm: Float = 0f,
)

data class ControllerButtons(
    val armToggle: Boolean = false,      // A button
    val emergencyStop: Boolean = false,  // B button
    val paddelUp: Boolean = false,       // RB
    val paddleDown: Boolean = false,     // LB
    val sportOn: Boolean = false,        // START
    val sportOff: Boolean = false,       // BACK
)

/** Decoded injection payloads ready to hand to MflInjector / KdcanSession. */
data class ControllerVehicleCommands(
    /** KWP 0x30 payload for DME (service + localId + controlByte + value) */
    val throttlePayload: List<Int>,
    /** KWP 0x30 payload for DSC steering torque advisory */
    val steeringPayload: List<Int>,
    /** KWP 0x30 payload for DSC brake hint */
    val brakePayload: List<Int>,
    /** True if any payload should actually be sent this tick */
    val hasActiveCommands: Boolean,
    /** Human summary for the UI log */
    val summary: String,
)

object XboxControllerManager {

    // ── Axis IDs ──────────────────────────────────────────────────────────────
    private const val AXIS_STEERING = MotionEvent.AXIS_X
    private const val AXIS_THROTTLE = MotionEvent.AXIS_RTRIGGER
    private const val AXIS_BRAKE    = MotionEvent.AXIS_LTRIGGER

    // ── KWP local IDs (community-derived for E60 DME/DSC 0x30 control) ───────
    const val DME_LOCAL_ID_THROTTLE  = 0xA2
    const val DSC_LOCAL_ID_STEERING  = 0xA1
    const val DSC_LOCAL_ID_BRAKE     = 0xA3

    /**
     * Parse raw MotionEvent axes into normalised [ControllerAxes].
     * Call from Activity.dispatchGenericMotionEvent when source is JOYSTICK or GAMEPAD.
     */
    fun parseAxes(event: MotionEvent): ControllerAxes {
        val rawSteering = event.getAxisValue(AXIS_STEERING)
        val rawThrottle = event.getAxisValue(AXIS_THROTTLE)
        val rawBrake    = event.getAxisValue(AXIS_BRAKE)

        return ControllerAxes(
            steeringNorm = applyDeadzone(rawSteering),
            throttleNorm = rawThrottle.coerceIn(0f, 1f),
            brakeNorm    = rawBrake.coerceIn(0f, 1f),
        )
    }

    /** Apply symmetric deadzone to a -1..+1 axis value. */
    fun applyDeadzone(raw: Float): Float {
        return if (abs(raw) < AXIS_DEADZONE) 0f
        else ((raw - AXIS_DEADZONE * (if (raw > 0) 1 else -1)) / (1f - AXIS_DEADZONE)).coerceIn(-1f, 1f)
    }

    /**
     * Convert [ControllerAxes] to KWP 0x30 payloads.
     *
     * @param axes            Normalised axis values.
     * @param throttleCeiling Maximum throttle fraction allowed (0.0–1.0).
     * @param armed           If false, throttle payload is zeroed out.
     */
    fun buildCommands(
        axes: ControllerAxes,
        throttleCeiling: Float = DEFAULT_THROTTLE_CEILING,
        armed: Boolean = false,
    ): ControllerVehicleCommands {
        val cappedThrottle = if (armed) (axes.throttleNorm * throttleCeiling.coerceIn(0f, 1f)) else 0f
        val throttleByte   = (cappedThrottle * 255f).roundToInt().coerceIn(0, 255)

        // Steering: map -1..+1 to 0..255 (127 = centre)
        val steeringByte = ((axes.steeringNorm * 127f) + 127f).roundToInt().coerceIn(0, 255)

        val brakeByte = (axes.brakeNorm * 255f).roundToInt().coerceIn(0, 255)

        // KWP 0x30 payload: [localId, controlOption=0x03, valueByte]
        val throttlePayload  = listOf(DME_LOCAL_ID_THROTTLE, 0x03, throttleByte)
        val steeringPayload  = listOf(DSC_LOCAL_ID_STEERING, 0x03, steeringByte)
        val brakePayload     = listOf(DSC_LOCAL_ID_BRAKE,    0x03, brakeByte)

        val hasActive = armed && (throttleByte > 0 || brakeByte > 0 || steeringByte != 127)

        val summary = buildString {
            append("STR %+.2f (%3d)".format(axes.steeringNorm, steeringByte))
            append("  THR %.2f (%3d)".format(cappedThrottle, throttleByte))
            append("  BRK %.2f (%3d)".format(axes.brakeNorm, brakeByte))
            if (!armed) append("  [DISARMED]")
        }

        return ControllerVehicleCommands(
            throttlePayload  = throttlePayload,
            steeringPayload  = steeringPayload,
            brakePayload     = brakePayload,
            hasActiveCommands = hasActive,
            summary          = summary,
        )
    }

    /** True if a MotionEvent comes from a gamepad / joystick source. */
    fun isGamepadMotion(event: MotionEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) ||
               (source and InputDevice.SOURCE_GAMEPAD  == InputDevice.SOURCE_GAMEPAD)
    }

    /** Describe the controller for a connection log line. */
    fun describeDevice(device: InputDevice?): String {
        if (device == null) return "Unknown controller"
        return "${device.name}  VID=0x%04X PID=0x%04X  ID=${device.id}".format(
            device.vendorId, device.productId
        )
    }

    /** Scan attached input devices and return a gamepad descriptor if one is present. */
    fun findAttachedController(): InputDevice? {
        return InputDevice.getDeviceIds()
            .mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { device: InputDevice ->
                val src = device.sources
                ((src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
            }
    }
}
