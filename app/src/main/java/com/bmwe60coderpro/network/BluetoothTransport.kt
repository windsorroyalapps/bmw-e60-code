package com.bmwe60coderpro.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothTransport(
    private val deviceAddress: String,
    private val connectTimeoutMs: Int = 10000,
    private val readTimeoutMs: Int = 3000
) : Transport {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connected = false
    private var resolvedAddress: String = ""
    private var connectedDeviceName: String = ""

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override suspend fun listDevices(): List<DeviceInfo> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.map {
            DeviceInfo(it.address, it.name ?: "Unknown")
        } ?: emptyList()
    }

    override suspend fun connect(targetId: String?) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not available")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is disabled")

        val address = targetId ?: deviceAddress
        if (address.isBlank()) {
            throw IllegalStateException("Bluetooth MAC address is required")
        }

        // Validate MAC format
        if (!isValidMacAddress(address)) {
            throw IllegalStateException("Invalid Bluetooth MAC address format: $address")
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid Bluetooth address: $address")
        }

        // Check if device is bonded (paired)
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            throw IllegalStateException("Device $address is not paired. Pair it in Android Bluetooth settings first.")
        }

        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket?.connect()
        inputStream = socket?.inputStream
        outputStream = socket?.outputStream
        connected = true
        resolvedAddress = address
        connectedDeviceName = device.name ?: "Unknown"
    }

    override suspend fun disconnect() {
        connected = false
        resolvedAddress = ""
        connectedDeviceName = ""
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    override fun isConnected(): Boolean = connected && socket?.isConnected == true

    override suspend fun write(bytes: ByteArray) {
        outputStream?.write(bytes)
        outputStream?.flush()
    }

    override suspend fun read(timeoutMs: Int): ByteArray {
        val buffer = ByteArray(4096)
        val read = inputStream?.read(buffer) ?: 0
        return if (read > 0) buffer.copyOf(read) else byteArrayOf()
    }

    /** Returns the MAC address of the currently connected device, or empty string if not connected */
    fun getConnectedDeviceMac(): String = if (connected) resolvedAddress else ""

    /** Returns the name of the currently connected device, or empty string if not connected */
    fun getConnectedDeviceName(): String = if (connected) connectedDeviceName else ""

    /** Validates a Bluetooth MAC address format (XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX) */
    fun isValidMacAddress(mac: String): Boolean {
        return mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
    }

    /** Gets device info from an already-connected/paired device by MAC address */
    fun getDeviceInfo(mac: String): Pair<String, String>? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!adapter.isEnabled) return null
        return try {
            val device = adapter.getRemoteDevice(mac)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                (device.name ?: "Unknown") to device.address
            } else null
        } catch (e: Exception) {
            null
        }
    }
}