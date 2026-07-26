package com.bmwe60coderpro.protocol

object BmwModuleMetadata {
    val dtcDictionaries: Map<String, Map<Int, String>> = mapOf(
        BmwTargets.DME.name to mapOf(
            0x2A67 to "valvetronic plausibility / eccentric shaft correlation",
            0x2A6B to "throttle / limp-home plausibility",
            0x2CFB to "throttle actuator adaptation / plausibility",
            0x2DED to "air mass system plausibility",
            0x2EF4 to "oxygen sensor adaptation / mixture control",
            0x2F71 to "ambient or intake temperature plausibility",
            0x29E0 to "mixture control bank 1",
            0x29E1 to "mixture control bank 2",
            0x29F4 to "catalyst conversion bank 1",
            0x29F5 to "catalyst conversion bank 2",
        ),
        BmwTargets.EGS.name to mapOf(
            0x4F81 to "gear ratio monitoring clutch A",
            0x4F82 to "gear ratio monitoring clutch B",
            0x4F85 to "EGS CAN torque intervention timeout",
            0x507D to "parking lock / selector monitoring",
            0x5088 to "transmission oil wear / adaptation threshold",
        ),
        BmwTargets.DSC.name to mapOf(
            0x5E19 to "engine management CAN timeout",
            0x5E1A to "transmission CAN timeout",
            0x5E3F to "steering-angle sensor plausibility",
            0x5E40 to "yaw-rate sensor plausibility",
            0x5E43 to "wheel-speed sensor signal implausible",
            0x5DF0 to "brake-pressure sensor plausibility",
        ),
        BmwTargets.KOMBI.name to mapOf(
            0xA3B4 to "cluster internal EEPROM / coding fault",
            0xA559 to "CAN message missing from CAS or DSC",
            0xA3C6 to "service / CBS data invalid",
        ),
        BmwTargets.SZL.name to mapOf(
            0x94E2 to "steering-angle sensor internal fault",
            0x94E7 to "SZL coding / identification mismatch",
            0x94F0 to "multifunction steering wheel communication fault",
        ),
        BmwTargets.CAS.name to mapOf(
            0xA0B4 to "engine start starter operation",
            0xA0B5 to "terminal 50 / starter plausibility",
            0xA0C1 to "key recognition / authentication",
            0xA116 to "ELV / steering lock plausibility",
        ),
        BmwTargets.ACSM.name to mapOf(
            0x01 to listOf(
                "airbag_status_flags",
                "occupancy_class_raw",
                "deployment_status_raw",
            ),
        ),
        BmwTargets.CCC.name to mapOf(
            0x01 to listOf(
                "ccc_status_flags",
                "map_slot_active_raw",
                "most_bus_status_raw",
            ),
            0x02 to listOf(
                "active_map_slot",
                "drive_mode_raw",
            ),
        ),
        BmwTargets.FRM.name to mapOf(
            0x9CBB to "FRM lamp output short circuit",
            0x9CBC to "FRM lamp output open circuit",
            0x9CC5 to "window lifter anti-trap / initialization",
            0x9D12 to "footwell module coding or voltage fault",
        ),
        BmwTargets.ACSM.name to mapOf(
            0xC011 to "airbag system internal fault",
            0xC012 to "driver airbag squib circuit fault",
            0xC013 to "passenger airbag squib circuit fault",
            0xC014 to "side airbag squib fault",
            0xC021 to "occupancy classification system fault",
            0xC022 to "seatbelt pre-tensioner circuit fault",
            0xC101 to "crash data stored",
        ),
        BmwTargets.CCC.name to mapOf(
            0xE001 to "CCC navigation HDD fault",
            0xE002 to "CCC MOST bus communication fault",
            0xE003 to "CCC coding / variant mismatch",
            0xE004 to "CCC map data read fault",
        ),
    )

    val liveLabelMaps: Map<String, Map<Int, List<String>>> = mapOf(
        BmwTargets.DME.name to mapOf(
            0x01 to listOf(
                "engine_speed_rpm_x4",
                "throttle_angle_pct_raw",
                "coolant_temp_c_offset40",
                "intake_temp_c_offset40",
                "battery_voltage_x10",
                "load_or_airmass_raw",
            ),
            0x11 to listOf(
                "air_mass_or_load_hi",
                "air_mass_or_load_lo",
                "torque_or_injection_hi",
                "torque_or_injection_lo",
                "pedal_pct_raw",
                "lambda_control_raw",
            ),
        ),
        BmwTargets.EGS.name to mapOf(
            0x01 to listOf(
                "current_gear_raw",
                "selector_position_raw",
                "input_speed_rpm_x4_hi",
                "input_speed_rpm_x4_lo",
                "output_speed_rpm_x4_hi",
                "output_speed_rpm_x4_lo",
            ),
            0x12 to listOf(
                "oil_temp_c_offset40",
                "lockup_state_bits",
                "shift_program_raw",
            ),
        ),
        BmwTargets.DSC.name to mapOf(
            0x01 to listOf(
                "status_flags_1",
                "status_flags_2",
                "vehicle_speed_kph_x100_hi",
                "vehicle_speed_kph_x100_lo",
            ),
            0x02 to listOf(
                "steering_angle_hi",
                "steering_angle_lo",
                "yaw_rate_hi",
                "yaw_rate_lo",
                "lateral_accel_hi",
                "lateral_accel_lo",
            ),
            0x11 to listOf(
                "wheel_speed_fl_hi",
                "wheel_speed_fl_lo",
                "wheel_speed_fr_hi",
                "wheel_speed_fr_lo",
                "wheel_speed_rl_hi",
                "wheel_speed_rl_lo",
                "wheel_speed_rr_hi",
                "wheel_speed_rr_lo",
            ),
        ),
        BmwTargets.KOMBI.name to mapOf(
            0x01 to listOf(
                "vehicle_speed_kph_x100_hi",
                "vehicle_speed_kph_x100_lo",
                "engine_speed_rpm_x4_hi",
                "engine_speed_rpm_x4_lo",
            ),
            0x82 to listOf(
                "cbs_service_flags",
                "check_control_count",
                "odometer_km_hi",
                "odometer_km_lo",
            ),
        ),
        BmwTargets.SZL.name to mapOf(
            0x01 to listOf(
                "turn_signal_flags",
                "wiper_flags",
                "button_flags",
            ),
            0x02 to listOf(
                "steering_angle_hi",
                "steering_angle_lo",
                "button_matrix_1",
                "button_matrix_2",
            ),
        ),
        BmwTargets.CAS.name to mapOf(
            0x01 to listOf(
                "terminal_flags",
                "key_presence_flags",
                "start_authorization_flags",
            ),
            0x82 to listOf(
                "key_slot_status",
                "remote_button_flags",
                "terminal_status_2",
            ),
        ),
        BmwTargets.FRM.name to mapOf(
            0x01 to listOf(
                "lighting_flags_1",
                "lighting_flags_2",
                "window_flags",
            ),
            0x11 to listOf(
                "output_stage_1_raw",
                "output_stage_2_raw",
            ),
        ),
    )
}
