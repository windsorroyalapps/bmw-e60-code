package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.CodingChange
import com.bmwe60coderpro.data.DatenDocument

enum class SteeringRetrofitPresetKind {
    F_SERIES_BASIC,
    F_SERIES_HEATED,
    F_SERIES_PADDLES,
    F_SERIES_FULL,
}

data class RetrofitModulePatch(
    val module: String,
    val description: String,
    val changes: List<CodingChange>,
)

data class SteeringRetrofitPreset(
    val kind: SteeringRetrofitPresetKind,
    val label: String,
    val description: String,
    val modulePatches: List<RetrofitModulePatch>,
    val validationChecklist: List<String>,
)

object SteeringRetrofitManager {
    val presets = listOf(
        SteeringRetrofitPreset(
            kind = SteeringRetrofitPresetKind.F_SERIES_BASIC,
            label = "F-series wheel basic",
            description = "Prepare SZL and KOMBI style coding for F-series multifunction wheel recognition on E-series platform.",
            modulePatches = listOf(
                RetrofitModulePatch(
                    module = "SZL",
                    description = "Primary steering-column switch center changes.",
                    changes = listOf(
                        CodingChange("SZL", "SZL_LENKRAD_TYP", "F_SERIE"),
                        CodingChange("SZL", "MULTIFUNKTION", "aktiv"),
                        CodingChange("SZL", "SPORT_TASTE", "aktiv"),
                        CodingChange("SZL", "TELEFON_TASTE", "aktiv"),
                    ),
                ),
                RetrofitModulePatch(
                    module = "KOMBI",
                    description = "Cluster-side multifunction interpretation enablement.",
                    changes = listOf(CodingChange("KOMBI", "MFL", "aktiv")),
                ),
            ),
            validationChecklist = listOf(
                "Read and save original SZL trace before patching.",
                "Read and save original KOMBI trace before patching.",
                "After coding, verify horn, MFL buttons, telephone key, and sport button input blocks on the SZL service page.",
                "If buttons are present but actions are missing, re-check KOMBI and gateway side coding assumptions before changing more parameters.",
            ),
        ),
        SteeringRetrofitPreset(
            kind = SteeringRetrofitPresetKind.F_SERIES_HEATED,
            label = "F-series heated wheel",
            description = "Adds heating-related flag preparation on top of basic F-series wheel support.",
            modulePatches = listOf(
                RetrofitModulePatch(
                    module = "SZL",
                    description = "Steering wheel heating and button interpretation.",
                    changes = listOf(
                        CodingChange("SZL", "SZL_LENKRAD_TYP", "F_SERIE"),
                        CodingChange("SZL", "LENKRAD_HEIZUNG", "aktiv"),
                        CodingChange("SZL", "MULTIFUNKTION", "aktiv"),
                        CodingChange("SZL", "TELEFON_TASTE", "aktiv"),
                    ),
                ),
                RetrofitModulePatch(
                    module = "KOMBI",
                    description = "Cluster acknowledgement for multifunction wheel inputs.",
                    changes = listOf(CodingChange("KOMBI", "MFL", "aktiv")),
                ),
            ),
            validationChecklist = listOf(
                "Confirm wheel heating hardware is actually present before leaving LENKRAD_HEIZUNG active.",
                "Verify no new SZL fault is created after coding.",
                "Check heated-wheel and MFL button state on the SZL service page after ignition cycle.",
            ),
        ),
        SteeringRetrofitPreset(
            kind = SteeringRetrofitPresetKind.F_SERIES_PADDLES,
            label = "F-series paddles",
            description = "Prepare paddle-related coding on top of steering-wheel multifunction support.",
            modulePatches = listOf(
                RetrofitModulePatch(
                    module = "SZL",
                    description = "Wheel type and multifunction input decode.",
                    changes = listOf(
                        CodingChange("SZL", "SZL_LENKRAD_TYP", "F_SERIE"),
                        CodingChange("SZL", "MULTIFUNKTION", "aktiv"),
                    ),
                ),
                RetrofitModulePatch(
                    module = "KOMBI",
                    description = "Cluster side MFL enablement.",
                    changes = listOf(CodingChange("KOMBI", "MFL", "aktiv")),
                ),
                RetrofitModulePatch(
                    module = "EGS",
                    description = "Gearbox paddle interpretation flag.",
                    changes = listOf(CodingChange("EGS", "EGS_SCHALTWIPPEN", "aktiv")),
                ),
            ),
            validationChecklist = listOf(
                "Save original EGS trace before enabling paddle flags.",
                "Verify wheel button and paddle transitions in the SZL live page before road testing.",
                "Verify gear requests on the EGS service page with ignition on and drivetrain stationary.",
            ),
        ),
        SteeringRetrofitPreset(
            kind = SteeringRetrofitPresetKind.F_SERIES_FULL,
            label = "F-series full retrofit pack",
            description = "Bundles basic, telephone, sport, heating, and paddle-oriented coding hints into one exportable local patch set.",
            modulePatches = listOf(
                RetrofitModulePatch(
                    module = "SZL",
                    description = "Main SZL retrofit patch set.",
                    changes = listOf(
                        CodingChange("SZL", "SZL_LENKRAD_TYP", "F_SERIE"),
                        CodingChange("SZL", "MULTIFUNKTION", "aktiv"),
                        CodingChange("SZL", "SPORT_TASTE", "aktiv"),
                        CodingChange("SZL", "TELEFON_TASTE", "aktiv"),
                        CodingChange("SZL", "LENKRAD_HEIZUNG", "aktiv"),
                    ),
                ),
                RetrofitModulePatch(
                    module = "KOMBI",
                    description = "Cluster-side wheel button handling.",
                    changes = listOf(CodingChange("KOMBI", "MFL", "aktiv")),
                ),
                RetrofitModulePatch(
                    module = "EGS",
                    description = "Gearbox paddle support.",
                    changes = listOf(CodingChange("EGS", "EGS_SCHALTWIPPEN", "aktiv")),
                ),
            ),
            validationChecklist = listOf(
                "Back up SZL, KOMBI, and EGS coding first.",
                "Code one module at a time and cycle ignition between modules.",
                "Use the SZL service page to confirm button matrix changes before testing paddle behavior.",
                "Use the EGS page to confirm gear-request data before any dynamic test.",
                "If the heated-wheel function is not wired, remove LENKRAD_HEIZUNG from the exported bundle before writing.",
            ),
        ),
    )

    private val templates = mapOf(
        "SZL" to "SZL {
  SZL_LENKRAD_TYP = E_SERIE;
  MULTIFUNKTION = nicht_aktiv;
  SPORT_TASTE = nicht_aktiv;
  TELEFON_TASTE = nicht_aktiv;
  LENKRAD_HEIZUNG = nicht_aktiv;
}
",
        "KOMBI" to "KOMBI {
  MFL = nicht_aktiv;
}
",
        "EGS" to "EGS {
  EGS_SCHALTWIPPEN = nicht_aktiv;
}
",
    )

    fun preset(kind: SteeringRetrofitPresetKind): SteeringRetrofitPreset = presets.first { it.kind == kind }

    fun renderModulePatch(module: String, kind: SteeringRetrofitPresetKind? = null): String {
        val base = DatenManager.parse(templates[module] ?: "$module {
}
")
        val values = base.values.toMutableMap()
        if (kind != null) {
            preset(kind).modulePatches
                .filter { it.module == module }
                .flatMap { it.changes }
                .forEach { values[it.parameter] = it.value }
        }
        return DatenManager.render(DatenDocument(base.module, values))
    }

    fun exportBundle(kind: SteeringRetrofitPresetKind): String {
        val p = preset(kind)
        return buildString {
            append("Retrofit pack: ").append(p.label).append("
")
            append(p.description).append("

")
            p.modulePatches.forEach { patch ->
                append(patch.module).append("
")
                append(renderModulePatch(patch.module, kind)).append("
")
            }
            append("Validation checklist
")
            p.validationChecklist.forEachIndexed { index, item ->
                append(index + 1).append(". ").append(item).append("
")
            }
        }
    }

    fun preview(kind: SteeringRetrofitPresetKind): String {
        val p = preset(kind)
        return buildString {
            append(p.label).append(": ").append(p.description).append("

")
            p.modulePatches.forEach { patch ->
                append("[").append(patch.module).append("] ").append(patch.description).append("
")
                patch.changes.forEach { change -> append("- ").append(change.parameter).append(" = ").append(change.value).append("
") }
                append("
")
            }
            append("Validation
")
            p.validationChecklist.forEach { append("- ").append(it).append("
") }
        }
    }
}
