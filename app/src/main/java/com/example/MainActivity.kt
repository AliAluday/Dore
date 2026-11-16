package com.example

import android.Manifest
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
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.InstantTicketPassDialog
import com.example.ui.components.LiveOverlayAlertBanner
import com.example.ui.components.TopStickyHeader
import com.example.ui.components.VenueDetailsSheet
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.MerchantDashboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.TicketsScreen
import com.example.ui.theme.DoriTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DoriViewModel
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DoriNotificationHelper.ensureChannel(this)
        setContent {
            val viewModel: DoriViewModel = viewModel()
            DoriAppRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun DoriAppRoot(viewModel: DoriViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotificationPermission by remember {
        mutableStateOf(DoriNotificationHelper.hasNotificationPermission(context))
    }

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

    LaunchedEffect(state.activeTurnAlert) {
        if (state.activeTurnAlert != null) {
            delay(5000L)
            viewModel.dismissTurnAlert()
        }
    }

    BackHandler(enabled = state.currentTab != AppTab.EXPLORE) {
        viewModel.selectTab(AppTab.EXPLORE)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        DoriTheme(darkTheme = state.preferences.isDarkMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = 86.dp)
                        )
                    },
                    topBar = {
                        TopStickyHeader(
                            selectedDistrict = state.preferences.selectedDistrict,
                            isDarkMode = state.preferences.isDarkMode,
                            activeBookingCount = state.activeBookings.size,
                            isSimulationRunning = state.isLiveSimulationRunning,
                            onSelectDistrict = viewModel::selectDistrict,
                            onToggleDarkMode = viewModel::toggleDarkMode,
                            onOpenTickets = { viewModel.selectTab(AppTab.TICKETS) }
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
                                onSelectDistrict = viewModel::selectDistrict,
                                onSelectArea = viewModel::selectArea,
                                onSearchChange = viewModel::updateSearchQuery,
                                onCategorySelect = viewModel::selectCategory,
                                onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                onSortSelect = viewModel::selectSortOption,
                                onBookFastestTurn = viewModel::bookFastestTurn,
                                onVenueClick = { viewModel.openVenueDetails(it.id) },
                                onToggleFavorite = viewModel::toggleVenueFavorite
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
                                onExploreClick = { viewModel.selectTab(AppTab.EXPLORE) }
                            )

                            AppTab.MERCHANT -> MerchantDashboardScreen(
                                state = state,
                                onSelectVenue = viewModel::selectMerchantVenue,
                                onCallNext = viewModel::callNextCustomer,
                                onSkipCurrent = viewModel::skipCurrentCustomer,
                                onCallCustomerByName = viewModel::callCustomerByName,
                                onMarkNoShow = viewModel::markCustomerNoShow,
                                onTogglePauseQueue = viewModel::togglePauseQueue,
                                onToggleAcceptingBookings = viewModel::toggleAcceptingBookings,
                                onAddWalkIn = viewModel::addWalkIn,
                                onToggleLiveSimulation = viewModel::toggleLiveSimulation
                            )

                            AppTab.PROFILE -> ProfileScreen(
                                state = state,
                                hasNotificationPermission = hasNotificationPermission,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onSaveProfile = viewModel::updateProfile,
                                onToggleDarkMode = viewModel::toggleDarkMode,
                                onSetMuteSound = viewModel::setMuteSound,
                                onSetMuteVibration = viewModel::setMuteVibration
                            )
                        }

                        // Modern Floating Pill Bottom Navigation Dock
                        FloatingBottomDock(
                            currentTab = state.currentTab,
                            activeTicketsCount = state.activeBookings.size,
                            onSelectTab = viewModel::selectTab,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                // Venue Details Bottom Sheet
                state.selectedVenueForDetails?.let { venue ->
                    VenueDetailsSheet(
                        venue = venue,
                        reviews = state.reviewsByVenue[venue.id].orEmpty(),
                        userProfile = state.preferences,
                        onDismiss = { viewModel.openVenueDetails(null) },
                        onConfirmBooking = { name, phone, option, notes ->
                            viewModel.bookTicket(
                                venueId = venue.id,
                                customerName = name,
                                customerPhone = phone,
                                selectedOption = option,
                                notes = notes
                            )
                        }
                    )
                }

                // Instant Ticket + Scannable QR Pass Modal right upon booking
                state.newlyBookedTicket?.let { booked ->
                    InstantTicketPassDialog(
                        booking = booked,
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
        }
    }
}

@Composable
private fun FloatingBottomDock(
    currentTab: AppTab,
    activeTicketsCount: Int,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 14.dp,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val icon = when (tab) {
                    AppTab.EXPLORE -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
                    AppTab.TICKETS -> if (selected) Icons.Filled.ConfirmationNumber else Icons.Outlined.ConfirmationNumber
                    AppTab.MERCHANT -> if (selected) Icons.Filled.Storefront else Icons.Outlined.Storefront
                    AppTab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
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
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (tab == AppTab.TICKETS && activeTicketsCount > 0) {
                            BadgedBox(
                                badge = { Badge { Text("$activeTicketsCount") } }
                            ) {
                                Icon(icon, contentDescription = tab.label, tint = fgColor, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = tab.label, tint = fgColor, modifier = Modifier.size(20.dp))
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
