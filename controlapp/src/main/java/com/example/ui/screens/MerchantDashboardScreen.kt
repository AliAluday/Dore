package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookingEntity
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.QueueItemStatus
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.DoriUiState
import com.example.util.IntentHelpers

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MerchantDashboardScreen(
    state: DoriUiState,
    onSelectVenue: (Int) -> Unit,
    onSelectProvider: (Long) -> Unit,
    onAcceptTicket: (Long, String) -> Unit,
    onRejectTicket: (Long, String) -> Unit,
    onSyncBridge: () -> Unit,
    onCallNext: (Int) -> Unit,
    onSkipCurrent: (Int) -> Unit,
    onCallCustomerByName: (Int, Long, String) -> Unit,
    onMarkNoShow: (Int, Long, String) -> Unit,
    onTogglePauseQueue: (Int) -> Unit,
    onToggleAcceptingBookings: (Int) -> Unit,
    onAddWalkIn: (Int, String, String) -> Unit,
    onToggleLiveSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cityAndAllVenues = remember(state.allVenues, state.preferences.selectedDistrict) {
        val districtName = state.preferences.selectedDistrict.displayName
        val inDistrict = state.allVenues.filter { it.city == districtName }
        val others = state.allVenues.filter { it.city != districtName }
        inDistrict + others
    }
    val selectedVenue = cityAndAllVenues.find { it.id == state.selectedMerchantVenueId }
        ?: cityAndAllVenues.firstOrNull()

    var showWalkInDialog by remember { mutableStateOf(false) }
    var venueMenuExpanded by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var rejectingBooking by remember { mutableStateOf<BookingEntity?>(null) }

    val activeProvider = state.activeProvider
    val activeProviders = remember(state.providers) { state.providers.filter { it.isActive } }
    val canCallNext = activeProvider?.canCallNext ?: true
    val canPauseVenue = activeProvider?.canPauseVenue ?: true

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (selectedVenue == null) {
            Text("لا توجد مراكز مسجلة")
            return
        }

        val venueQueue = state.queueItemsByVenue[selectedVenue.id].orEmpty()
        val servingItem = venueQueue.firstOrNull { it.status == QueueItemStatus.SERVING.name }
        val waitingItems = venueQueue.filter { it.status == QueueItemStatus.WAITING.name }
        val finishedItems = venueQueue.filter {
            it.status == QueueItemStatus.DONE.name ||
                it.status == QueueItemStatus.SKIPPED.name ||
                it.status == QueueItemStatus.NO_SHOW.name
        }.reversed()

        val venueBookings = remember(state.activeBookings, selectedVenue.id) {
            state.activeBookings.filter { it.venueId == selectedVenue.id }
        }
        val pendingCount = venueBookings.count { it.approvalStatus == BookingApprovalStatus.PENDING.name }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active Operator / Provider Switcher Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (activeProvider != null) IndigoAccentLight.copy(alpha = 0.55f)
                        else EmeraldPrimary.copy(alpha = 0.38f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeProvider != null) {
                            IndigoAccentLight.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable { providerMenuExpanded = true }
                                        .testTag("merchant_provider_switcher")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 9.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (activeProvider != null) IndigoAccentLight.copy(alpha = 0.18f)
                                                        else EmeraldPrimary.copy(alpha = 0.16f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (activeProvider != null) Icons.Default.Badge else Icons.Default.AdminPanelSettings,
                                                    contentDescription = null,
                                                    tint = if (activeProvider != null) IndigoAccentLight else EmeraldPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = activeProvider?.let { "${it.name} • ${it.customTitle}" }
                                                        ?: "وضع الأدمن الكامل (${state.activeAdmin?.name ?: "الإدارة"})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = activeProvider?.let {
                                                        "مرتبط بـ ${it.venueName} • قَبِل ${it.acceptedCount} تذكرة"
                                                    } ?: "تبديل إلى طبيب / صاحب ملعب / تاجر",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "تبديل المشغّل",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = providerMenuExpanded,
                                    onDismissRequest = { providerMenuExpanded = false },
                                    modifier = Modifier.heightIn(max = 320.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "وضع الأدمن الكامل",
                                                    fontWeight = if (activeProvider == null) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (activeProvider == null) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "إدارة جميع المراكز والطوابير بحرية",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectProvider(0L)
                                            providerMenuExpanded = false
                                        }
                                    )
                                    HorizontalDivider()
                                    activeProviders.forEach { provider ->
                                        val isCurrent = activeProvider?.id == provider.id
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = "${provider.name} (${provider.customTitle})",
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isCurrent) IndigoAccentLight else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = provider.venueName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onSelectProvider(provider.id)
                                                providerMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = onSyncBridge,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("merchant_sync_bridge_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "مزامنة التذاكر", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("مزامنة", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (activeProvider != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ProviderCapTag("قبول التذاكر", activeProvider.canAcceptTickets)
                                ProviderCapTag("استدعاء الدور", activeProvider.canCallNext)
                                ProviderCapTag("إيقاف المركز", activeProvider.canPauseVenue)
                            }
                            val restricted = listOfNotNull(
                                if (!activeProvider.canAcceptTickets) "قبول التذاكر" else null,
                                if (!activeProvider.canCallNext) "استدعاء الدور" else null,
                                if (!activeProvider.canPauseVenue) "إيقاف المركز" else null
                            )
                            if (restricted.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = StatusUrgent.copy(alpha = 0.09f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = StatusUrgent, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "صلاحيات مقيدة لهذا الحساب: ${restricted.joinToString("، ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusUrgent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Venue Selector Dropdown
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = activeProvider == null) { venueMenuExpanded = true }
                            .testTag("merchant_venue_dropdown")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = selectedVenue.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (activeProvider != null) {
                                            "${selectedVenue.city} • مثبّت حسب صلاحية ${activeProvider.customTitle}"
                                        } else {
                                            "${selectedVenue.city} • ${selectedVenue.area}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (activeProvider == null) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "تبديل المركز",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                TextButton(onClick = { onSelectProvider(0L) }) {
                                    Text("تحرير", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = venueMenuExpanded,
                        onDismissRequest = { venueMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        cityAndAllVenues.forEach { venue ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = venue.name,
                                            fontWeight = if (venue.id == selectedVenue.id) FontWeight.Bold else FontWeight.Normal,
                                            color = if (venue.id == selectedVenue.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${venue.city} • ${venue.area}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectVenue(venue.id)
                                    venueMenuExpanded = false
                                },
                                modifier = Modifier.testTag("merchant_venue_chip_${venue.id}")
                            )
                        }
                    }
                }
            }

            // Instant Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MerchantStatCard(
                        label = "بانتظار القبول",
                        value = "$pendingCount",
                        icon = Icons.Default.HourglassTop,
                        tint = AmberAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MerchantStatCard(
                        label = "بالانتظار",
                        value = "${waitingItems.size}",
                        icon = Icons.Default.Group,
                        tint = IndigoAccentLight,
                        modifier = Modifier.weight(1f)
                    )
                    MerchantStatCard(
                        label = "الرقم الحالي",
                        value = if (selectedVenue.currentServingNumber <= 0) "—" else "#${selectedVenue.currentServingNumber}",
                        icon = Icons.Default.ConfirmationNumber,
                        tint = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Incoming Bookings & Ticket Acceptance Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "التذاكر الواردة وقبول الطلبات (${venueBookings.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (pendingCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = AmberGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "$pendingCount جديد",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (venueBookings.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لا توجد تذاكر محجوزة حالياً لهذا المركز — اضغط (مزامنة) لسحب الحجوزات الجديدة من نسخة المستخدم.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(venueBookings, key = { "bk_${it.id}" }) { booking ->
                    IncomingTicketApprovalCard(
                        booking = booking,
                        canAct = activeProvider?.canAcceptTickets ?: true,
                        onAccept = { onAcceptTicket(booking.id, "تم القبول وتأكيد الموعد") },
                        onReject = { rejectingBooking = booking }
                    )
                }
            }

            // Live Simulation Toggle Row
            item {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = if (state.isLiveSimulationRunning) AmberGold.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (state.isLiveSimulationRunning) AmberGold.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 11.dp, vertical = 6.dp),
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
                                tint = if (state.isLiveSimulationRunning) AmberAccent else EmeraldPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "محاكاة التنبيهات الحية",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = state.isLiveSimulationRunning,
                            onCheckedChange = { onToggleLiveSimulation() },
                            modifier = Modifier.testTag("live_simulation_toggle")
                        )
                    }
                }
            }

            // Current Serving & Call Controls
            item {
                Card(
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "العميل قيد الخدمة الآن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = servingItem?.let { "${it.customerName} (${it.ticketCode})" } ?: "لا يوجد عميل حالياً",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                if (servingItem != null) {
                                    Text(
                                        text = servingItem.selectedOption,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showWalkInDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .alpha(if (canCallNext) 1f else 0.55f)
                                    .testTag("add_walkin_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حضوري", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onCallNext(selectedVenue.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(38.dp)
                                    .alpha(if (canCallNext) 1f else 0.55f)
                                    .testTag("call_next_button")
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اتصل بالتالي", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { onSkipCurrent(selectedVenue.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(38.dp)
                                    .alpha(if (canCallNext) 1f else 0.55f)
                                    .testTag("skip_customer_button")
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("تخطي", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (canPauseVenue) 1f else 0.55f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("إيقاف الطابور مؤقتاً", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = selectedVenue.isPaused,
                                onCheckedChange = { onTogglePauseQueue(selectedVenue.id) },
                                modifier = Modifier.testTag("pause_queue_switch")
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (canPauseVenue) 1f else 0.55f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("استقبال طلبات الحجز الجديدة", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = selectedVenue.isAcceptingBookings,
                                onCheckedChange = { onToggleAcceptingBookings(selectedVenue.id) },
                                modifier = Modifier.testTag("accept_bookings_switch")
                            )
                        }
                    }
                }
            }

            // Waiting Queue List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "طابور الانتظار (${waitingItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الانتظار المقدر: ${waitingItems.size * selectedVenue.avgMinutesPerPerson} د",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (waitingItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "الطابور فارغ حالياً — أضف عميل حضوري أو اسحب الحجوزات بالمزامنة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(waitingItems, key = { it.id }) { item ->
                    QueueWaitingRow(
                        item = item,
                        canAct = canCallNext,
                        onCallByName = { onCallCustomerByName(selectedVenue.id, item.id, item.customerName) },
                        onMarkNoShow = { onMarkNoShow(selectedVenue.id, item.id, item.customerName) }
                    )
                }
            }

            if (finishedItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المنتهية اليوم (${selectedVenue.servedTodayCount})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(finishedItems.take(5), key = { it.id }) { item ->
                    val statusEnum = QueueItemStatus.entries.find { it.name == item.status } ?: QueueItemStatus.DONE
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.customerName} (${item.ticketCode})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = statusEnum.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (statusEnum) {
                                    QueueItemStatus.DONE -> StatusOpen
                                    QueueItemStatus.NO_SHOW -> StatusUrgent
                                    else -> StatusPaused
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWalkInDialog && selectedVenue != null) {
        var walkInName by remember { mutableStateOf("") }
        var walkInOption by remember {
            mutableStateOf(selectedVenue.customOptions.firstOrNull() ?: "خدمة عامة")
        }
        AlertDialog(
            onDismissRequest = { showWalkInDialog = false },
            title = { Text("إضافة عميل حضوري") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = walkInName,
                        onValueChange = { walkInName = it },
                        label = { Text("اسم العميل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = walkInOption,
                        onValueChange = { walkInOption = it },
                        label = { Text("الخدمة المطلوبة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddWalkIn(selectedVenue.id, walkInName, walkInOption)
                        showWalkInDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWalkInDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    rejectingBooking?.let { target ->
        var rejectNote by remember { mutableStateOf("الجدول ممتلئ حالياً — يرجى الحجز في وقت لاحق") }
        AlertDialog(
            onDismissRequest = { rejectingBooking = null },
            title = { Text("الاعتذار عن التذكرة ${target.ticketCode}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "العميل: ${target.customerName} (${target.selectedOption})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rejectNote,
                        onValueChange = { rejectNote = it },
                        label = { Text("سبب الاعتذار (يصل للمستخدم)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectTicket(target.id, rejectNote)
                        rejectingBooking = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUrgent)
                ) {
                    Text("تأكيد الرفض")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingBooking = null }) {
                    Text("تراجع")
                }
            }
        )
    }
}

@Composable
private fun ProviderCapTag(label: String, allowed: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (allowed) StatusOpen.copy(alpha = 0.14f) else StatusUrgent.copy(alpha = 0.12f)
    ) {
        Text(
            text = if (allowed) "✓ $label" else "✕ $label",
            style = MaterialTheme.typography.labelSmall,
            color = if (allowed) StatusOpen else StatusUrgent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun IncomingTicketApprovalCard(
    booking: BookingEntity,
    canAct: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val approval = BookingApprovalStatus.entries.find { it.name == booking.approvalStatus }
        ?: BookingApprovalStatus.PENDING
    val badgeColor = when (approval) {
        BookingApprovalStatus.PENDING -> AmberAccent
        BookingApprovalStatus.ACCEPTED -> StatusOpen
        BookingApprovalStatus.REJECTED -> StatusUrgent
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (approval == BookingApprovalStatus.PENDING) AmberGold.copy(alpha = 0.55f)
            else StatusOpen.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("incoming_ticket_card_${booking.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${booking.customerName} • #${booking.queueNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${booking.ticketCode} • ${booking.selectedOption} • ${IntentHelpers.formatDinar(booking.priceDinar)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (booking.customerPhone.isNotBlank()) {
                        Text(
                            text = "هاتف: ${booking.customerPhone}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (booking.notes.isNotBlank() || booking.optionalInfo.isNotBlank()) {
                        Text(
                            text = listOf(booking.notes, booking.optionalInfo)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = approval.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            if (approval == BookingApprovalStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAct) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canAct) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(35.dp)
                            .testTag("accept_ticket_${booking.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قبول التذكرة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUrgent),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(35.dp)
                            .testTag("reject_ticket_${booking.id}")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("رفض", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusOpen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buildString {
                            append("✓ تم التعامل مع الطلب")
                            if (booking.handledByProvider.isNotBlank()) append(" بواسطة ${booking.handledByProvider}")
                            if (booking.approvalNote.isNotBlank()) append(" — ${booking.approvalNote}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusOpen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueWaitingRow(
    item: MerchantQueueItemEntity,
    canAct: Boolean,
    onCallByName: () -> Unit,
    onMarkNoShow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.customerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.ticketCode} • ${item.selectedOption}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "#${item.ticketNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onCallByName,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .alpha(if (canAct) 1f else 0.55f)
                        .testTag("call_by_name_${item.id}")
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("نداء بالاسم", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onMarkNoShow,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUrgent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .alpha(if (canAct) 1f else 0.55f)
                        .testTag("no_show_${item.id}")
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("توقف عن الحضور", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
