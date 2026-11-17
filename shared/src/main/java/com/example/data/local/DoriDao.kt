package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DoriDao {

    // Venues
    @Query("SELECT * FROM venues ORDER BY rating DESC")
    fun observeAllVenues(): Flow<List<VenueEntity>>

    @Query("SELECT * FROM venues")
    suspend fun getAllVenuesOnce(): List<VenueEntity>

    @Query("SELECT * FROM venues WHERE id = :venueId")
    suspend fun getVenueById(venueId: Int): VenueEntity?

    @Query("SELECT COUNT(*) FROM venues")
    suspend fun getVenueCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenues(venues: List<VenueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenue(venue: VenueEntity)

    @Query("SELECT MAX(id) FROM venues")
    suspend fun getMaxVenueId(): Int?

    @Query("DELETE FROM venues WHERE id = :venueId")
    suspend fun deleteVenueById(venueId: Int)

    @Update
    suspend fun updateVenue(venue: VenueEntity)

    @Query("UPDATE venues SET isFavorite = :isFavorite WHERE id = :venueId")
    suspend fun setFavorite(venueId: Int, isFavorite: Boolean)

    @Transaction
    suspend fun replaceAllVenues(venues: List<VenueEntity>) {
        deleteAllVenues()
        if (venues.isNotEmpty()) insertVenues(venues)
    }

    // Reviews
    @Query("SELECT * FROM reviews ORDER BY createdAt DESC")
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews")
    suspend fun getAllReviewsOnce(): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE venueId = :venueId ORDER BY createdAt DESC")
    fun observeReviewsForVenue(venueId: Int): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<ReviewEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long

    @Transaction
    suspend fun replaceAllReviews(reviews: List<ReviewEntity>) {
        deleteAllReviews()
        if (reviews.isNotEmpty()) insertReviews(reviews)
    }

    // Bookings
    @Query("SELECT * FROM bookings ORDER BY createdAt DESC")
    fun observeAllBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings")
    suspend fun getAllBookingsOnce(): List<BookingEntity>

    @Query("SELECT * FROM bookings WHERE id = :bookingId")
    suspend fun getBookingById(bookingId: Long): BookingEntity?

    @Query("SELECT * FROM bookings WHERE status = 'ACTIVE'")
    suspend fun getActiveBookingsOnce(): List<BookingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: BookingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookings(bookings: List<BookingEntity>)

    @Update
    suspend fun updateBooking(booking: BookingEntity)

    @Query("DELETE FROM bookings")
    suspend fun deleteAllBookings()

    @Transaction
    suspend fun replaceAllBookings(bookings: List<BookingEntity>) {
        deleteAllBookings()
        if (bookings.isNotEmpty()) insertBookings(bookings)
    }

    // Merchant Queue Items
    @Query("SELECT * FROM merchant_queue_items ORDER BY ticketNumber ASC")
    fun observeAllQueueItems(): Flow<List<MerchantQueueItemEntity>>

    @Query("SELECT * FROM merchant_queue_items")
    suspend fun getAllQueueItemsOnce(): List<MerchantQueueItemEntity>

    @Query("SELECT * FROM merchant_queue_items WHERE venueId = :venueId ORDER BY ticketNumber ASC")
    suspend fun getQueueItemsForVenueOnce(venueId: Int): List<MerchantQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<MerchantQueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: MerchantQueueItemEntity): Long

    @Update
    suspend fun updateQueueItem(item: MerchantQueueItemEntity)

    @Query("DELETE FROM merchant_queue_items WHERE venueId = :venueId")
    suspend fun deleteQueueItemsForVenue(venueId: Int)

    @Transaction
    suspend fun replaceAllQueueItems(items: List<MerchantQueueItemEntity>) {
        deleteAllQueueItems()
        if (items.isNotEmpty()) insertQueueItems(items)
    }

    @Query("DELETE FROM venues")
    suspend fun deleteAllVenues()

    @Query("DELETE FROM reviews")
    suspend fun deleteAllReviews()

    @Query("DELETE FROM merchant_queue_items")
    suspend fun deleteAllQueueItems()
}
