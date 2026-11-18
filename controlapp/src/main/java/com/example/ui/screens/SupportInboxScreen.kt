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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusUrgent
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
    onOpenChatTicket: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 0 = Normal messaging (المراسلات), 1 = Problem resolution cards (بطاقات حل المشاكل)
    var inboxSection by remember { mutableIntStateOf(0) }
    var filterIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val canReply = state.activeAdmin?.hasPermission(AdminPermissionType.REPLY_SUPPORT) ?: true

    val allChatTickets = remember(state.supportTickets) {
        state.supportTickets.filter { SupportRequestType.fromName(it.requestType) == SupportRequestType.CHAT }
    }
    val allProblemCards = remember(state.supportTickets) {
        state.supportTickets.filter { SupportRequestType.fromName(it.requestType) == SupportRequestType.COMPLAINT }
    }
    val pendingChatCount = remember(allChatTickets) {
        allChatTickets.count {
            it.status == SupportTicketStatus.SENT.name ||
                it.status == SupportTicketStatus.DELIVERED.name ||
                it.status == SupportTicketStatus.IN_REVIEW.name
        }
    }
    val pendingCardCount = remember(allProblemCards) {
        allProblemCards.count {
            it.status == SupportTicketStatus.SENT.name ||
                it.status == SupportTicketStatus.DELIVERED.name ||
                it.status == SupportTicketStatus.IN_REVIEW.name
        }
    }

    val filteredItems = remember(
        allChatTickets,
        allProblemCards,
        state.supportMessagesByTicket,
        inboxSection,
        filterIndex,
        searchQuery
    ) {
        val baseList = if (inboxSection == 0) allChatTickets else allProblemCards
        val q = searchQuery.trim()
        baseList
            .sortedByDescending { it.updatedAt }
            .filter { ticket ->
                val matchesFilter = when (filterIndex) {
                    1 -> ticket.status == SupportTicketStatus.SENT.name ||
                        ticket.status == SupportTicketStatus.DELIVERED.name ||
                        ticket.status == SupportTicketStatus.IN_REVIEW.name
                    2 -> ticket.status == SupportTicketStatus.REPLIED.name
                    3 -> ticket.status == SupportTicketStatus.CLOSED.name
                    else -> true
                }
                if (!matchesFilter) return@filter false
                if (q.isEmpty()) return@filter true
                val latestBody = state.supportMessagesByTicket[ticket.externalId]?.lastOrNull()?.body.orEmpty()
                ticket.senderName.contains(q, ignoreCase = true) ||
                    ticket.senderPhone.contains(q, ignoreCase = true) ||
                    ticket.relatedVenueName.contains(q, ignoreCase = true) ||
                    ticket.subject.contains(q, ignoreCase = true) ||
                    ticket.message.contains(q, ignoreCase = true) ||
                    latestBody.contains(q, ignoreCase = true)
            }
    }

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
            // Compact Gradient Conversations Header
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
                                        text = if (inboxSection == 0) "مراسلات الدعم (${allChatTickets.size})"
                                        else "بطاقات حل المشاكل (${allProblemCards.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (inboxSection == 0) {
                                            if (pendingChatCount > 0) "$pendingChatCount محادثة بانتظار الرد — اضغط للرد"
                                            else "اضغط على أي محادثة لفتح الدردشة والرد المباشر"
                                        } else {
                                            if (pendingCardCount > 0) "$pendingCardCount بطاقة مشكلة بانتظار الحل"
                                            else "تظهر طلبات حل المشاكل كبطاقات للحل والمتابعة"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSyncBridge()
                                },
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

            // Segmented Mode Tabs: Normal Chat vs Problem Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chatSelected = inboxSection == 0
                    val cardsSelected = inboxSection == 1
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (chatSelected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                inboxSection = 0
                            }
                            .testTag("ctrl_support_section_chats")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = if (chatSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "المراسلات (${allChatTickets.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (chatSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (cardsSelected) StatusUrgent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                inboxSection = 1
                            }
                            .testTag("ctrl_support_section_cards")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReportProblem,
                                contentDescription = null,
                                tint = if (cardsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "بطاقات المشاكل (${allProblemCards.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (cardsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Compact Search by User Name / Phone
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث باسم المستخدم أو رقم الهاتف...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "مسح البحث", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ctrl_support_search")
                )
            }

            // Filter Pills
            item {
                val filterLabels = listOf("الكل", "بانتظار الرد", "تم الرد", "مغلقة")
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

            if (filteredItems.isEmpty()) {
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
                                text = if (inboxSection == 0) "لا توجد مراسلات مطابقة" else "لا توجد بطاقات مشاكل مطابقة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (inboxSection == 0) {
                                    "عندما يبدأ المستخدم مراسلة عادية ستظهر محادثته هنا للرد الفوري."
                                } else {
                                    "عندما يقدم المستخدم طلب لحل مشكلة سيظهر هنا كبطاقة لإضافة الحل."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (inboxSection == 0) {
                items(filteredItems, key = { it.id }) { ticket ->
                    val threadMsgs = buildControlThreadMessages(
                        ticket = ticket,
                        stored = state.supportMessagesByTicket[ticket.externalId].orEmpty()
                    )
                    ConversationUserRow(
                        ticket = ticket,
                        threadMessages = threadMsgs,
                        onClick = { onOpenChatTicket(ticket.id) }
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { ticket ->
                    val threadMsgs = buildControlThreadMessages(
                        ticket = ticket,
                        stored = state.supportMessagesByTicket[ticket.externalId].orEmpty()
                    )
                    ControlProblemTicketCard(
                        ticket = ticket,
                        threadMessages = threadMsgs,
                        canReply = canReply,
                        onSendReply = { text -> onReplyTicket(ticket.id, text) },
                        onMarkInReview = { onUpdateStatus(ticket.id, SupportTicketStatus.IN_REVIEW) },
                        onCloseTicket = { onUpdateStatus(ticket.id, SupportTicketStatus.CLOSED) },
                        onReopenTicket = { onUpdateStatus(ticket.id, SupportTicketStatus.IN_REVIEW) },
                        onOpenFullView = { onOpenChatTicket(ticket.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlProblemTicketCard(
    ticket: SupportTicketEntity,
    threadMessages: List<SupportMessageEntity>,
    canReply: Boolean,
    onSendReply: (String) -> Unit,
    onMarkInReview: () -> Unit,
    onCloseTicket: () -> Unit,
    onReopenTicket: () -> Unit,
    onOpenFullView: () -> Unit
) {
    val statusEnum = SupportTicketStatus.fromName(ticket.status)
    val catEnum = SupportCategory.fromName(ticket.category)
    val statusColor = when (statusEnum) {
        SupportTicketStatus.REPLIED -> StatusOpen
        SupportTicketStatus.DELIVERED, SupportTicketStatus.SENT -> IndigoAccentLight
        SupportTicketStatus.IN_REVIEW -> AmberGold
        SupportTicketStatus.CLOSED -> StatusPaused
    }
    val timeLabel = remember(ticket.updatedAt) {
        SimpleDateFormat("HH:mm • dd/MM", Locale("ar")).format(Date(ticket.updatedAt))
    }
    val displayUserName = ticket.senderName.trim().ifBlank { "مستخدم دوري" }
    val adminReplies = remember(threadMessages) {
        threadMessages.filter { it.isFromAdmin }
    }
    val userExtraNotes = remember(threadMessages, ticket.message) {
        threadMessages.filter { !it.isFromAdmin && it.body.trim() != ticket.message.trim() }
    }
    val haptic = LocalHapticFeedback.current
    var resolutionDraft by remember(ticket.id) { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ctrl_support_card_${ticket.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = StatusUrgent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "بطاقة مشكلة",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusUrgent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = displayUserName,
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
                    Text(
                        text = statusEnum.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = buildString {
                    append(ticket.externalId)
                    append(" • ${catEnum.displayName}")
                    if (ticket.relatedVenueName.isNotBlank()) append(" • ${ticket.relatedVenueName}")
                    if (ticket.senderPhone.isNotBlank()) append(" • ${ticket.senderPhone}")
                    append(" • $timeLabel")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = ticket.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = ticket.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                    userExtraNotes.forEach { extra ->
                        Text(
                            text = "توضيح المستخدم: ${extra.body}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (adminReplies.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldPrimary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "الحل المرسل:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                        adminReplies.forEach { r ->
                            Text(
                                text = "${r.senderName.ifBlank { "الإدارة" }}: ${r.body}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Card status actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ticket.status != SupportTicketStatus.CLOSED.name) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMarkInReview()
                        },
                        enabled = canReply,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("قيد المراجعة", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCloseTicket()
                        },
                        enabled = canReply,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("تم الحل / إغلاق", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onReopenTicket()
                        },
                        enabled = canReply,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("ctrl_support_reopen_${ticket.id}")
                    ) {
                        Text("إعادة فتح", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onOpenFullView,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("فتح البطاقة", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Inline resolution reply composer on the problem card
            if (ticket.status != SupportTicketStatus.CLOSED.name) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = resolutionDraft,
                        onValueChange = { resolutionDraft = it },
                        placeholder = { Text("اكتب الحل والرد على البطاقة...", style = MaterialTheme.typography.bodySmall) },
                        enabled = canReply,
                        minLines = 1,
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ctrl_support_reply_input_${ticket.id}")
                    )
                    IconButton(
                        onClick = {
                            val txt = resolutionDraft.trim()
                            if (txt.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSendReply(txt)
                                resolutionDraft = ""
                            }
                        },
                        enabled = canReply && resolutionDraft.trim().isNotEmpty(),
                        modifier = Modifier
                            .size(44.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(
                                if (canReply && resolutionDraft.trim().isNotEmpty()) EmeraldPrimary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("ctrl_support_send_reply_${ticket.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال الحل",
                            tint = if (canReply && resolutionDraft.trim().isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

fun buildControlThreadMessages(
    ticket: SupportTicketEntity,
    stored: List<SupportMessageEntity>
): List<SupportMessageEntity> {
    fun isSynthetic(id: String): Boolean =
        id.startsWith("PUSH-") || id.startsWith("SYNC-") || id.startsWith("LEGACY-")

    val deduped = mutableListOf<SupportMessageEntity>()
    for (msg in stored.sortedBy { it.createdAt }) {
        val bodyKey = msg.body.trim()
        val existingIdx = deduped.indexOfFirst {
            it.isFromAdmin == msg.isFromAdmin && it.body.trim() == bodyKey
        }
        if (existingIdx >= 0) {
            val existing = deduped[existingIdx]
            if (isSynthetic(existing.externalMessageId) && !isSynthetic(msg.externalMessageId)) {
                deduped[existingIdx] = msg
            } else if (!isSynthetic(existing.externalMessageId) && !isSynthetic(msg.externalMessageId) &&
                existing.externalMessageId != msg.externalMessageId
            ) {
                if (kotlin.math.abs(msg.createdAt - existing.createdAt) >= 2_500L) {
                    deduped.add(msg)
                }
            }
        } else {
            deduped.add(msg)
        }
    }

    if (deduped.isEmpty() && ticket.message.isNotBlank()) {
        deduped.add(
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
    if (ticket.adminReply.isNotBlank() &&
        deduped.none { it.isFromAdmin && it.body.trim() == ticket.adminReply.trim() }
    ) {
        deduped.add(
            SupportMessageEntity(
                externalMessageId = "LEGACY-A-${ticket.externalId}-${ticket.adminReply.hashCode()}",
                ticketExternalId = ticket.externalId,
                isFromAdmin = true,
                senderName = ticket.repliedByAdmin.ifBlank { "فريق الدعم الفني" },
                body = ticket.adminReply,
                createdAt = maxOf(ticket.updatedAt, (deduped.lastOrNull()?.createdAt ?: 0L) + 1L)
            )
        )
    }
    return deduped.sortedBy { it.createdAt }
}

@Composable
private fun ConversationUserRow(
    ticket: SupportTicketEntity,
    threadMessages: List<SupportMessageEntity>,
    onClick: () -> Unit
) {
    val statusEnum = SupportTicketStatus.fromName(ticket.status)
    val isComplaint = SupportRequestType.fromName(ticket.requestType) == SupportRequestType.COMPLAINT
    val needsReply = statusEnum == SupportTicketStatus.SENT ||
        statusEnum == SupportTicketStatus.DELIVERED ||
        statusEnum == SupportTicketStatus.IN_REVIEW
    val statusColor = when (statusEnum) {
        SupportTicketStatus.REPLIED -> StatusOpen
        SupportTicketStatus.DELIVERED, SupportTicketStatus.SENT -> IndigoAccentLight
        SupportTicketStatus.IN_REVIEW -> AmberGold
        SupportTicketStatus.CLOSED -> StatusPaused
    }
    val timeLabel = remember(ticket.updatedAt) {
        SimpleDateFormat("HH:mm • dd/MM", Locale("ar")).format(Date(ticket.updatedAt))
    }
    val latestMsg = threadMessages.lastOrNull()
    val messageCount = maxOf(1, threadMessages.size)
    val displayUserName = ticket.senderName.trim().ifBlank { "مستخدم دوري" }
    val avatarInitial = displayUserName.firstOrNull()?.toString() ?: "م"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (needsReply) EmeraldPrimary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (needsReply) 1.2.dp else 1.dp,
            color = if (needsReply) EmeraldPrimary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("ctrl_support_card_${ticket.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // User Avatar with unread indicator
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isComplaint) StatusUrgent.copy(alpha = 0.14f)
                            else EmeraldPrimary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isComplaint) {
                        Icon(
                            imageVector = Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = StatusUrgent,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = avatarInitial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }
                }
                if (needsReply) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary)
                    )
                }
            }

            // User Name + Last Message Preview
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                        Text(
                            text = displayUserName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (ticket.senderPhone.isNotBlank()) {
                            Text(
                                text = ticket.senderPhone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (needsReply) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (needsReply) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Request type + venue chip row (compact single line)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isComplaint) StatusUrgent.copy(alpha = 0.14f) else EmeraldPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isComplaint) "شكوى" else "محادثة",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isComplaint) StatusUrgent else EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (ticket.relatedVenueName.isNotBlank()) {
                        Text(
                            text = ticket.relatedVenueName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else if (isComplaint && ticket.subject.isNotBlank()) {
                        Text(
                            text = ticket.subject,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                // Last message snippet + status/count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (latestMsg?.isFromAdmin == true) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = StatusOpen,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = latestMsg?.let { msg ->
                                if (msg.isFromAdmin) {
                                    "الدعم (${msg.senderName.ifBlank { "الإدارة" }}): ${msg.body}"
                                } else {
                                    msg.body
                                }
                            } ?: ticket.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (needsReply) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (needsReply) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = statusColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = statusEnum.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (needsReply) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$messageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (needsReply) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlSupportChatOverlay(
    ticket: SupportTicketEntity,
    messages: List<SupportMessageEntity>,
    canReply: Boolean,
    onSendReply: (String) -> Unit,
    onMarkInReview: () -> Unit,
    onCloseTicket: () -> Unit,
    onReopenTicket: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

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

    var replyDraft by remember(ticket.id) { mutableStateOf("") }
    var copiedBadge by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isComplaint = SupportRequestType.fromName(ticket.requestType) == SupportRequestType.COMPLAINT
    val catEnum = SupportCategory.fromName(ticket.category)
    val displayUserName = ticket.senderName.trim().ifBlank { "مستخدم دوري" }

    LaunchedEffect(copiedBadge) {
        if (copiedBadge) {
            delay(1800L)
            copiedBadge = false
        }
    }

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
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
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
                modifier = Modifier.fillMaxSize()
            ) {
                // Header showing User Name & Conversation Context
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MintPrimaryDark.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isComplaint) Icons.Default.ReportProblem else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isComplaint) Color(0xFFFCA5A5) else MintPrimaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = displayUserName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val copyValue = ticket.senderPhone.ifBlank { ticket.externalId }
                                            clipboardManager.setText(AnnotatedString(copyValue))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            copiedBadge = true
                                        }
                                        .padding(vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (copiedBadge) {
                                            "✓ تم نسخ ${ticket.senderPhone.ifBlank { ticket.externalId }}"
                                        } else {
                                            buildString {
                                                if (ticket.senderPhone.isNotBlank()) {
                                                    append(ticket.senderPhone)
                                                    append(" • ")
                                                }
                                                append(if (isComplaint) "شكوى (${catEnum.displayName})" else "محادثة مباشرة")
                                                if (ticket.relatedVenueName.isNotBlank()) {
                                                    append(" • ${ticket.relatedVenueName}")
                                                }
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (copiedBadge) MintPrimaryDark else Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        imageVector = if (copiedBadge) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "نسخ البيانات",
                                        tint = if (copiedBadge) MintPrimaryDark else Color.White.copy(alpha = 0.72f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ticket.status != SupportTicketStatus.CLOSED.name) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onMarkInReview()
                                    },
                                    enabled = canReply,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("مراجعة", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCloseTicket()
                                    },
                                    enabled = canReply,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("إغلاق", style = MaterialTheme.typography.labelSmall, color = MintPrimaryDark)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onReopenTicket()
                                    },
                                    enabled = canReply,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("ctrl_support_overlay_reopen_${ticket.id}")
                                ) {
                                    Text("إعادة فتح", style = MaterialTheme.typography.labelSmall, color = MintPrimaryDark)
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

                // Complaint subject banner if complaint
                if (isComplaint && ticket.subject.isNotBlank()) {
                    Surface(
                        color = StatusUrgent.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = StatusUrgent, modifier = Modifier.size(14.dp))
                            Text(
                                text = "موضوع الشكوى: ${ticket.subject}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                                shape = RoundedCornerShape(
                                    topStart = 14.dp,
                                    topEnd = 14.dp,
                                    bottomStart = if (msg.isFromAdmin) 4.dp else 14.dp,
                                    bottomEnd = if (msg.isFromAdmin) 14.dp else 4.dp
                                ),
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
                                            text = if (msg.isFromAdmin) {
                                                "أنت (${msg.senderName.ifBlank { "الدعم" }})"
                                            } else {
                                                displayUserName
                                            },
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Chat Composer Bar or Closed Reopen Bar
                val isClosed = ticket.status == SupportTicketStatus.CLOSED.name
                if (isClosed) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "المحادثة مغلقة — أعد فتحها للرد على المستخدم",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onReopenTicket,
                                enabled = canReply,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("ctrl_support_composer_reopen_${ticket.id}")
                            ) {
                                Text("إعادة فتح", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val quickReplies = remember {
                                listOf(
                                    "أهلاً بك، جاري متابعة طلبك الآن",
                                    "تم حل المشكلة بنجاح، نتمنى لك يوماً سعيداً",
                                    "يرجى تزويدنا برقم التذكرة للمتابعة"
                                )
                            }
                            if (canReply) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(quickReplies.size) { index ->
                                        val snippet = quickReplies[index]
                                        AssistChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                replyDraft = snippet
                                            },
                                            label = {
                                                Text(
                                                    text = snippet,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = EmeraldPrimary.copy(alpha = 0.08f),
                                                labelColor = EmeraldPrimary
                                            ),
                                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
                                            modifier = Modifier
                                                .height(30.dp)
                                                .testTag("ctrl_support_quick_reply_$index")
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = replyDraft,
                                    onValueChange = { replyDraft = it },
                                    placeholder = {
                                        Text(
                                            "اكتب رسالتك إلى $displayUserName...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    enabled = canReply,
                                    minLines = 1,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("ctrl_support_reply_input_${ticket.id}")
                                )
                                IconButton(
                                    onClick = {
                                        val txt = replyDraft.trim()
                                        if (txt.isNotEmpty()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSendReply(txt)
                                            replyDraft = ""
                                        }
                                    },
                                    enabled = canReply && replyDraft.trim().isNotEmpty(),
                                    modifier = Modifier
                                        .size(46.dp)
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .background(
                                            if (canReply && replyDraft.trim().isNotEmpty()) EmeraldPrimary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .testTag("ctrl_support_send_reply_${ticket.id}")
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
        }
    }
}
