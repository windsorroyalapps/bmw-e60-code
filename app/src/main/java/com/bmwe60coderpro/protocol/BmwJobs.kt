package com.bmwe60coderpro.protocol

/**
 * BMW-oriented KWP/K-line style jobs.
 */

data class EcuTarget(
    val name: String,
    val targetAddress: Int,
)

enum class JobCategory { SESSION, IDENTIFICATION, FAULTS, LIVE_DATA, CONTROL, CUSTOM, MODULE_PACK }

data class JobStep(
    val serviceId: Int,
    val payload: List<Int> = emptyList(),
    val label: String,
)

data class BmwJob(
    val id: String,
    val label: String,
    val category: JobCategory,
    val steps: List<JobStep>,
    val description: String,
    val readOnly: Boolean = true,
    val supportedTargets: Set<String> = emptySet(),
) {
    val primaryStep: JobStep get() = steps.first()

    fun appliesTo(target: EcuTarget): Boolean {
        return supportedTargets.isEmpty() || supportedTargets.contains(target.name)
    }
}

object BmwTargets {
    val DME = EcuTarget("DME / DDE", 0x12)
    val EGS = EcuTarget("EGS", 0x32)
    val DSC = EcuTarget("DSC", 0x56)
    val KOMBI = EcuTarget("KOMBI", 0x80)
    val SZL = EcuTarget("SZL", 0x5E)
    val CAS = EcuTarget("CAS", 0x40)
    val FRM = EcuTarget("FRM / LM", 0x60)

    val ACSM = EcuTarget("ACSM", 0x57)
    val CCC = EcuTarget("CCC", 0x68)

    val defaults = listOf(DME, EGS, DSC, KOMBI, SZL, CAS, FRM, ACSM, CCC)
}

private fun step(serviceId: Int, vararg payload: Int, label: String) =
    JobStep(serviceId = serviceId, payload = payload.toList(), label = label)

private fun standardSessionPack() = listOf(
    step(0x10, 0x81, label = "Default session"),
    step(0x3E, 0x00, label = "Tester present"),
)

object BmwJobs {
    private val generic = listOf(
        BmwJob(
            id = "start_session_default",
            label = "Start session (0x10 0x81)",
            category = JobCategory.SESSION,
            steps = listOf(step(0x10, 0x81, label = "Default session")),
            description = "Standard KWP start diagnostic session request.",
        ),
        BmwJob(
            id = "start_session_extended",
            label = "Start extended session (0x10 0x86)",
            category = JobCategory.SESSION,
            steps = listOf(step(0x10, 0x86, label = "Extended session")),
            description = "Often accepted by BMW-oriented diagnostic stacks for fuller access.",
        ),
        BmwJob(
            id = "tester_present",
            label = "Tester present (0x3E 0x00)",
            category = JobCategory.SESSION,
            steps = listOf(step(0x3E, 0x00, label = "Tester present")),
            description = "Keep diagnostic session alive.",
        ),
        BmwJob(
            id = "ecu_id_90",
            label = "Read ECU identification 0x90",
            category = JobCategory.IDENTIFICATION,
            steps = listOf(step(0x1A, 0x90, label = "ID 0x90")),
            description = "Generic ECU identification local identifier 0x90.",
        ),
        BmwJob(
            id = "ecu_id_9A",
            label = "Read ECU identification 0x9A",
            category = JobCategory.IDENTIFICATION,
            steps = listOf(step(0x1A, 0x9A, label = "ID 0x9A")),
            description = "Often used for supplier/software identification.",
        ),
        BmwJob(
            id = "ecu_id_9B",
            label = "Read ECU identification 0x9B",
            category = JobCategory.IDENTIFICATION,
            steps = listOf(step(0x1A, 0x9B, label = "ID 0x9B")),
            description = "Alternate identification record.",
        ),
        BmwJob(
            id = "faults_read",
            label = "Read stored DTCs",
            category = JobCategory.FAULTS,
            steps = listOf(step(0x18, 0x00, 0xFF, 0x00, label = "Read DTC block")),
            description = "Common KWP read-DTC-style query used by many modules.",
        ),
        BmwJob(
            id = "faults_clear",
            label = "Clear DTCs",
            category = JobCategory.FAULTS,
            steps = listOf(step(0x14, 0xFF, 0x00, label = "Clear DTCs")),
            description = "Clear stored fault memory. Use carefully.",
            readOnly = false,
        ),
        BmwJob(
            id = "read_vin_chunk",
            label = "Read VIN / ASCII block",
            category = JobCategory.IDENTIFICATION,
            steps = listOf(step(0x21, 0x81, label = "Read ASCII block 0x81")),
            description = "Module-specific local identifier often used for ASCII data blocks.",
        ),
        BmwJob(
            id = "read_status",
            label = "Read status block",
            category = JobCategory.LIVE_DATA,
            steps = listOf(step(0x21, 0x01, label = "Read status 0x01")),
            description = "Generic local identifier request for a small status block.",
        ),
    )

    private val serviceJobs = listOf(
        BmwJob(
            id = "dme_live_basic",
            label = "DME live data: basic",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "DME basic live 0x01")),
            description = "Read core DME live values such as RPM, temps, throttle and battery.",
            supportedTargets = setOf(BmwTargets.DME.name),
        ),
        BmwJob(
            id = "dme_live_air",
            label = "DME live data: air / torque",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x11, label = "DME air/torque live 0x11")),
            description = "Read DME air mass, torque or injection related values.",
            supportedTargets = setOf(BmwTargets.DME.name),
        ),
        BmwJob(
            id = "egs_live_basic",
            label = "EGS live data: gears / speeds",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "EGS basic live 0x01")),
            description = "Read EGS gear state and shaft speeds.",
            supportedTargets = setOf(BmwTargets.EGS.name),
        ),
        BmwJob(
            id = "egs_live_temp",
            label = "EGS live data: temp / lockup",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x12, label = "EGS temp live 0x12")),
            description = "Read EGS oil temperature and lockup style status.",
            supportedTargets = setOf(BmwTargets.EGS.name),
        ),
        BmwJob(
            id = "dsc_live_status",
            label = "DSC live data: vehicle status",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "DSC status 0x01")),
            description = "Read DSC vehicle speed and status flags.",
            supportedTargets = setOf(BmwTargets.DSC.name),
        ),
        BmwJob(
            id = "dsc_live_sensor",
            label = "DSC live data: steering / yaw",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x02, label = "DSC steering/yaw 0x02")),
            description = "Read DSC steering angle, yaw and lateral acceleration style values.",
            supportedTargets = setOf(BmwTargets.DSC.name),
        ),
        BmwJob(
            id = "dsc_live_wheels",
            label = "DSC live data: wheel speeds",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x11, label = "DSC wheel speeds 0x11")),
            description = "Read individual wheel speed values from DSC.",
            supportedTargets = setOf(BmwTargets.DSC.name),
        ),
        BmwJob(
            id = "kombi_live_drive",
            label = "KOMBI live data: speed / rpm",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "KOMBI drive block 0x01")),
            description = "Read cluster speed and RPM style values.",
            supportedTargets = setOf(BmwTargets.KOMBI.name),
        ),
        BmwJob(
            id = "kombi_live_cbs",
            label = "KOMBI live data: CBS / odometer",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x82, label = "KOMBI CBS block 0x82")),
            description = "Read cluster CBS and odometer style values.",
            supportedTargets = setOf(BmwTargets.KOMBI.name),
        ),
        BmwJob(
            id = "szl_live_switches",
            label = "SZL live data: switch states",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "SZL switch block 0x01")),
            description = "Read indicator, wiper and button flags from SZL.",
            supportedTargets = setOf(BmwTargets.SZL.name),
        ),
        BmwJob(
            id = "szl_live_angle",
            label = "SZL live data: angle / buttons",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x02, label = "SZL angle block 0x02")),
            description = "Read steering angle and button matrix style values.",
            supportedTargets = setOf(BmwTargets.SZL.name),
        ),
        BmwJob(
            id = "cas_live_terminals",
            label = "CAS live data: terminals / auth",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "CAS terminal block 0x01")),
            description = "Read terminal state, key presence and start authorization flags.",
            supportedTargets = setOf(BmwTargets.CAS.name),
        ),
        BmwJob(
            id = "cas_live_keyslot",
            label = "CAS live data: key slot / remote",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x82, label = "CAS keyslot block 0x82")),
            description = "Read key slot and remote-button style states.",
            supportedTargets = setOf(BmwTargets.CAS.name),
        ),
        BmwJob(
            id = "frm_live_status",
            label = "FRM live data: lighting / windows",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "FRM lighting block 0x01")),
            description = "Read lighting and window status flags.",
            supportedTargets = setOf(BmwTargets.FRM.name),
        ),
        BmwJob(
            id = "frm_live_outputs",
            label = "FRM live data: outputs",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x11, label = "FRM output block 0x11")),
            description = "Read FRM/LM output stage style values.",
            supportedTargets = setOf(BmwTargets.FRM.name),
        ),
    )

    private val moduleSpecific = listOf(
        BmwJob(
            id = "e60_dme_probe_pack",
            label = "E60 DME probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "DME identity 0x90"),
                step(0x1A, 0x9A, label = "DME software/supplier 0x9A"),
                step(0x21, 0x81, label = "DME VIN/ASCII block 0x81"),
                step(0x21, 0x01, label = "DME status block 0x01"),
                step(0x21, 0x11, label = "DME live block 0x11"),
                step(0x18, 0x00, 0xFF, 0x00, label = "DME read DTCs"),
            ),
            description = "E60-oriented DME workflow.",
            supportedTargets = setOf(BmwTargets.DME.name),
        ),
        BmwJob(
            id = "e60_egs_probe_pack",
            label = "E60 EGS probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "EGS identity 0x90"),
                step(0x1A, 0x9A, label = "EGS software/supplier 0x9A"),
                step(0x21, 0x01, label = "EGS status block 0x01"),
                step(0x21, 0x12, label = "EGS live block 0x12"),
                step(0x21, 0x81, label = "EGS ASCII/VIN block 0x81"),
                step(0x18, 0x00, 0xFF, 0x00, label = "EGS read DTCs"),
            ),
            description = "E60-oriented EGS workflow.",
            supportedTargets = setOf(BmwTargets.EGS.name),
        ),
        BmwJob(
            id = "e60_dsc_probe_pack",
            label = "E60 DSC probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "DSC identity 0x90"),
                step(0x1A, 0x9A, label = "DSC software/supplier 0x9A"),
                step(0x21, 0x01, label = "DSC status block 0x01"),
                step(0x21, 0x02, label = "DSC sensor block 0x02"),
                step(0x21, 0x11, label = "DSC live block 0x11"),
                step(0x18, 0x00, 0xFF, 0x00, label = "DSC read DTCs"),
            ),
            description = "E60-oriented DSC workflow.",
            supportedTargets = setOf(BmwTargets.DSC.name),
        ),
        BmwJob(
            id = "e60_kombi_probe_pack",
            label = "E60 KOMBI probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "KOMBI identity 0x90"),
                step(0x1A, 0x9A, label = "KOMBI software/supplier 0x9A"),
                step(0x21, 0x01, label = "KOMBI status block 0x01"),
                step(0x21, 0x81, label = "KOMBI ASCII/VIN block 0x81"),
                step(0x21, 0x82, label = "KOMBI service/CBS block 0x82"),
                step(0x18, 0x00, 0xFF, 0x00, label = "KOMBI read DTCs"),
            ),
            description = "E60-oriented KOMBI workflow.",
            supportedTargets = setOf(BmwTargets.KOMBI.name),
        ),
        BmwJob(
            id = "e60_szl_probe_pack",
            label = "E60 SZL probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "SZL identity 0x90"),
                step(0x1A, 0x9A, label = "SZL software/supplier 0x9A"),
                step(0x21, 0x01, label = "SZL switch state block 0x01"),
                step(0x21, 0x02, label = "SZL angle/button block 0x02"),
                step(0x21, 0x81, label = "SZL ASCII/VIN block 0x81"),
                step(0x18, 0x00, 0xFF, 0x00, label = "SZL read DTCs"),
            ),
            description = "E60-oriented SZL workflow.",
            supportedTargets = setOf(BmwTargets.SZL.name),
        ),
        BmwJob(
            id = "e60_cas_probe_pack",
            label = "E60 CAS probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "CAS identity 0x90"),
                step(0x1A, 0x9A, label = "CAS software/supplier 0x9A"),
                step(0x21, 0x01, label = "CAS terminal/key status block 0x01"),
                step(0x21, 0x81, label = "CAS VIN/ASCII block 0x81"),
                step(0x21, 0x82, label = "CAS key/slot block 0x82"),
                step(0x18, 0x00, 0xFF, 0x00, label = "CAS read DTCs"),
            ),
            description = "E60-oriented CAS workflow.",
            supportedTargets = setOf(BmwTargets.CAS.name),
        ),
        BmwJob(
            id = "e60_frm_probe_pack",
            label = "E60 FRM / LM probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "FRM identity 0x90"),
                step(0x1A, 0x9A, label = "FRM software/supplier 0x9A"),
                step(0x21, 0x01, label = "FRM status block 0x01"),
                step(0x21, 0x11, label = "FRM output block 0x11"),
                step(0x21, 0x81, label = "FRM ASCII/VIN block 0x81"),
                step(0x18, 0x00, 0xFF, 0x00, label = "FRM read DTCs"),
            ),
            description = "E60-oriented FRM / LM workflow.",
            supportedTargets = setOf(BmwTargets.FRM.name),
        ),
        BmwJob(
            id = "e60_acsm_probe_pack",
            label = "E60 ACSM probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "ACSM identity 0x90"),
                step(0x1A, 0x9A, label = "ACSM software/supplier 0x9A"),
                step(0x21, 0x01, label = "ACSM status block 0x01"),
                step(0x21, 0x81, label = "ACSM ASCII/VIN block 0x81"),
                step(0x18, 0x00, 0xFF, 0x00, label = "ACSM read DTCs"),
            ),
            description = "E60-oriented ACSM airbag module workflow.",
            supportedTargets = setOf(BmwTargets.ACSM.name),
        ),
        BmwJob(
            id = "acsm_live_status",
            label = "ACSM live: deployment status",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x01, label = "ACSM status 0x01")),
            description = "Read ACSM occupancy and deployment status block.",
            supportedTargets = setOf(BmwTargets.ACSM.name),
        ),
        BmwJob(
            id = "e60_ccc_probe_pack",
            label = "E60 CCC probe pack",
            category = JobCategory.MODULE_PACK,
            steps = standardSessionPack() + listOf(
                step(0x1A, 0x90, label = "CCC identity 0x90"),
                step(0x1A, 0x9A, label = "CCC software/supplier 0x9A"),
                step(0x21, 0x01, label = "CCC status block 0x01"),
                step(0x21, 0x81, label = "CCC ASCII/VIN block 0x81"),
                step(0x18, 0x00, 0xFF, 0x00, label = "CCC read DTCs"),
            ),
            description = "E60-oriented CCC Car Communication Computer workflow.",
            supportedTargets = setOf(BmwTargets.CCC.name),
        ),
        BmwJob(
            id = "ccc_live_mapslot",
            label = "CCC live: map slot / mode",
            category = JobCategory.LIVE_DATA,
            steps = standardSessionPack() + listOf(step(0x21, 0x02, label = "CCC mode block 0x02")),
            description = "Read CCC active map slot and drive mode status.",
            supportedTargets = setOf(BmwTargets.CCC.name),
        ),
        // KWP coding read/write jobs used by the live coding engine
        BmwJob(
            id = "read_coding_9B",
            label = "Read module coding (0x9B)",
            category = JobCategory.IDENTIFICATION,
            steps = listOf(step(0x1A, 0x9B, label = "Coding record 0x9B")),
            description = "Read current coding / variant identification record from any module.",
        ),
        BmwJob(
            id = "write_coding_3B",
            label = "Write module coding (0x3B)",
            category = JobCategory.CONTROL,
            steps = listOf(step(0x3B, 0x9B, label = "Write coding 0x3B/0x9B")),
            description = "Write coding record to module. Payload byte(s) follow 0x9B in the step.",
            readOnly = false,
        ),
        // CAS engine start/stop sequence
        BmwJob(
            id = "cas_remote_start_sequence",
            label = "CAS remote start sequence",
            category = JobCategory.CONTROL,
            steps = listOf(
                // Step 1: programming/extended session (0x10 0x85) — gives CAS write access.
                // Do NOT combine with standardSessionPack (0x10 0x81) — CAS resets on
                // back-to-back session switches.
                step(0x10, 0x85, label = "CAS programming session"),
                // Step 2: keep-alive so CAS does not drop the session before the routine call
                step(0x3E, 0x00, label = "Tester present"),
                // Step 3: RoutineControl startRoutine (0x31 0x01) routine ID 0x0004 = engine start.
                // 0x0004 is the community-documented CAS E60 engine-start routine ID.
                // Payload: [0x31, 0x01, 0x00, 0x04]
                step(0x31, 0x01, 0x00, 0x04, label = "CAS start routine 0x0004"),
            ),
            description = "CAS KWP routine control: programming session → tester present → " +
                "startRoutine 0x0004 (engine crank request). " +
                "Requires ignition KL15 ON and valid key authenticated in CAS. " +
                "EXPERIMENTAL — stationary/off-road use only.",
            readOnly = false,
            supportedTargets = setOf(BmwTargets.CAS.name),
        ),
        BmwJob(
            id = "cas_remote_stop_sequence",
            label = "CAS remote stop sequence",
            category = JobCategory.CONTROL,
            steps = listOf(
                step(0x10, 0x85, label = "CAS programming session"),
                step(0x3E, 0x00, label = "Tester present"),
                // Routine 0x0005 = engine stop request on E60 CAS
                step(0x31, 0x01, 0x00, 0x05, label = "CAS stop routine 0x0005"),
            ),
            description = "CAS KWP routine control: programming session → tester present → " +
                "startRoutine 0x0005 (engine stop request).",
            readOnly = false,
            supportedTargets = setOf(BmwTargets.CAS.name),
        ),
        // CCC map slot write via WriteDataByIdentifier (0x2E)
        // CCC map slot switching via KWP 0x3B writeDataByLocalIdentifier.
        // E60 CCC runs KWP2000 not UDS — use 0x3B not 0x2E.
        // Local ID 0x02 = CCC drive-program / map selection register.
        // Value 0x01 = Comfort, 0x02 = Sport, 0x03 = Race/Track.
        // Single extended session open — no standardSessionPack first.
        BmwJob(
            id = "ccc_write_mapslot_comfort",
            label = "CCC: set Comfort map",
            category = JobCategory.CONTROL,
            steps = listOf(
                step(0x10, 0x86, label = "CCC extended session"),
                step(0x3E, 0x00, label = "Tester present"),
                step(0x3B, 0x02, 0x01, label = "KWP write local ID 0x02 = Comfort"),
            ),
            description = "Set CCC map slot to Comfort via KWP 0x3B writeDataByLocalIdentifier.",
            readOnly = false,
            supportedTargets = setOf(BmwTargets.CCC.name),
        ),
        BmwJob(
            id = "ccc_write_mapslot_sport",
            label = "CCC: set Sport map",
            category = JobCategory.CONTROL,
            steps = listOf(
                step(0x10, 0x86, label = "CCC extended session"),
                step(0x3E, 0x00, label = "Tester present"),
                step(0x3B, 0x02, 0x02, label = "KWP write local ID 0x02 = Sport"),
            ),
            description = "Set CCC map slot to Sport via KWP 0x3B writeDataByLocalIdentifier.",
            readOnly = false,
            supportedTargets = setOf(BmwTargets.CCC.name),
        ),
        BmwJob(
            id = "ccc_write_mapslot_race",
            label = "CCC: set Race/Track map",
            category = JobCategory.CONTROL,
            steps = listOf(
                step(0x10, 0x86, label = "CCC extended session"),
                step(0x3E, 0x00, label = "Tester present"),
                step(0x3B, 0x02, 0x03, label = "KWP write local ID 0x02 = Race"),
            ),
            description = "Set CCC map slot to Race/Track via KWP 0x3B writeDataByLocalIdentifier.",
            readOnly = false,
            supportedTargets = setOf(BmwTargets.CCC.name),
        ),
    )

    val all: List<BmwJob> = generic + serviceJobs + moduleSpecific

    fun forTarget(target: EcuTarget): List<BmwJob> = all.filter { it.appliesTo(target) }

    fun byId(id: String): BmwJob? = all.firstOrNull { it.id == id }
}
