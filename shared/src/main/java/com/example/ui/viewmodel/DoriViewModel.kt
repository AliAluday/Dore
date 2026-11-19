package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bridge.DoriLocalBridgeClient
import com.example.data.cloud.CloudSyncStatus
import com.example.data.cloud.CloudSyncUiState
import com.example.data.cloud.DoriCloudSyncManager
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
import android.util.Log
import com.example.util.AlertNotifier
import com.example.util.DoriNotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val activeTurnAlert: LiveTurnAlert? = null,
    val selectedVenueForDetails: VenueEntity? = null,
    val bookingSessionId: Long = 0L,
    val newlyBookedTicket: BookingEntity? = null,
    val cloudSync: CloudSyncUiState = CloudSyncUiState()
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
    val activeTurnAlert: LiveTurnAlert? = null,
    val selectedVenueIdForDetails: Int? = null,
    val bookingSessionId: Long = 0L,
    val newlyBookedTicket: BookingEntity? = null
)

class DoriViewModel(
    application: Application,
    private val appMode: DoriAppMode = DoriAppMode.USER_APP,
    private val firestoreDatabaseId: String = ""
) : AndroidViewModel(application) {

    private val database = DoriDatabase.getInstance(application)
    private val prefs = UserPreferencesDataStore(application)
    private val fallbackCloudState = MutableStateFlow(CloudSyncUiState(status = CloudSyncStatus.OFFLINE))

    private val cloudSyncManager: DoriCloudSyncManager? = if (firestoreDatabaseId.isNotBlank()) {
        runCatching {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                val firestore = FirebaseFirestore.getInstance(firestoreDatabaseId)
                runCatching {
                    firestore.firestoreSettings = firestoreSettings {
                        setLocalCacheSettings(persistentCacheSettings {})
                    }
                }
                DoriCloudSyncManager(
                    firestore = firestore,
                    auth = auth,
                    dao = database.doriDao(),
                    prefs = prefs,
                    isControlApp = appMode == DoriAppMode.CONTROL_APP,
                    scope = viewModelScope,
                    onRealtimeBookingAlert = if (appMode == DoriAppMode.USER_APP) {
                        { alert -> viewModelScope.launch { handleTurnAlerts(listOf(alert)) } }
                    } else {
                        null
                    }
                )
            } else {
                null
            }
        }.onFailure { err ->
            Log.e("DoriViewModel", "Failed to initialize Firebase Cloud Sync", err)
            fallbackCloudState.value = CloudSyncUiState(
                status = CloudSyncStatus.ERROR,
                errorMessage = "تعذر تهيئة الاتصال السحابي: ${err.localizedMessage ?: "خطأ غير معروف"}"
            )
        }.getOrNull()
    } else {
        null
    }
    private val repository = DoriRepository(database.doriDao(), prefs, cloudSyncManager)

    val isCloudSyncEnabled: Boolean
        get() = cloudSyncManager != null

    private val initialTab = if (appMode == DoriAppMode.CONTROL_APP) {
        AppTab.CONTROL_FEATURES
    } else {
        AppTab.EXPLORE
    }

    private val filterState = MutableStateFlow(FilterState(currentTab = initialTab))
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()
    private val bridgeSyncMutex = Mutex()

    init {
        viewModelScope.launch {
            if (cloudSyncManager != null) {
                cloudSyncManager.start()
            } else {
                repository.ensureSeeded()
                syncLocalBridgeInternal(silent = true)
            }
        }
    }

    fun ensureCloudSyncStarted() {
        cloudSyncManager?.start()
    }

    override fun onCleared() {
        cloudSyncManager?.stop()
        super.onCleared()
    }

    suspend fun prepareSignOut() {
        cloudSyncManager?.clearLocalSessionCache()
    }

    fun retryAdminAccessCheck() {
        cloudSyncManager?.retryAdminAccessCheck()
    }

    fun authorizeCloudAdmin(targetUid: String, email: String, displayName: String) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_ADMINS)) return@launch
            val cloud = cloudSyncManager
            if (cloud == null) {
                _toastMessage.emit("المزامنة السحابية غير مفعلة حالياً")
                return@launch
            }
            cloud.authorizeCloudAdmin(targetUid, email, displayName)
                .onSuccess { _toastMessage.emit("تم منح صلاحية الدخول السحابي لنسخة التحكم ✓") }
                .onFailure { err -> _toastMessage.emit(err.message ?: "تعذر منح الصلاحية") }
        }
    }

    fun revokeCloudAdmin(targetUid: String) {
        viewModelScope.launch {
            if (!requireAdminPermission(AdminPermissionType.MANAGE_ADMINS)) return@launch
            val cloud = cloudSyncManager ?: return@launch
            cloud.revokeCloudAdmin(targetUid)
                .onSuccess { _toastMessage.emit("تم سحب صلاحية الدخول السحابي ✓") }
                .onFailure { err -> _toastMessage.emit(err.message ?: "تعذر سحب الصلاحية") }
        }
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
        repository.supportMessages,
        cloudSyncManager?.syncState ?: fallbackCloudState
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
        val cloudSyncState = values[10] as CloudSyncUiState

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
            activeTurnAlert = filters.activeTurnAlert,
            selectedVenueForDetails = detailVenue,
            bookingSessionId = filters.bookingSessionId,
            newlyBookedTicket = filters.newlyBookedTicket,
            cloudSync = cloudSyncState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DoriUiState(appMode = appMode, currentTab = initialTab)
    )

    private suspend fun requireAdminPermission(permission: AdminPermissionType): Boolean {
        if (appMode != DoriAppMode.CONTROL_APP) return true
        val admin = uiState.value.activeAdmin ?: return true
        if (!admin.isActive) {
            _toastMessage.emit("حساب (${admin.name}) موقوف مؤقتاً ولا يملك صلاحية التنفيذ")
            return false
        }
        if (admin.hasPermission(permission)) return true
        _toastMessage.emit("حساب (${admin.name}) لا يملك صلاحية: ${permission.label}")
        return false
    }

    private suspend fun requireProviderOrQueuePermission(
        venueId: Int,
        actionLabel: String,
        predicate: (ServiceProviderEntity) -> Boolean
    ): Pair<Boolean, ServiceProviderEntity?> {
        val prov = uiState.value.activeProvider
        if (prov != null) {
            if (!prov.isActive) {
                _toastMessage.emit("حساب ${prov.name} موقوف مؤقتاً")
                return false to null
            }
            if (prov.venueId != venueId) {
                _toastMessage.emit("حساب ${prov.name} مخصص لمركز (${prov.venueName}) فقط")
                return false to null
            }
            if (!predicate(prov)) {
                _toastMessage.emit("حساب ${prov.name} لا يملك صلاحية: $actionLabel")
                return false to null
            }
            return true to prov
        }
        val ok = requireAdminPermission(AdminPermissionType.MANAGE_QUEUES)
        return ok to null
    }

    fun selectTab(tab: AppTab) {
        filterState.update { it.copy(currentTab = tab) }
        if (cloudSyncManager == null) {
            viewModelScope.launch { syncLocalBridgeInternal(silent = true) }
        }
    }

    fun syncLocalBridge(silent: Boolean = false) {
        viewModelScope.launch {
            syncLocalBridgeInternal(silent = silent)
        }
    }

    private suspend fun pushControlSnapshotToUserApp(includeBookings: Boolean = false) {
        if (cloudSyncManager != null) return
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
        val cloud = cloudSyncManager
        if (cloud != null) {
            val result = cloud.syncNow()
            if (!silent) {
                result.onSuccess { count ->
                    _toastMessage.emit(
                        if (count > 0) "تمت المزامنة السحابية بنجاح ✓"
                        else "جميع البيانات متزامنة مع السحابة ✓"
                    )
                }.onFailure { err ->
                    _toastMessage.emit(err.message ?: "تعذر المزامنة مع السحابة")
                }
            }
            return@withLock
        }
        val ctx = getApplication<Application>()
        val isCtrl = appMode == DoriAppMode.CONTROL_APP
        val changedCount = withContext(Dispatchers.IO) {
            var count = 0
            val remoteMessages = DoriLocalBridgeClient.pullSupportMessagesFromPeer(ctx, isCtrl)
            count += repository.mergePulledSupportMessages(remoteMessages, isControlApp = isCtrl)
            val remoteTickets = DoriLocalBridgeClient.pullSupportTicketsFromPeer(ctx, isCtrl)
            count += repository.mergePulledSupportTickets(remoteTickets, isCtrl)

            if (isCtrl) {
                repository.getAllSupportMessagesSnapshot()
                    .filter { it.isFromAdmin && !it.syncedToPeer }
                    .forEach { msg ->
                        if (DoriLocalBridgeClient.pushSupportMessageToUser(ctx, msg)) {
                            repository.markSupportMessageSynced(msg.id)
                            count++
                        }
                    }
                val unsynced = repository.getAllSupportTicketsSnapshot()
                unsynced.filter { it.adminReply.isNotBlank() }.forEach { replied ->
                    DoriLocalBridgeClient.pushSupportReplyToUser(ctx, replied)
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
                repository.getAllSupportMessagesSnapshot()
                    .filter { !it.isFromAdmin && !it.syncedToPeer }
                    .forEach { msg ->
                        if (DoriLocalBridgeClient.pushSupportMessageToControl(ctx, msg)) {
                            repository.markSupportMessageSynced(msg.id)
                            count++
                        }
                    }
                val localTickets = repository.getAllSupportTicketsSnapshot()
                localTickets.filter { !it.syncedToControl }.forEach { pending ->
                    if (DoriLocalBridgeClient.pushSupportTicketToControl(ctx, pending)) {
                        repository.markSupportTicketDelivered(pending.id)
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
                        if (queueResetChanged && applied.lastQueueResetRevision >= 0L && remoteControl.queueResetRevision > 0L) {
                            repository.clearStaleLocalActiveBookingsBefore(remoteControl.queueResetRevision)
                        }
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
            val ctrl = uiState.value.preferences.controlConfig
            if (!ctrl.allowNewBookings) {
                _toastMessage.emit("الحجوزات متوقفة مؤقتاً حالياً")
                return@launch
            }
            if (venue.category in ctrl.disabledCategories) {
                _toastMessage.emit("هذا القسم متوقف مؤقتاً حالياً")
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
                if (cloudSyncManager == null) {
                    withContext(Dispatchers.IO) {
                        DoriLocalBridgeClient.pushBookingToControl(getApplication(), booking)
                    }
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
            if (cloudSyncManager == null) {
                repository.getBookingById(bookingId)?.let { updated ->
                    withContext(Dispatchers.IO) {
                        DoriLocalBridgeClient.pushBookingToControl(getApplication(), updated)
                    }
                }
            }
            _toastMessage.emit(
                if (earned > 0) "تم إتمام الخدمة بنجاح! حصلت على +$earned نقاط ولاء"
                else "تم إتمام الخدمة بنجاح ✓"
            )
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
            if (cloudSyncManager == null) {
                repository.getBookingById(bookingId)?.let { updated ->
                    withContext(Dispatchers.IO) {
                        DoriLocalBridgeClient.pushBookingToControl(getApplication(), updated)
                    }
                }
            }
            _toastMessage.emit("تم إلغاء الحجز ونقله إلى السجل")
        }
    }

    fun quickRebook(historyBooking: BookingEntity) {
        viewModelScope.launch {
            val venue = uiState.value.allVenues.find { it.id == historyBooking.venueId }
            val disabled = uiState.value.preferences.controlConfig.disabledCategories
            when {
                venue == null -> _toastMessage.emit("عذراً، لم يعد هذا المركز متاحاً حالياً")
                venue.category in disabled -> _toastMessage.emit("هذا القسم متوقف مؤقتاً حالياً")
                !venue.isOpen || !venue.isAcceptingBookings ->
                    _toastMessage.emit("${venue.name} مغلق أو متوقف عن استقبال الحجوزات حالياً")
                else -> {
                    openVenueDetails(venue.id)
                    _toastMessage.emit("أدخل معلومات الحجز الجديد لنفس اليوم")
                }
            }
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
                if (cloudSyncManager != null) {
                    _toastMessage.emit(
                        if (requestType == SupportRequestType.COMPLAINT) "تم إرسال وحفظ طلب الشكوى سحابياً ✓"
                        else "تم إرسال رسالتك إلى الدعم الفني سحابياً ✓"
                    )
                } else {
                    val pushed = withContext(Dispatchers.IO) {
                        val msgs = repository.getAllSupportMessagesSnapshot()
                            .filter { it.ticketExternalId == ticket.externalId && !it.isFromAdmin && !it.syncedToPeer }
                        msgs.forEach { m ->
                            if (DoriLocalBridgeClient.pushSupportMessageToControl(getApplication(), m)) {
                                repository.markSupportMessageSynced(m.id)
                            }
                        }
                        DoriLocalBridgeClient.pushSupportTicketToControl(getApplication(), ticket)
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
                if (cloudSyncManager == null) {
                    val pushed = withContext(Dispatchers.IO) {
                        val mOk = DoriLocalBridgeClient.pushSupportMessageToControl(getApplication(), msgEntity)
                        if (mOk) repository.markSupportMessageSynced(msgEntity.id)
                        val tOk = DoriLocalBridgeClient.pushSupportTicketToControl(getApplication(), updatedTicket)
                        tOk && mOk
                    }
                    if (pushed) {
                        repository.markSupportTicketDelivered(updatedTicket.id)
                    }
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
                    if (cloudSyncManager != null) {
                        _toastMessage.emit("تم إرسال الرد وحفظه سحابياً ✓")
                    } else {
                        val pushed = withContext(Dispatchers.IO) {
                            val msgs = repository.getAllSupportMessagesSnapshot()
                                .filter { it.ticketExternalId == updated.externalId && it.isFromAdmin && !it.syncedToPeer }
                            msgs.forEach { m ->
                                if (DoriLocalBridgeClient.pushSupportMessageToUser(getApplication(), m)) {
                                    repository.markSupportMessageSynced(m.id)
                                }
                            }
                            DoriLocalBridgeClient.pushSupportReplyToUser(getApplication(), updated)
                        }
                        _toastMessage.emit(
                            if (pushed) "تم إرسال الرد وإشعار المستخدم ✓"
                            else "تم حفظ الرد وسيظهر للمستخدم عند المزامنة"
                        )
                    }
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
            if (cloudSyncManager == null) {
                withContext(Dispatchers.IO) {
                    DoriLocalBridgeClient.pushSupportReplyToUser(getApplication(), updated)
                }
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
            if (updated.isSuperAdmin && (!updated.isActive || !updated.canManageAdmins)) {
                _toastMessage.emit("لا يمكن تعطيل حساب المدير العام الأساسي أو سحب صلاحية إدارة الأدمن")
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
            val booking = repository.getBookingById(bookingId) ?: return@launch
            val (allowed, prov) = requireProviderOrQueuePermission(
                venueId = booking.venueId,
                actionLabel = "قبول التذاكر"
            ) { it.canAcceptTickets }
            if (!allowed) return@launch
            val actorName = prov?.let { "${it.name} (${it.customTitle})" }
                ?: uiState.value.activeAdmin?.name
                ?: "استقبال المركز"
            val updated = repository.acceptBookingTicket(
                bookingId = bookingId,
                actorName = actorName,
                note = note,
                providerId = prov?.id
            ) ?: return@launch
            if (cloudSyncManager == null) {
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
            }
            _toastMessage.emit("تم قبول التذكرة ${updated.ticketCode} بواسطة $actorName ✓")
        }
    }

    fun rejectBookingTicket(bookingId: Long, note: String = "") {
        viewModelScope.launch {
            val booking = repository.getBookingById(bookingId) ?: return@launch
            val (allowed, prov) = requireProviderOrQueuePermission(
                venueId = booking.venueId,
                actionLabel = "رفض التذاكر"
            ) { it.canAcceptTickets }
            if (!allowed) return@launch
            val actorName = prov?.let { "${it.name} (${it.customTitle})" }
                ?: uiState.value.activeAdmin?.name
                ?: "استقبال المركز"
            val updated = repository.rejectBookingTicket(
                bookingId = bookingId,
                actorName = actorName,
                note = note
            ) ?: return@launch
            if (cloudSyncManager == null) {
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
            }
            _toastMessage.emit("تم الاعتذار عن التذكرة ${updated.ticketCode}")
        }
    }

    // ==================== Merchant Dashboard actions ====================

    fun selectMerchantVenue(venueId: Int) {
        filterState.update { it.copy(selectedMerchantVenueId = venueId) }
    }

    fun callNextCustomer(venueId: Int) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "استدعاء الدور"
            ) { it.canCallNext }
            if (!allowed) return@launch
            val alerts = repository.callNextCustomer(venueId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
        }
    }

    fun skipCurrentCustomer(venueId: Int) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "تخطي الدور"
            ) { it.canCallNext }
            if (!allowed) return@launch
            val alerts = repository.skipCurrentCustomer(venueId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم تخطي العميل الحالي والنداء على التالي")
        }
    }

    fun callCustomerByName(venueId: Int, queueItemId: Long, customerName: String) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "استدعاء العميل"
            ) { it.canCallNext }
            if (!allowed) return@launch
            val alerts = repository.callCustomerByName(venueId, queueItemId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم استدعاء $customerName الآن")
        }
    }

    fun markCustomerNoShow(venueId: Int, queueItemId: Long, customerName: String) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "تسجيل التوقف عن الحضور"
            ) { it.canCallNext }
            if (!allowed) return@launch
            val alerts = repository.markCustomerNoShow(venueId, queueItemId)
            handleTurnAlerts(alerts)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تم تسجيل توقف $customerName عن الحضور")
        }
    }

    fun togglePauseQueue(venueId: Int) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "إيقاف الطابور"
            ) { it.canPauseVenue }
            if (!allowed) return@launch
            repository.togglePauseQueue(venueId)
            pushControlSnapshotToUserApp()
        }
    }

    fun toggleAcceptingBookings(venueId: Int) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "تعديل استقبال الحجوزات"
            ) { it.canPauseVenue }
            if (!allowed) return@launch
            repository.toggleAcceptingBookings(venueId)
            pushControlSnapshotToUserApp()
        }
    }

    fun addWalkIn(venueId: Int, name: String, option: String) {
        viewModelScope.launch {
            val (allowed, _) = requireProviderOrQueuePermission(
                venueId = venueId,
                actionLabel = "إضافة عميل حضوري"
            ) { it.canAcceptTickets || it.canCallNext }
            if (!allowed) return@launch
            repository.addWalkInCustomer(venueId, name, option)
            pushControlSnapshotToUserApp(includeBookings = true)
            _toastMessage.emit("تمت إضافة العميل إلى طابور المحل")
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
            val alerts = repository.advanceActiveVenuesOneStep()
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
            filterState.update { it.copy(activeTurnAlert = null) }
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
            val cleanPhone = UserProfileSettings.normalizeDigits(phone.trim())
            if (cleanPhone.isNotEmpty() && !UserProfileSettings.isValidPhone(cleanPhone)) {
                _toastMessage.emit("يرجى إدخال رقم هاتف صحيح (10 إلى 14 رقماً) أو تركه فارغاً")
                return@launch
            }
            repository.updateProfile(name.trim(), cleanPhone, district, age.trim(), optionalNote.trim())
            _toastMessage.emit(
                if (cloudSyncManager != null) "تم حفظ ومزامنة معلوماتك السحابية ✓"
                else "تم حفظ معلوماتك ✓"
            )
        }
    }

    fun testTurnAlert() {
        viewModelScope.launch {
            val prefsNow = uiState.value.preferences
            AlertNotifier.triggerTurnAlert(
                context = getApplication(),
                muteSound = prefsNow.muteSound,
                muteVibration = prefsNow.muteVibration,
                isUrgent = true
            )
            val statusText = when {
                prefsNow.muteSound && prefsNow.muteVibration -> "الصوت والاهتزاز مكتومان حالياً"
                prefsNow.muteSound -> "تم اختبار الاهتزاز (الصوت مكتوم)"
                prefsNow.muteVibration -> "تم اختبار نغمة التنبيه (الاهتزاز مكتوم)"
                else -> "تم اختبار نغمة واهتزاز تنبيه الدور بنجاح 🔔"
            }
            _toastMessage.emit(statusText)
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
    private val firestoreDatabaseId: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DoriViewModel::class.java)) {
            return DoriViewModel(
                application = application,
                appMode = appMode,
                firestoreDatabaseId = firestoreDatabaseId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
