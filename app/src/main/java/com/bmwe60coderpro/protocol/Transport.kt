package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.DeviceInfo

interface Transport {
    suspend fun listDevices(): List<DeviceInfo>
    suspend fun connect(targetId: String? = null)
    suspend fun disconnect()
    suspend fun write(bytes: ByteArray)
    suspend fun read(timeoutMs: Int = 1200): ByteArray
    /** Clear any stale data from the adapter's RX buffer. */
    suspend fun purge()
    fun isConnected(): Boolean
}
