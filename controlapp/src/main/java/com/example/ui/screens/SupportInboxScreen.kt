package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.model.AdminPermissionType
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
import com.example.ui.viewmodel.DoriUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SupportInboxScreen(
    state: DoriUiState,
    onSyncBridge: () -> Unit,
    onReplyTicket: (Long, String) -> Unit,
    onUpdateStatus: (Long, SupportTicketStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    var openChatTicketId by remember { mutableStateOf<Long?>(null) }
    val canReply = state.activeAdmin?.hasPermission(AdminPermissionType.REPLY_SUPPORT) ?: true

    val filteredTickets = remember(state.supportTickets, filterIndex) {
        when (filterIndex) {
            1 -> state.supportTickets.filter {
                SupportRequestType.fromName(it.requestType) == SupportRequestType.CHAT
            }
            2 -> state.supportTickets.filter {
                SupportRequestType.fromName(it.requestType) == SupportRequestType.COMPLAINT
            }
            3 -> state.supportTickets.filter {
                it.status == SupportTicketStatus.SENT.name ||
                    it.status == SupportTicketStatus.DELIVERED.name ||
                    it.status == SupportTicketStatus.IN_REVIEW.name
            }
            4 -> state.supportTickets.filter { it.status == SupportTicketStatus.REPLIED.name }
            5 -> state.supportTickets.filter { it.status == SupportTicketStatus.CLOSED.name }
            else -> state.supportTickets
        }
    }

    val openChatTicket = openChatTicketId?.let { id -> state.supportTickets.find { it.id == id } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gradient Support Header
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0A1428), Color(0xFF064E3B), Color(0xFF1E1B4B))
                                )
                            )
                            .padding(11.dp)
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
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "صندوق الدعم والشكاوى (${state.supportTickets.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (state.supportAlertCount > 0) {
                                            "${state.supportAlertCount} محادثة/شكوى بانتظار الرد"
                                        } else {
                                            "المحادثات المباشرة والشكاوى الواردة من تطبيق دوري"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Button(
                                onClick = onSyncBridge,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintPrimaryDark,
                                    contentColor = Color(0xFF04221B)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("ctrl_support_sync_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("سحب", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Filter Pills
            item {
                val filterLabels = listOf("الكل", "محادثات", "شكاوى", "بانتظار الرد", "تم الرد", "مغلقة")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filterLabels.size) { idx ->
                        FilterChip(
                            selected = filterIndex == idx,
                            onClick = { filterIndex = idx },
                            label = { Text(filterLabels[idx], style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }

            if (filteredTickets.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MarkChatUnread,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "لا توجد رسائل أو شكاوى في هذا القسم",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "عندما يرسل المستخدم رسالة أو شكوى من تطبيق دوري ستصل هنا تلقائياً أو عند الضغط على سحب.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTickets, key = { it.id }) { ticket ->
                    val threadMsgs = state.supportMessagesByTicket[ticket.externalId].orEmpty()
                    ControlSupportTicketCard(
                        ticket = ticket,
                        threadMessages = threadMsgs,
                        canReply = canReply,
                        onOpenChat = { openChatTicketId = ticket.id },
                        onReply = { text -> onReplyTicket(ticket.id, text) },
                        onMarkInReview = { onUpdateStatus(ticket.id, SupportTicketStatus.IN_REVIEW) },
                        onClose = { onUpdateStatus(ticket.id, SupportTicketStatus.CLOSED) }
                    )
                }
            }
        }

        if (openChatTicket != null) {
            ControlSupportChatOverlay(
                ticket = openChatTicket,
                messages = buildControlThreadMessages(
                    openChatTicket,
                    state.supportMessagesByTicket[openChatTicket.externalId].orEmpty()
                ),
                canReply = canReply,
                onSendReply = { text -> onReplyTicket(openChatTicket.id, text) },
                onMarkInReview = { onUpdateStatus(openChatTicket.id, SupportTicketStatus.IN_REVIEW) },
                onCloseTicket = { onUpdateStatus(openChatTicket.id, SupportTicketStatus.CLOSED) },
                onDismiss = { openChatTicketId = null }
            )
        }
    }
}

private fun buildControlThreadMessages(
    ticket: SupportTicketEntity,
    stored: List<SupportMessageEntity>
): List<SupportMessageEntity> {
    if (stored.isNotEmpty()) return stored
    val list = mutableListOf<SupportMessageEntity>()
    if (ticket.message.isNotBlank()) {
        list.add(
            SupportMessageEntity(
                externalMessageId = "LEGACY-U-${ticket.externalId}",
                ticketExternalId = ticket.externalId,
                isFromAdmin = false,
                senderName = ticket.senderName,
                body = ticket.message,
                createdAt = ticket.createdAt
            )
        )
    }
    if (ticket.adminReply.isNotBlank()) {
        list.add(
            SupportMessageEntity(
                externalMessageId = "LEGACY-A-${ticket.externalId}",
                ticketExternalId = ticket.externalId,
                isFromAdmin = true,
                senderName = ticket.repliedByAdmin.ifBlank { "فريق الدعم الفني" },
                body = ticket.adminReply,
                createdAt = ticket.updatedAt
            )
        )
    }
    return list
}

@Composable
private fun ControlSupportTicketCard(
    ticket: SupportTicketEntity,
    threadMessages: List<SupportMessageEntity>,
    canReply: Boolean,
    onOpenChat: () -> Unit,
    onReply: (String) -> Unit,
    onMarkInReview: () -> Unit,
    onClose: () -> Unit
) {
    var replyDraft by remember(ticket.id) { mutableStateOf("") }
    val statusEnum = SupportTicketStatus.fromName(ticket.status)
    val catEnum = SupportCategory.fromName(ticket.category)
    val isComplaint = SupportRequestType.fromName(ticket.requestType) == SupportRequestType.COMPLAINT
    val statusColor = when (statusEnum) {
        SupportTicketStatus.REPLIED -> StatusOpen
        SupportTicketStatus.DELIVERED, SupportTicketStatus.SENT -> IndigoAccentLight
        SupportTicketStatus.IN_REVIEW -> AmberGold
        SupportTicketStatus.CLOSED -> StatusPaused
    }
    val dateLabel = remember(ticket.updatedAt) {
        SimpleDateFormat("HH:mm • dd/MM", Locale("ar")).format(Date(ticket.updatedAt))
    }
    val latestMsg = threadMessages.lastOrNull()

    Card(
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ctrl_support_card_${ticket.id}")
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
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isComplaint) Icons.Default.ReportProblem else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isComplaint) StatusUrgent else statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "${ticket.senderName}${if (ticket.senderPhone.isNotBlank()) " • ${ticket.senderPhone}" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${if (isComplaint) "طلب شكوى" else "محادثة مباشرة"} • ${catEnum.displayName} • $dateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = statusEnum.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(onClick = onOpenChat)
            ) {
                Column(
                    modifier = Modifier.padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = buildString {
                            append(ticket.subject)
                            if (ticket.relatedVenueName.isNotBlank()) {
                                append(" — ${ticket.relatedVenueName}")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = latestMsg?.let {
                            val who = if (it.isFromAdmin) "رد الإدارة (${it.senderName}): " else "${ticket.senderName}: "
                            "$who${it.body}"
                        } ?: ticket.message,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "فتح المحادثة الكاملة (${maxOf(1, threadMessages.size)} رسائل) ←",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                label = { Text("رد سريع على ${ticket.senderName}...") },
                enabled = canReply,
                minLines = 1,
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ctrl_support_reply_input_${ticket.id}")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Button(
                    onClick = {
                        val txt = replyDraft.trim()
                        if (txt.isNotEmpty()) {
                            onReply(txt)
                            replyDraft = ""
                        }
                    },
                    enabled = canReply && replyDraft.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(34.dp)
                        .testTag("ctrl_support_send_reply_${ticket.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("إرسال", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenChat,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .height(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("المحادثة", style = MaterialTheme.typography.labelSmall)
                }

                if (statusEnum != SupportTicketStatus.CLOSED) {
                    OutlinedButton(
                        onClick = onMarkInReview,
                        enabled = canReply,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(0.65f)
                            .height(34.dp)
                    ) {
                        Text("مراجعة", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onClose,
                        enabled = canReply,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(0.4f)
                            .height(34.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "إغلاق", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSupportChatOverlay(
    ticket: SupportTicketEntity,
    messages: List<SupportMessageEntity>,
    canReply: Boolean,
    onSendReply: (String) -> Unit,
    onMarkInReview: () -> Unit,
    onCloseTicket: () -> Unit,
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

    BackHandler(enabled = true) {
        handleExitAttempt()
    }

    var replyDraft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isComplaint = SupportRequestType.fromName(ticket.requestType) == SupportRequestType.COMPLAINT

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
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
                // Header
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (isComplaint) "شكوى: " else "محادثة: "}${ticket.senderName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(ticket.subject)
                                    if (ticket.relatedVenueName.isNotBlank()) {
                                        append(" • ${ticket.relatedVenueName}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ticket.status != SupportTicketStatus.CLOSED.name) {
                                OutlinedButton(
                                    onClick = onMarkInReview,
                                    enabled = canReply,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("مراجعة", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = onCloseTicket,
                                    enabled = canReply,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("إغلاق", style = MaterialTheme.typography.labelSmall, color = MintPrimaryDark)
                                }
                            }
                            IconButton(
                                onClick = handleExitAttempt,
                                modifier = Modifier
                                    .size(34.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
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

                // Messages Thread
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.externalMessageId }) { msg ->
                        val timeLabel = remember(msg.createdAt) {
                            SimpleDateFormat("HH:mm", Locale("ar")).format(Date(msg.createdAt))
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.isFromAdmin) Alignment.Start else Alignment.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (msg.isFromAdmin) {
                                    EmeraldPrimary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (msg.isFromAdmin) EmeraldPrimary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (msg.isFromAdmin) {
                                            Icon(
                                                imageVector = Icons.Default.AdminPanelSettings,
                                                contentDescription = null,
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = if (msg.isFromAdmin) msg.senderName.ifBlank { "الدعم" } else ticket.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isFromAdmin) EmeraldPrimary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = msg.body,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Reply Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = replyDraft,
                        onValueChange = { replyDraft = it },
                        placeholder = { Text("اكتب ردك للمستخدم...", style = MaterialTheme.typography.bodySmall) },
                        enabled = canReply,
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val txt = replyDraft.trim()
                            if (txt.isNotEmpty()) {
                                onSendReply(txt)
                                replyDraft = ""
                            }
                        },
                        enabled = canReply && replyDraft.trim().isNotEmpty(),
                        modifier = Modifier
                            .size(44.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(
                                if (canReply && replyDraft.trim().isNotEmpty()) EmeraldPrimary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال الرد",
                            tint = if (canReply && replyDraft.trim().isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}
