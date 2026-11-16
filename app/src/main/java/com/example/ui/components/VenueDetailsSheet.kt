package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ReviewEntity
import com.example.data.local.VenueEntity
import com.example.data.model.UserProfileSettings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.util.IntentHelpers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VenueDetailsSheet(
    venue: VenueEntity,
    reviews: List<ReviewEntity>,
    userProfile: UserProfileSettings,
    onDismiss: () -> Unit,
    onConfirmBooking: (customerName: String, customerPhone: String, selectedOption: String, notes: String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedOption by remember(venue.id) {
        mutableStateOf(venue.customOptions.firstOrNull() ?: "خدمة عامة")
    }
    var customerName by remember(venue.id, userProfile.userName) {
        mutableStateOf(userProfile.userName)
    }
    var customerPhone by remember(venue.id, userProfile.userPhone) {
        mutableStateOf(userProfile.userPhone)
    }
    var notes by remember(venue.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title + Rating + Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = venue.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${venue.category} • ${venue.city} - ${venue.area}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = IntentHelpers.formatDinar(venue.priceDinar),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = venue.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // Info Pills: Rating, Hours, Wait
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoStatPill(
                    icon = Icons.Default.Star,
                    text = "${venue.rating} (${venue.reviewCount})",
                    tint = AmberGold,
                    modifier = Modifier.weight(1f)
                )
                InfoStatPill(
                    icon = Icons.Default.Schedule,
                    text = venue.workingHours,
                    tint = EmeraldPrimary,
                    modifier = Modifier.weight(1.4f)
                )
                InfoStatPill(
                    icon = Icons.Default.AccessTime,
                    text = "${venue.estimatedWaitMinutes} د",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(0.8f)
                )
            }

            // Direct Action Buttons: Phone, Map, Messaging
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { IntentHelpers.dialPhone(context, venue.phone) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("venue_detail_call_button")
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الهاتف", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        IntentHelpers.openMapLocation(
                            context,
                            venue.latitude,
                            venue.longitude,
                            venue.name
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("venue_detail_map_button")
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الموقع", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        IntentHelpers.openMessaging(
                            context,
                            venue.whatsapp,
                            "مرحباً، أود الاستفسار عن الدور في ${venue.name}"
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("venue_detail_message_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الرسائل", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider()

            // Custom Options per Venue
            Text(
                text = "اختر نوع الخدمة المخصصة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                venue.customOptions.forEach { option ->
                    FilterChip(
                        selected = selectedOption == option,
                        onClick = { selectedOption = option },
                        label = { Text(option) }
                    )
                }
            }

            // Auto-filled user data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بيانات الحجز (مسح تلقائي من ملفك)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        customerName = userProfile.userName
                        customerPhone = userProfile.userPhone
                    }
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ملء تلقائي")
                }
            }

            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("الاسم الكامل") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_name_input")
            )

            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("رقم الهاتف") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_phone_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات إضافية (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onConfirmBooking(customerName, customerPhone, selectedOption, notes)
                },
                enabled = venue.isOpen && venue.isAcceptingBookings,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_venue_booking_button")
            ) {
                Text(
                    text = if (!venue.isAcceptingBookings) {
                        "الاستقبال متوقف مؤقتاً"
                    } else if (venue.isPaused) {
                        "احجز الآن (الطابور متوقف مؤقتاً)"
                    } else {
                        "تأكيد حجز الدور وإصدار تذكرة QR"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            HorizontalDivider()

            // Customer Reviews
            Text(
                text = "آراء الزبائن (${reviews.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (reviews.isEmpty()) {
                Text(
                    text = "لا توجد تقييمات بعد، كن أول من يقيم هذا المكان بعد إتمام خدمتك!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                reviews.forEach { rev ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = rev.authorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${rev.rating}.0",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = rev.comment,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}
