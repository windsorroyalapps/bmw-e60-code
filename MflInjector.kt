package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.util.HexUtils

/**
 * MFL packet injector.
 *
 * Sends KWP service 0x30 (inputOutputControlByLocalIdentifier) frames to the E60 KOMBI (0x80)
 * to simulate MFL button presses that the F-series wheel cannot natively produce in E-series
 * KWP format.
 *
 * The injection approach:
 *   1. The app reads SZL 0x21/0x02 continuously via the K+DCAN adapter.
 *   2. [SzlButtonDecoder] converts raw bytes into [SzlButtonFrame] + [MflEvent] list.
 *   3. This class builds and transmits one KWP 0x30 frame per active MflEvent per poll cycle.
 *   4. A short de-bounce window prevents re-firing held buttons every poll tick.
 *
 * Frame structure (KWP over K-line / KDCAN):
 *   [0x80 | len] [target=0x80 KOMBI] [source=0xF1 tester] [0x30 service] [0xA0 localId]
 *   [0x03 control=freeze-current-state] [button_code] [checksum]
 *
 * Safety note: This is a diagnostic-channel command (KWP service 0x30). It does not write
 * permanent coding data. The KOMBI treats 0x30 as a temporary input override and reverts
 * when the diagnostic session closes or tester-present keep-alive stops. You should not
 * leave a live injection session running while driving; close the session when done testing.
 */
object MflInjector {

    data class InjectionResult(
        val event: MflEvent,
        val frameHex: String,
        val responseHex: String,
        val success: Boolean,
        val note: String,
    )

    /**
     * Build and inject a single MFL event into KOMBI.
     *
     * @param session   Active [KdcanSession] already connected and pointed at KOMBI (0x80).
     * @param event     The [MflEvent] to inject.
     * @param dryRun    If true, build the frame but do not transmit it.
     */
    suspend fun inject(
        session: KdcanSession,
        event: MflEvent,
        dryRun: Boolean = false,
    ): InjectionResult {
        // Ensure session target is KOMBI
        session.setTarget(BmwTargets.KOMBI)

        // Build a raw hex string: service 0x30 + the three payload bytes
        // KdcanSession.sendRawHex expects the full KWP frame as hex, but we can also
        // compose a synthetic BmwJob with a single step and let the session handle framing.
        val job = BmwJob(
            id = "mfl_inject_${event.label}",
            label = "MFL inject: ${event.label}",
            category = JobCategory.CONTROL,
            steps = listOf(
                JobStep(
                    serviceId = 0x30,
                    payload = event.kwpPayload,
                    label = "MFL 0x30 ${event.label}",
                )
            ),
            description = "Inject MFL button event '${event.label}' to KOMBI via KWP 0x30.",
            readOnly = false,
            supportedTargets = setOf(BmwTargets.KOMBI.name),
        )

        return if (dryRun) {
            // Describe the frame without transmitting
            val payloadHex = event.kwpPayload.joinToString(" ") { "0x%02X".format(it) }
            InjectionResult(
                event = event,
                frameHex = "30 ${payloadHex} [dry-run, not sent]",
                responseHex = "",
                success = true,
                note = "Dry-run: frame built but not transmitted.",
            )
        } else {
            val result = session.execute(job)
            InjectionResult(
                event = event,
                frameHex = result.requestHex,
                responseHex = result.responseHex,
                success = result.success,
                note = result.summary,
            )
        }
    }

    /**
     * Inject all MFL events from an [SzlButtonFrame] in one call.
     * Returns one [InjectionResult] per event (may be empty if no buttons are pressed).
     */
    suspend fun injectFrame(
        session: KdcanSession,
        frame: SzlButtonFrame,
        dryRun: Boolean = false,
    ): List<InjectionResult> {
        return frame.mflEvents.map { event -> inject(session, event, dryRun) }
    }

    /** Format a list of injection results as a compact log line for the UI. */
    fun summarise(results: List<InjectionResult>): String {
        if (results.isEmpty()) return "No buttons active"
        return results.joinToString(" | ") { r ->
            "${r.event.label}: ${if (r.success) "OK" else "FAIL"}"
        }
    }
}
