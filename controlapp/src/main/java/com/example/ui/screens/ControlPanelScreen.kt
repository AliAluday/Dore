package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.VenueEntity
import com.example.data.model.AdminPermissionType
import com.example.data.model.MedicalSpecialty
import com.example.data.model.NinevehDistrict
import com.example.data.model.ServiceCategory
import com.example.data.model.UserAppControlConfig
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DoriUiState
import com.example.util.IntentHelpers

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlPanelScreen(
    state: DoriUiState,
    activeTab: AppTab,
    onUpdateControlConfig: ((UserAppControlConfig) -> UserAppControlConfig) -> Unit,
    onToggleCategoryEnabled: (ServiceCategory) -> Unit,
    onSetBroadcastMessage: (String) -> Unit,
    onAddCustomVenue: (
        name: String,
        district: NinevehDistrict,
        area: String,
        category: ServiceCategory,
        specialty: String,
        priceDinar: Int,
        avgMinutes: Int,
        phone: String
    ) -> Unit,
    onToggleVenueOpen: (Int) -> Unit,
    onAdjustVenuePrice: (Int, Int) -> Unit,
    onAdjustVenueWait: (Int, Int) -> Unit,
    onDeleteVenue: (Int) -> Unit,
    onSetDistrictVenuesOpen: (Boolean) -> Unit,
    onAdvanceAllQueues: () -> Unit,
    onCompleteAllActive: () -> Unit,
    onCancelAllActive: () -> Unit,
    onResetAllQueues: () -> Unit,
    onAdjustLoyaltyPoints: (Int) -> Unit,
    onResetLoyaltyPoints: () -> Unit,
    onFactoryReset: () -> Unit,
    onSyncBridge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctrl = state.preferences.controlConfig
    var broadcastInput by remember(ctrl.broadcastMessage) { mutableStateOf(ctrl.broadcastMessage) }
    var showAddVenueDialog by remember { mutableStateOf(false) }
    var showFactoryResetConfirm by remember { mutableStateOf(false) }
    var pendingBatchAction by remember { mutableStateOf<BatchCommandAction?>(null) }
    var lastExecutedCommand by remember { mutableStateOf<String?>(null) }
    var deletingVenue by remember { mutableStateOf<VenueEntity?>(null) }
    var featuresSubTab by remember { mutableIntStateOf(0) }
    var venueSearchQuery by remember { mutableStateOf("") }
    var venueFilterCategory by remember { mutableStateOf(ServiceCategory.ALL) }
    val haptic = LocalHapticFeedback.current

    val activeAdmin = state.activeAdmin
    val canManageFeatures = activeAdmin?.hasPermission(AdminPermissionType.MANAGE_FEATURES) ?: true
    val canManageVenues = activeAdmin?.hasPermission(AdminPermissionType.MANAGE_VENUES) ?: true
    val canRunCommands = activeAdmin?.hasPermission(AdminPermissionType.RUN_COMMANDS) ?: true
    val providersByVenue = remember(state.providers) {
        state.providers.filter { it.isActive }.groupBy { it.venueId }
    }
    val filteredControlVenues = remember(state.districtVenues, venueSearchQuery, venueFilterCategory) {
        val q = venueSearchQuery.trim()
        state.districtVenues.filter { venue ->
            val catMatch = venueFilterCategory == ServiceCategory.ALL ||
                venue.category == venueFilterCategory.displayName
            val queryMatch = q.isEmpty() ||
                venue.name.contains(q, ignoreCase = true) ||
                venue.area.contains(q, ignoreCase = true) ||
                venue.city.contains(q, ignoreCase = true)
            catMatch && queryMatch
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            // Compact Admin Status Strip
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0A1428), Color(0xFF064E3B), Color(0xFF1E1B4B))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "دوري تحكم • ${activeAdmin?.name ?: "الإدارة العامة"}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeAdmin?.roleTitle ?: "مدير عام"} • ${state.allVenues.size} مركز • ${state.pendingApprovalBookings.size} بانتظار القبول",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (ctrl.allowNewBookings) StatusOpen.copy(alpha = 0.22f)
                                else StatusUrgent.copy(alpha = 0.28f)
                            ) {
                                Text(
                                    text = if (ctrl.allowNewBookings) "مفعّل" else "متوقف",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ctrl.allowNewBookings) MintPrimaryDark else Color(0xFFFDA4AF),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            when (activeTab) {
                AppTab.CONTROL_FEATURES -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Local Trial & Cross-App Sync Card
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "وضع تجريبي محلي • الجسر مفعّل",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "مزامنة الدعم والتذاكر مباشرة مع نسخة المستخدم",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = onSyncBridge,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("ctrl_sync_bridge_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("مزامنة", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Segmented Sub-tabs: Features vs Quick Commands
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                TabRow(
                                    selectedTabIndex = featuresSubTab,
                                    containerColor = Color.Transparent,
                                    divider = {}
                                ) {
                                    Tab(
                                        selected = featuresSubTab == 0,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            featuresSubTab = 0
                                        },
                                        text = { Text("المميزات والإعلانات", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.testTag("ctrl_subtab_features")
                                    )
                                    Tab(
                                        selected = featuresSubTab == 1,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            featuresSubTab = 1
                                        },
                                        text = { Text("الأوامر السريعة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.testTag("ctrl_subtab_commands")
                                    )
                                }
                            }
                        }

                        if (featuresSubTab == 0) {
                            if (!canManageFeatures) {
                                item {
                                    PermissionLockedBanner(
                                        permissionName = AdminPermissionType.MANAGE_FEATURES.label,
                                        adminName = activeAdmin?.name ?: ""
                                    )
                                }
                            }

                        // Broadcast Message Card
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Campaign, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "رسالة تنبيه / إعلان عام للمستخدمين",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = broadcastInput,
                                            onValueChange = { broadcastInput = it },
                                            placeholder = { Text("اكتب إعلاناً يظهر في أعلى الاستكشاف...", style = MaterialTheme.typography.bodySmall) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            trailingIcon = {
                                                if (broadcastInput.isNotEmpty()) {
                                                    IconButton(onClick = {
                                                        broadcastInput = ""
                                                        onSetBroadcastMessage("")
                                                    }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(17.dp))
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("control_broadcast_input")
                                        )
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSetBroadcastMessage(broadcastInput)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(46.dp)
                                                .testTag("control_broadcast_send")
                                        ) {
                                            Text("نشر", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }

                        // Core Feature Switches
                        item {
                            Card(
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.alpha(if (canManageFeatures) 1f else 0.65f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "التحكم بمميزات نسخة المستخدم",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    ControlSwitchRow(
                                        icon = Icons.Default.ConfirmationNumber,
                                        title = "استقبال الحجوزات الجديدة",
                                        subtitle = "إيقاف أو تشغيل الحجز في كامل التطبيق",
                                        checked = ctrl.allowNewBookings,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(allowNewBookings = checked) }
                                        },
                                        tag = "ctrl_switch_allow_bookings"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.FastForward,
                                        title = "زر أسرع دور (⚡)",
                                        subtitle = "إظهار زر الحجز الأسرع في الصفحة الرئيسية",
                                        checked = ctrl.enableFastestTurn,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableFastestTurn = checked) }
                                        },
                                        tag = "ctrl_switch_fastest_turn"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.QrCode2,
                                        title = "إصدار رمز QR للتذاكر",
                                        subtitle = "عرض وتوليد رمز QR الحقيقي في التذاكر",
                                        checked = ctrl.enableQrCode,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableQrCode = checked) }
                                        },
                                        tag = "ctrl_switch_qr"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.Share,
                                        title = "مشاركة التذكرة عبر واتساب",
                                        subtitle = "تفعيل زر إرسال التذكرة على واتساب",
                                        checked = ctrl.enableWhatsAppShare,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableWhatsAppShare = checked) }
                                        },
                                        tag = "ctrl_switch_whatsapp"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.Star,
                                        title = "التقييمات وآراء الزبائن",
                                        subtitle = "إظهار المراجعات وطلب تقييم الخدمة",
                                        checked = ctrl.enableReviews,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableReviews = checked) }
                                        },
                                        tag = "ctrl_switch_reviews"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.WorkspacePremium,
                                        title = "نظام نقاط الولاء",
                                        subtitle = "منح نقاط ولاء تلقائياً عند إتمام الدور",
                                        checked = ctrl.enableLoyalty,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableLoyalty = checked) }
                                        },
                                        tag = "ctrl_switch_loyalty"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.Tune,
                                        title = "الحقول الاختيارية بالحجز",
                                        subtitle = "إظهار حقلي العمر والملاحظة الإضافية",
                                        checked = ctrl.enableOptionalFields,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableOptionalFields = checked) }
                                        },
                                        tag = "ctrl_switch_optional_fields"
                                    )
                                    ControlSwitchRow(
                                        icon = Icons.Default.NotificationsActive,
                                        title = "شريط وإشعارات الدور الحية",
                                        subtitle = "تفعيل التنبيهات المنبثقة والصوت والاهتزاز",
                                        checked = ctrl.enableLiveAlerts,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateControlConfig { it.copy(enableLiveAlerts = checked) }
                                        },
                                        tag = "ctrl_switch_live_alerts"
                                    )

                                    if (ctrl.enableLoyalty) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        Text(
                                            text = "نقاط الولاء لكل تذكرة مكتملة:",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(5, 10, 20, 30).forEach { pts ->
                                                FilterChip(
                                                    selected = ctrl.pointsPerBooking == pts,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onUpdateControlConfig { it.copy(pointsPerBooking = pts) }
                                                    },
                                                    label = { Text("+$pts نقطة") },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    modifier = Modifier.testTag("ctrl_points_chip_$pts")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Categories Visibility Control
                        item {
                            Card(
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "الأقسام الظاهرة في الاستكشاف",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ServiceCategory.fiveCategories.forEach { category ->
                                            val enabled = category.displayName !in ctrl.disabledCategories
                                            FilterChip(
                                                selected = enabled,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onToggleCategoryEnabled(category)
                                                },
                                                label = { Text(category.displayName) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = StatusOpen.copy(alpha = 0.18f),
                                                    selectedLabelColor = StatusOpen,
                                                    selectedLeadingIconColor = StatusOpen
                                                ),
                                                modifier = Modifier.testTag("ctrl_category_${category.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        } else {
                            if (!canRunCommands) {
                                item {
                                    PermissionLockedBanner(
                                        permissionName = AdminPermissionType.RUN_COMMANDS.label,
                                        adminName = activeAdmin?.name ?: ""
                                    )
                                }
                            }
                            item {
                                AnimatedVisibility(visible = lastExecutedCommand != null) {
                                    lastExecutedCommand?.let { bannerText ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = StatusOpen.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, StatusOpen.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ctrl_last_command_banner")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = StatusOpen,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = bannerText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = StatusOpen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { lastExecutedCommand = null },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "إخفاء",
                                                        tint = StatusOpen,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                CommandActionCard(
                                    title = "تقديم جميع الأدوار خطوة",
                                    subtitle = "تحريك كل التذاكر النشطة في الطوابير وتنبيه المستخدمين",
                                    buttonLabel = "تنفيذ الآن",
                                    icon = Icons.Default.FastForward,
                                    accent = EmeraldPrimary,
                                    testTag = "ctrl_cmd_advance_all",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAdvanceAllQueues()
                                        if (canRunCommands) {
                                            lastExecutedCommand = "آخر إجراء مُنفَّذ: تقديم جميع الأدوار خطوة ✓"
                                        }
                                    }
                                )
                            }
                            item {
                                CommandActionCard(
                                    title = "إتمام كل التذاكر النشطة (${state.activeBookings.size})",
                                    subtitle = "إنهاء جميع التذاكر الجارية ومنح نقاط الولاء",
                                    buttonLabel = "إتمام الكل",
                                    icon = Icons.Default.CheckCircle,
                                    accent = StatusOpen,
                                    testTag = "ctrl_cmd_complete_all",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (canRunCommands) {
                                            pendingBatchAction = BatchCommandAction.COMPLETE_ALL
                                        } else {
                                            onCompleteAllActive()
                                        }
                                    }
                                )
                            }
                            item {
                                CommandActionCard(
                                    title = "إلغاء كل التذاكر النشطة",
                                    subtitle = "نقل التذاكر النشطة إلى السجل وتفريغ الدور للمستخدم",
                                    buttonLabel = "إلغاء الكل",
                                    icon = Icons.Default.Cancel,
                                    accent = AmberAccent,
                                    testTag = "ctrl_cmd_cancel_all",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (canRunCommands) {
                                            pendingBatchAction = BatchCommandAction.CANCEL_ALL
                                        } else {
                                            onCancelAllActive()
                                        }
                                    }
                                )
                            }
                            item {
                                CommandActionCard(
                                    title = "تصفير الطوابير والبدء من #1",
                                    subtitle = "إعادة ترقيم جميع المراكز لتبدأ من التذكرة رقم 1",
                                    buttonLabel = "تصفير #1",
                                    icon = Icons.Default.LockOpen,
                                    accent = MaterialTheme.colorScheme.secondary,
                                    testTag = "ctrl_cmd_reset_queues",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (canRunCommands) {
                                            pendingBatchAction = BatchCommandAction.RESET_QUEUES
                                        } else {
                                            onResetAllQueues()
                                        }
                                    }
                                )
                            }
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "نقاط ولاء المستخدم (${state.preferences.loyaltyPoints})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "منح مكافأة فورية أو تصفير الرصيد",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onAdjustLoyaltyPoints(50)
                                                    if (canRunCommands) {
                                                        lastExecutedCommand = "آخر إجراء مُنفَّذ: إضافة +50 نقطة ولاء ✓"
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color(0xFF1F1600)),
                                                modifier = Modifier.testTag("ctrl_cmd_add_50_points")
                                            ) {
                                                Text("+50", fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (canRunCommands) {
                                                        pendingBatchAction = BatchCommandAction.RESET_POINTS
                                                    } else {
                                                        onResetLoyaltyPoints()
                                                    }
                                                }
                                            ) {
                                                Text("تصفير")
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                CommandActionCard(
                                    title = "إعادة ضبط المصنع بالكامل",
                                    subtitle = "استعادة المراكز الأولية والطاقم وإعدادات نسخة المستخدم الافتراضية",
                                    buttonLabel = "إعادة الضبط",
                                    icon = Icons.Default.RestartAlt,
                                    accent = StatusUrgent,
                                    testTag = "ctrl_cmd_factory_reset",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (canRunCommands) {
                                            showFactoryResetConfirm = true
                                        } else {
                                            onFactoryReset()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                AppTab.CONTROL_VENUES -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (!canManageVenues) {
                            item {
                                PermissionLockedBanner(
                                    permissionName = AdminPermissionType.MANAGE_VENUES.label,
                                    adminName = activeAdmin?.name ?: ""
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { showAddVenueDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(36.dp)
                                        .testTag("ctrl_add_venue_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("إضافة مركز", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { onSetDistrictVenuesOpen(true) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(36.dp)
                                ) {
                                    Text("فتح الكل", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onSetDistrictVenuesOpen(false) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUrgent),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(36.dp)
                                ) {
                                    Text("إغلاق الكل", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = venueSearchQuery,
                                    onValueChange = { venueSearchQuery = it },
                                    placeholder = { Text("ابحث باسم المركز أو المنطقة...", style = MaterialTheme.typography.bodySmall) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (venueSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { venueSearchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(ServiceCategory.entries) { cat ->
                                        FilterChip(
                                            selected = venueFilterCategory == cat,
                                            onClick = { venueFilterCategory = cat },
                                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                Text(
                                    text = "المراكز المعروضة (${filteredControlVenues.size} من ${state.districtVenues.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        items(filteredControlVenues, key = { it.id }) { venue ->
                            ControlVenueCard(
                                venue = venue,
                                linkedProviders = providersByVenue[venue.id].orEmpty(),
                                canManageVenues = canManageVenues,
                                onToggleOpen = { onToggleVenueOpen(venue.id) },
                                onAdjustPrice = { delta -> onAdjustVenuePrice(venue.id, delta) },
                                onAdjustWait = { delta -> onAdjustVenueWait(venue.id, delta) },
                                onDelete = {
                                    if (canManageVenues) {
                                        deletingVenue = venue
                                    } else {
                                        onDeleteVenue(venue.id)
                                    }
                                }
                            )
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    if (showAddVenueDialog) {
        AddCustomVenueDialog(
            initialDistrict = if (state.preferences.selectedDistrict == NinevehDistrict.ALL_NINEVEH) {
                NinevehDistrict.MOSUL_LEFT
            } else {
                state.preferences.selectedDistrict
            },
            onDismiss = { showAddVenueDialog = false },
            onConfirm = { name, district, area, category, specialty, price, avgMin, phone ->
                onAddCustomVenue(name, district, area, category, specialty, price, avgMin, phone)
                showAddVenueDialog = false
            }
        )
    }

    if (showFactoryResetConfirm) {
        AlertDialog(
            onDismissRequest = { showFactoryResetConfirm = false },
            title = { Text("تأكيد إعادة ضبط المصنع") },
            text = {
                Text("سيتم استعادة جميع المراكز الافتراضية، مسح التذاكر، وإعادة جميع مفاتيح لوحة التحكم إلى الوضع الافتراضي.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFactoryReset()
                        showFactoryResetConfirm = false
                        lastExecutedCommand = "آخر إجراء مُنفَّذ: إعادة ضبط المصنع بالكامل ✓"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUrgent)
                ) {
                    Text("نعم، إعادة الضبط")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetConfirm = false }) {
                    Text("تراجع")
                }
            }
        )
    }

    pendingBatchAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingBatchAction = null },
            title = { Text(action.dialogTitle) },
            text = { Text(action.dialogBody) },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (action) {
                            BatchCommandAction.COMPLETE_ALL -> onCompleteAllActive()
                            BatchCommandAction.CANCEL_ALL -> onCancelAllActive()
                            BatchCommandAction.RESET_QUEUES -> onResetAllQueues()
                            BatchCommandAction.RESET_POINTS -> onResetLoyaltyPoints()
                        }
                        lastExecutedCommand = "آخر إجراء مُنفَّذ: ${action.successBanner} ✓"
                        pendingBatchAction = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == BatchCommandAction.COMPLETE_ALL) StatusOpen else StatusUrgent
                    )
                ) {
                    Text(action.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchAction = null }) {
                    Text("تراجع")
                }
            }
        )
    }

    deletingVenue?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingVenue = null },
            title = { Text("تأكيد حذف المركز") },
            text = {
                Text("هل أنت متأكد من حذف «${target.name}»؟ سيتم إلغاء أي حجوزات نشطة مرتبطة به وتعطيل حسابات المزودين التابعة له.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVenue(target.id)
                        deletingVenue = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUrgent)
                ) {
                    Text("حذف المركز")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVenue = null }) {
                    Text("تراجع")
                }
            }
        )
    }
}

private enum class BatchCommandAction(
    val dialogTitle: String,
    val dialogBody: String,
    val confirmLabel: String,
    val successBanner: String
) {
    COMPLETE_ALL(
        dialogTitle = "تأكيد إتمام كل التذاكر النشطة",
        dialogBody = "سيتم إنهاء جميع التذاكر النشطة حالياً ومنح نقاط الولاء (إن كانت مفعّلة).",
        confirmLabel = "إتمام الكل",
        successBanner = "إتمام جميع التذاكر النشطة"
    ),
    CANCEL_ALL(
        dialogTitle = "تأكيد إلغاء كل التذاكر النشطة",
        dialogBody = "سيتم نقل جميع التذاكر النشطة إلى السجل كملغاة وتفريغ الأدوار.",
        confirmLabel = "إلغاء الكل",
        successBanner = "إلغاء جميع التذاكر النشطة"
    ),
    RESET_QUEUES(
        dialogTitle = "تأكيد تصفير جميع الطوابير",
        dialogBody = "ستتم إعادة ترقيم جميع المراكز والبدء من التذكرة #1.",
        confirmLabel = "تصفير الطوابير",
        successBanner = "تصفير جميع الطوابير والبدء من #1"
    ),
    RESET_POINTS(
        dialogTitle = "تأكيد تصفير نقاط الولاء",
        dialogBody = "سيتم تصفير رصيد نقاط الولاء للمستخدم إلى 0.",
        confirmLabel = "تصفير الرصيد",
        successBanner = "تصفير رصيد نقاط الولاء"
    )
}

@Composable
private fun ControlSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun PermissionLockedBanner(
    permissionName: String,
    adminName: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StatusUrgent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, StatusUrgent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = StatusUrgent, modifier = Modifier.size(18.dp))
            Text(
                text = "الحساب الحالي ($adminName) لا يملك صلاحية ($permissionName) — التعديلات محمية.",
                style = MaterialTheme.typography.labelMedium,
                color = StatusUrgent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ControlVenueCard(
    venue: VenueEntity,
    linkedProviders: List<ServiceProviderEntity>,
    canManageVenues: Boolean,
    onToggleOpen: () -> Unit,
    onAdjustPrice: (Int) -> Unit,
    onAdjustWait: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (venue.isOpen) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            else StatusUrgent.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (canManageVenues) 1f else 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = venue.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${venue.city} • ${venue.area} • ${venue.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (linkedProviders.isNotEmpty()) {
                        Text(
                            text = linkedProviders.joinToString(" • ") { "${it.name} (${it.customTitle})" },
                            style = MaterialTheme.typography.labelSmall,
                            color = IndigoAccentLight,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = venue.isOpen,
                        onCheckedChange = { onToggleOpen() },
                        modifier = Modifier.testTag("ctrl_venue_open_${venue.id}")
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ctrl_venue_delete_${venue.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف المركز",
                            tint = StatusUrgent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjustPrice(-5000) },
                        modifier = Modifier
                            .size(34.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "إنقاص السعر", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = IntentHelpers.formatDinar(venue.priceDinar),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { onAdjustPrice(5000) },
                        modifier = Modifier
                            .size(34.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "زيادة السعر", modifier = Modifier.size(16.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjustWait(-2) },
                        modifier = Modifier
                            .size(34.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "تقليل وقت الانتظار", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${venue.avgMinutesPerPerson} د/دور",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { onAdjustWait(2) },
                        modifier = Modifier
                            .size(34.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "زيادة وقت الانتظار", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandActionCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag(testTag)
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomVenueDialog(
    initialDistrict: NinevehDistrict,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        district: NinevehDistrict,
        area: String,
        category: ServiceCategory,
        specialty: String,
        priceDinar: Int,
        avgMinutes: Int,
        phone: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var district by remember { mutableStateOf(initialDistrict) }
    var area by remember { mutableStateOf(initialDistrict.areas.firstOrNull() ?: "") }
    var category by remember { mutableStateOf(ServiceCategory.CLINICS) }
    var specialty by remember { mutableStateOf(MedicalSpecialty.DENTAL) }
    var priceText by remember { mutableStateOf("15000") }
    var waitText by remember { mutableStateOf("8") }
    var phone by remember { mutableStateOf("07701234567") }
    var districtExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مركز جديد") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المركز أو العيادة *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_venue_name_input")
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { districtExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("القضاء: ${district.displayName}")
                    }
                    DropdownMenu(
                        expanded = districtExpanded,
                        onDismissRequest = { districtExpanded = false },
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        NinevehDistrict.selectableDistricts.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.displayName) },
                                onClick = {
                                    district = item
                                    area = item.areas.firstOrNull() ?: ""
                                    districtExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("الحي أو المنطقة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ServiceCategory.fiveCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (category == ServiceCategory.CLINICS) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MedicalSpecialty.entries.filter { it != MedicalSpecialty.ALL }.forEach { spec ->
                            FilterChip(
                                selected = specialty == spec,
                                onClick = { specialty = spec },
                                label = { Text(spec.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                        label = { Text("السعر (د.ع)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waitText,
                        onValueChange = { waitText = it.filter { c -> c.isDigit() } },
                        label = { Text("د/دور") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        district,
                        area,
                        category,
                        specialty.displayName,
                        priceText.toIntOrNull() ?: 15000,
                        waitText.toIntOrNull() ?: 8,
                        phone
                    )
                },
                enabled = name.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("add_venue_confirm_button")
            ) {
                Text("حفظ وإضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
