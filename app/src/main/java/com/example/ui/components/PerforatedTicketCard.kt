package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BookingEntity
import com.example.data.model.TicketStage
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StatusApproaching
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusUrgent
import com.example.util.IntentHelpers
import com.example.util.QrCodeGenerator

@Composable
fun QrCodeCanvas(
    payload: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color(0xFF0A1212),
    lightColor: Color = Color.White
) {
    val matrix = remember(payload) { QrCodeGenerator.generateMatrix(payload) }
    Canvas(
        modifier = modifier
            .background(lightColor, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        val count = matrix.size
        val cellSize = size.minDimension / count.toFloat()
        val offsetX = (size.width - cellSize * count) / 2f
        val offsetY = (size.height - cellSize * count) / 2f
        for (r in 0 until count) {
            for (c in 0 until count) {
                if (matrix[r][c]) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(offsetX + c * cellSize, offsetY + r * cellSize),
                        size = Size(cellSize + 0.4f, cellSize + 0.4f)
                    )
                }
            }
        }
    }
}

fun perforatedTicketShape(cutoutRatio: Float = 0.56f) = GenericShape { size, _ ->
    val corner = 44f
    val notchR = 26f
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
    onZoomQr: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onCompleteClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = TicketStage.entries.find { it.name == booking.stage } ?: TicketStage.MONITORED
    val stageColor by animateColorAsState(
        targetValue = when (stage) {
            TicketStage.MONITORED -> EmeraldPrimary
            TicketStage.APPROACHING -> StatusApproaching
            TicketStage.YOUR_TURN -> StatusOpen
        },
        label = "stageColor"
    )

    val ticketShape = remember { perforatedTicketShape(0.55f) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ticketShape,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Upper Ticket Header + QR & ETA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stageColor.copy(alpha = 0.08f))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = stageColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${booking.venueCategory} • ${booking.venueCity}",
                                style = MaterialTheme.typography.labelMedium,
                                color = stageColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = booking.venueName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "الخدمة: ${booking.selectedOption}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = stageColor
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "رقم التذكرة",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = booking.ticketCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3-Stage Progress Indicator
                ThreeStageProgressIndicator(currentStage = stage)

                Spacer(modifier = Modifier.height(16.dp))

                // QR + ETA Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onZoomQr() }
                            .testTag("ticket_qr_button")
                    ) {
                        QrCodeCanvas(
                            payload = "DORI|${booking.ticketCode}|${booking.venueId}|${booking.customerPhone}",
                            modifier = Modifier.size(106.dp)
                        )
                        Surface(
                            shape = CircleShape,
                            color = EmeraldPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "تكبير رمز QR",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = stageColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "الوقت المتوقع للوصول",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (booking.estimatedMinutes <= 0) "حان دورك الآن!" else "${booking.estimatedMinutes} دقيقة (أمامك ${booking.peopleAhead})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = stageColor
                                    )
                                }
                            }
                        }

                        Text(
                            text = "اضغط على رمز QR للتكبير والمسح عند الاستقبال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Perforated Dashed Line
            val outlineColor = MaterialTheme.colorScheme.outline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 24.dp)
            ) {
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                )
            }

            // Lower Stub: Customer Auto-filled info & Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = booking.customerName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = booking.customerPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "التكلفة المقدرة: ${IntentHelpers.formatDinar(booking.priceDinar)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (booking.notes.isNotBlank()) {
                        Text(
                            text = "ملاحظة: ${booking.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions: WhatsApp Share + Complete + Cancel
                OutlinedButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_whatsapp_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مشاركة التذكرة عبر واتساب")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCompleteClick,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("complete_ticket_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إتمام (+10 نقاط)")
                    }

                    OutlinedButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUrgent),
                        modifier = Modifier
                            .weight(0.75f)
                            .testTag("cancel_ticket_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeStageProgressIndicator(currentStage: TicketStage) {
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
                reached -> EmeraldPrimary
                else -> MaterialTheme.colorScheme.outline
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (reached) dotColor else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (reached) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stage.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (index < stages.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .background(
                            if (index < currentStage.stepIndex) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
