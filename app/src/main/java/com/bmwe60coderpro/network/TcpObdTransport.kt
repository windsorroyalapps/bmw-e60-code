package com.bmwe60coderpro.network

import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Per-read poll interval; also the quiet gap that ends a received burst. */
private const val TCP_POLL_TIMEOUT_MS = 250

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
        val inStream = input ?: error("TCP transport not connected")
        // Poll in short slices so a quiet gap after received data ends the burst
        // promptly, while the overall deadline still honors timeoutMs.
        socket?.soTimeout = TCP_POLL_TIMEOUT_MS
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        var sawData = false

        while (System.currentTimeMillis() < deadline) {
            val count = try {
                inStream.read(buffer)
            } catch (e: SocketTimeoutException) {
                // Previously this escaped uncaught and crashed the caller.
                if (sawData) break else continue
            }
            when {
                count > 0 -> {
                    out.write(buffer, 0, count)
                    sawData = true
                }
                count < 0 -> {
                    // EOF — peer closed the connection. Mark it so isConnected() is honest.
                    runCatching { socket?.close() }
                    break
                }
            }
        }
        out.toByteArray()
    }

    override suspend fun purge() = withContext(Dispatchers.IO) {
        val inStream = input ?: return@withContext
        // skip() is not guaranteed to skip all requested bytes — loop until drained.
        while (inStream.available() > 0) {
            val skipped = inStream.skip(inStream.available().toLong())
            if (skipped <= 0L) {
                if (inStream.read() < 0) break // EOF
            }
        }
    }

    override fun isConnected(): Boolean = socket?.isConnected == true && socket?.isClosed == false
}
