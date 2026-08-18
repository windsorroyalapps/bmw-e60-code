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
private const val SIM_POLL_TIMEOUT_MS = 250

/**
 * SIM / TCU remote transport.
 *
 * HOW REMOTE START VIA SIM WORKS
 * ─────────────────────────────────────────────────────────────────────────────
 * The E60 has no built-in SIM that exposes OBD at the application layer.
 * The design document describes two real-world approaches:
 *
 *  A) Android bridge device left in the car
 *     A second Android phone/tablet is left permanently connected to the OBD
 *     port via a K+DCAN cable (USB OTG). That device runs a tiny TCP relay
 *     service (e.g. the open-source "OBD-Gateway" app, or a Python socat bridge)
 *     that listens on a port and forwards raw bytes to/from the K+DCAN adapter.
 *     The bridge device uses the car's own 4G hotspot or its own SIM data.
 *     The remote phone connects to that device's public IP (or via a VPN/tunnel)
 *     on the configured port, and this transport carries the KWP frames over
 *     the TCP connection exactly as TcpObdTransport does for a local ENET adapter.
 *
 *  B) BMW TCU / Combox (factory telematics)
 *     Some E60s have a TCU (Telematics Control Unit) or Combox with a SIM.
 *     These expose an internal TCP socket on port 6801 (MOST/IBUS gateway) or
 *     the BMW ConnectedDrive API. That API is proprietary and requires BMW
 *     backend credentials — it is not implemented here. Use approach A.
 *
 * SETUP FOR APPROACH A
 *  1. Flash the bridge phone with a TCP relay app.
 *     Example using termux + socat on the bridge phone:
 *       socat TCP-LISTEN:35001,fork,reuseaddr \
 *             FILE:/dev/ttyUSB0,b115200,raw,echo=0
 *     (replace /dev/ttyUSB0 with the actual ttyUSB node of your K+DCAN adapter)
 *  2. Port-forward 35001 from the bridge phone's mobile IP, or use a VPN
 *     (e.g. Tailscale — free, works over 4G, no port forwarding needed).
 *  3. In this app, enter the bridge phone's VPN/mobile IP and port 35001
 *     in the SIM Remote Host / Port fields.
 *  4. Tap "Connect via SIM" — this transport opens a TCP socket to that address
 *     and the KWP session runs exactly as it does over a local ENET connection.
 *
 * This class is functionally identical to TcpObdTransport but carries its own
 * configurable defaults and a distinct type so the ViewModel can distinguish
 * a local ENET connection from a remote SIM connection.
 */
class SimRemoteTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 8000,   // longer — mobile data latency
    private val defaultReadTimeoutMs: Int = 4000, // longer — round-trip over internet
) : Transport {

    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    override suspend fun listDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        listOf(DeviceInfo(id = "sim:$host:$port", name = "SIM Remote Bridge ($host:$port)"))
    }

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.soTimeout = defaultReadTimeoutMs
        s.tcpNoDelay = true          // reduce latency on mobile data
        socket = s
        input  = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input  = null
        output = null
        socket = null
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = output ?: error("SIM transport not connected")
        out.write(bytes)
        out.flush()
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val inStream = input ?: error("SIM transport not connected")
        // Poll in short slices so a quiet gap after received data ends the burst
        // promptly, while the overall deadline still honors the (mobile-latency
        // adjusted) timeoutMs.
        socket?.soTimeout = SIM_POLL_TIMEOUT_MS
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(defaultReadTimeoutMs)
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

    override fun isConnected(): Boolean =
        socket?.isConnected == true && socket?.isClosed == false
}
