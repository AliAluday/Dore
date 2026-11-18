package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.VenueEntity
import com.example.data.model.SupportCategory
import com.example.data.model.SupportRequestType
import com.example.data.model.SupportTicketStatus
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.StatusUrgent
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusPaused
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SupportSheetTab(val label: String) {
    LIVE_CHAT("محادثة مباشرة"),
    COMPLAINT_FORM("تقديم شكوى"),
    MY_REQUESTS("طلباتي")
}

@Composable
fun SupportCenterSheet(
    tickets: List<SupportTicketEntity>,
    messagesByTicket: Map<String, List<SupportMessageEntity>>,
    venues: List<VenueEntity>,
    defaultName: String,
    defaultPhone: String,
    onSendTicket: (
        senderName: String,
        senderPhone: String,
        category: SupportCategory,
        subject: String,
        message: String,
        requestType: SupportRequestType,
        relatedVenueName: String
    ) -> Unit,
    onSendFollowUp: (
        ticketExternalId: String,
        senderName: String,
        messageBody: String
    ) -> Unit,
    onSyncReplies: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleExitAttempt: () -> Unit = {
        if (isKeyboardOpen) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            onDismiss()
        }
    }

    // Hardware / gesture back respects keyboard state first: hides keyboard without exiting chat/complaint.
    BackHandler(enabled = true) {
        handleExitAttempt()
    }

    var currentTab by remember { mutableStateOf(SupportSheetTab.LIVE_CHAT) }
    var selectedTicketExtId by remember {
        mutableStateOf(
            tickets.firstOrNull {
                SupportRequestType.fromName(it.requestType) == SupportRequestType.CHAT &&
                    it.status != SupportTicketStatus.CLOSED.name
            }?.externalId
                ?: tickets.firstOrNull { it.status != SupportTicketStatus.CLOSED.name }?.externalId
                ?: tickets.firstOrNull()?.externalId
        )
    }
    var awaitNewTicketSwitch by remember { mutableStateOf(false) }
    var previousTicketsCount by remember { mutableStateOf(tickets.size) }

    LaunchedEffect(tickets.size, awaitNewTicketSwitch) {
        if (awaitNewTicketSwitch && tickets.size > previousTicketsCount) {
            selectedTicketExtId = tickets.firstOrNull()?.externalId
            awaitNewTicketSwitch = false
        }
        previousTicketsCount = tickets.size
    }

    var senderName by remember(defaultName) { mutableStateOf(defaultName) }
    var senderPhone by remember(defaultPhone) { mutableStateOf(defaultPhone) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = handleExitAttempt
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.93f)
                .imePadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Compact Top Handle + Gradient Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF0A1428), Color(0xFF064E3B), Color(0xFF1E1B4B))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                                    .background(MintPrimaryDark.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "الدعم الفني والشكاوى",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isKeyboardOpen) "اضغط خروج لإخفاء الكيبورد أولاً"
                                    else "محادثة فورية وتقديم طلبات الشكوى",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onSyncReplies,
                                modifier = Modifier
                                    .size(34.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .testTag("support_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث الردود",
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = handleExitAttempt,
                                modifier = Modifier
                                    .size(34.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .testTag("support_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isKeyboardOpen) "إخفاء الكيبورد" else "خروج",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                // Compact 3-Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportSheetTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        val tabIcon = when (tab) {
                            SupportSheetTab.LIVE_CHAT -> Icons.AutoMirrored.Filled.Chat
                            SupportSheetTab.COMPLAINT_FORM -> Icons.Default.ReportProblem
                            SupportSheetTab.MY_REQUESTS -> Icons.Default.ListAlt
                        }
                        val labelText = if (tab == SupportSheetTab.MY_REQUESTS && tickets.isNotEmpty()) {
                            "${tab.label} (${tickets.size})"
                        } else {
                            tab.label
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentTab = tab }
                                .testTag("support_tab_${tab.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = null,
                                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                AnimatedContent(
                    targetState = currentTab,
                    label = "supportTabAnim",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { tab ->
                    when (tab) {
                        SupportSheetTab.LIVE_CHAT -> {
                            val activeTicket = tickets.find { it.externalId == selectedTicketExtId }
                            val threadMessages = remember(activeTicket, messagesByTicket) {
                                buildThreadMessages(activeTicket, messagesByTicket)
                            }
                            LiveSupportChatTab(
                                tickets = tickets,
                                activeTicket = activeTicket,
                                messages = threadMessages,
                                senderName = senderName,
                                onNameChange = { senderName = it },
                                onSelectTicket = { extId -> selectedTicketExtId = extId },
                                onStartNewChat = { selectedTicketExtId = null },
                                onSendMessage = { text ->
                                    if (activeTicket != null) {
                                        onSendFollowUp(
                                            activeTicket.externalId,
                                            senderName.ifBlank { activeTicket.senderName.ifBlank { "مستخدم دوري" } },
                                            text
                                        )
                                    } else {
                                        previousTicketsCount = tickets.size
                                        awaitNewTicketSwitch = true
                                        onSendTicket(
                                            senderName.ifBlank { "مستخدم دوري" },
                                            senderPhone,
                                            SupportCategory.GENERAL,
                                            "محادثة دعم مباشر",
                                            text,
                                            SupportRequestType.CHAT,
                                            ""
                                        )
                                    }
                                }
                            )
                        }

                        SupportSheetTab.COMPLAINT_FORM -> {
                            ComplaintRequestFormTab(
                                venues = venues,
                                senderName = senderName,
                                senderPhone = senderPhone,
                                onNameChange = { senderName = it },
                                onPhoneChange = { senderPhone = it },
                                onSubmitComplaint = { category, venueName, subject, details ->
                                    previousTicketsCount = tickets.size
                                    awaitNewTicketSwitch = true
                                    onSendTicket(
                                        senderName.ifBlank { "مستخدم دوري" },
                                        senderPhone,
                                        category,
                                        subject,
                                        details,
                                        SupportRequestType.COMPLAINT,
                                        venueName
                                    )
                                    currentTab = SupportSheetTab.LIVE_CHAT
                                }
                            )
                        }

                        SupportSheetTab.MY_REQUESTS -> {
                            SupportRequestsListTab(
                                tickets = tickets,
                                messagesByTicket = messagesByTicket,
                                onOpenThread = { extId ->
                                    selectedTicketExtId = extId
                                    currentTab = SupportSheetTab.LIVE_CHAT
                                },
                                onSyncReplies = onSyncReplies,
                                onNewComplaint = { currentTab = SupportSheetTab.COMPLAINT_FORM }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildThreadMessages(
    ticket: SupportTicketEntity?,
    messagesByTicket: Map<String, List<SupportMessageEntity>>
): List<SupportMessageEntity> {
    if (ticket == null) return emptyList()
    val stored = messagesByTicket[ticket.externalId].orEmpty()
    if (stored.isNotEmpty()) return stored

    // Graceful fallback if legacy ticket has no SupportMessageEntity rows
    val synthetic = mutableListOf<SupportMessageEntity>()
    if (ticket.message.isNotBlank()) {
        synthetic.add(
            SupportMessageEntity(
                externalMessageId = "LEGACY-U-${ticket.externalId}",
                ticketExternalId = ticket.externalId,
                isFromAdmin = false,
                senderName = ticket.senderName.ifBlank { "أنت" },
                body = ticket.message,
                syncedToPeer = ticket.syncedToControl,
                createdAt = ticket.createdAt
            )
        )
    }
    if (ticket.adminReply.isNotBlank()) {
        synthetic.add(
            SupportMessageEntity(
                externalMessageId = "LEGACY-A-${ticket.externalId}",
                ticketExternalId = ticket.externalId,
                isFromAdmin = true,
                senderName = "فريق الدعم الفني",
                body = ticket.adminReply,
                syncedToPeer = true,
                createdAt = ticket.updatedAt
            )
        )
    }
    return synthetic
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveSupportChatTab(
    tickets: List<SupportTicketEntity>,
    activeTicket: SupportTicketEntity?,
    messages: List<SupportMessageEntity>,
    senderName: String,
    onNameChange: (String) -> Unit,
    onSelectTicket: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageDraft by remember { mutableStateOf("") }
    var showNameField by remember { mutableStateOf(senderName.isBlank()) }
    val listState = rememberLazyListState()

    val quickStarters = listOf(
        "أحتاج مساعدة بخصوص دوري",
        "هل السعر ثابت عند الحضور؟",
        "كيف أتابع تذكرتي المباشرة؟"
    )

    LaunchedEffect(messages.size, activeTicket?.externalId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Thread Switcher Strip
        if (tickets.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = activeTicket == null,
                        onClick = onStartNewChat,
                        leadingIcon = {
                            Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("محادثة جديدة", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
                items(tickets, key = { it.externalId }) { ticket ->
                    val isSelected = activeTicket?.externalId == ticket.externalId
                    val isComplaint = SupportRequestType.fromName(ticket.requestType) == SupportRequestType.COMPLAINT
                    val hasReply = ticket.status == SupportTicketStatus.REPLIED.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTicket(ticket.externalId) },
                        label = {
                            val prefix = if (isComplaint) "شكوى: " else ""
                            val suffix = if (hasReply) " • تم الرد" else ""
                            Text(
                                text = "$prefix${ticket.subject.take(18)}$suffix",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }
        }

        if (showNameField && activeTicket == null) {
            OutlinedTextField(
                value = senderName,
                onValueChange = onNameChange,
                label = { Text("اسمك في المحادثة (اختياري)") },
                placeholder = { Text("اكتب اسمك ليظهر في المحادثة") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("support_chat_sender_name")
            )
        }

        if (activeTicket != null) {
            val isComplaint = SupportRequestType.fromName(activeTicket.requestType) == SupportRequestType.COMPLAINT
            val statusEnum = SupportTicketStatus.fromName(activeTicket.status)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            if (isComplaint) append("طلب شكوى • ")
                            append(activeTicket.subject)
                            if (activeTicket.relatedVenueName.isNotBlank()) {
                                append(" (${activeTicket.relatedVenueName})")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeTicket.syncedToControl && statusEnum == SupportTicketStatus.SENT) "تم الاستلام" else statusEnum.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (statusEnum == SupportTicketStatus.REPLIED) StatusOpen else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Chat Bubbles List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    ChatMessageBubble(
                        senderLabel = "فريق الدعم الفني",
                        body = "أهلاً بك${if (senderName.isNotBlank()) " يا $senderName" else ""} 👋 اكتب رسالتك بالأسفل وسيرد عليك فريق الدعم الفني مباشرة في هذه المحادثة.",
                        isFromAdmin = true,
                        timestamp = System.currentTimeMillis(),
                        isDelivered = true
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickStarters.forEach { starter ->
                            AssistChip(
                                onClick = { messageDraft = starter },
                                label = { Text(starter, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.externalMessageId }) { msg ->
                    ChatMessageBubble(
                        senderLabel = if (msg.isFromAdmin) "فريق الدعم الفني" else "أنت",
                        body = msg.body,
                        isFromAdmin = msg.isFromAdmin,
                        timestamp = msg.createdAt,
                        isDelivered = msg.syncedToPeer
                    )
                }
            }
        }

        // Pinned Chat Composer Bar
        val isClosed = activeTicket?.status == SupportTicketStatus.CLOSED.name
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        if (isClosed) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "هذه المحادثة مغلقة حالياً",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onStartNewChat,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("بدء محادثة جديدة", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { messageDraft = it },
                    placeholder = { Text("اكتب رسالتك للدعم الفني...", style = MaterialTheme.typography.bodySmall) },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("support_chat_input")
                )
                IconButton(
                    onClick = {
                        val text = messageDraft.trim()
                        if (text.isNotEmpty()) {
                            onSendMessage(text)
                            messageDraft = ""
                        }
                    },
                    enabled = messageDraft.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (messageDraft.trim().isNotEmpty()) EmeraldPrimary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("support_chat_send")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال الرسالة",
                        tint = if (messageDraft.trim().isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    senderLabel: String,
    body: String,
    isFromAdmin: Boolean,
    timestamp: Long,
    isDelivered: Boolean
) {
    val timeLabel = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale("ar")).format(Date(timestamp))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromAdmin) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isFromAdmin) 14.dp else 4.dp,
                bottomEnd = if (isFromAdmin) 4.dp else 14.dp
            ),
            color = if (isFromAdmin) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                EmeraldPrimary.copy(alpha = 0.16f)
            },
            border = BorderStroke(
                1.dp,
                if (isFromAdmin) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                else EmeraldPrimary.copy(alpha = 0.4f)
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isFromAdmin) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            tint = StatusOpen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = senderLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isFromAdmin) StatusOpen else EmeraldPrimary
                    )
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isFromAdmin) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (isDelivered) StatusOpen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComplaintRequestFormTab(
    venues: List<VenueEntity>,
    senderName: String,
    senderPhone: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmitComplaint: (
        category: SupportCategory,
        relatedVenueName: String,
        subject: String,
        details: String
    ) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(SupportCategory.COMPLAINT) }
    var selectedVenueName by remember { mutableStateOf("") }
    var venueMenuExpanded by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val quickComplaintTemplates = listOf(
        "تأخر طويل في الطابور" to "انتظرت طويلاً ولم يتم تحديث الدور في الوقت المتوقع، أرجو المتابعة.",
        "اختلاف السعر عن التطبيق" to "تم طلب مبلغ مختلف عن السعر المعروض في التطبيق عند الحضور للمركز.",
        "شكوى على جودة الخدمة" to "أرغب بتقديم شكوى رسمية بخصوص التعامل أو التنظيم داخل المركز."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StatusUrgent.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, StatusUrgent.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.ReportProblem, contentDescription = null, tint = StatusUrgent, modifier = Modifier.size(18.dp))
                Text(
                    text = "تقديم طلب شكوى رسمي — تتم متابعته والرد عليك في المحادثة المباشرة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text("تصنيف الطلب:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SupportCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("support_cat_${cat.name.lowercase()}")
                )
            }
        }

        // Optional Venue Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { venueMenuExpanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedVenueName.isBlank()) "المركز المعني بالشكوى (اختياري): شكوى عامة"
                        else "المركز المعني: $selectedVenueName",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            DropdownMenu(
                expanded = venueMenuExpanded,
                onDismissRequest = { venueMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("شكوى عامة (بدون مركز محدد)") },
                    onClick = {
                        selectedVenueName = ""
                        venueMenuExpanded = false
                    }
                )
                venues.take(18).forEach { venue ->
                    DropdownMenuItem(
                        text = { Text("${venue.name} • ${venue.city}") },
                        onClick = {
                            selectedVenueName = venue.name
                            venueMenuExpanded = false
                        }
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickComplaintTemplates.forEach { (title, body) ->
                AssistChip(
                    onClick = {
                        subject = title
                        details = body
                        validationError = null
                    },
                    label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = senderName,
                onValueChange = onNameChange,
                label = { Text("الاسم") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("support_sender_name_input")
            )
            OutlinedTextField(
                value = senderPhone,
                onValueChange = onPhoneChange,
                label = { Text("الهاتف (اختياري)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("support_sender_phone_input")
            )
        }

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("عنوان الشكوى أو الطلب") },
            placeholder = { Text("مثال: تأخر في استقبال التذكرة") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("support_subject_input")
        )

        OutlinedTextField(
            value = details,
            onValueChange = {
                details = it
                validationError = null
            },
            label = { Text("تفاصيل الشكوى *") },
            placeholder = { Text("اشرح المشكلة أو الطلب بالتفصيل...") },
            minLines = 2,
            maxLines = 4,
            isError = validationError != null,
            supportingText = validationError?.let { err -> { Text(err) } },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("support_message_input")
        )

        Button(
            onClick = {
                if (details.trim().length < 4) {
                    validationError = "يرجى كتابة 4 أحرف على الأقل في تفاصيل الشكوى"
                    return@Button
                }
                onSubmitComplaint(
                    selectedCategory,
                    selectedVenueName,
                    subject.ifBlank { "طلب شكوى: ${selectedCategory.displayName}" },
                    details
                )
                subject = ""
                details = ""
                validationError = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("support_send_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("إرسال طلب الشكوى", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SupportRequestsListTab(
    tickets: List<SupportTicketEntity>,
    messagesByTicket: Map<String, List<SupportMessageEntity>>,
    onOpenThread: (String) -> Unit,
    onSyncReplies: () -> Unit,
    onNewComplaint: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل المحادثات والشكاوى (${tickets.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = onSyncReplies,
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("تحديث", style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = onNewComplaint,
                        leadingIcon = { Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("شكوى جديدة", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (tickets.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا توجد محادثات أو شكاوى مسجلة بعد — ابدأ بمراسلة الدعم أو قدم طلب شكوى.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(tickets, key = { it.id }) { ticket ->
                val thread = messagesByTicket[ticket.externalId].orEmpty()
                val msgCount = maxOf(1, thread.size)
                val lastMsg = thread.lastOrNull()
                val previewText = when {
                    lastMsg != null && lastMsg.isFromAdmin -> "فريق الدعم الفني: ${lastMsg.body}"
                    lastMsg != null -> "أنت: ${lastMsg.body}"
                    ticket.adminReply.isNotBlank() -> "فريق الدعم الفني: ${ticket.adminReply}"
                    else -> ticket.message
                }
                SupportHistoryCard(
                    ticket = ticket,
                    messageCount = msgCount,
                    previewText = previewText,
                    onClick = { onOpenThread(ticket.externalId) }
                )
            }
        }
    }
}

@Composable
private fun SupportHistoryCard(
    ticket: SupportTicketEntity,
    messageCount: Int,
    previewText: String,
    onClick: () -> Unit
) {
    val statusEnum = SupportTicketStatus.fromName(ticket.status)
    val catEnum = SupportCategory.fromName(ticket.category)
    val isComplaint = SupportRequestType.fromName(ticket.requestType) == SupportRequestType.COMPLAINT
    val statusColor = when (statusEnum) {
        SupportTicketStatus.REPLIED -> StatusOpen
        SupportTicketStatus.DELIVERED -> IndigoAccentLight
        SupportTicketStatus.IN_REVIEW -> AmberGold
        SupportTicketStatus.CLOSED -> StatusPaused
        SupportTicketStatus.SENT -> MaterialTheme.colorScheme.primary
    }
    val statusIcon = when (statusEnum) {
        SupportTicketStatus.REPLIED -> Icons.Default.MarkChatRead
        SupportTicketStatus.DELIVERED -> Icons.Default.CheckCircle
        else -> Icons.Default.Schedule
    }
    val dateLabel = remember(ticket.updatedAt) {
        SimpleDateFormat("HH:mm • dd/MM", Locale("ar")).format(Date(ticket.updatedAt))
    }

    Card(
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .testTag("user_support_card_${ticket.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isComplaint) StatusUrgent.copy(alpha = 0.14f) else EmeraldPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (isComplaint) "شكوى" else "محادثة",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isComplaint) StatusUrgent else EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = ticket.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = if (ticket.syncedToControl && statusEnum == SupportTicketStatus.SENT) "تم الاستلام" else statusEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (ticket.relatedVenueName.isNotBlank()) {
                Text(
                    text = "المركز: ${ticket.relatedVenueName} • ${catEnum.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ticket.externalId} • $dateLabel • $messageCount رسائل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "فتح المحادثة ←",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
