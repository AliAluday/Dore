package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.NinevehDistrict
import com.example.ui.theme.AmberGold
import com.example.ui.theme.IndigoAccentDark
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.MintPrimaryLight

@Composable
fun TopStickyHeader(
    selectedDistrict: NinevehDistrict,
    isDarkMode: Boolean,
    activeBookingCount: Int,
    isSimulationRunning: Boolean,
    onSelectDistrict: (NinevehDistrict) -> Unit,
    onToggleDarkMode: () -> Unit,
    onOpenTickets: () -> Unit,
    modifier: Modifier = Modifier
) {
    var districtMenuExpanded by remember { mutableStateOf(false) }

    val headerGradient = if (isDarkMode) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF0A1224), Color(0xFF0E1B2E), Color(0xFF082224))
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F6FF), Color(0xFFECFDF5))
        )
    }

    val accentTint by animateColorAsState(
        targetValue = if (isDarkMode) MintPrimaryDark else MintPrimaryLight,
        label = "headerAccent"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = if (isDarkMode) 8.dp else 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Emblem + Nineveh District Picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accentTint.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accentTint.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = accentTint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "دوري",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSimulationRunning) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AmberGold)
                                )
                            }
                        }
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable { districtMenuExpanded = true }
                                .testTag("city_selector_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "اختر قضاء في نينوى",
                                    tint = if (isDarkMode) IndigoAccentDark else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedDistrict.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = districtMenuExpanded,
                            onDismissRequest = { districtMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 380.dp)
                        ) {
                            NinevehDistrict.entries.forEach { district ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = district.displayName,
                                                fontWeight = if (district == selectedDistrict) FontWeight.Bold else FontWeight.Normal,
                                                color = if (district == selectedDistrict) accentTint else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (district.areas.isNotEmpty()) {
                                                Text(
                                                    text = "${district.areas.size} منطقة وحي",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = if (district == selectedDistrict) accentTint else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        onSelectDistrict(district)
                                        districtMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("district_option_${district.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Active Tickets Pill + Theme Switcher
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeBookingCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AmberGold,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenTickets() }
                                .testTag("header_active_tickets_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = "التذاكر النشطة",
                                    tint = Color(0xFF1E1B08),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$activeBookingCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF1E1B08),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("dark_mode_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkMode) "الوضع النهاري" else "الوضع الليلي",
                            tint = if (isDarkMode) AmberGold else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
