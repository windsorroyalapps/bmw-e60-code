package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.VehicleProfileKind

data class CommProfile(
    val name: String,
    val requestTimeoutMs: Int,
    val retries: Int,
    val interFrameDelayMs: Long,
    val postConnectDelayMs: Long,
    val preJobDelayMs: Long,
    val autoTesterPresentBeforeJob: Boolean,
    val recommendExtendedSession: Boolean,
)

object BmwCommProfiles {
    private val defaultProfile = CommProfile(
        name = "BMW Generic",
        requestTimeoutMs = 2000,
        retries = 3,
        interFrameDelayMs = 60,
        postConnectDelayMs = 500,
        preJobDelayMs = 50,
        autoTesterPresentBeforeJob = true,
        recommendExtendedSession = false,
    )

    private val byTarget = mapOf(
        BmwTargets.DME.name to CommProfile("E60 DME / DDE",1600,2,45,350,25,true,true),
        BmwTargets.EGS.name to CommProfile("E60 EGS",1700,2,55,350,30,true,true),
        BmwTargets.DSC.name to CommProfile("E60 DSC",1800,2,65,400,35,true,true),
        BmwTargets.KOMBI.name to CommProfile("E60 KOMBI",1300,1,35,250,20,false,false),
        BmwTargets.SZL.name to CommProfile("E60 SZL",1400,1,45,300,25,true,false),
        BmwTargets.CAS.name to CommProfile("E60 CAS",1500,2,55,450,35,true,false),
        BmwTargets.FRM.name to CommProfile("E60 FRM / LM",1400,1,40,300,25,false,false),
        // ACSM: needs extended session for coding writes; conservative timeouts
        BmwTargets.ACSM.name to CommProfile("E60 ACSM",1600,2,55,400,35,true,true),
        // CCC: MOST-bridged module; longer settle, extended session for map writes
        BmwTargets.CCC.name to CommProfile("E60 CCC",1800,2,60,500,40,true,true),
        BmwTargets.EWS.name to CommProfile("BMW EWS", 1500, 2, 70, 400, 50, false, false),
        BmwTargets.LSZ.name to CommProfile("BMW LSZ/LCM", 1400, 1, 50, 300, 30, false, false),
        BmwTargets.GM.name to CommProfile("BMW GM/ZKE", 1500, 1, 60, 400, 40, false, false),
        BmwTargets.ZGM.name to CommProfile("BMW ZGM", 1200, 2, 20, 250, 20, true, false),
        BmwTargets.FEM.name to CommProfile("BMW FEM/BDC", 1100, 2, 15, 200, 20, true, true),
    )

    fun forTarget(target: EcuTarget, vehicleProfile: VehicleProfileKind = VehicleProfileKind.GENERIC_E60): CommProfile {
        val base = byTarget[target.name] ?: defaultProfile
        return when (vehicleProfile) {
            VehicleProfileKind.GENERIC_E60 -> base
            VehicleProfileKind.N52_6HP -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "N52 DME", requestTimeoutMs = 1500, interFrameDelayMs = 40, postConnectDelayMs = 320)
                BmwTargets.EGS.name -> base.copy(name = "ZF 6HP (N52)", requestTimeoutMs = 1650, interFrameDelayMs = 50)
                else -> base
            }
            VehicleProfileKind.N54_6HP -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "N54 DME", requestTimeoutMs = 1750, retries = 2, interFrameDelayMs = 55, postConnectDelayMs = 420)
                BmwTargets.EGS.name -> base.copy(name = "ZF 6HP (N54)", requestTimeoutMs = 1750, interFrameDelayMs = 55)
                else -> base
            }
            VehicleProfileKind.M57_6HP -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "M57 DDE", requestTimeoutMs = 1850, retries = 2, interFrameDelayMs = 60, postConnectDelayMs = 450)
                BmwTargets.EGS.name -> base.copy(name = "ZF 6HP (M57)", requestTimeoutMs = 1800, interFrameDelayMs = 60)
                else -> base
            }
            VehicleProfileKind.N62_6HP -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "N62 DME", requestTimeoutMs = 1900, retries = 2, interFrameDelayMs = 60, postConnectDelayMs = 500)
                BmwTargets.CAS.name -> base.copy(name = "N62 CAS", requestTimeoutMs = 1600, postConnectDelayMs = 475)
                else -> base
            }
            VehicleProfileKind.E46_GENERIC -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "E46 DME (MS43/MS45)", requestTimeoutMs = 1500, interFrameDelayMs = 70)
                BmwTargets.EWS.name -> base.copy(name = "EWS3", requestTimeoutMs = 1800, interFrameDelayMs = 80)
                else -> base.copy(interFrameDelayMs = 75) // Older K-line is slower
            }
            VehicleProfileKind.E39_GENERIC, VehicleProfileKind.E38_GENERIC, VehicleProfileKind.E53_GENERIC, VehicleProfileKind.E83_GENERIC, VehicleProfileKind.E85_GENERIC -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "${vehicleProfile.name} DME", requestTimeoutMs = 1600, interFrameDelayMs = 75)
                BmwTargets.EWS.name -> base.copy(name = "EWS (Legacy)", requestTimeoutMs = 1800, interFrameDelayMs = 85)
                else -> base.copy(interFrameDelayMs = 80)
            }
            VehicleProfileKind.E90_GENERIC, VehicleProfileKind.E87_GENERIC, VehicleProfileKind.E70_GENERIC, VehicleProfileKind.E71_GENERIC -> when (target.name) {
                BmwTargets.CAS.name -> base.copy(name = "CAS2/3 (DCAN)", requestTimeoutMs = 1400, interFrameDelayMs = 35)
                BmwTargets.DME.name -> base.copy(name = "E9x/E8x/E7x DME", requestTimeoutMs = 1500, interFrameDelayMs = 40)
                else -> base.copy(interFrameDelayMs = 40)
            }
            VehicleProfileKind.E92_N54 -> when (target.name) {
                BmwTargets.DME.name -> base.copy(name = "N54 DME (E92)", requestTimeoutMs = 1750, interFrameDelayMs = 50)
                BmwTargets.CAS.name -> base.copy(name = "CAS3 (E92)", requestTimeoutMs = 1450, interFrameDelayMs = 40)
                else -> base.copy(interFrameDelayMs = 45)
            }
            VehicleProfileKind.F10_GENERIC, VehicleProfileKind.F30_GENERIC, VehicleProfileKind.G30_GENERIC, VehicleProfileKind.F20_GENERIC -> {
                // F/G series ENET is much faster
                base.copy(name = "${vehicleProfile.name} ENET", requestTimeoutMs = 1000, interFrameDelayMs = 10, postConnectDelayMs = 200)
            }
            VehicleProfileKind.E65_GENERIC -> when (target.name) {
                BmwTargets.CAS.name -> base.copy(name = "CAS1/2 (E65)", requestTimeoutMs = 1600, interFrameDelayMs = 50, postConnectDelayMs = 500)
                else -> base.copy(interFrameDelayMs = 55)
            }
            VehicleProfileKind.E89_GENERIC -> when (target.name) {
                BmwTargets.CAS.name -> base.copy(name = "CAS3+ (E89)", requestTimeoutMs = 1400, interFrameDelayMs = 35)
                else -> base.copy(interFrameDelayMs = 40)
            }
        }
    }
}
