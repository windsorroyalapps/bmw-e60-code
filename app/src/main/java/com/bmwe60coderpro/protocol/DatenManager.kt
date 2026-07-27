package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.CodingChange
import com.bmwe60coderpro.data.CodingPreset
import com.bmwe60coderpro.data.CodingPresetKind
import com.bmwe60coderpro.data.DatenDocument

import com.bmwe60coderpro.util.HexUtils

object DatenManager {
    val presets = listOf(
        CodingPreset(
            kind = CodingPresetKind.DIGITAL_SPEED,
            label = "Digital speed in cluster",
            description = "Enable digital speed style display in KOMBI.",
            changes = listOf(CodingChange("KOMBI", "DIGITAL_V", "aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.SEATBELT_CHIME_OFF,
            label = "Seatbelt chime off",
            description = "Relax seatbelt gong behavior in KOMBI.",
            changes = listOf(CodingChange("KOMBI", "GURTWARNUNG", "nicht_aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.BULB_CHECKS_RELAXED,
            label = "Bulb checks relaxed",
            description = "Reduce cold/warm bulb check annoyance in FRM/LM.",
            changes = listOf(
                CodingChange("FRM", "KALTUEBERWACHUNG", "nicht_aktiv"),
                CodingChange("FRM", "WARMUEBERWACHUNG", "nicht_aktiv"),
            ),
        ),
        CodingPreset(
            kind = CodingPresetKind.DISCLAIMER_OFF,
            label = "CCC disclaimer off",
            description = "Suppress CCC startup disclaimer style prompt.",
            changes = listOf(CodingChange("CCC", "LEGAL_DISCLAIMER", "nicht_aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.MFL_ENABLE,
            label = "MFL enable",
            description = "Enable multifunction wheel interpretation in SZL/KOMBI.",
            changes = listOf(
                CodingChange("SZL", "MULTIFUNKTION", "aktiv"),
                CodingChange("KOMBI", "MFL", "aktiv"),
            ),
        ),
        CodingPreset(
            kind = CodingPresetKind.SPORT_BUTTON_ENABLE,
            label = "Sport button enable",
            description = "Enable sport button flag where supported.",
            changes = listOf(CodingChange("SZL", "SPORT_TASTE", "aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.WELCOME_LIGHTS,
            label = "Welcome lights",
            description = "Enable welcome-light style options in FRM.",
            changes = listOf(CodingChange("FRM", "WELCOME_LIGHTS", "aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.AIRBAG_OCCUPANCY_OFF,
            label = "Airbag occupancy sensor disable (ACSM)",
            description = "Disable passenger occupancy classification warning in ACSM.",
            changes = listOf(CodingChange("ACSM", "OC3_WARNLEUCHTE", "nicht_aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.AIRBAG_SBR_OFF,
            label = "Seatbelt reminder / ABG warning off (ACSM)",
            description = "Disable seatbelt reminder chime and warning light from ACSM side.",
            changes = listOf(
                CodingChange("ACSM", "SBR_WARNUNG", "nicht_aktiv"),
                CodingChange("ACSM", "GURTSTRAFFER_WARNUNG", "nicht_aktiv"),
            ),
        ),
        CodingPreset(
            kind = CodingPresetKind.CCC_DISCLAIMER_OFF,
            label = "CCC startup disclaimer off",
            description = "Suppress the CCC navigation/iDrive startup disclaimer.",
            changes = listOf(CodingChange("CCC", "LEGAL_DISCLAIMER", "nicht_aktiv")),
        ),
        CodingPreset(
            kind = CodingPresetKind.CBS_RESET_PREPARE,
            label = "CBS reset prepare (KOMBI)",
            description = "Prepare KOMBI CBS counter coding hints for service interval reset.",
            changes = listOf(
                CodingChange("KOMBI", "CBS_ZURUECKSETZEN", "aktiv"),
                CodingChange("KOMBI", "SERVICE_INTERVALL_ANZEIGE", "aktiv"),
            ),
        ),
        CodingPreset(
            kind = CodingPresetKind.WARNING_SUPPRESSION_TRACK,
            label = "Track warning suppression pack",
            description = "Prepare a conservative track-use coding profile.",
            changes = listOf(
                CodingChange("KOMBI", "GURTWARNUNG", "nicht_aktiv"),
                CodingChange("ACSM", "SBR_WARNUNG", "nicht_aktiv"),
                CodingChange("CCC", "LEGAL_DISCLAIMER", "nicht_aktiv"),
            ),
        ),
    )

    fun preset(kind: CodingPresetKind): CodingPreset = presets.first { it.kind == kind }

    fun parse(text: String): DatenDocument {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val module = lines.firstOrNull()?.substringBefore("{")?.trim().orEmpty().ifBlank { "CUSTOM" }
        val values = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            if (line.startsWith("}")) return@forEach
            val clean = line.removeSuffix(";")
            val parts = clean.split("=").map { it.trim() }
            if (parts.size == 2) values[parts[0]] = parts[1]
        }
        return DatenDocument(module, values)
    }

    fun render(document: DatenDocument): String = buildString {
        append(document.module).append(" {\n")
        document.values.forEach { (k, v) -> append("  ").append(k).append(" = ").append(v).append(";\n") }
        append("}\n")
    }

    fun applyPreset(text: String, preset: CodingPreset): String {
        val current = parse(text)
        val values = current.values.toMutableMap()
        preset.changes.forEach { change ->
            if (change.module.equals(current.module, ignoreCase = true) || current.module == "CUSTOM") {
                values[change.parameter] = change.value
            }
        }
        val targetModule = preset.changes.firstOrNull()?.module ?: current.module
        return render(DatenDocument(if (current.module == "CUSTOM") targetModule else current.module, values))
    }

    private val moduleTemplates = mapOf(
        "DME" to "DME {\n  DIGITAL_V = nicht_aktiv;\n}\n",
        "KOMBI" to "KOMBI {\n  DIGITAL_V = nicht_aktiv;\n  GURTWARNUNG = aktiv;\n  MFL = nicht_aktiv;\n  CBS_ZURUECKSETZEN = nicht_aktiv;\n  SERVICE_INTERVALL_ANZEIGE = aktiv;\n}\n",
        "FRM" to "FRM {\n  KALTUEBERWACHUNG = aktiv;\n  WARMUEBERWACHUNG = aktiv;\n  WELCOME_LIGHTS = nicht_aktiv;\n}\n",
        "SZL" to "SZL {\n  SZL_LENKRAD_TYP = E_SERIE;\n  MULTIFUNKTION = nicht_aktiv;\n  SPORT_TASTE = nicht_aktiv;\n  TELEFON_TASTE = nicht_aktiv;\n  LENKRAD_HEIZUNG = nicht_aktiv;\n}\n",
        "EGS" to "EGS {\n  EGS_SCHALTWIPPEN = nicht_aktiv;\n}\n",
        "CAS" to "CAS {\n  START_STOP_TASTE = nicht_aktiv;\n}\n",
        "ACSM" to "ACSM {\n  OC3_WARNLEUCHTE = aktiv;\n  SBR_WARNUNG = aktiv;\n  GURTSTRAFFER_WARNUNG = aktiv;\n}\n",
        "CCC" to "CCC {\n  LEGAL_DISCLAIMER = aktiv;\n}\n",
    )

    fun templateFor(module: String): String = moduleTemplates[module.uppercase()] ?: "$module {\n}\n"

    /** Build a KWP 0x3B coding write payload from a DatenDocument.
     *  The encoding used here is the plain ASCII text record format accepted by
     *  many E60-era modules via local ID 0x9B: each parameter is encoded as a
     *  length-prefixed ASCII key=value pair. This is a simplified representation;
     *  real modules may require binary-coded values — always verify with a read-back. */
    fun buildCodingWritePayload(doc: DatenDocument): List<Int> {
        val text = render(doc)
        return text.map { it.code and 0xFF }
    }

    /** Parse an ASCII coding record returned by 0x1A 0x9B back into a DatenDocument.
     *  If the response is not bracketed text, we assume it's a binary record (NETTODAT). */
    fun parseCodingRecord(module: String, asciiResponse: String): DatenDocument {
        return if (asciiResponse.contains("{") && asciiResponse.contains("}")) {
            parse(asciiResponse)
        } else {
            // Treat as binary/hex record
            val values = linkedMapOf<String, String>()
            val bytes = asciiResponse.map { it.code.toByte() }.toByteArray()
            val hex = HexUtils.bytesToHex(bytes).replace(" ", "")
            values["raw_payload_hex"] = hex

            // Heuristic: If it's a short record, it might be a single parameter
            if (bytes.size < 8) {
                values["data_value"] = "0x" + hex
            } else {
                // Chunk into 4-byte parameters if possible
                bytes.toList().chunked(4).forEachIndexed { i, chunk ->
                    val chunkHex = HexUtils.bytesToHex(chunk.toByteArray()).replace(" ", "")
                    values["param_$i"] = "0x$chunkHex"
                }
            }
            DatenDocument(module, values)
        }
    }

    fun previewPatch(text: String, preset: CodingPreset): String {
        val before = parse(text).values
        return preset.changes.joinToString("\n") { change ->
            val oldValue = before[change.parameter] ?: "<unset>"
            "${change.module}.${change.parameter}: $oldValue -> ${change.value}"
        }
    }
}
