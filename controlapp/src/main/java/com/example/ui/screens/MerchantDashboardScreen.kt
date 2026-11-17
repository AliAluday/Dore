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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.model.QueueItemStatus
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.DoriUiState

@Composable
fun MerchantDashboardScreen(
    state: DoriUiState,
    onSelectVenue: (Int) -> Unit,
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (selectedVenue == null) {
            Text("لا توجد محلات مسجلة")
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact Venue Selector Dropdown (replaces 35 scrolling chips)
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { venueMenuExpanded = true }
                            .testTag("merchant_venue_dropdown")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = selectedVenue.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${selectedVenue.city} • ${selectedVenue.area}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "تبديل المحل",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

            // Instant Stats Cards: Waiting, Served Today, Current Number
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MerchantStatCard(
                        label = "بالانتظار",
                        value = "${waitingItems.size}",
                        icon = Icons.Default.Group,
                        tint = AmberAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MerchantStatCard(
                        label = "خُدموا اليوم",
                        value = "${selectedVenue.servedTodayCount}",
                        icon = Icons.Default.CheckCircle,
                        tint = StatusOpen,
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

            // Compact Live Simulation Toggle Row
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                                tint = if (state.isLiveSimulationRunning) AmberAccent else EmeraldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "محاكاة التنبيهات الحية",
                                style = MaterialTheme.typography.titleSmall,
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
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "العميل قيد الخدمة الآن",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = servingItem?.let { "${it.customerName} (${it.ticketCode})" } ?: "لا يوجد عميل حالياً",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                if (servingItem != null) {
                                    Text(
                                        text = servingItem.selectedOption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showWalkInDialog = true },
                                modifier = Modifier.testTag("add_walkin_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حضوري")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onCallNext(selectedVenue.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("call_next_button")
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اتصل بالتالي")
                            }

                            OutlinedButton(
                                onClick = { onSkipCurrent(selectedVenue.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("skip_customer_button")
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تخطي")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "بالانتظار (${waitingItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (waitingItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "الطابور فارغ حالياً — أضف عميل حضوري أو احجز تذكرة من الاستكشاف.",
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
                        onCallByName = { onCallCustomerByName(selectedVenue.id, item.id, item.customerName) },
                        onMarkNoShow = { onMarkNoShow(selectedVenue.id, item.id, item.customerName) }
                    )
                }
            }

            if (finishedItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المنتهية اليوم",
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
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QueueWaitingRow(
    item: MerchantQueueItemEntity,
    onCallByName: () -> Unit,
    onMarkNoShow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.customerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.ticketCode} • ${item.selectedOption}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "#${item.ticketNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCallByName,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("call_by_name_${item.id}")
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نداء بالاسم", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onMarkNoShow,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUrgent),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("no_show_${item.id}")
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("توقف عن الحضور", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
