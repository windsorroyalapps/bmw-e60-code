package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.util.HexUtils

/**
 * CAN / KWP Injection engine for advanced vehicle control.
 * 
 * This class handles "spoofing" of vehicle states by using KWP2000 IO Control (0x30)
 * and Routine Control (0x31). These services allow a tester to override physical
 * inputs or trigger internal ECU functions.
 */
object CanInjector {

    data class InjectableAction(
        val id: String,
        val label: String,
        val target: EcuTarget,
        val serviceId: Int,
        val payload: List<Int>,
        val description: String,
        val riskLevel: RiskLevel = RiskLevel.LOW
    )

    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    data class InjectionMacro(
        val id: String,
        val label: String,
        val description: String,
        val actions: List<String>, // List of action IDs
        val delayBetweenMs: Long = 500
    )

    val ACTIONS = listOf(
        // ── CAS (Car Access System) ──────────────────────────────────────────
        InjectableAction(
            "cas_lock", "Central Lock", BmwTargets.CAS, 0x30, listOf(0x00, 0x01, 0x01),
            "Command CAS to lock all doors via IO Control."
        ),
        InjectableAction(
            "cas_unlock", "Central Unlock", BmwTargets.CAS, 0x30, listOf(0x00, 0x01, 0x02),
            "Command CAS to unlock all doors via IO Control."
        ),
        InjectableAction(
            "cas_trunk", "Open Trunk", BmwTargets.CAS, 0x30, listOf(0x00, 0x02, 0x01),
            "Trigger trunk release solenoid.", RiskLevel.MEDIUM
        ),
        InjectableAction(
            "cas_key_verify", "Verify Key in Slot", BmwTargets.CAS, 0x31, listOf(0x01, 0x00, 0x03),
            "Trigger key authentication and verification routine."
        ),

        // ── FRM / LM (Footwell / Light Module) ────────────────────────────────
        InjectableAction(
            "frm_high_beam", "Flash High Beams", BmwTargets.FRM, 0x30, listOf(0x00, 0x10, 0x01),
            "Momentarily override high beam output."
        ),
        InjectableAction(
            "frm_window_dr_dn", "Driver Window Down", BmwTargets.FRM, 0x30, listOf(0x00, 0x20, 0x01),
            "Force driver window regulator down.", RiskLevel.MEDIUM
        ),
        InjectableAction(
            "frm_window_dr_up", "Driver Window Up", BmwTargets.FRM, 0x30, listOf(0x00, 0x20, 0x02),
            "Force driver window regulator up.", RiskLevel.MEDIUM
        ),

        // ── KOMBI (Instrument Cluster) ───────────────────────────────────────
        InjectableAction(
            "kombi_gauge_sweep", "Gauge Self-Test", BmwTargets.KOMBI, 0x31, listOf(0x01, 0x00, 0x01),
            "Trigger cluster needle sweep routine."
        ),
        InjectableAction(
            "kombi_lcd_test", "LCD Pixel Test", BmwTargets.KOMBI, 0x30, listOf(0x00, 0x05, 0x01),
            "Illuminate all pixels on the cluster display."
        ),

        // ── EGS (Electronic Gearbox) ─────────────────────────────────────────
        InjectableAction(
            "egs_interlock_off", "Release Shift Lock", BmwTargets.EGS, 0x30, listOf(0x00, 0x02, 0x01),
            "Electrically release the shift lever solenoid (allows shifting without brake).", RiskLevel.HIGH
        ),
        
        // ── DME (Engine) ─────────────────────────────────────────────────────
        InjectableAction(
            "dme_fan_high", "Electric Fan 100%", BmwTargets.DME, 0x30, listOf(0x00, 0x08, 0xFF),
            "Force engine cooling fan to maximum speed.", RiskLevel.MEDIUM
        ),
        InjectableAction(
            "dme_fuel_pump", "Fuel Pump Prime", BmwTargets.DME, 0x30, listOf(0x00, 0x04, 0x01),
            "Activate fuel pump relay for 5 seconds.", RiskLevel.MEDIUM
        )
    )

    val MACROS = listOf(
        InjectionMacro(
            "welcome_lights", "Welcome Lights", "Unlocks car and flashes high beams.",
            listOf("cas_unlock", "frm_high_beam")
        ),
        InjectionMacro(
            "vent_cabin", "Vent Cabin", "Unlocks car and rolls down driver window.",
            listOf("cas_unlock", "frm_window_dr_dn")
        ),
        InjectionMacro(
            "track_prep", "Track Prep", "High fan, fuel pump prime, and gauge sweep.",
            listOf("dme_fan_high", "dme_fuel_pump", "kombi_gauge_sweep"),
            delayBetweenMs = 1000
        ),
        InjectionMacro(
            "stealth_exit", "Stealth Exit", "Turns off lights, locks doors and closes windows.",
            listOf("frm_high_beam", "cas_lock", "frm_window_dr_up"),
            delayBetweenMs = 1000
        )
    )

    suspend fun inject(session: KdcanSession, action: InjectableAction): JobResult {
        session.setTarget(action.target)
        val job = BmwJob(
            id = "inject_${action.id}",
            label = "Inject: ${action.label}",
            category = JobCategory.CONTROL,
            steps = listOf(
                JobStep(
                    serviceId = action.serviceId,
                    payload = action.payload,
                    label = action.label
                )
            ),
            description = action.description,
            readOnly = false,
            supportedTargets = setOf(action.target.name)
        )
        return session.execute(job)
    }

    suspend fun sendRawCanFrame(session: KdcanSession, message: E60CanBus.CanMessageDef, payload: List<Int>): String {
        return session.sendRawFrame(
            targetAddress = message.id,
            serviceId = payload.firstOrNull() ?: 0,
            payload = if (payload.size > 1) payload.drop(1) else emptyList()
        )
    }
}
