package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.util.HexUtils

/**
 * E60 / E61 K-CAN (100 kbps) and PT-CAN (500 kbps) message database.
 *
 * Sources:
 * - llilakoblock/bmw-e87-e90-can-bt (GitHub) — E87/E90 CAN reverse engineering
 * - M5Board / E60 CAN bus research community
 * - ubis/bmwcanmodule — K-CAN emulator patterns
 *
 * These IDs are for passive CAN sniffing and injection. K-CAN runs at 100 kbps
 * and carries comfort/infotainment data. PT-CAN runs at 500 kbps and carries
 * powertrain data (engine, transmission, DSC).
 */

object E60CanBus {

    enum class CanBus {
        K_CAN,      // 100 kbps — comfort, doors, lighting, SZL, iDrive
        PT_CAN,     // 500 kbps — engine, gearbox, DSC, steering
        K_CAN2,     // 500 kbps — newer E60 LCI, FRM, LM
    }

    data class CanMessageDef(
        val id: Int,
        val name: String,
        val bus: CanBus,
        val length: Int,
        val description: String,
        val fields: List<FieldDef>,
    )

    data class FieldDef(
        val name: String,
        val byteStart: Int,
        val bitStart: Int = 0,
        val bitLength: Int = 8,
        val signed: Boolean = false,
        val scale: Double = 1.0,
        val offset: Double = 0.0,
        val unit: String = "",
    )

    // ── PT-CAN (500 kbps) Powertrain ─────────────────────────────────────────

    val PT_CAN_MESSAGES = listOf(
        CanMessageDef(
            id = 0x0A8,
            name = "TorqueClutchBrake",
            bus = CanBus.PT_CAN,
            length = 8,
            description = "Engine torque, clutch and brake status",
            fields = listOf(
                FieldDef("torque_nm", 0, 0, 16, signed = true, scale = 0.01, offset = 0.0, unit = "Nm"),
                FieldDef("clutch_pressed", 2, 0, 8, scale = 1.0, unit = "bool"),
                FieldDef("brake_pressed", 3, 0, 8, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x0AA,
            name = "EngineRPMThrottle",
            bus = CanBus.PT_CAN,
            length = 8,
            description = "Engine RPM and throttle position",
            fields = listOf(
                FieldDef("rpm", 0, 0, 16, scale = 0.25, unit = "rpm"),
                FieldDef("throttle_pct", 2, 0, 16, scale = 0.01, unit = "%"),
                FieldDef("torque_request", 4, 0, 16, signed = true, scale = 0.01, unit = "Nm"),
            )
        ),
        CanMessageDef(
            id = 0x0C4,
            name = "SteeringPosition1",
            bus = CanBus.PT_CAN,
            length = 7,
            description = "Steering wheel position (SZL → DSC)",
            fields = listOf(
                FieldDef("steering_angle", 0, 0, 16, signed = true, scale = 0.1, unit = "deg"),
                FieldDef("steering_speed", 2, 0, 16, signed = true, scale = 0.1, unit = "deg/s"),
            )
        ),
        CanMessageDef(
            id = 0x0C8,
            name = "SteeringPosition2",
            bus = CanBus.PT_CAN,
            length = 6,
            description = "Steering wheel position (sent 2x as often as 0x0C4)",
            fields = listOf(
                FieldDef("steering_angle", 0, 0, 16, signed = true, scale = 0.1, unit = "deg"),
                FieldDef("steering_speed", 2, 0, 16, signed = true, scale = 0.1, unit = "deg/s"),
            )
        ),
        CanMessageDef(
            id = 0x0CE,
            name = "WheelSpeeds",
            bus = CanBus.PT_CAN,
            length = 8,
            description = "Individual wheel speeds from DSC",
            fields = listOf(
                FieldDef("wheel_fl", 0, 0, 16, scale = 0.01, unit = "km/h"),
                FieldDef("wheel_fr", 2, 0, 16, scale = 0.01, unit = "km/h"),
                FieldDef("wheel_rl", 4, 0, 16, scale = 0.01, unit = "km/h"),
                FieldDef("wheel_rr", 6, 0, 16, scale = 0.01, unit = "km/h"),
            )
        ),
        CanMessageDef(
            id = 0x19E,
            name = "ABSBrakingForce",
            bus = CanBus.PT_CAN,
            length = 8,
            description = "ABS / braking force from DSC",
            fields = listOf(
                FieldDef("brake_pressure", 0, 0, 16, scale = 0.1, unit = "bar"),
                FieldDef("abs_active", 2, 0, 8, scale = 1.0, unit = "bool"),
                FieldDef("dsc_active", 3, 0, 8, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x1A6,
            name = "SpeedCluster",
            bus = CanBus.PT_CAN,
            length = 8,
            description = "Speed as used by instrument cluster",
            fields = listOf(
                FieldDef("speed_kph", 0, 0, 16, scale = 0.01, unit = "km/h"),
                FieldDef("rpm", 2, 0, 16, scale = 0.25, unit = "rpm"),
            )
        ),
        CanMessageDef(
            id = 0x1B4,
            name = "SpeedMPHHandbrake",
            bus = CanBus.PT_CAN,
            length = 8,
            description = "Speed in MPH and handbrake status",
            fields = listOf(
                FieldDef("speed_mph", 0, 0, 16, scale = 0.01, unit = "mph"),
                FieldDef("handbrake", 2, 0, 8, scale = 1.0, unit = "bool"),
            )
        ),
    )

    // ── K-CAN (100 kbps) Comfort / Body ──────────────────────────────────────

    val K_CAN_MESSAGES = listOf(
        CanMessageDef(
            id = 0x130,
            name = "IgnitionKey",
            bus = CanBus.K_CAN,
            length = 5,
            description = "Ignition and key status from CAS",
            fields = listOf(
                FieldDef("terminal_15", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("terminal_r", 0, 1, 1, scale = 1.0, unit = "bool"),
                FieldDef("key_inserted", 1, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("key_valid", 1, 1, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x1C2,
            name = "PDCData",
            bus = CanBus.K_CAN,
            length = 8,
            description = "PDC (Park Distance Control) sensor data",
            fields = listOf(
                FieldDef("pdc_front_left", 0, 0, 8, scale = 1.0, unit = "cm"),
                FieldDef("pdc_front_mid", 1, 0, 8, scale = 1.0, unit = "cm"),
                FieldDef("pdc_front_right", 2, 0, 8, scale = 1.0, unit = "cm"),
                FieldDef("pdc_rear_left", 3, 0, 8, scale = 1.0, unit = "cm"),
                FieldDef("pdc_rear_mid", 4, 0, 8, scale = 1.0, unit = "cm"),
                FieldDef("pdc_rear_right", 5, 0, 8, scale = 1.0, unit = "cm"),
            )
        ),
        CanMessageDef(
            id = 0x24A,
            name = "ReverseStatus",
            bus = CanBus.K_CAN,
            length = 2,
            description = "Reverse gear status",
            fields = listOf(
                FieldDef("reverse", 0, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x252,
            name = "WiperStatus",
            bus = CanBus.K_CAN,
            length = 2,
            description = "Windscreen wiper status from JBE",
            fields = listOf(
                FieldDef("wiper_active", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("wiper_speed", 0, 1, 2, scale = 1.0, unit = "level"),
            )
        ),
        CanMessageDef(
            id = 0x264,
            name = "iDriveRotary",
            bus = CanBus.K_CAN,
            length = 6,
            description = "iDrive controller rotary input from CON",
            fields = listOf(
                FieldDef("rotary_x", 0, 0, 16, signed = true, scale = 1.0, unit = "ticks"),
                FieldDef("rotary_y", 2, 0, 16, signed = true, scale = 1.0, unit = "ticks"),
                FieldDef("rotary_button", 4, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x267,
            name = "iDriveButtons",
            bus = CanBus.K_CAN,
            length = 6,
            description = "iDrive controller directional buttons from CON",
            fields = listOf(
                FieldDef("button_menu", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("button_back", 0, 1, 1, scale = 1.0, unit = "bool"),
                FieldDef("button_option", 0, 2, 1, scale = 1.0, unit = "bool"),
                FieldDef("button_navi", 0, 3, 1, scale = 1.0, unit = "bool"),
                FieldDef("button_tel", 0, 4, 1, scale = 1.0, unit = "bool"),
                FieldDef("button_radio", 0, 5, 1, scale = 1.0, unit = "bool"),
                FieldDef("button_cd", 0, 6, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x26E,
            name = "IgnitionStatus",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Full ignition status from CAS",
            fields = listOf(
                FieldDef("terminal_15", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("terminal_30", 0, 1, 1, scale = 1.0, unit = "bool"),
                FieldDef("engine_running", 1, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x273,
            name = "CICStatus",
            bus = CanBus.K_CAN,
            length = 8,
            description = "CCC / CIC status",
            fields = listOf(
                FieldDef("screen_on", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("menu_active", 0, 1, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x2A6,
            name = "WiperControls",
            bus = CanBus.K_CAN,
            length = 2,
            description = "Windscreen wiper controls from SZL",
            fields = listOf(
                FieldDef("wiper_stalk", 0, 0, 3, scale = 1.0, unit = "pos"),
                FieldDef("washer", 0, 3, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x2B4,
            name = "DoorLockRemote",
            bus = CanBus.K_CAN,
            length = 2,
            description = "Door locking via remote control",
            fields = listOf(
                FieldDef("lock_cmd", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("unlock_cmd", 0, 1, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x2CA,
            name = "OutsideTemp",
            bus = CanBus.K_CAN,
            length = 2,
            description = "Outside temperature from KOMBI",
            fields = listOf(
                FieldDef("temp_c", 0, 0, 8, signed = true, offset = -40.0, unit = "°C"),
            )
        ),
        CanMessageDef(
            id = 0x2D6,
            name = "AirConStatus",
            bus = CanBus.K_CAN,
            length = 3,
            description = "Air conditioning status",
            fields = listOf(
                FieldDef("ac_on", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("fan_speed", 1, 0, 4, scale = 1.0, unit = "level"),
            )
        ),
        CanMessageDef(
            id = 0x2E6,
            name = "ClimateControl",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Climate control status (fan and temp)",
            fields = listOf(
                FieldDef("driver_temp", 0, 0, 8, scale = 0.5, offset = 10.0, unit = "°C"),
                FieldDef("passenger_temp", 1, 0, 8, scale = 0.5, offset = 10.0, unit = "°C"),
                FieldDef("fan_speed", 2, 0, 8, scale = 1.0, unit = "%"),
            )
        ),
        CanMessageDef(
            id = 0x2F8,
            name = "TimeDate",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Report time and date from KOMBI",
            fields = listOf(
                FieldDef("hour", 0, 0, 8, scale = 1.0, unit = "h"),
                FieldDef("minute", 1, 0, 8, scale = 1.0, unit = "min"),
                FieldDef("second", 2, 0, 8, scale = 1.0, unit = "s"),
                FieldDef("day", 3, 0, 8, scale = 1.0, unit = "d"),
                FieldDef("month", 4, 0, 8, scale = 1.0, unit = "m"),
                FieldDef("year", 5, 0, 8, scale = 1.0, offset = 2000.0, unit = "y"),
            )
        ),
        CanMessageDef(
            id = 0x2FC,
            name = "DoorStatus",
            bus = CanBus.K_CAN,
            length = 7,
            description = "Door status from CAS",
            fields = listOf(
                FieldDef("driver_door", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("passenger_door", 0, 1, 1, scale = 1.0, unit = "bool"),
                FieldDef("rear_left_door", 0, 2, 1, scale = 1.0, unit = "bool"),
                FieldDef("rear_right_door", 0, 3, 1, scale = 1.0, unit = "bool"),
                FieldDef("trunk", 0, 4, 1, scale = 1.0, unit = "bool"),
                FieldDef("hood", 0, 5, 1, scale = 1.0, unit = "bool"),
                FieldDef("driver_lock", 1, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("passenger_lock", 1, 1, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x328,
            name = "BatteryResetCounter",
            bus = CanBus.K_CAN,
            length = 6,
            description = "1-second count from battery removal / reset",
            fields = listOf(
                FieldDef("seconds_since_reset", 0, 0, 32, scale = 1.0, unit = "s"),
            )
        ),
        CanMessageDef(
            id = 0x32E,
            name = "InteriorSensors",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Internal temp, light and solar sensors",
            fields = listOf(
                FieldDef("interior_temp", 0, 0, 8, scale = 0.5, offset = -40.0, unit = "°C"),
                FieldDef("light_sensor", 2, 0, 16, scale = 1.0, unit = "lux_raw"),
            )
        ),
        CanMessageDef(
            id = 0x330,
            name = "OdometerFuel",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Odometer, average fuel, and range from KOMBI",
            fields = listOf(
                FieldDef("odometer_km", 0, 0, 32, scale = 0.1, unit = "km"),
                FieldDef("avg_fuel_l100", 4, 0, 16, scale = 0.01, unit = "L/100km"),
                FieldDef("range_km", 6, 0, 16, scale = 0.1, unit = "km"),
            )
        ),
        CanMessageDef(
            id = 0x349,
            name = "FuelLevel",
            bus = CanBus.K_CAN,
            length = 5,
            description = "Fuel level sensors from JBE",
            fields = listOf(
                FieldDef("fuel_level_left", 0, 0, 16, scale = 0.01, unit = "L"),
                FieldDef("fuel_level_right", 2, 0, 16, scale = 0.01, unit = "L"),
            )
        ),
        CanMessageDef(
            id = 0x34F,
            name = "Handbrake",
            bus = CanBus.K_CAN,
            length = 2,
            description = "Handbrake status",
            fields = listOf(
                FieldDef("handbrake_on", 0, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x362,
            name = "AvgMPH_MPG",
            bus = CanBus.K_CAN,
            length = 7,
            description = "Average MPH and average MPG from KOMBI",
            fields = listOf(
                FieldDef("avg_mph", 0, 0, 16, scale = 0.1, unit = "mph"),
                FieldDef("avg_mpg", 2, 0, 16, scale = 0.01, unit = "mpg"),
            )
        ),
        CanMessageDef(
            id = 0x366,
            name = "ExtTempRange",
            bus = CanBus.K_CAN,
            length = 4,
            description = "External temperature and range",
            fields = listOf(
                FieldDef("ext_temp_c", 0, 0, 8, signed = true, scale = 0.5, unit = "°C"),
                FieldDef("range_km", 1, 0, 16, scale = 0.1, unit = "km"),
            )
        ),
        CanMessageDef(
            id = 0x380,
            name = "VIN",
            bus = CanBus.K_CAN,
            length = 7,
            description = "VIN number broadcast from CAS",
            fields = listOf(
                FieldDef("vin_ascii", 0, 0, 56, scale = 1.0, unit = "ascii"),
            )
        ),
        CanMessageDef(
            id = 0x394,
            name = "ServiceData",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Hours and distance since last service",
            fields = listOf(
                FieldDef("hours_since_service", 0, 0, 16, scale = 1.0, unit = "h"),
                FieldDef("km_since_service", 2, 0, 16, scale = 1.0, unit = "km"),
            )
        ),
        CanMessageDef(
            id = 0x3B4,
            name = "BatteryVoltage",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Battery voltage and charge status",
            fields = listOf(
                FieldDef("battery_v", 0, 0, 16, scale = 0.01, unit = "V"),
                FieldDef("charge_status", 2, 0, 8, scale = 1.0, unit = "raw"),
            )
        ),
        CanMessageDef(
            id = 0x3B6,
            name = "PassengerWindow",
            bus = CanBus.K_CAN,
            length = 3,
            description = "Passenger front window status",
            fields = listOf(
                FieldDef("window_pos", 0, 0, 8, scale = 1.0, unit = "%"),
                FieldDef("window_moving", 1, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x3B7,
            name = "DriverRearWindow",
            bus = CanBus.K_CAN,
            length = 3,
            description = "Driver rear window status from JBE",
            fields = listOf(
                FieldDef("window_pos", 0, 0, 8, scale = 1.0, unit = "%"),
                FieldDef("window_moving", 1, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x3B8,
            name = "DriverWindow",
            bus = CanBus.K_CAN,
            length = 3,
            description = "Driver front window status",
            fields = listOf(
                FieldDef("window_pos", 0, 0, 8, scale = 1.0, unit = "%"),
                FieldDef("window_moving", 1, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x3B9,
            name = "PassengerRearWindow",
            bus = CanBus.K_CAN,
            length = 3,
            description = "Passenger rear window status from JBE",
            fields = listOf(
                FieldDef("window_pos", 0, 0, 8, scale = 1.0, unit = "%"),
                FieldDef("window_moving", 1, 0, 1, scale = 1.0, unit = "bool"),
            )
        ),
        CanMessageDef(
            id = 0x581,
            name = "SeatbeltStatus",
            bus = CanBus.K_CAN,
            length = 8,
            description = "Seatbelt status",
            fields = listOf(
                FieldDef("driver_belt", 0, 0, 1, scale = 1.0, unit = "bool"),
                FieldDef("passenger_belt", 0, 1, 1, scale = 1.0, unit = "bool"),
                FieldDef("rear_left_belt", 0, 2, 1, scale = 1.0, unit = "bool"),
                FieldDef("rear_right_belt", 0, 3, 1, scale = 1.0, unit = "bool"),
            )
        ),
    )

    val ALL_MESSAGES: List<CanMessageDef> = PT_CAN_MESSAGES + K_CAN_MESSAGES

    fun byId(id: Int): CanMessageDef? = ALL_MESSAGES.firstOrNull { it.id == id }

    fun byName(name: String): CanMessageDef? = ALL_MESSAGES.firstOrNull { it.name == name }

    fun byBus(bus: CanBus): List<CanMessageDef> = ALL_MESSAGES.filter { it.bus == bus }

    /**
     * Decode a raw CAN frame into human-readable field values.
     */
    fun decodeFrame(id: Int, data: ByteArray): Map<String, String> {
        val def = byId(id) ?: return mapOf("unknown_id" to "0x%03X".format(id), "raw_hex" to HexUtils.bytesToHex(data))
        val result = mutableMapOf<String, String>()
        result["name"] = def.name
        result["bus"] = def.bus.name
        result["description"] = def.description

        for (field in def.fields) {
            val value = extractField(data, field)
            result[field.name] = when {
                field.unit == "bool" -> if (value != 0.0) "true" else "false"
                field.unit == "ascii" -> extractAscii(data, field.byteStart, field.bitLength / 8)
                else -> "%.2f ${field.unit}".format(value)
            }
        }
        return result
    }

    private fun extractField(data: ByteArray, field: FieldDef): Double {
        if (field.bitLength <= 8) {
            val byte = data.getOrNull(field.byteStart)?.toInt() ?: return 0.0
            val raw = (byte shr field.bitStart) and ((1 shl field.bitLength) - 1)
            val signed = if (field.signed && (raw and (1 shl (field.bitLength - 1))) != 0) {
                raw - (1 shl field.bitLength)
            } else raw
            return signed * field.scale + field.offset
        }
        // Multi-byte big-endian
        val bytes = field.bitLength / 8
        var raw = 0
        for (i in 0 until bytes) {
            raw = (raw shl 8) or (data.getOrNull(field.byteStart + i)?.toInt() ?: 0)
        }
        val signed = if (field.signed && (raw and (1 shl (field.bitLength - 1))) != 0) {
            raw - (1 shl field.bitLength)
        } else raw
        return signed * field.scale + field.offset
    }

    private fun extractAscii(data: ByteArray, start: Int, len: Int): String {
        return (start until (start + len).coerceAtMost(data.size))
            .mapNotNull { idx ->
                val c = data.getOrNull(idx)?.toInt() ?: return@mapNotNull null
                if (c in 32..126) c.toChar() else null
            }
            .joinToString("")
    }
}

