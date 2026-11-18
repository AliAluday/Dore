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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminAccountEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.AdminPermissionType
import com.example.data.model.ProviderRole
import com.example.ui.components.AdminFormDialog
import com.example.ui.components.ProviderFormDialog
import com.example.ui.components.TeamPermissionChip
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
    var searchQuery by remember { mutableStateOf("") }
    var showAddAdminDialog by remember { mutableStateOf(false) }
    var editingAdmin by remember { mutableStateOf<AdminAccountEntity?>(null) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ServiceProviderEntity?>(null) }

    val activeAdmin = state.activeAdmin
    val canManageAdmins = activeAdmin?.hasPermission(AdminPermissionType.MANAGE_ADMINS) ?: true
    val canManageProviders = activeAdmin?.hasPermission(AdminPermissionType.MANAGE_PROVIDERS) ?: true

    val filteredAdmins = remember(state.admins, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) state.admins
        else state.admins.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.roleTitle.contains(q, ignoreCase = true) ||
                it.phone.contains(q, ignoreCase = true)
        }
    }

    val filteredProviders = remember(state.providers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) state.providers
        else state.providers.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.customTitle.contains(q, ignoreCase = true) ||
                it.venueName.contains(q, ignoreCase = true)
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
            TabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = {
                        selectedSubTab = 0
                        searchQuery = ""
                    },
                    text = { Text("الأدمن والصلاحيات (${state.admins.size})") },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    modifier = Modifier.testTag("team_admins_tab")
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = {
                        selectedSubTab = 1
                        searchQuery = ""
                    },
                    text = { Text("الأطباء والتجار (${state.providers.size})") },
                    icon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.testTag("team_providers_tab")
                )
            }

            // Organized Quick Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (selectedSubTab == 0) "ابحث عن أدمن بالاسم أو المسمى..."
                        else "ابحث عن طبيب أو صاحب ملعب أو مركز...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("team_search_input")
            )

            if (selectedSubTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                    items(filteredAdmins, key = { it.id }) { admin ->
                        AdminAccountCard(
                            admin = admin,
                            isCurrent = activeAdmin?.id == admin.id,
                            canEditPermissions = canManageAdmins,
                            onActivate = { onSelectActiveAdmin(admin.id) },
                            onEditDialog = { editingAdmin = admin },
                            onUpdate = onUpdateAdmin,
                            onDelete = { onDeleteAdmin(admin.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                    items(filteredProviders, key = { it.id }) { provider ->
                        ServiceProviderCard(
                            provider = provider,
                            isCurrentMode = state.activeProvider?.id == provider.id,
                            canManage = canManageProviders,
                            onToggleActive = {
                                onUpdateProvider(provider.copy(isActive = !provider.isActive))
                            },
                            onToggleCapability = { updated -> onUpdateProvider(updated) },
                            onEditDialog = { editingProvider = provider },
                            onEnterProviderMode = { onOpenProviderWorkspace(provider.id) },
                            onDelete = { onDeleteProvider(provider.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddAdminDialog) {
        AdminFormDialog(
            initialAdmin = null,
            onDismiss = { showAddAdminDialog = false },
            onSave = { created ->
                onAddAdmin(
                    created.name,
                    created.roleTitle,
                    created.phone,
                    created.canManageFeatures,
                    created.canManageVenues,
                    created.canManageQueues,
                    created.canReplySupport,
                    created.canManageProviders,
                    created.canManageAdmins,
                    created.canRunCommands
                )
                showAddAdminDialog = false
            }
        )
    }

    editingAdmin?.let { target ->
        AdminFormDialog(
            initialAdmin = target,
            onDismiss = { editingAdmin = null },
            onSave = { updated ->
                onUpdateAdmin(updated)
                editingAdmin = null
            }
        )
    }

    if (showAddProviderDialog) {
        ProviderFormDialog(
            initialProvider = null,
            venues = state.allVenues,
            onDismiss = { showAddProviderDialog = false },
            onSave = { created ->
                onAddProvider(
                    created.name,
                    ProviderRole.fromName(created.providerRole),
                    created.customTitle,
                    created.phone,
                    created.venueId,
                    created.canAcceptTickets,
                    created.canCallNext,
                    created.canPauseVenue
                )
                showAddProviderDialog = false
            }
        )
    }

    editingProvider?.let { target ->
        ProviderFormDialog(
            initialProvider = target,
            venues = state.allVenues,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                onUpdateProvider(updated)
                editingProvider = null
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
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(19.dp)
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
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("switch_active_admin_button")
                    ) {
                        Text("تبديل", style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(15.dp))
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
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("ctrl_add_admin_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (canManageAdmins) "إضافة أدمن جديد وتحديد صلاحياته"
                    else "إضافة أدمن (يتطلب صلاحية إدارة الأدمن)",
                    style = MaterialTheme.typography.labelMedium,
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
    onEditDialog: () -> Unit,
    onUpdate: (AdminAccountEntity) -> Unit,
    onDelete: () -> Unit
) {
    val permCount = AdminPermissionType.entries.count { admin.hasPermission(it) }

    Card(
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(
            1.dp,
            if (isCurrent) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_card_${admin.id}")
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    text = "مدير أساسي",
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${admin.roleTitle} • $permCount/7 صلاحية" +
                            if (admin.phone.isNotBlank()) " • ${admin.phone}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isCurrent) {
                        OutlinedButton(
                            onClick = onActivate,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("تفعيل", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (canEditPermissions) {
                        IconButton(
                            onClick = onEditDialog,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("edit_admin_${admin.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الأدمن وصلاحياته", tint = EmeraldPrimary, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (!admin.isSuperAdmin && canEditPermissions) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delete_admin_${admin.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الأدمن", tint = StatusUrgent, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حالة الحساب (مفعّل)", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = admin.isActive,
                    enabled = canEditPermissions,
                    onCheckedChange = { onUpdate(admin.copy(isActive = it)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (canEditPermissions) "الصلاحيات (اضغط للتعديل المباشر):"
                    else "صلاحيات الحساب (للتعديل بدّل لحساب يملك إدارة الأدمن):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canEditPermissions) {
                    TextButton(onClick = onEditDialog, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("تعديل الكل", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TeamPermissionChip(
                    label = "المميزات",
                    checked = admin.canManageFeatures,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageFeatures = !admin.canManageFeatures)) }
                )
                TeamPermissionChip(
                    label = "المراكز",
                    checked = admin.canManageVenues,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageVenues = !admin.canManageVenues)) }
                )
                TeamPermissionChip(
                    label = "الطوابير والتذاكر",
                    checked = admin.canManageQueues,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageQueues = !admin.canManageQueues)) }
                )
                TeamPermissionChip(
                    label = "الدعم الفني",
                    checked = admin.canReplySupport,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canReplySupport = !admin.canReplySupport)) }
                )
                TeamPermissionChip(
                    label = "الأطباء والتجار",
                    checked = admin.canManageProviders,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageProviders = !admin.canManageProviders)) }
                )
                TeamPermissionChip(
                    label = "إدارة الأدمن",
                    checked = admin.canManageAdmins,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canManageAdmins = !admin.canManageAdmins)) }
                )
                TeamPermissionChip(
                    label = "الأوامر الحساسة",
                    checked = admin.canRunCommands,
                    enabled = canEditPermissions,
                    onToggle = { onUpdate(admin.copy(canRunCommands = !admin.canRunCommands)) }
                )
            }
        }
    }
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
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, IndigoAccentLight.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
                            "أضف أو عدّل صلاحيات أي طبيب أو صاحب ملعب أو تاجر لقبول التذاكر"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeProvider != null) {
                    TextButton(onClick = onClearProviderMode) {
                        Text("إنهاء الوضع", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Button(
                onClick = onOpenAddProvider,
                enabled = canManageProviders,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccentLight),
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("ctrl_add_provider_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("إضافة طبيب / صاحب ملعب / تاجر", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
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
    onEditDialog: () -> Unit,
    onEnterProviderMode: () -> Unit,
    onDelete: () -> Unit
) {
    val roleEnum = ProviderRole.fromName(provider.providerRole)
    val roleIcon = providerIcon(roleEnum)
    val capCount = listOf(provider.canAcceptTickets, provider.canCallNext, provider.canPauseVenue).count { it }

    Card(
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(
            1.dp,
            if (isCurrentMode) IndigoAccentLight else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}")
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(IndigoAccentLight.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(roleIcon, contentDescription = null, tint = IndigoAccentLight, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "${provider.venueName} • $capCount/3 صلاحيات • مقبولة: ${provider.acceptedCount}",
                            style = MaterialTheme.typography.labelSmall,
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
                        IconButton(
                            onClick = onEditDialog,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("edit_provider_${provider.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل مقدم الخدمة", tint = IndigoAccentLight, modifier = Modifier.size(17.dp))
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delete_provider_${provider.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = StatusUrgent, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TeamPermissionChip(
                    label = "قبول ورفض التذاكر",
                    checked = provider.canAcceptTickets,
                    enabled = canManage,
                    onToggle = { onToggleCapability(provider.copy(canAcceptTickets = !provider.canAcceptTickets)) }
                )
                TeamPermissionChip(
                    label = "استدعاء الدور",
                    checked = provider.canCallNext,
                    enabled = canManage,
                    onToggle = { onToggleCapability(provider.copy(canCallNext = !provider.canCallNext)) }
                )
                TeamPermissionChip(
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
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .testTag("open_provider_workspace_${provider.id}")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isCurrentMode) "مفعّل حالياً — عرض التذاكر"
                    else "دخول بوضع ${provider.customTitle} لقبول التذاكر",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun providerIcon(role: ProviderRole): ImageVector = when (role) {
    ProviderRole.DOCTOR -> Icons.Default.LocalHospital
    ProviderRole.FIELD_OWNER -> Icons.Default.SportsSoccer
    ProviderRole.LAB_SPECIALIST -> Icons.Default.Science
    ProviderRole.WORKSHOP_OWNER -> Icons.Default.Build
    ProviderRole.BEAUTY_EXPERT -> Icons.Default.Spa
    ProviderRole.MERCHANT -> Icons.Default.Storefront
}
