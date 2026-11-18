package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SupportTicketEntity
import com.example.data.model.AdminPermissionType
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicketStatus
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.MintPrimaryDark
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
    val canReply = state.activeAdmin?.hasPermission(AdminPermissionType.REPLY_SUPPORT) ?: true

    val filteredTickets = remember(state.supportTickets, filterIndex) {
        when (filterIndex) {
            1 -> state.supportTickets.filter {
                it.status == SupportTicketStatus.SENT.name ||
                    it.status == SupportTicketStatus.DELIVERED.name ||
                    it.status == SupportTicketStatus.IN_REVIEW.name
            }
            2 -> state.supportTickets.filter { it.status == SupportTicketStatus.REPLIED.name }
            3 -> state.supportTickets.filter { it.status == SupportTicketStatus.CLOSED.name }
            else -> state.supportTickets
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gradient Support Header
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = MintPrimaryDark,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = "صندوق الدعم الفني (${state.supportTickets.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (state.supportAlertCount > 0) {
                                            "${state.supportAlertCount} رسالة بانتظار الرد أو المراجعة"
                                        } else {
                                            "الرسائل الواردة مباشرة من تطبيق دوري"
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
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("ctrl_support_sync_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("سحب الرسائل", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Filter Pills
            item {
                val filterLabels = listOf("الكل", "جديدة وبانتظار الرد", "تم الرد", "مغلقة")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filterLabels.size) { idx ->
                        FilterChip(
                            selected = filterIndex == idx,
                            onClick = { filterIndex = idx },
                            label = { Text(filterLabels[idx]) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (filteredTickets.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MarkChatUnread,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "لا توجد رسائل دعم فني في هذا القسم",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "عندما يرسل المستخدم رسالة من تطبيق دوري ستصل هنا تلقائياً أو عند الضغط على سحب الرسائل.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTickets, key = { it.id }) { ticket ->
                    ControlSupportTicketCard(
                        ticket = ticket,
                        canReply = canReply,
                        onReply = { text -> onReplyTicket(ticket.id, text) },
                        onMarkInReview = { onUpdateStatus(ticket.id, SupportTicketStatus.IN_REVIEW) },
                        onClose = { onUpdateStatus(ticket.id, SupportTicketStatus.CLOSED) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlSupportTicketCard(
    ticket: SupportTicketEntity,
    canReply: Boolean,
    onReply: (String) -> Unit,
    onMarkInReview: () -> Unit,
    onClose: () -> Unit
) {
    var replyDraft by remember(ticket.id, ticket.adminReply) { mutableStateOf(ticket.adminReply) }
    val statusEnum = SupportTicketStatus.fromName(ticket.status)
    val catEnum = SupportCategory.fromName(ticket.category)
    val statusColor = when (statusEnum) {
        SupportTicketStatus.REPLIED -> StatusOpen
        SupportTicketStatus.DELIVERED, SupportTicketStatus.SENT -> IndigoAccentLight
        SupportTicketStatus.IN_REVIEW -> AmberGold
        SupportTicketStatus.CLOSED -> StatusPaused
    }
    val dateLabel = remember(ticket.createdAt) {
        SimpleDateFormat("HH:mm • dd/MM", Locale("ar")).format(Date(ticket.createdAt))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ctrl_support_card_${ticket.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "${ticket.senderName}${if (ticket.senderPhone.isNotBlank()) " • ${ticket.senderPhone}" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${catEnum.displayName} • $dateLabel",
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
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = ticket.subject,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = ticket.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (ticket.adminReply.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = StatusOpen, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "آخر رد بواسطة ${ticket.repliedByAdmin}:",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusOpen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                label = { Text("اكتب الرد للمستخدم...") },
                enabled = canReply,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ctrl_support_reply_input_${ticket.id}")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onReply(replyDraft) },
                    enabled = canReply && replyDraft.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("ctrl_support_send_reply_${ticket.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال الرد")
                }

                if (statusEnum != SupportTicketStatus.CLOSED) {
                    OutlinedButton(
                        onClick = onMarkInReview,
                        enabled = canReply,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("مراجعة", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onClose,
                        enabled = canReply,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "إغلاق", modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}
