package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.FlashMode
import com.bmwe60coderpro.data.FlashPlan
import com.bmwe60coderpro.util.HexUtils

object FlashingManager {
    fun plan(module: String, hex: String, mode: FlashMode, chunkSize: Int = 8): FlashPlan {
        val bytes = HexUtils.hexToBytes(hex).map { it.toInt() and 0xFF }
        val chunks = bytes.chunked(chunkSize)
        val frames = chunks.mapIndexed { index, chunk ->
            buildString {
                append("36 ")
                append(index.toString(16).padStart(2, '0').uppercase())
                if (chunk.isNotEmpty()) append(' ')
                append(chunk.joinToString(" ") { it.toString(16).padStart(2, '0').uppercase() })
            }.trim()
        }
        val summary = if (mode == FlashMode.DRY_RUN) {
            "Dry-run flash plan for $module with ${chunks.size} transfer chunk(s)"
        } else {
            "Expert write plan prepared for $module with ${chunks.size} transfer chunk(s)"
        }
        return FlashPlan(mode, module, bytes.size, chunkSize, chunks.size, frames, summary)
    }
}
