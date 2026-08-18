package com.bmwe60coderpro.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bmwe60coderpro.data.AdapterPresets
import com.bmwe60coderpro.data.AppState
import com.bmwe60coderpro.data.CodingPresetKind
import com.bmwe60coderpro.data.FlashMode
import com.bmwe60coderpro.data.ModuleSnapshot
import com.bmwe60coderpro.data.RemoteStartMode
import com.bmwe60coderpro.data.RemoteSafetyMode
import com.bmwe60coderpro.data.ServiceScreen
import com.bmwe60coderpro.data.TuningMap
import com.bmwe60coderpro.data.TransportType
import com.bmwe60coderpro.protocol.DatenManager
import com.bmwe60coderpro.protocol.E60AddressBook
import com.bmwe60coderpro.protocol.CanInjector
import com.bmwe60coderpro.protocol.E60CanBus
import com.bmwe60coderpro.protocol.MflInjector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rawHexState = remember { mutableStateOf("68 6A F1 01 00") }
    val pollingIntervalState = remember(state.pollingIntervalMs) { mutableStateOf(state.pollingIntervalMs.toString()) }
    val codingState = remember(state.codingText) { mutableStateOf(state.codingText) }
    val flashHexState = remember(state.flashInputHex) { mutableStateOf(state.flashInputHex) }
    val selectedPreset = AdapterPresets.byKind(state.profile.adapterPreset)
    val selectedVehicleProfile = E60AddressBook.byKind(state.profile.vehicleProfile)
    val activeScreen = ServiceScreens.byScreen(state.selectedServiceScreen)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("BMW E60 Coder Pro", style = MaterialTheme.typography.headlineMedium)
            Text("Native BMW E60 diagnostic, coding, tuning, and experimental tooling project with USB/Ethernet transport and dedicated service pages.")
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.serviceScreens.forEach { def ->
                    OutlinedButton(onClick = { vm.selectServiceScreen(def.screen) }) {
                        Text(if (state.selectedServiceScreen == def.screen) "✓ ${def.title}" else def.title)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activeScreen.title, style = MaterialTheme.typography.titleLarge)
                    Text(activeScreen.subtitle)
                    if (activeScreen.targetId != null) {
                        Text("Module target: ${activeScreen.targetId}")
                        Text("Address map: ${E60AddressBook.describeTarget(state.profile.vehicleProfile, activeScreen.targetId)}")
                    }
                }
            }
        }

        when (state.selectedServiceScreen) {
            ServiceScreen.OVERVIEW -> {
                item {
                    LiveDashboardCard(
                        state = state,
                        pollingInterval = pollingIntervalState.value,
                        onPollingIntervalChange = {
                            pollingIntervalState.value = it
                            vm.updatePollingInterval(it)
                        },
                        onStart = vm::startDashboardPolling,
                        onStop = vm::stopDashboardPolling,
                    )
                }
                item { DashboardSnapshotGrid(state) }
                item { VehicleProfileCard(state, vm, selectedVehicleProfile.label, selectedVehicleProfile.notes) }
                item { AdapterPresetCard(state, vm, selectedPreset.label, selectedPreset.notes) }
                item { TransportCard(state = state, vm = vm) }
                item {
                    if (state.showConnectionPopup) {
                        ConnectionStatusPopup(state = state, vm = vm)
                    }
                }
                item { OverviewJobsCard(state, vm, rawHexState.value, { rawHexState.value = it }) }
            }
            ServiceScreen.CODING -> item {
                CodingScreen(state, vm, codingState.value, onTextChange = {
                    codingState.value = it
                    vm.updateCodingText(it)
                })
            }
            ServiceScreen.TUNING -> item { TuningScreen(state, vm) }
            ServiceScreen.CCC -> item { CccScreen(state, vm) }
            ServiceScreen.STEERING -> item { SteeringScreen(state, vm) }
            ServiceScreen.FLASHING -> item {
                FlashingScreen(state, vm, flashHexState.value, onHexChange = {
                    flashHexState.value = it
                    vm.updateFlashInputHex(it)
                })
            }
            ServiceScreen.EXPERIMENTS -> item { ExperimentalScreen(state, vm) }
            ServiceScreen.GAUGES -> item { GaugesScreen(state, vm) }
            ServiceScreen.INJECTION -> item { InjectionScreen(state, vm) }
            else -> {
                item { TransportCompactCard(state = state, vm = vm) }
                item {
                    if (state.showConnectionPopup) {
                        ConnectionStatusPopup(state = state, vm = vm)
                    }
                }
                item {
                    DedicatedServiceModuleCard(
                        vm = vm,
                        state = state,
                        def = activeScreen,
                        snapshot = activeScreen.targetId?.let { state.moduleSnapshots[it] }
                    )
                }
            }
        }

        item { DecodedPayloadCard(state.decodedFields) }
        item { Text("Logs") }
        items(state.logs) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${entry.timestamp} [${entry.level}]", fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text(entry.message, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun VehicleProfileCard(state: AppState, vm: MainViewModel, label: String, notes: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vehicle profile / address book", style = MaterialTheme.typography.titleMedium)

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label)
                }
                if (expanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            vm.vehicleProfiles.forEach { profile ->
                                TextButton(
                                    onClick = {
                                        vm.selectVehicleProfile(profile.kind)
                                        expanded = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        buildString {
                                            if (state.profile.vehicleProfile == profile.kind) append("✓ ")
                                            append(profile.label)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("Notes: $notes", style = MaterialTheme.typography.bodySmall)
            Text("Target: ${E60AddressBook.describeTarget(state.profile.vehicleProfile, state.selectedTargetId)}", 
                 style = MaterialTheme.typography.bodySmall)
        }
    }
}@Composable
private fun AdapterPresetCard(state: AppState, vm: MainViewModel, label: String, notes: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adapter preset")
            AdapterPresets.all.forEach { preset ->
                OutlinedButton(onClick = { vm.applyAdapterPreset(preset.kind) }, modifier = Modifier.fillMaxWidth()) {
                    Text(buildString {
                        if (state.profile.adapterPreset == preset.kind) append("✓ ")
                        append(preset.label)
                    })
                }
            }
            Text("Selected preset: $label")
            Text("Notes: $notes")
        }
    }
}

@Composable
private fun OverviewJobsCard(state: AppState, vm: MainViewModel, rawHex: String, onRawHexChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Job runner")
            vm.targets().forEach { target ->
                OutlinedButton(onClick = { vm.selectTarget(target.name) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.selectedTargetId == target.name) "✓ ${target.name}" else target.name)
                }
            }
            vm.availableJobs().take(12).forEach { job ->
                OutlinedButton(onClick = { vm.selectJob(job.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(buildString {
                        if (state.selectedJobId == job.id) append("✓ ")
                        append(job.label)
                        if (!job.readOnly) append(" [writes]")
                    })
                }
            }
            Button(onClick = vm::runSelectedJob, enabled = state.connected && !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Run selected BMW job")
            }
            OutlinedTextField(value = rawHex, onValueChange = onRawHexChange, label = { Text("Raw hex request") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.sendRawHex(rawHex) }, enabled = state.connected && !state.busy) { Text("Send raw frame") }
            Text("Last response: ${state.rawResponse}", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CodingScreen(state: AppState, vm: MainViewModel, text: String, onTextChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Live ECU read / write ──────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live ECU coding", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Read current coding from an ECU module, edit parameters, then write back. " +
                    "Requires active K+DCAN connection. Always read before writing.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Module quick-load row
                Text("Load module template or read live:", style = MaterialTheme.typography.labelMedium)
                val modules = listOf("KOMBI", "FRM", "SZL", "EGS", "CAS", "ACSM", "CCC", "DME")
                modules.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { mod ->
                            OutlinedButton(
                                onClick = { vm.loadModuleTemplate(mod) },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            ) { Text(mod, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }

                // Read from ECU button
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.readCodingFromEcu(state.codingModule) },
                        enabled = state.connected && !state.busy && !state.codingLiveBusy,
                    ) { Text("↓ Read from ECU") }
                    Button(
                        onClick = vm::analyzeCodingText,
                        enabled = !state.busy,
                    ) { Text("Analyze") }
                }

                if (state.codingReadResult.isNotBlank()) {
                    Text(state.codingReadResult, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Daten text editor ──────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daten editor — ${state.codingModule}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Coding parameters") },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::applySelectedCodingPreset,
                        enabled = !state.busy,
                    ) { Text("Apply preset") }
                    Button(
                        onClick = vm::writeCodingToEcu,
                        enabled = state.connected && !state.busy && !state.codingLiveBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("↑ Write to ECU") }
                }
                if (state.codingWriteResult.isNotBlank()) {
                    Text(state.codingWriteResult, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Vehicle Order (VO/FA) ───────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vehicle Order (VO / FA)", style = MaterialTheme.typography.titleMedium)
                Text("The VO string defines vehicle options. Stored in CAS and FRM/LMA.",
                    style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = state.vehicleOrder,
                    onValueChange = vm::updateVehicleOrderText,
                    label = { Text("VO / FA String") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                )

                var voOption by remember { mutableStateOf("") }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = voOption,
                        onValueChange = { voOption = it },
                        label = { Text("Add/Remove Option (e.g. $676)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = { vm.addVoOption(voOption); voOption = "" }) {
                        Text("Add")
                    }
                    Button(onClick = { vm.removeVoOption(voOption); voOption = "" }) {
                        Text("Rem")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.readVehicleOrder("CAS") },
                        enabled = state.connected && !state.busy && !state.codingLiveBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Read CAS") }
                    Button(
                        onClick = { vm.readVehicleOrder("FRM") },
                        enabled = state.connected && !state.busy && !state.codingLiveBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Read FRM") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.writeVehicleOrder("CAS") },
                        enabled = state.connected && !state.busy && !state.codingLiveBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Write CAS") }
                    Button(
                        onClick = { vm.writeVehicleOrder("FRM") },
                        enabled = state.connected && !state.busy && !state.codingLiveBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Write FRM") }
                }
            }
        }

        // ── Coding presets ─────────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coding presets", style = MaterialTheme.typography.titleMedium)
                Text("Select a preset to preview, then Apply Preset to merge into the editor above.",
                    style = MaterialTheme.typography.bodySmall)
                DatenManager.presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { vm.selectCodingPreset(preset.kind) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.selectedCodingPreset == preset.kind) "✓ ${preset.label}" else preset.label)
                    }
                }
                if (state.codingPreview.isNotBlank()) {
                    Text("Patch preview", style = MaterialTheme.typography.labelMedium)
                    Text(state.codingPreview, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Warning light suppression ──────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Warning light suppression", style = MaterialTheme.typography.titleMedium)
                Text(
                    "One-tap write specific warning suppression coding to the relevant module. " +
                    "Requires live ECU connection. For track / off-road use only.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val suppressPresets = listOf(
                    CodingPresetKind.SEATBELT_CHIME_OFF,
                    CodingPresetKind.BULB_CHECKS_RELAXED,
                    CodingPresetKind.AIRBAG_SBR_OFF,
                    CodingPresetKind.AIRBAG_OCCUPANCY_OFF,
                    CodingPresetKind.CCC_DISCLAIMER_OFF,
                    CodingPresetKind.WARNING_SUPPRESSION_TRACK,
                )
                suppressPresets.forEach { kind ->
                    val preset = DatenManager.preset(kind)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { vm.applyWarningSuppression(kind) },
                            modifier = Modifier.weight(1f),
                            enabled = state.connected && !state.busy,
                        ) { Text(preset.label) }
                    }
                }
                if (state.warningSuppressResult.isNotBlank()) {
                    Text(state.warningSuppressResult, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Trace Backups ──────────────────────────────────────────────────
        if (state.codingBackups.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trace Backups", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = vm::clearBackups) { Text("Clear") }
                    }
                    Text("Auto-saved before writes and after reads.", style = MaterialTheme.typography.bodySmall)

                    state.codingBackups.forEach { backup ->
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(backup.timestamp))
                        OutlinedButton(
                            onClick = { vm.restoreBackup(backup) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${if (backup.isVo) "VO" else backup.module} @ $time", style = MaterialTheme.typography.labelMedium)
                                Text("Restore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TuningScreen(state: AppState, vm: MainViewModel) {
    val bmwOrange = Color(0xFFFF8800)
    val darkBackground = Color(0xFF1A1A1A)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── DME Live Tuning Card ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkBackground)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DME Live Map Tuning", style = MaterialTheme.typography.titleLarge, color = bmwOrange)
                Text(
                    "Read and modify fuel and ignition maps directly from DME RAM/Flash. " +
                    "Modification is for track use only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = bmwOrange.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::readTuningMaps,
                        enabled = state.connected && !state.tuningLiveBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = bmwOrange, contentColor = Color.Black)
                    ) { Text("↓ Read DME Maps") }
                    Button(
                        onClick = vm::writeTuningMaps,
                        enabled = state.connected && !state.tuningLiveBusy && (state.fuelMap != null || state.ignitionMap != null),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) { Text("↑ Write to DME") }
                }

                if (state.tuningReadResult.isNotEmpty()) {
                    Text(state.tuningReadResult, color = bmwOrange, style = MaterialTheme.typography.labelSmall)
                }
                if (state.tuningWriteResult.isNotEmpty()) {
                    Text(state.tuningWriteResult, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ── Map Editor ────────────────────────────────────────────────────
        state.fuelMap?.let { map ->
            MapEditorCard("Fuel Injection Map (Target Lambda)", map, true, vm)
        }

        state.ignitionMap?.let { map ->
            MapEditorCard("Ignition Timing Map (Advance BTDC)", map, false, vm)
        }

        // ── CCC Integration ───────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CCC map switching / tuning", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Select a map slot to preview its tuning profile. Use the CCC Integration screen " +
                    "to send the slot change to the vehicle live over K+DCAN.",
                    style = MaterialTheme.typography.bodySmall,
                )
                vm.tuneProfiles.forEach { profile ->
                    OutlinedButton(
                        onClick = { vm.setMapSlot(profile.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.selectedMapSlot == profile.name) "✓ ${profile.name}" else profile.name)
                    }
                }
                Button(onClick = vm::exportTunePlan, modifier = Modifier.fillMaxWidth()) {
                    Text("Export tune plan notes")
                }
                Text(state.tuningSummary, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live map write", style = MaterialTheme.typography.titleMedium)
                Text("Tap a slot to send it to CCC immediately. Also available on the CCC Integration screen.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Comfort", "Sport", "Race").forEach { slot ->
                        Button(
                            onClick = { vm.writeCccMapSlot(slot) },
                            enabled = state.connected && !state.busy && !state.cccLiveMapBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text(slot) }
                    }
                }
                if (state.cccLiveMapResult.isNotBlank()) {
                    Text(state.cccLiveMapResult, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MapEditorCard(title: String, map: TuningMap, isFuel: Boolean, vm: MainViewModel) {
    val bmwOrange = Color(0xFFFF8800)
    val darkBackground = Color(0xFF1A1A1A)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = darkBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, bmwOrange.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = bmwOrange, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Scrollable Grid for Map
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    // Header (RPM)
                    Row {
                        Spacer(modifier = Modifier.width(60.dp))
                        map.xAxis.forEach { rpm ->
                            Text(
                                rpm.toString(),
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.Center,
                                color = bmwOrange.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    map.table.forEachIndexed { rIdx, row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Sidebar (Load)
                            Text(
                                map.yAxis[rIdx].toString(),
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.End,
                                color = bmwOrange.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            row.forEachIndexed { cIdx, value ->
                                var textValue by remember(value) { mutableStateOf("%.2f".format(value)) }
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = {
                                        textValue = it
                                        it.toFloatOrNull()?.let { f ->
                                            vm.updateMapValue(isFuel, rIdx, cIdx, f)
                                        }
                                    },
                                    modifier = Modifier.width(60.dp).padding(2.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = bmwOrange,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CccScreen(state: AppState, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Live CCC map slot switching ────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CCC live map switching", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sends KWP WriteDataByIdentifier (0x2E) to the CCC to request a map slot change on the fly. " +
                    "The CCC then signals the DME/DDE to load the corresponding fuel and throttle map. " +
                    "Requires active K+DCAN connection with CCC accessible.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Active slot: ${state.selectedMapSlot}", fontFamily = FontFamily.Monospace)

                val mapSlots = listOf("Comfort" to "Economy / daily drive map", "Sport" to "Sharper throttle, higher rev targets", "Race" to "Track / off-road use only")
                mapSlots.forEach { (slot, desc) ->
                    val isActive = state.selectedMapSlot == slot
                    if (isActive) {
                        Button(
                            onClick = { vm.writeCccMapSlot(slot) },
                            enabled = state.connected && !state.busy && !state.cccLiveMapBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("✓ $slot  —  $desc") }
                    } else {
                        OutlinedButton(
                            onClick = { vm.writeCccMapSlot(slot) },
                            enabled = state.connected && !state.busy && !state.cccLiveMapBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("$slot  —  $desc") }
                    }
                }

                if (state.cccLiveMapResult.isNotBlank()) {
                    Text(state.cccLiveMapResult, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }

                Divider()
                Text("Live CCC probe", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.runQuickServiceJob("CCC", "ccc_live_mapslot") },
                        enabled = state.connected && !state.busy,
                    ) { Text("Read map slot") }
                    OutlinedButton(
                        onClick = { vm.runQuickServiceJob("CCC", "e60_ccc_probe_pack") },
                        enabled = state.connected && !state.busy,
                    ) { Text("Full CCC probe") }
                }
            }
        }

        // ── Tune planning notes ────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tune planning notes", style = MaterialTheme.typography.titleMedium)
                Text("Select a slot below for a prepared DME/DDE map-routing summary.",
                    style = MaterialTheme.typography.bodySmall)
                vm.tuneProfiles.forEach { profile ->
                    OutlinedButton(
                        onClick = { vm.setMapSlot(profile.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.selectedMapSlot == profile.name) "✓ ${profile.name}" else profile.name)
                    }
                }
                Button(onClick = vm::exportTunePlan, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh tune bundle notes")
                }
                Text(state.tuningSummary, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SteeringScreen(state: AppState, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LIN / steering retrofit", style = MaterialTheme.typography.titleLarge)
            Text("Coding patches for SZL/KOMBI/EGS plus a live F-series button monitor with MFL packet injection.")

            // ── Quick hint packs ───────────────────────────────────────────
            Text("Quick hint packs", style = MaterialTheme.typography.titleMedium)
            vm.steeringProfiles.forEach { profile ->
                OutlinedButton(onClick = { vm.setSteeringProfile(profile.name) }, modifier = Modifier.fillMaxWidth()) {
                    Text(profile.name)
                }
            }
            Text(state.steeringSummary.ifBlank { "No steering profile prepared" }, fontFamily = FontFamily.Monospace)

            Spacer(Modifier.height(4.dp))

            // ── Retrofit daten bundles ─────────────────────────────────────
            Text("Retrofit daten bundles", style = MaterialTheme.typography.titleMedium)
            vm.steeringRetrofitPresets.forEach { preset ->
                OutlinedButton(onClick = { vm.prepareSteeringRetrofit(preset.kind) }, modifier = Modifier.fillMaxWidth()) {
                    Text(preset.label)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.loadSteeringModuleTemplate("SZL", preset.kind) }) { Text("Load SZL") }
                    Button(onClick = { vm.loadSteeringModuleTemplate("KOMBI", preset.kind) }) { Text("Load KOMBI") }
                    if (preset.modulePatches.any { it.module == "EGS" }) {
                        Button(onClick = { vm.loadSteeringModuleTemplate("EGS", preset.kind) }) { Text("Load EGS") }
                    }
                }
            }
            Text("Bundle preview", style = MaterialTheme.typography.titleMedium)
            Text(state.steeringBundlePreview, fontFamily = FontFamily.Monospace)
            Text("Validation checklist", style = MaterialTheme.typography.titleMedium)
            Text(state.steeringValidationSummary, fontFamily = FontFamily.Monospace)

            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            // ── Live SZL monitor + MFL injector ───────────────────────────
            Text("Live SZL monitor / MFL injector", style = MaterialTheme.typography.titleMedium)
            Text(
                "Polls SZL 0x02 at ~6 Hz, decodes F-series button bits, injects KWP 0x30 to KOMBI.\n" +
                "Requires active K+DCAN connection and SZL_LENKRAD_TYP = F_SERIE already coded.",
                style = MaterialTheme.typography.bodySmall,
            )

            // Dry-run / live toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.szlMonitorDryRun,
                        onClick = { vm.setSzlMonitorDryRun(true) },
                        enabled = !state.szlMonitorActive,
                    )
                    Text("Dry run")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !state.szlMonitorDryRun,
                        onClick = { vm.setSzlMonitorDryRun(false) },
                        enabled = !state.szlMonitorActive,
                    )
                    Text("Live inject")
                }
            }

            // Start / stop buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.startSzlMonitor() },
                    enabled = state.connected && !state.szlMonitorActive && !state.busy,
                ) { Text("▶ Start monitor") }
                OutlinedButton(
                    onClick = { vm.stopSzlMonitor() },
                    enabled = state.szlMonitorActive,
                ) { Text("■ Stop") }
            }

            // Live button state readout
            if (state.szlMonitorActive || state.szlLiveMatrix1 != 0 || state.szlLiveMatrix2 != 0) {
                Text(
                    "Matrix raw:  0x%02X  0x%02X".format(state.szlLiveMatrix1, state.szlLiveMatrix2),
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Active:  ${state.szlLiveActiveButtons.ifEmpty { listOf("none") }.joinToString("  ")}",
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Last Δ:  ${state.szlLiveLastDiff}",
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Inject log
            if (state.mflInjectionLog.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Injection log", style = MaterialTheme.typography.labelMedium)
                state.mflInjectionLog.takeLast(12).forEach { line ->
                    Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FlashingScreen(state: AppState, vm: MainViewModel, hex: String, onHexChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Flashing manager", style = MaterialTheme.typography.titleLarge)
            Text("Generates chunked transfer plans from binary hex. Dry-run is the default safety mode.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { RadioButton(selected = state.flashMode == FlashMode.DRY_RUN, onClick = { vm.setFlashMode(FlashMode.DRY_RUN) }); Text("Dry run") }
                Row { RadioButton(selected = state.flashMode == FlashMode.EXPERT_WRITE, onClick = { vm.setFlashMode(FlashMode.EXPERT_WRITE) }); Text("Expert") }
            }
            OutlinedTextField(value = hex, onValueChange = onHexChange, label = { Text("Input hex image") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = vm::buildFlashPlan, modifier = Modifier.fillMaxWidth()) { Text("Build flash plan") }
            Text(state.flashPlanSummary)
            state.lastFlashPlan?.let { plan ->
                Text("Frames: ${plan.chunkCount} x ${plan.chunkSize} bytes")
                plan.frames.take(8).forEach { frame -> Text(frame, fontFamily = FontFamily.Monospace) }
                if (plan.frames.size > 8) Text("… ${plan.frames.size - 8} more frame(s)")
            }
        }
    }
}

@Composable
private fun GaugesScreen(state: AppState, vm: MainViewModel) {
    val bmwOrange = Color(0xFFFF8800)
    val darkBackground = Color(0xFF1A1A1A)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkBackground)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Performance Gauges", style = MaterialTheme.typography.titleLarge, color = bmwOrange)
                    Button(
                        onClick = { if (state.pollingEnabled) vm.stopDashboardPolling() else vm.startDashboardPolling() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.pollingEnabled) Color.Red else bmwOrange,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(if (state.pollingEnabled) "Stop" else "Start Polling")
                    }
                }
                Text("Real-time data from DME, EGS, and DSC", style = MaterialTheme.typography.bodySmall, color = bmwOrange.copy(alpha = 0.7f))
            }
        }

        // Main Gauges Grid
        val dme = state.moduleSnapshots["DME / DDE"]?.decoded ?: emptyMap()
        val egs = state.moduleSnapshots["EGS"]?.decoded ?: emptyMap()
        val dsc = state.moduleSnapshots["DSC"]?.decoded ?: emptyMap()

        val rpm = dme["engine_speed_rpm"]?.toDoubleOrNull() ?: 0.0
        val speed = dsc["vehicle_speed_kph"]?.toDoubleOrNull() ?: 0.0
        val coolant = dme["coolant_temp_c"]?.toDoubleOrNull() ?: 0.0
        val oilTemp = egs["oil_temp_c"]?.toDoubleOrNull() ?: 0.0
        val gear = egs["current_gear_raw"] ?: "P"
        val voltage = dme["battery_v"]?.toDoubleOrNull() ?: 0.0

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularGaugeBox(label = "RPM", value = rpm.toInt().toString(), min = 0f, max = 8000f, current = rpm.toFloat(), unit = "min⁻¹", modifier = Modifier.weight(1f))
            CircularGaugeBox(label = "Speed", value = speed.toInt().toString(), min = 0f, max = 280f, current = speed.toFloat(), unit = "km/h", modifier = Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularGaugeBox(label = "Coolant", value = coolant.toInt().toString(), min = 0f, max = 130f, current = coolant.toFloat(), unit = "°C", modifier = Modifier.weight(1f))
            CircularGaugeBox(label = "Oil Temp", value = oilTemp.toInt().toString(), min = 0f, max = 150f, current = oilTemp.toFloat(), unit = "°C", modifier = Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeBox(label = "Gear", value = gear, unit = "", modifier = Modifier.weight(1f))
            CircularGaugeBox(label = "Voltage", value = "%.1f".format(voltage), min = 9f, max = 16f, current = voltage.toFloat(), unit = "V", modifier = Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkBackground)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Additional Data", color = bmwOrange, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Throttle", color = bmwOrange.copy(alpha = 0.8f))
                    Text("${dme["throttle_angle_pct"] ?: "0"} %", color = bmwOrange, fontFamily = FontFamily.Monospace)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pedal", color = bmwOrange.copy(alpha = 0.8f))
                    Text("${dme["pedal_pct"] ?: "0"} %", color = bmwOrange, fontFamily = FontFamily.Monospace)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Intake Temp", color = bmwOrange.copy(alpha = 0.8f))
                    Text("${dme["intake_temp_c"] ?: "0"} °C", color = bmwOrange, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun CircularGaugeBox(
    label: String,
    value: String,
    min: Float,
    max: Float,
    current: Float,
    unit: String,
    modifier: Modifier = Modifier
) {
    val bmwOrange = Color(0xFFFF8800)
    val darkBackground = Color(0xFF1A1A1A)
    val animatedValue by animateFloatAsState(
        targetValue = current,
        animationSpec = tween(durationMillis = 300),
        label = "gaugeValue"
    )

    Card(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = darkBackground),
        border = BorderStroke(1.dp, bmwOrange.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sweepAngle = 240f
                val startAngle = 150f
                val progress = ((animatedValue - min) / (max - min)).coerceIn(0f, 1f)

                // Background track
                drawArc(
                    color = bmwOrange.copy(alpha = 0.1f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx())
                )

                // Progress track
                drawArc(
                    color = bmwOrange,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * progress,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label.uppercase(), color = bmwOrange.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    value,
                    color = bmwOrange,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                if (unit.isNotEmpty()) {
                    Text(unit, color = bmwOrange.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GaugeBox(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val bmwOrange = Color(0xFFFF8800)
    val darkBackground = Color(0xFF1A1A1A)

    Card(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = darkBackground),
        border = androidx.compose.foundation.BorderStroke(2.dp, bmwOrange.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label.uppercase(), color = bmwOrange.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                color = bmwOrange,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            if (unit.isNotEmpty()) {
                Text(unit, color = bmwOrange.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
private fun ExperimentalScreen(state: AppState, vm: MainViewModel) {
    val isRunning = state.controllerArmed || state.controllerTickHz != "—"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Xbox wired USB controller bridge ──────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Xbox wired USB controller bridge", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Plug in an Xbox controller via USB OTG. Left stick X = steering, " +
                    "RT = throttle, LT = brake. A button arms/disarms. B = emergency stop. RB/LB = paddle shift.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // ── Controller status row ──────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (state.controllerConnected) "●" else "○")
                    Text(
                        state.controllerName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                OutlinedButton(onClick = { vm.scanController() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan for controller")
                }

                Divider()

                // ── Channel toggles ────────────────────────────────────────
                Text("Active channels", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.controllerSendThrottle,
                        onCheckedChange = { vm.setControllerSendThrottle(it) },
                        enabled = !isRunning,
                    )
                    Text("Throttle (DME)")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.controllerSendSteering,
                        onCheckedChange = { vm.setControllerSendSteering(it) },
                        enabled = !isRunning,
                    )
                    Text("Steering advisory (DSC/EPS)")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.controllerSendBrake,
                        onCheckedChange = { vm.setControllerSendBrake(it) },
                        enabled = !isRunning,
                    )
                    Text("Brake hint (DSC)")
                }

                // ── Throttle ceiling slider ────────────────────────────────
                Text(
                    "Throttle ceiling: ${"%.0f".format(state.controllerThrottleCeiling * 100f)} %",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = state.controllerThrottleCeiling,
                    onValueChange = { vm.setControllerThrottleCeiling(it) },
                    valueRange = 0.05f..1.0f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                )

                Divider()

                // ── Start / Stop / E-Stop ──────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.startControllerBridge() },
                        enabled = !isRunning && state.controllerConnected,
                    ) { Text("▶ Start") }
                    OutlinedButton(
                        onClick = { vm.stopControllerBridge() },
                        enabled = isRunning,
                    ) { Text("■ Stop") }
                    Button(
                        onClick = {
                            vm.onControllerKey(
                                AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BUTTON_B)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("E-Stop") }
                }

                // ── Arm state badge ────────────────────────────────────────
                if (isRunning) {
                    Text(
                        if (state.controllerArmed)
                            "ARMED  — controller input is live"
                        else
                            "Running disarmed — press A on controller to arm",
                        color = if (state.controllerArmed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Divider()

                // ── Live axis readout ──────────────────────────────────────
                Text("Live axes", style = MaterialTheme.typography.labelMedium)
                Text(
                    "STR %+.2f   THR %.2f   BRK %.2f   %s".format(
                        state.controllerSteeringNorm,
                        state.controllerThrottleNorm,
                        state.controllerBrakeNorm,
                        state.controllerTickHz,
                    ),
                    fontFamily = FontFamily.Monospace,
                )
                if (state.controllerLastSummary != "—") {
                    Text(
                        state.controllerLastSummary,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // ── Injection log ──────────────────────────────────────────
                if (state.controllerLog.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Injection log", style = MaterialTheme.typography.labelMedium)
                    state.controllerLog.takeLast(10).forEach { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // ── Remote start / stop (CAS KWP routine) ────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remote start / stop", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sends CAS KWP routine 0x31 (startRoutine 0x0004/0x0005) for engine crank/stop. " +
                    "Uses Session 0x03 keyless bypass. EXPERIMENTAL — stationary only.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // ── Connection mode selector ───────────────────────────────
                Text("Connection mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.remoteStartMode == RemoteStartMode.LOCAL_KDCAN,
                            onClick = { vm.setRemoteStartMode(RemoteStartMode.LOCAL_KDCAN) },
                            enabled = !state.remoteStartArmed,
                        )
                        Text("Local K+DCAN")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.remoteStartMode == RemoteStartMode.SIM_REMOTE,
                            onClick = { vm.setRemoteStartMode(RemoteStartMode.SIM_REMOTE) },
                            enabled = !state.remoteStartArmed,
                        )
                        Text("SIM / Remote bridge")
                    }
                }

                // ── SIM remote connection fields ───────────────────────────
                if (state.remoteStartMode == RemoteStartMode.SIM_REMOTE) {
                    val simHostState = remember { androidx.compose.runtime.mutableStateOf(state.simRemoteHost) }
                    val simPortState = remember { androidx.compose.runtime.mutableStateOf(state.simRemotePort.toString()) }

                    Text(
                        "Enter the IP address (or VPN hostname) and TCP port of the Android bridge " +
                        "device left in the car. The bridge device must be running a K+DCAN TCP relay " +
                        "(e.g. socat or OBD-Gateway app) and have mobile data or WiFi available.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = simHostState.value,
                        onValueChange = { simHostState.value = it; vm.setSimRemoteHost(it) },
                        label = { Text("Bridge host IP / hostname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = simPortState.value,
                        onValueChange = { simPortState.value = it; vm.setSimRemotePort(it) },
                        label = { Text("TCP port (default 35001)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.connectSimRemote() },
                            enabled = !state.simConnected && !state.simConnecting,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (state.simConnecting) "Connecting…" else "Connect via SIM") }
                        OutlinedButton(
                            onClick = { vm.disconnectSimRemote() },
                            enabled = state.simConnected,
                            modifier = Modifier.weight(1f),
                        ) { Text("Disconnect") }
                    }
                    if (state.simConnectionResult.isNotBlank()) {
                        Text(state.simConnectionResult, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                Divider()

                // ── Connection readiness indicator ─────────────────────────
                val canSendLocal = state.remoteStartMode == RemoteStartMode.LOCAL_KDCAN && state.connected
                val canSendSim   = state.remoteStartMode == RemoteStartMode.SIM_REMOTE  && state.simConnected
                val canSend      = canSendLocal || canSendSim
                Text(
                    when {
                        state.remoteStartMode == RemoteStartMode.LOCAL_KDCAN && !state.connected ->
                            "● Not connected — connect K+DCAN first"
                        state.remoteStartMode == RemoteStartMode.SIM_REMOTE && !state.simConnected ->
                            "● SIM bridge not connected — connect above first"
                        else -> "● Ready — arm to enable send buttons"
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )

                // ── Arm / Disarm ───────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.armRemoteStart() },
                        enabled = !state.remoteStartArmed && canSend && !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Arm") }
                    OutlinedButton(
                        onClick = { vm.disarmRemoteStart() },
                        enabled = state.remoteStartArmed,
                        modifier = Modifier.weight(1f),
                    ) { Text("Disarm") }
                }
                if (state.remoteStartArmed) {
                    Text("⚠ ARMED", color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace)
                }

                // ── Pre-flight Checklist ──────────────────────────────────
                Text("Pre-flight checklist", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SafetyCheckItem("Gear in Park (P)", state.safetyGearP)
                    SafetyCheckItem("Hood closed", state.safetyHoodClosed)
                    SafetyCheckItem("Brake released", state.safetyBrakeReleased)
                    SafetyCheckItem("Battery voltage > 11.5V", state.safetyVoltageOk)
                }

                Divider()

                // ── Start / Stop ───────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.sendRemoteStart() },
                        enabled = state.remoteStartArmed && !state.remoteStartBusy && !state.remoteStarted,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("▶ Start") }
                    Button(
                        onClick = { vm.sendRemoteStop() },
                        enabled = (state.remoteStartArmed || state.remoteStarted) && !state.remoteStartBusy,
                        modifier = Modifier.weight(1f),
                    ) { Text("■ Stop") }

                    if (state.remoteStarted) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "${state.safetyRpm} RPM",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (state.remoteStartResult.isNotBlank()) {
                    Text(state.remoteStartResult, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SafetyCheckItem(label: String, passed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        val tint = if (passed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (passed) MaterialTheme.colorScheme.onSurface 
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = if (passed) FontWeight.Medium else FontWeight.Normal
        )
    }
}


@Composable
private fun LiveDashboardCard(
    state: AppState,
    pollingInterval: String,
    onPollingIntervalChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Home dashboard", style = MaterialTheme.typography.titleLarge)
            Text("Continuously polls DME, EGS, DSC, and CAS quick-live jobs into reusable cards.")
            Text("Status: ${state.dashboardStatus}")
            OutlinedTextField(value = pollingInterval, onValueChange = onPollingIntervalChange, label = { Text("Polling interval ms") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = state.connected && !state.pollingEnabled && !state.busy) { Text("Start polling") }
                OutlinedButton(onClick = onStop, enabled = state.pollingEnabled) { Text("Stop polling") }
            }
        }
    }
}

@Composable
private fun DashboardSnapshotGrid(state: AppState) {
    val dashboardTargets = listOf("DME / DDE", "EGS", "DSC", "CAS")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live dashboard cards", style = MaterialTheme.typography.titleLarge)
            dashboardTargets.forEach { targetId ->
                val snapshot = state.moduleSnapshots[targetId]
                DashboardModuleCard(targetId = targetId, snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun DashboardModuleCard(targetId: String, snapshot: ModuleSnapshot?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(targetId, style = MaterialTheme.typography.titleMedium)
            if (snapshot == null) {
                Text("No polled data yet")
            } else {
                Text("${snapshot.title} • ${snapshot.timestamp}")
                Text(snapshot.summary)
                val topFields = snapshot.decoded.entries.take(6)
                if (topFields.isEmpty()) {
                    Text(snapshot.rawResponse.ifBlank { "<empty>" }, fontFamily = FontFamily.Monospace)
                } else {
                    topFields.forEach { (k, v) -> Text("$k = $v", fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

@Composable
private fun TransportCard(state: AppState, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Transport")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row { RadioButton(selected = state.selectedTransport == TransportType.USB_KDCAN, onClick = { vm.setTransport(TransportType.USB_KDCAN) }); Text("USB K+DCAN") }
                Row { RadioButton(selected = state.selectedTransport == TransportType.ETHERNET_OBD, onClick = { vm.setTransport(TransportType.ETHERNET_OBD) }); Text("Ethernet OBD") }
                Row { RadioButton(selected = state.selectedTransport == TransportType.BLUETOOTH_OBD, onClick = { vm.setTransport(TransportType.BLUETOOTH_OBD) }); Text("Bluetooth Vgate") }
            }
            when (state.selectedTransport) {
                TransportType.USB_KDCAN -> {
                    OutlinedTextField(value = state.profile.baudRate.toString(), onValueChange = vm::updateBaudRate, label = { Text("Baud rate") }, modifier = Modifier.fillMaxWidth())
                }
                TransportType.ETHERNET_OBD -> {
                    OutlinedTextField(value = state.profile.tcpHost, onValueChange = vm::updateHost, label = { Text("Adapter IP") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.profile.tcpPort.toString(), onValueChange = vm::updatePort, label = { Text("TCP port") }, modifier = Modifier.fillMaxWidth())
                }
                TransportType.BLUETOOTH_OBD -> {
                    OutlinedTextField(
                        value = state.bluetoothMac,
                        onValueChange = { vm.setBluetoothMac(it) },
                        label = { Text("Vgate Bluetooth MAC") },
                        placeholder = { Text("00:00:00:00:00:00") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.bluetoothMac.isNotEmpty() && !vm.validateBluetoothMac(state.bluetoothMac),
                        supportingText = {
                            if (state.bluetoothMac.isNotEmpty() && !vm.validateBluetoothMac(state.bluetoothMac)) {
                                Text("Invalid MAC format. Use XX:XX:XX:XX:XX:XX", color = Color.Red)
                            }
                        }
                    )

                    // Show connected device info
                    if (state.bluetoothConnectedMac.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Connected Device", fontWeight = FontWeight.Bold)
                                Text("Name: ${state.bluetoothConnectedName}")
                                Text("MAC: ${state.bluetoothConnectedMac}", fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Text(
                        "Pair Vgate adapter in Android Bluetooth settings first, then enter its MAC address.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.scanBluetoothDevices() },
                            enabled = !state.bluetoothScanning
                        ) {
                            Text(if (state.bluetoothScanning) "Scanning..." else "Scan Paired Devices")
                        }

                        OutlinedButton(
                            onClick = { vm.readConnectedBluetoothMac() },
                            enabled = state.connected
                        ) {
                            Text("Get MAC from Device")
                        }
                    }

                    if (state.bluetoothDevices.isNotEmpty()) {
                        Text("Paired devices:", fontWeight = FontWeight.Bold)
                        state.bluetoothDevices.forEach { (name, mac) ->
                            TextButton(
                                onClick = { vm.setBluetoothMac(mac) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$name — $mac")
                            }
                        }
                    }
                }
            }
            Text("Connect timeout: ${state.profile.connectTimeoutMs} ms")
            Text("Read timeout: ${state.profile.readTimeoutMs} ms")
            Text("Adapter settle delay: ${state.profile.settleDelayMs} ms")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.forceIgnitionOn, onCheckedChange = { vm.setForceIgnitionOn(it) })
                Text("Force ignition ON", style = MaterialTheme.typography.bodySmall)
            }
            if (state.ignitionStatus.isNotEmpty()) {
                Text("Ignition: ${state.ignitionStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::refreshDevices) { Text("Scan") }
                Button(onClick = vm::connect, enabled = !state.connected && !state.busy) { Text("Connect") }
                OutlinedButton(onClick = vm::disconnect, enabled = state.connected) { Text("Disconnect") }
            }
            Text(if (state.connected) "Status: Connected" else "Status: Disconnected")
            Text("Vehicle profile: ${state.activeVehicleProfile}")
            Text("Comm profile: ${state.activeCommProfile}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InjectionScreen(state: AppState, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CAN / KWP Injection", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Directly override vehicle state by spoofing control messages via KWP 0x30 (IO Control) " +
                    "and 0x31 (Routine Control). Use with extreme caution.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ── Status Bar ──────────────────────────────────────────────────
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.injectionBusy) MaterialTheme.colorScheme.secondaryContainer 
                                 else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.injectionBusy) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = if (state.injectionResult.isBlank()) "Ready for injection" else state.injectionResult,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Actions Grid ────────────────────────────────────────────────
        val groups = CanInjector.ACTIONS.groupBy { it.target.name }
        groups.forEach { (targetName, actions) ->
            Text(targetName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEach { action ->
                    InjectionActionChip(action, state, vm)
                }
            }
        }

        // ── Macros ──────────────────────────────────────────────────────
        Text("Sequential Macros", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CanInjector.MACROS.forEach { macro ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.runInjectionMacro(macro) },
                    enabled = !state.injectionBusy && (state.connected || state.simConnected)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(macro.label, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "${macro.actions.size} steps | ${macro.delayBetweenMs}ms delay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // ── Raw CAN Frame Injection ─────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Raw CAN Frame Injection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Select a message definition and enter hex payload to inject raw CAN frames. " +
                    "Requires active K+DCAN connection.",
                    style = MaterialTheme.typography.bodySmall
                )

                var selectedMsgName by remember { mutableStateOf(E60CanBus.ALL_MESSAGES.firstOrNull()?.name ?: "") }
                var payloadHex by remember { mutableStateOf("") }

                if (E60CanBus.ALL_MESSAGES.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Msg:", style = MaterialTheme.typography.labelMedium)
                        Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                E60CanBus.ALL_MESSAGES.forEach { msg ->
                                    OutlinedButton(
                                        onClick = { 
                                            selectedMsgName = msg.name
                                            // Pre-fill length if empty
                                            if (payloadHex.isBlank()) {
                                                payloadHex = "00 ".repeat(msg.length).trim()
                                            }
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        colors = if (selectedMsgName == msg.name) 
                                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                            else ButtonDefaults.outlinedButtonColors()
                                    ) {
                                        Text(
                                            text = msg.name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val msgDef = E60CanBus.byName(selectedMsgName)
                msgDef?.let {
                    Text("ID: 0x${it.id.toString(16).uppercase()} | Bus: ${it.bus} | Len: ${it.length}", 
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    
                    if (it.description.isNotBlank()) {
                        Text(it.description, style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    }
                }

                OutlinedTextField(
                    value = payloadHex,
                    onValueChange = { payloadHex = it },
                    label = { Text("Payload Hex (e.g. 01 02 FF)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                )

                Button(
                    onClick = { vm.injectRawCanFrame(selectedMsgName, payloadHex) },
                    enabled = (state.connected || state.simConnected) && !state.injectionBusy && payloadHex.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.simConnected) "Simulate Injection" else "Inject Raw Frame")
                }
            }
        }

        // ── Injection History / Logs ──────────────────────────────────
        if (state.logs.any { it.level == "INFO" || it.level == "ERROR" }) {
            Text("Recent Activity", style = MaterialTheme.typography.labelLarge)
            state.logs.filter { it.level == "INFO" || it.level == "ERROR" }.take(5).forEach { log ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(log.timestamp, style = MaterialTheme.typography.labelSmall)
                        Text(log.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun InjectionActionChip(action: CanInjector.InjectableAction, state: AppState, vm: MainViewModel) {
    val color = when (action.riskLevel) {
        CanInjector.RiskLevel.LOW -> MaterialTheme.colorScheme.primary
        CanInjector.RiskLevel.MEDIUM -> Color(0xFFFFA000) // Amber
        CanInjector.RiskLevel.HIGH -> Color(0xFFFF5722) // Deep Orange
        CanInjector.RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }

    OutlinedButton(
        onClick = { vm.runInjectionAction(action) },
        enabled = !state.injectionBusy && (state.connected || state.simConnected),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(action.label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(
                "0x${action.serviceId.toString(16).uppercase()}", 
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun TransportCompactCard(state: AppState, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Session")
            Text(if (state.connected) "Connected" else "Disconnected")
            Text("Vehicle profile: ${state.activeVehicleProfile}")
            Text("Comm profile: ${state.activeCommProfile}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.forceIgnitionOn, onCheckedChange = { vm.setForceIgnitionOn(it) })
                Text("Force ignition ON", style = MaterialTheme.typography.bodySmall)
            }
            if (state.ignitionStatus.isNotEmpty()) {
                Text("Ignition: ${state.ignitionStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::refreshDevices) { Text("Scan") }
                Button(onClick = vm::connect, enabled = !state.connected && !state.busy) { Text("Connect") }
                OutlinedButton(onClick = vm::disconnect, enabled = state.connected) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun DedicatedServiceModuleCard(vm: MainViewModel, state: AppState, def: ServiceScreenDefinition, snapshot: ModuleSnapshot?) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(def.title, style = MaterialTheme.typography.titleLarge)
            Text(def.subtitle)
            Text("Selected target: ${state.selectedTargetId}")
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                def.actions.forEach { action ->
                    OutlinedButton(onClick = { vm.runQuickServiceJob(action.targetId, action.jobId) }, enabled = state.connected && !state.busy) {
                        Text(action.label)
                    }
                }
            }

            // Key data section for CAS screen
            if (def.screen == ServiceScreen.CAS) {
                Divider(Modifier.padding(vertical = 8.dp))
                Text("Key Data (No Key Required)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Button(
                    onClick = { vm.readKeyData() },
                    enabled = !state.keyDataBusy && state.connected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.keyDataBusy) "Reading..." else "Read All Key Data")
                }

                if (state.keyDataError.isNotEmpty()) {
                    Text("Error: ${state.keyDataError}", color = Color.Red)
                }

                state.keyDataResult?.let { result ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ISN: ${result.isn}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("VIN: ${result.vin}", fontFamily = FontFamily.Monospace)
                            Text("Module: ${result.moduleVersion}", fontFamily = FontFamily.Monospace)
                            Text("Key count: ${result.keyCount}", fontFamily = FontFamily.Monospace)

                            if (result.keySlots.isNotEmpty()) {
                                Text("Select Key Slot:", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    result.keySlots.forEach { slot ->
                                        val isSelected = state.selectedKeySlot == slot.slotNumber
                                        Button(
                                            onClick = { vm.selectKeySlot(slot.slotNumber) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Text("${slot.slotNumber}")
                                        }
                                    }
                                }

                                result.keySlots.forEach { slot ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Slot ${slot.slotNumber}", fontFamily = FontFamily.Monospace)
                                        when {
                                            slot.keyPresent -> Text(
                                                "✓ Key present",
                                                color = Color(0xFF4CAF50),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            slot.hasModuleData -> Text(
                                                "◌ Programmed (no key)",
                                                color = Color(0xFF2196F3),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            else -> Text(
                                                "✗ Empty",
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    if (slot.keyId.isNotEmpty()) {
                                        Text("  ID: ${slot.keyId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                    if (slot.hasModuleData && !slot.keyPresent) {
                                        Text("  ${slot.moduleDataStatus}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF2196F3))
                                    }
                                }
                            }

                            if (result.rawKeyData.isNotEmpty()) {
                                Text("Raw Data:", fontWeight = FontWeight.Bold)
                                Text(result.rawKeyData, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { vm.exportKeyData() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export Key Data")
                            }
                            OutlinedButton(
                                onClick = { vm.saveKeyDataToFile(context, "ak90") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.keyDataBusy
                            ) {
                                Text("📲 Export for AK90+")
                            }
                        }
                    }

                    // Key Slot Detail Section
                    if (state.selectedKeySlot > 0) {
                        Spacer(Modifier.height(8.dp))
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Key Slot ${state.selectedKeySlot} Detail", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                                Button(
                                    onClick = { vm.readKeySlotDetail(state.selectedKeySlot) },
                                    enabled = !state.keySlotDetailBusy && state.connected,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (state.keySlotDetailBusy) "Reading Slot ${state.selectedKeySlot}..." else "Read Slot ${state.selectedKeySlot} Detail")
                                }

                                if (state.keySlotDetailError.isNotEmpty()) {
                                    Text("Error: ${state.keySlotDetailError}", color = Color.Red)
                                }

                                state.keySlotDetail?.let { detail ->
                                    if (detail.slotNumber == state.selectedKeySlot) {
                                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Key ID: ${detail.keyId}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                Text("Transponder: ${detail.transponderType}", fontFamily = FontFamily.Monospace)
                                                Text("Transponder ID: ${detail.transponderId}", fontFamily = FontFamily.Monospace)
                                                Text("Key Track: ${detail.keyTrack}", fontFamily = FontFamily.Monospace)
                                                Text("Status: ${detail.keyStatus}", fontFamily = FontFamily.Monospace)
                                                Text("Valid: ${if (detail.isValid) "Yes" else "No"}", fontFamily = FontFamily.Monospace)

                                                if (detail.keyDataHex.isNotEmpty()) {
                                                    Text("Key Data (Hex):", fontWeight = FontWeight.Bold)
                                                    Text(detail.keyDataHex, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                                }

                                                if (detail.rawResponse.isNotEmpty()) {
                                                    Text("Raw Response:", fontWeight = FontWeight.Bold)
                                                    Text(detail.rawResponse, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = { vm.exportKeySlotDetail() },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Export Slot ${detail.slotNumber} for New Key")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } ?: run {
                    if (!state.keyDataBusy && state.keyDataError.isEmpty()) {
                        Text("Press 'Read All Key Data' to fetch key information from module memory.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!state.connected) {
                    Text("Connect to vehicle first.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Divider(Modifier.padding(vertical = 8.dp))
            }

            if (snapshot == null) {
                Text("No data captured for this module yet")
            } else {
                Text("Last action: ${snapshot.title}")
                Text("Summary: ${snapshot.summary}")
                if (snapshot.timestamp.isNotBlank()) Text("Captured: ${snapshot.timestamp}")
                FocusFieldGrid(def.focusKeys, snapshot.decoded)
                Text("Raw response", style = MaterialTheme.typography.titleMedium)
                Text(snapshot.rawResponse.ifBlank { "<empty>" }, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun FocusFieldGrid(focusKeys: List<String>, decoded: Map<String, String>) {
    val shown = focusKeys.mapNotNull { key -> decoded[key]?.let { key to it } }
    if (shown.isEmpty()) {
        Text("No focused decoded values yet")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { (key, value) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(key, style = MaterialTheme.typography.labelMedium)
                    Text(value, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun DecodedPayloadCard(decodedFields: Map<String, String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Decoded payload fields")
            if (decodedFields.isEmpty()) {
                Text("No decoded fields yet")
            } else {
                decodedFields.forEach { (k, v) -> Text("$k = $v", fontFamily = FontFamily.Monospace) }
            }
        }
    }

}

@Composable
private fun ConnectionStatusPopup(state: AppState, vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = { if (!state.busy) vm.dismissConnectionPopup() },
        title = { Text("Connection Status") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Current: ${state.connectionStep}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Divider()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.connectionStatusLines) { line ->
                        val color = when {
                            line.startsWith("ERROR") -> MaterialTheme.colorScheme.error
                            line.startsWith("SUCCESS") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = vm::dismissConnectionPopup,
                enabled = !state.busy
            ) {
                Text(if (state.busy) "Connecting..." else "Close")
            }
        }
    )
}