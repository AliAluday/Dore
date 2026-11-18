package com.example.control

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.LiveOverlayAlertBanner
import com.example.ui.components.TopStickyHeader
import com.example.ui.screens.ControlPanelScreen
import com.example.ui.screens.MerchantDashboardScreen
import com.example.ui.theme.DoriTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DoriAppMode
import com.example.ui.viewmodel.DoriViewModel
import com.example.ui.viewmodel.DoriViewModelFactory
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.delay

class ControlMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DoriNotificationHelper.ensureChannel(this)

        setContent {
            val app = applicationContext as Application
            val factory = remember(app) {
                DoriViewModelFactory(
                    application = app,
                    appMode = DoriAppMode.CONTROL_APP
                )
            }
            val viewModel: DoriViewModel = viewModel(factory = factory)

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ControlAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ControlAppRoot(
    viewModel: DoriViewModel
) {
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

    BackHandler(enabled = state.currentTab != AppTab.CONTROL_FEATURES) {
        viewModel.selectTab(AppTab.CONTROL_FEATURES)
    }

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
                        searchQuery = "",
                        isSearchExpanded = false,
                        onToggleSearch = {},
                        onSearchChange = {},
                        onSelectDistrict = viewModel::selectDistrict,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onOpenTickets = { viewModel.selectTab(AppTab.CONTROL_QUEUES) },
                        brandTitle = "دوري تحكم",
                        showSearchAction = false,
                        showTicketsBadge = false
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (state.currentTab) {
                        AppTab.CONTROL_QUEUES -> MerchantDashboardScreen(
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

                        else -> ControlPanelScreen(
                            state = state,
                            activeTab = state.currentTab,
                            onUpdateControlConfig = viewModel::updateControlConfig,
                            onToggleCategoryEnabled = viewModel::toggleCategoryEnabled,
                            onSetBroadcastMessage = viewModel::setBroadcastMessage,
                            onAddCustomVenue = viewModel::addCustomVenue,
                            onToggleVenueOpen = viewModel::toggleVenueOpen,
                            onAdjustVenuePrice = viewModel::adjustVenuePrice,
                            onAdjustVenueWait = viewModel::adjustVenueWaitMinutes,
                            onDeleteVenue = viewModel::deleteVenue,
                            onSetDistrictVenuesOpen = viewModel::setDistrictVenuesOpen,
                            onAdvanceAllQueues = viewModel::advanceAllQueuesNow,
                            onCompleteAllActive = viewModel::completeAllActiveBookings,
                            onCancelAllActive = viewModel::cancelAllActiveBookings,
                            onResetAllQueues = viewModel::resetAllQueuesToOne,
                            onAdjustLoyaltyPoints = viewModel::adjustLoyaltyPoints,
                            onResetLoyaltyPoints = viewModel::resetLoyaltyPoints,
                            onFactoryReset = viewModel::factoryResetAllData
                        )
                    }

                    ControlFloatingBottomDock(
                        currentTab = state.currentTab,
                        activeTicketsCount = state.activeBookings.size,
                        onSelectTab = viewModel::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            if (state.preferences.controlConfig.enableLiveAlerts) {
                LiveOverlayAlertBanner(
                    alert = state.activeTurnAlert,
                    onOpenTicket = {
                        viewModel.selectTab(AppTab.CONTROL_QUEUES)
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
private fun ControlFloatingBottomDock(
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
            AppTab.controlTabs.forEach { tab ->
                val selected = currentTab == tab
                val icon = when (tab) {
                    AppTab.CONTROL_FEATURES -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
                    AppTab.CONTROL_VENUES -> if (selected) Icons.Filled.Storefront else Icons.Outlined.Storefront
                    AppTab.CONTROL_QUEUES -> if (selected) Icons.Filled.Group else Icons.Outlined.Group
                    AppTab.CONTROL_COMMANDS -> if (selected) Icons.Filled.Bolt else Icons.Outlined.Bolt
                    else -> Icons.Outlined.Tune
                }
                val bgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "ctrlDockBg"
                )
                val fgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "ctrlDockFg"
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
                        if (tab == AppTab.CONTROL_QUEUES && activeTicketsCount > 0) {
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
