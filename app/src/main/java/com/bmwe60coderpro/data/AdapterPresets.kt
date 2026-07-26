package com.bmwe60coderpro.data

object AdapterPresets {
    val all = listOf(
        AdapterPreset(
            kind = AdapterPresetKind.USB_FTDI_FAST,
            label = "USB FTDI K+DCAN Fast",
            transport = TransportType.USB_KDCAN,
            baudRate = 115200,
            connectTimeoutMs = 2000,
            readTimeoutMs = 1500,
            settleDelayMs = 250,
            notes = "Best default for proper FTDI-based K+DCAN cables."
        ),
        AdapterPreset(
            kind = AdapterPresetKind.USB_FTDI_SAFE,
            label = "USB FTDI K+DCAN Safe",
            transport = TransportType.USB_KDCAN,
            baudRate = 57600,
            connectTimeoutMs = 2500,
            readTimeoutMs = 1800,
            settleDelayMs = 350,
            notes = "Slower, safer USB profile for marginal OTG hubs or unstable cars."
        ),
        AdapterPreset(
            kind = AdapterPresetKind.USB_CH340_SAFE,
            label = "USB CH340 Clone Safe",
            transport = TransportType.USB_KDCAN,
            baudRate = 38400,
            connectTimeoutMs = 3000,
            readTimeoutMs = 2200,
            settleDelayMs = 450,
            notes = "Use for cheap CH340/clone serial adapters that drop frames at high baud."
        ),
        AdapterPreset(
            kind = AdapterPresetKind.ETH_ENET,
            label = "Ethernet ENET Direct",
            transport = TransportType.ETHERNET_OBD,
            tcpHost = "192.168.0.10",
            tcpPort = 6801,
            connectTimeoutMs = 1500,
            readTimeoutMs = 1200,
            settleDelayMs = 150,
            notes = "Direct ENET-style Ethernet adapter profile."
        ),
        AdapterPreset(
            kind = AdapterPresetKind.ETH_GENERIC_TCP,
            label = "Ethernet Generic TCP OBD",
            transport = TransportType.ETHERNET_OBD,
            tcpHost = "192.168.0.10",
            tcpPort = 35000,
            connectTimeoutMs = 2500,
            readTimeoutMs = 1800,
            settleDelayMs = 250,
            notes = "Generic TCP bridge for Ethernet-to-serial style OBD adapters."
        ),
    )

    fun byKind(kind: AdapterPresetKind): AdapterPreset = all.first { it.kind == kind }
}
