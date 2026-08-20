package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.ModuleSnapshot

/**
 * Combines consecutive diagnostic results for one ECU into a durable UI snapshot.
 *
 * Dashboard polling queries more than one local identifier per ECU. Replacing the
 * snapshot for every individual result discarded RPM/coolant after the DME air-data
 * request, gear data after the EGS temperature request, and vehicle speed after the
 * DSC wheel-speed request. This reducer keeps the latest value for every decoded key.
 */
object DiagnosticSnapshotReducer {
    private const val LAST_ERROR_KEY = "diagnostic_last_error"
    private const val LAST_ERROR_RESPONSE_KEY = "diagnostic_last_error_response"

    fun apply(
        previous: ModuleSnapshot?,
        result: JobResult,
        snapshotTitleSuffix: String = "",
        timestamp: String,
    ): ModuleSnapshot {
        val priorFields = previous?.decoded.orEmpty()
        val nextFields = if (result.success) {
            priorFields + result.decoded - LAST_ERROR_KEY - LAST_ERROR_RESPONSE_KEY
        } else {
            priorFields + mapOf(
                LAST_ERROR_KEY to result.summary,
                LAST_ERROR_RESPONSE_KEY to result.responseHex.ifBlank { "<empty>" },
            )
        }

        return ModuleSnapshot(
            targetId = result.target.name,
            title = if (result.success || previous == null) result.job.label + snapshotTitleSuffix else previous.title,
            summary = if (result.success) result.summary else "${result.summary} — last valid values retained",
            decoded = nextFields,
            rawResponse = result.responseHex,
            timestamp = timestamp,
        )
    }
}
