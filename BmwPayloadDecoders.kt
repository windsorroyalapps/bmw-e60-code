package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.util.HexUtils
import kotlin.math.roundToInt

data class DecodeContext(
    val target: EcuTarget,
    val step: JobStep,
)

object BmwPayloadDecoders {
    fun decode(context: DecodeContext, payload: List<Int>): Map<String, String> {
        if (payload.isEmpty()) return emptyMap()
        val service = payload.first()
        val data = payload.drop(1)
        val map = linkedMapOf<String, String>()
        map["service"] = "0x%02X".format(service)
        map["payload_hex"] = HexUtils.bytesToHex(payload.map { it.toByte() }.toByteArray())
        map["target"] = context.target.name

        when (service) {
            0x50 -> decodeSession(data, map)
            0x5A -> decodeIdentification(context, data, map)
            0x58 -> decodeDtcBlock(context, data, map)
            0x54 -> map["result"] = "fault memory clear acknowledged"
            0x61 -> decodeLocalIdentifier(context, data, map)
            0x7E -> map["result"] = "tester present acknowledged"
            0x7F -> decodeNegativeResponse(payload, map)
        }

        val ascii = asciiFrom(data)
        if (ascii.isNotBlank()) map.putIfAbsent("ascii", ascii)
        return map
    }

    private fun decodeSession(data: List<Int>, map: MutableMap<String, String>) {
        val accepted = data.firstOrNull()
        if (accepted != null) {
            map["session"] = when (accepted) {
                0x81 -> "default"
                0x85 -> "programming"
                0x86 -> "extended"
                else -> "0x%02X".format(accepted)
            }
        }
    }

    private fun decodeIdentification(context: DecodeContext, data: List<Int>, map: MutableMap<String, String>) {
        val record = data.firstOrNull() ?: return
        val body = data.drop(1)
        map["id_record"] = "0x%02X".format(record)
        val ascii = asciiFrom(body)
        if (ascii.isNotBlank()) {
            map["text"] = ascii
            extractVin(ascii)?.let { map["vin"] = it }
            extractPartNumber(ascii)?.let { map["part_number"] = it }
        } else {
            map["body_hex"] = HexUtils.bytesToHex(body.map { it.toByte() }.toByteArray())
        }
        map["id_record_label"] = when (record) {
            0x90 -> "basic identification"
            0x9A -> "software / supplier identification"
            0x9B -> "variant / coding identification"
            else -> "unknown record"
        }
        map["target"] = context.target.name
    }

    private fun decodeDtcBlock(context: DecodeContext, data: List<Int>, map: MutableMap<String, String>) {
        if (data.isEmpty()) return
        map["dtc_payload_len"] = data.size.toString()
        val startIndex = if (data.size % 3 == 1) 1 else 0
        if (startIndex == 1) map["dtc_header"] = "0x%02X".format(data.first())
        val dictionary = BmwModuleMetadata.dtcDictionaries[context.target.name].orEmpty()
        val dtcTriples = data.drop(startIndex).chunked(3)
        var statusActive = 0
        var knownCount = 0
        dtcTriples.filter { it.size >= 2 }.take(16).forEachIndexed { idx, chunk ->
            val code = ((chunk[0] and 0xFF) shl 8) or (chunk[1] and 0xFF)
            val status = chunk.getOrNull(2)
            val label = dictionary[code]
            if (label != null) knownCount += 1
            if ((status ?: 0) != 0) statusActive += 1
            map["dtc_${idx + 1}_code"] = "%04X".format(code)
            map["dtc_${idx + 1}_status"] = status?.let { "0x%02X".format(it) } ?: "none"
            map["dtc_${idx + 1}_meaning"] = label ?: "unknown / not yet mapped for ${context.target.name}"
            map["dtc_${idx + 1}_severity_guess"] = severityGuess(status)
        }
        map["dtc_count_estimate"] = dtcTriples.count { it.size >= 2 }.toString()
        map["dtc_known_count"] = knownCount.toString()
        map["dtc_nonzero_status_count"] = statusActive.toString()
        if (dtcTriples.none { it.size >= 2 }) map["dtc_info"] = "No DTC triples recognised; inspect payload hex"
    }

    private fun decodeLocalIdentifier(context: DecodeContext, data: List<Int>, map: MutableMap<String, String>) {
        if (data.isEmpty()) return
        val localId = data.first()
        val body = data.drop(1)
        map["local_id"] = "0x%02X".format(localId)
        map["local_id_label"] = localIdLabel(context.target.name, localId)
        annotateLiveLabels(context.target.name, localId, body, map)
        when (context.target.name) {
            BmwTargets.DME.name -> decodeDme(localId, body, map)
            BmwTargets.EGS.name -> decodeEgs(localId, body, map)
            BmwTargets.DSC.name -> decodeDsc(localId, body, map)
            BmwTargets.KOMBI.name -> decodeKombi(localId, body, map)
            BmwTargets.SZL.name -> decodeSzl(localId, body, map)
            BmwTargets.CAS.name -> decodeCas(localId, body, map)
            BmwTargets.FRM.name  -> decodeFrm(localId, body, map)
            BmwTargets.ACSM.name -> decodeAcsm(localId, body, map)
            BmwTargets.CCC.name  -> decodeCcc(localId, body, map)
            else -> decodeGenericBlock(body, map)
        }
    }

    private fun decodeDme(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                u16(body, 0)?.let { map["engine_speed_rpm"] = oneDecimal(it / 4.0) }
                body.getOrNull(2)?.let { map["throttle_angle_pct"] = oneDecimal(it * 100.0 / 255.0) }
                body.getOrNull(3)?.let { map["coolant_temp_c"] = (it - 40).toString() }
                body.getOrNull(4)?.let { map["intake_temp_c"] = (it - 40).toString() }
                body.getOrNull(5)?.let { map["battery_v"] = oneDecimal(it / 10.0) }
            }
            0x11 -> {
                decodeGenericBlock(body, map)
                u16(body, 0)?.let { map["air_mass_or_load_raw"] = it.toString() }
                u16(body, 2)?.let { map["torque_or_injection_raw"] = it.toString() }
                body.getOrNull(4)?.let { map["pedal_pct"] = oneDecimal(it * 100.0 / 255.0) }
                body.getOrNull(5)?.let { map["lambda_control_raw"] = it.toString() }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "DME values are E60-oriented and partly heuristic across ECU software versions"
    }

    private fun decodeEgs(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["current_gear_raw"] = it.toString() }
                body.getOrNull(1)?.let { map["selector_position_raw"] = it.toString() }
                u16(body, 2)?.let { map["input_speed_rpm"] = oneDecimal(it / 4.0) }
                u16(body, 4)?.let { map["output_speed_rpm"] = oneDecimal(it / 4.0) }
            }
            0x12 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["oil_temp_c"] = (it - 40).toString() }
                body.getOrNull(1)?.let { map["lockup_state_bits"] = toBits(it) }
                body.getOrNull(2)?.let { map["shift_program_raw"] = it.toString() }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "EGS values are E60-oriented and partly heuristic"
    }

    private fun decodeDsc(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["status_flags_1"] = toBits(it) }
                body.getOrNull(1)?.let { map["status_flags_2"] = toBits(it) }
                u16(body, 2)?.let { map["vehicle_speed_kph"] = oneDecimal(it / 100.0) }
            }
            0x02 -> {
                decodeGenericBlock(body, map)
                u16(body, 0)?.let { map["steering_angle_raw"] = it.toString() }
                u16(body, 2)?.let { map["yaw_rate_raw"] = it.toString() }
                u16(body, 4)?.let { map["lateral_accel_raw"] = it.toString() }
            }
            0x11 -> {
                decodeGenericBlock(body, map)
                listOf("wheel_speed_fl", "wheel_speed_fr", "wheel_speed_rl", "wheel_speed_rr").forEachIndexed { idx, name ->
                    u16(body, idx * 2)?.let { map["${name}_raw"] = it.toString() }
                }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "DSC values are E60-oriented and partly heuristic"
    }

    private fun decodeKombi(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x82 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["cbs_service_flags"] = toBits(it) }
                body.getOrNull(1)?.let { map["check_control_count"] = it.toString() }
                u16(body, 2)?.let { map["odometer_km_raw"] = it.toString() }
            }
            0x01 -> {
                decodeGenericBlock(body, map)
                u16(body, 0)?.let { map["vehicle_speed_kph"] = oneDecimal(it / 100.0) }
                u16(body, 2)?.let { map["engine_speed_rpm"] = oneDecimal(it / 4.0) }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "KOMBI values are E60-oriented and partly heuristic"
    }

    private fun decodeSzl(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["turn_signal_flags"] = toBits(it) }
                body.getOrNull(1)?.let { map["wiper_flags"] = toBits(it) }
                body.getOrNull(2)?.let { map["button_flags"] = toBits(it) }
            }
            0x02 -> {
                decodeGenericBlock(body, map)
                u16(body, 0)?.let { map["steering_angle_raw"] = it.toString() }
                val m1 = body.getOrNull(2) ?: 0
                val m2 = body.getOrNull(3) ?: 0
                map["button_matrix_1"] = toBits(m1)
                map["button_matrix_2"] = toBits(m2)
                // F-series decoded button names (SZL_LENKRAD_TYP = F_SERIE required)
                val frame = com.bmwe60coderpro.protocol.SzlButtonDecoder.decode(m1, m2)
                map["fseries_active_buttons"] = if (frame.activeButtons.isEmpty()) "none" else frame.activeButtons.joinToString(", ")
                map["fseries_mfl_events_count"] = frame.mflEvents.size.toString()
                map["fseries_mfl_events"] = if (frame.mflEvents.isEmpty()) "none" else frame.mflEvents.joinToString(", ") { it.label }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "SZL values are E60-oriented and partly heuristic"
    }

    private fun decodeCas(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["terminal_flags"] = toBits(it) }
                body.getOrNull(1)?.let { map["key_presence_flags"] = toBits(it) }
                body.getOrNull(2)?.let { map["start_authorization_flags"] = toBits(it) }
            }
            0x82 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["key_slot_status"] = toBits(it) }
                body.getOrNull(1)?.let { map["remote_button_flags"] = toBits(it) }
                body.getOrNull(2)?.let { map["terminal_status_2"] = toBits(it) }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "CAS interpretation is partially heuristic"
    }

    private fun decodeFrm(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["lighting_flags_1"] = toBits(it) }
                body.getOrNull(1)?.let { map["lighting_flags_2"] = toBits(it) }
                body.getOrNull(2)?.let { map["window_flags"] = toBits(it) }
            }
            0x11 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["output_stage_1_raw"] = it.toString() }
                body.getOrNull(1)?.let { map["output_stage_2_raw"] = it.toString() }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "FRM/LM values are E60-oriented and partly heuristic"
    }

    private fun annotateLiveLabels(targetName: String, localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        val labels = BmwModuleMetadata.liveLabelMaps[targetName]?.get(localId).orEmpty()
        if (labels.isEmpty()) return
        labels.forEachIndexed { index, label ->
            val raw = body.getOrNull(index) ?: return@forEachIndexed
            map["live_${label}"] = "0x%02X".format(raw)
        }
        map["live_map_size"] = labels.size.toString()
    }

    private fun localIdLabel(targetName: String, localId: Int): String = when (targetName) {
        BmwTargets.DME.name -> when (localId) {
            0x01 -> "basic live data"
            0x11 -> "air / torque live data"
            0x81 -> "ASCII / VIN text"
            else -> "module-specific block"
        }
        BmwTargets.EGS.name -> when (localId) {
            0x01 -> "gear / speed live data"
            0x12 -> "oil temp / lockup live data"
            0x81 -> "ASCII / VIN text"
            else -> "module-specific block"
        }
        BmwTargets.DSC.name -> when (localId) {
            0x01 -> "vehicle status block"
            0x02 -> "steering / yaw / lateral block"
            0x11 -> "wheel speed block"
            else -> "module-specific block"
        }
        BmwTargets.KOMBI.name -> when (localId) {
            0x01 -> "vehicle speed / rpm block"
            0x82 -> "CBS / odometer block"
            0x81 -> "ASCII / VIN text"
            else -> "module-specific block"
        }
        BmwTargets.SZL.name -> when (localId) {
            0x01 -> "switch status block"
            0x02 -> "steering angle / buttons block"
            0x81 -> "ASCII / VIN text"
            else -> "module-specific block"
        }
        BmwTargets.CAS.name -> when (localId) {
            0x01 -> "terminal / start authorization block"
            0x82 -> "key slot / remote status block"
            0x81 -> "ASCII / VIN text"
            else -> "module-specific block"
        }
        BmwTargets.FRM.name -> when (localId) {
            0x01 -> "lighting / windows status block"
            0x11 -> "output stage block"
            0x81 -> "ASCII / VIN text"
            else -> "module-specific block"
        }
        else -> "module-specific block"
    }

    private fun decodeAsciiBlock(body: List<Int>, map: MutableMap<String, String>) {
        decodeGenericBlock(body, map)
        val ascii = asciiFrom(body)
        if (ascii.isNotBlank()) {
            map["text"] = ascii
            extractVin(ascii)?.let { map["vin"] = it }
            extractPartNumber(ascii)?.let { map["part_number"] = it }
        }
    }

    private fun decodeGenericBlock(body: List<Int>, map: MutableMap<String, String>) {
        map["data_len"] = body.size.toString()
        map["data_hex"] = HexUtils.bytesToHex(body.map { it.toByte() }.toByteArray())
        body.take(12).forEachIndexed { idx, b -> map["byte_$idx"] = "0x%02X".format(b) }
        if (body.size >= 2) map["u16_be_0"] = u16(body, 0).toString()
        if (body.size >= 4) map["u16_be_2"] = u16(body, 2).toString()
    }

    private fun decodeNegativeResponse(payload: List<Int>, map: MutableMap<String, String>) {
        if (payload.size < 3) return
        val requested = payload[1]
        val nrc = payload[2]
        map["requested_service"] = "0x%02X".format(requested)
        map["nrc"] = "0x%02X".format(nrc)
        map["nrc_text"] = when (nrc) {
            0x10 -> "generalReject"
            0x11 -> "serviceNotSupported"
            0x12 -> "subFunctionNotSupported"
            0x21 -> "busyRepeatRequest"
            0x22 -> "conditionsNotCorrect"
            0x31 -> "requestOutOfRange"
            0x33 -> "securityAccessDenied"
            0x35 -> "invalidKey"
            0x78 -> "responsePending"
            else -> "unknown"
        }
    }

    private fun decodeAcsm(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["airbag_status_flags"] = toBits(it) }
                body.getOrNull(1)?.let {
                    map["occupancy_class_raw"] = it.toString()
                    map["occupancy_class"] = when (it) {
                        0x00 -> "empty / no occupant"
                        0x01 -> "small occupant"
                        0x02 -> "normal occupant"
                        0x03 -> "heavy occupant"
                        else -> "0x%02X".format(it)
                    }
                }
                body.getOrNull(2)?.let {
                    map["deployment_status_raw"] = toBits(it)
                    map["deployment_occurred"] = if ((it and 0x01) != 0) "yes" else "no"
                }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "ACSM values are E60-oriented and partly heuristic"
    }

    private fun decodeCcc(localId: Int, body: List<Int>, map: MutableMap<String, String>) {
        when (localId) {
            0x81 -> decodeAsciiBlock(body, map)
            0x01 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let { map["ccc_status_flags"] = toBits(it) }
                body.getOrNull(1)?.let {
                    map["map_slot_active_raw"] = it.toString()
                    map["map_slot_active"] = when (it) {
                        0x01 -> "Comfort"
                        0x02 -> "Sport"
                        0x03 -> "Race / Track"
                        0x04 -> "Custom"
                        else -> "0x%02X".format(it)
                    }
                }
                body.getOrNull(2)?.let { map["most_bus_status_raw"] = toBits(it) }
            }
            0x02 -> {
                decodeGenericBlock(body, map)
                body.getOrNull(0)?.let {
                    map["active_map_slot"] = when (it) {
                        0x01 -> "Comfort"
                        0x02 -> "Sport"
                        0x03 -> "Race / Track"
                        0x04 -> "Custom"
                        else -> "unknown (0x%02X)".format(it)
                    }
                }
                body.getOrNull(1)?.let { map["drive_mode_raw"] = "0x%02X".format(it) }
            }
            else -> decodeGenericBlock(body, map)
        }
        map["decoder_note"] = "CCC values are E60-oriented and partly heuristic"
    }

    private fun asciiFrom(bytes: List<Int>): String {
        return bytes.mapNotNull {
            val c = it.toChar()
            if (c.code in 32..126) c else null
        }.joinToString("")
    }

    private fun extractVin(text: String): String? {
        val vin = Regex("[A-HJ-NPR-Z0-9]{17}").find(text)?.value
        return vin
    }

    private fun extractPartNumber(text: String): String? {
        return Regex("\b(?:[0-9]{7}|[0-9]{11})\b").find(text)?.value
    }

    private fun u16(data: List<Int>, index: Int): Int? {
        val hi = data.getOrNull(index) ?: return null
        val lo = data.getOrNull(index + 1) ?: return null
        return ((hi and 0xFF) shl 8) or (lo and 0xFF)
    }

    private fun toBits(value: Int): String = (7 downTo 0).joinToString("") { bit -> if ((value shr bit) and 1 == 1) "1" else "0" }

    private fun oneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

    private fun severityGuess(status: Int?): String = when {
        status == null -> "unknown"
        status == 0 -> "stored / inactive"
        status and 0x80 != 0 -> "present now"
        status and 0x08 != 0 -> "warning / pending"
        else -> "stored"
    }
}
