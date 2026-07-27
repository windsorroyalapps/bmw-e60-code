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
        Log.d(TAG, "Found ${deviceList.size} USB devices")
        deviceList.values.map { device ->
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            val hasDriver = driver != null
            Log.d(TAG, "Device: ${device.deviceName} VID=${device.vendorId} PID=${device.productId} driver=$hasDriver")
            DeviceInfo(
                id = "${device.vendorId}:${device.productId}",
                name = driver?.device?.productName ?: device.deviceName,
                type = if (hasDriver) "USB_SERIAL" else "USB_UNKNOWN",
            )
        }
    }

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        val usbManager = application.getSystemService(UsbManager::class.java)
            ?: error("UsbManager not available")

        val deviceList = usbManager.deviceList
        Log.d(TAG, "Found ${deviceList.size} USB devices")

        for (device in deviceList.values) {
            Log.d(TAG, "Checking device: ${device.deviceName} VID=${device.vendorId} PID=${device.productId}")
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver != null) {
                Log.d(TAG, "Driver found: ${driver.javaClass.simpleName}")
                if (targetId != null && "${device.vendorId}:${device.productId}" != targetId) {
                    Log.d(TAG, "Skipping: targetId mismatch (want $targetId)")
                    continue
                }
                val granted = permissionManager.ensurePermission(usbManager, device)
                if (!granted) {
                    error("USB permission denied for ${device.deviceName}. Please grant permission in the system dialog.")
                }
                port = driver.ports.firstOrNull()
                    ?: error("No serial port on ${device.deviceName}")
                port?.open(usbManager.openDevice(device))
                    ?: error("Failed to open USB device")
                port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                port?.dtr = true
                port?.rts = true
                connected = true
                Log.i(TAG, "Connected to ${device.deviceName}")
                return@withContext
            } else {
                Log.w(TAG, "No driver for ${device.deviceName} VID=${device.vendorId} PID=${device.productId}")
            }
        }
        val found = deviceList.values.joinToString { "${it.vendorId}:${it.productId}" }
        error("No USB serial device found. Detected: [$found]. Make sure your K+DCAN cable is plugged in.")
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { port?.close() }
        port = null
        connected = false
        Log.d(TAG, "Disconnected")
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        port?.write(bytes, 1000) ?: error("USB port not open")
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        val count = port?.read(buffer, timeoutMs) ?: 0
        buffer.copyOf(count.coerceAtLeast(0))
    }

    override fun isConnected(): Boolean = connected && port != null
}
