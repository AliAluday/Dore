package com.example.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object QrCodeGenerator {
    const val SIZE = 25

    fun generateMatrix(payload: String): Array<BooleanArray> {
        val matrix = Array(SIZE) { BooleanArray(SIZE) }
        val reserved = Array(SIZE) { BooleanArray(SIZE) }

        fun placeFinder(startRow: Int, startCol: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = startRow + r
                    val cc = startCol + c
                    if (rr !in 0 until SIZE || cc !in 0 until SIZE) continue
                    reserved[rr][cc] = true
                    val isBorder = r == -1 || r == 7 || c == -1 || c == 7
                    if (isBorder) {
                        matrix[rr][cc] = false
                    } else {
                        val onOuter = r == 0 || r == 6 || c == 0 || c == 6
                        val inInner = r in 2..4 && c in 2..4
                        matrix[rr][cc] = onOuter || inInner
                    }
                }
            }
        }

        // Three corner finder patterns
        placeFinder(0, 0)
        placeFinder(0, SIZE - 7)
        placeFinder(SIZE - 7, 0)

        // Version 2 alignment pattern at (18, 18) -> 16..20
        for (r in 16..20) {
            for (c in 16..20) {
                reserved[r][c] = true
                val onOuter = r == 16 || r == 20 || c == 16 || c == 20
                val isCenter = r == 18 && c == 18
                matrix[r][c] = onOuter || isCenter
            }
        }

        // Timing patterns
        for (i in 8 until SIZE - 8) {
            reserved[6][i] = true
            matrix[6][i] = i % 2 == 0
            reserved[i][6] = true
            matrix[i][6] = i % 2 == 0
        }

        // Dark module
        reserved[SIZE - 8][8] = true
        matrix[SIZE - 8][8] = true

        // Deterministic hash stream from payload for data modules
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        val extendedBits = BooleanArray(SIZE * SIZE)
        var bitIndex = 0
        var round = 0
        while (bitIndex < extendedBits.size) {
            val roundBytes = if (round == 0) digest else {
                MessageDigest.getInstance("SHA-256").digest(digest + byteArrayOf(round.toByte()))
            }
            for (b in roundBytes) {
                for (bit in 7 downTo 0) {
                    if (bitIndex < extendedBits.size) {
                        extendedBits[bitIndex++] = ((b.toInt() shr bit) and 1) == 1
                    }
                }
            }
            round++
        }

        var idx = 0
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                if (!reserved[r][c]) {
                    matrix[r][c] = extendedBits[idx % extendedBits.size]
                    idx++
                }
            }
        }

        return matrix
    }
}
