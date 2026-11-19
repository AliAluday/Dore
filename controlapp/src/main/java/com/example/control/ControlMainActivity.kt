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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.cloud.AdminAccessState
import com.example.data.cloud.GoogleAuthManager
import com.example.data.cloud.UserCancelledSignInException
import com.example.data.model.AdminPermissionType
import com.example.data.model.SupportTicketStatus
import com.example.ui.components.ControlAccessDeniedScreen
import com.example.ui.components.DoriAuthScreen
import com.example.ui.components.LiveOverlayAlertBanner
import com.example.ui.components.TopStickyHeader
import com.example.ui.screens.ControlPanelScreen
import com.example.ui.screens.ControlSupportChatOverlay
import com.example.ui.screens.MerchantDashboardScreen
import com.example.ui.screens.SupportInboxScreen
import com.example.ui.screens.TeamAndProvidersScreen
import com.example.ui.screens.buildControlThreadMessages
import com.example.ui.theme.DoriTheme
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DoriAppMode
import com.example.ui.viewmodel.DoriViewModel
import com.example.ui.viewmodel.DoriViewModelFactory
import com.example.util.DoriNotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControlMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DoriNotificationHelper.ensureChannel(this)

        val firestoreDatabaseId = getString(R.string.firestore_database_id)
        val webClientId = getString(R.string.default_web_client_id)

        setContent {
            val app = applicationContext as Application
            val auth = remember { FirebaseAuth.getInstance() }
            var currentUser by remember { mutableStateOf(auth.currentUser) }
            var isSigningIn by remember { mutableStateOf(false) }
            var authError by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    currentUser = firebaseAuth.currentUser
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            LaunchedEffect(Unit) {
                if (auth.currentUser == null) {
                    isSigningIn = true
                    val silentUser = GoogleAuthManager.attemptSilentSignIn(this@ControlMainActivity, webClientId)
                    if (silentUser != null) {
                        currentUser = silentUser
                    }
                    isSigningIn = false
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val signedInUser = currentUser
                if (signedInUser == null) {
                    DoriTheme(darkTheme = true) {
                        DoriAuthScreen(
                            isControlApp = true,
                            isLoading = isSigningIn,
                            errorMessage = authError,
                            onSignInClick = {
                                if (isSigningIn) return@DoriAuthScreen
                                scope.launch {
                                    isSigningIn = true
                                    authError = null
                                    GoogleAuthManager.signInInteractive(this@ControlMainActivity, webClientId)
                                        .onSuccess { user -> currentUser = user }
                                        .onFailure { err ->
                                            if (err !is UserCancelledSignInException) {
                                                authError = err.message ?: "تعذر تسجيل الدخول"
                                            }
                                        }
                                    isSigningIn = false
                                }
                            }
                        )
                    }
                } else {
                    key(signedInUser.uid) {
                        val factory = remember(app, signedInUser.uid, firestoreDatabaseId) {
                            DoriViewModelFactory(
                                application = app,
                                appMode = DoriAppMode.CONTROL_APP,
                                firestoreDatabaseId = firestoreDatabaseId
                            )
                        }
                        val viewModel: DoriViewModel = viewModel(
                            key = "ctrl_${signedInUser.uid}",
                            factory = factory
                        )
                        LaunchedEffect(signedInUser.uid) {
                            viewModel.ensureCloudSyncStarted()
                        }
                        ControlAppRoot(
                            viewModel = viewModel,
                            onSignOut = {
                                scope.launch {
                                    viewModel.prepareSignOut()
                                    GoogleAuthManager.signOut(this@ControlMainActivity)
                                    authError = null
                                    currentUser = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlAppRoot(
    viewModel: DoriViewModel,
    onSignOut: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var openSupportTicketId by remember { mutableStateOf<Long?>(null) }
    val openSupportTicket = openSupportTicketId?.let { id ->
        state.supportTickets.find { it.id == id }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(lifecycleOwner, viewModel.isCloudSyncEnabled) {
        if (!viewModel.isCloudSyncEnabled) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    viewModel.syncLocalBridge(silent = true)
                    delay(3500L)
                }
            }
        }
    }

    LaunchedEffect(state.activeTurnAlert) {
        if (state.activeTurnAlert != null) {
            delay(5000L)
            viewModel.dismissTurnAlert()
        }
    }

    BackHandler(enabled = openSupportTicket == null && state.currentTab != AppTab.CONTROL_FEATURES) {
        viewModel.selectTab(AppTab.CONTROL_FEATURES)
    }

    DoriTheme(darkTheme = state.preferences.isDarkMode) {
        if (viewModel.isCloudSyncEnabled && state.cloudSync.adminAccess == AdminAccessState.CHECKING) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                        Text(
                            text = "جاري التحقق من صلاحيات الإدارة السحابية...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@DoriTheme
        }

        if (viewModel.isCloudSyncEnabled && state.cloudSync.adminAccess == AdminAccessState.UNAUTHORIZED) {
            ControlAccessDeniedScreen(
                userEmail = state.cloudSync.userEmail,
                userUid = state.cloudSync.userUid,
                onRetry = viewModel::retryAdminAccessCheck,
                onSignOut = onSignOut
            )
            return@DoriTheme
        }

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
                        searchQuery = "",
                        isSearchExpanded = false,
                        onToggleSearch = {},
                        onSearchChange = {},
                        onSelectDistrict = viewModel::selectDistrict,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onOpenTickets = { viewModel.selectTab(AppTab.CONTROL_QUEUES) },
                        supportBadgeCount = state.supportAlertCount,
                        onOpenSupport = { viewModel.selectTab(AppTab.CONTROL_SUPPORT) },
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
                            onSelectProvider = viewModel::setActiveProvider,
                            onAcceptTicket = viewModel::acceptBookingTicket,
                            onRejectTicket = viewModel::rejectBookingTicket,
                            onSyncBridge = { viewModel.syncLocalBridge(silent = false) },
                            onCallNext = viewModel::callNextCustomer,
                            onSkipCurrent = viewModel::skipCurrentCustomer,
                            onCallCustomerByName = viewModel::callCustomerByName,
                            onMarkNoShow = viewModel::markCustomerNoShow,
                            onTogglePauseQueue = viewModel::togglePauseQueue,
                            onToggleAcceptingBookings = viewModel::toggleAcceptingBookings,
                            onAddWalkIn = viewModel::addWalkIn
                        )

                        AppTab.CONTROL_TEAM -> TeamAndProvidersScreen(
                            state = state,
                            onSelectActiveAdmin = viewModel::setActiveAdmin,
                            onAddAdmin = viewModel::addAdminAccount,
                            onUpdateAdmin = viewModel::updateAdminAccount,
                            onDeleteAdmin = viewModel::deleteAdminAccount,
                            onSelectActiveProvider = viewModel::setActiveProvider,
                            onAddProvider = viewModel::addServiceProvider,
                            onUpdateProvider = viewModel::updateServiceProvider,
                            onDeleteProvider = viewModel::deleteServiceProvider,
                            onOpenProviderWorkspace = { providerId ->
                                viewModel.setActiveProvider(providerId)
                                viewModel.selectTab(AppTab.CONTROL_QUEUES)
                            },
                            onAuthorizeCloudAdmin = viewModel::authorizeCloudAdmin,
                            onRevokeCloudAdmin = viewModel::revokeCloudAdmin
                        )

                        AppTab.CONTROL_SUPPORT -> SupportInboxScreen(
                            state = state,
                            onSyncBridge = { viewModel.syncLocalBridge(silent = false) },
                            onReplyTicket = viewModel::replyToSupportTicket,
                            onUpdateStatus = viewModel::updateSupportTicketStatus,
                            onOpenChatTicket = { ticketId -> openSupportTicketId = ticketId }
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
                            onFactoryReset = viewModel::factoryResetAllData,
                            onSyncBridge = { viewModel.syncLocalBridge(silent = false) },
                            onSignOut = onSignOut
                        )
                    }

                    ControlFloatingBottomDock(
                        currentTab = state.currentTab,
                        activeTicketsCount = state.activeBookings.size,
                        supportAlertCount = state.supportAlertCount,
                        onSelectTab = viewModel::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
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

            if (openSupportTicket != null) {
                val canReply = state.activeAdmin?.hasPermission(AdminPermissionType.REPLY_SUPPORT) ?: true
                ControlSupportChatOverlay(
                    ticket = openSupportTicket,
                    messages = buildControlThreadMessages(
                        openSupportTicket,
                        state.supportMessagesByTicket[openSupportTicket.externalId].orEmpty()
                    ),
                    canReply = canReply,
                    onSendReply = { text -> viewModel.replyToSupportTicket(openSupportTicket.id, text) },
                    onMarkInReview = { viewModel.updateSupportTicketStatus(openSupportTicket.id, SupportTicketStatus.IN_REVIEW) },
                    onCloseTicket = { viewModel.updateSupportTicketStatus(openSupportTicket.id, SupportTicketStatus.CLOSED) },
                    onReopenTicket = { viewModel.updateSupportTicketStatus(openSupportTicket.id, SupportTicketStatus.IN_REVIEW) },
                    onDismiss = { openSupportTicketId = null }
                )
            }
        }
    }
}

@Composable
private fun ControlFloatingBottomDock(
    currentTab: AppTab,
    activeTicketsCount: Int,
    supportAlertCount: Int,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 560.dp)
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
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.controlTabs.forEach { tab ->
                val selected = currentTab == tab
                val icon = when (tab) {
                    AppTab.CONTROL_FEATURES -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
                    AppTab.CONTROL_VENUES -> if (selected) Icons.Filled.Storefront else Icons.Outlined.Storefront
                    AppTab.CONTROL_QUEUES -> if (selected) Icons.Filled.Group else Icons.Outlined.Group
                    AppTab.CONTROL_TEAM -> if (selected) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings
                    AppTab.CONTROL_SUPPORT -> if (selected) Icons.Filled.SupportAgent else Icons.Outlined.SupportAgent
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

                val badgeCount = when (tab) {
                    AppTab.CONTROL_QUEUES -> activeTicketsCount
                    AppTab.CONTROL_SUPPORT -> supportAlertCount
                    else -> 0
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(bgColor)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (badgeCount > 0) {
                            BadgedBox(
                                badge = { Badge { Text("$badgeCount") } }
                            ) {
                                Icon(icon, contentDescription = tab.label, tint = fgColor, modifier = Modifier.size(17.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = tab.label, tint = fgColor, modifier = Modifier.size(17.dp))
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = fgColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
