package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.DeviceInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KdcanSessionTest {
    @Test
    fun `build and parse preserves short KWP frame fields`() {
        val request = KwpFrameCodec.buildFrame(
            targetAddress = 0x12,
            sourceAddress = 0xF1,
            serviceId = 0x10,
            payload = listOf(0x81),
        )

        assertContentEquals(byteArrayOf(0x82.toByte(), 0x12, 0xF1.toByte(), 0x10, 0x81.toByte(), 0x16), request)
        val parsed = assertNotNull(KwpFrameCodec.parse(request))
        assertTrue(parsed.targetAddress == 0x12)
        assertTrue(parsed.sourceAddress == 0xF1)
        assertTrue(parsed.payload == listOf(0x10, 0x81))
    }

    @Test
    fun `parser rejects corrupted checksum`() {
        val valid = KwpFrameCodec.buildFrame(0x12, 0xF1, 0x10, listOf(0x81))
        val corrupted = valid.copyOf().also { it[it.lastIndex] = 0x00 }

        assertNotNull(KwpFrameCodec.parse(valid))
        assertFalse(KwpFrameCodec.parse(corrupted) != null)
    }

    @Test
    fun `session accepts a response when echo and response arrive in split reads`() = runBlocking {
        val transport = SplitEchoTransport()
        val session = KdcanSession(transport, BmwTargets.DME)
        val result = session.execute(BmwJobs.byId("start_session_default")!!)

        assertTrue(result.success)
        assertTrue(result.responseHex.contains("82 F1 12 50 81"))
    }

    @Test
    fun `live data continues to the local identifier after session start without tester present`() = runBlocking {
        val transport = AddressAwareTransport()
        val session = KdcanSession(transport, BmwTargets.DME)
        val result = session.executeOnTarget(BmwTargets.DME, BmwJobs.byId("dme_live_basic")!!)

        assertTrue(result.success)
        assertTrue(transport.requestedServices.containsAll(listOf(0x10, 0x21)))
        assertFalse(transport.requestedServices.contains(0x3E))
    }

    @Test
    fun `concurrent diagnostic requests keep their selected ECU target`() = runBlocking {
        val transport = AddressAwareTransport()
        val session = KdcanSession(transport, BmwTargets.DME)
        val dmeJob = BmwJobs.byId("start_session_default")!!

        val dme = async { session.executeOnTarget(BmwTargets.DME, dmeJob) }
        val cas = async { session.executeOnTarget(BmwTargets.CAS, dmeJob) }

        assertTrue(dme.await().target == BmwTargets.DME)
        assertTrue(cas.await().target == BmwTargets.CAS)
        assertTrue(transport.requestedTargets.containsAll(listOf(0x12, 0x40)))
    }

    private class SplitEchoTransport : Transport {
        private val reads = ArrayDeque<ByteArray>()

        override suspend fun listDevices(): List<DeviceInfo> = emptyList()
        override suspend fun connect(targetId: String?) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun purge() = Unit
        override fun isConnected(): Boolean = true

        override suspend fun write(bytes: ByteArray) {
            val response = KwpFrameCodec.buildFrame(
                targetAddress = 0xF1,
                sourceAddress = 0x12,
                serviceId = 0x50,
                payload = listOf(0x81),
            )
            reads.addLast(bytes.copyOfRange(0, 2))
            reads.addLast(bytes.copyOfRange(2, bytes.size) + response)
        }

        override suspend fun read(timeoutMs: Int): ByteArray =
            if (reads.isEmpty()) ByteArray(0) else reads.removeFirst()
    }

    private class AddressAwareTransport : Transport {
        private val reads = ArrayDeque<ByteArray>()
        val requestedTargets = mutableListOf<Int>()
        val requestedServices = mutableListOf<Int>()

        override suspend fun listDevices(): List<DeviceInfo> = emptyList()
        override suspend fun connect(targetId: String?) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun purge() = Unit
        override fun isConnected(): Boolean = true

        override suspend fun write(bytes: ByteArray) {
            val request = assertNotNull(KwpFrameCodec.parse(bytes))
            requestedTargets += request.targetAddress
            requestedServices += request.payload.first()
            val response = KwpFrameCodec.buildFrame(
                targetAddress = 0xF1,
                sourceAddress = request.targetAddress,
                serviceId = request.payload.first() + 0x40,
                payload = request.payload.drop(1),
            )
            reads.addLast(bytes + response)
        }

        override suspend fun read(timeoutMs: Int): ByteArray =
            if (reads.isEmpty()) ByteArray(0) else reads.removeFirst()
    }
}
