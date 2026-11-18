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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminAccountEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.VenueEntity
import com.example.data.model.AdminPermissionType
import com.example.data.model.ProviderRole
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.DoriUiState

@Composable
fun TeamAndProvidersScreen(
    state: DoriUiState,
    onSelectActiveAdmin: (Long) -> Unit,
    onAddAdmin: (
        name: String,
        roleTitle: String,
        phone: String,
        canManageFeatures: Boolean,
        canManageVenues: Boolean,
        canManageQueues: Boolean,
        canReplySupport: Boolean,
        canManageProviders: Boolean,
        canManageAdmins: Boolean,
        canRunCommands: Boolean
    ) -> Unit,
    onUpdateAdmin: (AdminAccountEntity) -> Unit,
    onDeleteAdmin: (Long) -> Unit,
    onSelectActiveProvider: (Long) -> Unit,
    onAddProvider: (
        name: String,
        role: ProviderRole,
        customTitle: String,
        phone: String,
        venueId: Int,
        canAcceptTickets: Boolean,
        canCallNext: Boolean,
        canPauseVenue: Boolean
    ) -> Unit,
    onUpdateProvider: (ServiceProviderEntity) -> Unit,
    onDeleteProvider: (Long) -> Unit,
    onOpenProviderWorkspace: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showAddAdminDialog by remember { mutableStateOf(false) }
    var showAddProviderDialog by remember { mutableStateOf(false) }

    val activeAdmin = state.activeAdmin
    val canManageAdmins = activeAdmin?.hasPermission(AdminPermissionType.MANAGE_ADMINS) ?: true
    val canManageProviders = activeAdmin?.hasPermission(AdminPermissionType.MANAGE_PROVIDERS) ?: true

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
                    text = { Text("الأدمن والصلاحيات (${state.admins.size})") },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    modifier = Modifier.testTag("team_admins_tab")
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("الأطباء والتجار (${state.providers.size})") },
                    icon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.testTag("team_providers_tab")
                )
            }

            if (selectedSubTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ActiveAdminSessionCard(
                            activeAdmin = activeAdmin,
                            allAdmins = state.admins,
                            canManageAdmins = canManageAdmins,
                            onSelectActiveAdmin = onSelectActiveAdmin,
                            onOpenAddAdmin = { showAddAdminDialog = true }
                        )
                    }

                    items(state.admins, key = { it.id }) { admin ->
                        AdminAccountCard(
                            admin = admin,
                            isCurrent = activeAdmin?.id == admin.id,
                            canEditPermissions = canManageAdmins,
                            onActivate = { onSelectActiveAdmin(admin.id) },
                            onUpdate = onUpdateAdmin,
                            onDelete = { onDeleteAdmin(admin.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ProvidersHeaderCard(
                            activeProvider = state.activeProvider,
                            providerCount = state.providers.size,
                            canManageProviders = canManageProviders,
                            onClearProviderMode = { onSelectActiveProvider(0L) },
                            onOpenAddProvider = { showAddProviderDialog = true }
                        )
                    }

                    items(state.providers, key = { it.id }) { provider ->
                        ServiceProviderCard(
                            provider = provider,
                            isCurrentMode = state.activeProvider?.id == provider.id,
                            canManage = canManageProviders,
                            onToggleActive = {
                                onUpdateProvider(provider.copy(isActive = !provider.isActive))
                            },
                            onToggleCapability = { updated -> onUpdateProvider(updated) },
                            onEnterProviderMode = { onOpenProviderWorkspace(provider.id) },
                            onDelete = { onDeleteProvider(provider.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddAdminDialog) {
        AddAdminDialog(
            onDismiss = { showAddAdminDialog = false },
            onConfirm = { name, role, phone, f, v, q, s, p, a, c ->
                onAddAdmin(name, role, phone, f, v, q, s, p, a, c)
                showAddAdminDialog = false
            }
        )
    }

    if (showAddProviderDialog) {
        AddProviderDialog(
            venues = state.allVenues,
            onDismiss = { showAddProviderDialog = false },
            onConfirm = { name, role, customTitle, phone, venueId, accept, call, pause ->
                onAddProvider(name, role, customTitle, phone, venueId, accept, call, pause)
                showAddProviderDialog = false
            }
        )
    }
}

@Composable
private fun ActiveAdminSessionCard(
    activeAdmin: AdminAccountEntity?,
    allAdmins: List<AdminAccountEntity>,
    canManageAdmins: Boolean,
    onSelectActiveAdmin: (Long) -> Unit,
    onOpenAddAdmin: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "الأدمن النشط: ${activeAdmin?.name ?: "المدير العام"}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeAdmin?.roleTitle ?: "صلاحيات كاملة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("switch_active_admin_button")
                    ) {
                        Text("تبديل", style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        allAdmins.forEach { admin ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${admin.name} — ${admin.roleTitle}",
                                        fontWeight = if (admin.id == activeAdmin?.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectActiveAdmin(admin.id)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpenAddAdmin,
                enabled = canManageAdmins,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ctrl_add_admin_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (canManageAdmins) "إضافة أدمن جديد وتحديد صلاحياته"
                    else "إضافة أدمن (يتطلب صلاحية إدارة الأدمن)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminAccountCard(
    admin: AdminAccountEntity,
    isCurrent: Boolean,
    canEditPermissions: Boolean,
    onActivate: () -> Unit,
    onUpdate: (AdminAccountEntity) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.2.dp,
            if (isCurrent) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_card_${admin.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = admin.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (admin.isSuperAdmin) {
                            Surface(
                                shape = CircleShape,
                                color = AmberGold.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "مدير عام",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isCurrent) {
                            Surface(
                                shape = CircleShape,
                                color = StatusOpen.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "النشط الآن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusOpen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${admin.roleTitle}${if (admin.phone.isNotBlank()) " • ${admin.phone}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isCurrent) {
                        OutlinedButton(
                            onClick = onActivate,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("تفعيل", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (!admin.isSuperAdmin && canEditPermissions) {
                        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_admin_${admin.id}")) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الأدمن", tint = StatusUrgent)
                        }
                    }
                }
            }

            if (!admin.isSuperAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حالة الحساب (مفعّل)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = admin.isActive,
                        enabled = canEditPermissions,
                        onCheckedChange = { onUpdate(admin.copy(isActive = it)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text(
                text = if (admin.isSuperAdmin) "يملك كافة الصلاحيات بشكل دائم"
                else "صلاحيات الأدمن (اضغط للتعديل):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PermissionChip(
                    label = "المميزات",
                    checked = admin.canManageFeatures,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageFeatures = !admin.canManageFeatures)) }
                )
                PermissionChip(
                    label = "المراكز",
                    checked = admin.canManageVenues,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageVenues = !admin.canManageVenues)) }
                )
                PermissionChip(
                    label = "الطوابير والتذاكر",
                    checked = admin.canManageQueues,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageQueues = !admin.canManageQueues)) }
                )
                PermissionChip(
                    label = "الدعم الفني",
                    checked = admin.canReplySupport,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canReplySupport = !admin.canReplySupport)) }
                )
                PermissionChip(
                    label = "الأطباء والتجار",
                    checked = admin.canManageProviders,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageProviders = !admin.canManageProviders)) }
                )
                PermissionChip(
                    label = "إدارة الأدمن",
                    checked = admin.canManageAdmins,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageAdmins = !admin.canManageAdmins)) }
                )
                PermissionChip(
                    label = "الأوامر الحساسة",
                    checked = admin.canRunCommands,
                    enabled = !admin.isSuperAdmin && canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canRunCommands = !admin.canRunCommands)) }
                )
            }
        }
    }
}

@Composable
private fun PermissionChip(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = checked,
        onClick = { if (enabled) onToggle() },
        enabled = enabled || checked,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            if (checked) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = StatusOpen.copy(alpha = 0.16f),
            selectedLabelColor = StatusOpen,
            selectedLeadingIconColor = StatusOpen
        )
    )
}

@Composable
private fun ProvidersHeaderCard(
    activeProvider: ServiceProviderEntity?,
    providerCount: Int,
    canManageProviders: Boolean,
    onClearProviderMode: () -> Unit,
    onOpenAddProvider: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, IndigoAccentLight.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الأطباء وأصحاب الملاعب والتجار ($providerCount)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (activeProvider != null) {
                            "الوضع النشط: ${activeProvider.name} (${activeProvider.customTitle})"
                        } else {
                            "أضف طبيباً أو صاحب ملعب أو تاجراً ليقبل التذاكر ويدير مركزه"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeProvider != null) {
                    TextButton(onClick = onClearProviderMode) {
                        Text("إنهاء الوضع")
                    }
                }
            }

            Button(
                onClick = onOpenAddProvider,
                enabled = canManageProviders,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccentLight),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ctrl_add_provider_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة طبيب / صاحب ملعب / تاجر", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceProviderCard(
    provider: ServiceProviderEntity,
    isCurrentMode: Boolean,
    canManage: Boolean,
    onToggleActive: () -> Unit,
    onToggleCapability: (ServiceProviderEntity) -> Unit,
    onEnterProviderMode: () -> Unit,
    onDelete: () -> Unit
) {
    val roleEnum = ProviderRole.fromName(provider.providerRole)
    val roleIcon = providerIcon(roleEnum)

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.2.dp,
            if (isCurrentMode) IndigoAccentLight else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(IndigoAccentLight.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(roleIcon, contentDescription = null, tint = IndigoAccentLight, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = IndigoAccentLight.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = provider.customTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndigoAccentLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${provider.venueName} • مقبولة: ${provider.acceptedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = provider.isActive,
                        enabled = canManage,
                        onCheckedChange = { onToggleActive() }
                    )
                    if (canManage) {
                        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_provider_${provider.id}")) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = StatusUrgent)
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PermissionChip(
                    label = "قبول ورفض التذاكر",
                    checked = provider.canAcceptTickets,
                    enabled = canManage,
                    onToggle = { onToggleCapability(provider.copy(canAcceptTickets = !provider.canAcceptTickets)) }
                )
                PermissionChip(
                    label = "استدعاء الدور",
                    checked = provider.canCallNext,
                    enabled = canManage,
                    onToggle = { onToggleCapability(provider.copy(canCallNext = !provider.canCallNext)) }
                )
                PermissionChip(
                    label = "إيقاف/فتح الحجز",
                    checked = provider.canPauseVenue,
                    enabled = canManage,
                    onToggle = { onToggleCapability(provider.copy(canPauseVenue = !provider.canPauseVenue)) }
                )
            }

            Button(
                onClick = onEnterProviderMode,
                enabled = provider.isActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentMode) StatusOpen else EmeraldPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_provider_workspace_${provider.id}")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCurrentMode) "مفعّل حالياً — عرض التذاكر"
                    else "دخول بوضع ${provider.customTitle} لقبول التذاكر",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAdminDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        roleTitle: String,
        phone: String,
        canFeatures: Boolean,
        canVenues: Boolean,
        canQueues: Boolean,
        canSupport: Boolean,
        canProviders: Boolean,
        canAdmins: Boolean,
        canCommands: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var roleTitle by remember { mutableStateOf("مشرف تشغيل") }
    var phone by remember { mutableStateOf("") }
    var canFeatures by remember { mutableStateOf(false) }
    var canVenues by remember { mutableStateOf(true) }
    var canQueues by remember { mutableStateOf(true) }
    var canSupport by remember { mutableStateOf(true) }
    var canProviders by remember { mutableStateOf(true) }
    var canAdmins by remember { mutableStateOf(false) }
    var canCommands by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة أدمن جديد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الأدمن *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_admin_name_input")
                )
                OutlinedTextField(
                    value = roleTitle,
                    onValueChange = { roleTitle = it },
                    label = { Text("المسمى الوظيفي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_admin_role_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("اختر الصلاحيات:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PermissionChip("المميزات", canFeatures, true) { canFeatures = !canFeatures }
                    PermissionChip("المراكز", canVenues, true) { canVenues = !canVenues }
                    PermissionChip("الطوابير", canQueues, true) { canQueues = !canQueues }
                    PermissionChip("الدعم", canSupport, true) { canSupport = !canSupport }
                    PermissionChip("التجار", canProviders, true) { canProviders = !canProviders }
                    PermissionChip("الأدمن", canAdmins, true) { canAdmins = !canAdmins }
                    PermissionChip("الأوامر", canCommands, true) { canCommands = !canCommands }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name, roleTitle, phone,
                        canFeatures, canVenues, canQueues, canSupport, canProviders, canAdmins, canCommands
                    )
                },
                enabled = name.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("confirm_add_admin_button")
            ) {
                Text("إضافة الأدمن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProviderDialog(
    venues: List<VenueEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        role: ProviderRole,
        customTitle: String,
        phone: String,
        venueId: Int,
        canAccept: Boolean,
        canCallNext: Boolean,
        canPause: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(ProviderRole.DOCTOR) }
    var customTitle by remember { mutableStateOf(ProviderRole.DOCTOR.displayName) }
    var phone by remember { mutableStateOf("") }
    var selectedVenue by remember(venues) { mutableStateOf(venues.firstOrNull()) }
    var venueMenuExpanded by remember { mutableStateOf(false) }
    var canAccept by remember { mutableStateOf(true) }
    var canCallNext by remember { mutableStateOf(true) }
    var canPause by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة طبيب / صاحب ملعب / تاجر") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم الكامل *") },
                    placeholder = { Text("مثال: د. علي الحمداني أو كابتن أحمد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_provider_name_input")
                )

                Text("نوع النشاط / الصفة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProviderRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = {
                                selectedRole = role
                                customTitle = role.displayName
                            },
                            label = { Text(role.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("التسمية المخصصة") },
                    placeholder = { Text("طبيب، صاحب ملعب، تاجر...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_provider_title_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Venue Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { venueMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedVenue?.name ?: "اختر المركز المرتبط",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = venueMenuExpanded,
                        onDismissRequest = { venueMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        venues.forEach { venue ->
                            DropdownMenuItem(
                                text = { Text("${venue.name} (${venue.city})") },
                                onClick = {
                                    selectedVenue = venue
                                    venueMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("الصلاحيات:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PermissionChip("قبول التذاكر", canAccept, true) { canAccept = !canAccept }
                    PermissionChip("استدعاء التالي", canCallNext, true) { canCallNext = !canCallNext }
                    PermissionChip("إيقاف الطابور", canPause, true) { canPause = !canPause }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val vId = selectedVenue?.id ?: 1
                    onConfirm(name, selectedRole, customTitle, phone, vId, canAccept, canCallNext, canPause)
                },
                enabled = name.trim().length >= 3 && selectedVenue != null,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccentLight),
                modifier = Modifier.testTag("confirm_add_provider_button")
            ) {
                Text("حفظ وإضافة", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun providerIcon(role: ProviderRole): ImageVector = when (role) {
    ProviderRole.DOCTOR -> Icons.Default.LocalHospital
    ProviderRole.FIELD_OWNER -> Icons.Default.SportsSoccer
    ProviderRole.LAB_SPECIALIST -> Icons.Default.Science
    ProviderRole.WORKSHOP_OWNER -> Icons.Default.Build
    ProviderRole.BEAUTY_EXPERT -> Icons.Default.Spa
    ProviderRole.MERCHANT -> Icons.Default.Storefront
}
