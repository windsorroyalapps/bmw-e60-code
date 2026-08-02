package com.bmwe60coderpro.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import com.bmwe60coderpro.util.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private var elmVersion = ""

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override suspend fun listDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext emptyList()
        if (!adapter.isEnabled) return@withContext emptyList()
        adapter.bondedDevices?.map {
            DeviceInfo(it.address, it.name ?: "Unknown")
        } ?: emptyList()
    }

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not available")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is disabled")

        val address = targetId ?: deviceAddress
        if (address.isBlank()) {
            throw IllegalStateException("Bluetooth MAC address is required")
        }
        if (!isValidMacAddress(address)) {
            throw IllegalStateException("Invalid Bluetooth MAC address format: $address")
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid Bluetooth address: $address")
        }

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            throw IllegalStateException("Device $address is not paired. Pair it in Android Bluetooth settings first.")
        }

        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket?.connect()
        inputStream = socket?.inputStream
        outputStream = socket?.outputStream

        delay(500)
        sendRaw("ATZ")
        delay(500)
        sendRaw("ATE0")
        sendRaw("ATL1")
        sendRaw("ATS0")
        sendRaw("ATH1")
        sendRaw("ATSP0")

        elmVersion = readRaw(2000).trim()

        connected = true
        resolvedAddress = address
        connectedDeviceName = device.name ?: "Unknown"
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { sendRaw("ATZ") }
        connected = false
        resolvedAddress = ""
        connectedDeviceName = ""
        elmVersion = ""
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    override fun isConnected(): Boolean = connected && socket?.isConnected == true

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val hex = HexUtils.bytesToHex(bytes).replace(" ", "")
        sendRaw(hex)
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val raw = readRaw(timeoutMs)
            .replace(">", "")
            .replace("\r", "")
            .replace("\n", " ")
            .trim()

        if (raw.isEmpty() || raw == "NO DATA" || raw == "ERROR" || raw == "UNABLE TO CONNECT" || raw == "?") {
            return@withContext byteArrayOf()
        }

        try {
            HexUtils.hexToBytes(raw.replace(" ", ""))
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    fun getConnectedDeviceMac(): String = if (connected) resolvedAddress else ""
    fun getConnectedDeviceName(): String = if (connected) connectedDeviceName else ""
    fun getElmVersion(): String = elmVersion

    fun isValidMacAddress(mac: String): Boolean {
        return mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
    }

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

    private fun sendRaw(command: String) {
        val data = (command + "\r").toByteArray(Charsets.US_ASCII)
        outputStream?.write(data)
        outputStream?.flush()
    }

    private fun readRaw(timeoutMs: Int = 2000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            val available = inputStream?.available() ?: 0
            if (available > 0) {
                val buf = ByteArray(available)
                val read = inputStream?.read(buf) ?: 0
                if (read > 0) {
                    sb.append(String(buf.copyOf(read), Charsets.US_ASCII))
                    if (sb.contains(">")) break
                }
            } else {
                Thread.sleep(5)
            }
        }
        return sb.toString()
    }
}