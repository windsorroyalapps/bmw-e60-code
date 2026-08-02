package com.bmwe60coderpro.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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

    override fun open(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!adapter.isEnabled) return false

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            return false
        }

        return try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            connected = true
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    override fun close() {
        connected = false
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    override fun isConnected(): Boolean = connected && socket?.isConnected == true

    override fun send(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            connected = false
            false
        }
    }

    override fun receive(): ByteArray? {
        return try {
            val available = inputStream?.available() ?: 0
            if (available > 0) {
                val buffer = ByteArray(available)
                val read = inputStream?.read(buffer) ?: 0
                if (read > 0) buffer.copyOf(read) else null
            } else {
                null
            }
        } catch (e: Exception) {
            connected = false
            null
        }
    }

    override fun readWithTimeout(timeoutMs: Int): ByteArray? {
        return try {
            socket?.soTimeout = timeoutMs
            val buffer = ByteArray(4096)
            val read = inputStream?.read(buffer) ?: 0
            if (read > 0) buffer.copyOf(read) else null
        } catch (e: Exception) {
            null
        }
    }
}