package com.bmwe60coderpro.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticSnapshotReducerTest {
    @Test
    fun `successive live data jobs retain fields required by all gauges`() {
        val basic = result(
            jobId = "dme_live_basic",
            success = true,
            decoded = mapOf(
                "engine_speed_rpm" to "2400.0",
                "coolant_temp_c" to "92",
                "battery_v" to "13.8",
            ),
        )
        val air = result(
            jobId = "dme_live_air",
            success = true,
            decoded = mapOf("air_mass_or_load_raw" to "481"),
        )

        val afterBasic = DiagnosticSnapshotReducer.apply(null, basic, " [poll]", "12:00:00")
        val afterAir = DiagnosticSnapshotReducer.apply(afterBasic, air, " [poll]", "12:00:01")

        assertEquals("2400.0", afterAir.decoded["engine_speed_rpm"])
        assertEquals("92", afterAir.decoded["coolant_temp_c"])
        assertEquals("13.8", afterAir.decoded["battery_v"])
        assertEquals("481", afterAir.decoded["air_mass_or_load_raw"])
    }

    @Test
    fun `failed poll keeps last valid gauge data and records its diagnostic error`() {
        val good = result(
            jobId = "dsc_live_status",
            success = true,
            decoded = mapOf("vehicle_speed_kph" to "64.2"),
        )
        val failed = result(
            jobId = "dsc_live_wheels",
            success = false,
            summary = "Negative response to 0x21 NRC 0x31",
            responseHex = "83 F1 56 7F 21 31 6B",
        )

        val afterGood = DiagnosticSnapshotReducer.apply(null, good, " [poll]", "12:00:00")
        val afterFailure = DiagnosticSnapshotReducer.apply(afterGood, failed, " [poll]", "12:00:01")

        assertEquals("64.2", afterFailure.decoded["vehicle_speed_kph"])
        assertEquals("Negative response to 0x21 NRC 0x31", afterFailure.decoded["diagnostic_last_error"])
        assertTrue(afterFailure.summary.contains("last valid values retained"))
    }

    private fun result(
        jobId: String,
        success: Boolean,
        decoded: Map<String, String> = emptyMap(),
        summary: String = "OK",
        responseHex: String = "82 F1 12 61 01 87",
    ): JobResult = JobResult(
        job = BmwJobs.byId(jobId)!!,
        target = BmwTargets.DME,
        requestHex = "82 12 F1 21 01 A6",
        responseHex = responseHex,
        success = success,
        summary = summary,
        decoded = decoded,
    )
}
