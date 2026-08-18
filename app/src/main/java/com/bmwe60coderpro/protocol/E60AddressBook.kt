package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.AdapterPresetKind
import com.bmwe60coderpro.data.ConnectionProfile
import com.bmwe60coderpro.data.VehicleProfileKind

/**
 * Represents a specific vehicle configuration including its diagnostic characteristics.
 *
 * @property kind The unique identifier for this vehicle profile.
 * @property label A user-friendly display name for the profile.
 * @property notes Additional technical details or warnings for this specific profile.
 * @property recommendedPreset The preferred communication adapter settings for this vehicle.
 */
data class VehicleProfile(
    val kind: VehicleProfileKind,
    val label: String,
    val notes: String,
    val recommendedPreset: AdapterPresetKind,
)

/**
 * Defines a diagnostic target (ECU) within a vehicle.
 *
 * @property name The identifier name of the ECU (e.g., DME, CAS).
 * @property address The hex address used to communicate with this ECU on the bus.
 * @property notes Descriptive information about this specific target definition.
 */
data class TargetDefinition(
    val name: String,
    val address: Int,
    val notes: String,
)

/**
 * A registry of known BMW vehicle profiles and their associated ECU target addresses.
 * Provides lookup mechanisms for vehicle-specific diagnostic configurations.
 */
object E60AddressBook {
    /**
     * The master list of supported vehicle profiles across E-series and F-series models.
     */
    val profiles = listOf(
        VehicleProfile(
            kind = VehicleProfileKind.GENERIC_E60,
            label = "Generic E60 / E61",
            notes = "Safe generic E60 target map for mixed chassis and unknown ECU family.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.N52_6HP,
            label = "E60 N52 + 6HP",
            notes = "Good default for 525i/530i-era N52 cars with ZF 6HP EGS.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.N54_6HP,
            label = "E60 N54 + 6HP",
            notes = "Turbo petrol profile with slightly more conservative DME timing.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.M57_6HP,
            label = "E60 M57 + 6HP",
            notes = "Diesel-focused profile for M57 DDE variants and ZF 6HP.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.N62_6HP,
            label = "E60 N62 + 6HP",
            notes = "V8 profile with slower startup and safer retries on engine diagnostics.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E90_GENERIC,
            label = "E90 / E91 / E92 / E93",
            notes = "3 Series with CAS2/CAS3. Force ignition via CAS terminal control.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E46_GENERIC,
            label = "E46 3 Series",
            notes = "EWS3/MSK-based. No CAS; force ignition uses EWS bypass sequence.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E39_GENERIC,
            label = "E39 5 Series",
            notes = "EWS3-based with K-bus. Force ignition via EWS bypass.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.F10_GENERIC,
            label = "F10 / F11 5 Series",
            notes = "F-series with CAS4/FEM. Uses ENET gateway for force ignition.",
            recommendedPreset = AdapterPresetKind.ETH_ENET,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E70_GENERIC,
            label = "E70 X5",
            notes = "X5 with CAS3. Force ignition via CAS3 terminal control.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E71_GENERIC,
            label = "E71 X6",
            notes = "X6 with CAS3. Force ignition via CAS3 terminal control.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E87_GENERIC,
            label = "E81 / E87 / E82 / E88",
            notes = "1 Series with CAS2/CAS3. Force ignition via CAS terminal control.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E92_N54,
            label = "E92 335i N54",
            notes = "E92 coupe with N54 and CAS3. Turbo-specific force ignition profile.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.F30_GENERIC,
            label = "F30 / F31 3 Series",
            notes = "F30 with FEM/BDC. Uses ENET for force ignition and diagnostics.",
            recommendedPreset = AdapterPresetKind.ETH_ENET,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E53_GENERIC,
            label = "E53 X5",
            notes = "X5 E53 with EWS3. No CAS; force ignition uses EWS bypass.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E65_GENERIC,
            label = "E65 / E66 7 Series",
            notes = "7 Series E65/E66. CAS1/CAS2 via ZGM. Use FTDI SAFE.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E89_GENERIC,
            label = "E89 Z4",
            notes = "Z4 E89 with CAS3+. Force ignition via CAS terminal control.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_FAST,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.G30_GENERIC,
            label = "G30 5 Series",
            notes = "G30 with BDC. Uses ENET for diagnostics and force ignition.",
            recommendedPreset = AdapterPresetKind.ETH_ENET,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E83_GENERIC,
            label = "E83 X3",
            notes = "X3 E83 with EWS3. No CAS; force ignition uses EWS bypass.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E85_GENERIC,
            label = "E85 Z4",
            notes = "Z4 E85 with EWS3. No CAS; force ignition uses EWS bypass.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.E38_GENERIC,
            label = "E38 7 Series",
            notes = "7 Series E38. Older K-bus/I-bus diagnostics. Use FTDI SAFE.",
            recommendedPreset = AdapterPresetKind.USB_FTDI_SAFE,
        ),
        VehicleProfile(
            kind = VehicleProfileKind.F20_GENERIC,
            label = "F20 / F21 1 Series",
            notes = "F20 with FEM. Uses ENET for diagnostics and force ignition.",
            recommendedPreset = AdapterPresetKind.ETH_ENET,
        ),
    )

    /**
     * Common ECU targets and their default addresses used as a baseline for most profiles.
     */
    private val genericTargets = listOf(
        TargetDefinition(BmwTargets.DME.name, 0x12, "Engine ECU default BMW target address"),
        TargetDefinition(BmwTargets.EGS.name, 0x32, "ZF gearbox controller default target address"),
        TargetDefinition(BmwTargets.DSC.name, 0x56, "DSC / ABS target address"),
        TargetDefinition(BmwTargets.KOMBI.name, 0x80, "Instrument cluster target address"),
        TargetDefinition(BmwTargets.SZL.name, 0x5E, "Steering column switch center"),
        TargetDefinition(BmwTargets.CAS.name, 0x40, "Car access system"),
        TargetDefinition(BmwTargets.FRM.name,  0x60, "FRM / LM lighting module"),
        TargetDefinition(BmwTargets.ACSM.name, 0x57, "Airbag / ACSM module (Advanced Crash Safety Manager)"),
        TargetDefinition(BmwTargets.CCC.name,  0x68, "CCC Car Communication Computer"),
        TargetDefinition(BmwTargets.EWS.name,  0x44, "EWS Immobilizer (E39/E46/E53)"),
        TargetDefinition(BmwTargets.LSZ.name,  0xD0, "Light Switch Center (E46) / LCM (E39)"),
        TargetDefinition(BmwTargets.GM.name,   0x00, "General Module (ZKE)"),
    )

    private val fSeriesTargets = genericTargets.filter {
        it.name != BmwTargets.EWS.name && it.name != BmwTargets.LSZ.name && it.name != BmwTargets.GM.name && it.name != BmwTargets.CCC.name
    } + listOf(
        TargetDefinition(BmwTargets.ZGM.name, 0x10, "Central Gateway Module (ZGM)"),
        TargetDefinition(BmwTargets.FEM.name, 0x40, "Front Electronics Module / BDC (FEM/BDC)"),
        TargetDefinition("HU_ENTRY", 0x63, "Headunit Entry / NBT"),
    )

    /**
     * Maps vehicle kinds to their specific list of [TargetDefinition]s, allowing for
     * per-model overrides of ECU addresses or notes.
     */
    private val byProfile: Map<VehicleProfileKind, List<TargetDefinition>> = mapOf(
        VehicleProfileKind.GENERIC_E60 to genericTargets,
        VehicleProfileKind.N52_6HP to genericTargets,
        VehicleProfileKind.N54_6HP to genericTargets.map {
            when (it.name) {
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "N54 MSD80/MSD81 commonly responds on the usual engine address")
                else -> it
            }
        },
        VehicleProfileKind.M57_6HP to genericTargets.map {
            when (it.name) {
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "Diesel DDE target address")
                else -> it
            }
        },
        VehicleProfileKind.N62_6HP to genericTargets.map {
            when (it.name) {
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "N62 DMEs use the standard engine target here")
                else -> it
            }
        },
        VehicleProfileKind.E90_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS3 on E90 — force ignition supported")
                else -> it
            }
        },
        VehicleProfileKind.E92_N54 to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS3 on E92 — force ignition supported")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "N54 MSD80/MSD81 on E92")
                else -> it
            }
        },
        VehicleProfileKind.E46_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E46 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E46 DME with EWS3 integration")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.EWS.name, 0x44, "E46 EWS3 Immobilizer"),
            TargetDefinition(BmwTargets.LSZ.name, 0xD0, "E46 Light Switch Center (LSZ)")
        ),
        VehicleProfileKind.E39_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E39 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E39 DME with EWS3 integration")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.EWS.name, 0x44, "E39 EWS3 Immobilizer"),
            TargetDefinition(BmwTargets.LSZ.name, 0xD0, "E39 Light Check Module (LCM)")
        ),
        VehicleProfileKind.F10_GENERIC to fSeriesTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS4 on F10 — gateway routed")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "F10 N55/N20/N63 DME via gateway")
                else -> it
            }
        },
        VehicleProfileKind.E70_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS3 on E70 X5")
                else -> it
            }
        },
        VehicleProfileKind.E71_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS3 on E71 X6")
                else -> it
            }
        },
        VehicleProfileKind.E87_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS2/CAS3 on E87")
                else -> it
            }
        },
        VehicleProfileKind.F30_GENERIC to fSeriesTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "FEM/BDC on F30 — gateway routed")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "F30 N20/N26/B48 DME via gateway")
                else -> it
            }
        },
        VehicleProfileKind.E53_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E53 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E53 DME with EWS3 integration")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.EWS.name, 0x44, "E53 EWS3 Immobilizer"),
            TargetDefinition(BmwTargets.LSZ.name, 0xD0, "E53 Light Check Module (LCM)")
        ),
        VehicleProfileKind.E65_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS1/CAS2 on E65 via ZGM")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.ZGM.name, 0x10, "E65 Central Gateway Module")
        ),
        VehicleProfileKind.E89_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS3+ on E89")
                else -> it
            }
        },
        VehicleProfileKind.G30_GENERIC to fSeriesTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "BDC on G30 — gateway routed")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "G30 B58/B48/B57 DME via gateway")
                else -> it
            }
        },
        VehicleProfileKind.E83_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E83 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E83 DME with EWS3 integration")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.EWS.name, 0x44, "E83 EWS3 Immobilizer"),
            TargetDefinition(BmwTargets.LSZ.name, 0xD0, "E83 Light Switch Center (LSZ)")
        ),
        VehicleProfileKind.E85_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E85 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E85 DME with EWS3 integration")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.EWS.name, 0x44, "E85 EWS3 Immobilizer"),
            TargetDefinition(BmwTargets.LSZ.name, 0xD0, "E85 Light Switch Center (LSZ)")
        ),
        VehicleProfileKind.E38_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E38 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E38 DME with EWS2/3 integration")
                else -> it
            }
        } + listOf(
            TargetDefinition(BmwTargets.EWS.name, 0x44, "E38 EWS Immobilizer"),
            TargetDefinition(BmwTargets.LSZ.name, 0xD0, "E38 Light Check Module (LCM)")
        ),
        VehicleProfileKind.F20_GENERIC to fSeriesTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "FEM on F20 — gateway routed")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "F20 N13/N20/B48 DME via gateway")
                else -> it
            }
        },
    )

    /**
     * Retrieves a [VehicleProfile] by its [VehicleProfileKind].
     * @throws NoSuchElementException if no profile matches the kind.
     */
    fun byKind(kind: VehicleProfileKind): VehicleProfile = profiles.first { it.kind == kind }

    /**
     * Returns a list of [EcuTarget]s available for the specified vehicle kind.
     */
    fun targetsFor(kind: VehicleProfileKind): List<EcuTarget> {
        return byProfile[kind].orEmpty().map { EcuTarget(it.name, it.address) }
    }

    /**
     * Provides a human-readable description of a specific ECU target for a given vehicle.
     * Includes the hex address and technical notes.
     */
    fun describeTarget(kind: VehicleProfileKind, targetName: String): String {
        val entry = byProfile[kind].orEmpty().firstOrNull { it.name == targetName }
        return entry?.let { "0x${it.address.toString(16).uppercase()} — ${it.notes}" } ?: "Unknown target"
    }
}
