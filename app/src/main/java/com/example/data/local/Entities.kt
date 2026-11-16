package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.BookingStatus
import com.example.data.model.QueueItemStatus
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
    val currentServingNumber: Int = 10,
    val nextTicketNumber: Int = 14,
    val servedTodayCount: Int = 24,
    val avgMinutesPerPerson: Int = 8,
    val isFavorite: Boolean = false,
    val waitingCount: Int = 3
) {
    val customOptions: List<String>
        get() = customOptionsCsv.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    val estimatedWaitMinutes: Int
        get() = if (!isOpen) 999 else (waitingCount.coerceAtLeast(0)) * avgMinutesPerPerson
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
    val priceDinar: Int,
    val status: String = BookingStatus.ACTIVE.name,
    val stage: String = TicketStage.MONITORED.name,
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
