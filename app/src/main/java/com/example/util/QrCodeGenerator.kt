package com.example.util

import com.example.data.local.BookingEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    /**
     * Builds a clean, human-readable & scanner-friendly UTF-8 ticket payload
     * so any phone camera or QR reader displays clear booking details immediately.
     */
    fun buildTicketPayload(booking: BookingEntity): String {
        val etaLabel = if (booking.estimatedMinutes <= 0) "الآن" else "${booking.estimatedMinutes} دقيقة"
        return buildString {
            appendLine("دوري | DORI")
            appendLine("التذكرة: #${booking.queueNumber} (${booking.ticketCode})")
            appendLine("الجهة: ${booking.venueName}")
            appendLine("الخدمة: ${booking.selectedOption}")
            appendLine("الاسم: ${booking.customerName}")
            appendLine("الهاتف: ${booking.customerPhone}")
            appendLine("قبلك: ${booking.peopleAhead} | التقديري: $etaLabel")
            if (booking.optionalInfo.isNotBlank()) {
                append("إضافي: ${booking.optionalInfo}")
            }
        }.trim()
    }

    /**
     * Generates a true ISO/IEC 18004 QR BitMatrix via ZXing with UTF-8 encoding,
     * M-level error correction, and a 2-module quiet zone for reliable camera scanning.
     */
    fun generateMatrix(payload: String): Array<BooleanArray> {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        return Array(height) { row ->
            BooleanArray(width) { col ->
                bitMatrix.get(col, row)
            }
        }
    }
}
