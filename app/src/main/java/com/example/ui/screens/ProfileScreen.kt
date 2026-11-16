package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LoyaltyTier
import com.example.data.model.NinevehDistrict
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.viewmodel.DoriUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    state: DoriUiState,
    onSaveProfile: (String, String, NinevehDistrict) -> Unit,
    onToggleDarkMode: () -> Unit,
    onSetMuteSound: (Boolean) -> Unit,
    onSetMuteVibration: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = state.preferences
    val points = prefs.loyaltyPoints
    val tier = LoyaltyTier.fromPoints(points)
    val nextTarget = tier.nextTierPoints
    val rawProgress = if (nextTarget == null) 1f else {
        val span = (nextTarget - tier.minPoints).coerceAtLeast(1)
        ((points - tier.minPoints).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(targetValue = rawProgress, label = "loyaltyProgress")

    var nameInput by remember(prefs.userName) { mutableStateOf(prefs.userName) }
    var phoneInput by remember(prefs.userPhone) { mutableStateOf(prefs.userPhone) }
    var districtInput by remember(prefs.selectedDistrict) { mutableStateOf(prefs.selectedDistrict) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Futuristic Loyalty Card
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0A1428), Color(0xFF064E3B), Color(0xFF312E81))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, MintPrimaryDark.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WorkspacePremium,
                                                contentDescription = null,
                                                tint = when (tier) {
                                                    LoyaltyTier.SILVER -> Color(0xFFE2E8F0)
                                                    LoyaltyTier.GOLD -> AmberGold
                                                    LoyaltyTier.DIAMOND -> DiamondBlue
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "العضوية ${tier.title}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "$points نقطة ولاء",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "+10 نقاط عند إتمام كل خدمة في نينوى",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }

                                Image(
                                    painter = painterResource(id = R.drawable.img_loyalty_crest),
                                    contentDescription = "وسام الولاء",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "فضية (0)  •  ذهبية (100)  •  ماسية (250)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    if (nextTarget != null) {
                                        Text(
                                            text = "المتبقي ${nextTarget - points}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintPrimaryDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    color = MintPrimaryDark,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                                Text(
                                    text = "الميزة الحالية: ${tier.perkDescription}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }
                }
            }

            // Local Nineveh District & Profile Form
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "بياناتك الشخصية وقضاء نينوى المحلي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تُستخدم للمسح التلقائي عند الحجز وتُحفظ محلياً بالكامل على جهازك.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("الاسم الكامل") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input")
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("رقم الهاتف") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_phone_input")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationCity,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "القضاء الافتراضي في نينوى",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NinevehDistrict.entries.forEach { district ->
                                FilterChip(
                                    selected = districtInput == district,
                                    onClick = { districtInput = district },
                                    label = { Text(district.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = { onSaveProfile(nameInput, phoneInput, districtInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_profile_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ البيانات محلياً")
                        }
                    }
                }
            }

            // Android Alert & Theme Settings
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "تجهيزات الأندرويد والتنبيهات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        SettingToggleRow(
                            icon = Icons.Default.DarkMode,
                            title = "الوضع الليلي",
                            subtitle = "تبديل مظهر التطبيق بين النهاري والليلي",
                            checked = prefs.isDarkMode,
                            onCheckedChange = { onToggleDarkMode() },
                            tag = "profile_dark_mode_switch"
                        )

                        SettingToggleRow(
                            icon = Icons.Default.NotificationsOff,
                            title = "كتم صوت التنبيه",
                            subtitle = "إيقاف نغمة التنبيه عند اقتراب أو حلول الدور",
                            checked = prefs.muteSound,
                            onCheckedChange = onSetMuteSound,
                            tag = "profile_mute_sound_switch"
                        )

                        SettingToggleRow(
                            icon = Icons.Default.Vibration,
                            title = "كتم الاهتزاز",
                            subtitle = "إيقاف الاهتزاز عند تحديث حالة التذكرة",
                            checked = prefs.muteVibration,
                            onCheckedChange = onSetMuteVibration,
                            tag = "profile_mute_vibration_switch"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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
