package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.BookingEntity
import com.example.data.model.BookingStatus
import com.example.ui.components.PerforatedTicketCard
import com.example.ui.components.QrCodeCanvas
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.DoriUiState
import com.example.util.IntentHelpers

@Composable
fun TicketsScreen(
    state: DoriUiState,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onSelectActiveBooking: (Long) -> Unit,
    onCompleteBooking: (Long, Int?, String?) -> Unit,
    onCancelBooking: (Long) -> Unit,
    onQuickRebook: (BookingEntity) -> Unit,
    onSubmitHistoryReview: (Long, Int, String) -> Unit,
    onExploreClick: () -> Unit,
    onSyncBridge: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var zoomedBooking by remember { mutableStateOf<BookingEntity?>(null) }
    var completingBooking by remember { mutableStateOf<BookingEntity?>(null) }
    var cancellingBooking by remember { mutableStateOf<BookingEntity?>(null) }
    var reviewingHistoryBooking by remember { mutableStateOf<BookingEntity?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            TabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("التذاكر النشطة (${state.activeBookings.size})") },
                    icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null) },
                    modifier = Modifier.testTag("tickets_active_tab")
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("السجل والتقييمات (${state.historyBookings.size})") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.testTag("tickets_history_tab")
                )
            }

            if (selectedSubTab == 0) {
                if (state.activeBookings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "لا توجد تذاكر نشطة حالياً",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "احجز دورك من قسم الاستكشاف أو اضغط على أسرع دور للحصول على تذكرة QR فورية.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onExploreClick,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                modifier = Modifier.testTag("go_to_explore_button")
                            ) {
                                Text("استكشف الأماكن المفتوحة")
                            }
                            OutlinedButton(
                                onClick = onOpenSupport,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("التواصل مع الدعم الفني")
                            }
                        }
                    }
                } else {
                    val selectedBooking = state.activeBookings.find { it.id == state.selectedActiveBookingId }
                        ?: state.activeBookings.first()

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!hasNotificationPermission) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "فعّل إذن الإشعارات لتنبيهك عند اقتراب وحلول دورك",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        TextButton(
                                            onClick = onRequestNotificationPermission,
                                            modifier = Modifier.testTag("tickets_enable_notifications_button")
                                        ) {
                                            Text("تفعيل", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.activeBookings.size > 1) {
                            item {
                                Text(
                                    text = "حجوزاتك النشطة (${state.activeBookings.size}) — تنقّل بسهولة:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.activeBookings, key = { it.id }) { item ->
                                        FilterChip(
                                            selected = item.id == selectedBooking.id,
                                            onClick = { onSelectActiveBooking(item.id) },
                                            label = { Text("#${item.queueNumber} • ${item.venueName}") },
                                            modifier = Modifier.testTag("multi_booking_chip_${item.id}")
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            PerforatedTicketCard(
                                booking = selectedBooking,
                                controlConfig = state.preferences.controlConfig,
                                onZoomQr = { zoomedBooking = selectedBooking },
                                onShareWhatsApp = {
                                    IntentHelpers.shareTicketViaWhatsApp(context, selectedBooking)
                                },
                                onCompleteClick = {
                                    if (state.preferences.controlConfig.enableReviews) {
                                        completingBooking = selectedBooking
                                    } else {
                                        onCompleteBooking(selectedBooking.id, null, null)
                                    }
                                },
                                onCancelClick = { cancellingBooking = selectedBooking }
                            )
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "المزامنة والمساعدة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onSyncBridge,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("tickets_sync_approval_button")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تحديث القبول", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Button(
                                            onClick = onOpenSupport,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("tickets_support_button")
                                        ) {
                                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("الدعم الفني", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // History & Reviews tab
                if (state.historyBookings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "السجل فارغ حالياً",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ستظهر هنا حجوزاتك المكتملة والملغاة مع إمكانية إعادة الحجز السريع والتقييم.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.historyBookings, key = { it.id }) { booking ->
                            HistoryBookingCard(
                                booking = booking,
                                reviewsEnabled = state.preferences.controlConfig.enableReviews,
                                onQuickRebook = { onQuickRebook(booking) },
                                onRateClick = { reviewingHistoryBooking = booking }
                            )
                        }
                    }
                }
            }
        }
    }

    // Zoomable QR Modal
    zoomedBooking?.let { b ->
        Dialog(onDismissRequest = { zoomedBooking = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = b.venueName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "رمز التذكرة: ${b.ticketCode}",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    QrCodeCanvas(
                        payload = com.example.util.QrCodeGenerator.buildTicketPayload(b),
                        modifier = Modifier.size(260.dp)
                    )
                    Text(
                        text = "اعرض هذا الرمز لموظف الاستقبال لمسح الدور",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { zoomedBooking = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }

    // Complete & Rate Dialog
    completingBooking?.let { b ->
        val ctrl = state.preferences.controlConfig
        RatingDialog(
            title = "إتمام الخدمة وتقييم ${b.venueName}",
            subtitle = if (ctrl.enableLoyalty) {
                "ستحصل فوراً على +${ctrl.pointsPerBooking} نقطة ولاء عند إتمام الخدمة."
            } else {
                "شارك تقييمك السريع بعد إتمام الخدمة."
            },
            confirmLabel = if (ctrl.enableLoyalty) "إتمام (+${ctrl.pointsPerBooking} نقطة)" else "إتمام الخدمة",
            onDismiss = { completingBooking = null },
            onConfirm = { stars, comment ->
                onCompleteBooking(b.id, stars, comment)
                completingBooking = null
            }
        )
    }

    // History Review Dialog
    reviewingHistoryBooking?.let { b ->
        RatingDialog(
            title = "تقييم ${b.venueName}",
            subtitle = "شارك تقييمك بالنجوم والتعليق لمساعدة بقية المستخدمين.",
            confirmLabel = "حفظ التقييم",
            onDismiss = { reviewingHistoryBooking = null },
            onConfirm = { stars, comment ->
                onSubmitHistoryReview(b.id, stars, comment)
                reviewingHistoryBooking = null
            }
        )
    }

    // Internal Cancellation Confirmation Dialog
    cancellingBooking?.let { b ->
        AlertDialog(
            onDismissRequest = { cancellingBooking = null },
            title = { Text("تأكيد الإلغاء الداخلي") },
            text = {
                Text("هل تريد إلغاء تذكرة ${b.ticketCode} في ${b.venueName}؟ سيتم حفظها في السجل لتتمكن من إعادة الحجز لاحقاً.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelBooking(b.id)
                        cancellingBooking = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUrgent)
                ) {
                    Text("نعم، إلغاء التذكرة")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancellingBooking = null }) {
                    Text("تراجع")
                }
            }
        )
    }
}

@Composable
private fun HistoryBookingCard(
    booking: BookingEntity,
    reviewsEnabled: Boolean,
    onQuickRebook: () -> Unit,
    onRateClick: () -> Unit
) {
    val isCompleted = booking.status == BookingStatus.COMPLETED.name
    val badgeColor = if (isCompleted) StatusOpen else StatusUrgent
    val badgeText = if (isCompleted) "مكتمل" else "ملغى"

    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_booking_card_${booking.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.venueName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${booking.ticketCode} • ${booking.selectedOption}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (booking.handledByProvider.isNotBlank()) {
                        Text(
                            text = "بواسطة: ${booking.handledByProvider}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (booking.approvalNote.isNotBlank()) {
                        Text(
                            text = booking.approvalNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            val rating = booking.userRating
            if (reviewsEnabled && rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(rating) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = booking.userReview ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onQuickRebook,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_rebook_button_${booking.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حجز جديد لنفس المكان")
                }

                if (reviewsEnabled && isCompleted && booking.userRating == null) {
                    OutlinedButton(
                        onClick = onRateClick,
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قيّم الخدمة")
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var selectedStars by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (star in 1..5) {
                        IconButton(onClick = { selectedStars = star }) {
                            Icon(
                                imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star نجوم",
                                tint = if (star <= selectedStars) AmberGold else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("اكتب تعليقك (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStars, comment) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
