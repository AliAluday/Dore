package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: DoriViewModel = viewModel()
            DoriAppRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun DoriAppRoot(viewModel: DoriViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Return to Explore when pressing back from secondary tabs
    BackHandler(enabled = state.currentTab != AppTab.EXPLORE) {
        viewModel.selectTab(AppTab.EXPLORE)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        DoriTheme(darkTheme = state.preferences.isDarkMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopStickyHeader(
                            selectedCity = state.preferences.selectedCity,
                            isDarkMode = state.preferences.isDarkMode,
                            activeBookingCount = state.activeBookings.size,
                            isSimulationRunning = state.isLiveSimulationRunning,
                            onSelectCity = viewModel::selectCity,
                            onToggleDarkMode = viewModel::toggleDarkMode,
                            onOpenTickets = { viewModel.selectTab(AppTab.TICKETS) }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = state.currentTab == tab
                                val icon = when (tab) {
                                    AppTab.EXPLORE -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
                                    AppTab.TICKETS -> if (selected) Icons.Filled.ConfirmationNumber else Icons.Outlined.ConfirmationNumber
                                    AppTab.MERCHANT -> if (selected) Icons.Filled.Storefront else Icons.Outlined.Storefront
                                    AppTab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                                }

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    label = { Text(tab.label) },
                                    icon = {
                                        if (tab == AppTab.TICKETS && state.activeBookings.isNotEmpty()) {
                                            BadgedBox(
                                                badge = {
                                                    Badge { Text("${state.activeBookings.size}") }
                                                }
                                            ) {
                                                Icon(icon, contentDescription = tab.label)
                                            }
                                        } else {
                                            Icon(icon, contentDescription = tab.label)
                                        }
                                    },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
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
                                onSaveProfile = viewModel::updateProfile,
                                onToggleDarkMode = viewModel::toggleDarkMode,
                                onSetMuteSound = viewModel::setMuteSound,
                                onSetMuteVibration = viewModel::setMuteVibration
                            )
                        }
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
