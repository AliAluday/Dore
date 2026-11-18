package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.ReviewEntity
import com.example.data.local.VenueEntity
import com.example.data.model.DoctorUrgency
import com.example.data.model.DoctorVisitType
import com.example.data.model.FieldBookingDuration
import com.example.data.model.ServiceCategory
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusUrgent
import com.example.util.IntentHelpers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VenueDetailsSheet(
    venue: VenueEntity,
    bookingSessionId: Long,
    controlConfig: UserAppControlConfig,
    reviews: List<ReviewEntity>,
    onDismiss: () -> Unit,
    onConfirmBooking: (
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String,
        optionalInfo: String,
        calculatedPriceDinar: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isField = venue.category == ServiceCategory.FIELDS.displayName
    val isClinic = venue.category == ServiceCategory.CLINICS.displayName
    val isLab = venue.category == ServiceCategory.LABS.displayName

    // Fresh per-booking selections & blank user inputs on every new booking session
    var selectedOption by remember(venue.id, bookingSessionId) {
        mutableStateOf(venue.customOptions.firstOrNull() ?: "خدمة عامة")
    }
    var selectedHours by remember(venue.id, bookingSessionId) {
        mutableDoubleStateOf(FieldBookingDuration.MIN_HOURS)
    }
    var doctorVisitType by remember(venue.id, bookingSessionId) {
        mutableStateOf(DoctorVisitType.NEW_CHECKUP)
    }
    var doctorUrgency by remember(venue.id, bookingSessionId) {
        mutableStateOf(DoctorUrgency.NORMAL)
    }
    var labFasting by remember(venue.id, bookingSessionId) { mutableStateOf(false) }

    var customerName by remember(venue.id, bookingSessionId) { mutableStateOf("") }
    var customerPhone by remember(venue.id, bookingSessionId) { mutableStateOf("") }
    var showOptionalFields by remember(venue.id, bookingSessionId) { mutableStateOf(false) }
    var optionalAge by remember(venue.id, bookingSessionId) { mutableStateOf("") }
    var optionalNote by remember(venue.id, bookingSessionId) { mutableStateOf("") }
    var showReviews by remember(venue.id, bookingSessionId) { mutableStateOf(false) }
    var hasAttemptedSubmit by remember(venue.id, bookingSessionId) { mutableStateOf(false) }

    val isNameValid = UserProfileSettings.isValidFullName(customerName)
    val isPhoneValid = UserProfileSettings.isValidPhone(customerPhone)

    val calculatedPrice = remember(venue.id, venue.priceDinar, selectedHours, doctorVisitType) {
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Concise Venue Header + Contact Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = StatusOpen.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StatusOpen.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = null,
                                tint = StatusOpen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "حجز بنفس اليوم • دور مباشر",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusOpen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = venue.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${venue.city} • ${venue.area} • ${IntentHelpers.formatDinar(calculatedPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(
                        onClick = { IntentHelpers.dialPhone(context, venue.phone) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("venue_detail_call_button")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "اتصال", modifier = Modifier.size(16.dp))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            IntentHelpers.openMapLocation(context, venue.latitude, venue.longitude, venue.name)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("venue_detail_map_button")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "الموقع", modifier = Modifier.size(16.dp))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            IntentHelpers.openMessaging(context, venue.whatsapp, "مرحباً، أود الحجز في ${venue.name}")
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("venue_detail_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "رسالة", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 3-Metric Queue Summary: Sequential #1+ Ticket, People Ahead, Queue ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueueMetricBox(
                    icon = Icons.Default.ConfirmationNumber,
                    label = "دورك القادم",
                    value = "#${venue.nextTicketNumber}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                QueueMetricBox(
                    icon = Icons.Default.Groups,
                    label = "قبلك",
                    value = "${venue.queueAheadCount}",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                QueueMetricBox(
                    icon = Icons.Default.AccessTime,
                    label = "التقديري",
                    value = if (venue.estimatedWaitMinutes <= 0) "فوري" else "${venue.estimatedWaitMinutes} د",
                    tint = StatusOpen,
                    modifier = Modifier.weight(1f)
                )
            }

            // Service Options
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                venue.customOptions.forEach { option ->
                    FilterChip(
                        selected = selectedOption == option,
                        onClick = { selectedOption = option },
                        label = { Text(option, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Sports Fields Hours Stepper (1.5h min, 0.5h step, 3.0h max)
            if (isField) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                                    .size(36.dp)
                                    .testTag("field_hours_decrease")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص نصف ساعة", modifier = Modifier.size(18.dp))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SportsSoccer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = FieldBookingDuration.formatArabic(selectedHours),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("field_hours_label")
                                    )
                                }
                                Text(
                                    text = "الإجمالي: ${IntentHelpers.formatDinar(calculatedPrice)}",
                                    style = MaterialTheme.typography.labelSmall,
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
                                    .size(36.dp)
                                    .testTag("field_hours_increase")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة نصف ساعة", modifier = Modifier.size(18.dp))
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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

            // Clinics Same-Day Visit Type & Urgency
            if (isClinic) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DoctorVisitType.entries.forEach { visit ->
                                FilterChip(
                                    selected = doctorVisitType == visit,
                                    onClick = { doctorVisitType = visit },
                                    label = { Text(visit.displayName, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("doctor_visit_${visit.name.lowercase()}")
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DoctorUrgency.entries.forEach { urgency ->
                                val isUrgent = urgency == DoctorUrgency.URGENT
                                FilterChip(
                                    selected = doctorUrgency == urgency,
                                    onClick = { doctorUrgency = urgency },
                                    label = { Text(urgency.displayName, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isUrgent) StatusUrgent else MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("doctor_urgency_${urgency.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (isLab) {
                FilterChip(
                    selected = labFasting,
                    onClick = { labFasting = !labFasting },
                    label = { Text("يتطلب صيام (8-12 ساعة)") }
                )
            }

            // MANDATORY CUSTOMER INFO: Always entered fresh from the beginning on every booking
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (hasAttemptedSubmit && (!isNameValid || !isPhoneValid)) StatusUrgent
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
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
                        Text(
                            text = "بيانات الحجز الجديد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(shape = CircleShape, color = StatusUrgent.copy(alpha = 0.16f)) {
                            Text(
                                text = "إلزامي *",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusUrgent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("الاسم الكامل *") },
                        placeholder = { Text("أدخل الاسم الثلاثي لهذا الحجز") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = hasAttemptedSubmit && !isNameValid,
                        supportingText = if (hasAttemptedSubmit && !isNameValid) {
                            { Text("الاسم الكامل إلزامي (3 أحرف على الأقل)", color = StatusUrgent) }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("booking_name_input")
                    )

                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("رقم الهاتف *") },
                        placeholder = { Text("0770xxxxxxx") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = hasAttemptedSubmit && !isPhoneValid,
                        supportingText = if (hasAttemptedSubmit && !isPhoneValid) {
                            { Text("رقم الهاتف إلزامي (10 إلى 14 رقماً)", color = StatusUrgent) }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("booking_phone_input")
                    )
                }
            }

            // Collapsible Optional Information (controlled by Control Panel)
            if (controlConfig.enableOptionalFields) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showOptionalFields = !showOptionalFields }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "معلومات اختيارية (العمر أو ملاحظة)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showOptionalFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showOptionalFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = optionalAge,
                            onValueChange = { optionalAge = it },
                            label = { Text("العمر (اختياري)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("booking_optional_age_input")
                        )
                        OutlinedTextField(
                            value = optionalNote,
                            onValueChange = { optionalNote = it },
                            label = { Text("ملاحظة إضافية (اختياري)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("booking_optional_note_input")
                        )
                    }
                }
            }

            // Primary Confirm Button
            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (!isNameValid || !isPhoneValid) return@Button

                    val finalOption = when {
                        isField -> "$selectedOption • ${FieldBookingDuration.shortLabel(selectedHours)}"
                        isClinic -> "$selectedOption • ${doctorVisitType.displayName}"
                        isLab && labFasting -> "$selectedOption • صائم"
                        else -> selectedOption
                    }
                    val serviceNotes = buildString {
                        if (isClinic) {
                            append("التخصص: ${venue.specialty} • ${doctorUrgency.displayName}")
                        } else if (isField) {
                            append("المدة: ${FieldBookingDuration.formatArabic(selectedHours)}")
                        }
                    }
                    val combinedOptional = if (!controlConfig.enableOptionalFields) "" else buildString {
                        if (optionalAge.isNotBlank()) append("العمر: ${optionalAge.trim()}")
                        if (optionalNote.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(optionalNote.trim())
                        }
                    }
                    onConfirmBooking(
                        customerName.trim(),
                        customerPhone.trim(),
                        finalOption,
                        serviceNotes,
                        combinedOptional,
                        calculatedPrice
                    )
                },
                enabled = controlConfig.allowNewBookings && venue.isOpen && venue.isAcceptingBookings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("confirm_venue_booking_button")
            ) {
                Text(
                    text = if (!controlConfig.allowNewBookings) {
                        "الحجز متوقف مؤقتاً"
                    } else if (!venue.isOpen || !venue.isAcceptingBookings) {
                        "الاستقبال متوقف مؤقتاً"
                    } else if (isField) {
                        "تأكيد #${venue.nextTicketNumber} • ${FieldBookingDuration.shortLabel(selectedHours)} (${IntentHelpers.formatDinar(calculatedPrice)})"
                    } else if (controlConfig.enableQrCode) {
                        "تأكيد الحجز #${venue.nextTicketNumber} وإصدار QR"
                    } else {
                        "تأكيد الحجز #${venue.nextTicketNumber}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Collapsible Customer Reviews (controlled by Control Panel)
            if (controlConfig.enableReviews && reviews.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReviews = !showReviews }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "آراء الزبائن (${reviews.size}) • ${venue.rating}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showReviews) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showReviews) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reviews.forEach { rev ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = rev.authorName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text(text = rev.comment, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(text = "★ ${rev.rating}", style = MaterialTheme.typography.labelMedium, color = AmberGold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueMetricBox(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
