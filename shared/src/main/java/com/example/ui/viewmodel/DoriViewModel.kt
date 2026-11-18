package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bridge.DoriLocalBridgeClient
import com.example.data.local.AdminAccountEntity
import com.example.data.local.BookingEntity
import com.example.data.local.DoriDatabase
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.VenueEntity
import com.example.data.model.AdminPermissionType
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.BookingStatus
import com.example.data.model.LiveTurnAlert
import com.example.data.model.MedicalSpecialty
import com.example.data.model.NinevehDistrict
import com.example.data.model.ProviderRole
import com.example.data.model.ServiceCategory
import com.example.data.model.SortOption
import com.example.data.model.SupportCategory
import com.example.data.model.SupportRequestType
import com.example.data.model.SupportTicketStatus
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.example.data.repository.DoriRepository
import com.example.util.AlertNotifier
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    CONTROL_TEAM("الطاقم"),
    CONTROL_SUPPORT("الدعم");

    companion object {
        val userTabs = listOf(EXPLORE, TICKETS, PROFILE)
        val controlTabs = listOf(CONTROL_FEATURES, CONTROL_VENUES, CONTROL_QUEUES, CONTROL_TEAM, CONTROL_SUPPORT)
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
    val pendingApprovalBookings: List<BookingEntity> = emptyList(),
    val selectedActiveBookingId: Long? = null,
    val historyBookings: List<BookingEntity> = emptyList(),
    val selectedMerchantVenueId: Int = 1,
    val queueItemsByVenue: Map<Int, List<MerchantQueueItemEntity>> = emptyMap(),
    val supportTickets: List<SupportTicketEntity> = emptyList(),
    val supportMessagesByTicket: Map<String, List<SupportMessageEntity>> = emptyMap(),
    val supportAlertCount: Int = 0,
    val admins: List<AdminAccountEntity> = emptyList(),
    val activeAdmin: AdminAccountEntity? = null,
    val providers: List<ServiceProviderEntity> = emptyList(),
    val activeProvider: ServiceProviderEntity? = null,
    val isLiveSimulationRunning: Boolean = false,
    val activeTurnAlert: LiveTurnAlert? = null,
    val selectedVenueForDetails: VenueEntity? = null,
    val bookingSessionId: Long = 0L,
    val newlyBookedTicket: BookingEntity? = null
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
    val newlyBookedTicket: BookingEntity? = null
)

class DoriViewModel(
    application: Application,
    private val appMode: DoriAppMode = DoriAppMode.USER_APP
) : AndroidViewModel(application) {

    private val database = DoriDatabase.getInstance(application)
    private val prefs = UserPreferencesDataStore(application)
    private val repository = DoriRepository(database.doriDao(), prefs)

    private val initialTab = if (appMode == DoriAppMode.CONTROL_APP) {
        AppTab.CONTROL_FEATURES
    } else {
        AppTab.EXPLORE
    }

    private val filterState = MutableStateFlow(FilterState(currentTab = initialTab))
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()
    private val bridgeSyncMutex = Mutex()

    private var simulationJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            syncLocalBridgeInternal(silent = true)
        }
    }

    override fun onCleared() {
        simulationJob?.cancel()
        super.onCleared()
    }

    val uiState: StateFlow<DoriUiState> = combine(
        filterState,
        repository.userPreferences,
        repository.venues,
        repository.reviews,
        repository.bookings,
        repository.queueItems,
        repository.supportTickets,
        repository.admins,
        repository.providers,
        repository.supportMessages
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
        @Suppress("UNCHECKED_CAST")
        val supportTickets = values[6] as List<SupportTicketEntity>
        @Suppress("UNCHECKED_CAST")
        val admins = values[7] as List<AdminAccountEntity>
        @Suppress("UNCHECKED_CAST")
        val providers = values[8] as List<ServiceProviderEntity>
        @Suppress("UNCHECKED_CAST")
        val supportMessages = values[9] as List<SupportMessageEntity>

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
        val effectiveCategory = if (
            filters.selectedCategory != ServiceCategory.ALL &&
            filters.selectedCategory.displayName in disabledCategories
        ) {
            ServiceCategory.ALL
        } else {
            filters.selectedCategory
        }
        val enabledDistrictVenues = districtVenues.filter { it.category !in disabledCategories }
        val activeArea = filters.selectedArea?.takeIf { it in availableAreas }
        val openDistrictVenues = enabledDistrictVenues.filter { it.isOpen && it.isAcceptingBookings && !it.isPaused }
        val fastestMinutes = openDistrictVenues.minOfOrNull { it.estimatedWaitMinutes } ?: 0
        val avgRating = if (enabledDistrictVenues.isNotEmpty()) {
            kotlin.math.round((enabledDistrictVenues.sumOf { it.rating } / enabledDistrictVenues.size) * 10.0) / 10.0
        } else 0.0

        val filtered = enabledDistrictVenues
            .filter { venue ->
                val matchesCategory = effectiveCategory == ServiceCategory.ALL ||
                    venue.category == effectiveCategory.displayName
                val matchesSpecialty = effectiveCategory != ServiceCategory.CLINICS ||
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
        val pendingApprovals = activeList.filter { it.approvalStatus == BookingApprovalStatus.PENDING.name }
        val historyList = bookings.filter { it.status != BookingStatus.ACTIVE.name }
        val effectiveBookingId = if (activeList.any { it.id == filters.selectedActiveBookingId }) {
            filters.selectedActiveBookingId
        } else {
            activeList.firstOrNull()?.id
        }

        val activeAdmin = admins.find { it.id == userPrefs.activeAdminId }
            ?: admins.firstOrNull()
        val activeProvider = providers.find { it.id == userPrefs.activeProviderId && it.isActive }

        val effectiveMerchantId = if (activeProvider != null && venues.any { it.id == activeProvider.venueId }) {
            activeProvider.venueId
        } else if (venues.any { it.id == filters.selectedMerchantVenueId }) {
            filters.selectedMerchantVenueId
        } else {
            districtVenues.firstOrNull()?.id ?: venues.firstOrNull()?.id ?: 1
        }

        val supportBadgeCount = if (appMode == DoriAppMode.CONTROL_APP) {
            supportTickets.count {
                it.status == SupportTicketStatus.SENT.name ||
                    it.status == SupportTicketStatus.DELIVERED.name ||
                    it.status == SupportTicketStatus.IN_REVIEW.name
            }
        } else {
            supportTickets.count { it.status == SupportTicketStatus.REPLIED.name }
        }

        val detailVenue = filters.selectedVenueIdForDetails?.let { id -> venues.find { it.id == id } }

        DoriUiState(
            appMode = appMode,
            currentTab = filters.currentTab,
            preferences = userPrefs,
            selectedArea = activeArea,
            availableAreas = availableAreas,
            searchQuery = filters.searchQuery,
            selectedCategory = effectiveCategory,
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
            pendingApprovalBookings = pendingApprovals,
            selectedActiveBookingId = effectiveBookingId,
            historyBookings = historyList,
            selectedMerchantVenueId = effectiveMerchantId,
            queueItemsByVenue = queueItems.groupBy { it.venueId },
            supportTickets = supportTickets,
            supportMessagesByTicket = supportMessages.groupBy { it.ticketExternalId },
            supportAlertCount = supportBadgeCount,
            admins = admins,
            activeAdmin = activeAdmin,
            providers = providers,
            activeProvider = activeProvider,
            isLiveSimulationRunning = filters.isLiveSimulationRunning,
            activeTurnAlert = filters.activeTurnAlert,
            selectedVenueForDetails = detailVenue,
            bookingSessionId = filters.bookingSessionId,
            newlyBookedTicket = filters.newlyBookedTicket
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DoriUiState(appMode = appMode, currentTab = initialTab)
    )

    private suspend fun requireAdminPermission(permission: AdminPermissionType): Boolean {
        if (appMode != DoriAppMode.CONTROL_APP) return true
        val admin = uiState.value.activeAdmin ?: return true
        if (admin.hasPermission(permission)) return true
        _toastMessage.emit("حساب (${admin.name}) لا يملك صلاحية: ${permission.label}")
        return false
    }

    fun selectTab(tab: AppTab) {
        filterState.update { it.copy(currentTab = tab) }
        viewModelScope.launch { syncLocalBridgeInternal(silent = true) }
    }

    fun syncLocalBridge(silent: Boolean = false) {
        viewModelScope.launch {
            syncLocalBridgeInternal(silent = silent)
        }
    }

    private suspend fun pushControlSnapshotToUserApp(includeBookings: Boolean = false) {
        if (appMode != DoriAppMode.CONTROL_APP) return
        val ctx = getApplication<Application>()
        withContext(Dispatchers.IO) {
            if (includeBookings) {
                repository.getAllBookingsSnapshot().forEach { b ->
                    DoriLocalBridgeClient.pushBookingDecisionToUser(
                        context = ctx,
                        ticketCode = b.ticketCode,
                        approvalStatus = b.approvalStatus,
                        handledByProvider = b.handledByProvider,
                        approvalNote = b.approvalNote,
                        status = b.status,
                        stage = b.stage,
                        peopleAhead = b.peopleAhead,
                        estimatedMinutes = b.estimatedMinutes
                    )
                }
            }
            val (payload, venues, providers) = repository.getControlBridgeSnapshot()
            DoriLocalBridgeClient.pushVenuesToUser(ctx, venues)
            DoriLocalBridgeClient.pushProvidersToUser(ctx, providers)
            DoriLocalBridgeClient.pushControlStateToUser(ctx, payload)
        }
    }

    private suspend fun syncLocalBridgeInternal(silent: Boolean) = bridgeSyncMutex.withLock {
        val ctx = getApplication<Application>()
        val isCtrl = appMode == DoriAppMode.CONTROL_APP
        val changedCount = withContext(Dispatchers.IO) {
            var count = 0
            val remoteTickets = DoriLocalBridgeClient.pullSupportTicketsFromPeer(ctx, isCtrl)
            count += repository.mergePulledSupportTickets(remoteTickets, isCtrl)
            val remoteMessages = DoriLocalBridgeClient.pullSupportMessagesFromPeer(ctx, isCtrl)
            count += repository.mergePulledSupportMessages(remoteMessages)

            if (isCtrl) {
                val unsynced = repository.getAllSupportTicketsSnapshot()
                unsynced.filter { it.adminReply.isNotBlank() }.forEach { replied ->
                    DoriLocalBridgeClient.pushSupportReplyToUser(ctx, replied)
                }
                repository.getAllSupportMessagesSnapshot()
                    .filter { it.isFromAdmin && !it.syncedToPeer }
                    .forEach { msg ->
                        if (DoriLocalBridgeClient.pushSupportMessageToUser(ctx, msg)) {
                            repository.markSupportMessageSynced(msg.id)
                            count++
                        }
                    }
                val remoteBookings = DoriLocalBridgeClient.pullBookingsFromUser(ctx)
                count += repository.mergePulledBookingsForControl(remoteBookings)

                repository.getAllBookingsSnapshot().forEach { b ->
                    DoriLocalBridgeClient.pushBookingDecisionToUser(
                        context = ctx,
                        ticketCode = b.ticketCode,
                        approvalStatus = b.approvalStatus,
                        handledByProvider = b.handledByProvider,
                        approvalNote = b.approvalNote,
                        status = b.status,
                        stage = b.stage,
                        peopleAhead = b.peopleAhead,
                        estimatedMinutes = b.estimatedMinutes
                    )
                }

                val (payload, venues, providers) = repository.getControlBridgeSnapshot()
                if (payload.controlRevision > 0L || payload.queueResetRevision > 0L || payload.loyaltyRevision > 0L) {
                    DoriLocalBridgeClient.pushVenuesToUser(ctx, venues)
                    DoriLocalBridgeClient.pushProvidersToUser(ctx, providers)
                    DoriLocalBridgeClient.pushControlStateToUser(ctx, payload)
                }
            } else {
                val localTickets = repository.getAllSupportTicketsSnapshot()
                localTickets.filter { !it.syncedToControl }.forEach { pending ->
                    if (DoriLocalBridgeClient.pushSupportTicketToControl(ctx, pending)) {
                        repository.markSupportTicketDelivered(pending.id)
                        count++
                    }
                }
                repository.getAllSupportMessagesSnapshot()
                    .filter { !it.isFromAdmin && !it.syncedToPeer }
                    .forEach { msg ->
                        if (DoriLocalBridgeClient.pushSupportMessageToControl(ctx, msg)) {
                            repository.markSupportMessageSynced(msg.id)
                            count++
                        }
                    }
                repository.getAllBookingsSnapshot()
                    .filter { it.status == BookingStatus.ACTIVE.name }
                    .forEach { activeBooking ->
                        DoriLocalBridgeClient.pushBookingToControl(ctx, activeBooking)
                    }

                val decisions = DoriLocalBridgeClient.pullBookingDecisionsFromControl(ctx)
                decisions.forEach { d ->
                    if (repository.applyBookingApprovalDecisionLocal(d)) {
                        count++
                    }
                }

                val remoteControl = DoriLocalBridgeClient.pullControlStateFromControl(ctx)
                if (remoteControl != null) {
                    val applied = repository.getAppliedBridgeSyncState()
                    val needsControlSync =
                        remoteControl.controlRevision > applied.lastControlRevision ||
                            remoteControl.queueResetRevision > applied.lastQueueResetRevision ||
                            remoteControl.loyaltyRevision > applied.lastLoyaltyRevision
                    if (needsControlSync) {
                        val queueResetChanged =
                            remoteControl.queueResetRevision > applied.lastQueueResetRevision
                        val remoteVenues = DoriLocalBridgeClient.pullVenuesFromControl(ctx)
                        if (remoteVenues != null) {
                            count += repository.mergeSyncedVenuesFromControl(
                                remoteVenues = remoteVenues,
                                queueResetChanged = queueResetChanged
                            )
                        }
                        val remoteProviders = DoriLocalBridgeClient.pullProvidersFromControl(ctx)
                        if (remoteProviders != null) {
                            count += repository.mergeSyncedProvidersFromControl(remoteProviders)
                        }
                        if (repository.applyPulledControlState(remoteControl)) {
                            count++
                        }
                    }
                }
            }
            count
        }
        if (!silent) {
            _toastMessage.emit(
                if (changedCount > 0) "تم تحديث البيانات ($changedCount تحديث جديد)"
                else "البيانات محدّثة بالفعل"
            )
        }
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
                _toastMessage.emit("الحجوزات متوقفة مؤقتاً حالياً")
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
                withContext(Dispatchers.IO) {
                    DoriLocalBridgeClient.pushBookingToControl(getApplication(), booking)
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
                _toastMessage.emit("الحجوزات متوقفة مؤقتاً حالياً")
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

    // ==================== Support Center Commands ====================

    fun sendSupportMessage(
        senderName: String,
        senderPhone: String,
        category: SupportCategory,
        subject: String,
        message: String,
        requestType: SupportRequestType = SupportRequestType.CHAT,
        relatedVenueName: String = ""
    ) {
        viewModelScope.launch {
            val result = repository.createSupportTicket(
                senderName = senderName,
                senderPhone = senderPhone,
                category = category,
                subject = subject,
                message = message,
                requestType = requestType,
                relatedVenueName = relatedVenueName
            )
            result.onSuccess { ticket ->
                val pushed = withContext(Dispatchers.IO) {
                    val ticketOk = DoriLocalBridgeClient.pushSupportTicketToControl(getApplication(), ticket)
                    val msgs = repository.getAllSupportMessagesSnapshot()
                        .filter { it.ticketExternalId == ticket.externalId && !it.isFromAdmin && !it.syncedToPeer }
                    msgs.forEach { m ->
                        if (DoriLocalBridgeClient.pushSupportMessageToControl(getApplication(), m)) {
                            repository.markSupportMessageSynced(m.id)
                        }
                    }
                    ticketOk
                }
                if (pushed) {
                    repository.markSupportTicketDelivered(ticket.id)
                    _toastMessage.emit(
                        if (requestType == SupportRequestType.COMPLAINT) "تم إرسال طلب الشكوى بنجاح ✓"
                        else "تم إرسال رسالتك إلى الدعم الفني ✓"
                    )
                } else {
                    _toastMessage.emit(
                        if (requestType == SupportRequestType.COMPLAINT) "تم تسجيل طلب الشكوى بنجاح ✓"
                        else "تم تسجيل رسالتك — ستصلك الإجابة في المحادثة ✓"
                    )
                }
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "تعذر الإرسال")
            }
        }
    }

    fun sendUserChatFollowUp(
        ticketExternalId: String,
        senderName: String,
        messageBody: String
    ) {
        viewModelScope.launch {
            repository.sendUserSupportFollowUp(
                ticketExternalId = ticketExternalId,
                senderName = senderName,
                messageBody = messageBody
            ).onSuccess { (updatedTicket, msgEntity) ->
                val pushed = withContext(Dispatchers.IO) {
                    val tOk = DoriLocalBridgeClient.pushSupportTicketToControl(getApplication(), updatedTicket)
                    val mOk = DoriLocalBridgeClient.pushSupportMessageToControl(getApplication(), msgEntity)
                    if (mOk) repository.markSupportMessageSynced(msgEntity.id)
                    tOk && mOk
                }
                if (pushed) {
                    repository.markSupportTicketDelivered(updatedTicket.id)
                }
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "تعذر إرسال الرسالة")
            }
        }
    }

    fun replyToSupportTicket(ticketId: Long, replyText: String) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.REPLY_SUPPORT)) return@launch
            val admin = uiState.value.activeAdmin
            val adminLabel = admin?.name?.takeIf { it.isNotBlank() } ?: "فريق الدعم الفني"
            repository.replyToSupportTicket(ticketId, replyText, adminLabel)
                .onSuccess { updated ->
                    val pushed = withContext(Dispatchers.IO) {
                        val ticketOk = DoriLocalBridgeClient.pushSupportReplyToUser(getApplication(), updated)
                        val msgs = repository.getAllSupportMessagesSnapshot()
                            .filter { it.ticketExternalId == updated.externalId && it.isFromAdmin && !it.syncedToPeer }
                        msgs.forEach { m ->
                            if (DoriLocalBridgeClient.pushSupportMessageToUser(getApplication(), m)) {
                                repository.markSupportMessageSynced(m.id)
                            }
                        }
                        ticketOk
                    }
                    _toastMessage.emit(
                        if (pushed) "تم إرسال الرد وإشعار المستخدم ✓"
                        else "تم حفظ الرد وسيظهر للمستخدم عند المزامنة"
                    )
                }
                .onFailure { err ->
                    _toastMessage.emit(err.message ?: "تعذر إرسال الرد")
                }
        }
    }

    fun updateSupportTicketStatus(ticketId: Long, status: SupportTicketStatus) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.REPLY_SUPPORT)) return@launch
            val updated = repository.updateSupportTicketStatus(ticketId, status) ?: return@launch
            withContext(Dispatchers.IO) {
                DoriLocalBridgeClient.pushSupportReplyToUser(getApplication(), updated)
            }
            _toastMessage.emit("تم تحديث حالة التذكرة إلى: ${status.label}")
        }
    }

    // ==================== Admin Management Commands ====================

    fun setActiveAdmin(adminId: Long) {
        viewModelScope.launch {
            repository.setActiveAdmin(adminId)
            val target = uiState.value.admins.find { it.id == adminId }
            if (target != null) {
                _toastMessage.emit("تم التبديل إلى حساب الأدمن: ${target.name}")
            }
        }
    }

    fun addAdminAccount(
        name: String,
        roleTitle: String,
        phone: String,
        canManageFeatures: Boolean,
        canManageVenues: Boolean,
        canManageQueues: Boolean,
        canReplySupport: Boolean,
        canManageProviders: Boolean,
        canManageAdmins: Boolean,
        canRunCommands: Boolean
    ) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_ADMINS)) return@launch
            repository.addAdminAccount(
                name = name,
                roleTitle = roleTitle,
                phone = phone,
                canManageFeatures = canManageFeatures,
                canManageVenues = canManageVenues,
                canManageQueues = canManageQueues,
                canReplySupport = canReplySupport,
                canManageProviders = canManageProviders,
                canManageAdmins = canManageAdmins,
                canRunCommands = canRunCommands
            ).onSuccess { created ->
                _toastMessage.emit("تمت إضافة الأدمن \"${created.name}\" بنجاح")
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "تعذر إضافة الأدمن")
            }
        }
    }

    fun updateAdminAccount(updated: AdminAccountEntity) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_ADMINS)) return@launch
            val cleanName = updated.name.trim()
            if (cleanName.length < 3) {
                _toastMessage.emit("اسم الأدمن يجب أن لا يقل عن 3 أحرف")
                return@launch
            }
            val otherCanManageAdmins = uiState.value.admins.any {
                it.id != updated.id && it.isActive && it.canManageAdmins
            }
            if ((!updated.isActive || !updated.canManageAdmins) && !otherCanManageAdmins) {
                _toastMessage.emit("يجب أن يمتلك أدمن نشط واحد على الأقل صلاحية (إدارة الأدمن)")
                return@launch
            }
            val normalized = updated.copy(
                name = cleanName,
                roleTitle = updated.roleTitle.trim().ifBlank { "مشرف تشغيل" },
                phone = updated.phone.trim()
            )
            repository.updateAdminAccount(normalized)
            _toastMessage.emit("تم تحديث بيانات وصلاحيات ${normalized.name} ✓")
        }
    }

    fun deleteAdminAccount(adminId: Long) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_ADMINS)) return@launch
            repository.deleteAdminAccount(adminId)
                .onSuccess { _toastMessage.emit("تم حذف حساب الأدمن") }
                .onFailure { err -> _toastMessage.emit(err.message ?: "تعذر حذف الحساب") }
        }
    }

    // ==================== Service Providers (Doctors / Field Owners / Merchants) ====================

    fun setActiveProvider(providerId: Long) {
        viewModelScope.launch {
            repository.setActiveProvider(providerId)
            val prov = uiState.value.providers.find { it.id == providerId }
            if (prov != null) {
                filterState.update { it.copy(selectedMerchantVenueId = prov.venueId) }
                _toastMessage.emit("أنت تعمل الآن بصلاحية: ${prov.name} (${prov.customTitle})")
            } else {
                _toastMessage.emit("العودة إلى وضع الإدارة الكامل")
            }
        }
    }

    fun addServiceProvider(
        name: String,
        role: ProviderRole,
        customTitle: String,
        phone: String,
        venueId: Int,
        canAcceptTickets: Boolean,
        canCallNext: Boolean,
        canPauseVenue: Boolean
    ) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_PROVIDERS)) return@launch
            repository.addServiceProvider(
                name = name,
                role = role,
                customTitle = customTitle,
                phone = phone,
                venueId = venueId,
                canAcceptTickets = canAcceptTickets,
                canCallNext = canCallNext,
                canPauseVenue = canPauseVenue
            ).onSuccess { prov ->
                pushControlSnapshotToUserApp()
                _toastMessage.emit("تمت إضافة ${prov.name} (${prov.customTitle})")
            }.onFailure { err ->
                _toastMessage.emit(err.message ?: "تعذر إضافة مقدم الخدمة")
            }
        }
    }

    fun updateServiceProvider(provider: ServiceProviderEntity) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_PROVIDERS)) return@launch
            val cleanName = provider.name.trim()
            if (cleanName.length < 3) {
                _toastMessage.emit("اسم مقدم الخدمة يجب أن لا يقل عن 3 أحرف")
                return@launch
            }
            val venue = uiState.value.allVenues.find { it.id == provider.venueId }
            val normalized = provider.copy(
                name = cleanName,
                customTitle = provider.customTitle.trim().ifBlank {
                    ProviderRole.fromName(provider.providerRole).displayName
                },
                phone = provider.phone.trim(),
                venueName = venue?.name ?: provider.venueName
            )
            repository.updateServiceProvider(normalized)
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم تحديث بيانات وصلاحيات ${normalized.name} ✓")
        }
    }

    fun deleteServiceProvider(providerId: Long) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_PROVIDERS)) return@launch
            repository.deleteServiceProvider(providerId)
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم حذف مقدم الخدمة")
        }
    }

    fun acceptBookingTicket(bookingId: Long, note: String = "") {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null) {
                if (!prov.canAcceptTickets || !prov.isActive) {
                    _toastMessage.emit("حساب ${prov.name} لا يملك صلاحية قبول التذاكر")
                    return@launch
                }
            } else if (!requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) {
                return@launch
            }
            val actorName = prov?.let { "${it.name} (${it.customTitle})" }
                ?: uiState.value.activeAdmin?.name
                ?: "استقبال المركز"
            val updated = repository.acceptBookingTicket(
                bookingId = bookingId,
                actorName = actorName,
                note = note,
                providerId = prov?.id
            ) ?: return@launch
            withContext(Dispatchers.IO) {
                DoriLocalBridgeClient.pushBookingDecisionToUser(
                    context = getApplication(),
                    ticketCode = updated.ticketCode,
                    approvalStatus = updated.approvalStatus,
                    handledByProvider = updated.handledByProvider,
                    approvalNote = updated.approvalNote,
                    status = updated.status,
                    stage = updated.stage,
                    peopleAhead = updated.peopleAhead,
                    estimatedMinutes = updated.estimatedMinutes
                )
            }
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم قبول التذكرة ${updated.ticketCode} بواسطة $actorName ✓")
        }
    }

    fun rejectBookingTicket(bookingId: Long, note: String = "") {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null) {
                if (!prov.canAcceptTickets || !prov.isActive) {
                    _toastMessage.emit("حساب ${prov.name} لا يملك صلاحية رفض التذاكر")
                    return@launch
                }
            } else if (!requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) {
                return@launch
            }
            val actorName = prov?.let { "${it.name} (${it.customTitle})" }
                ?: uiState.value.activeAdmin?.name
                ?: "استقبال المركز"
            val updated = repository.rejectBookingTicket(
                bookingId = bookingId,
                actorName = actorName,
                note = note
            ) ?: return@launch
            withContext(Dispatchers.IO) {
                DoriLocalBridgeClient.pushBookingDecisionToUser(
                    context = getApplication(),
                    ticketCode = updated.ticketCode,
                    approvalStatus = updated.approvalStatus,
                    handledByProvider = updated.handledByProvider,
                    approvalNote = updated.approvalNote,
                    status = updated.status,
                    stage = updated.stage,
                    peopleAhead = updated.peopleAhead,
                    estimatedMinutes = updated.estimatedMinutes
                )
            }
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم الاعتذار عن التذكرة ${updated.ticketCode}")
        }
    }

    // ==================== Merchant Dashboard actions ====================

    fun selectMerchantVenue(venueId: Int) {
        filterState.update { it.copy(selectedMerchantVenueId = venueId) }
    }

    fun callNextCustomer(venueId: Int) {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null && !prov.canCallNext) {
                _toastMessage.emit("${prov.name} لا يملك صلاحية استدعاء الدور")
                return@launch
            }
            if (prov == null && !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) return@launch
            val alerts = repository.callNextCustomer(venueId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
        }
    }

    fun skipCurrentCustomer(venueId: Int) {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null && !prov.canCallNext) {
                _toastMessage.emit("${prov.name} لا يملك صلاحية تخطي الدور")
                return@launch
            }
            if (prov == null && !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) return@launch
            val alerts = repository.skipCurrentCustomer(venueId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم تخطي العميل الحالي والنداء على التالي")
        }
    }

    fun callCustomerByName(venueId: Int, queueItemId: Long, customerName: String) {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null && !prov.canCallNext) {
                _toastMessage.emit("${prov.name} لا يملك صلاحية استدعاء العميل")
                return@launch
            }
            if (prov == null && !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) return@launch
            val alerts = repository.callCustomerByName(venueId, queueItemId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم استدعاء $customerName الآن")
        }
    }

    fun markCustomerNoShow(venueId: Int, queueItemId: Long, customerName: String) {
        viewModelScope.launch {
            if (uiState.value.activeProvider == null &&
                !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)
            ) return@launch
            val alerts = repository.markCustomerNoShow(venueId, queueItemId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم تسجيل توقف $customerName عن الحضور")
        }
    }

    fun togglePauseQueue(venueId: Int) {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null && !prov.canPauseVenue) {
                _toastMessage.emit("${prov.name} لا يملك صلاحية إيقاف الطابور")
                return@launch
            }
            if (prov == null && !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) return@launch
            repository.togglePauseQueue(venueId)
            pushControlSnapshotToUserApp()
        }
    }

    fun toggleAcceptingBookings(venueId: Int) {
        viewModelScope.launch {
            val prov = uiState.value.activeProvider
            if (prov != null && !prov.canPauseVenue) {
                _toastMessage.emit("${prov.name} لا يملك صلاحية تعديل استقبال الحجوزات")
                return@launch
            }
            if (prov == null && !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)) return@launch
            repository.toggleAcceptingBookings(venueId)
            pushControlSnapshotToUserApp()
        }
    }

    fun addWalkIn(venueId: Int, name: String, option: String) {
        viewModelScope.launch {
            if (uiState.value.activeProvider == null &&
                !requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)
            ) return@launch
            repository.addWalkInCustomer(venueId, name, option)
            pushControlSnapshotToUserApp(includeBookings = true)
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
            if (!requireAdminPermission(AdminPermissionType.MANAGE_FEATURES)) return@launch
            repository.updateControlConfig(transform)
            pushControlSnapshotToUserApp()
        }
    }

    fun toggleCategoryEnabled(category: ServiceCategory) {
        if (category == ServiceCategory.ALL) return
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_FEATURES)) return@launch
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
            pushControlSnapshotToUserApp()
        }
    }

    fun setBroadcastMessage(message: String) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_FEATURES)) return@launch
            repository.updateControlConfig { it.copy(broadcastMessage = message.trim()) }
            pushControlSnapshotToUserApp()
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
            if (!requireAdminPermission(AdminPermissionType.MANAGE_VENUES)) return@launch
            repository.addCustomVenue(name, district, area, category, specialty, priceDinar, avgMinutes, phone)
                .onSuccess { venue ->
                    pushControlSnapshotToUserApp()
                    _toastMessage.emit("تمت إضافة \"${venue.name}\" إلى نسخة المستخدم")
                }
                .onFailure { err ->
                    _toastMessage.emit(err.message ?: "تعذر إضافة المركز")
                }
        }
    }

    fun toggleVenueOpen(venueId: Int) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_VENUES)) return@launch
            repository.toggleVenueOpen(venueId)
            pushControlSnapshotToUserApp()
        }
    }

    fun adjustVenuePrice(venueId: Int, deltaDinar: Int) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_VENUES)) return@launch
            repository.adjustVenuePrice(venueId, deltaDinar)
            pushControlSnapshotToUserApp()
        }
    }

    fun adjustVenueWaitMinutes(venueId: Int, deltaMinutes: Int) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_VENUES)) return@launch
            repository.adjustVenueWaitMinutes(venueId, deltaMinutes)
            pushControlSnapshotToUserApp(includeBookings = true)
        }
    }

    fun deleteVenue(venueId: Int) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_VENUES)) return@launch
            repository.deleteVenue(venueId)
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم حذف المركز من القائمة")
        }
    }

    fun setDistrictVenuesOpen(open: Boolean) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_VENUES)) return@launch
            val district = uiState.value.preferences.selectedDistrict
            repository.setAllVenuesOpenInDistrict(district, open)
            pushControlSnapshotToUserApp()
            _toastMessage.emit(
                if (open) "تم فتح جميع مراكز ${district.displayName}"
                else "تم إغلاق جميع مراكز ${district.displayName} مؤقتاً"
            )
        }
    }

    fun advanceAllQueuesNow() {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            val alerts = repository.advanceActiveUserTicketDemoStep()
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم تقديم جميع الأدوار النشطة خطوة للأمام")
        }
    }

    fun completeAllActiveBookings() {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            val count = repository.completeAllActiveBookings()
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم إتمام $count تذكرة نشطة")
        }
    }

    fun cancelAllActiveBookings() {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            val count = repository.cancelAllActiveBookings()
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم إلغاء $count تذكرة نشطة")
        }
    }

    fun resetAllQueuesToOne() {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            repository.resetAllQueuesToOne()
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم تصفير جميع الطوابير والبدء من التذكرة #1")
        }
    }

    fun adjustLoyaltyPoints(delta: Int) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            repository.adjustLoyaltyPoints(delta)
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم تحديث نقاط الولاء")
        }
    }

    fun resetLoyaltyPoints() {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            repository.resetLoyaltyPoints()
            pushControlSnapshotToUserApp()
            _toastMessage.emit("تم تصفير نقاط الولاء")
        }
    }

    fun factoryResetAllData() {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.RUN_COMMANDS)) return@launch
            simulationJob?.cancel()
            simulationJob = null
            filterState.update { it.copy(isLiveSimulationRunning = false, activeTurnAlert = null) }
            repository.factoryResetAllData()
            pushControlSnapshotToUserApp()
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
    private val appMode: DoriAppMode
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DoriViewModel::class.java)) {
            return DoriViewModel(
                application = application,
                appMode = appMode
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
