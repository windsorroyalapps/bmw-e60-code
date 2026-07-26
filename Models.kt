package com.bmwe60coderpro.data

enum class TransportType { USB_KDCAN, ETHERNET_OBD }

enum class AdapterPresetKind { USB_FTDI_FAST, USB_FTDI_SAFE, USB_CH340_SAFE, ETH_ENET, ETH_GENERIC_TCP }

enum class VehicleProfileKind { GENERIC_E60, N52_6HP, N54_6HP, M57_6HP, N62_6HP }

enum class ServiceScreen {
    OVERVIEW,
    DME,
    EGS,
    DSC,
    KOMBI,
    SZL,
    CAS,
    FRM,
    ACSM,
    CODING,
    TUNING,
    CCC,
    STEERING,
    FLASHING,
    EXPERIMENTS,
}

enum class CodingPresetKind {
    DIGITAL_SPEED,
    SEATBELT_CHIME_OFF,
    BULB_CHECKS_RELAXED,
    DISCLAIMER_OFF,
    MFL_ENABLE,
    SPORT_BUTTON_ENABLE,
    WELCOME_LIGHTS,
    WARNING_SUPPRESSION_TRACK,
    AIRBAG_OCCUPANCY_OFF,
    AIRBAG_SBR_OFF,
    CCC_DISCLAIMER_OFF,
    CBS_RESET_PREPARE,
}

enum class FlashMode { DRY_RUN, EXPERT_WRITE }

enum class RemoteSafetyMode { SAFE_SIMULATION, EXPERIMENTAL_ONLY }
enum class RemoteStartMode { LOCAL_KDCAN, SIM_REMOTE }

data class AdapterPreset(
    val kind: AdapterPresetKind,
    val label: String,
    val transport: TransportType,
    val baudRate: Int? = null,
    val tcpHost: String? = null,
    val tcpPort: Int? = null,
    val connectTimeoutMs: Int = 2000,
    val readTimeoutMs: Int = 1500,
    val settleDelayMs: Long = 250,
    val notes: String = "",
)

data class DeviceInfo(
    val id: String,
    val name: String,
    val vendorId: Int? = null,
    val productId: Int? = null,
)

data class ConnectionProfile(
    val transport: TransportType = TransportType.USB_KDCAN,
    val baudRate: Int = 115200,
    val tcpHost: String = "192.168.0.10",
    val tcpPort: Int = 35000,
    val connectTimeoutMs: Int = 2000,
    val readTimeoutMs: Int = 1500,
    val settleDelayMs: Long = 250,
    val adapterPreset: AdapterPresetKind = AdapterPresetKind.USB_FTDI_FAST,
    val vehicleProfile: VehicleProfileKind = VehicleProfileKind.GENERIC_E60,
)

data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
)

data class ModuleSnapshot(
    val targetId: String,
    val title: String,
    val summary: String,
    val decoded: Map<String, String> = emptyMap(),
    val rawResponse: String = "",
    val timestamp: String = "",
)

data class CodingChange(
    val module: String,
    val parameter: String,
    val value: String,
)

data class CodingPreset(
    val kind: CodingPresetKind,
    val label: String,
    val description: String,
    val changes: List<CodingChange>,
)

data class DatenDocument(
    val module: String,
    val values: Map<String, String>,
)

data class FlashPlan(
    val mode: FlashMode,
    val module: String,
    val bytes: Int,
    val chunkSize: Int,
    val chunkCount: Int,
    val frames: List<String>,
    val summary: String,
)

data class AppState(
    val activeCommProfile: String = "BMW Generic",
    val activeVehicleProfile: String = "Generic E60 / E61",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val pollingEnabled: Boolean = false,
    val pollingIntervalMs: Long = 1200,
    val dashboardStatus: String = "Stopped",
    val selectedTransport: TransportType = TransportType.USB_KDCAN,
    val discoveredDevices: List<DeviceInfo> = emptyList(),
    val selectedDeviceId: String? = null,
    val selectedTargetId: String = "DME / DDE",
    val selectedJobId: String = "ecu_id_9A",
    val selectedServiceScreen: ServiceScreen = ServiceScreen.OVERVIEW,
    val vehicleInfo: String = "Not queried",
    val rawResponse: String = "",
    val lastJobSummary: String = "No job run yet",
    val decodedFields: Map<String, String> = emptyMap(),
    val moduleSnapshots: Map<String, ModuleSnapshot> = emptyMap(),
    val logs: List<LogEntry> = emptyList(),
    val profile: ConnectionProfile = ConnectionProfile(),
    val selectedCodingPreset: CodingPresetKind = CodingPresetKind.DIGITAL_SPEED,
    val codingModule: String = "KOMBI",
    val codingText: String = "KOMBI {\n  DIGITAL_V = nicht_aktiv;\n  GURTWARNUNG = aktiv;\n}\n",
    val codingPreview: String = "",
    val selectedMapSlot: String = "Sport",
    val tuningSummary: String = "No tune exported yet",
    val flashingModule: String = "DME / DDE",
    val flashInputHex: String = "DE AD BE EF 12 34 56 78",
    val flashPlanSummary: String = "No flash plan generated",
    val lastFlashPlan: FlashPlan? = null,
    val flashMode: FlashMode = FlashMode.DRY_RUN,
    val steeringSummary: String = "No steering profile applied",
    val steeringBundlePreview: String = "No steering retrofit bundle built",
    val steeringValidationSummary: String = "No hardware validation plan generated",
    // SZL live button monitor + MFL injector
    val szlMonitorActive: Boolean = false,
    val szlMonitorDryRun: Boolean = true,
    val szlLiveMatrix1: Int = 0,
    val szlLiveMatrix2: Int = 0,
    val szlLiveActiveButtons: List<String> = emptyList(),
    val szlLiveLastDiff: String = "—",
    val mflInjectionLog: List<String> = emptyList(),
    val remoteSafetyMode: RemoteSafetyMode = RemoteSafetyMode.SAFE_SIMULATION,
    val experimentSummary: String = "Experimental features are prepared but not armed",
    // Live ECU coding engine
    val codingReadResult: String = "",
    val codingWriteResult: String = "",
    val codingLiveBusy: Boolean = false,
    // CCC live map switching
    val cccLiveMapResult: String = "",
    val cccLiveMapBusy: Boolean = false,
    // Remote start/stop
    // Remote start - SIM/TCU bridge connection config
    val remoteStartMode: RemoteStartMode = RemoteStartMode.LOCAL_KDCAN,
    val simRemoteHost: String = "",
    val simRemotePort: Int = 35001,
    val simConnected: Boolean = false,
    val simConnecting: Boolean = false,
    val simConnectionResult: String = "",
    val remoteStartArmed: Boolean = false,
    val remoteStartResult: String = "",
    val remoteStartBusy: Boolean = false,
    // Warning suppression
    val warningSuppressResult: String = "",
    // ACSM
    val acsmResult: String = "",
    // Xbox wired USB controller bridge
    val controllerConnected: Boolean = false,
    val controllerName: String = "No controller detected",
    val controllerArmed: Boolean = false,
    val controllerDryRun: Boolean = true,
    val controllerSendThrottle: Boolean = true,
    val controllerSendSteering: Boolean = false,
    val controllerSendBrake: Boolean = false,
    val controllerThrottleCeiling: Float = 0.30f,
    val controllerSteeringNorm: Float = 0f,
    val controllerThrottleNorm: Float = 0f,
    val controllerBrakeNorm: Float = 0f,
    val controllerLastSummary: String = "—",
    val controllerLog: List<String> = emptyList(),
    val controllerTickHz: String = "—",
)

// Appended: Xbox controller bridge state (used by AppState extension)
// AppState is extended via a separate sealed wrapper to avoid rewriting the entire data class.
// The ViewModel holds these fields in a companion ControllerUiState.
