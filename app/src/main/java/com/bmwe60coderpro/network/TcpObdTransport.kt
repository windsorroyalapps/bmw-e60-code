package com.bmwe60coderpro.network

import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TcpObdTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 2000,
    private val defaultReadTimeoutMs: Int = 1500,
) : Transport {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    override suspend fun listDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        listOf(DeviceInfo(id = "$host:$port", name = "TCP OBD Adapter ($host:$port)"))
    }

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.soTimeout = defaultReadTimeoutMs
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        input?.close()
        output?.close()
        socket?.close()
        input = null
        output = null
        socket = null
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = output ?: error("TCP transport not connected")
        out.write(bytes)
        out.flush()
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        socket?.soTimeout = timeoutMs
        val inStream = input ?: error("TCP transport not connected")
        val buffer = ByteArray(4096)
        val count = inStream.read(buffer)
        if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    }

    override suspend fun purge() = withContext(Dispatchers.IO) {
        val inStream = input ?: return@withContext
        if (inStream.available() > 0) {
            inStream.skip(inStream.available().toLong())
        }
    }

    override fun isConnected(): Boolean = socket?.isConnected == true && socket?.isClosed == false
}
