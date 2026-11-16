package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DoriDao {

    // Venues
    @Query("SELECT * FROM venues ORDER BY rating DESC")
    fun observeAllVenues(): Flow<List<VenueEntity>>

    @Query("SELECT * FROM venues WHERE id = :venueId")
    suspend fun getVenueById(venueId: Int): VenueEntity?

    @Query("SELECT COUNT(*) FROM venues")
    suspend fun getVenueCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenues(venues: List<VenueEntity>)

    @Update
    suspend fun updateVenue(venue: VenueEntity)

    @Query("UPDATE venues SET isFavorite = :isFavorite WHERE id = :venueId")
    suspend fun setFavorite(venueId: Int, isFavorite: Boolean)

    // Reviews
    @Query("SELECT * FROM reviews ORDER BY createdAt DESC")
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE venueId = :venueId ORDER BY createdAt DESC")
    fun observeReviewsForVenue(venueId: Int): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<ReviewEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long

    // Bookings
    @Query("SELECT * FROM bookings ORDER BY createdAt DESC")
    fun observeAllBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE id = :bookingId")
    suspend fun getBookingById(bookingId: Long): BookingEntity?

    @Query("SELECT * FROM bookings WHERE status = 'ACTIVE'")
    suspend fun getActiveBookingsOnce(): List<BookingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: BookingEntity): Long

    @Update
    suspend fun updateBooking(booking: BookingEntity)

    // Merchant Queue Items
    @Query("SELECT * FROM merchant_queue_items ORDER BY ticketNumber ASC")
    fun observeAllQueueItems(): Flow<List<MerchantQueueItemEntity>>

    @Query("SELECT * FROM merchant_queue_items WHERE venueId = :venueId ORDER BY ticketNumber ASC")
    suspend fun getQueueItemsForVenueOnce(venueId: Int): List<MerchantQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<MerchantQueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: MerchantQueueItemEntity): Long

    @Update
    suspend fun updateQueueItem(item: MerchantQueueItemEntity)
}
