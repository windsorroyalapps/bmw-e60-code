package com.bmwe60coderpro.ui

import com.bmwe60coderpro.data.ServiceScreen

data class QuickServiceAction(
    val label: String,
    val targetId: String,
    val jobId: String,
)

data class ServiceScreenDefinition(
    val screen: ServiceScreen,
    val title: String,
    val subtitle: String,
    val targetId: String? = null,
    val focusKeys: List<String> = emptyList(),
    val actions: List<QuickServiceAction> = emptyList(),
)

object ServiceScreens {
    val all = listOf(
        ServiceScreenDefinition(
            screen = ServiceScreen.OVERVIEW,
            title = "Overview",
            subtitle = "Connection state, profile selection, ECU targets, and quick access to the dedicated BMW service pages.",
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.DME,
            title = "DME / DDE service",
            subtitle = "Engine ECU identification, live values, VIN text, and fault memory.",
            targetId = "DME / DDE",
            focusKeys = listOf("engine_speed_rpm", "throttle_angle_pct", "coolant_temp_c", "intake_temp_c", "battery_v", "air_mass_or_load_raw", "torque_or_injection_raw", "pedal_pct"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "DME / DDE", "ecu_id_9A"),
                QuickServiceAction("Basic live", "DME / DDE", "dme_live_basic"),
                QuickServiceAction("Air / torque", "DME / DDE", "dme_live_air"),
                QuickServiceAction("Read faults", "DME / DDE", "faults_read"),
                QuickServiceAction("Full probe", "DME / DDE", "e60_dme_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.EGS,
            title = "EGS service",
            subtitle = "Gearbox state, oil temp, selector state, shaft speeds, and fault memory.",
            targetId = "EGS",
            focusKeys = listOf("current_gear_raw", "selector_position_raw", "input_speed_rpm", "output_speed_rpm", "oil_temp_c", "lockup_state_bits", "shift_program_raw"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "EGS", "ecu_id_9A"),
                QuickServiceAction("Gear / speed live", "EGS", "egs_live_basic"),
                QuickServiceAction("Temp / lockup", "EGS", "egs_live_temp"),
                QuickServiceAction("Read faults", "EGS", "faults_read"),
                QuickServiceAction("Full probe", "EGS", "e60_egs_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.DSC,
            title = "DSC service",
            subtitle = "Vehicle speed, steering angle, yaw, lateral acceleration, wheel speeds, and fault memory.",
            targetId = "DSC",
            focusKeys = listOf("vehicle_speed_kph", "steering_angle_raw", "yaw_rate_raw", "lateral_accel_raw", "wheel_speed_fl_raw", "wheel_speed_fr_raw", "wheel_speed_rl_raw", "wheel_speed_rr_raw"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "DSC", "ecu_id_9A"),
                QuickServiceAction("Status", "DSC", "dsc_live_status"),
                QuickServiceAction("Steering / yaw", "DSC", "dsc_live_sensor"),
                QuickServiceAction("Wheel speeds", "DSC", "dsc_live_wheels"),
                QuickServiceAction("Read faults", "DSC", "faults_read"),
                QuickServiceAction("Full probe", "DSC", "e60_dsc_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.KOMBI,
            title = "KOMBI service",
            subtitle = "Cluster speed and RPM mirror, CBS data, odometer-style blocks, and fault memory.",
            targetId = "KOMBI",
            focusKeys = listOf("vehicle_speed_kph", "engine_speed_rpm", "cbs_service_flags", "check_control_count", "odometer_km_raw"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "KOMBI", "ecu_id_9A"),
                QuickServiceAction("Speed / RPM", "KOMBI", "kombi_live_drive"),
                QuickServiceAction("CBS / odometer", "KOMBI", "kombi_live_cbs"),
                QuickServiceAction("Read faults", "KOMBI", "faults_read"),
                QuickServiceAction("Full probe", "KOMBI", "e60_kombi_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.SZL,
            title = "SZL service",
            subtitle = "Steering wheel switch state, angle-style values, button matrix data, and fault memory.",
            targetId = "SZL",
            focusKeys = listOf("turn_signal_flags", "wiper_flags", "button_flags", "steering_angle_raw", "button_matrix_1", "button_matrix_2"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "SZL", "ecu_id_9A"),
                QuickServiceAction("Switch states", "SZL", "szl_live_switches"),
                QuickServiceAction("Angle / buttons", "SZL", "szl_live_angle"),
                QuickServiceAction("Read faults", "SZL", "faults_read"),
                QuickServiceAction("Full probe", "SZL", "e60_szl_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.CAS,
            title = "CAS service",
            subtitle = "Terminal state, start authorization, key presence, key slot data, and fault memory.",
            targetId = "CAS",
            focusKeys = listOf("terminal_flags", "key_presence_flags", "start_authorization_flags", "key_slot_status", "remote_button_flags", "terminal_status_2"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "CAS", "ecu_id_9A"),
                QuickServiceAction("Terminals / auth", "CAS", "cas_live_terminals"),
                QuickServiceAction("Key slot / remote", "CAS", "cas_live_keyslot"),
                QuickServiceAction("Read faults", "CAS", "faults_read"),
                QuickServiceAction("Full probe", "CAS", "e60_cas_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.FRM,
            title = "FRM / LM service",
            subtitle = "Lighting state, window flags, output-stage style values, and fault memory.",
            targetId = "FRM / LM",
            focusKeys = listOf("lighting_flags_1", "lighting_flags_2", "window_flags", "output_stage_1_raw", "output_stage_2_raw"),
            actions = listOf(
                QuickServiceAction("Identify ECU", "FRM / LM", "ecu_id_9A"),
                QuickServiceAction("Lighting / windows", "FRM / LM", "frm_live_status"),
                QuickServiceAction("Outputs", "FRM / LM", "frm_live_outputs"),
                QuickServiceAction("Read faults", "FRM / LM", "faults_read"),
                QuickServiceAction("Full probe", "FRM / LM", "e60_frm_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(
            screen = ServiceScreen.ACSM,
            title = "ACSM / Airbag service",
            subtitle = "Airbag system identification, occupancy status, warning suppression presets, and fault memory.",
            targetId = "ACSM",
            focusKeys = listOf("airbag_status_flags", "occupancy_class_raw", "deployment_status_raw"),
            actions = listOf(
                QuickServiceAction("Identify ECU",    "ACSM", "ecu_id_9A"),
                QuickServiceAction("Status block",    "ACSM", "acsm_live_status"),
                QuickServiceAction("Read faults",     "ACSM", "faults_read"),
                QuickServiceAction("Full probe",      "ACSM", "e60_acsm_probe_pack"),
            ),
        ),
        ServiceScreenDefinition(ServiceScreen.CODING, "Coding", "Daten editor, coding presets, patch preview, and warning-light related presets."),
        ServiceScreenDefinition(ServiceScreen.TUNING, "DME Tuning", "Live DME fuel and ignition map editing, CCC map slot selection, and tune summaries."),
        ServiceScreenDefinition(ServiceScreen.CCC, "CCC integration", "CCC map switching notes and prepared command bundles for the selected tune slot."),
        ServiceScreenDefinition(ServiceScreen.STEERING, "Steering retrofit", "F-series wheel / MFL / paddle retrofit helpers and coding hints."),
        ServiceScreenDefinition(ServiceScreen.FLASHING, "Flashing", "Dry-run / expert flash planning with chunk preview and transfer frame generation."),
        ServiceScreenDefinition(ServiceScreen.EXPERIMENTS, "Experimental", "Remote control and gamepad preparation screens kept in safe simulation mode by default."),
        ServiceScreenDefinition(ServiceScreen.GAUGES, "Gauges", "High-visibility performance gauges in classic BMW orange."),
    )

    fun byScreen(screen: ServiceScreen): ServiceScreenDefinition = all.first { it.screen == screen }
}
