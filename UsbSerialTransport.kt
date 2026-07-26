package com.bmwe60coderpro.usb

import android.app.Application
import android.hardware.usb.UsbManager
import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbSerialTransport(
    private val application: Application,
    private val baudRate: Int = 115200,
    private val defaultReadTimeoutMs: Int = 1500,
) : Transport {
    private val usbManager = application.getSystemService(UsbManager::class.java)
    private val permissionManager = UsbPermissionManager(application.applicationContext)
    private var port: UsbSerialPort? = null

    override suspend fun listDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).mapIndexed { index, driver ->
            val device = driver.device
            DeviceInfo(
                id = "usb-$index-${device.deviceId}",
                name = device.productName ?: device.deviceName ?: "USB Serial Device",
                vendorId = device.vendorId,
                productId = device.productId,
            )
        }
    }

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val match = drivers.firstOrNull { driver ->
            targetId == null || targetId.endsWith(driver.device.deviceId.toString())
        } ?: error("No USB serial device found")

        val permitted = permissionManager.ensurePermission(usbManager, match.device)
        if (!permitted) error("USB permission denied")
        val connection = usbManager.openDevice(match.device)
            ?: error("USB device unavailable after permission grant")
        val serialPort = match.ports.firstOrNull() ?: error("No serial port on device")
        serialPort.open(connection)
        serialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        serialPort.dtr = true
        serialPort.rts = true
        port = serialPort
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { port?.close() }
        port = null
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val p = port ?: error("USB transport not connected")
        p.write(bytes, 1000)
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val p = port ?: error("USB transport not connected")
        val buffer = ByteArray(4096)
        val count = p.read(buffer, if (timeoutMs > 0) timeoutMs else defaultReadTimeoutMs)
        if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    }

    override fun isConnected(): Boolean = port != null
}
