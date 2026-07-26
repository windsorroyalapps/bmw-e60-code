package com.bmwe60coderpro.data

import android.content.Context

class AppPreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bmw_e60_coder_prefs", Context.MODE_PRIVATE)

    fun loadProfile(): ConnectionProfile = ConnectionProfile(
        transport = runCatching { TransportType.valueOf(prefs.getString("transport", TransportType.USB_KDCAN.name)!!) }.getOrDefault(TransportType.USB_KDCAN),
        baudRate = prefs.getInt("baudRate", 115200),
        tcpHost = prefs.getString("tcpHost", "192.168.0.10") ?: "192.168.0.10",
        tcpPort = prefs.getInt("tcpPort", 35000),
        connectTimeoutMs = prefs.getInt("connectTimeoutMs", 2000),
        readTimeoutMs = prefs.getInt("readTimeoutMs", 1500),
        settleDelayMs = prefs.getLong("settleDelayMs", 250L),
        adapterPreset = runCatching { AdapterPresetKind.valueOf(prefs.getString("adapterPreset", AdapterPresetKind.USB_FTDI_FAST.name)!!) }.getOrDefault(AdapterPresetKind.USB_FTDI_FAST),
        vehicleProfile = runCatching { VehicleProfileKind.valueOf(prefs.getString("vehicleProfile", VehicleProfileKind.GENERIC_E60.name)!!) }.getOrDefault(VehicleProfileKind.GENERIC_E60),
    )

    fun loadPollingInterval(defaultMs: Long = 1200L): Long = prefs.getLong("pollingIntervalMs", defaultMs)
    fun loadSelectedScreen(defaultScreen: ServiceScreen = ServiceScreen.OVERVIEW): ServiceScreen =
        runCatching { ServiceScreen.valueOf(prefs.getString("selectedScreen", defaultScreen.name)!!) }.getOrDefault(defaultScreen)

    fun saveProfile(profile: ConnectionProfile) {
        prefs.edit()
            .putString("transport", profile.transport.name)
            .putInt("baudRate", profile.baudRate)
            .putString("tcpHost", profile.tcpHost)
            .putInt("tcpPort", profile.tcpPort)
            .putInt("connectTimeoutMs", profile.connectTimeoutMs)
            .putInt("readTimeoutMs", profile.readTimeoutMs)
            .putLong("settleDelayMs", profile.settleDelayMs)
            .putString("adapterPreset", profile.adapterPreset.name)
            .putString("vehicleProfile", profile.vehicleProfile.name)
            .apply()
    }

    fun savePollingInterval(ms: Long) { prefs.edit().putLong("pollingIntervalMs", ms).apply() }
    fun saveSelectedScreen(screen: ServiceScreen) { prefs.edit().putString("selectedScreen", screen.name).apply() }
}
