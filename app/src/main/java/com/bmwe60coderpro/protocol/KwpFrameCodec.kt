package com.bmwe60coderpro.protocol

/**
 * ISO 14230/KWP frame helpers used by the direct K+DCAN serial path.
 *
 * The short-address frame layout is:
 * [format, target, source, payload..., additive checksum].
 * For a long-format frame, the payload length follows the address bytes.
 */
internal object KwpFrameCodec {
    private const val SHORT_FORMAT_MASK = 0x80
    private const val LONG_FORMAT_MASK = 0xC0
    private const val FORMAT_CLASS_MASK = 0xC0

    data class Frame(
        val bytes: ByteArray,
        val targetAddress: Int,
        val sourceAddress: Int,
        val payload: List<Int>,
    )

    data class LocatedFrame(
        val offset: Int,
        val frame: Frame,
    )

    fun buildFrame(targetAddress: Int, sourceAddress: Int, serviceId: Int, payload: List<Int>): ByteArray {
        val serviceData = listOf(serviceId and 0xFF) + payload.map { it and 0xFF }
        require(serviceData.size in 1..0x3F) { "Short KWP frame payload must contain 1..63 byte(s)" }

        val withoutChecksum = mutableListOf(
            SHORT_FORMAT_MASK or serviceData.size,
            targetAddress and 0xFF,
            sourceAddress and 0xFF,
        )
        withoutChecksum.addAll(serviceData)
        return (withoutChecksum + additiveChecksum(withoutChecksum)).map { it.toByte() }.toByteArray()
    }

    fun expectedFrameLength(buffer: ByteArray, offset: Int = 0): Int? {
        if (offset !in buffer.indices) return null
        return when (val formatClass = (buffer[offset].toInt() and 0xFF) and FORMAT_CLASS_MASK) {
            SHORT_FORMAT_MASK -> 3 + ((buffer[offset].toInt() and 0xFF) and 0x3F) + 1
            LONG_FORMAT_MASK -> {
                val lengthIndex = offset + 3
                if (lengthIndex < buffer.size) 4 + (buffer[lengthIndex].toInt() and 0xFF) + 1 else null
            }
            else -> null
        }
    }

    fun parse(frameBytes: ByteArray): Frame? {
        val expectedLength = expectedFrameLength(frameBytes) ?: return null
        if (expectedLength != frameBytes.size || frameBytes.size < 5) return null
        if ((frameBytes.last().toInt() and 0xFF) != additiveChecksum(frameBytes.dropLast(1))) return null

        val isLongFrame = ((frameBytes[0].toInt() and 0xFF) and FORMAT_CLASS_MASK) == LONG_FORMAT_MASK
        val payloadStart = if (isLongFrame) 4 else 3
        val payloadEnd = frameBytes.size - 1
        if (payloadStart >= payloadEnd) return null

        return Frame(
            bytes = frameBytes,
            targetAddress = frameBytes[1].toInt() and 0xFF,
            sourceAddress = frameBytes[2].toInt() and 0xFF,
            payload = frameBytes.copyOfRange(payloadStart, payloadEnd).map { it.toInt() and 0xFF },
        )
    }

    /**
     * Finds the first checksum-valid complete KWP frame in [buffer]. This tolerates
     * delayed echo remnants and transport noise without treating arbitrary bytes as ECU data.
     */
    fun findFirstCompleteFrame(buffer: ByteArray, startOffset: Int = 0): LocatedFrame? {
        if (startOffset !in 0..buffer.size) return null
        for (offset in startOffset until buffer.size) {
            val expectedLength = expectedFrameLength(buffer, offset) ?: continue
            if (expectedLength < 5 || offset + expectedLength > buffer.size) continue
            val candidate = buffer.copyOfRange(offset, offset + expectedLength)
            val frame = parse(candidate) ?: continue
            return LocatedFrame(offset, frame)
        }
        return null
    }

    fun isResponsePending(frame: Frame): Boolean =
        frame.payload.size >= 3 && frame.payload[0] == 0x7F && frame.payload[2] == 0x78

    fun additiveChecksum(bytes: Iterable<Int>): Int = bytes.sumOf { it and 0xFF } and 0xFF

    private fun additiveChecksum(bytes: List<Byte>): Int =
        additiveChecksum(bytes.map { it.toInt() and 0xFF })
}
