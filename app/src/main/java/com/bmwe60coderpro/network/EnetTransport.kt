package com.bmwe60coderpro.network

import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import com.bmwe60coderpro.util.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Socket

/**
 * BMW ENET WiFi transport.
 *
 * Used for F-series (F10, F30, etc.) and E60 LCI with ENET cable.
 * Communicates over TCP port 50160 (diagnostic port) or 6801 (ICOM).
 *
 * Based on ediabaslib ENET implementation and community research.
 * ENET uses BMW-FAST protocol over raw TCP.
 *
 * Default IPs:
 * - ENET cable direct: 169.254.0.1 (auto-IP)
 * - WiFi adapter: 192.168.4.1 (ESP8266 hotspot) or assigned IP
 * - ICOM: DHCP-assigned IP
 */
class EnetTransport(
    private val host: String = "169.254.0.1",
    private val port: Int = 50160,
    private val readTimeoutMs: Int = 3000,
) : Transport {

    private var socket: Socket? = null
    private var connected = false

    override suspend fun listDevices(): List<DeviceInfo> = emptyList()

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        socket = Socket(host, port)
        socket!!.soTimeout = readTimeoutMs
        socket!!.tcpNoDelay = true
        connected = true

        // ENET handshake: send identification frame
        val identFrame = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        socket!!.getOutputStream().write(identFrame)
        socket!!.getOutputStream().flush()
        delay(100)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }
        socket = null
        connected = false
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.getOutputStream() ?: error("ENET not connected")
        out.write(bytes)
        out.flush()
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val inp = socket?.getInputStream() ?: error("ENET not connected")
        val buffer = ByteArray(8192)
        val count = inp.read(buffer)
        if (count > 0) buffer.copyOf(count) else ByteArray(0)
    }

    override fun isConnected(): Boolean = connected

    /** Build a BMW-FAST frame for ENET. */
    fun buildFastFrame(target: Int, source: Int, payload: ByteArray): ByteArray {
        val length = payload.size
        val header = byteArrayOf(
            (length shr 8).toByte(),
            (length and 0xFF).toByte(),
            target.toByte(),
            source.toByte(),
        )
        return header + payload
    }

    /** Parse a BMW-FAST frame from ENET. */
    fun parseFastFrame(data: ByteArray): Pair<Int, ByteArray>? {
        if (data.size < 4) return null
        val length = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val target = data[2].toInt() and 0xFF
        if (data.size < 4 + length) return null
        return target to data.copyOfRange(4, 4 + length)
    }
}
