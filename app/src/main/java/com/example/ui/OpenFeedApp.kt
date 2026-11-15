package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.components.AddChannelBottomSheet
import com.example.ui.components.MediaViewerOverlay
import com.example.ui.screens.ChannelDetailScreen
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OpenFeedTheme
import kotlinx.coroutines.launch

object OpenFeedRoutes {
    const val ROUTE_FEED = "feed"
    const val ROUTE_CHANNELS = "channels"
    const val ROUTE_SAVED = "saved"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_CHANNEL_DETAIL = "channel_detail/{username}"

    fun channelDetail(username: String): String = "channel_detail/$username"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopLevelDestination(
        route = OpenFeedRoutes.ROUTE_FEED,
        labelRes = R.string.nav_feed,
        selectedIcon = Icons.Rounded.RssFeed,
        unselectedIcon = Icons.Outlined.RssFeed,
        testTag = "nav_tab_feed"
    ),
    TopLevelDestination(
        route = OpenFeedRoutes.ROUTE_CHANNELS,
        labelRes = R.string.nav_channels,
        selectedIcon = Icons.Rounded.Forum,
        unselectedIcon = Icons.Outlined.Forum,
        testTag = "nav_tab_channels"
    ),
    TopLevelDestination(
        route = OpenFeedRoutes.ROUTE_SAVED,
        labelRes = R.string.nav_saved,
        selectedIcon = Icons.Rounded.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "nav_tab_saved"
    ),
    TopLevelDestination(
        route = OpenFeedRoutes.ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
fun OpenFeedApp(viewModel: OpenFeedViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevel = topDestinations.any { it.route == currentRoute }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(OpenFeedRoutes.ROUTE_FEED) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openChannelDetail: (String) -> Unit = { username ->
        val clean = username.trim().removePrefix("@")
        if (clean.isNotEmpty()) {
            navController.navigate(OpenFeedRoutes.channelDetail(clean))
        }
    }

    OpenFeedTheme(
        themeMode = uiState.settings.themeMode,
        dynamicColor = uiState.settings.dynamicColor
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    if (currentRoute == OpenFeedRoutes.ROUTE_FEED || currentRoute == OpenFeedRoutes.ROUTE_CHANNELS) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.clearAddChannelError()
                                showAddSheet = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_channel")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.add_channel_title)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (isTopLevel && !useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isTopLevel && useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = OpenFeedRoutes.ROUTE_FEED
                        ) {
                            composable(OpenFeedRoutes.ROUTE_FEED) {
                                FeedScreen(
                                    uiState = uiState,
                                    onSetFilterMode = viewModel::setFilterMode,
                                    onSelectChannelChip = viewModel::selectChannelChip,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onRefreshAll = viewModel::refreshAllChannels,
                                    onOpenAddChannel = {
                                        viewModel.clearAddChannelError()
                                        showAddSheet = true
                                    },
                                    onQuickAddChannel = { username ->
                                        viewModel.addChannel(username)
                                    },
                                    onOpenChannelDetail = openChannelDetail,
                                    onToggleBookmark = viewModel::toggleBookmark,
                                    onOpenMedia = viewModel::openMediaViewer,
                                    onToggleAudio = viewModel::toggleAudioPlayback,
                                    onDownloadMedia = { post, attachment ->
                                        viewModel.downloadAttachment(context, post, attachment)
                                    },
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(OpenFeedRoutes.ROUTE_CHANNELS) {
                                ChannelsScreen(
                                    uiState = uiState,
                                    onAddChannel = { raw -> viewModel.addChannel(raw) },
                                    onOpenChannelDetail = openChannelDetail,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onRemoveChannel = viewModel::removeChannel
                                )
                            }

                            composable(OpenFeedRoutes.ROUTE_SAVED) {
                                SavedScreen(
                                    uiState = uiState,
                                    onOpenChannelDetail = openChannelDetail,
                                    onToggleBookmark = viewModel::toggleBookmark,
                                    onOpenMedia = viewModel::openMediaViewer,
                                    onToggleAudio = viewModel::toggleAudioPlayback,
                                    onDownloadMedia = { post, attachment ->
                                        viewModel.downloadAttachment(context, post, attachment)
                                    },
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(OpenFeedRoutes.ROUTE_SETTINGS) {
                                SettingsScreen(
                                    uiState = uiState,
                                    onSetTransportMode = viewModel::setTransportMode,
                                    onSetBridgeLangProfile = viewModel::setBridgeLangProfile,
                                    onSetUseDoH = viewModel::setUseDoH,
                                    onSetDohProvider = viewModel::setDohProvider,
                                    onSaveCustomMirror = viewModel::setCustomMirrorUrl,
                                    onSetThemeMode = viewModel::setThemeMode,
                                    onSetDynamicColor = viewModel::setDynamicColor,
                                    onSetAutoLoadMedia = viewModel::setAutoLoadMedia,
                                    onSetFontScale = viewModel::setFontScale,
                                    onRunDiagnostics = viewModel::runDiagnostics,
                                    onClearCache = viewModel::clearCache
                                )
                            }

                            composable(
                                route = OpenFeedRoutes.ROUTE_CHANNEL_DETAIL,
                                arguments = listOf(navArgument("username") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val username = backStackEntry.arguments?.getString("username").orEmpty()
                                ChannelDetailScreen(
                                    username = username,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenChannelDetail = openChannelDetail,
                                    onOpenMedia = viewModel::openMediaViewer,
                                    onDownloadMedia = { post, attachment ->
                                        viewModel.downloadAttachment(context, post, attachment)
                                    },
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showAddSheet) {
                AddChannelBottomSheet(
                    isAdding = uiState.isAddingChannel,
                    errorMessage = uiState.addChannelError,
                    subscribedUsernames = uiState.channels.map { it.username }.toSet(),
                    onAddChannel = { raw ->
                        viewModel.addChannel(raw) {
                            showAddSheet = false
                        }
                    },
                    onDismiss = { showAddSheet = false }
                )
            }

            uiState.activeMediaViewer?.let { viewerState ->
                MediaViewerOverlay(
                    state = viewerState,
                    transportMode = uiState.settings.transportMode,
                    isDownloading = uiState.downloadingKey != null,
                    onDownload = { post, attachment ->
                        viewModel.downloadAttachment(context, post, attachment)
                    },
                    onClose = viewModel::closeMediaViewer
                )
            }
        }
    }
}
