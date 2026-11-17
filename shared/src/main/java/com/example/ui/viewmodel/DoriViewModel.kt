package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookingEntity
import com.example.data.local.DoriDatabase
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.VenueEntity
import com.example.data.model.BookingStatus
import com.example.data.model.LiveTurnAlert
import com.example.data.model.MedicalSpecialty
import com.example.data.model.NinevehDistrict
import com.example.data.model.ServiceCategory
import com.example.data.model.SortOption
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.example.data.remote.DoriCloudSyncRepository
import com.example.data.repository.DoriRepository
import com.example.util.AlertNotifier
import com.example.util.DoriNotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DoriAppMode {
    USER_APP,
    CONTROL_APP
}

enum class AppTab(val label: String) {
    EXPLORE("استكشاف"),
    TICKETS("تذاكر"),
    PROFILE("حساب"),
    CONTROL_FEATURES("المميزات"),
    CONTROL_VENUES("المراكز"),
    CONTROL_QUEUES("الطوابير"),
    CONTROL_COMMANDS("الأوامر");

    companion object {
        val userTabs = listOf(EXPLORE, TICKETS, PROFILE)
        val controlTabs = listOf(CONTROL_FEATURES, CONTROL_VENUES, CONTROL_QUEUES, CONTROL_COMMANDS)
    }
}

data class DoriUiState(
    val appMode: DoriAppMode = DoriAppMode.USER_APP,
    val currentTab: AppTab = AppTab.EXPLORE,
    val preferences: UserProfileSettings = UserProfileSettings(),
    val selectedArea: String? = null,
    val availableAreas: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ServiceCategory = ServiceCategory.ALL,
    val selectedSpecialty: MedicalSpecialty = MedicalSpecialty.ALL,
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
    val selectedVenueForDetails: VenueEntity? = null,
    val bookingSessionId: Long = 0L,
    val newlyBookedTicket: BookingEntity? = null,
    val cloudSyncError: String? = null
)

private data class FilterState(
    val currentTab: AppTab = AppTab.EXPLORE,
    val selectedArea: String? = null,
    val searchQuery: String = "",
    val selectedCategory: ServiceCategory = ServiceCategory.ALL,
    val selectedSpecialty: MedicalSpecialty = MedicalSpecialty.ALL,
    val showFavoritesOnly: Boolean = false,
    val sortOption: SortOption = SortOption.HIGHEST_RATED,
    val selectedActiveBookingId: Long? = null,
    val selectedMerchantVenueId: Int = 1,
    val isLiveSimulationRunning: Boolean = false,
    val activeTurnAlert: LiveTurnAlert? = null,
    val selectedVenueIdForDetails: Int? = null,
    val bookingSessionId: Long = 0L,
    val newlyBookedTicket: BookingEntity? = null,
    val cloudSyncError: String? = null
)

class DoriViewModel(
    application: Application,
    private val appMode: DoriAppMode = DoriAppMode.USER_APP,
    firestore: FirebaseFirestore? = null,
    private val enableCloudSync: Boolean = firestore != null
) : AndroidViewModel(application) {

    private val database = DoriDatabase.getInstance(application)
    private val prefs = UserPreferencesDataStore(application)
    private val cloudSync = firestore?.let { DoriCloudSyncRepository(it) }
    private val repository = DoriRepository(database.doriDao(), prefs, cloudSync)

    private val initialTab = if (appMode == DoriAppMode.CONTROL_APP) {
        AppTab.CONTROL_FEATURES
    } else {
        AppTab.EXPLORE
    }

    private val filterState = MutableStateFlow(FilterState(currentTab = initialTab))
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private var simulationJob: Job? = null
    private var cloudSyncJobs: List<Job> = emptyList()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            if (enableCloudSync && cloudSync != null) {
                repository.bootstrapInitialCloudIfEmpty()
                startCloudSyncListeners()
            }
        }
    }

    private fun startCloudSyncListeners() {
        stopCloudSyncListeners()
        val jobs = mutableListOf<Job>()

        repository.observeRemoteConfig()?.let { flow ->
            jobs += viewModelScope.launch {
                flow.catch { error -> handleCloudFlowError(error) }
                    .collect { remoteSettings ->
                        if (remoteSettings != null) {
                            repository.applyRemoteConfig(remoteSettings)
                        }
                    }
            }
        }

        repository.observeRemoteVenues()?.let { flow ->
            jobs += viewModelScope.launch {
                flow.catch { error -> handleCloudFlowError(error) }
                    .collect { remoteVenues ->
                        repository.applyRemoteVenues(remoteVenues)
                    }
            }
        }

        repository.observeRemoteBookings()?.let { flow ->
            jobs += viewModelScope.launch {
                flow.catch { error -> handleCloudFlowError(error) }
                    .collect { remoteBookings ->
                        repository.applyRemoteBookings(remoteBookings)
                    }
            }
        }

        repository.observeRemoteQueueItems()?.let { flow ->
            jobs += viewModelScope.launch {
                flow.catch { error -> handleCloudFlowError(error) }
                    .collect { remoteItems ->
                        repository.applyRemoteQueueItems(remoteItems)
                    }
            }
        }

        repository.observeReviewsOrNull()?.let { flow ->
            jobs += viewModelScope.launch {
                flow.catch { error -> handleCloudFlowError(error) }
                    .collect { remoteReviews ->
                        repository.applyRemoteReviews(remoteReviews)
                    }
            }
        }

        cloudSyncJobs = jobs
    }

    private fun DoriRepository.observeReviewsOrNull() = observeRemoteReviews()

    private fun handleCloudFlowError(error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val message = (firestoreError ?: error).localizedMessage ?: "تعذرت المزامنة مع السحابة"
        filterState.update { it.copy(cloudSyncError = message) }
    }

    fun stopCloudSyncListeners() {
        cloudSyncJobs.forEach { it.cancel() }
        cloudSyncJobs = emptyList()
    }

    override fun onCleared() {
        stopCloudSyncListeners()
        simulationJob?.cancel()
        super.onCleared()
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
            emptyList()
        } else {
            district.areas
        }

        val disabledCategories = userPrefs.controlConfig.disabledCategories
        val enabledDistrictVenues = districtVenues.filter { it.category !in disabledCategories }
        val activeArea = filters.selectedArea?.takeIf { it in availableAreas }
        val openDistrictVenues = enabledDistrictVenues.filter { it.isOpen && it.isAcceptingBookings && !it.isPaused }
        val fastestMinutes = openDistrictVenues.minOfOrNull { it.estimatedWaitMinutes } ?: 0
        val avgRating = if (enabledDistrictVenues.isNotEmpty()) {
            kotlin.math.round((enabledDistrictVenues.sumOf { it.rating } / enabledDistrictVenues.size) * 10.0) / 10.0
        } else 0.0

        val filtered = enabledDistrictVenues
            .filter { venue ->
                val matchesCategory = filters.selectedCategory == ServiceCategory.ALL ||
                    venue.category == filters.selectedCategory.displayName
                val matchesSpecialty = filters.selectedCategory != ServiceCategory.CLINICS ||
                    filters.selectedSpecialty == MedicalSpecialty.ALL ||
                    venue.specialty == filters.selectedSpecialty.displayName
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
                    venue.specialty.contains(q, ignoreCase = true) ||
                    venue.description.contains(q, ignoreCase = true)
                matchesCategory && matchesSpecialty && matchesArea && matchesFav && matchesSearch
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
            appMode = appMode,
            currentTab = filters.currentTab,
            preferences = userPrefs,
            selectedArea = activeArea,
            availableAreas = availableAreas,
            searchQuery = filters.searchQuery,
            selectedCategory = filters.selectedCategory,
            selectedSpecialty = filters.selectedSpecialty,
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
            selectedVenueForDetails = detailVenue,
            bookingSessionId = filters.bookingSessionId,
            newlyBookedTicket = filters.newlyBookedTicket,
            cloudSyncError = filters.cloudSyncError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DoriUiState(appMode = appMode, currentTab = initialTab)
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
        filterState.update {
            it.copy(
                selectedCategory = category,
                selectedSpecialty = if (category == ServiceCategory.CLINICS) it.selectedSpecialty else MedicalSpecialty.ALL
            )
        }
    }

    fun selectMedicalSpecialty(specialty: MedicalSpecialty) {
        filterState.update { it.copy(selectedSpecialty = specialty) }
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
        filterState.update {
            it.copy(
                selectedVenueIdForDetails = venueId,
                bookingSessionId = if (venueId != null) it.bookingSessionId + 1L else it.bookingSessionId
            )
        }
    }

    fun quickBookVenue(venue: VenueEntity) {
        viewModelScope.launch {
            if (!uiState.value.preferences.controlConfig.allowNewBookings) {
                _toastMessage.emit("تم إيقاف الحجوزات مؤقتاً من لوحة التحكم")
                return@launch
            }
            if (!venue.isOpen || !venue.isAcceptingBookings) {
                _toastMessage.emit("هذا المكان متوقف عن استقبال الحجوزات حالياً")
                return@launch
            }
            openVenueDetails(venue.id)
        }
    }

    fun bookTicket(
        venueId: Int,
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String = "",
        optionalInfo: String = "",
        customPriceDinar: Int? = null
    ) {
        viewModelScope.launch {
            val result = repository.bookQueueTicket(
                venueId = venueId,
                customerName = customerName,
                customerPhone = customerPhone,
                selectedOption = selectedOption,
                notes = notes,
                optionalInfo = optionalInfo,
                customPriceDinar = customPriceDinar
            )
            result.onSuccess { booking ->
                if (uiState.value.preferences.controlConfig.enableLiveAlerts) {
                    DoriNotificationHelper.showBookingCreatedNotification(getApplication(), booking)
                }
                filterState.update {
                    it.copy(
                        selectedVenueIdForDetails = null,
                        selectedActiveBookingId = booking.id,
                        newlyBookedTicket = booking,
                        currentTab = AppTab.TICKETS,
                        selectedMerchantVenueId = venueId
                    )
                }
                _toastMessage.emit("تم إصدار التكت #${booking.queueNumber} (${booking.ticketCode})")
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "تعذر إتمام الحجز")
            }
        }
    }

    fun bookFastestTurn() {
        viewModelScope.launch {
            val ctrl = uiState.value.preferences.controlConfig
            if (!ctrl.allowNewBookings) {
                _toastMessage.emit("تم إيقاف الحجوزات مؤقتاً من لوحة التحكم")
                return@launch
            }
            val district = uiState.value.preferences.selectedDistrict
            val disabled = ctrl.disabledCategories
            val fastest = uiState.value.districtVenues
                .filter { it.category !in disabled && it.isOpen && it.isAcceptingBookings && !it.isPaused }
                .minByOrNull { it.estimatedWaitMinutes }
                ?: repository.findFastestVenueInDistrict(district)?.takeIf { it.category !in disabled }
            if (fastest != null) {
                openVenueDetails(fastest.id)
                _toastMessage.emit("⚡ أسرع دور متاح في ${fastest.name} — أدخل معلومات الحجز")
            } else {
                _toastMessage.emit("لا توجد أماكن متاحة للحجز السريع في ${district.displayName} حالياً")
            }
        }
    }

    fun dismissNewlyBookedTicket() {
        filterState.update { it.copy(newlyBookedTicket = null) }
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
        openVenueDetails(historyBooking.venueId)
        viewModelScope.launch {
            _toastMessage.emit("أدخل معلومات الحجز الجديد لنفس اليوم")
        }
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
        val prefsNow = uiState.value.preferences
        if (!prefsNow.controlConfig.enableLiveAlerts) return
        val mostUrgent = alerts.maxByOrNull { it.stage.stepIndex } ?: return
        filterState.update { it.copy(activeTurnAlert = mostUrgent) }
        DoriNotificationHelper.showTurnAlertNotification(getApplication(), mostUrgent)
        AlertNotifier.triggerTurnAlert(
            context = getApplication(),
            muteSound = prefsNow.muteSound,
            muteVibration = prefsNow.muteVibration,
            isUrgent = mostUrgent.stage == TicketStage.YOUR_TURN
        )
    }

    // ==================== Control Panel Commands ====================

    fun updateControlConfig(transform: (UserAppControlConfig) -> UserAppControlConfig) {
        viewModelScope.launch {
            repository.updateControlConfig(transform)
        }
    }

    fun toggleCategoryEnabled(category: ServiceCategory) {
        if (category == ServiceCategory.ALL) return
        viewModelScope.launch {
            repository.updateControlConfig { cfg ->
                val next = cfg.disabledCategories.toMutableSet()
                if (category.displayName in next) {
                    next.remove(category.displayName)
                } else {
                    next.add(category.displayName)
                }
                cfg.copy(disabledCategories = next)
            }
            if (filterState.value.selectedCategory == category) {
                filterState.update { it.copy(selectedCategory = ServiceCategory.ALL) }
            }
        }
    }

    fun setBroadcastMessage(message: String) {
        viewModelScope.launch {
            repository.updateControlConfig { it.copy(broadcastMessage = message.trim()) }
            _toastMessage.emit(
                if (message.isBlank()) "تم مسح رسالة الإعلان العام"
                else "تم نشر الإعلان لنسخة المستخدم"
            )
        }
    }

    fun addCustomVenue(
        name: String,
        district: NinevehDistrict,
        area: String,
        category: ServiceCategory,
        specialty: String,
        priceDinar: Int,
        avgMinutes: Int,
        phone: String
    ) {
        viewModelScope.launch {
            repository.addCustomVenue(name, district, area, category, specialty, priceDinar, avgMinutes, phone)
                .onSuccess { venue ->
                    _toastMessage.emit("تمت إضافة \"${venue.name}\" إلى نسخة المستخدم")
                }
                .onFailure { err ->
                    _toastMessage.emit(err.message ?: "تعذر إضافة المركز")
                }
        }
    }

    fun toggleVenueOpen(venueId: Int) {
        viewModelScope.launch { repository.toggleVenueOpen(venueId) }
    }

    fun adjustVenuePrice(venueId: Int, deltaDinar: Int) {
        viewModelScope.launch { repository.adjustVenuePrice(venueId, deltaDinar) }
    }

    fun adjustVenueWaitMinutes(venueId: Int, deltaMinutes: Int) {
        viewModelScope.launch { repository.adjustVenueWaitMinutes(venueId, deltaMinutes) }
    }

    fun deleteVenue(venueId: Int) {
        viewModelScope.launch {
            repository.deleteVenue(venueId)
            _toastMessage.emit("تم حذف المركز من القائمة")
        }
    }

    fun setDistrictVenuesOpen(open: Boolean) {
        viewModelScope.launch {
            val district = uiState.value.preferences.selectedDistrict
            repository.setAllVenuesOpenInDistrict(district, open)
            _toastMessage.emit(
                if (open) "تم فتح جميع مراكز ${district.displayName}"
                else "تم إغلاق جميع مراكز ${district.displayName} مؤقتاً"
            )
        }
    }

    fun advanceAllQueuesNow() {
        viewModelScope.launch {
            val alerts = repository.advanceActiveUserTicketDemoStep()
            handleTurnAlerts(alerts)
            _toastMessage.emit("تم تقديم جميع الأدوار النشطة خطوة للأمام")
        }
    }

    fun completeAllActiveBookings() {
        viewModelScope.launch {
            val count = repository.completeAllActiveBookings()
            _toastMessage.emit("تم إتمام $count تذكرة نشطة")
        }
    }

    fun cancelAllActiveBookings() {
        viewModelScope.launch {
            val count = repository.cancelAllActiveBookings()
            _toastMessage.emit("تم إلغاء $count تذكرة نشطة")
        }
    }

    fun resetAllQueuesToOne() {
        viewModelScope.launch {
            repository.resetAllQueuesToOne()
            _toastMessage.emit("تم تصفير جميع الطوابير والبدء من التذكرة #1")
        }
    }

    fun adjustLoyaltyPoints(delta: Int) {
        viewModelScope.launch {
            repository.adjustLoyaltyPoints(delta)
            _toastMessage.emit("تم تحديث نقاط الولاء")
        }
    }

    fun resetLoyaltyPoints() {
        viewModelScope.launch {
            repository.resetLoyaltyPoints()
            _toastMessage.emit("تم تصفير نقاط الولاء")
        }
    }

    fun factoryResetAllData() {
        viewModelScope.launch {
            simulationJob?.cancel()
            simulationJob = null
            filterState.update { it.copy(isLiveSimulationRunning = false, activeTurnAlert = null) }
            repository.factoryResetAllData()
            _toastMessage.emit("تمت استعادة الإعدادات والبيانات الافتراضية بالكامل")
        }
    }

    fun updateProfile(
        name: String,
        phone: String,
        district: NinevehDistrict,
        age: String = "",
        optionalNote: String = ""
    ) {
        viewModelScope.launch {
            repository.updateProfile(name.trim(), phone.trim(), district, age.trim(), optionalNote.trim())
            _toastMessage.emit("تم حفظ معلوماتك محلياً")
        }
    }

    fun setMuteSound(muted: Boolean) {
        viewModelScope.launch { repository.setMuteSound(muted) }
    }

    fun setMuteVibration(muted: Boolean) {
        viewModelScope.launch { repository.setMuteVibration(muted) }
    }
}

class DoriViewModelFactory(
    private val application: Application,
    private val appMode: DoriAppMode,
    private val firestore: FirebaseFirestore?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DoriViewModel::class.java)) {
            return DoriViewModel(
                application = application,
                appMode = appMode,
                firestore = firestore,
                enableCloudSync = firestore != null
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
