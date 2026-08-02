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

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override suspend fun listDevices(): List<DeviceInfo> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.map {
            DeviceInfo(it.address, it.name ?: "Unknown", "bluetooth")
        } ?: emptyList()
    }

    override suspend fun connect(targetId: String?) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not available")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is disabled")

        val address = targetId ?: deviceAddress
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid Bluetooth address: $address")
        }

        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket?.connect()
        inputStream = socket?.inputStream
        outputStream = socket?.outputStream
        connected = true
    }

    override suspend fun disconnect() {
        connected = false
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
}