package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.example.data.local.ReviewEntity
import com.example.data.local.VenueEntity
import com.example.data.model.DoctorTimePeriod
import com.example.data.model.DoctorUrgency
import com.example.data.model.DoctorVisitType
import com.example.data.model.FieldBookingDuration
import com.example.data.model.ServiceCategory
import com.example.data.model.UserProfileSettings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StatusUrgent
import com.example.util.IntentHelpers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VenueDetailsSheet(
    venue: VenueEntity,
    reviews: List<ReviewEntity>,
    userProfile: UserProfileSettings,
    onDismiss: () -> Unit,
    onConfirmBooking: (
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String,
        calculatedPriceDinar: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isField = venue.category == ServiceCategory.FIELDS.displayName
    val isClinic = venue.category == ServiceCategory.CLINICS.displayName
    val isLab = venue.category == ServiceCategory.LABS.displayName

    var selectedOption by remember(venue.id) {
        mutableStateOf(venue.customOptions.firstOrNull() ?: "خدمة عامة")
    }
    var selectedHours by remember(venue.id) {
        mutableDoubleStateOf(FieldBookingDuration.MIN_HOURS)
    }
    var doctorVisitType by remember(venue.id) { mutableStateOf(DoctorVisitType.NEW_CHECKUP) }
    var doctorPeriod by remember(venue.id) { mutableStateOf(DoctorTimePeriod.EVENING) }
    var doctorUrgency by remember(venue.id) { mutableStateOf(DoctorUrgency.NORMAL) }
    var patientAge by remember(venue.id) { mutableStateOf("") }
    var labFasting by remember(venue.id) { mutableStateOf(false) }

    var customerName by remember(venue.id, userProfile.userName) {
        mutableStateOf(userProfile.userName)
    }
    var customerPhone by remember(venue.id, userProfile.userPhone) {
        mutableStateOf(userProfile.userPhone)
    }
    var notes by remember(venue.id) { mutableStateOf("") }

    val calculatedPrice = remember(venue.id, selectedHours, doctorVisitType) {
        when {
            isField -> (venue.priceDinar * selectedHours).toInt()
            isClinic -> (venue.priceDinar * doctorVisitType.priceMultiplier).toInt()
            else -> venue.priceDinar
        }
    }

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
            // Title + Specialty Badge + Dynamic Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (venue.specialty.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isClinic) Icons.Default.MedicalServices else Icons.Default.SportsSoccer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = venue.specialty,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
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
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (isField) "الإجمالي" else "التكلفة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = IntentHelpers.formatDinar(calculatedPrice),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (isField) {
                            Text(
                                text = "(${IntentHelpers.formatDinar(venue.priceDinar)}/س)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
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
                            "مرحباً، أود الاستفسار عن الحجز في ${venue.name}"
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

            // Base Venue Options
            Text(
                text = if (isField) "اختر نوع الملعب" else if (isClinic) "اختر الإجراء أو الفحص الطبي" else "اختر نوع الخدمة المخصصة",
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

            // SPECIALIZED FLOW 1: Sports Fields Hours Stepper (Min 1.5h, Step 0.5h, Max 3.0h)
            if (isField) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsSoccer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "تحديد عدد ساعات حجز الملعب",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "أقل مدة ساعة ونصف (1.5) • الزيادة نصف ساعة (0.5) • أقصى مدة 3 ساعات",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilledIconButton(
                                onClick = {
                                    selectedHours = FieldBookingDuration.clamp(
                                        selectedHours - FieldBookingDuration.STEP_HOURS
                                    )
                                },
                                enabled = selectedHours > FieldBookingDuration.MIN_HOURS,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("field_hours_decrease")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص نصف ساعة")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = FieldBookingDuration.formatArabic(selectedHours),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("field_hours_label")
                                )
                                Text(
                                    text = "الإجمالي: ${IntentHelpers.formatDinar(calculatedPrice)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    selectedHours = FieldBookingDuration.clamp(
                                        selectedHours + FieldBookingDuration.STEP_HOURS
                                    )
                                },
                                enabled = selectedHours < FieldBookingDuration.MAX_HOURS,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("field_hours_increase")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة نصف ساعة")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FieldBookingDuration.validSteps.forEach { step ->
                                FilterChip(
                                    selected = selectedHours == step,
                                    onClick = { selectedHours = step },
                                    label = { Text(FieldBookingDuration.shortLabel(step)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("field_hours_chip_$step")
                                )
                            }
                        }
                    }
                }
            }

            // SPECIALIZED FLOW 2: Doctors & Clinics Distinct Medical Booking Flow
            if (isClinic) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "تفاصيل الحجز الطبي (${venue.specialty})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "نوع الزيارة (يختلف السعر حسب نوع الزيارة):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DoctorVisitType.entries.forEach { visit ->
                                FilterChip(
                                    selected = doctorVisitType == visit,
                                    onClick = { doctorVisitType = visit },
                                    label = { Text(visit.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("doctor_visit_${visit.name.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "فترة المعاينة المفضلة:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DoctorTimePeriod.entries.forEach { period ->
                                FilterChip(
                                    selected = doctorPeriod == period,
                                    onClick = { doctorPeriod = period },
                                    label = { Text("${period.displayName} (${period.rangeText})") },
                                    modifier = Modifier.testTag("doctor_period_${period.name.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "أولوية الحالة:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DoctorUrgency.entries.forEach { urgency ->
                                val isUrgent = urgency == DoctorUrgency.URGENT
                                FilterChip(
                                    selected = doctorUrgency == urgency,
                                    onClick = { doctorUrgency = urgency },
                                    label = { Text(urgency.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isUrgent) StatusUrgent else MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = patientAge,
                            onValueChange = { patientAge = it },
                            label = { Text("عمر المريض (اختياري)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // SPECIALIZED FLOW 3: Labs fasting option
            if (isLab) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = labFasting,
                        onClick = { labFasting = !labFasting },
                        label = { Text("يتطلب صيام (8-12 ساعة)") }
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
                    text = if (isClinic) "بيانات المراجع (مسح تلقائي)" else "بيانات الحجز (مسح تلقائي من ملفك)",
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
                label = { Text(if (isClinic) "اسم المراجع الكامل" else "الاسم الكامل") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_name_input")
            )

            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("رقم الهاتف") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_phone_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(if (isClinic) "الأعراض أو ملاحظات للطبيب (اختياري)" else "ملاحظات إضافية (اختياري)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val finalOption = when {
                        isField -> "$selectedOption • ${FieldBookingDuration.shortLabel(selectedHours)}"
                        isClinic -> "$selectedOption • ${doctorVisitType.displayName} (${doctorPeriod.displayName})"
                        isLab && labFasting -> "$selectedOption • صائم"
                        else -> selectedOption
                    }
                    val finalNotes = buildString {
                        if (isClinic) {
                            append("التخصص: ${venue.specialty} • ${doctorUrgency.displayName}")
                            if (patientAge.isNotBlank()) append(" • العمر: $patientAge")
                            if (notes.isNotBlank()) append(" • $notes")
                        } else if (isField) {
                            append("المدة: ${FieldBookingDuration.formatArabic(selectedHours)}")
                            if (notes.isNotBlank()) append(" • $notes")
                        } else {
                            append(notes)
                        }
                    }
                    onConfirmBooking(
                        customerName,
                        customerPhone,
                        finalOption,
                        finalNotes,
                        calculatedPrice
                    )
                },
                enabled = venue.isOpen && venue.isAcceptingBookings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_venue_booking_button")
            ) {
                Text(
                    text = if (!venue.isAcceptingBookings) {
                        "الاستقبال متوقف مؤقتاً"
                    } else if (venue.isPaused) {
                        "احجز الآن (الطابور متوقف مؤقتاً)"
                    } else if (isField) {
                        "تأكيد حجز ${FieldBookingDuration.shortLabel(selectedHours)} (${IntentHelpers.formatDinar(calculatedPrice)})"
                    } else if (isClinic) {
                        "حجز الموعد الطبي • ${IntentHelpers.formatDinar(calculatedPrice)}"
                    } else {
                        "تأكيد الحجز وإصدار التكت ورمز QR"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
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
                        shape = RoundedCornerShape(14.dp),
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
    tint: Color,
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
