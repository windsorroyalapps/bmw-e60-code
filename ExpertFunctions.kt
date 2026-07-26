package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.VehicleProfileKind

data class TuneProfile(
    val name: String,
    val description: String,
    val cccSummary: String,
    val dmeHint: String,
)

data class SteeringProfile(
    val name: String,
    val description: String,
    val codingHints: List<String>,
)

object ExpertFunctions {
    val tuneProfiles = listOf(
        TuneProfile("Comfort", "Softer throttle / smoother response baseline.", "CCC comfort slot prepared", "DME target torque limiter baseline"),
        TuneProfile("Sport", "Sharper throttle with normal safety ceilings.", "CCC sport slot prepared", "DME torque request and pedal tables flagged"),
        TuneProfile("Race", "Track-focused placeholder profile for off-road use only.", "CCC race slot prepared", "DME high-response profile exported as notes"),
        TuneProfile("Custom", "User-defined map slot notes.", "CCC custom slot prepared", "Custom DME map pack notes only"),
    )

    val steeringProfiles = listOf(
        SteeringProfile("F-series wheel basic", "Prepare SZL/KOMBI coding hints for F-series wheel recognition.", listOf("SZL_LENKRAD_TYP = F_SERIE", "MULTIFUNKTION = aktiv", "SPORT_TASTE = aktiv")),
        SteeringProfile("Heated wheel", "Prepare heating and MFL hints.", listOf("LENKRAD_HEIZUNG = aktiv", "MULTIFUNKTION = aktiv")),
        SteeringProfile("Paddle retrofit", "Prepare paddle / wheel button retrofit notes.", listOf("EGS_SCHALTWIPPEN = aktiv", "MFL = aktiv")),
    )

    fun tuneSummary(slot: String, profile: VehicleProfileKind): String {
        val tune = tuneProfiles.firstOrNull { it.name.equals(slot, true) } ?: tuneProfiles[1]
        return "${tune.name} for ${profile.name}: ${tune.description} | ${tune.cccSummary} | ${tune.dmeHint}"
    }

    fun steeringSummary(profileName: String): String {
        val p = steeringProfiles.firstOrNull { it.name == profileName } ?: steeringProfiles.first()
        return buildString {
            append(p.name).append(": ").append(p.description)
            append(" | ")
            append(p.codingHints.joinToString("; "))
        }
    }
}
