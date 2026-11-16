package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookingEntity
import com.example.data.local.DoriDatabase
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.VenueEntity
import com.example.data.model.BookingStatus
import com.example.data.model.LiveTurnAlert
import com.example.data.model.NinevehDistrict
import com.example.data.model.ServiceCategory
import com.example.data.model.SortOption
import com.example.data.model.TicketStage
import com.example.data.model.UserProfileSettings
import com.example.data.repository.DoriRepository
import com.example.util.AlertNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    EXPLORE("الاستكشاف"),
    TICKETS("تذكرتي"),
    MERCHANT("لوحة المحلات"),
    PROFILE("الحساب والولاء")
}

data class DoriUiState(
    val currentTab: AppTab = AppTab.EXPLORE,
    val preferences: UserProfileSettings = UserProfileSettings(),
    val selectedArea: String? = null,
    val availableAreas: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ServiceCategory = ServiceCategory.ALL,
    val showFavoritesOnly: Boolean = false,
    val sortOption: SortOption = SortOption.HIGHEST_RATED,
    val allVenues: List<VenueEntity> = emptyList(),
    val districtVenues: List<VenueEntity> = emptyList(),
    val filteredVenues: List<VenueEntity> = emptyList(),
    val openCountInDistrict: Int = 0,
    val fastestWaitMinutes: Int = 0,
    val averageRatingInDistrict: Double = 0.0,
    val reviewsByVenue: Map<Int, List<ReviewEntity>> = emptyMap(),
    val activeBookings: List<BookingEntity> = emptyList(),
    val selectedActiveBookingId: Long? = null,
    val historyBookings: List<BookingEntity> = emptyList(),
    val selectedMerchantVenueId: Int = 1,
    val queueItemsByVenue: Map<Int, List<MerchantQueueItemEntity>> = emptyMap(),
    val isLiveSimulationRunning: Boolean = false,
    val activeTurnAlert: LiveTurnAlert? = null,
    val selectedVenueForDetails: VenueEntity? = null
)

private data class FilterState(
    val currentTab: AppTab = AppTab.EXPLORE,
    val selectedArea: String? = null,
    val searchQuery: String = "",
    val selectedCategory: ServiceCategory = ServiceCategory.ALL,
    val showFavoritesOnly: Boolean = false,
    val sortOption: SortOption = SortOption.HIGHEST_RATED,
    val selectedActiveBookingId: Long? = null,
    val selectedMerchantVenueId: Int = 1,
    val isLiveSimulationRunning: Boolean = false,
    val activeTurnAlert: LiveTurnAlert? = null,
    val selectedVenueIdForDetails: Int? = null
)

class DoriViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DoriDatabase.getInstance(application)
    private val prefs = UserPreferencesDataStore(application)
    private val repository = DoriRepository(database.doriDao(), prefs)

    private val filterState = MutableStateFlow(FilterState())
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private var simulationJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    val uiState: StateFlow<DoriUiState> = combine(
        filterState,
        repository.userPreferences,
        repository.venues,
        repository.reviews,
        repository.bookings,
        repository.queueItems
    ) { values ->
        val filters = values[0] as FilterState
        val userPrefs = values[1] as UserProfileSettings
        @Suppress("UNCHECKED_CAST")
        val venues = values[2] as List<VenueEntity>
        @Suppress("UNCHECKED_CAST")
        val reviews = values[3] as List<ReviewEntity>
        @Suppress("UNCHECKED_CAST")
        val bookings = values[4] as List<BookingEntity>
        @Suppress("UNCHECKED_CAST")
        val queueItems = values[5] as List<MerchantQueueItemEntity>

        val district = userPrefs.selectedDistrict
        val districtVenues = if (district == NinevehDistrict.ALL_NINEVEH) {
            venues
        } else {
            venues.filter { it.city == district.displayName }
        }

        val availableAreas = if (district == NinevehDistrict.ALL_NINEVEH) {
            NinevehDistrict.selectableDistricts.flatMap { it.areas }.distinct()
        } else {
            district.areas
        }

        val activeArea = filters.selectedArea?.takeIf { it in availableAreas }
        val openDistrictVenues = districtVenues.filter { it.isOpen && it.isAcceptingBookings && !it.isPaused }
        val fastestMinutes = openDistrictVenues.minOfOrNull { it.estimatedWaitMinutes } ?: 0
        val avgRating = if (districtVenues.isNotEmpty()) {
            kotlin.math.round((districtVenues.sumOf { it.rating } / districtVenues.size) * 10.0) / 10.0
        } else 0.0

        val filtered = districtVenues
            .filter { venue ->
                val matchesCategory = filters.selectedCategory == ServiceCategory.ALL ||
                    venue.category == filters.selectedCategory.displayName
                val matchesArea = activeArea == null ||
                    venue.area.contains(activeArea, ignoreCase = true) ||
                    venue.description.contains(activeArea, ignoreCase = true)
                val matchesFav = !filters.showFavoritesOnly || venue.isFavorite
                val q = filters.searchQuery.trim()
                val matchesSearch = q.isEmpty() ||
                    venue.name.contains(q, ignoreCase = true) ||
                    venue.area.contains(q, ignoreCase = true) ||
                    venue.city.contains(q, ignoreCase = true) ||
                    venue.category.contains(q, ignoreCase = true) ||
                    venue.description.contains(q, ignoreCase = true)
                matchesCategory && matchesArea && matchesFav && matchesSearch
            }
            .let { list ->
                when (filters.sortOption) {
                    SortOption.HIGHEST_RATED -> list.sortedByDescending { it.rating }
                    SortOption.SHORTEST_WAIT -> list.sortedBy { it.estimatedWaitMinutes }
                    SortOption.SERVICE_PRICE -> list.sortedWith(
                        compareBy<VenueEntity> { it.category }.thenBy { it.priceDinar }
                    )
                }
            }

        val activeList = bookings.filter { it.status == BookingStatus.ACTIVE.name }
        val historyList = bookings.filter { it.status != BookingStatus.ACTIVE.name }
        val effectiveBookingId = if (activeList.any { it.id == filters.selectedActiveBookingId }) {
            filters.selectedActiveBookingId
        } else {
            activeList.firstOrNull()?.id
        }

        val effectiveMerchantId = if (venues.any { it.id == filters.selectedMerchantVenueId }) {
            filters.selectedMerchantVenueId
        } else {
            districtVenues.firstOrNull()?.id ?: venues.firstOrNull()?.id ?: 1
        }

        val detailVenue = filters.selectedVenueIdForDetails?.let { id -> venues.find { it.id == id } }

        DoriUiState(
            currentTab = filters.currentTab,
            preferences = userPrefs,
            selectedArea = activeArea,
            availableAreas = availableAreas,
            searchQuery = filters.searchQuery,
            selectedCategory = filters.selectedCategory,
            showFavoritesOnly = filters.showFavoritesOnly,
            sortOption = filters.sortOption,
            allVenues = venues,
            districtVenues = districtVenues,
            filteredVenues = filtered,
            openCountInDistrict = openDistrictVenues.size,
            fastestWaitMinutes = fastestMinutes,
            averageRatingInDistrict = avgRating,
            reviewsByVenue = reviews.groupBy { it.venueId },
            activeBookings = activeList,
            selectedActiveBookingId = effectiveBookingId,
            historyBookings = historyList,
            selectedMerchantVenueId = effectiveMerchantId,
            queueItemsByVenue = queueItems.groupBy { it.venueId },
            isLiveSimulationRunning = filters.isLiveSimulationRunning,
            activeTurnAlert = filters.activeTurnAlert,
            selectedVenueForDetails = detailVenue
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DoriUiState()
    )

    fun selectTab(tab: AppTab) {
        filterState.update { it.copy(currentTab = tab) }
    }

    fun selectDistrict(district: NinevehDistrict) {
        viewModelScope.launch {
            repository.setSelectedDistrict(district)
            filterState.update {
                val firstInDistrict = if (district == NinevehDistrict.ALL_NINEVEH) {
                    uiState.value.allVenues.firstOrNull()?.id
                } else {
                    uiState.value.allVenues.firstOrNull { v -> v.city == district.displayName }?.id
                }
                it.copy(
                    selectedArea = null,
                    selectedMerchantVenueId = firstInDistrict ?: it.selectedMerchantVenueId
                )
            }
        }
    }

    fun selectArea(area: String?) {
        filterState.update {
            it.copy(selectedArea = if (it.selectedArea == area) null else area)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            repository.setDarkMode(!uiState.value.preferences.isDarkMode)
        }
    }

    fun updateSearchQuery(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: ServiceCategory) {
        filterState.update { it.copy(selectedCategory = category) }
    }

    fun toggleFavoritesFilter() {
        filterState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun selectSortOption(option: SortOption) {
        filterState.update { it.copy(sortOption = option) }
    }

    fun toggleVenueFavorite(venue: VenueEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(venue.id, venue.isFavorite)
        }
    }

    fun openVenueDetails(venueId: Int?) {
        filterState.update { it.copy(selectedVenueIdForDetails = venueId) }
    }

    fun bookTicket(
        venueId: Int,
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val result = repository.bookQueueTicket(
                venueId = venueId,
                customerName = customerName,
                customerPhone = customerPhone,
                selectedOption = selectedOption,
                notes = notes
            )
            result.onSuccess { booking ->
                filterState.update {
                    it.copy(
                        selectedVenueIdForDetails = null,
                        selectedActiveBookingId = booking.id,
                        currentTab = AppTab.TICKETS,
                        selectedMerchantVenueId = venueId
                    )
                }
                _toastMessage.emit("تم حجز دورك بنجاح (${booking.ticketCode})")
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "تعذر إتمام الحجز")
            }
        }
    }

    fun bookFastestTurn() {
        viewModelScope.launch {
            val district = uiState.value.preferences.selectedDistrict
            val result = repository.bookFastestTurnInDistrict(district)
            result.onSuccess { booking ->
                filterState.update {
                    it.copy(
                        selectedActiveBookingId = booking.id,
                        currentTab = AppTab.TICKETS,
                        selectedMerchantVenueId = booking.venueId
                    )
                }
                _toastMessage.emit("⚡ تم حجز أسرع دور في ${booking.venueName} (${booking.ticketCode})")
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "لا يوجد دور متاح حالياً")
            }
        }
    }

    fun selectActiveBooking(bookingId: Long) {
        filterState.update { it.copy(selectedActiveBookingId = bookingId) }
    }

    fun completeBooking(bookingId: Long, rating: Int?, comment: String?) {
        viewModelScope.launch {
            val earned = repository.completeBookingWithOptionalReview(bookingId, rating, comment)
            _toastMessage.emit("تم إتمام الخدمة بنجاح! حصلت على +$earned نقاط ولاء")
        }
    }

    fun submitHistoryReview(bookingId: Long, rating: Int, comment: String) {
        viewModelScope.launch {
            repository.submitReviewForHistoryBooking(bookingId, rating, comment)
            _toastMessage.emit("شكراً لتقييمك! تم نشر رأيك لأهالي نينوى")
        }
    }

    fun cancelBooking(bookingId: Long) {
        viewModelScope.launch {
            repository.cancelBooking(bookingId)
            _toastMessage.emit("تم إلغاء الحجز ونقله إلى السجل")
        }
    }

    fun quickRebook(historyBooking: BookingEntity) {
        val prefsNow = uiState.value.preferences
        bookTicket(
            venueId = historyBooking.venueId,
            customerName = prefsNow.userName,
            customerPhone = prefsNow.userPhone,
            selectedOption = historyBooking.selectedOption
        )
    }

    // Merchant Dashboard actions
    fun selectMerchantVenue(venueId: Int) {
        filterState.update { it.copy(selectedMerchantVenueId = venueId) }
    }

    fun callNextCustomer(venueId: Int) {
        viewModelScope.launch {
            val alerts = repository.callNextCustomer(venueId)
            handleTurnAlerts(alerts)
        }
    }

    fun skipCurrentCustomer(venueId: Int) {
        viewModelScope.launch {
            val alerts = repository.skipCurrentCustomer(venueId)
            handleTurnAlerts(alerts)
            _toastMessage.emit("تم تخطي العميل الحالي والنداء على التالي")
        }
    }

    fun callCustomerByName(venueId: Int, queueItemId: Long, customerName: String) {
        viewModelScope.launch {
            val alerts = repository.callCustomerByName(venueId, queueItemId)
            handleTurnAlerts(alerts)
            _toastMessage.emit("تم استدعاء $customerName الآن")
        }
    }

    fun markCustomerNoShow(venueId: Int, queueItemId: Long, customerName: String) {
        viewModelScope.launch {
            val alerts = repository.markCustomerNoShow(venueId, queueItemId)
            handleTurnAlerts(alerts)
            _toastMessage.emit("تم تسجيل توقف $customerName عن الحضور")
        }
    }

    fun togglePauseQueue(venueId: Int) {
        viewModelScope.launch {
            repository.togglePauseQueue(venueId)
        }
    }

    fun toggleAcceptingBookings(venueId: Int) {
        viewModelScope.launch {
            repository.toggleAcceptingBookings(venueId)
        }
    }

    fun addWalkIn(venueId: Int, name: String, option: String) {
        viewModelScope.launch {
            repository.addWalkInCustomer(venueId, name, option)
            _toastMessage.emit("تمت إضافة العميل إلى طابور المحل")
        }
    }

    fun toggleLiveSimulation() {
        val currentlyRunning = filterState.value.isLiveSimulationRunning
        if (currentlyRunning) {
            simulationJob?.cancel()
            simulationJob = null
            filterState.update { it.copy(isLiveSimulationRunning = false) }
            viewModelScope.launch { _toastMessage.emit("تم إيقاف المحاكاة التلقائية") }
        } else {
            filterState.update { it.copy(isLiveSimulationRunning = true) }
            simulationJob = viewModelScope.launch {
                _toastMessage.emit("بدأت المحاكاة الحية للطابور مع تنبيهات الصوت والاهتزاز")
                while (isActive) {
                    delay(3500L)
                    val activeVenueId = filterState.value.selectedMerchantVenueId
                    val activeTickets = uiState.value.activeBookings
                    val alerts = if (activeTickets.isNotEmpty()) {
                        repository.advanceActiveUserTicketDemoStep()
                    } else {
                        repository.callNextCustomer(activeVenueId)
                    }
                    handleTurnAlerts(alerts)
                }
            }
        }
    }

    fun dismissTurnAlert() {
        filterState.update { it.copy(activeTurnAlert = null) }
    }

    private fun handleTurnAlerts(alerts: List<LiveTurnAlert>) {
        val mostUrgent = alerts.maxByOrNull { it.stage.stepIndex } ?: return
        filterState.update { it.copy(activeTurnAlert = mostUrgent) }
        val prefsNow = uiState.value.preferences
        AlertNotifier.triggerTurnAlert(
            context = getApplication(),
            muteSound = prefsNow.muteSound,
            muteVibration = prefsNow.muteVibration,
            isUrgent = mostUrgent.stage == TicketStage.YOUR_TURN
        )
    }

    // Profile & local settings
    fun updateProfile(name: String, phone: String, district: NinevehDistrict) {
        viewModelScope.launch {
            repository.updateProfile(name, phone, district)
            _toastMessage.emit("تم حفظ بياناتك وإعدادات القضاء في نينوى محلياً")
        }
    }

    fun setMuteSound(muted: Boolean) {
        viewModelScope.launch { repository.setMuteSound(muted) }
    }

    fun setMuteVibration(muted: Boolean) {
        viewModelScope.launch { repository.setMuteVibration(muted) }
    }
}
