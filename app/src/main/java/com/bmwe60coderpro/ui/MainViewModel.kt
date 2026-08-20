package com.bmwe60coderpro.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bmwe60coderpro.data.AdapterPresetKind
import com.bmwe60coderpro.data.AppPreferencesRepository
import com.bmwe60coderpro.data.AdapterPresets
import com.bmwe60coderpro.data.AppState
import com.bmwe60coderpro.data.CodingBackup
import com.bmwe60coderpro.data.CodingPresetKind
import com.bmwe60coderpro.data.ConnectionProfile
import com.bmwe60coderpro.data.FlashMode
import com.bmwe60coderpro.data.LogEntry
import com.bmwe60coderpro.data.ModuleSnapshot
import com.bmwe60coderpro.data.RemoteSafetyMode
import com.bmwe60coderpro.data.ServiceScreen
import com.bmwe60coderpro.data.TransportType
import com.bmwe60coderpro.data.TuningMap
import com.bmwe60coderpro.data.VehicleProfileKind
import com.bmwe60coderpro.data.RemoteStartMode
import com.bmwe60coderpro.network.BluetoothTransport
import com.bmwe60coderpro.data.KeySlotInfo
import com.bmwe60coderpro.data.KeySlotDetail
import com.bmwe60coderpro.data.KeyDataResult
import com.bmwe60coderpro.network.SimRemoteTransport
import com.bmwe60coderpro.network.TcpObdTransport
import com.bmwe60coderpro.protocol.BmwCommProfiles
import com.bmwe60coderpro.protocol.BmwJob
import com.bmwe60coderpro.protocol.BmwJobs
import com.bmwe60coderpro.protocol.CanInjector
import com.bmwe60coderpro.protocol.JobCategory
import com.bmwe60coderpro.protocol.JobStep
import com.bmwe60coderpro.protocol.BmwTargets
import com.bmwe60coderpro.protocol.DatenManager
import com.bmwe60coderpro.protocol.DiagnosticSnapshotReducer
import com.bmwe60coderpro.protocol.E60AddressBook
import com.bmwe60coderpro.protocol.EcuTarget
import com.bmwe60coderpro.protocol.ExpertFunctions
import com.bmwe60coderpro.protocol.FlashingManager
import com.bmwe60coderpro.protocol.KdcanSession
import com.bmwe60coderpro.protocol.SteeringRetrofitManager
import com.bmwe60coderpro.protocol.SteeringRetrofitPresetKind
import com.bmwe60coderpro.protocol.Transport
import com.bmwe60coderpro.usb.UsbSerialTransport
import com.bmwe60coderpro.usb.UsbPermissionManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.bmwe60coderpro.controller.ControllerInjector
import com.bmwe60coderpro.controller.XboxControllerManager
import com.bmwe60coderpro.protocol.SzlButtonDecoder
import com.bmwe60coderpro.protocol.MflInjector
import com.bmwe60coderpro.protocol.E60CanBus
import com.bmwe60coderpro.util.HexUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(private val application: Application) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val adapterPresets = AdapterPresets.all
    val vehicleProfiles = E60AddressBook.profiles
    val serviceScreens = ServiceScreens.all
    val codingPresets = DatenManager.presets
    val tuneProfiles = ExpertFunctions.tuneProfiles
    val steeringProfiles = ExpertFunctions.steeringProfiles
    val steeringRetrofitPresets = SteeringRetrofitManager.presets

    private val prefs = AppPreferencesRepository(application.applicationContext)
    private var transport: Transport? = null
    private var session: KdcanSession? = null
    private var pollingJob: Job? = null
    /** Last logged failure per target/job, used to keep repeated polling errors readable. */
    private val lastPollFailureByJob = mutableMapOf<String, String>()
    private var connectJob: Job? = null
    // Separate session used for SIM remote start — does not share the main K+DCAN session
    private var simSession: KdcanSession? = null
    private var simTransport: SimRemoteTransport? = null
    private var szlMonitorJob: Job? = null
    private var controllerJob: Job? = null
    private var safetyWatchdogJob: Job? = null
    // Latest axes from MotionEvent — written from Activity thread, read by coroutine
    @Volatile private var latestAxes = com.bmwe60coderpro.controller.ControllerAxes()
    @Volatile private var latestButtons = com.bmwe60coderpro.controller.ControllerButtons()
    @Volatile private var controllerTickCount = 0L
    @Volatile private var controllerLastTickMs = 0L

    init {
        val savedProfile = prefs.loadProfile()
        val savedScreen = prefs.loadSelectedScreen()
        val savedInterval = prefs.loadPollingInterval()
        _state.value = _state.value.copy(
            profile = savedProfile,
            selectedTransport = savedProfile.transport,
            selectedServiceScreen = savedScreen,
            pollingIntervalMs = savedInterval,
        )
        selectVehicleProfile(savedProfile.vehicleProfile)
        selectServiceScreen(savedScreen)
        applyAdapterPreset(savedProfile.adapterPreset)
    }

    fun targets(): List<EcuTarget> = E60AddressBook.targetsFor(_state.value.profile.vehicleProfile)

    fun setTransport(type: TransportType) {
        _state.value = _state.value.copy(selectedTransport = type, profile = _state.value.profile.copy(transport = type))
        prefs.saveProfile(_state.value.profile)
    }

    fun selectServiceScreen(screen: ServiceScreen) {
        _state.value = _state.value.copy(selectedServiceScreen = screen)
        prefs.saveSelectedScreen(screen)
        val def = ServiceScreens.byScreen(screen)
        def.targetId?.let { selectTarget(it) }
    }

    fun selectVehicleProfile(kind: VehicleProfileKind) {
        val vp = E60AddressBook.byKind(kind)
        val targets = E60AddressBook.targetsFor(kind)
        val currentTarget = targets.firstOrNull { it.name == _state.value.selectedTargetId } ?: targets.firstOrNull() ?: BmwTargets.DME
        val nextJobs = BmwJobs.forTarget(currentTarget)
        
        val preset = AdapterPresets.byKind(vp.recommendedPreset)
        val newProfile = _state.value.profile.copy(
            vehicleProfile = kind,
            transport = preset.transport,
            adapterPreset = vp.recommendedPreset,
            baudRate = preset.baudRate ?: _state.value.profile.baudRate,
            tcpHost = preset.tcpHost ?: _state.value.profile.tcpHost,
            tcpPort = preset.tcpPort ?: _state.value.profile.tcpPort,
            connectTimeoutMs = preset.connectTimeoutMs,
            readTimeoutMs = preset.readTimeoutMs,
            settleDelayMs = preset.settleDelayMs,
        )

        _state.value = _state.value.copy(
            activeVehicleProfile = vp.label,
            selectedTargetId = currentTarget.name,
            selectedJobId = nextJobs.firstOrNull()?.id ?: _state.value.selectedJobId,
            activeCommProfile = BmwCommProfiles.forTarget(currentTarget, kind).name,
            selectedTransport = preset.transport,
            profile = newProfile,
            tuningSummary = ExpertFunctions.tuneSummary(_state.value.selectedMapSlot, kind),
        )
        prefs.saveProfile(_state.value.profile)
        session?.setVehicleProfile(kind)
        session?.setTarget(currentTarget)
        log("INFO", "Vehicle profile selected: ${vp.label}")
    }

    fun applyAdapterPreset(kind: AdapterPresetKind) {
        val preset = AdapterPresets.byKind(kind)
        val newProfile = _state.value.profile.copy(
            transport = preset.transport,
            baudRate = preset.baudRate ?: _state.value.profile.baudRate,
            tcpHost = preset.tcpHost ?: _state.value.profile.tcpHost,
            tcpPort = preset.tcpPort ?: _state.value.profile.tcpPort,
            connectTimeoutMs = preset.connectTimeoutMs,
            readTimeoutMs = preset.readTimeoutMs,
            settleDelayMs = preset.settleDelayMs,
            adapterPreset = preset.kind,
        )
        _state.value = _state.value.copy(
            selectedTransport = preset.transport,
            profile = newProfile
        )
        prefs.saveProfile(_state.value.profile)
        log("INFO", "Preset applied: ${preset.label}")
    }

    fun updateBaudRate(value: String) {
        val baud = value.toIntOrNull() ?: return
        _state.value = _state.value.copy(profile = _state.value.profile.copy(baudRate = baud))
        prefs.saveProfile(_state.value.profile)
    }

    fun updateHost(value: String) {
        _state.value = _state.value.copy(profile = _state.value.profile.copy(tcpHost = value))
        prefs.saveProfile(_state.value.profile)
    }

    fun updatePort(value: String) {
        val port = value.toIntOrNull() ?: return
        _state.value = _state.value.copy(profile = _state.value.profile.copy(tcpPort = port))
        prefs.saveProfile(_state.value.profile)
    }

    fun updatePollingInterval(value: String) {
        val ms = value.toLongOrNull()?.coerceIn(300L, 10_000L) ?: return
        _state.value = _state.value.copy(pollingIntervalMs = ms)
        prefs.savePollingInterval(ms)
    }

    fun forceIgnitionOn() {
        val profile = _state.value.profile.vehicleProfile
        val jobId = when (profile) {
            VehicleProfileKind.E46_GENERIC,
            VehicleProfileKind.E39_GENERIC -> "force_ignition_ews"
            VehicleProfileKind.F10_GENERIC,
            VehicleProfileKind.F30_GENERIC -> "force_ignition_cas4"
            VehicleProfileKind.E90_GENERIC,
            VehicleProfileKind.E92_N54,
            VehicleProfileKind.E70_GENERIC,
            VehicleProfileKind.E71_GENERIC,
            VehicleProfileKind.E87_GENERIC -> "force_ignition_cas3"
            else -> "force_ignition_cas2" // E60 default
        }
        val job = BmwJobs.byId(jobId) ?: return
        viewModelScope.launch {
            runBusy {
                try {
                    val result = session?.execute(job)
                    if (result != null && result.success) {
                        _state.value = _state.value.copy(
                            forceIgnitionOn = true,
                            ignitionStatus = "Ignition forced ON via ${job.label}"
                        )
                        log("INFO", "Force ignition: ${job.label} -> OK")
                    } else {
                        _state.value = _state.value.copy(
                            ignitionStatus = "Force ignition failed: ${result?.summary ?: "No response"}"
                        )
                        log("ERROR", "Force ignition failed: ${result?.summary ?: "No response"}")
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        ignitionStatus = "Force ignition failed: ${e.message}"
                    )
                    log("ERROR", "Force ignition failed: ${e.message}")
                }
            }
        }
    }

    fun readKeyData() {
        val profile = _state.value.profile.vehicleProfile
        val target = targets().firstOrNull { it.name == "CAS" || it.name == "DME / DDE" } ?: return

        val (isnJobId, memoryJobId, vinJobId) = when (profile) {
            VehicleProfileKind.E46_GENERIC,
            VehicleProfileKind.E39_GENERIC -> 
                listOf("ews_direct_read_memory", "ews_direct_read_memory", "")
            VehicleProfileKind.F10_GENERIC,
            VehicleProfileKind.F30_GENERIC ->
                listOf("fem_direct_read_memory", "fem_direct_read_memory", "")
            else ->
                listOf("cas_direct_read_isn", "cas_direct_read_key_memory", "cas_direct_read_vin")
        }

        _state.value = _state.value.copy(keyDataBusy = true, keyDataError = "", busy = true)

        viewModelScope.launch {
            try {
                var isn = ""
                var vin = ""
                var moduleVersion = ""
                var rawData = ""
                val keySlots = mutableListOf<KeySlotInfo>()

                BmwJobs.byId(isnJobId)?.let { job ->
                    val result = session?.execute(job)
                    if (result?.success == true) {
                        isn = result.decoded["isn"] ?: result.responseHex.take(8)
                        moduleVersion = result.decoded["module_version"] ?: ""
                        rawData = result.responseHex

                        val responseBytes = parseHexBytes(result.responseHex)
                        var idx = 0
                        val count = responseBytes.getOrNull(idx++) ?: 4
                        for (i in 0 until minOf(count, 4)) {
                            val statusByte = responseBytes.getOrNull(idx++) ?: 0
                            val hasKey = (statusByte and 0x01) != 0
                            val hasModuleData = (statusByte and 0x02) != 0
                            val idLen = responseBytes.getOrNull(idx++) ?: 0
                            val keyId = if (idLen > 0 && responseBytes.size > idx + idLen) {
                                responseBytes.subList(idx, idx + idLen).joinToString("") { b -> "%02X".format(b) }
                            } else ""
                            idx += idLen

                            keySlots.add(KeySlotInfo(
                                slotNumber = i + 1,
                                keyPresent = hasKey,
                                keyId = keyId,
                                keyStatus = if (hasKey) "Key present" else if (hasModuleData) "Programmed (no key)" else "Empty",
                                keyType = result.decoded["key_type_${i+1}"] ?: "Unknown",
                                hasModuleData = hasModuleData,
                                moduleDataStatus = if (hasModuleData) "Data stored in module" else "No data"
                            ))
                        }
                    }
                }

                BmwJobs.byId(vinJobId)?.let { job ->
                    val result = session?.execute(job)
                    if (result?.success == true) {
                        vin = result.decoded["vin"] ?: result.decoded["ascii"] ?: ""
                    }
                }

                _state.value = _state.value.copy(
                    keyDataResult = KeyDataResult(
                        isn = isn,
                        vin = vin,
                        keySlots = keySlots,
                        keyCount = keySlots.count { slot: KeySlotInfo -> slot.keyPresent },
                        moduleVersion = moduleVersion,
                        rawKeyData = rawData
                    ),
                    keyDataBusy = false,
                    busy = false
                )
                val programmedCount = keySlots.count { slot: KeySlotInfo -> slot.hasModuleData }
                log("INFO", "Key data read: ${keySlots.count { slot: KeySlotInfo -> slot.keyPresent }} keys present, $programmedCount slots programmed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    keyDataBusy = false,
                    busy = false,
                    keyDataError = "Key read failed: ${e.message}"
                )
                log("ERROR", "Key data read failed: ${e.message}")
            }
        }
    }
    fun exportKeyData() {
        val result = _state.value.keyDataResult ?: return
        val export = buildString {
            appendLine("BMW Key Data Export — Read Directly from Module Memory (No Key Required)")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Vehicle Profile: ${_state.value.activeVehicleProfile}")
            appendLine()
            appendLine("ISN: ${result.isn}")
            appendLine("VIN: ${result.vin}")
            appendLine("Module: ${result.moduleVersion}")
            appendLine("Key Count: ${result.keyCount}")
            appendLine()
            result.keySlots.forEach { slot ->
                appendLine("Slot ${slot.slotNumber}:")
                appendLine("  Key Present: ${if (slot.keyPresent) "Yes" else "No"}")
                appendLine("  Module Data: ${if (slot.hasModuleData) "Yes" else "No"}")
                appendLine("  ID: ${slot.keyId}")
                appendLine("  Status: ${slot.keyStatus}")
                appendLine("  Type: ${slot.keyType}")
            }
            appendLine()
            appendLine("Raw Data: ${result.rawKeyData}")
            appendLine()
            appendLine("NOTE: All data read directly from module EEPROM/memory.")
            appendLine("No physical key is required to retrieve this information.")
        }
        val file = java.io.File(application.cacheDir, "key_export_${System.currentTimeMillis()}.txt")
        file.writeText(export)
        log("INFO", "Key data exported to ${file.absolutePath} (${export.length} chars)")
    }
    fun selectKeySlot(slotNumber: Int) {
        _state.value = _state.value.copy(
            selectedKeySlot = slotNumber,
            keySlotDetail = null,
            keySlotDetailError = "",
            keySlotDetailBusy = false
        )
        log("INFO", "Selected key slot $slotNumber")
    }

    fun setBluetoothMac(mac: String) {
        // Auto-format MAC with colons if user enters continuous hex
        val formattedMac = if (mac.length == 12 && !mac.contains(":")) {
            mac.chunked(2).joinToString(":")
        } else mac

        _state.value = _state.value.copy(
            bluetoothMac = formattedMac,
            profile = _state.value.profile.copy(bluetoothMac = formattedMac)
        )
        prefs.saveProfile(_state.value.profile)
    }

    fun readConnectedBluetoothMac() {
        val transport = session?.getTransport()
        if (transport is BluetoothTransport) {
            val mac = transport.getConnectedDeviceMac()
            val name = transport.getConnectedDeviceName()
            if (mac.isNotEmpty()) {
                _state.value = _state.value.copy(
                    bluetoothConnectedMac = mac,
                    bluetoothConnectedName = name,
                    bluetoothMac = mac,
                    profile = _state.value.profile.copy(bluetoothMac = mac)
                )
                prefs.saveProfile(_state.value.profile)
                log("INFO", "Read MAC from connected Bluetooth device: $name ($mac)")
            } else {
                _state.value = _state.value.copy(
                    dashboardStatus = "No Bluetooth device currently connected"
                )
            }
        } else {
            _state.value = _state.value.copy(
                dashboardStatus = "Bluetooth transport not active"
            )
        }
    }

    fun validateBluetoothMac(mac: String): Boolean {
        return mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
    }

    fun scanBluetoothDevices() {
        _state.value = _state.value.copy(bluetoothScanning = true)
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        application,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    _state.value = _state.value.copy(
                        bluetoothScanning = false,
                        dashboardStatus = "Bluetooth permission is required to inspect paired devices."
                    )
                    return@launch
                }
                val adapter = application.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                if (adapter == null || !adapter.isEnabled) {
                    _state.value = _state.value.copy(
                        bluetoothScanning = false,
                        dashboardStatus = "Bluetooth is disabled. Enable it in Android settings."
                    )
                    return@launch
                }

                val bonded = adapter.bondedDevices?.map { 
                    (it.name ?: "Unknown") to it.address 
                } ?: emptyList()

                _state.value = _state.value.copy(
                    bluetoothDevices = bonded,
                    bluetoothScanning = false,
                    dashboardStatus = "Found ${bonded.size} paired Bluetooth devices"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    bluetoothScanning = false,
                    dashboardStatus = "Bluetooth scan failed: ${e.message}"
                )
            }
        }
    }

    fun checkBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                application, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun readKeySlotDetail(slotNumber: Int) {
        val profile = _state.value.profile.vehicleProfile
        val target = targets().firstOrNull { it.name == "CAS" || it.name == "DME / DDE" } ?: return

        val jobId = when (profile) {
            VehicleProfileKind.E46_GENERIC,
            VehicleProfileKind.E39_GENERIC -> "ews_direct_read_slot_$slotNumber"
            VehicleProfileKind.F10_GENERIC,
            VehicleProfileKind.F30_GENERIC -> "fem_direct_read_slot_$slotNumber"
            else -> "cas_direct_read_slot_$slotNumber"
        }

        val job = BmwJobs.byId(jobId) ?: run {
            _state.value = _state.value.copy(keySlotDetailError = "No direct read job for slot $slotNumber")
            return
        }

        _state.value = _state.value.copy(keySlotDetailBusy = true, keySlotDetailError = "", busy = true)

        viewModelScope.launch {
            try {
                val result = session?.execute(job)
                if (result?.success == true) {
                    val responseBytes = parseHexBytes(result.responseHex)

                    val statusByte = responseBytes.getOrNull(0) ?: 0
                    val hasKey = (statusByte and 0x01) != 0
                    val hasModuleData = (statusByte and 0x02) != 0
                    val isDisabled = (statusByte and 0x04) != 0

                    var idx = 1
                    val keyIdLen = responseBytes.getOrNull(idx++) ?: 0
                    val keyId = if (keyIdLen > 0 && responseBytes.size > idx + keyIdLen) {
                        responseBytes.subList(idx, idx + keyIdLen).joinToString("") { b -> "%02X".format(b) }
                    } else ""
                    idx += keyIdLen

                    val transponderTypeByte = responseBytes.getOrNull(idx++) ?: 0
                    val transponderType = when (transponderTypeByte) {
                        0x01 -> "PCF7935"
                        0x02 -> "PCF7945"
                        0x03 -> "HITAG2"
                        0x04 -> "HITAG AES"
                        0x05 -> "NCF29A1"
                        0x06 -> "PCF7953"
                        0x07 -> "NCF29A1XM"
                        else -> "Unknown (0x${"%02X".format(transponderTypeByte)})"
                    }

                    val transponderIdLen = responseBytes.getOrNull(idx++) ?: 4
                    val transponderId = if (transponderIdLen > 0 && responseBytes.size > idx + transponderIdLen) {
                        responseBytes.subList(idx, idx + transponderIdLen).joinToString("") { b -> "%02X".format(b) }
                    } else ""
                    idx += transponderIdLen

                    val trackData = if (responseBytes.size > idx) {
                        responseBytes.subList(idx, responseBytes.size).joinToString(" ") { b -> "%02X".format(b) }
                    } else ""

                    _state.value = _state.value.copy(
                        keySlotDetail = KeySlotDetail(
                            slotNumber = slotNumber,
                            keyPresent = hasKey,
                            keyId = result.decoded["key_id"] ?: keyId,
                            keyStatus = when {
                                isDisabled -> "Disabled"
                                hasKey -> "Key present"
                                hasModuleData -> "Programmed (no key)"
                                else -> "Empty"
                            },
                            keyType = result.decoded["key_type"] ?: transponderType,
                            transponderType = result.decoded["transponder_type"] ?: transponderType,
                            transponderId = result.decoded["transponder_id"] ?: transponderId,
                            keyTrack = result.decoded["key_track"] ?: trackData,
                            isValid = hasModuleData || hasKey,
                            keyDataHex = result.responseHex,
                            rawResponse = result.summary,
                            hasModuleData = hasModuleData,
                            moduleDataStatus = if (hasModuleData) "Module has stored key data" else "No module data"
                        ),
                        keySlotDetailBusy = false,
                        busy = false
                    )
                    log("INFO", "Key slot $slotNumber read from module memory: key=$hasKey, data=$hasModuleData, ID=$keyId")
                } else {
                    _state.value = _state.value.copy(
                        keySlotDetailBusy = false,
                        busy = false,
                        keySlotDetailError = "Failed to read slot $slotNumber: ${result?.summary ?: "No response"}"
                    )
                    log("ERROR", "Key slot $slotNumber read failed: ${result?.summary ?: "No response"}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    keySlotDetailBusy = false,
                    busy = false,
                    keySlotDetailError = "Slot $slotNumber read error: ${e.message}"
                )
                log("ERROR", "Key slot $slotNumber read error: ${e.message}")
            }
        }
    }
    fun exportKeySlotDetail() {
        val detail = _state.value.keySlotDetail ?: return
        val result = _state.value.keyDataResult
        val export = buildString {
            appendLine("BMW Key Slot ${detail.slotNumber} Export — For New Key Generation")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Vehicle Profile: ${_state.value.activeVehicleProfile}")
            appendLine("VIN: ${result?.vin ?: "N/A"}")
            appendLine("ISN: ${result?.isn ?: "N/A"}")
            appendLine()
            appendLine("=== KEY SLOT ${detail.slotNumber} DATA ===")
            appendLine("Key Present: ${if (detail.keyPresent) "Yes" else "No"}")
            appendLine("Module Data: ${if (detail.hasModuleData) "Yes" else "No"}")
            appendLine("Key ID: ${detail.keyId}")
            appendLine("Transponder Type: ${detail.transponderType}")
            appendLine("Transponder ID: ${detail.transponderId}")
            appendLine("Key Track: ${detail.keyTrack}")
            appendLine("Status: ${detail.keyStatus}")
            appendLine("Valid: ${if (detail.isValid) "Yes" else "No"}")
            appendLine()
            appendLine("=== RAW HEX DATA ===")
            appendLine(detail.keyDataHex)
            appendLine()
            appendLine("=== PROGRAMMING NOTES ===")
            appendLine("1. All data read directly from module memory — no key required")
            appendLine("2. Use ISN and VIN to order correct transponder type")
            appendLine("3. Program transponder with Key ID and Track data")
            appendLine("4. CAS sync may be required after key insertion")
            appendLine("5. If slot is empty but has module data, key programming should succeed")
        }
        val file = java.io.File(application.cacheDir, "key_slot_${detail.slotNumber}_export_${System.currentTimeMillis()}.txt")
        file.writeText(export)
        log("INFO", "Key slot ${detail.slotNumber} exported to ${file.absolutePath} (${export.length} chars)")
    }
    fun exportKeyDataToJson(): String {
        val result = _state.value.keyDataResult ?: return "{}"
        val json = org.json.JSONObject()
        json.put("source", "bmw-e60-code")
        json.put("exportType", "cas_key_data")
        json.put("exportDate", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        json.put("isn", result.isn)
        json.put("vin", result.vin)
        json.put("moduleVersion", result.moduleVersion)
        json.put("keyCount", result.keyCount)
        val slotsArray = org.json.JSONArray()
        result.keySlots.forEach { slot ->
            val slotObj = org.json.JSONObject()
            slotObj.put("slotNumber", slot.slotNumber)
            slotObj.put("keyPresent", slot.keyPresent)
            slotObj.put("keyId", slot.keyId)
            slotObj.put("keyStatus", slot.keyStatus)
            slotObj.put("keyType", slot.keyType)
            slotsArray.put(slotObj)
        }
        json.put("keySlots", slotsArray)
        return json.toString(2)
    }

    fun exportKeyDataForAk90(): String {
        val result = _state.value.keyDataResult ?: return "{}"
        val detail = _state.value.keySlotDetail
        val ak90Json = org.json.JSONObject()
        ak90Json.put("format", "ak90-plus-v1")
        ak90Json.put("sourceApp", "bmw-e60-code")
        ak90Json.put("exportDate", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        val vehicleObj = org.json.JSONObject()
        vehicleObj.put("vin", result.vin)
        vehicleObj.put("isn", result.isn)
        vehicleObj.put("moduleVersion", result.moduleVersion)
        vehicleObj.put("casType", "CAS3")
        ak90Json.put("vehicle", vehicleObj)
        val keysArray = org.json.JSONArray()
        result.keySlots.forEach { slot ->
            val keyObj = org.json.JSONObject()
            keyObj.put("slot", slot.slotNumber)
            keyObj.put("present", slot.keyPresent)
            keyObj.put("id", slot.keyId)
            keyObj.put("status", slot.keyStatus)
            keyObj.put("type", slot.keyType)
            if (detail != null && detail.slotNumber == slot.slotNumber) {
                keyObj.put("transponderType", detail.transponderType)
                keyObj.put("transponderId", detail.transponderId)
                keyObj.put("keyTrack", detail.keyTrack)
                keyObj.put("keyDataHex", detail.keyDataHex)
                keyObj.put("isValid", detail.isValid)
            }
            keysArray.put(keyObj)
        }
        ak90Json.put("keys", keysArray)
        ak90Json.put("totalKeys", result.keyCount)
        return ak90Json.toString(2)
    }

    fun saveKeyDataToFile(context: android.content.Context, format: String = "ak90") {
        val json = if (format == "ak90") exportKeyDataForAk90() else exportKeyDataToJson()
        val filename = if (format == "ak90") "ak90_export_${System.currentTimeMillis()}.json" else "e60_keys_${System.currentTimeMillis()}.json"
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, filename)
            file.writeText(json)
            log("INFO", "Saved to Downloads: ${file.absolutePath}")
            _state.value = _state.value.copy(keyDataError = "", dashboardStatus = "Saved: $filename")
        } catch (e: Exception) {
            log("ERROR", "Save failed: ${e.message}")
            _state.value = _state.value.copy(keyDataError = "Save failed: ${e.message}")
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            runBusy {
                val t = buildTransport(_state.value.profile)
                val devices = t.listDevices()
                _state.value = _state.value.copy(discoveredDevices = devices)
                log("INFO", "Found ${devices.size} device(s)")
            }
        }
    }

    fun selectDevice(id: String) {
        _state.value = _state.value.copy(selectedDeviceId = id)
    }

    fun selectTarget(name: String) {
        val target = targets().firstOrNull { it.name == name } ?: BmwTargets.DME
        val nextJobs = BmwJobs.forTarget(target)
        val selectedJobId = nextJobs.firstOrNull()?.id ?: _state.value.selectedJobId
        _state.value = _state.value.copy(
            selectedTargetId = name,
            selectedJobId = selectedJobId,
            activeCommProfile = BmwCommProfiles.forTarget(target, _state.value.profile.vehicleProfile).name
        )
        session?.setTarget(target)
    }

    fun availableJobs() = BmwJobs.forTarget(currentTarget())

    fun selectJob(id: String) {
        _state.value = _state.value.copy(selectedJobId = id)
    }


    private fun pushConnectionStatus(line: String) {
        _state.value = _state.value.copy(
            connectionStatusLines = _state.value.connectionStatusLines + line,
            connectionStep = line,
        )
    }

    private fun clearConnectionStatus() {
        _state.value = _state.value.copy(
            showConnectionPopup = true,
            connectionStatusLines = emptyList(),
            connectionStep = "Starting connection...",
        )
    }

    fun dismissConnectionPopup() {
        _state.value = _state.value.copy(showConnectionPopup = false)
    }
    /**
     * Force ignition ON via CAS (0x40) using RoutineControl 0x31 routine 0x0001.
     * Sends raw frames so the main session target is never switched.
     */
    private suspend fun forceIgnitionOnCAS(session: KdcanSession) {
        pushConnectionStatus("Forcing ignition ON via CAS...")
        val casAddr = BmwTargets.CAS.targetAddress
        try {
            val r1 = runCatching { session.sendRawFrame(casAddr, 0x10, listOf(0x03)) }.getOrDefault("ERR")
            pushConnectionStatus("  CAS 0x10 0x03 → $r1")
            delay(40)
            val r2 = runCatching { session.sendRawFrame(casAddr, 0x3E, listOf(0x00)) }.getOrDefault("ERR")
            pushConnectionStatus("  CAS 0x3E 0x00 → $r2")
            delay(40)
            val r3 = runCatching { session.sendRawFrame(casAddr, 0x31, listOf(0x01, 0x00, 0x01)) }.getOrDefault("ERR")
            pushConnectionStatus("  CAS 0x31 0x01 0x00 0x01 → $r3")
            val ok = r3.contains("71") || r3.contains("51") || (r3.isNotEmpty() && !r3.contains("7F"))
            val status = if (ok) "Ignition ON — CAS accepted routine 0x0001" else "Ignition command sent — CAS response: $r3"
            _state.value = _state.value.copy(ignitionStatus = status)
            pushConnectionStatus(status)
            log("CAS", status)
        } catch (e: Exception) {
            val err = "Ignition force failed: ${e.message}"
            _state.value = _state.value.copy(ignitionStatus = err)
            pushConnectionStatus("ERROR: $err")
            log("ERROR", err)
        }
    }

    fun connect() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            runBusy {
                clearConnectionStatus()
                pushConnectionStatus("Stopping any active polling...")
                stopPollingInternal(false)
                pushConnectionStatus("Disconnecting previous transport...")
                transport?.disconnect()
                pushConnectionStatus("Building transport (${_state.value.profile.transport.name})...")
                transport = buildTransport(_state.value.profile)
                val currentTransport = transport
                if (currentTransport == null) {
                    pushConnectionStatus("ERROR: Transport initialization failed")
                    _state.value = _state.value.copy(dashboardStatus = "Transport init failed")
                    return@runBusy
                }
                pushConnectionStatus("Transport ready. Opening connection...")
                try {
                    currentTransport.connect(_state.value.selectedDeviceId)
                } catch (e: Exception) {
                    pushConnectionStatus("ERROR: ${e.message}")
                    _state.value = _state.value.copy(dashboardStatus = "Connect failed: ${e.message}")
                    log("ERROR", "Transport connect failed: ${e.message}")
                    return@runBusy
                }
                if (!currentTransport.isConnected()) {
                    pushConnectionStatus("ERROR: Transport open failed (isConnected() = false)")
                    _state.value = _state.value.copy(dashboardStatus = "Transport not connected after open")
                    log("ERROR", "Transport isConnected() returned false after connect()")
                    return@runBusy
                }
                pushConnectionStatus("Transport connected. Starting K+DCAN session...")
                val newSession = KdcanSession(currentTransport, currentTarget(), _state.value.profile.vehicleProfile)
                pushConnectionStatus("Session created. Settling (${_state.value.profile.settleDelayMs}ms)...")
                newSession.onConnected(_state.value.profile.settleDelayMs)
                pushConnectionStatus("Reading communication profile...")
                val profile = newSession.getCommProfile()
                pushConnectionStatus("Profile: ${profile.name}")
                pushConnectionStatus("Verifying ECU communication (${currentTarget().name}) with read-only session and ID requests...")
                val verification = newSession.verifyCommunication()
                pushConnectionStatus("ECU response: ${verification.summary}")
                if (!verification.connected) {
                    currentTransport.disconnect()
                    transport = null
                    session = null
                    _state.value = _state.value.copy(
                        connected = false,
                        rawResponse = verification.responseHex,
                        vehicleInfo = verification.summary,
                        dashboardStatus = "ECU did not confirm K+DCAN communication",
                    )
                    pushConnectionStatus("ERROR: No valid ECU response. Check ignition, physical K-Line/D-CAN switch, and adapter preset.")
                    log("ERROR", "K+DCAN verification failed: ${verification.summary}")
                    return@runBusy
                }

                session = newSession
                _state.value = _state.value.copy(
                    connected = true,
                    vehicleInfo = verification.summary,
                    rawResponse = verification.responseHex,
                    activeCommProfile = profile.name,
                    activeVehicleProfile = E60AddressBook.byKind(_state.value.profile.vehicleProfile).label,
                    dashboardStatus = "Connected, polling stopped",
                )
                pushConnectionStatus("SUCCESS: Valid K+DCAN response from ${currentTarget().name}")

                // Force ignition ON if requested
                if (_state.value.forceIgnitionOn) {
                    forceIgnitionOnCAS(newSession)
                }
                log("INFO", "Connected to ${currentTarget().name}")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        viewModelScope.launch {
            stopControllerBridge()
            stopSzlMonitor()
            disconnectSimRemote()
            stopPollingInternal(false)
            transport?.disconnect()
            transport = null
            session = null
            _state.value = _state.value.copy(
                connected = false,
                activeCommProfile = BmwCommProfiles.forTarget(currentTarget(), _state.value.profile.vehicleProfile).name,
                dashboardStatus = "Disconnected",
            )
            log("INFO", "Disconnected")
        }
    }

    fun sendRawHex(hex: String) {
        viewModelScope.launch {
            runBusy {
                val response = session?.sendRawHex(hex) ?: error("Not connected")
                _state.value = _state.value.copy(rawResponse = response, decodedFields = emptyMap())
                log("TX", hex)
                log("RX", response.ifBlank { "<empty>" })
            }
        }
    }

    fun runSelectedJob() {
        runJobOnTarget(currentTarget().name, _state.value.selectedJobId)
    }

    fun runQuickServiceJob(targetId: String, jobId: String) {
        runJobOnTarget(targetId, jobId)
    }

    fun startDashboardPolling() {
        if (_state.value.pollingEnabled || !_state.value.connected) return
        val activeSession = session ?: return
        pollingJob = viewModelScope.launch {
            _state.value = _state.value.copy(pollingEnabled = true, dashboardStatus = "Polling every ${_state.value.pollingIntervalMs} ms")
            log("INFO", "Dashboard polling started")
            while (_state.value.connected && _state.value.pollingEnabled) {
                var completedReads = 0
                var successfulReads = 0
                val pollJobs = dashboardPollPlan()
                pollJobs.forEach { (targetId, jobId) ->
                    if (!_state.value.connected || !_state.value.pollingEnabled) return@forEach
                    val target = targets().firstOrNull { it.name == targetId } ?: return@forEach
                    val job = BmwJobs.byId(jobId)?.takeIf { it.appliesTo(target) } ?: return@forEach
                    try {
                        activeSession.setVehicleProfile(_state.value.profile.vehicleProfile)
                        val result = activeSession.executeOnTarget(target, job)
                        completedReads += 1
                        if (result.success) {
                            successfulReads += 1
                            lastPollFailureByJob.remove("${target.name}:${job.id}")
                        } else {
                            val failureKey = "${target.name}:${job.id}"
                            if (lastPollFailureByJob[failureKey] != result.summary) {
                                lastPollFailureByJob[failureKey] = result.summary
                                log("POLL", "$failureKey failed: ${result.summary}")
                            }
                        }
                        applyJobResult(result, updateSelection = false, logDetail = false, snapshotTitleSuffix = " [poll]")
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        completedReads += 1
                        val failureKey = "${target.name}:${job.id}"
                        val reason = t.message ?: t.javaClass.simpleName
                        if (lastPollFailureByJob[failureKey] != reason) {
                            lastPollFailureByJob[failureKey] = reason
                            log("POLL", "$failureKey failed: $reason")
                        }
                    }
                    delay(100)
                }
                _state.value = _state.value.copy(
                    dashboardStatus = "Last refresh ${timestamp()} • $successfulReads/$completedReads reads OK • interval ${_state.value.pollingIntervalMs} ms"
                )
                delay(_state.value.pollingIntervalMs)
            }
        }
    }

    fun stopDashboardPolling() {
        viewModelScope.launch { stopPollingInternal(true) }
    }

    fun updateCodingText(value: String) {
        _state.value = _state.value.copy(codingText = value)
    }

    fun selectCodingPreset(kind: CodingPresetKind) {
        val preset = DatenManager.preset(kind)
        _state.value = _state.value.copy(selectedCodingPreset = kind, codingPreview = DatenManager.previewPatch(_state.value.codingText, preset))
    }

    fun applySelectedCodingPreset() {
        val preset = DatenManager.preset(_state.value.selectedCodingPreset)
        val patched = DatenManager.applyPreset(_state.value.codingText, preset)
        _state.value = _state.value.copy(
            codingText = patched,
            codingModule = DatenManager.parse(patched).module,
            codingPreview = DatenManager.previewPatch(patched, preset),
            lastJobSummary = "Applied coding preset: ${preset.label}",
        )
        log("CODE", "Applied coding preset ${preset.label}")
    }

    fun analyzeCodingText() {
        val doc = DatenManager.parse(_state.value.codingText)
        _state.value = _state.value.copy(
            codingModule = doc.module,
            codingPreview = doc.values.entries.joinToString("\n") { "${it.key} = ${it.value}" },
            lastJobSummary = "Parsed ${doc.values.size} coding parameter(s) for ${doc.module}",
        )
        log("CODE", "Parsed coding text for ${doc.module}")
    }

    fun setMapSlot(slot: String) {
        _state.value = _state.value.copy(
            selectedMapSlot = slot,
            tuningSummary = ExpertFunctions.tuneSummary(slot, _state.value.profile.vehicleProfile),
        )
    }

    fun exportTunePlan() {
        val summary = ExpertFunctions.tuneSummary(_state.value.selectedMapSlot, _state.value.profile.vehicleProfile)
        _state.value = _state.value.copy(tuningSummary = summary)
        log("TUNE", summary)
    }

    fun setSteeringProfile(name: String) {
        val summary = ExpertFunctions.steeringSummary(name)
        _state.value = _state.value.copy(steeringSummary = summary)
        log("SZL", summary)
    }

    fun prepareSteeringRetrofit(kind: SteeringRetrofitPresetKind) {
        val preset = SteeringRetrofitManager.preset(kind)
        _state.value = _state.value.copy(
            steeringSummary = preset.label,
            steeringBundlePreview = SteeringRetrofitManager.exportBundle(kind),
            steeringValidationSummary = preset.validationChecklist.joinToString("\n") { "• $it" },
            codingPreview = SteeringRetrofitManager.preview(kind),
        )
        log("SZL", "Prepared retrofit bundle: ${preset.label}")
    }

    fun loadSteeringModuleTemplate(module: String, kind: SteeringRetrofitPresetKind) {
        val rendered = SteeringRetrofitManager.renderModulePatch(module, kind)
        _state.value = _state.value.copy(
            codingText = rendered,
            codingModule = module,
            codingPreview = SteeringRetrofitManager.preview(kind),
        )
        log("CODE", "Loaded $module retrofit template for ${SteeringRetrofitManager.preset(kind).label}")
    }

    fun setSzlMonitorDryRun(dryRun: Boolean) {
        _state.value = _state.value.copy(szlMonitorDryRun = dryRun)
    }

    fun startSzlMonitor() {
        if (szlMonitorJob?.isActive == true) return
        szlMonitorJob = viewModelScope.launch {
            _state.value = _state.value.copy(szlMonitorActive = true)
            log("SZL", "SZL button monitor started (dryRun=${_state.value.szlMonitorDryRun})")
            var prevFrame: com.bmwe60coderpro.protocol.SzlButtonFrame? = null
            try {
                while (true) {
                    val activeSession = session ?: break
                    val szlTarget = targets().firstOrNull { it.name == BmwTargets.SZL.name } ?: break
                    val kombiTarget = targets().firstOrNull { it.name == BmwTargets.KOMBI.name }

                    // 1. Read SZL 0x21/0x02 (angle + button matrix block)
                    activeSession.setTarget(szlTarget)
                    val job = BmwJobs.byId("szl_live_angle") ?: break
                    val result = runCatching { activeSession.execute(job) }.getOrNull()

                    if (result != null && result.success) {
                        // Decoded keys are prefixed with the step label
                        val stepPrefix = "SZL live data: angle / buttons:"
                        val m1Bits = result.decoded["${stepPrefix}button_matrix_1"] ?: ""
                        val m2Bits = result.decoded["${stepPrefix}button_matrix_2"] ?: ""
                        val m1 = if (m1Bits.all { it == '0' || it == '1' }) m1Bits.toInt(2) else 0
                        val m2 = if (m2Bits.all { it == '0' || it == '1' }) m2Bits.toInt(2) else 0

                        val frame = SzlButtonDecoder.decode(m1, m2)
                        val diff = SzlButtonDecoder.diff(prevFrame, frame)

                        // 2. Inject into KOMBI for any active button events
                        val injectionSummary = when {
                            frame.mflEvents.isEmpty() -> "No buttons active"
                            kombiTarget == null -> "KOMBI target unavailable"
                            else -> {
                                activeSession.setTarget(kombiTarget)
                                val injResults = MflInjector.injectFrame(
                                    session = activeSession,
                                    frame = frame,
                                    dryRun = _state.value.szlMonitorDryRun,
                                )
                                // Restore SZL target for next poll iteration
                                activeSession.setTarget(szlTarget)
                                MflInjector.summarise(injResults)
                            }
                        }

                        val logLine = "${timestamp()} $diff → $injectionSummary"
                        val newLog = (_state.value.mflInjectionLog + logLine).takeLast(40)

                        _state.value = _state.value.copy(
                            szlLiveMatrix1 = m1,
                            szlLiveMatrix2 = m2,
                            szlLiveActiveButtons = frame.activeButtons,
                            szlLiveLastDiff = diff,
                            mflInjectionLog = newLog,
                        )
                        prevFrame = frame
                    }

                    delay(150L) // ~6-7 Hz — comfortable for K-line bandwidth + button response feel
                }
            } catch (e: CancellationException) {
                // Normal cancellation via stopSzlMonitor(), swallow silently
            } catch (t: Throwable) {
                log("SZL", "Monitor error: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                _state.value = _state.value.copy(szlMonitorActive = false)
                log("SZL", "SZL button monitor stopped")
            }
        }
    }

    fun stopSzlMonitor() {
        szlMonitorJob?.cancel()
        szlMonitorJob = null
        _state.value = _state.value.copy(szlMonitorActive = false)
    }

    // ── Xbox controller bridge ───────────────────────────────────────────────

    /** Called by MainActivity.dispatchGenericMotionEvent on the UI thread. */
    fun onControllerMotion(event: MotionEvent) {
        if (!XboxControllerManager.isGamepadMotion(event)) return
        latestAxes = XboxControllerManager.parseAxes(event)
        // If a controller is seen for the first time, probe and update name
        if (!_state.value.controllerConnected) {
            val device = event.device
            _state.value = _state.value.copy(
                controllerConnected = true,
                controllerName = XboxControllerManager.describeDevice(device),
            )
            log("CTRL", "Controller connected: ${_state.value.controllerName}")
        }
    }

    /** Called by MainActivity.dispatchKeyEvent for gamepad button events. */
    fun onControllerKey(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        
        // Track whether the key code is one we care about for the vehicle bridge
        var handled = true

        // Update latestButtons state for the periodic bridge tick
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_THUMBR -> latestButtons = latestButtons.copy(horn = isDown)
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (isDown) toggleControllerArmed()
                latestButtons = latestButtons.copy(armToggle = isDown)
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (isDown) triggerEmergencyStop()
                latestButtons = latestButtons.copy(emergencyStop = isDown)
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> latestButtons = latestButtons.copy(paddleUp = isDown)
            KeyEvent.KEYCODE_BUTTON_L1 -> latestButtons = latestButtons.copy(paddleDown = isDown)
            KeyEvent.KEYCODE_BUTTON_X -> latestButtons = latestButtons.copy(xButton = isDown)
            KeyEvent.KEYCODE_BUTTON_Y -> latestButtons = latestButtons.copy(yButton = isDown)
            KeyEvent.KEYCODE_BUTTON_START -> latestButtons = latestButtons.copy(sportOn = isDown)
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE ->
                latestButtons = latestButtons.copy(sportOff = isDown)
            else -> handled = false
        }

        // Immediate actions for specific button presses (outside the 20Hz bridge tick)
        if (isDown && handled) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_R1 -> sendPaddleEvent(up = true)
                KeyEvent.KEYCODE_BUTTON_L1 -> sendPaddleEvent(up = false)
                KeyEvent.KEYCODE_BUTTON_START -> log("CTRL", "Sport mode requested")
                KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE -> log("CTRL", "Normal mode requested")
            }
        }

        // Return true for handled button events to prevent Android system from interpreting 
        // them as UI navigation (e.g. Back/Home) while the app is active.
        return handled
    }

    fun toggleControllerBridge() {
        if (controllerJob?.isActive == true) {
            stopControllerBridge()
        } else {
            startControllerBridge()
        }
    }

    fun setControllerSendThrottle(v: Boolean) {
        _state.value = _state.value.copy(controllerSendThrottle = v)
    }

    fun setControllerSendSteering(v: Boolean) {
        _state.value = _state.value.copy(controllerSendSteering = v)
    }

    fun setControllerSendBrake(v: Boolean) {
        _state.value = _state.value.copy(controllerSendBrake = v)
    }

    fun setControllerThrottleCeiling(pct: Float) {
        _state.value = _state.value.copy(controllerThrottleCeiling = pct.coerceIn(0f, 1f))
    }

    fun setForceIgnitionOn(v: Boolean) {
        _state.value = _state.value.copy(forceIgnitionOn = v)
    }

    fun scanController() {
        val device = XboxControllerManager.findAttachedController()
        if (device != null) {
            _state.value = _state.value.copy(
                controllerConnected = true,
                controllerName = XboxControllerManager.describeDevice(device),
            )
            log("CTRL", "Controller found: ${_state.value.controllerName}")
        } else {
            _state.value = _state.value.copy(
                controllerConnected = false,
                controllerName = "No controller detected",
            )
            log("CTRL", "No gamepad/joystick device found")
        }
    }

    fun startControllerBridge() {
        if (controllerJob?.isActive == true) return
        controllerJob = viewModelScope.launch {
            log("CTRL", "Controller bridge started")
            controllerTickCount = 0L
            controllerLastTickMs = System.currentTimeMillis()

            // Initialize diagnostic sessions on DME and DSC
            val activeSession = session
            if (activeSession != null && _state.value.connected) {
                val initResult = ControllerInjector.initSessions(activeSession)
                log("CTRL", "Session init: $initResult")
                _state.value = _state.value.copy(controllerLastSummary = initResult)
            }

            // Keep-alive coroutine
            val keepAliveJob = launch {
                while (true) {
                    val s = session
                    if (s != null && _state.value.connected) {
                        val ka = ControllerInjector.keepAlive(s)
                        if (!ka.contains("No keep-alive")) log("CTRL", ka)
                    }
                    delay(2000L)
                }
            }

            try {
                while (true) {
                    val st = _state.value
                    val axes = latestAxes
                    val buttons = latestButtons
                    val commands = XboxControllerManager.buildCommands(
                        axes             = axes,
                        buttons          = buttons,
                        throttleCeiling  = st.controllerThrottleCeiling,
                        armed            = st.controllerArmed,
                    )

                    // Update live axis display unconditionally
                    _state.value = _state.value.copy(
                        controllerSteeringNorm  = axes.steeringNorm,
                        controllerThrottleNorm  = axes.throttleNorm,
                        controllerBrakeNorm     = axes.brakeNorm,
                        controllerLastSummary   = commands.summary,
                    )

                    // Inject into vehicle only when armed and connected
                    if (commands.hasActiveCommands && st.connected) {
                        val s = session
                        if (s != null) {
                            val result = ControllerInjector.tick(
                                session       = s,
                                commands      = commands,
                                sendThrottle  = st.controllerSendThrottle,
                                sendSteering  = st.controllerSendSteering,
                                sendBrake     = st.controllerSendBrake,
                            )
                            val nowMs = System.currentTimeMillis()
                            val elapsed = (nowMs - controllerLastTickMs).coerceAtLeast(1L)
                            controllerLastTickMs = nowMs
                            controllerTickCount++
                            val hz = "%.1f Hz".format(1000.0 / elapsed)

                            val logLine = "${timestamp()} ${result.summary}"
                            val newLog = (_state.value.controllerLog + logLine).takeLast(40)
                            _state.value = _state.value.copy(
                                controllerLog    = newLog,
                                controllerTickHz = hz,
                            )
                        }
                    }

                    delay(100L) // 10 Hz — K-line safe cadence
                }
            } catch (e: CancellationException) {
                // normal stop
            } catch (t: Throwable) {
                log("CTRL", "Bridge error: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                keepAliveJob.cancel()
                _state.value = _state.value.copy(controllerArmed = false)
                log("CTRL", "Controller bridge stopped")
            }
        }
    }
    fun stopControllerBridge() {
        controllerJob?.cancel()
        controllerJob = null
        _state.value = _state.value.copy(controllerArmed = false)
        log("CTRL", "Controller bridge stopped by user")
    }

    private fun toggleControllerArmed() {
        val nowArmed = !_state.value.controllerArmed
        _state.value = _state.value.copy(controllerArmed = nowArmed)
        log("CTRL", if (nowArmed) "ARMED — controller input live" else "DISARMED")
    }

    private fun triggerEmergencyStop() {
        _state.value = _state.value.copy(controllerArmed = false)
        viewModelScope.launch {
            val activeSession = session
            if (activeSession != null) {
                val result = ControllerInjector.emergencyStop(
                    session = activeSession,
                    
                )
                log("CTRL", "E-STOP: ${result.summary}")
            } else {
                log("CTRL", "E-STOP (no session)")
            }
        }
    }

    private fun sendPaddleEvent(up: Boolean) {
        log("CTRL", if (up) "Paddle UP request" else "Paddle DOWN request")
        // Reuse MFL injector for paddle — same KOMBI 0x30 route as SZL monitor
        viewModelScope.launch {
            val activeSession = session ?: return@launch
            val code = if (up) com.bmwe60coderpro.protocol.MflButtonCode.PADDLE_UP
                       else com.bmwe60coderpro.protocol.MflButtonCode.PADDLE_DOWN
            val event = com.bmwe60coderpro.protocol.MflEvent(
                label      = if (up) "Paddle▶" else "◀Paddle",
                kwpPayload = listOf(0xA0, 0x03, code),
            )
            com.bmwe60coderpro.protocol.MflInjector.inject(activeSession, event, false)
        }
    }

    // ── Live ECU coding engine ──────────────────────────────────────────────

    /** Read current coding record from an ECU module via KWP 0x1A 0x9B,
     *  populate the coding text editor with the result. */
    fun readCodingFromEcu(module: String) {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(codingLiveBusy = true, codingReadResult = "Reading $module…")
                val activeSession = session ?: error("Not connected")
                val target = targets().firstOrNull { it.name.equals(module, ignoreCase = true) }
                    ?: error("Unknown module $module")
                // Use the cable exclusively for the on-demand read and retain the target
                // throughout session start and record retrieval.
                stopPollingInternal(false)
                activeSession.setVehicleProfile(_state.value.profile.vehicleProfile)
                val sessionJob = BmwJobs.byId("start_session_default") ?: error("Job not found: start_session_default")
                val sessionResult = activeSession.executeOnTarget(target, sessionJob)
                if (!sessionResult.success) error("Cannot start diagnostic session for $module: ${sessionResult.summary}")
                val readJob = BmwJobs.byId("read_coding_9B") ?: error("Job not found: read_coding_9B")
                val result = activeSession.executeOnTarget(target, readJob)
                if (result.success) {
                    val ascii = result.decoded.values.firstOrNull { it.length > 3 } ?: ""
                    val populated = if (ascii.isNotBlank())
                        DatenManager.parseCodingRecord(module, ascii).let { doc ->
                            if (doc.values.size > 1) DatenManager.render(doc)
                            else DatenManager.templateFor(module)
                        }
                    else DatenManager.templateFor(module)
                    _state.value = _state.value.copy(
                        codingText = populated,
                        codingModule = module,
                        codingReadResult = "Read OK from $module (${result.summary})",
                        codingLiveBusy = false,
                    )
                    saveBackup(module, populated, isVo = false)
                    log("CODE", "Read coding from $module: ${result.summary}")
                } else {
                    // Fall back to template so user still has something to work with
                    _state.value = _state.value.copy(
                        codingText = DatenManager.templateFor(module),
                        codingModule = module,
                        codingReadResult = "Read failed ($module): ${result.summary} — template loaded",
                        codingLiveBusy = false,
                    )
                    log("CODE", "Coding read failed for $module — loaded template")
                }
            }
            _state.value = _state.value.copy(codingLiveBusy = false)
        }
    }

    /** Write the current coding text editor content back to the ECU via KWP 0x3B 0x9B. */
    fun writeCodingToEcu() {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(codingLiveBusy = true, codingWriteResult = "Writing…")
                val activeSession = session ?: error("Not connected")
                val doc = DatenManager.parse(_state.value.codingText)

                // Backup before write
                saveBackup(doc.module, _state.value.codingText, isVo = false)

                val target = targets().firstOrNull { it.name.equals(doc.module, ignoreCase = true) }
                    ?: error("Unknown module ${doc.module}. Set module name in first line of coding text.")
                activeSession.setTarget(target)
                // Extended session required for write
                val extSession = BmwJobs.byId("start_session_extended") ?: error("Job not found: start_session_extended")
                activeSession.execute(extSession)
                // Build write payload — ASCII coding record
                val payload = DatenManager.buildCodingWritePayload(doc)
                val writeJob = BmwJob(
                    id = "live_coding_write_${doc.module}",
                    label = "Write coding: ${doc.module}",
                    category = JobCategory.CONTROL,
                    steps = listOf(
                        JobStep(serviceId = 0x3B, payload = listOf(0x9B) + payload, label = "Write 0x3B/0x9B")
                    ),
                    description = "Live coding write to ${doc.module}",
                    readOnly = false,
                    supportedTargets = setOf(target.name),
                )
                val result = activeSession.execute(writeJob)
                val msg = if (result.success)
                    "Write OK → ${doc.module} (${doc.values.size} parameter(s))"
                else
                    "Write FAILED → ${doc.module}: ${result.summary}"
                _state.value = _state.value.copy(
                    codingWriteResult = msg,
                    codingLiveBusy = false,
                )
                log(if (result.success) "CODE" else "ERROR", msg)
            }
            _state.value = _state.value.copy(codingLiveBusy = false)
        }
    }

    fun loadModuleTemplate(module: String) {
        val text = DatenManager.templateFor(module)
        _state.value = _state.value.copy(
            codingText = text,
            codingModule = module,
            codingReadResult = "Template loaded for $module",
        )
    }

    // ── Vehicle Order (VO/FA) ───────────────────────────────────────────────

    /** Read Vehicle Order (FA) string from CAS or FRM/LMA. */
    fun readVehicleOrder(module: String = "CAS") {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(codingLiveBusy = true, codingReadResult = "Reading VO from $module…")
                val activeSession = session ?: error("Not connected")
                val target = targets().firstOrNull { it.name.contains(module, ignoreCase = true) }
                    ?: error("Unknown module $module")
                stopPollingInternal(false)
                activeSession.setVehicleProfile(_state.value.profile.vehicleProfile)

                val readJob = BmwJobs.byId("read_vo_fa") ?: error("Job not found: read_vo_fa")
                val result = activeSession.executeOnTarget(target, readJob)

                if (result.success) {
                    val rawVo = result.decoded.values.firstOrNull { it.length > 5 } ?: ""
                    _state.value = _state.value.copy(
                        vehicleOrder = rawVo,
                        codingReadResult = "VO read OK from $module",
                    )
                    saveBackup(module, rawVo, isVo = true)
                    log("VO", "VO read from $module: $rawVo")
                } else {
                    _state.value = _state.value.copy(
                        codingReadResult = "VO read failed from $module: ${result.summary}",
                    )
                    log("ERROR", "VO read failed for $module")
                }
            }
            _state.value = _state.value.copy(codingLiveBusy = false)
        }
    }

    /** Write current Vehicle Order (FA) string to CAS or FRM/LMA. */
    fun writeVehicleOrder(module: String = "CAS") {
        viewModelScope.launch {
            runBusy {
                val vo = _state.value.vehicleOrder
                if (vo.isBlank()) error("VO string is empty")

                // Backup before write
                saveBackup(module, vo, isVo = true)

                _state.value = _state.value.copy(codingLiveBusy = true, codingWriteResult = "Writing VO to $module…")
                val activeSession = session ?: error("Not connected")
                val target = targets().firstOrNull { it.name.contains(module, ignoreCase = true) }
                    ?: error("Unknown module $module")
                activeSession.setTarget(target)

                // Build write payload: 0x80 (Local ID) + ASCII VO string
                val payload = vo.map { it.code and 0xFF }
                val writeJob = BmwJob(
                    id = "write_vo_fa_live",
                    label = "Write VO to $module",
                    category = JobCategory.CONTROL,
                    steps = listOf(
                        JobStep(serviceId = 0x3B, payload = listOf(0x80) + payload, label = "Write FA block 0x80")
                    ),
                    description = "Write Vehicle Order (FA) string.",
                    readOnly = false,
                    supportedTargets = setOf(target.name),
                )

                val result = activeSession.execute(writeJob)
                val msg = if (result.success) "VO write OK to $module" else "VO write FAILED to $module: ${result.summary}"

                _state.value = _state.value.copy(
                    codingWriteResult = msg,
                )
                log(if (result.success) "VO" else "ERROR", msg)
            }
            _state.value = _state.value.copy(codingLiveBusy = false)
        }
    }

    fun updateVehicleOrderText(text: String) {
        _state.value = _state.value.copy(vehicleOrder = text)
    }

    fun addVoOption(option: String) {
        val clean = option.trim().uppercase().removePrefix("$")
        if (clean.isEmpty()) return
        val current = _state.value.vehicleOrder
        val options = current.split("$").filter { it.isNotBlank() }.toMutableSet()
        if (options.add(clean)) {
            val newVo = options.sorted().joinToString("$", prefix = "$")
            _state.value = _state.value.copy(vehicleOrder = newVo)
            log("VO", "Added option: $clean")
        }
    }

    fun removeVoOption(option: String) {
        val clean = option.trim().uppercase().removePrefix("$")
        if (clean.isEmpty()) return
        val current = _state.value.vehicleOrder
        val options = current.split("$").filter { it.isNotBlank() }.toMutableSet()
        if (options.remove(clean)) {
            val newVo = if (options.isEmpty()) "" else options.sorted().joinToString("$", prefix = "$")
            _state.value = _state.value.copy(vehicleOrder = newVo)
            log("VO", "Removed option: $clean")
        }
    }

    // ── CCC live map switching ───────────────────────────────────────────────

    fun writeCccMapSlot(slot: String) {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(cccLiveMapBusy = true, cccLiveMapResult = "Sending $slot…")
                val jobId = when (slot.lowercase()) {
                    "comfort" -> "ccc_write_mapslot_comfort"
                    "sport"   -> "ccc_write_mapslot_sport"
                    "race", "track" -> "ccc_write_mapslot_race"
                    else -> null
                }
                if (jobId == null) {
                    _state.value = _state.value.copy(
                        cccLiveMapResult = "Unknown slot '$slot'. Use Comfort / Sport / Race.",
                        cccLiveMapBusy = false,
                    )
                    return@runBusy
                }
                val activeSession = session ?: error("Not connected")
                val cccTarget = targets().firstOrNull { it.name == BmwTargets.CCC.name }
                    ?: error("CCC not in target list for this vehicle profile")
                activeSession.setTarget(cccTarget)
                val job = BmwJobs.byId(jobId) ?: error("Job not found: $jobId")
                val result = activeSession.execute(job)
                val msg = if (result.success)
                    "CCC map slot → $slot OK"
                else
                    "CCC map write FAILED: ${result.summary}"
                _state.value = _state.value.copy(
                    selectedMapSlot = slot,
                    tuningSummary = ExpertFunctions.tuneSummary(slot, _state.value.profile.vehicleProfile),
                    cccLiveMapResult = msg,
                    cccLiveMapBusy = false,
                )
                log(if (result.success) "CCC" else "ERROR", msg)
            }
            _state.value = _state.value.copy(cccLiveMapBusy = false)
        }
    }

    // ── Remote start / stop ──────────────────────────────────────────────────

    fun armRemoteStart() {
        _state.value = _state.value.copy(
            remoteStartArmed = true,
            remoteStartResult = "Armed — press Start or Stop to send CAS routine.",
        )
        log("CAS", "Remote start armed")
    }

    fun disarmRemoteStart() {
        _state.value = _state.value.copy(
            remoteStartArmed = false,
            remoteStartResult = "Disarmed",
        )
        log("CAS", "Remote start disarmed")
    }

    fun setRemoteStartMode(mode: RemoteStartMode) {
        _state.value = _state.value.copy(remoteStartMode = mode)
    }

    fun setSimRemoteHost(host: String) {
        _state.value = _state.value.copy(simRemoteHost = host.trim())
    }

    fun setSimRemotePort(portStr: String) {
        val port = portStr.toIntOrNull() ?: return
        _state.value = _state.value.copy(simRemotePort = port)
    }

    /** Open a dedicated TCP session to the remote SIM bridge device. */
    fun connectSimRemote() {
        viewModelScope.launch {
            val host = _state.value.simRemoteHost
            val port = _state.value.simRemotePort
            if (host.isBlank()) {
                _state.value = _state.value.copy(simConnectionResult = "Enter bridge host IP/hostname first")
                return@launch
            }
            _state.value = _state.value.copy(simConnecting = true, simConnectionResult = "Connecting to $host:$port…")
            try {
                simTransport?.disconnect()
                val t = SimRemoteTransport(host, port)
                withContext(Dispatchers.IO) { t.connect() }
                val sess = com.bmwe60coderpro.protocol.KdcanSession(
                    transport = t,
                    target = BmwTargets.CAS,
                    vehicleProfile = _state.value.profile.vehicleProfile,
                )
                simTransport = t
                simSession = sess
                _state.value = _state.value.copy(
                    simConnected = true,
                    simConnecting = false,
                    simConnectionResult = "SIM remote connected to $host:$port",
                )
                log("SIM", "Connected to $host:$port")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    simConnected = false,
                    simConnecting = false,
                    simConnectionResult = "Connection failed: ${e.message}",
                )
                log("SIM", "Connect failed: ${e.message}")
            }
        }
    }

    fun disconnectSimRemote() {
        viewModelScope.launch {
            simTransport?.disconnect()
            simTransport = null
            simSession = null
            _state.value = _state.value.copy(
                simConnected = false,
                simConnectionResult = "SIM remote disconnected",
                remoteStartArmed = false,
            )
            log("SIM", "Disconnected")
        }
    }

    /** Returns the correct session for remote start — SIM session or local K+DCAN. */
    private fun remoteStartSession(): com.bmwe60coderpro.protocol.KdcanSession? {
        return when (_state.value.remoteStartMode) {
            RemoteStartMode.SIM_REMOTE  -> simSession
            RemoteStartMode.LOCAL_KDCAN -> session
        }
    }

    fun sendRemoteStart() {
        if (!_state.value.remoteStartArmed) {
            _state.value = _state.value.copy(remoteStartResult = "Not armed — arm first")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(remoteStartBusy = true, remoteStartResult = "Checking safety interlocks…")
            try {
                val activeSession = remoteStartSession()
                    ?: error(if (_state.value.remoteStartMode == RemoteStartMode.SIM_REMOTE)
                        "SIM remote not connected — tap Connect via SIM first"
                    else "Not connected to vehicle via K+DCAN")

                if (!checkRemoteStartSafety(activeSession, logFailure = true)) {
                    _state.value = _state.value.copy(remoteStartBusy = false)
                    return@launch
                }

                _state.value = _state.value.copy(remoteStartResult = "Sending start sequence…")

                val casTarget = targets().firstOrNull { it.name == BmwTargets.CAS.name }
                    ?: error("CAS not in target list")
                activeSession.setTarget(casTarget)
                val job = BmwJobs.byId("cas_remote_start_sequence") ?: error("Job not found: cas_remote_start_sequence")
                
                val res = activeSession.execute(job)
                
                if (res.success) {
                    _state.value = _state.value.copy(
                        remoteStartResult = "Start command sent. Verifying engine run...",
                        remoteStartBusy = true
                    )
                    // Verification loop: poll RPM for 5 seconds
                    var started = false
                    for (i in 1..10) {
                        delay(500)
                        val rpmResult = pollEngineRpm(activeSession)
                        if (rpmResult > 500) {
                            started = true
                            break
                        }
                    }
                    
                    if (started) {
                        _state.value = _state.value.copy(
                            remoteStartArmed = false,
                            remoteStarted = true,
                            remoteStartResult = "Engine started successfully",
                            remoteStartBusy = false
                        )
                        startSafetyWatchdog()
                        log("CAS", "Remote start success: Engine running")
                    } else {
                        _state.value = _state.value.copy(
                            remoteStartResult = "Start sequence complete but engine did not run (RPM < 500)",
                            remoteStartBusy = false
                        )
                        log("WARN", "Remote start: Command sent but no combustion detected")
                    }
                } else {
                    _state.value = _state.value.copy(
                        remoteStartResult = "Start FAILED: ${res.summary}",
                        remoteStartBusy = false
                    )
                    log("ERROR", "Remote start failed: ${res.summary}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    remoteStartResult = "Error: ${e.message}",
                    remoteStartBusy = false,
                    remoteStartArmed = false,
                )
                log("ERROR", "Remote start: ${e.message}")
            }
        }
    }

    fun sendRemoteStop() {
        viewModelScope.launch {
            _state.value = _state.value.copy(remoteStartBusy = true, remoteStartResult = "Sending stop sequence…")
            try {
                stopSafetyWatchdog()
                val activeSession = remoteStartSession()
                    ?: error(if (_state.value.remoteStartMode == RemoteStartMode.SIM_REMOTE)
                        "SIM remote not connected"
                    else "Not connected to vehicle")

                val casTarget = targets().firstOrNull { it.name == BmwTargets.CAS.name }
                    ?: error("CAS not in target list")
                activeSession.setTarget(casTarget)
                val job = BmwJobs.byId("cas_remote_stop_sequence") ?: error("Job not found: cas_remote_stop_sequence")
                val res = activeSession.execute(job)

                val msg = if (res.success) "Engine stopped OK" else "Stop FAILED: ${res.summary}"
                _state.value = _state.value.copy(
                    remoteStartArmed = false,
                    remoteStarted = false,
                    remoteStartResult = msg,
                    remoteStartBusy = false,
                )
                log(if (res.success) "CAS" else "ERROR", msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    remoteStartResult = "Error: ${e.message}",
                    remoteStartBusy = false,
                    remoteStarted = false,
                )
                log("ERROR", "Remote stop: ${e.message}")
            }
        }
    }

    private suspend fun checkRemoteStartSafety(session: KdcanSession, logFailure: Boolean): Boolean {
        // We need to poll multiple modules to get a full safety picture
        val safetyResults = mutableMapOf<String, String>()

        // 1. Poll DME for battery voltage
        val dmeRes = runCatching { session.execute(BmwJobs.byId("dme_live_basic")!!) }.getOrNull()
        dmeRes?.decoded?.let { safetyResults.putAll(it) }

        // 2. Poll EGS for Gear position
        session.setTarget(BmwTargets.EGS)
        val egsRes = runCatching { session.execute(BmwJobs.byId("egs_live_basic")!!) }.getOrNull()
        egsRes?.decoded?.let { safetyResults.putAll(it) }

        // 3. Poll CAS for Terminal status (includes some hood/brake signals on some E60 variants)
        session.setTarget(BmwTargets.CAS)
        val casRes = runCatching { session.execute(BmwJobs.byId("cas_live_terminals")!!) }.getOrNull()
        casRes?.decoded?.let { safetyResults.putAll(it) }

        // Map raw values to safety flags
        val gearRaw = safetyResults["EGS basic live 0x01:selector_position_raw"]?.toIntOrNull() ?: 0
        // E60 EGS: P is usually 0x01 or 0x02. 0x01 is common for GS19.
        val gearP = gearRaw == 1 || gearRaw == 2 || safetyResults["EGS basic live 0x01:selector_position_raw"]?.contains("P") == true
        
        // Hood status is often in CAS terminal flags or specialized FRM blocks. 
        // Heuristic: check terminal_flags bit 5 (often used for hood/trunk inhibit)
        val termFlags = safetyResults["CAS terminal block 0x01:terminal_flags"] ?: "00000000"
        val hoodClosed = termFlags.length > 5 && termFlags[5] == '0'
            
        // Brake status can be in DSC or DME. DME basic block 0x01 doesn't have it, but DSC status 0x01 does.
        session.setTarget(BmwTargets.DSC)
        val dscRes = runCatching { session.execute(BmwJobs.byId("dsc_live_status")!!) }.getOrNull()
        val brakePressed = dscRes?.decoded?.get("DSC status 0x01:status_flags_1")?.let { it.length > 0 && it[0] == '1' } ?: false
        val brakeReleased = !brakePressed
        
        val voltage = safetyResults["DME basic live 0x01:battery_v"]?.toDoubleOrNull() ?: 12.0
        val voltageOk = voltage > 11.5

        _state.value = _state.value.copy(
            safetyGearP = gearP,
            safetyHoodClosed = hoodClosed,
            safetyBrakeReleased = brakeReleased,
            safetyVoltageOk = voltageOk
        )

        if (!gearP || !hoodClosed || !brakeReleased || !voltageOk) {
            if (logFailure) {
                val reasons = mutableListOf<String>()
                if (!gearP) reasons.add("Gear not in P (Raw: $gearRaw)")
                if (!hoodClosed) reasons.add("Hood open or inhibited")
                if (!brakeReleased) reasons.add("Brake pressed")
                if (!voltageOk) reasons.add("Low voltage (%.1fV)".format(voltage))
                val msg = "Safety Interlock: ${reasons.joinToString(", ")}"
                _state.value = _state.value.copy(remoteStartResult = msg)
                log("WARN", msg)
            }
            return false
        }
        return true
    }

    private suspend fun pollEngineRpm(session: KdcanSession): Int {
        val dmeTarget = targets().firstOrNull { it.name == BmwTargets.DME.name } ?: return 0
        session.setTarget(dmeTarget)
        val job = BmwJobs.byId("dme_live_basic") ?: return 0
        val res = runCatching { session.execute(job) }.getOrNull()
        val rpmStr = res?.decoded?.get("DME basic live 0x01: engine_speed_rpm") ?: "0"
        val rpm = rpmStr.substringBefore(" ").toDoubleOrNull()?.toInt() ?: 0
        _state.value = _state.value.copy(safetyRpm = rpm)
        return rpm
    }

    private fun startSafetyWatchdog() {
        safetyWatchdogJob?.cancel()
        safetyWatchdogJob = viewModelScope.launch {
            log("CAS", "Safety watchdog active")
            while (_state.value.remoteStarted) {
                delay(2000)
                val activeSession = remoteStartSession() ?: break
                if (!checkRemoteStartSafety(activeSession, logFailure = false)) {
                    log("WARN", "Safety violation detected! Emergency stopping...")
                    sendRemoteStop()
                    break
                }
                // Also check if engine stalled
                val rpm = pollEngineRpm(activeSession)
                if (rpm < 400) {
                    log("WARN", "Engine stall detected. Disarming.")
                    _state.value = _state.value.copy(remoteStarted = false, remoteStartResult = "Engine stalled")
                    break
                }
            }
        }
    }

    private fun stopSafetyWatchdog() {
        safetyWatchdogJob?.cancel()
        safetyWatchdogJob = null
    }
    fun applyWarningSuppression(presetKind: CodingPresetKind) {
        val preset = DatenManager.preset(presetKind)
        // Determine target module from first change
        val moduleName = preset.changes.firstOrNull()?.module ?: "KOMBI"
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(warningSuppressResult = "Applying ${preset.label}…")
                val activeSession = session ?: error("Not connected")
                val target = targets().firstOrNull { it.name.equals(moduleName, ignoreCase = true) }
                    ?: error("Module $moduleName not in target list")
                activeSession.setTarget(target)
                // Build a coding document from the template + preset
                val baseText = DatenManager.templateFor(moduleName)
                val patched = DatenManager.applyPreset(baseText, preset)
                val doc = DatenManager.parse(patched)
                val payload = DatenManager.buildCodingWritePayload(doc)
                // Extended session + write
                activeSession.execute(BmwJobs.byId("start_session_extended") ?: error("Job not found: start_session_extended"))
                val writeJob = BmwJob(
                    id = "suppress_write_${moduleName}",
                    label = "Warning suppress: ${preset.label}",
                    category = JobCategory.CONTROL,
                    steps = listOf(JobStep(serviceId = 0x3B, payload = listOf(0x9B) + payload, label = "Write 0x3B")),
                    description = "Apply warning suppression preset ${preset.label} to $moduleName",
                    readOnly = false,
                    supportedTargets = setOf(target.name),
                )
                val result = activeSession.execute(writeJob)
                val msg = if (result.success)
                    "${preset.label} → $moduleName OK"
                else
                    "${preset.label} FAILED: ${result.summary}"
                _state.value = _state.value.copy(
                    warningSuppressResult = msg,
                    codingText = patched,
                    codingModule = moduleName,
                )
                log(if (result.success) "WARN" else "ERROR", msg)
            }
        }
    }

    fun updateFlashInputHex(value: String) {
        _state.value = _state.value.copy(flashInputHex = value)
    }

    fun setFlashMode(mode: FlashMode) {
        _state.value = _state.value.copy(flashMode = mode)
    }

    fun buildFlashPlan() {
        val plan = FlashingManager.plan(_state.value.flashingModule, _state.value.flashInputHex, _state.value.flashMode)
        _state.value = _state.value.copy(lastFlashPlan = plan, flashPlanSummary = plan.summary)
        log("FLASH", plan.summary)
    }

    fun setRemoteSafetyMode(mode: RemoteSafetyMode) {
        _state.value = _state.value.copy(remoteSafetyMode = mode)
    }

    fun prepareExperimentalFeature(name: String) {
        val modeText = if (_state.value.remoteSafetyMode == RemoteSafetyMode.SAFE_SIMULATION) "safe simulation" else "experimental only"
        val summary = "$name prepared in $modeText mode. No live actuation command was armed automatically."
        _state.value = _state.value.copy(experimentSummary = summary)
        log("EXP", summary)
    }

    private suspend fun stopPollingInternal(logStop: Boolean) {
        pollingJob?.cancel()
        pollingJob = null
        lastPollFailureByJob.clear()
        _state.value = _state.value.copy(
            pollingEnabled = false,
            dashboardStatus = if (_state.value.connected) "Connected, polling stopped" else "Disconnected"
        )
        if (logStop) log("INFO", "Dashboard polling stopped")
    }

    private fun runJobOnTarget(targetId: String, jobId: String) {
        viewModelScope.launch {
            runBusy {
                val activeSession = session ?: error("Not connected")
                val target = targets().firstOrNull { it.name == targetId } ?: error("Unknown target $targetId")
                val job = BmwJobs.byId(jobId)?.takeIf { it.appliesTo(target) }
                    ?: error("Unknown job $jobId for target ${target.name}")
                // A manual diagnostic request must not compete with the dashboard for the
                // shared serial interface. The next polling run can be started explicitly.
                stopPollingInternal(false)
                activeSession.setVehicleProfile(_state.value.profile.vehicleProfile)
                val result = activeSession.executeOnTarget(target, job)
                applyJobResult(result, updateSelection = true, logDetail = true)
            }
        }
    }

    private fun applyJobResult(
        result: com.bmwe60coderpro.protocol.JobResult,
        updateSelection: Boolean,
        logDetail: Boolean,
        snapshotTitleSuffix: String = "",
    ) {
        val snapshots = _state.value.moduleSnapshots.toMutableMap()
        snapshots[result.target.name] = DiagnosticSnapshotReducer.apply(
            previous = snapshots[result.target.name],
            result = result,
            snapshotTitleSuffix = snapshotTitleSuffix,
            timestamp = timestamp(),
        )
        _state.value = _state.value.copy(
            selectedTargetId = if (updateSelection) result.target.name else _state.value.selectedTargetId,
            selectedJobId = if (updateSelection) result.job.id else _state.value.selectedJobId,
            rawResponse = result.responseHex,
            lastJobSummary = result.summary,
            vehicleInfo = "${result.target.name}: ${result.summary}",
            decodedFields = result.decoded,
            moduleSnapshots = snapshots,
            activeCommProfile = result.decoded["comm_profile"] ?: _state.value.activeCommProfile,
        )
        if (logDetail) {
            log("JOB", "${result.target.name} -> ${result.job.label}")
            log("TX", result.requestHex)
            log(if (result.success) "RX" else "NEG", result.responseHex.ifBlank { "<empty>" })
        }
    }

    private fun dashboardPollPlan(): List<Pair<String, String>> {
        val plan = when (_state.value.profile.vehicleProfile) {
            // On the E60 LCI N52 profile, the DME rejects the generic BMW 0x21
            // local identifiers. Poll standard OBD Mode 01 data directly from the
            // DME instead. Every PID is independent, so optional voltage support
            // cannot suppress the core RPM, speed, coolant, or throttle gauges.
            VehicleProfileKind.N52_6HP -> listOf(
                BmwTargets.DME.name to "n52_obd_rpm",
                BmwTargets.DME.name to "n52_obd_speed",
                BmwTargets.DME.name to "n52_obd_coolant",
                BmwTargets.DME.name to "n52_obd_throttle",
                BmwTargets.DME.name to "n52_obd_voltage",
            )
            else -> listOf(
                BmwTargets.DME.name to "dme_live_basic",
                BmwTargets.DME.name to "dme_live_air",
                BmwTargets.EGS.name to "egs_live_basic",
                BmwTargets.EGS.name to "egs_live_temp",
                BmwTargets.DSC.name to "dsc_live_status",
                BmwTargets.DSC.name to "dsc_live_wheels",
                // Cluster drive data is a useful read-only fallback for RPM/speed when an
                // ECU-specific DME or DSC local identifier is unavailable on this car.
                BmwTargets.KOMBI.name to "kombi_live_drive",
                BmwTargets.CAS.name to "cas_live_terminals",
            )
        }
        return plan.filter { (targetId, jobId) ->
            targets().any { it.name == targetId } && BmwJobs.byId(jobId) != null
        }
    }

    private fun currentTarget(): EcuTarget {
        return targets().firstOrNull { it.name == _state.value.selectedTargetId } ?: BmwTargets.DME
    }

    private fun saveBackup(module: String, content: String, isVo: Boolean = false) {
        val backup = CodingBackup(
            timestamp = System.currentTimeMillis(),
            module = module,
            content = content,
            isVo = isVo
        )
        _state.value = _state.value.copy(
            codingBackups = (listOf(backup) + _state.value.codingBackups).take(20)
        )
        log("BACKUP", "Saved $module backup (${if (isVo) "VO" else "Coding"})")
    }

    fun restoreBackup(backup: CodingBackup) {
        if (backup.isVo) {
            _state.value = _state.value.copy(vehicleOrder = backup.content)
        } else {
            _state.value = _state.value.copy(
                codingText = backup.content,
                codingModule = backup.module
            )
        }
        log("BACKUP", "Restored ${backup.module} from ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(backup.timestamp))}")
    }

    fun clearBackups() {
        _state.value = _state.value.copy(codingBackups = emptyList())
        log("BACKUP", "All backups cleared")
    }

    fun readTuningMaps() {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(tuningLiveBusy = true, tuningReadResult = "Reading maps...")
                val activeSession = session ?: error("Not connected")
                activeSession.setTarget(BmwTargets.DME)

                val fuelJob = BmwJobs.byId("dme_read_fuel_map") ?: error("Fuel job missing")
                val fuelResult = activeSession.execute(fuelJob)

                val ignitionJob = BmwJobs.byId("dme_read_ignition_map") ?: error("Ignition job missing")
                val ignitionResult = activeSession.execute(ignitionJob)

                if (fuelResult.success && ignitionResult.success) {
                    val fMap = parseTuningMap("Fuel", fuelResult.responseHex)
                    val iMap = parseTuningMap("Ignition", ignitionResult.responseHex)
                    _state.value = _state.value.copy(
                        fuelMap = fMap,
                        ignitionMap = iMap,
                        tuningReadResult = "Maps read successfully",
                        tuningLiveBusy = false
                    )
                    log("TUNE", "Fuel and Ignition maps loaded")
                } else {
                    _state.value = _state.value.copy(
                        tuningReadResult = "Error reading maps: ${fuelResult.summary} / ${ignitionResult.summary}",
                        tuningLiveBusy = false
                    )
                }
            }
        }
    }

    private fun parseTuningMap(name: String, hex: String): TuningMap {
        // Mock parsing: 8x8 map
        val rpmAxis = listOf(800, 1200, 2000, 3000, 4000, 5000, 6000, 7000)
        val loadAxis = listOf(10, 25, 40, 55, 70, 85, 100, 120)
        val rows = mutableListOf<List<Float>>()
        for (i in 0 until 8) {
            val row = mutableListOf<Float>()
            for (j in 0 until 8) {
                row.add(if (name == "Fuel") 14.7f else 15.0f + (i + j) / 2f)
            }
            rows.add(row)
        }
        return TuningMap(name.lowercase(), name, rpmAxis, loadAxis, rows)
    }

    fun updateMapValue(isFuel: Boolean, row: Int, col: Int, newValue: Float) {
        val currentMap = if (isFuel) _state.value.fuelMap else _state.value.ignitionMap
        currentMap?.let { map ->
            val newTable = map.table.mapIndexed { rIdx, rList ->
                if (rIdx == row) {
                    rList.mapIndexed { cIdx, v -> if (cIdx == col) newValue else v }
                } else rList
            }
            val newMap = map.copy(table = newTable)
            _state.value = if (isFuel) _state.value.copy(fuelMap = newMap) else _state.value.copy(ignitionMap = newMap)
        }
    }

    fun writeTuningMaps() {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(tuningLiveBusy = true, tuningWriteResult = "Writing maps...")
                // Mock write
                delay(1500)
                _state.value = _state.value.copy(
                    tuningWriteResult = "Maps written successfully (Simulation)",
                    tuningLiveBusy = false
                )
                log("TUNE", "Map write complete (Simulation)")
            }
        }
    }

    fun runInjectionAction(action: CanInjector.InjectableAction) {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(injectionBusy = true, injectionResult = "Injecting ${action.label}...")
                val activeSession = session ?: simSession
                if (activeSession == null) {
                    _state.value = _state.value.copy(
                        injectionBusy = false,
                        injectionResult = "Not connected to vehicle or SIM bridge."
                    )
                    return@runBusy
                }

                val res = CanInjector.inject(activeSession, action)
                _state.value = _state.value.copy(
                    injectionBusy = false,
                    injectionResult = "${action.label}: ${if (res.success) "Success" else "Failed (${res.summary})"}"
                )
                log(if (res.success) "INFO" else "ERROR", "Injection ${action.label}: ${res.summary}")
            }
        }
    }

    fun runInjectionMacro(macro: CanInjector.InjectionMacro) {
        viewModelScope.launch {
            runBusy {
                _state.value = _state.value.copy(injectionBusy = true, injectionResult = "Starting Macro: ${macro.label}...")
                val activeSession = session ?: simSession
                if (activeSession == null) {
                    _state.value = _state.value.copy(
                        injectionBusy = false,
                        injectionResult = "Not connected for macro execution."
                    )
                    return@runBusy
                }

                for ((index, actionId) in macro.actions.withIndex()) {
                    val action = CanInjector.ACTIONS.find { it.id == actionId }
                    if (action == null) {
                        log("ERROR", "Macro ${macro.id}: Action $actionId not found")
                        continue
                    }

                    _state.value = _state.value.copy(injectionResult = "Macro [${index + 1}/${macro.actions.size}]: ${action.label}...")
                    val res = CanInjector.inject(activeSession, action)
                    
                    if (!res.success) {
                        log("ERROR", "Macro ${macro.id} failed at ${action.label}: ${res.summary}")
                        _state.value = _state.value.copy(
                            injectionBusy = false,
                            injectionResult = "Macro Failed: ${action.label}"
                        )
                        return@runBusy
                    }

                    if (index < macro.actions.size - 1) {
                        delay(macro.delayBetweenMs)
                    }
                }

                _state.value = _state.value.copy(
                    injectionBusy = false,
                    injectionResult = "Macro Complete: ${macro.label}"
                )
                log("INFO", "Macro ${macro.label} executed successfully")
            }
        }
    }

    fun injectRawCanFrame(messageName: String, payloadHex: String) {
        viewModelScope.launch {
            val message = E60CanBus.byName(messageName) ?: return@launch
            val payload = HexUtils.hexToBytes(payloadHex).map { it.toInt() and 0xFF }
            val activeSession = session ?: simSession
            if (activeSession != null) {
                val res = CanInjector.sendRawCanFrame(activeSession, message, payload)
                log("INFO", "Raw CAN Inject 0x${message.id.toString(16)}: $res")
            }
        }
    }

    private inline suspend fun runBusy(block: suspend () -> Unit) {
        try {
            _state.value = _state.value.copy(busy = true)
            block()
        } catch (t: Throwable) {
            log("ERROR", t.message ?: t.javaClass.simpleName)
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    private fun buildTransport(profile: ConnectionProfile): Transport {
        return when (profile.transport) {
            TransportType.USB_KDCAN -> UsbSerialTransport(
                application = application,
                permissionManager = UsbPermissionManager(application),
                baudRate = profile.baudRate,
            )
            TransportType.ETHERNET_OBD -> TcpObdTransport(profile.tcpHost, profile.tcpPort, profile.connectTimeoutMs, profile.readTimeoutMs)
            TransportType.BLUETOOTH_OBD -> {
                if (profile.bluetoothMac.isBlank()) {
                    _state.value = _state.value.copy(dashboardStatus = "Bluetooth MAC address required")
                    throw IllegalArgumentException("Bluetooth MAC address required")
                }
                BluetoothTransport(application, profile.bluetoothMac, profile.connectTimeoutMs, profile.readTimeoutMs)
            }
        }
    }

    private fun log(level: String, message: String) {
        _state.value = _state.value.copy(logs = listOf(LogEntry(timestamp(), level, message)) + _state.value.logs)
    }

    private fun parseHexBytes(hexString: String): List<Int> {
        val trimmed = hexString.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (' ' in trimmed) {
            trimmed.split(" ").mapNotNull { it.toIntOrNull(16) }
        } else {
            trimmed.chunked(2).mapNotNull { it.toIntOrNull(16) }
        }
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
                }
            }
    }
}