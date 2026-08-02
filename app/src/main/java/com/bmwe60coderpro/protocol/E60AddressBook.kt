package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.AdapterPresetKind
import com.bmwe60coderpro.data.ConnectionProfile
import com.bmwe60coderpro.data.VehicleProfileKind

data class VehicleProfile(
    val kind: VehicleProfileKind,
    val label: String,
    val notes: String,
    val recommendedPreset: AdapterPresetKind,
)

data class TargetDefinition(
    val name: String,
    val address: Int,
    val notes: String,
)

object E60AddressBook {
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
    )

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
    )

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
        },
        VehicleProfileKind.E39_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x00, notes = "E39 has no CAS — use EWS bypass via DME")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "E39 DME with EWS3 integration")
                else -> it
            }
        },
        VehicleProfileKind.F10_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "CAS4 on F10 — gateway routed")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "F10 N55/N20 DME via gateway")
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
        VehicleProfileKind.F30_GENERIC to genericTargets.map {
            when (it.name) {
                BmwTargets.CAS.name -> it.copy(address = 0x40, notes = "FEM/BDC on F30 — gateway routed")
                BmwTargets.DME.name -> it.copy(address = 0x12, notes = "F30 N20/N26 DME via gateway")
                else -> it
            }
        },
    )

    fun byKind(kind: VehicleProfileKind): VehicleProfile = profiles.first { it.kind == kind }

    fun targetsFor(kind: VehicleProfileKind): List<EcuTarget> {
        return byProfile[kind].orEmpty().map { EcuTarget(it.name, it.address) }
    }

    fun describeTarget(kind: VehicleProfileKind, targetName: String): String {
        val entry = byProfile[kind].orEmpty().firstOrNull { it.name == targetName }
        return entry?.let { "0x${it.address.toString(16).uppercase()} — ${it.notes}" } ?: "Unknown target"
    }

    fun applyRecommendedPreset(profile: ConnectionProfile): ConnectionProfile {
        val recommended = byKind(profile.vehicleProfile).recommendedPreset
        return profile.copy(adapterPreset = recommended)
    }
}
