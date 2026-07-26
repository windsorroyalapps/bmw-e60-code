package com.bmwe60coderpro.util

object HexUtils {
    fun hexToBytes(value: String): ByteArray {
        val clean = value.replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0) { "Hex string length must be even" }
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
