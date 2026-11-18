package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminAccountEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.VenueEntity
import com.example.data.model.ProviderRole
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.StatusOpen

@Composable
fun TeamPermissionChip(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminFormDialog(
    initialAdmin: AdminAccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AdminAccountEntity) -> Unit
) {
    val isEditing = initialAdmin != null
    var name by remember(initialAdmin) { mutableStateOf(initialAdmin?.name ?: "") }
    var roleTitle by remember(initialAdmin) { mutableStateOf(initialAdmin?.roleTitle ?: "مشرف تشغيل") }
    var phone by remember(initialAdmin) { mutableStateOf(initialAdmin?.phone ?: "") }
    var isActive by remember(initialAdmin) { mutableStateOf(initialAdmin?.isActive ?: true) }
    var canFeatures by remember(initialAdmin) { mutableStateOf(initialAdmin?.canManageFeatures ?: false) }
    var canVenues by remember(initialAdmin) { mutableStateOf(initialAdmin?.canManageVenues ?: true) }
    var canQueues by remember(initialAdmin) { mutableStateOf(initialAdmin?.canManageQueues ?: true) }
    var canSupport by remember(initialAdmin) { mutableStateOf(initialAdmin?.canReplySupport ?: true) }
    var canProviders by remember(initialAdmin) { mutableStateOf(initialAdmin?.canManageProviders ?: true) }
    var canAdmins by remember(initialAdmin) { mutableStateOf(initialAdmin?.canManageAdmins ?: false) }
    var canCommands by remember(initialAdmin) { mutableStateOf(initialAdmin?.canRunCommands ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "تعديل صلاحيات وبيانات الأدمن" else "إضافة أدمن جديد")
        },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_admin_name_input")
                )
                OutlinedTextField(
                    value = roleTitle,
                    onValueChange = { roleTitle = it },
                    label = { Text("المسمى الوظيفي") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_admin_role_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الحساب مفعّل", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الصلاحيات:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                canFeatures = true; canVenues = true; canQueues = true
                                canSupport = true; canProviders = true; canAdmins = true; canCommands = true
                            }
                        ) {
                            Text("منح الكل", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                canFeatures = false; canVenues = false; canQueues = true
                                canSupport = true; canProviders = false; canAdmins = false; canCommands = false
                            }
                        ) {
                            Text("افتراضي", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TeamPermissionChip("المميزات", canFeatures, true) { canFeatures = !canFeatures }
                    TeamPermissionChip("المراكز", canVenues, true) { canVenues = !canVenues }
                    TeamPermissionChip("الطوابير", canQueues, true) { canQueues = !canQueues }
                    TeamPermissionChip("الدعم الفني", canSupport, true) { canSupport = !canSupport }
                    TeamPermissionChip("الأطباء والتجار", canProviders, true) { canProviders = !canProviders }
                    TeamPermissionChip("إدارة الأدمن", canAdmins, true) { canAdmins = !canAdmins }
                    TeamPermissionChip("الأوامر الحساسة", canCommands, true) { canCommands = !canCommands }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initialAdmin ?: AdminAccountEntity(name = name.trim(), roleTitle = roleTitle.trim())
                    onSave(
                        base.copy(
                            name = name.trim(),
                            roleTitle = roleTitle.trim().ifBlank { "مشرف تشغيل" },
                            phone = phone.trim(),
                            isActive = isActive,
                            canManageFeatures = canFeatures,
                            canManageVenues = canVenues,
                            canManageQueues = canQueues,
                            canReplySupport = canSupport,
                            canManageProviders = canProviders,
                            canManageAdmins = canAdmins,
                            canRunCommands = canCommands
                        )
                    )
                },
                enabled = name.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("confirm_add_admin_button")
            ) {
                Text(if (isEditing) "حفظ التعديلات" else "إضافة الأدمن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderFormDialog(
    initialProvider: ServiceProviderEntity?,
    venues: List<VenueEntity>,
    onDismiss: () -> Unit,
    onSave: (ServiceProviderEntity) -> Unit
) {
    val isEditing = initialProvider != null
    var name by remember(initialProvider) { mutableStateOf(initialProvider?.name ?: "") }
    var selectedRole by remember(initialProvider) {
        mutableStateOf(
            initialProvider?.let { ProviderRole.fromName(it.providerRole) } ?: ProviderRole.DOCTOR
        )
    }
    var customTitle by remember(initialProvider) {
        mutableStateOf(initialProvider?.customTitle ?: ProviderRole.DOCTOR.displayName)
    }
    var phone by remember(initialProvider) { mutableStateOf(initialProvider?.phone ?: "") }
    var selectedVenue by remember(initialProvider, venues) {
        mutableStateOf(
            venues.find { it.id == initialProvider?.venueId } ?: venues.firstOrNull()
        )
    }
    var venueMenuExpanded by remember { mutableStateOf(false) }
    var isActive by remember(initialProvider) { mutableStateOf(initialProvider?.isActive ?: true) }
    var canAccept by remember(initialProvider) { mutableStateOf(initialProvider?.canAcceptTickets ?: true) }
    var canCallNext by remember(initialProvider) { mutableStateOf(initialProvider?.canCallNext ?: true) }
    var canPause by remember(initialProvider) { mutableStateOf(initialProvider?.canPauseVenue ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "تعديل صلاحيات وبيانات مقدم الخدمة" else "إضافة طبيب / صاحب ملعب / تاجر")
        },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_provider_name_input")
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
                                val wasDefaultTitle = customTitle == selectedRole.displayName
                                selectedRole = role
                                if (wasDefaultTitle || customTitle.isBlank()) {
                                    customTitle = role.displayName
                                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_provider_title_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("المركز المرتبط:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الحساب مفعّل", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الصلاحيات:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        canAccept = true; canCallNext = true; canPause = true
                    }) {
                        Text("منح الكل", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeamPermissionChip("قبول ورفض التذاكر", canAccept, true) { canAccept = !canAccept }
                    TeamPermissionChip("استدعاء التالي", canCallNext, true) { canCallNext = !canCallNext }
                    TeamPermissionChip("إيقاف/فتح الحجز", canPause, true) { canPause = !canPause }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val venue = selectedVenue ?: return@Button
                    val base = initialProvider ?: ServiceProviderEntity(
                        name = name.trim(),
                        venueId = venue.id,
                        venueName = venue.name
                    )
                    onSave(
                        base.copy(
                            name = name.trim(),
                            providerRole = selectedRole.name,
                            customTitle = customTitle.trim().ifBlank { selectedRole.displayName },
                            phone = phone.trim().ifBlank { venue.phone },
                            venueId = venue.id,
                            venueName = venue.name,
                            isActive = isActive,
                            canAcceptTickets = canAccept,
                            canCallNext = canCallNext,
                            canPauseVenue = canPause
                        )
                    )
                },
                enabled = name.trim().length >= 3 && selectedVenue != null,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccentLight),
                modifier = Modifier.testTag("confirm_add_provider_button")
            ) {
                Text(if (isEditing) "حفظ التعديلات" else "حفظ وإضافة", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
