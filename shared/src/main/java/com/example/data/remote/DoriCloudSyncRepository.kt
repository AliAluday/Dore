package com.example.data.remote

import com.example.data.local.BookingEntity
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.VenueEntity
import com.example.data.model.UserProfileSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DoriCloudSyncRepository(
    private val db: FirebaseFirestore
) {
    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً للمزامنة مع السحابة")
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun configDoc(uid: String) = userDoc(uid).collection("config").document("settings")
    private fun venuesCol(uid: String) = userDoc(uid).collection("venues")
    private fun bookingsCol(uid: String) = userDoc(uid).collection("bookings")
    private fun queueCol(uid: String) = userDoc(uid).collection("queue_items")
    private fun reviewsCol(uid: String) = userDoc(uid).collection("reviews")

    fun observeConfig(): Flow<UserProfileSettings?> = flow {
        val uid = requireUserId()
        val path = "users/$uid/config/settings"
        emitAll(
            configDoc(uid).snapshots()
                .map { snapshot -> CloudConfig.fromDocument(snapshot)?.toUserProfileSettings() }
                .catch { error ->
                    unwrapAndLog(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeVenues(): Flow<List<VenueEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/venues"
        emitAll(
            venuesCol(uid).snapshots()
                .map { snapshot -> snapshot.documents.mapNotNull { CloudVenue.fromDocument(it) } }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeBookings(): Flow<List<BookingEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/bookings"
        emitAll(
            bookingsCol(uid).snapshots()
                .map { snapshot -> snapshot.documents.mapNotNull { CloudBookingMapper.fromDocument(it) } }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeQueueItems(): Flow<List<MerchantQueueItemEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/queue_items"
        emitAll(
            queueCol(uid).snapshots()
                .map { snapshot -> snapshot.documents.mapNotNull { CloudQueueItemMapper.fromDocument(it) } }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeReviews(): Flow<List<ReviewEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/reviews"
        emitAll(
            reviewsCol(uid).snapshots()
                .map { snapshot -> snapshot.documents.mapNotNull { CloudReviewMapper.fromDocument(it) } }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun hasRemoteVenues(): Result<Boolean> {
        var path = "users/unknown/venues"
        return try {
            val uid = requireUserId()
            path = "users/$uid/venues"
            val snap = venuesCol(uid).limit(1).get().await()
            Result.success(!snap.isEmpty)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun upsertConfig(settings: UserProfileSettings): Result<Unit> {
        var path = "users/unknown/config/settings"
        return try {
            val uid = requireUserId()
            val ref = configDoc(uid)
            path = ref.path
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.update(CloudConfig.toUpdateMap(settings)).await()
            } else {
                ref.set(CloudConfig.toCreateMap(uid, settings)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertVenue(venue: VenueEntity): Result<Unit> {
        var path = "users/unknown/venues/venue_${venue.id}"
        return try {
            val uid = requireUserId()
            val ref = venuesCol(uid).document("venue_${venue.id}")
            path = ref.path
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.update(CloudVenue.toUpdateMap(venue)).await()
            } else {
                ref.set(CloudVenue.toCreateMap(uid, venue)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertVenuesBatch(venues: List<VenueEntity>): Result<Unit> {
        if (venues.isEmpty()) return Result.success(Unit)
        var path = "users/unknown/venues"
        return try {
            val uid = requireUserId()
            path = "users/$uid/venues"
            val existingIds = venuesCol(uid).get().await().documents.map { it.id }.toSet()
            for (chunk in venues.chunked(250)) {
                val batch = db.batch()
                for (venue in chunk) {
                    val docId = "venue_${venue.id}"
                    val ref = venuesCol(uid).document(docId)
                    if (docId in existingIds) {
                        batch.update(ref, CloudVenue.toUpdateMap(venue))
                    } else {
                        batch.set(ref, CloudVenue.toCreateMap(uid, venue))
                    }
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVenue(venueId: Int): Result<Unit> {
        var path = "users/unknown/venues/venue_$venueId"
        return try {
            val uid = requireUserId()
            val ref = venuesCol(uid).document("venue_$venueId")
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertBooking(booking: BookingEntity): Result<Unit> {
        var path = "users/unknown/bookings/booking_${booking.id}"
        return try {
            val uid = requireUserId()
            val ref = bookingsCol(uid).document("booking_${booking.id}")
            path = ref.path
            val existing = ref.get().await()
            if (existing.exists()) {
                val currentStatus = existing.getString("status")
                if (currentStatus == "ACTIVE") {
                    ref.update(CloudBookingMapper.toActiveUpdateMap(booking)).await()
                } else {
                    val r = booking.userRating
                    val c = booking.userReview
                    if (r != null) {
                        ref.update(CloudBookingMapper.toHistoryReviewUpdateMap(r, c ?: "تجربة رائعة")).await()
                    }
                }
            } else {
                ref.set(CloudBookingMapper.toCreateMap(uid, booking)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertQueueItem(item: MerchantQueueItemEntity): Result<Unit> {
        var path = "users/unknown/queue_items/q_${item.id}"
        return try {
            val uid = requireUserId()
            val ref = queueCol(uid).document("q_${item.id}")
            path = ref.path
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.update(CloudQueueItemMapper.toUpdateMap(item)).await()
            } else {
                ref.set(CloudQueueItemMapper.toCreateMap(uid, item)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertQueueItemsBatch(items: List<MerchantQueueItemEntity>): Result<Unit> {
        if (items.isEmpty()) return Result.success(Unit)
        var path = "users/unknown/queue_items"
        return try {
            val uid = requireUserId()
            path = "users/$uid/queue_items"
            val existingIds = queueCol(uid).get().await().documents.map { it.id }.toSet()
            for (chunk in items.chunked(250)) {
                val batch = db.batch()
                for (item in chunk) {
                    val docId = "q_${item.id}"
                    val ref = queueCol(uid).document(docId)
                    if (docId in existingIds) {
                        batch.update(ref, CloudQueueItemMapper.toUpdateMap(item))
                    } else {
                        batch.set(ref, CloudQueueItemMapper.toCreateMap(uid, item))
                    }
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertReviewsBatch(reviews: List<ReviewEntity>): Result<Unit> {
        if (reviews.isEmpty()) return Result.success(Unit)
        var path = "users/unknown/reviews"
        return try {
            val uid = requireUserId()
            path = "users/$uid/reviews"
            val existingIds = reviewsCol(uid).get().await().documents.map { it.id }.toSet()
            for (chunk in reviews.chunked(250)) {
                val batch = db.batch()
                for (rev in chunk) {
                    val docId = "rev_${rev.id}"
                    val ref = reviewsCol(uid).document(docId)
                    if (docId !in existingIds) {
                        batch.set(ref, CloudReviewMapper.toCreateMap(uid, rev))
                    }
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteQueueItemsForVenue(venueId: Int): Result<Unit> {
        var path = "users/unknown/queue_items"
        return try {
            val uid = requireUserId()
            path = "users/$uid/queue_items"
            val snap = queueCol(uid).whereEqualTo("venueId", venueId).get().await()
            for (chunk in snap.documents.chunked(250)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllUserCollections(
        clearVenues: Boolean,
        clearReviews: Boolean,
        clearBookings: Boolean,
        clearQueueItems: Boolean
    ): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val cols = buildList {
                if (clearVenues) add(venuesCol(uid))
                if (clearReviews) add(reviewsCol(uid))
                if (clearBookings) add(bookingsCol(uid))
                if (clearQueueItems) add(queueCol(uid))
            }
            for (col in cols) {
                val docs = col.get().await().documents
                for (chunk in docs.chunked(250)) {
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun unwrapAndLog(
        error: Throwable,
        operation: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operation, path)
        }
    }
}
