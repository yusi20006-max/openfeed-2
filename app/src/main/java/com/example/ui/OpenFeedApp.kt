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
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.screens.ArticleReaderScreen
import com.example.ui.screens.ChannelDetailScreen
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OpenFeedTheme
import kotlinx.coroutines.launch

object OpenFeedRoutes {
    const val ROUTE_FEED = "feed"
    const val ROUTE_DISCOVER = "discover"
    const val ROUTE_CHANNELS = "channels"
    const val ROUTE_SAVED = "saved"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_CHANNEL_DETAIL = "channel_detail/{username}?postId={postId}"

    fun channelDetail(username: String, postId: Long? = null): String {
        return if (postId != null && postId > 0L) {
            "channel_detail/$username?postId=$postId"
        } else {
            "channel_detail/$username"
        }
    }
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
        route = OpenFeedRoutes.ROUTE_DISCOVER,
        labelRes = R.string.nav_discover,
        selectedIcon = Icons.Rounded.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_tab_discover"
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
    val pendingDeepLink by viewModel.pendingDeepLink.collectAsStateWithLifecycle()
    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
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

    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        navController.navigate(OpenFeedRoutes.channelDetail(target.username, target.postId)) {
            launchSingleTop = true
        }
        viewModel.consumeDeepLink()
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
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
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
                                    onOpenDiscover = {
                                        navigateTopLevel(OpenFeedRoutes.ROUTE_DISCOVER)
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
                                    },
                                    httpClient = viewModel.exoHttpClient,
                                    onMarkPostRead = viewModel::markPostRead,
                                    onMarkAllRead = viewModel::markAllRead,
                                    onToggleDarkMode = viewModel::toggleDarkMode,
                                    onOpenArticle = { post, url, title ->
                                        viewModel.openArticleReader(
                                            url = url,
                                            title = title,
                                            fallbackText = post.text,
                                            postUniqueKey = post.uniqueKey
                                        )
                                    }
                                )
                            }

                            composable(OpenFeedRoutes.ROUTE_DISCOVER) {
                                DiscoverScreen(
                                    uiState = uiState,
                                    onSubscribeSuggested = viewModel::subscribeSuggested,
                                    onOpenChannelDetail = openChannelDetail
                                )
                            }

                            composable(OpenFeedRoutes.ROUTE_CHANNELS) {
                                ChannelsScreen(
                                    uiState = uiState,
                                    onAddChannel = { raw -> viewModel.addChannel(raw) },
                                    onOpenDiscover = {
                                        navigateTopLevel(OpenFeedRoutes.ROUTE_DISCOVER)
                                    },
                                    onOpenChannelDetail = openChannelDetail,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onRemoveChannel = viewModel::removeChannel,
                                    onExportJsonBackup = { uri -> viewModel.exportBackupToUri(context, uri, asOpml = false) },
                                    onImportBackup = { uri -> viewModel.importBackupFromUri(context, uri) }
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
                                    },
                                    httpClient = viewModel.exoHttpClient,
                                    onMarkPostRead = viewModel::markPostRead,
                                    onOpenArticle = { post, url, title ->
                                        viewModel.openArticleReader(
                                            url = url,
                                            title = title,
                                            fallbackText = post.text,
                                            postUniqueKey = post.uniqueKey
                                        )
                                    },
                                    onPrepareOfflineNow = viewModel::prepareOfflineNow
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
                                    onClearCache = viewModel::clearCache,
                                    onExportJsonBackup = { uri -> viewModel.exportBackupToUri(context, uri, asOpml = false) },
                                    onExportOpmlBackup = { uri -> viewModel.exportBackupToUri(context, uri, asOpml = true) },
                                    onImportBackup = { uri -> viewModel.importBackupFromUri(context, uri) },
                                    onSetBackgroundSyncEnabled = { enabled -> viewModel.setBackgroundSyncEnabled(context, enabled) },
                                    onSetSyncIntervalMinutes = { mins -> viewModel.setSyncIntervalMinutes(context, mins) },
                                    onSetNotifyFavoritesOnly = viewModel::setNotifyFavoritesOnly,
                                    onTriggerBackgroundSyncNow = { viewModel.triggerBackgroundSyncNow(context) },
                                    onSetOfflinePrefetchPolicy = viewModel::setOfflinePrefetchPolicy,
                                    onSetOfflineMaxCacheMb = viewModel::setOfflineMaxCacheMb,
                                    onSetOfflineCacheFullRssArticles = viewModel::setOfflineCacheFullRssArticles,
                                    onPrepareOfflineNow = viewModel::prepareOfflineNow
                                )
                            }

                            composable(
                                route = OpenFeedRoutes.ROUTE_CHANNEL_DETAIL,
                                arguments = listOf(
                                    navArgument("username") { type = NavType.StringType },
                                    navArgument("postId") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    }
                                )
                            ) { backStackEntry ->
                                val username = backStackEntry.arguments?.getString("username").orEmpty()
                                val rawPostId = backStackEntry.arguments?.getLong("postId") ?: -1L
                                ChannelDetailScreen(
                                    username = username,
                                    targetPostId = rawPostId.takeIf { it > 0L },
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
                    transportMode = uiState.settings.transportMode,
                    onAddChannel = { raw ->
                        viewModel.addChannel(raw) {
                            showAddSheet = false
                        }
                    },
                    onSubscribeSuggested = { suggested ->
                        viewModel.subscribeSuggested(suggested)
                        showAddSheet = false
                    },
                    onOpenDiscover = {
                        navigateTopLevel(OpenFeedRoutes.ROUTE_DISCOVER)
                    },
                    onDismiss = { showAddSheet = false }
                )
            }

            uiState.activeMediaViewer?.let { viewerState ->
                MediaViewerOverlay(
                    state = viewerState,
                    transportMode = uiState.settings.transportMode,
                    isDownloading = uiState.downloadingKey != null,
                    httpClient = viewModel.exoHttpClient,
                    onDownload = { post, attachment ->
                        viewModel.downloadAttachment(context, post, attachment)
                    },
                    onClose = viewModel::closeMediaViewer
                )
            }

            if (readerState !is ArticleReaderUiState.Closed) {
                ArticleReaderScreen(
                    state = readerState,
                    fontScale = uiState.settings.fontScale,
                    themeMode = uiState.settings.themeMode,
                    onClose = viewModel::closeArticleReader,
                    onRetry = viewModel::retryArticleReader,
                    onFontScaleChange = viewModel::setFontScale,
                    onToggleDarkMode = viewModel::toggleDarkMode
                )
            }
        }
    }
}
