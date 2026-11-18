package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AdminPermissionType
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.BookingStatus
import com.example.data.model.ProviderRole
import com.example.data.model.QueueItemStatus
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicketStatus
import com.example.data.model.TicketStage

@Entity(tableName = "venues")
data class VenueEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val city: String,
    val category: String,
    val specialty: String = "",
    val area: String,
    val priceDinar: Int,
    val rating: Double,
    val reviewCount: Int,
    val workingHours: String,
    val phone: String,
    val whatsapp: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val customOptionsCsv: String,
    val isOpen: Boolean = true,
    val isPaused: Boolean = false,
    val isAcceptingBookings: Boolean = true,
    val currentServingNumber: Int = 0,
    val nextTicketNumber: Int = 1,
    val servedTodayCount: Int = 0,
    val avgMinutesPerPerson: Int = 8,
    val isFavorite: Boolean = false,
    val waitingCount: Int = 0
) {
    val customOptions: List<String>
        get() = customOptionsCsv.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    val queueAheadCount: Int
        get() = waitingCount.coerceAtLeast(0) + if (currentServingNumber > 0) 1 else 0

    val estimatedWaitMinutes: Int
        get() = if (!isOpen) 999 else queueAheadCount * avgMinutesPerPerson
}

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venueId: Int,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketCode: String,
    val queueNumber: Int,
    val venueId: Int,
    val venueName: String,
    val venueCity: String,
    val venueCategory: String,
    val venuePhone: String,
    val customerName: String,
    val customerPhone: String,
    val selectedOption: String,
    val notes: String = "",
    val optionalInfo: String = "",
    val priceDinar: Int,
    val status: String = BookingStatus.ACTIVE.name,
    val stage: String = TicketStage.MONITORED.name,
    val approvalStatus: String = BookingApprovalStatus.PENDING.name,
    val handledByProvider: String = "",
    val approvalNote: String = "",
    val peopleAhead: Int = 2,
    val estimatedMinutes: Int = 16,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val userRating: Int? = null,
    val userReview: String? = null
)

@Entity(tableName = "merchant_queue_items")
data class MerchantQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venueId: Int,
    val ticketNumber: Int,
    val ticketCode: String,
    val customerName: String,
    val customerPhone: String,
    val selectedOption: String,
    val status: String = QueueItemStatus.WAITING.name,
    val bookingId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "support_tickets")
data class SupportTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val senderName: String,
    val senderPhone: String,
    val category: String = SupportCategory.GENERAL.name,
    val subject: String,
    val message: String,
    val status: String = SupportTicketStatus.SENT.name,
    val adminReply: String = "",
    val repliedByAdmin: String = "",
    val syncedToControl: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admin_accounts")
data class AdminAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val roleTitle: String,
    val phone: String = "",
    val isActive: Boolean = true,
    val isSuperAdmin: Boolean = false,
    val canManageFeatures: Boolean = true,
    val canManageVenues: Boolean = true,
    val canManageQueues: Boolean = true,
    val canReplySupport: Boolean = true,
    val canManageProviders: Boolean = true,
    val canManageAdmins: Boolean = false,
    val canRunCommands: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun hasPermission(permission: AdminPermissionType): Boolean {
        if (!isActive) return false
        return when (permission) {
            AdminPermissionType.MANAGE_FEATURES -> canManageFeatures
            AdminPermissionType.MANAGE_VENUES -> canManageVenues
            AdminPermissionType.MANAGE_QUEUES -> canManageQueues
            AdminPermissionType.REPLY_SUPPORT -> canReplySupport
            AdminPermissionType.MANAGE_PROVIDERS -> canManageProviders
            AdminPermissionType.MANAGE_ADMINS -> canManageAdmins
            AdminPermissionType.RUN_COMMANDS -> canRunCommands
        }
    }
}

@Entity(tableName = "service_providers")
data class ServiceProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val providerRole: String = ProviderRole.DOCTOR.name,
    val customTitle: String = ProviderRole.DOCTOR.displayName,
    val phone: String = "",
    val venueId: Int,
    val venueName: String,
    val canAcceptTickets: Boolean = true,
    val canCallNext: Boolean = true,
    val canPauseVenue: Boolean = false,
    val isActive: Boolean = true,
    val acceptedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
