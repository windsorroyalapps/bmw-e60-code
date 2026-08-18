package com.bmwe60coderpro.network

import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import com.bmwe60coderpro.util.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.net.SocketTimeoutException

/** Per-read poll interval; also the quiet gap that ends a received burst. */
private const val ENET_POLL_TIMEOUT_MS = 250

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
        // The old implementation ignored timeoutMs entirely (constructor
        // soTimeout won every call) and returned the first TCP segment,
        // truncating BMW-FAST frames split across segments. Honor the caller's
        // deadline and accumulate until the stream goes quiet.
        socket?.soTimeout = ENET_POLL_TIMEOUT_MS
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        var sawData = false

        while (System.currentTimeMillis() < deadline) {
            val count = try {
                inp.read(buffer)
            } catch (e: SocketTimeoutException) {
                if (sawData) break else continue
            }
            when {
                count > 0 -> {
                    out.write(buffer, 0, count)
                    sawData = true
                }
                count < 0 -> {
                    // EOF — peer closed the connection.
                    runCatching { socket?.close() }
                    connected = false
                    break
                }
            }
        }
        out.toByteArray()
    }

    override suspend fun purge() = withContext(Dispatchers.IO) {
        val inp = socket?.getInputStream() ?: return@withContext
        // skip() is not guaranteed to skip all requested bytes — loop until drained.
        while (inp.available() > 0) {
            val skipped = inp.skip(inp.available().toLong())
            if (skipped <= 0L) {
                if (inp.read() < 0) break // EOF
            }
        }
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

    /**
     * Parse a BMW-FAST frame received over ENET/TCP.
     *
     * Frame layout (big-endian):
     *   [0..1] length of payload
     *   [2]    target address
     *   [3]    source address
     *   [4..]  payload (length bytes)
     *
     * Returns (target, payload) or null when the buffer is incomplete / invalid.
     */
    fun parseFastFrame(data: ByteArray): Pair<Int, ByteArray>? {
        // ENET header is 4 bytes: [LEN_HI, LEN_LO, TARGET, SOURCE]
        if (data.size < 4) return null
        
        val length = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        // Sanity: reject absurd lengths (BMW diagnostic payloads are typically < 4 KB)
        if (length > 4096) return null

        val target = data[2].toInt() and 0xFF
        // val source = data[3].toInt() and 0xFF // Source available if needed

        // Incomplete frame — wait for more data
        if (data.size < 4 + length) return null

        val payload = if (length == 0) ByteArray(0) else data.copyOfRange(4, 4 + length)
        return target to payload
    }
}
