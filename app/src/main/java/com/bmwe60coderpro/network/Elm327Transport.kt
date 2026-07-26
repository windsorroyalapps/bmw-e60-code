package com.bmwe60coderpro.network

import com.bmwe60coderpro.data.DeviceInfo
import com.bmwe60coderpro.protocol.Transport
import com.bmwe60coderpro.util.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * ELM327 Bluetooth / WiFi transport.
 *
 * Supports:
 * - Standard ELM327 Bluetooth adapters (PIC18F2480, PIC18F25K80)
 * - WiFi ELM327 adapters (ESP8266-based)
 * - Adapters with replacement firmware for BMW-FAST / K-Line
 *
 * Based on research from ediabaslib (uholeschak) and E46Track (tomicooler).
 * ELM327 commands are sent as ASCII with \r terminator.
 *
 * IMPORTANT: For BMW KWP2000 / DS2 / BMW-FAST, the ELM327 must have
 * replacement firmware or be a PIC18F25K80-based adapter. Standard MCP2515
 * clones will NOT work with K-Line protocols.
 */
class Elm327Transport(
    private val connectionType: ConnectionType = ConnectionType.BLUETOOTH_SPP,
    private val host: String = "192.168.0.10",
    private val port: Int = 35000,
    private val readTimeoutMs: Int = 2000,
) : Transport {

    enum class ConnectionType { BLUETOOTH_SPP, WIFI_TCP }

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var connected = false
    private var elmVersion = ""

    override suspend fun listDevices(): List<DeviceInfo> = emptyList()

    override suspend fun connect(targetId: String?) = withContext(Dispatchers.IO) {
        if (connectionType == ConnectionType.WIFI_TCP) {
            socket = Socket(host, port)
            socket!!.soTimeout = readTimeoutMs
            input = socket!!.getInputStream()
            output = socket!!.getOutputStream()
        } else {
            // Bluetooth SPP — requires Android BluetoothSocket, handled externally
            error("Bluetooth SPP requires platform-specific pairing. Use WiFi TCP or USB FTDI instead.")
        }

        // Initialize ELM327
        sendCommand("ATZ")      // Reset
        delay(500)
        sendCommand("ATE0")     // Echo off
        sendCommand("ATL1")     // Linefeeds on
        sendCommand("ATS0")     // Spaces off
        sendCommand("ATH1")     // Headers on (show CAN IDs)
        sendCommand("ATSP0")    // Auto protocol detect

        elmVersion = sendCommand("ATI").trim()
        connected = true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { sendCommand("ATZ") }
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
        connected = false
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val hex = HexUtils.bytesToHex(bytes)
        // For KWP over ELM327, we send raw hex bytes
        sendCommand(hex)
    }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val response = readRawResponse(timeoutMs)
        // Parse ELM327 response: remove prompts, convert hex to bytes
        parseElmResponse(response)
    }

    override fun isConnected(): Boolean = connected

    // ── ELM327-specific helpers ─────────────────────────────────────────────

    /** Send an AT command or raw hex and return the ASCII response. */
    private fun sendCommand(cmd: String): String {
        val out = output ?: error("ELM327 not connected")
        out.write("$cmd\r".toByteArray())
        out.flush()
        return readRawResponse(500)
    }

    private fun readRawResponse(timeoutMs: Int): String {
        val inp = input ?: return ""
        val buffer = StringBuilder()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (inp.available() > 0) {
                val b = inp.read()
                if (b == '>'.code) break // ELM prompt
                if (b >= 0) buffer.append(b.toChar())
            } else {
                Thread.sleep(10)
            }
        }
        return buffer.toString()
    }

    private fun parseElmResponse(raw: String): ByteArray {
        // Remove \r, \n, spaces, and "SEARCHING..." etc.
        val cleaned = raw
            .replace("\r", "")
            .replace("\n", " ")
            .replace("SEARCHING...", "")
            .replace("STOPPED", "")
            .replace("UNABLE TO CONNECT", "")
            .replace("NO DATA", "")
            .replace("ERROR", "")
            .replace(" ", "")
            .trim()

        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return ByteArray(0)

        return try {
            HexUtils.hexToBytes(cleaned)
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    /** Configure ELM327 for BMW KWP2000 / BMW-FAST over CAN. */
    suspend fun configureForBmwKwp() {
        sendCommand("ATZ")
        delay(300)
        sendCommand("ATE0")
        sendCommand("ATL1")
        sendCommand("ATS0")
        sendCommand("ATH1")
        sendCommand("ATCAF0")   // CAN auto format off
        sendCommand("ATCFC1")   // CAN flow control on
        sendCommand("ATSH7E0")  // Set CAN header (example: DME)
        sendCommand("ATST96")   // Set timeout (96 = ~150ms)
        sendCommand("ATSP6")    // ISO 15765-4 CAN (11-bit, 500kbps)
    }

    /** Configure ELM327 for BMW-FAST (BMW-specific CAN protocol). */
    suspend fun configureForBmwFast() {
        sendCommand("ATZ")
        delay(300)
        sendCommand("ATE0")
        sendCommand("ATL1")
        sendCommand("ATS0")
        sendCommand("ATH1")
        sendCommand("ATCAF1")   // CAN auto format on
        sendCommand("ATCFC1")   // CAN flow control on
        sendCommand("ATSH600")  // BMW-FAST header base
        sendCommand("ATST96")
        sendCommand("ATSP6")
    }

    fun getElmVersion(): String = elmVersion
}
