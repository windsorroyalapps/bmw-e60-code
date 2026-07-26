package com.bmwe60coderpro.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bmwe60coderpro.data.AdapterPresetKind
import com.bmwe60coderpro.data.AppPreferencesRepository
import com.bmwe60coderpro.data.AdapterPresets
import com.bmwe60coderpro.data.AppState
import com.bmwe60coderpro.data.CodingPresetKind
import com.bmwe60coderpro.data.ConnectionProfile
import com.bmwe60coderpro.data.FlashMode
import com.bmwe60coderpro.data.LogEntry
import com.bmwe60coderpro.data.ModuleSnapshot
import com.bmwe60coderpro.data.RemoteSafetyMode
import com.bmwe60coderpro.data.ServiceScreen
import com.bmwe60coderpro.data.TransportType
import com.bmwe60coderpro.data.VehicleProfileKind
import com.bmwe60coderpro.data.RemoteStartMode
import com.bmwe60coderpro.network.SimRemoteTransport
import com.bmwe60coderpro.network.TcpObdTransport
import com.bmwe60coderpro.protocol.BmwCommProfiles
import com.bmwe60coderpro.protocol.BmwJobs
import com.bmwe60coderpro.protocol.BmwTargets
import com.bmwe60coderpro.protocol.DatenManager
import com.bmwe60coderpro.protocol.E60AddressBook
import com.bmwe60coderpro.protocol.EcuTarget
import com.bmwe60coderpro.protocol.ExpertFunctions
import com.bmwe60coderpro.protocol.FlashingManager
import com.bmwe60coderpro.protocol.KdcanSession
import com.bmwe60coderpro.protocol.SteeringRetrofitManager
import com.bmwe60coderpro.protocol.SteeringRetrofitPresetKind
import com.bmwe60coderpro.protocol.Transport
import com.bmwe60coderpro.usb.UsbSerialTransport
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.bmwe60coderpro.controller.ControllerInjector
import com.bmwe60coderpro.controller.XboxControllerManager
import com.bmwe60coderpro.protocol.SzlButtonDecoder
import com.bmwe60coderpro.protocol.MflInjector
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
    // Separate session used for SIM remote start — does not share the main K+DCAN session
    private var simSession: KdcanSession? = null
    private var simTransport: SimRemoteTransport? = null
    private var szlMonitorJob: Job? = null
    private var controllerJob: Job? = null
    // Latest axes from MotionEvent — written from Activity thread, read by coroutine
    @Volatile private var latestAxes = com.bmwe60coderpro.controller.ControllerAxes()
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
        val currentProfile = _state.value.profile.copy(vehicleProfile = kind)
        val targets = E60AddressBook.targetsFor(kind)
        val currentTarget = targets.firstOrNull { it.name == _state.value.selectedTargetId } ?: targets.firstOrNull() ?: BmwTargets.DME
        val nextJobs = BmwJobs.forTarget(currentTarget)
        val preset = AdapterPresets.byKind(vp.recommendedPreset)
        _state.value = _state.value.copy(
            activeVehicleProfile = vp.label,
            selectedTargetId = currentTarget.name,
            selectedJobId = nextJobs.firstOrNull()?.id ?: _state.value.selectedJobId,
            activeCommProfile = BmwCommProfiles.forTarget(currentTarget, kind).name,
            selectedTransport = preset.transport,
            profile = currentProfile.copy(
                transport = preset.transport,
                adapterPreset = vp.recommendedPreset,
                baudRate = preset.baudRate ?: currentProfile.baudRate,
                tcpHost = preset.tcpHost ?: currentProfile.tcpHost,
                tcpPort = preset.tcpPort ?: currentProfile.tcpPort,
                connectTimeoutMs = preset.connectTimeoutMs,
                readTimeoutMs = preset.readTimeoutMs,
                settleDelayMs = preset.settleDelayMs,
            ),
            tuningSummary = ExpertFunctions.tuneSummary(_state.value.selectedMapSlot, kind),
        )
        prefs.saveProfile(_state.value.profile)
        session?.setVehicleProfile(kind)
        session?.setTarget(currentTarget)
        log("INFO", "Vehicle profile selected: ${vp.label}")
    }

    fun applyAdapterPreset(kind: AdapterPresetKind) {
        val preset = AdapterPresets.byKind(kind)
        _state.value = _state.value.copy(
            selectedTransport = preset.transport,
            profile = _state.value.profile.copy(
                transport = preset.transport,
                baudRate = preset.baudRate ?: _state.value.profile.baudRate,
                tcpHost = preset.tcpHost ?: _state.value.profile.tcpHost,
                tcpPort = preset.tcpPort ?: _state.value.profile.tcpPort,
                connectTimeoutMs = preset.connectTimeoutMs,
                readTimeoutMs = preset.readTimeoutMs,
                settleDelayMs = preset.settleDelayMs,
                adapterPreset = preset.kind,
            )
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

    fun connect() {
        viewModelScope.launch {
            runBusy {
                stopPollingInternal(false)
                transport?.disconnect()
                transport = buildTransport(_state.value.profile)
                transport!!.connect(_state.value.selectedDeviceId)
                session = KdcanSession(transport!!, currentTarget(), _state.value.profile.vehicleProfile)
                session!!.onConnected(_state.value.profile.settleDelayMs)
                val profile = session!!.getCommProfile()
                val vehicleInfo = session!!.tryIdentify()
                _state.value = _state.value.copy(
                    connected = true,
                    vehicleInfo = vehicleInfo,
                    activeCommProfile = profile.name,
                    activeVehicleProfile = E60AddressBook.byKind(_state.value.profile.vehicleProfile).label,
                    dashboardStatus = "Connected, polling stopped",
                )
                log("INFO", "Connected to ${currentTarget().name}")
            }
        }
    }

    fun disconnect() {
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
                val pollJobs = dashboardPollPlan()
                pollJobs.forEach { (targetId, jobId) ->
                    if (!_state.value.connected || !_state.value.pollingEnabled) return@forEach
                    val target = targets().firstOrNull { it.name == targetId } ?: return@forEach
                    val job = BmwJobs.byId(jobId)?.takeIf { it.appliesTo(target) } ?: return@forEach
                    runCatching {
                        activeSession.setVehicleProfile(_state.value.profile.vehicleProfile)
                        activeSession.setTarget(target)
                        val result = activeSession.execute(job)
                        applyJobResult(result, updateSelection = false, logDetail = false, snapshotTitleSuffix = " [poll]")
                    }.onFailure { t ->
                        log("POLL", "${target.name} ${job.label}: ${t.message ?: t.javaClass.simpleName}")
                    }
                    delay(100)
                }
                _state.value = _state.value.copy(dashboardStatus = "Last refresh ${timestamp()} • interval ${_state.value.pollingIntervalMs} ms")
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
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A     -> if (isDown) toggleControllerArmed()
            KeyEvent.KEYCODE_BUTTON_B     -> if (isDown) triggerEmergencyStop()
            KeyEvent.KEYCODE_BUTTON_R1    -> if (isDown) sendPaddleEvent(up = true)
            KeyEvent.KEYCODE_BUTTON_L1    -> if (isDown) sendPaddleEvent(up = false)
            KeyEvent.KEYCODE_BUTTON_START -> if (isDown) log("CTRL", "Sport mode on (hint)")
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE ->
                if (isDown) log("CTRL", "Sport mode off (hint)")
            else -> return false
        }
        return true
    }

    fun setControllerDryRun(dryRun: Boolean) {
        _state.value = _state.value.copy(controllerDryRun = dryRun)
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
            log("CTRL", "Controller bridge started (dryRun=${_state.value.controllerDryRun})")
            controllerTickCount = 0L
            controllerLastTickMs = System.currentTimeMillis()
            try {
                while (true) {
                    val st = _state.value
                    val axes = latestAxes
                    val commands = XboxControllerManager.buildCommands(
                        axes             = axes,
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
                        val activeSession = session
                        if (activeSession != null) {
                            val result = ControllerInjector.tick(
                                session       = activeSession,
                                commands      = commands,
                                sendThrottle  = st.controllerSendThrottle,
                                sendSteering  = st.controllerSendSteering,
                                sendBrake     = st.controllerSendBrake,
                                dryRun        = st.controllerDryRun,
                            )
                            val nowMs = System.currentTimeMillis()
                            val elapsed = (nowMs - controllerLastTickMs).coerceAtLeast(1L)
                            controllerLastTickMs = nowMs
                            controllerTickCount++
                            val hz = "%.1f Hz".format(1000.0 / elapsed)

                            if (controllerTickCount % 10L == 0L) { // log every 10 ticks
                                val logLine = "${timestamp()} ${result.summary}"
                                val newLog = (_state.value.controllerLog + logLine).takeLast(40)
                                _state.value = _state.value.copy(
                                    controllerLog    = newLog,
                                    controllerTickHz = hz,
                                )
                            } else {
                                _state.value = _state.value.copy(controllerTickHz = hz)
                            }
                        }
                    }

                    delay(20L) // 50 Hz target loop — fast enough for smooth feel on K-line
                }
            } catch (e: CancellationException) {
                // normal stop
            } catch (t: Throwable) {
                log("CTRL", "Bridge error: ${t.message ?: t.javaClass.simpleName}")
            } finally {
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
                    dryRun  = _state.value.controllerDryRun,
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
            com.bmwe60coderpro.protocol.MflInjector.inject(activeSession, event, _state.value.controllerDryRun)
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
                activeSession.setTarget(target)
                // Start default session, then read coding record 0x9B
                val sessionJob = BmwJobs.byId("start_session_default")!!
                activeSession.execute(sessionJob)
                val readJob = BmwJobs.byId("read_coding_9B")!!
                val result = activeSession.execute(readJob)
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
                val target = targets().firstOrNull { it.name.equals(doc.module, ignoreCase = true) }
                    ?: error("Unknown module ${doc.module}. Set module name in first line of coding text.")
                activeSession.setTarget(target)
                // Extended session required for write
                val extSession = BmwJobs.byId("start_session_extended")!!
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
                val job = BmwJobs.byId(jobId)!!
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
            _state.value = _state.value.copy(remoteStartBusy = true, remoteStartResult = "Sending start sequence…")
            try {
                val activeSession = remoteStartSession()
                    ?: error(if (_state.value.remoteStartMode == RemoteStartMode.SIM_REMOTE)
                        "SIM remote not connected — tap Connect via SIM first"
                    else "Not connected to vehicle via K+DCAN")
                val casTarget = targets().firstOrNull { it.name == BmwTargets.CAS.name }
                    ?: error("CAS not in target list")
                activeSession.setTarget(casTarget)
                val job = BmwJobs.byId("cas_remote_start_sequence")!!
                val result = activeSession.execute(job)
                val msg = if (result.success) "Start sequence sent OK — ${result.summary}"
                          else "Start FAILED: ${result.summary}"
                _state.value = _state.value.copy(
                    remoteStartArmed = false,
                    remoteStartResult = msg,
                    remoteStartBusy = false,
                )
                log(if (result.success) "CAS" else "ERROR", msg)
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
        if (!_state.value.remoteStartArmed) {
            _state.value = _state.value.copy(remoteStartResult = "Not armed — arm first")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(remoteStartBusy = true, remoteStartResult = "Sending stop sequence…")
            try {
                val activeSession = remoteStartSession()
                    ?: error(if (_state.value.remoteStartMode == RemoteStartMode.SIM_REMOTE)
                        "SIM remote not connected — tap Connect via SIM first"
                    else "Not connected to vehicle via K+DCAN")
                val casTarget = targets().firstOrNull { it.name == BmwTargets.CAS.name }
                    ?: error("CAS not in target list")
                activeSession.setTarget(casTarget)
                val job = BmwJobs.byId("cas_remote_stop_sequence")!!
                val result = activeSession.execute(job)
                val msg = if (result.success) "Stop sequence sent OK — ${result.summary}"
                          else "Stop FAILED: ${result.summary}"
                _state.value = _state.value.copy(
                    remoteStartArmed = false,
                    remoteStartResult = msg,
                    remoteStartBusy = false,
                )
                log(if (result.success) "CAS" else "ERROR", msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    remoteStartResult = "Error: ${e.message}",
                    remoteStartBusy = false,
                    remoteStartArmed = false,
                )
                log("ERROR", "Remote stop: ${e.message}")
            }
        }
    }

    // ── Warning light suppression ────────────────────────────────────────────

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
                activeSession.execute(BmwJobs.byId("start_session_extended")!!)
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
                activeSession.setVehicleProfile(_state.value.profile.vehicleProfile)
                activeSession.setTarget(target)
                val result = activeSession.execute(job)
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
        snapshots[result.target.name] = ModuleSnapshot(
            targetId = result.target.name,
            title = result.job.label + snapshotTitleSuffix,
            summary = result.summary,
            decoded = result.decoded,
            rawResponse = result.responseHex,
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
            activeCommProfile = session?.getCommProfile()?.name ?: _state.value.activeCommProfile,
        )
        if (logDetail) {
            log("JOB", "${result.target.name} -> ${result.job.label}")
            log("TX", result.requestHex)
            log(if (result.success) "RX" else "NEG", result.responseHex.ifBlank { "<empty>" })
        }
    }

    private fun dashboardPollPlan(): List<Pair<String, String>> = listOf(
        BmwTargets.DME.name to "dme_live_basic",
        BmwTargets.EGS.name to "egs_live_basic",
        BmwTargets.DSC.name to "dsc_live_wheels",
        BmwTargets.CAS.name to "cas_live_terminals",
    ).filter { (targetId, jobId) ->
        targets().any { it.name == targetId } && BmwJobs.byId(jobId) != null
    }

    private fun currentTarget(): EcuTarget {
        return targets().firstOrNull { it.name == _state.value.selectedTargetId } ?: BmwTargets.DME
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
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
            TransportType.USB_KDCAN -> UsbSerialTransport(application, profile.baudRate, profile.readTimeoutMs)
            TransportType.ETHERNET_OBD -> TcpObdTransport(profile.tcpHost, profile.tcpPort, profile.connectTimeoutMs, profile.readTimeoutMs)
        }
    }

    private fun log(level: String, message: String) {
        _state.value = _state.value.copy(logs = listOf(LogEntry(timestamp(), level, message)) + _state.value.logs)
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
