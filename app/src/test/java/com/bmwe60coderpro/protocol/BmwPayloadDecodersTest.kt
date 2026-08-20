package com.bmwe60coderpro.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class BmwPayloadDecodersTest {
    private fun decodeObd(pid: Int, vararg data: Int): Map<String, String> =
        BmwPayloadDecoders.decode(
            context = DecodeContext(
                target = BmwTargets.DME,
                step = JobStep(serviceId = 0x01, payload = listOf(pid), label = "OBD PID"),
            ),
            payload = listOf(0x41, pid) + data.toList(),
        )

    @Test
    fun `OBD engine speed response feeds the RPM gauge`() {
        val decoded = decodeObd(0x0C, 0x1F, 0x40)

        assertEquals("0x01", decoded["obd_mode"])
        assertEquals("0x0C", decoded["obd_pid"])
        assertEquals("2000.0", decoded["engine_speed_rpm"])
    }

    @Test
    fun `OBD speed coolant throttle and voltage responses feed the dashboard fields`() {
        assertEquals("88", decodeObd(0x0D, 0x58)["vehicle_speed_kph"])
        assertEquals("90", decodeObd(0x05, 0x82)["coolant_temp_c"])
        assertEquals("50.2", decodeObd(0x11, 0x80)["throttle_angle_pct"])
        assertEquals("14.2", decodeObd(0x42, 0x37, 0x78)["battery_v"])
    }
}
