package com.bmwe60coderpro.usb

import android.app.Application
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UsbSerialTransport"

class UsbSerialTransport(
    private val application: Application,
    private val permissionManager: UsbPermissionManager,
) : Transport {

    private var port: UsbSerialPort? = null
    private var connected = false

    override suspend fun listDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val usbManager = application.getSystemService(UsbManager::class.java)
            ?: return@withContext emptyList()
        val deviceList = usbManager.deviceList
        Log.d(TAG, "=== USB SCAN === Found ${deviceList.size} total USB devices")
        
        if (deviceList.isEmpty()) {
            Log.w(TAG, "No USB devices detected at all. Is OTG enabled? Is cable plugged in?")
        }
        
        deviceList.values.map { device ->
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            val hasDriver = driver != null
            val hasPermission = usbManager.hasPermission(device)
            Log.d(TAG, "Device: ${device.deviceName} VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)} driver=$hasDriver permission=$hasPermission")
            DeviceInfo(
                id = "${device.vendorId}:${device.productId}",
                name = driver?.device?.productName ?: device.deviceName,
            )
        }
    }

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        val usbManager = application.getSystemService(UsbManager::class.java)
            ?: error("UsbManager not available. Is this an Android device with USB host?")

        val deviceList = usbManager.deviceList
        Log.d(TAG, "=== CONNECT === Found ${deviceList.size} USB devices")
        
        if (deviceList.isEmpty()) {
            error("No USB devices found. Check:\n1. OTG adapter is connected\n2. K+DCAN cable is plugged in\n3. Cable has power (LED on?)")
        }

        // First try devices with known drivers
        for (device in deviceList.values) {
            Log.d(TAG, "Trying: ${device.deviceName} VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver != null) {
                Log.d(TAG, "Driver found: ${driver.javaClass.simpleName} for ${device.deviceName}")
                if (targetId != null && "${device.vendorId}:${device.productId}" != targetId) {
                    Log.d(TAG, "Skipping: targetId mismatch (want $targetId, have ${device.vendorId}:${device.productId})")
                    continue
                }
                
                // Request permission if needed
                if (!usbManager.hasPermission(device)) {
                    Log.d(TAG, "Requesting permission for ${device.deviceName}...")
                    val granted = permissionManager.ensurePermission(usbManager, device)
                    if (!granted) {
                        Log.w(TAG, "Permission denied for ${device.deviceName}")
                        continue // Try next device instead of failing
                    }
                }
                
                port = driver.ports.firstOrNull()
                if (port == null) {
                    Log.w(TAG, "No serial ports on ${device.deviceName}")
                    continue
                }
                
                val connection = usbManager.openDevice(device)
                if (connection == null) {
                    Log.w(TAG, "Failed to open ${device.deviceName}")
                    continue
                }
                
                try {
                    port?.open(connection)
                    port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    port?.dtr = true
                    port?.rts = true
                    connected = true
                    Log.i(TAG, "SUCCESS: Connected to ${device.deviceName}")
                    return@withContext
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure ${device.deviceName}: ${e.message}")
                    runCatching { port?.close() }
                    port = null
                }
            } else {
                Log.w(TAG, "No driver for ${device.deviceName}. VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
            }
        }
        
        // Build helpful error message
        val found = deviceList.values.joinToString("\n") { 
            "  - ${it.deviceName}: VID=0x${it.vendorId.toString(16)} PID=0x${it.productId.toString(16)}" 
        }
        error("No compatible USB serial device found.\n\nDetected devices:\n$found\n\nYour cable may need a different driver. Common K+DCAN chips: FTDI(0x0403), CH340(0x1A86), CP2102(0x10C4), PL2303(0x067B)")
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
        runCatching { port?.close() }
        port = null
        connected = false
        }
        Log.d(TAG, "Disconnected")
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        port?.write(bytes, 1000) ?: error("USB port not open. Did you call connect() first?")
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        val count = port?.read(buffer, timeoutMs) ?: 0
        buffer.copyOf(count.coerceAtLeast(0))
    }

    override suspend fun purge() = withContext(Dispatchers.IO) {
        // Drain any stale data in the RX buffer
        val buffer = ByteArray(1024)
        while ((port?.read(buffer, 1) ?: 0) > 0) {
            // Keep draining
        }
    }

    override fun isConnected(): Boolean = connected && port != null
}

