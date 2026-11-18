package com.example

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.InstantTicketPassDialog
import com.example.ui.components.LiveOverlayAlertBanner
import com.example.ui.components.SupportCenterSheet
import com.example.ui.components.TopStickyHeader
import com.example.ui.components.VenueDetailsSheet
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.TicketsScreen
import com.example.ui.theme.DoriTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DoriAppMode
import com.example.ui.viewmodel.DoriViewModel
import com.example.ui.viewmodel.DoriViewModelFactory
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DoriNotificationHelper.ensureChannel(this)

        setContent {
            val app = applicationContext as Application
            val factory = remember(app) {
                DoriViewModelFactory(
                    application = app,
                    appMode = DoriAppMode.USER_APP
                )
            }
            val viewModel: DoriViewModel = viewModel(factory = factory)

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DoriAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DoriAppRoot(
    viewModel: DoriViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotificationPermission by remember {
        mutableStateOf(DoriNotificationHelper.hasNotificationPermission(context))
    }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || DoriNotificationHelper.hasNotificationPermission(context)
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "تم تفعيل إشعارات الدور بنجاح"
                else "يمكنك تفعيل الإشعارات لاحقاً من صفحة الحساب"
            )
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = DoriNotificationHelper.hasNotificationPermission(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !DoriNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.syncLocalBridge(silent = true)
                delay(2500L)
            }
        }
    }

    LaunchedEffect(state.activeTurnAlert) {
        if (state.activeTurnAlert != null) {
            delay(5000L)
            viewModel.dismissTurnAlert()
        }
    }

    BackHandler(enabled = !showSupportSheet && state.currentTab != AppTab.EXPLORE) {
        viewModel.selectTab(AppTab.EXPLORE)
    }

    DoriTheme(darkTheme = state.preferences.isDarkMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = 72.dp)
                    )
                },
                topBar = {
                    TopStickyHeader(
                        selectedDistrict = state.preferences.selectedDistrict,
                        isDarkMode = state.preferences.isDarkMode,
                        activeBookingCount = state.activeBookings.size,
                        isSimulationRunning = state.isLiveSimulationRunning,
                        searchQuery = state.searchQuery,
                        isSearchExpanded = isSearchExpanded,
                        onToggleSearch = {
                            if (state.currentTab != AppTab.EXPLORE) {
                                viewModel.selectTab(AppTab.EXPLORE)
                                isSearchExpanded = true
                            } else {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded && state.searchQuery.isNotEmpty()) {
                                    viewModel.updateSearchQuery("")
                                }
                            }
                        },
                        onSearchChange = { query ->
                            if (state.currentTab != AppTab.EXPLORE) {
                                viewModel.selectTab(AppTab.EXPLORE)
                            }
                            viewModel.updateSearchQuery(query)
                        },
                        onSelectDistrict = viewModel::selectDistrict,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onOpenTickets = { viewModel.selectTab(AppTab.TICKETS) },
                        supportBadgeCount = state.supportAlertCount,
                        onOpenSupport = {
                            viewModel.syncLocalBridge(silent = true)
                            showSupportSheet = true
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (state.currentTab) {
                        AppTab.EXPLORE -> ExploreScreen(
                            state = state,
                            onSelectArea = viewModel::selectArea,
                            onClearSearch = {
                                viewModel.updateSearchQuery("")
                                isSearchExpanded = false
                            },
                            onCategorySelect = viewModel::selectCategory,
                            onSelectSpecialty = viewModel::selectMedicalSpecialty,
                            onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                            onSortSelect = viewModel::selectSortOption,
                            onBookFastestTurn = viewModel::bookFastestTurn,
                            onDirectBookVenue = viewModel::quickBookVenue,
                            onToggleFavorite = viewModel::toggleVenueFavorite,
                            onOpenSupport = {
                                viewModel.syncLocalBridge(silent = true)
                                showSupportSheet = true
                            }
                        )

                        AppTab.TICKETS -> TicketsScreen(
                            state = state,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestNotificationPermission = requestNotificationPermission,
                            onSelectActiveBooking = viewModel::selectActiveBooking,
                            onCompleteBooking = viewModel::completeBooking,
                            onCancelBooking = viewModel::cancelBooking,
                            onQuickRebook = viewModel::quickRebook,
                            onSubmitHistoryReview = viewModel::submitHistoryReview,
                            onExploreClick = { viewModel.selectTab(AppTab.EXPLORE) },
                            onSyncBridge = { viewModel.syncLocalBridge(silent = false) },
                            onOpenSupport = {
                                viewModel.syncLocalBridge(silent = true)
                                showSupportSheet = true
                            }
                        )

                        else -> ProfileScreen(
                            state = state,
                            hasNotificationPermission = hasNotificationPermission,
                            onResetDemoData = viewModel::factoryResetAllData,
                            onRequestNotificationPermission = requestNotificationPermission,
                            onSelectDistrict = viewModel::selectDistrict,
                            onToggleDarkMode = viewModel::toggleDarkMode,
                            onSetMuteSound = viewModel::setMuteSound,
                            onSetMuteVibration = viewModel::setMuteVibration,
                            onUpdateProfile = viewModel::updateProfile,
                            onOpenSupport = {
                                viewModel.syncLocalBridge(silent = true)
                                showSupportSheet = true
                            }
                        )
                    }

                    // Pure User Edition Floating Pill Bottom Navigation Dock (3 tabs only)
                    UserFloatingBottomDock(
                        currentTab = state.currentTab,
                        activeTicketsCount = state.activeBookings.size,
                        onSelectTab = viewModel::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Venue Details Bottom Sheet
            state.selectedVenueForDetails?.let { venue ->
                VenueDetailsSheet(
                    venue = venue,
                    bookingSessionId = state.bookingSessionId,
                    controlConfig = state.preferences.controlConfig,
                    reviews = state.reviewsByVenue[venue.id].orEmpty(),
                    onDismiss = { viewModel.openVenueDetails(null) },
                    onConfirmBooking = { name, phone, option, notes, optionalInfo, calculatedPrice ->
                        viewModel.bookTicket(
                            venueId = venue.id,
                            customerName = name,
                            customerPhone = phone,
                            selectedOption = option,
                            notes = notes,
                            optionalInfo = optionalInfo,
                            customPriceDinar = calculatedPrice
                        )
                    }
                )
            }

            // Instant Ticket + Scannable QR Pass Modal right upon booking
            state.newlyBookedTicket?.let { booked ->
                InstantTicketPassDialog(
                    booking = booked,
                    controlConfig = state.preferences.controlConfig,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestNotificationPermission = requestNotificationPermission,
                    onViewInTickets = {
                        viewModel.dismissNewlyBookedTicket()
                        viewModel.selectActiveBooking(booked.id)
                        viewModel.selectTab(AppTab.TICKETS)
                    },
                    onDismiss = viewModel::dismissNewlyBookedTicket
                )
            }

            // Floating Overlay Alert Banner on Live Queue Updates
            if (state.preferences.controlConfig.enableLiveAlerts) {
                LiveOverlayAlertBanner(
                    alert = state.activeTurnAlert,
                    onOpenTicket = { bookingId ->
                        viewModel.selectActiveBooking(bookingId)
                        viewModel.selectTab(AppTab.TICKETS)
                        viewModel.dismissTurnAlert()
                    },
                    onDismiss = viewModel::dismissTurnAlert,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            if (showSupportSheet) {
                val accountName = state.preferences.userName.ifBlank {
                    state.activeBookings.firstOrNull()?.customerName
                        ?: state.historyBookings.firstOrNull()?.customerName
                        ?: "مستخدم دوري"
                }
                val accountPhone = state.preferences.userPhone.ifBlank {
                    state.activeBookings.firstOrNull()?.customerPhone
                        ?: state.historyBookings.firstOrNull()?.customerPhone
                        ?: ""
                }
                SupportCenterSheet(
                    tickets = state.supportTickets,
                    messagesByTicket = state.supportMessagesByTicket,
                    venues = state.allVenues,
                    defaultName = accountName,
                    defaultPhone = accountPhone,
                    onSendTicket = viewModel::sendSupportMessage,
                    onSendFollowUp = viewModel::sendUserChatFollowUp,
                    onSyncReplies = { viewModel.syncLocalBridge(silent = false) },
                    onDismiss = { showSupportSheet = false }
                )
            }
        }
    }
}

@Composable
private fun UserFloatingBottomDock(
    currentTab: AppTab,
    activeTicketsCount: Int,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 10.dp,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.userTabs.forEach { tab ->
                val selected = currentTab == tab
                val icon = when (tab) {
                    AppTab.EXPLORE -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
                    AppTab.TICKETS -> if (selected) Icons.Filled.ConfirmationNumber else Icons.Outlined.ConfirmationNumber
                    AppTab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                    else -> Icons.Outlined.Explore
                }
                val bgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "dockBg"
                )
                val fgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "dockFg"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(bgColor)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (tab == AppTab.TICKETS && activeTicketsCount > 0) {
                            BadgedBox(
                                badge = { Badge { Text("$activeTicketsCount") } }
                            ) {
                                Icon(icon, contentDescription = tab.label, tint = fgColor, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = tab.label, tint = fgColor, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = fgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
