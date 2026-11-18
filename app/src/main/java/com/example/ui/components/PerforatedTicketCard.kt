package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookingEntity
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.ui.theme.AmberGold
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.StatusApproaching
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusUrgent
import com.example.util.IntentHelpers
import com.example.util.QrCodeGenerator

@Composable
fun QrCodeCanvas(
    payload: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White
) {
    val matrix = remember(payload) { QrCodeGenerator.generateMatrix(payload) }
    Canvas(
        modifier = modifier
            .background(lightColor, RoundedCornerShape(10.dp))
            .padding(4.dp)
    ) {
        drawRect(color = lightColor)
        val count = matrix.size
        if (count == 0) return@Canvas
        val cellSize = size.minDimension / count.toFloat()
        val offsetX = (size.width - cellSize * count) / 2f
        val offsetY = (size.height - cellSize * count) / 2f
        for (r in 0 until count) {
            val top = kotlin.math.floor((offsetY + r * cellSize).toDouble()).toFloat()
            val bottom = kotlin.math.ceil((offsetY + (r + 1) * cellSize).toDouble()).toFloat()
            for (c in 0 until count) {
                if (matrix[r][c]) {
                    val left = kotlin.math.floor((offsetX + c * cellSize).toDouble()).toFloat()
                    val right = kotlin.math.ceil((offsetX + (c + 1) * cellSize).toDouble()).toFloat()
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f))
                    )
                }
            }
        }
    }
}

fun perforatedTicketShape(cutoutRatio: Float = 0.62f) = GenericShape { size, _ ->
    val corner = 28f
    val notchR = 16f
    val notchY = size.height * cutoutRatio

    moveTo(corner, 0f)
    lineTo(size.width - corner, 0f)
    arcTo(
        rect = Rect(size.width - corner * 2, 0f, size.width, corner * 2),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(size.width, notchY - notchR)
    arcTo(
        rect = Rect(size.width - notchR, notchY - notchR, size.width + notchR, notchY + notchR),
        startAngleDegrees = -90f,
        sweepAngleDegrees = -180f,
        forceMoveTo = false
    )
    lineTo(size.width, size.height - corner)
    arcTo(
        rect = Rect(size.width - corner * 2, size.height - corner * 2, size.width, size.height),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(corner, size.height)
    arcTo(
        rect = Rect(0f, size.height - corner * 2, corner * 2, size.height),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(0f, notchY + notchR)
    arcTo(
        rect = Rect(-notchR, notchY - notchR, notchR, notchY + notchR),
        startAngleDegrees = 90f,
        sweepAngleDegrees = -180f,
        forceMoveTo = false
    )
    lineTo(0f, corner)
    arcTo(
        rect = Rect(0f, 0f, corner * 2, corner * 2),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    close()
}

@Composable
fun PerforatedTicketCard(
    booking: BookingEntity,
    controlConfig: UserAppControlConfig = UserAppControlConfig(),
    onZoomQr: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onCompleteClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = TicketStage.entries.find { it.name == booking.stage } ?: TicketStage.MONITORED
    val stageColor by animateColorAsState(
        targetValue = when (stage) {
            TicketStage.MONITORED -> IndigoAccentLight
            TicketStage.APPROACHING -> StatusApproaching
            TicketStage.YOUR_TURN -> StatusOpen
        },
        label = "stageColor"
    )

    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var copiedCode by remember(booking.id) { mutableStateOf(false) }
    val ticketShape = remember { perforatedTicketShape(0.62f) }
    val approval = BookingApprovalStatus.fromName(booking.approvalStatus)
    val approvalColor = when (approval) {
        BookingApprovalStatus.ACCEPTED -> StatusOpen
        BookingApprovalStatus.REJECTED -> StatusUrgent
        BookingApprovalStatus.PENDING -> AmberGold
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ticketShape,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact Gradient Ticket Top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                stageColor.copy(alpha = 0.16f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = booking.venueName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${booking.venueCategory} • ${booking.venueCity} • ${booking.selectedOption}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = approvalColor.copy(alpha = 0.14f),
                                border = BorderStroke(0.8.dp, approvalColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = when (approval) {
                                        BookingApprovalStatus.ACCEPTED ->
                                            "✓ مقبولة${if (booking.handledByProvider.isNotBlank()) " (${booking.handledByProvider})" else ""}"
                                        BookingApprovalStatus.REJECTED ->
                                            "✕ مرفوضة${if (booking.handledByProvider.isNotBlank()) " (${booking.handledByProvider})" else ""}"
                                        BookingApprovalStatus.PENDING -> "⏳ بانتظار القبول"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = approvalColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = IntentHelpers.formatDinar(booking.priceDinar),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Compact Queue Number + Tap-to-Copy Ticket Code Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = stageColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(booking.ticketCode))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                copiedCode = true
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "#${booking.queueNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (copiedCode) "✓ تم النسخ" else booking.ticketCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                ThreeStageProgressIndicator(currentStage = stage, activeColor = stageColor)

                Spacer(modifier = Modifier.height(6.dp))

                // Inline Metrics + Compact 58dp Clickable Mini-QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(0.8.dp, stageColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "قبلك",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${booking.peopleAhead}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = stageColor
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(0.8.dp, stageColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1.15f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "الانتظار",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = stageColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (booking.estimatedMinutes <= 0) "الآن!" else "${booking.estimatedMinutes} د",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = stageColor
                                )
                            }
                        }
                    }

                    if (controlConfig.enableQrCode) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.2.dp, stageColor.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onZoomQr()
                                }
                                .testTag("ticket_qr_button")
                        ) {
                            Box {
                                QrCodeCanvas(
                                    payload = QrCodeGenerator.buildTicketPayload(booking),
                                    modifier = Modifier.size(58.dp)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = stageColor,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "تكبير رمز QR",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Perforated Tear Divider
            val outlineColor = MaterialTheme.colorScheme.outline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 18.dp)
            ) {
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            }

            // Compact Bottom Ticket Stub
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = booking.customerName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = booking.customerPhone,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (booking.notes.isNotBlank() || booking.optionalInfo.isNotBlank()) {
                    val combinedNotes = listOf(booking.notes, booking.optionalInfo)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    Text(
                        text = combinedNotes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCompleteClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("complete_ticket_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (controlConfig.enableLoyalty && controlConfig.pointsPerBooking > 0) {
                                "إتمام (+${controlConfig.pointsPerBooking})"
                            } else {
                                "إتمام الخدمة"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCancelClick()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUrgent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("cancel_ticket_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("إلغاء", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }

                    if (controlConfig.enableWhatsAppShare) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShareWhatsApp()
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("share_whatsapp_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة عبر واتساب",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeStageProgressIndicator(currentStage: TicketStage, activeColor: Color) {
    val stages = TicketStage.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, stage ->
            val reached = index <= currentStage.stepIndex
            val isCurrent = index == currentStage.stepIndex
            val dotColor = when {
                isCurrent && stage == TicketStage.YOUR_TURN -> StatusOpen
                isCurrent && stage == TicketStage.APPROACHING -> AmberGold
                reached -> activeColor
                else -> MaterialTheme.colorScheme.outline
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (reached) dotColor else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (reached) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = stage.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (index < stages.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(
                            if (index < currentStage.stepIndex) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
