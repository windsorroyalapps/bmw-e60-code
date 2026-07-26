package com.bmwe60coderpro.protocol

/**
 * SZL F-series button monitor.
 *
 * The F-series multifunction steering wheel sends button states over LIN to the SZL, which
 * re-exposes them as KWP local-identifier 0x02 (angle / button block). On an E60 that has been
 * coded with SZL_LENKRAD_TYP = F_SERIE the byte layout of button_matrix_1 (body[2]) and
 * button_matrix_2 (body[3]) changes from the legacy E-series pattern.
 *
 * F-series button_matrix_1 (byte offset 2 of 0x02 response body):
 *   bit 0  – Volume UP
 *   bit 1  – Volume DOWN
 *   bit 2  – Next track / seek forward
 *   bit 3  – Previous track / seek backward
 *   bit 4  – Voice / Dictation
 *   bit 5  – Telephone answer
 *   bit 6  – Telephone end / reject
 *   bit 7  – MODE / source toggle
 *
 * F-series button_matrix_2 (byte offset 3 of 0x02 response body):
 *   bit 0  – Paddle RIGHT (upshift)
 *   bit 1  – Paddle LEFT (downshift)
 *   bit 2  – Sport / Drive mode
 *   bit 3  – Heated wheel toggle (if equipped)
 *   bit 4  – OK / select (iDrive shortcut)
 *   bit 5..7 – reserved / not mapped on all variants
 *
 * These are community-derived bit positions based on reverse-engineered SZL traces from
 * F10/F30 wheels fitted to E60/E61 platforms. Validate on your specific wheel variant before
 * relying on them for live injection.
 */

data class SzlButtonFrame(
    /** Raw byte at body[2] of the 0x02 response */
    val matrix1Raw: Int,
    /** Raw byte at body[3] of the 0x02 response */
    val matrix2Raw: Int,
    /** Human-readable active button names */
    val activeButtons: List<String>,
    /** MFL events this frame should produce on the E-series bus */
    val mflEvents: List<MflEvent>,
)

data class MflEvent(
    val label: String,
    /** KWP output-control payload bytes to write to KOMBI (0x80) for this event.
     *  Service 0x30 = inputOutputControlByLocalIdentifier.
     *  Local ID 0xA0 = MFL button input block (community-verified for E60 KOMBI).
     *  byte[0] = 0xA0 (local ID)
     *  byte[1] = control option (0x03 = freeze / short activation pulse)
     *  byte[2] = MFL button code (see MflButtonCode) */
    val kwpPayload: List<Int>,
)

/** KWP MFL button codes as seen by E60 KOMBI local ID 0xA0. */
object MflButtonCode {
    const val VOLUME_UP = 0x01
    const val VOLUME_DOWN = 0x02
    const val NEXT_TRACK = 0x03
    const val PREV_TRACK = 0x04
    const val VOICE = 0x05
    const val PHONE_ANSWER = 0x06
    const val PHONE_END = 0x07
    const val MODE = 0x08
    const val PADDLE_UP = 0x10
    const val PADDLE_DOWN = 0x11
    const val SPORT_MODE = 0x12
    const val OK_SELECT = 0x13
    const val HEAT_WHEEL = 0x14
    const val NONE = 0x00
}

object SzlButtonDecoder {

    private data class BitMapping(
        val byte: Int,         // 0 = matrix1, 1 = matrix2
        val bit: Int,
        val name: String,
        val mflCode: Int,
    )

    private val mappings = listOf(
        BitMapping(0, 0, "Vol+",        MflButtonCode.VOLUME_UP),
        BitMapping(0, 1, "Vol-",        MflButtonCode.VOLUME_DOWN),
        BitMapping(0, 2, "Next",        MflButtonCode.NEXT_TRACK),
        BitMapping(0, 3, "Prev",        MflButtonCode.PREV_TRACK),
        BitMapping(0, 4, "Voice",       MflButtonCode.VOICE),
        BitMapping(0, 5, "Phone ✔",     MflButtonCode.PHONE_ANSWER),
        BitMapping(0, 6, "Phone ✘",     MflButtonCode.PHONE_END),
        BitMapping(0, 7, "MODE",        MflButtonCode.MODE),
        BitMapping(1, 0, "Paddle▶",     MflButtonCode.PADDLE_UP),
        BitMapping(1, 1, "◀Paddle",     MflButtonCode.PADDLE_DOWN),
        BitMapping(1, 2, "Sport",       MflButtonCode.SPORT_MODE),
        BitMapping(1, 3, "HeatWheel",   MflButtonCode.HEAT_WHEEL),
        BitMapping(1, 4, "OK",          MflButtonCode.OK_SELECT),
    )

    fun decode(matrix1: Int, matrix2: Int): SzlButtonFrame {
        val bytes = listOf(matrix1, matrix2)
        val active = mutableListOf<String>()
        val events = mutableListOf<MflEvent>()

        for (m in mappings) {
            if ((bytes[m.byte] shr m.bit) and 1 == 1) {
                active += m.name
                if (m.mflCode != MflButtonCode.NONE) {
                    events += MflEvent(
                        label = m.name,
                        kwpPayload = listOf(0xA0, 0x03, m.mflCode),
                    )
                }
            }
        }

        return SzlButtonFrame(
            matrix1Raw = matrix1,
            matrix2Raw = matrix2,
            activeButtons = active,
            mflEvents = events,
        )
    }

    /** Produce a human-readable diff label from two consecutive frames for the UI log. */
    fun diff(prev: SzlButtonFrame?, curr: SzlButtonFrame): String {
        if (prev == null) return if (curr.activeButtons.isEmpty()) "—" else "PRESS: ${curr.activeButtons}"
        val pressed = curr.activeButtons - prev.activeButtons.toSet()
        val released = prev.activeButtons - curr.activeButtons.toSet()
        return buildString {
            if (pressed.isNotEmpty()) append("↓${pressed} ")
            if (released.isNotEmpty()) append("↑${released}")
            if (isEmpty()) append("—")
        }.trim()
    }
}
