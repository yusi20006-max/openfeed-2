package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.BridgeLangProfile
import com.example.data.model.ChannelInfo
import com.example.data.model.ConnectionDiagnostics
import com.example.data.model.DeepLinkTarget
import com.example.data.model.DohProvider
import com.example.data.model.FeedPost
import com.example.data.model.FeedSourceType
import com.example.data.model.MediaAttachment
import com.example.data.model.OfflinePrefetchPolicy
import com.example.data.model.OfflinePrepProgress
import com.example.data.model.OfflineVaultStats
import com.example.data.model.StarterChannels
import com.example.data.model.SuggestedChannel
import com.example.data.model.ThemeMode
import com.example.data.model.TransportMode
import com.example.data.model.UserSettings
import com.example.data.network.DeepLinkParser
import com.example.data.network.DeepLinkResult
import com.example.data.network.OpenFeedNetworkClient
import com.example.data.network.UrlRewriter
import com.example.data.repository.OpenFeedRepository
import com.example.work.BackgroundSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

enum class FeedFilterMode {
    ALL,
    UNREAD,
    FAVORITES,
    TELEGRAM,
    RSS
}

sealed interface MediaViewerState {
    data class PhotoGallery(
        val post: FeedPost,
        val photos: List<MediaAttachment>,
        val initialIndex: Int
    ) : MediaViewerState

    data class VideoPlayer(
        val post: FeedPost,
        val video: MediaAttachment
    ) : MediaViewerState
}

sealed interface ArticleReaderUiState {
    data object Closed : ArticleReaderUiState

    data class Loading(
        val url: String,
        val initialTitle: String,
        val fallbackText: String
    ) : ArticleReaderUiState

    data class Success(
        val article: com.example.data.network.ExtractedArticle,
        val fallbackText: String
    ) : ArticleReaderUiState

    data class Error(
        val url: String,
        val initialTitle: String,
        val message: String,
        val fallbackText: String
    ) : ArticleReaderUiState
}

data class FeedUiState(
    val channels: List<ChannelInfo> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val bookmarkedPosts: List<FeedPost> = emptyList(),
    val filterMode: FeedFilterMode = FeedFilterMode.ALL,
    val selectedChannelChip: String? = null,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val isAddingChannel: Boolean = false,
    val addChannelError: String? = null,
    val settings: UserSettings = UserSettings(),
    val cachedPostCount: Int = 0,
    val diagnostics: ConnectionDiagnostics = ConnectionDiagnostics(),
    val activeMediaViewer: MediaViewerState? = null,
    val downloadingKey: String? = null,
    val activeAudioUrl: String? = null,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val totalUnreadCount: Int = 0,
    val offlineVaultStats: OfflineVaultStats = OfflineVaultStats(),
    val offlinePrepProgress: OfflinePrepProgress? = null
)

class OpenFeedViewModel(
    private val repository: OpenFeedRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val networkClient: OpenFeedNetworkClient
) : ViewModel() {

    val exoHttpClient: OkHttpClient
        get() = networkClient.httpClient

    private val filterModeFlow = MutableStateFlow(FeedFilterMode.ALL)
    private val selectedChipFlow = MutableStateFlow<String?>(null)
    private val searchQueryFlow = MutableStateFlow("")
    private val isRefreshingFlow = MutableStateFlow(false)
    private val isAddingChannelFlow = MutableStateFlow(false)
    private val addChannelErrorFlow = MutableStateFlow<String?>(null)
    private val diagnosticsFlow = MutableStateFlow(ConnectionDiagnostics())
    private val activeMediaViewerFlow = MutableStateFlow<MediaViewerState?>(null)
    private val downloadingKeyFlow = MutableStateFlow<String?>(null)
    private val activeAudioUrlFlow = MutableStateFlow<String?>(null)
    private val offlinePrepProgressFlow = MutableStateFlow<OfflinePrepProgress?>(null)

    private val loadingOlderChannels = MutableStateFlow<Set<String>>(emptySet())
    private val channelOlderCursorMap = MutableStateFlow<Map<String, Long?>>(emptyMap())

    private val _pendingDeepLink = MutableStateFlow<DeepLinkTarget?>(null)
    val pendingDeepLink: StateFlow<DeepLinkTarget?> = _pendingDeepLink.asStateFlow()

    private val _readerState = MutableStateFlow<ArticleReaderUiState>(ArticleReaderUiState.Closed)
    val readerState: StateFlow<ArticleReaderUiState> = _readerState.asStateFlow()
    private var readerJob: kotlinx.coroutines.Job? = null
    private var lastReaderArgs: Triple<String, String, String>? = null

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val baseDataFlow = combine(
        repository.channelsFlow,
        repository.unifiedFeedFlow,
        repository.favoriteFeedFlow,
        repository.bookmarkedPostsFlow,
        preferencesRepository.settingsFlow
    ) { channels, unified, favorite, bookmarked, settings ->
        BaseDataBundle(channels, unified, favorite, bookmarked, settings)
    }

    private val uiControlFlow = combine(
        filterModeFlow,
        selectedChipFlow,
        searchQueryFlow,
        isRefreshingFlow,
        isAddingChannelFlow
    ) { filterMode, selectedChip, query, refreshing, adding ->
        UiControlBundle(filterMode, selectedChip, query, refreshing, adding)
    }

    private val offlineBundleFlow = combine(
        repository.offlineStatsFlow,
        offlinePrepProgressFlow
    ) { stats, progress ->
        stats to progress
    }

    private val extraControlFlow = combine(
        addChannelErrorFlow,
        repository.cachedPostCountFlow,
        diagnosticsFlow,
        activeMediaViewerFlow,
        downloadingKeyFlow
    ) { addError, count, diag, viewer, dlKey ->
        ExtraControlBundle(addError, count, diag, viewer, dlKey)
    }

    private val feedMetaFlow = combine(
        activeAudioUrlFlow,
        repository.unreadCountsFlow,
        offlineBundleFlow
    ) { audioUrl, unreadCounts, offlineBundle ->
        FeedMetaBundle(audioUrl, unreadCounts, offlineBundle.first, offlineBundle.second)
    }

    val uiState: StateFlow<FeedUiState> = combine(
        baseDataFlow,
        uiControlFlow,
        extraControlFlow,
        feedMetaFlow
    ) { base, ctrl, extra, meta ->
        val rawFeed = when (ctrl.filterMode) {
            FeedFilterMode.ALL -> base.unified
            FeedFilterMode.UNREAD -> base.unified.filter { !it.isRead }
            FeedFilterMode.FAVORITES -> base.favorite
            FeedFilterMode.TELEGRAM -> base.unified.filter { it.sourceType == FeedSourceType.TELEGRAM }
            FeedFilterMode.RSS -> base.unified.filter { it.sourceType == FeedSourceType.RSS }
        }
        val byChip = if (ctrl.selectedChip != null) {
            rawFeed.filter { it.channelUsername.equals(ctrl.selectedChip, ignoreCase = true) }
        } else {
            rawFeed
        }
        val query = ctrl.searchQuery.trim()
        val filteredPosts = if (query.isEmpty()) {
            byChip
        } else {
            byChip.filter { post ->
                post.text.contains(query, ignoreCase = true) ||
                    post.channelTitle.contains(query, ignoreCase = true) ||
                    post.channelUsername.contains(query, ignoreCase = true) ||
                    post.hashtags.any { it.contains(query, ignoreCase = true) }
            }
        }

        FeedUiState(
            channels = base.channels,
            posts = filteredPosts,
            bookmarkedPosts = base.bookmarked,
            filterMode = ctrl.filterMode,
            selectedChannelChip = ctrl.selectedChip,
            searchQuery = ctrl.searchQuery,
            isRefreshing = ctrl.refreshing,
            isAddingChannel = ctrl.adding,
            addChannelError = extra.addError,
            settings = base.settings,
            cachedPostCount = extra.cachedCount,
            diagnostics = extra.diagnostics,
            activeMediaViewer = extra.viewer,
            downloadingKey = extra.dlKey,
            activeAudioUrl = meta.audioUrl,
            unreadCounts = meta.unreadCounts,
            totalUnreadCount = meta.unreadCounts.values.sum(),
            offlineVaultStats = meta.offlineVaultStats,
            offlinePrepProgress = meta.offlinePrepProgress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedUiState()
    )

    fun observeChannel(username: String) = repository.observeChannel(username)

    fun observeChannelPosts(username: String) = repository.observeChannelPosts(username)

    fun isChannelLoadingOlder(username: String): StateFlow<Boolean> =
        loadingOlderChannels
            .combine(MutableStateFlow(username)) { set, u -> u in set }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setFilterMode(mode: FeedFilterMode) {
        filterModeFlow.value = mode
        if (mode == FeedFilterMode.FAVORITES) {
            selectedChipFlow.value = null
        }
    }

    fun selectChannelChip(username: String?) {
        selectedChannelChipValue(username)
    }

    private fun selectedChannelChipValue(username: String?) {
        selectedChipFlow.value = if (selectedChipFlow.value == username) null else username
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun clearAddChannelError() {
        addChannelErrorFlow.value = null
    }

    fun addChannel(rawInput: String, markFavorite: Boolean = false, onAdded: ((String) -> Unit)? = null) {
        val trimmed = rawInput.trim()
        val isRss = UrlRewriter.isRssFeedUrl(trimmed)
        if (!isRss && UrlRewriter.normalizeUsername(trimmed) == null) {
            addChannelErrorFlow.value = "Enter @username, t.me/channel, or RSS feed URL"
            return
        }

        viewModelScope.launch {
            isAddingChannelFlow.value = true
            addChannelErrorFlow.value = null
            runCatching {
                repository.addFeedFromInput(trimmed, favorite = markFavorite)
            }.onSuccess { info ->
                isAddingChannelFlow.value = false
                val label = if (info.sourceType == FeedSourceType.RSS) info.title else "@${info.username}"
                _events.tryEmit("Synced $label (${info.lastTransportUsed ?: "OK"})")
                onAdded?.invoke(info.username)
            }.onFailure { err ->
                isAddingChannelFlow.value = false
                addChannelErrorFlow.value = err.message ?: "Failed to fetch feed"
            }
        }
    }

    fun subscribeSuggested(suggested: SuggestedChannel) {
        if (isAddingChannelFlow.value) return
        viewModelScope.launch {
            isAddingChannelFlow.value = true
            addChannelErrorFlow.value = null
            runCatching {
                if (suggested.sourceType == FeedSourceType.RSS && !suggested.feedUrl.isNullOrBlank()) {
                    repository.addAndFetchRss(
                        feedUrl = suggested.feedUrl,
                        fallbackTitle = suggested.title,
                        fallbackLogoUrl = suggested.logoUrl
                    )
                } else {
                    repository.addAndFetchChannel(suggested.username)
                }
            }.onSuccess { info ->
                isAddingChannelFlow.value = false
                _events.tryEmit("Subscribed to ${info.title}")
            }.onFailure { err ->
                isAddingChannelFlow.value = false
                _events.tryEmit("Failed to sync ${suggested.title}: ${err.message}")
            }
        }
    }

    fun refreshAllChannels() {
        if (isRefreshingFlow.value) return
        viewModelScope.launch {
            isRefreshingFlow.value = true
            runCatching {
                repository.refreshAllChannels()
            }.onSuccess { (ok, fail) ->
                isRefreshingFlow.value = false
                when {
                    ok == 0 && fail == 0 -> _events.tryEmit("Add a channel or RSS feed to start reading")
                    fail == 0 -> _events.tryEmit("Updated $ok feed${if (ok == 1) "" else "s"}")
                    else -> _events.tryEmit("Updated $ok, $fail offline/unreachable")
                }
            }.onFailure { err ->
                isRefreshingFlow.value = false
                _events.tryEmit("Refresh failed: ${err.message}")
            }
        }
    }

    fun refreshSingleChannel(username: String) {
        viewModelScope.launch {
            isRefreshingFlow.value = true
            runCatching {
                repository.refreshChannelByKey(username)
            }.onSuccess { info ->
                isRefreshingFlow.value = false
                _events.tryEmit("${info.title} refreshed • ${info.lastLatencyMs ?: 0}ms")
            }.onFailure { err ->
                isRefreshingFlow.value = false
                _events.tryEmit("Could not refresh: ${err.message}")
            }
        }
    }

    fun loadOlderPosts(username: String, currentPosts: List<FeedPost>) {
        if (username in loadingOlderChannels.value) return
        val channelInfo = uiState.value.channels.firstOrNull { it.username == username }
        if (channelInfo?.sourceType == FeedSourceType.RSS) return
        val cursor = channelOlderCursorMap.value[username]
            ?: currentPosts.minOfOrNull { it.postId }
            ?: return

        viewModelScope.launch {
            loadingOlderChannels.value = loadingOlderChannels.value + username
            runCatching {
                repository.fetchOlderPosts(username, cursor)
            }.onSuccess { nextCursor ->
                channelOlderCursorMap.value = channelOlderCursorMap.value + (username to nextCursor)
                loadingOlderChannels.value = loadingOlderChannels.value - username
            }.onFailure { err ->
                loadingOlderChannels.value = loadingOlderChannels.value - username
                _events.tryEmit("No older posts available (${err.message})")
            }
        }
    }

    fun toggleFavorite(username: String, currentFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(username, currentFavorite)
        }
    }

    fun removeChannel(username: String) {
        val label = uiState.value.channels.firstOrNull { it.username == username }?.title
            ?: if (username.startsWith("rss_")) "RSS feed" else "@$username"
        viewModelScope.launch {
            repository.removeChannel(username)
            if (selectedChipFlow.value == username) {
                selectedChipFlow.value = null
            }
            _events.tryEmit("Removed $label")
        }
    }

    fun toggleBookmark(post: FeedPost) {
        viewModelScope.launch {
            repository.markPostAsRead(post.uniqueKey)
            repository.toggleBookmark(post)
            val msg = if (!post.isBookmarked) "Saved for offline reading" else "Removed from Saved"
            _events.tryEmit(msg)
        }
    }

    fun markPostRead(uniqueKey: String) {
        viewModelScope.launch {
            repository.markPostAsRead(uniqueKey)
        }
    }

    fun markChannelRead(username: String) {
        viewModelScope.launch {
            repository.markChannelAsRead(username)
            _events.tryEmit("Marked channel as read")
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
            _events.tryEmit("Marked all posts as read")
        }
    }

    fun openMediaViewer(state: MediaViewerState) {
        activeMediaViewerFlow.value = state
    }

    fun closeMediaViewer() {
        activeMediaViewerFlow.value = null
    }

    fun toggleAudioPlayback(url: String) {
        activeAudioUrlFlow.value = if (activeAudioUrlFlow.value == url) null else url
    }

    fun openArticleReader(
        url: String,
        title: String = "",
        fallbackText: String = "",
        postUniqueKey: String? = null,
        forceRefresh: Boolean = false
    ) {
        if (postUniqueKey != null) {
            markPostRead(postUniqueKey)
        }
        val cleanUrl = url.trim()
        lastReaderArgs = Triple(cleanUrl, title, fallbackText)
        readerJob?.cancel()
        _readerState.value = ArticleReaderUiState.Loading(
            url = cleanUrl,
            initialTitle = title,
            fallbackText = fallbackText
        )
        readerJob = viewModelScope.launch {
            runCatching {
                repository.fetchArticle(cleanUrl, forceRefresh = forceRefresh)
            }.onSuccess { extracted ->
                _readerState.value = ArticleReaderUiState.Success(
                    article = extracted,
                    fallbackText = fallbackText
                )
            }.onFailure { err ->
                _readerState.value = ArticleReaderUiState.Error(
                    url = cleanUrl,
                    initialTitle = title,
                    message = err.message ?: "Failed to load article",
                    fallbackText = fallbackText
                )
            }
        }
    }

    fun retryArticleReader() {
        val args = lastReaderArgs ?: return
        openArticleReader(
            url = args.first,
            title = args.second,
            fallbackText = args.third,
            forceRefresh = true
        )
    }

    fun closeArticleReader() {
        readerJob?.cancel()
        _readerState.value = ArticleReaderUiState.Closed
    }

    fun downloadAttachment(context: Context, post: FeedPost, attachment: MediaAttachment) {
        val key = attachment.url
        if (downloadingKeyFlow.value != null) return
        viewModelScope.launch {
            downloadingKeyFlow.value = key
            runCatching {
                repository.downloadAttachment(context, post, attachment)
            }.onSuccess { res ->
                downloadingKeyFlow.value = null
                _events.tryEmit("Saved to ${res.displayPath}")
            }.onFailure { err ->
                downloadingKeyFlow.value = null
                _events.tryEmit("Download failed: ${err.message}")
            }
        }
    }

    fun setTransportMode(mode: TransportMode) {
        viewModelScope.launch {
            preferencesRepository.setTransportMode(mode)
            _events.tryEmit("Transport: ${mode.label}")
        }
    }

    fun setBridgeLangProfile(profile: BridgeLangProfile) {
        viewModelScope.launch {
            preferencesRepository.setBridgeLangProfile(profile)
        }
    }

    fun setUseDoH(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseDoH(enabled)
            networkClient.dohDns.clearCache()
        }
    }

    fun setDohProvider(provider: DohProvider) {
        viewModelScope.launch {
            preferencesRepository.setDohProvider(provider)
            networkClient.dohDns.clearCache()
        }
    }

    fun setCustomMirrorUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setCustomMirrorUrl(url)
            _events.tryEmit("Custom mirror saved")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun toggleDarkMode(currentlyDark: Boolean) {
        val nextMode = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch {
            preferencesRepository.setThemeMode(nextMode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setAutoLoadMedia(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoLoadMedia(enabled) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setFontScale(scale) }
    }

    fun setOfflinePrefetchPolicy(policy: OfflinePrefetchPolicy) {
        viewModelScope.launch { preferencesRepository.setOfflinePrefetchPolicy(policy) }
    }

    fun setOfflineMaxCacheMb(maxMb: Int) {
        viewModelScope.launch { preferencesRepository.setOfflineMaxCacheMb(maxMb) }
    }

    fun setOfflineCacheFullRssArticles(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOfflineCacheFullRssArticles(enabled) }
    }

    fun prepareOfflineNow() {
        if (offlinePrepProgressFlow.value != null) return
        viewModelScope.launch {
            offlinePrepProgressFlow.value = OfflinePrepProgress(0, 1)
            runCatching {
                repository.prepareOfflineSnapshotNow { completed, total ->
                    if (total > 0) {
                        offlinePrepProgressFlow.value = OfflinePrepProgress(completed, total)
                    }
                }
            }.onSuccess { (photosCached, articlesCached) ->
                offlinePrepProgressFlow.value = null
                _events.tryEmit("Offline ready: $photosCached photos & $articlesCached articles cached (no videos)")
            }.onFailure { err ->
                offlinePrepProgressFlow.value = null
                _events.tryEmit("Offline sync error: ${err.message}")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearNonBookmarkedCache()
            _events.tryEmit("Cleared non-bookmarked posts, articles & photos")
        }
    }

    fun runDiagnostics() {
        if (diagnosticsFlow.value.isRunning) return
        viewModelScope.launch {
            diagnosticsFlow.value = ConnectionDiagnostics(isRunning = true)
            val result = repository.runDiagnostics()
            diagnosticsFlow.value = result
        }
    }

    fun handleIncomingIntent(intent: Intent?) {
        when (val result = DeepLinkParser.parseIntent(intent)) {
            is DeepLinkResult.OpenChannel -> {
                _pendingDeepLink.value = result.target
            }
            DeepLinkResult.PrivateInviteUnsupported -> {
                _events.tryEmit("Private invite links cannot be previewed; public channels only.")
            }
            DeepLinkResult.Ignored -> Unit
        }
    }

    fun consumeDeepLink() {
        _pendingDeepLink.value = null
    }

    fun exportBackupToUri(context: Context, uri: Uri, asOpml: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val content = if (asOpml) {
                    repository.exportBackupOpml()
                } else {
                    repository.exportBackupJson()
                }
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open destination file")
            }.onSuccess {
                _events.tryEmit(if (asOpml) "OPML exported successfully" else "Backup exported successfully")
            }.onFailure { err ->
                _events.tryEmit("Export failed: ${err.message}")
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("Unable to read selected file")
                repository.importBackup(raw)
            }.onSuccess { (channelsAdded, bookmarksAdded) ->
                if (channelsAdded == 0 && bookmarksAdded == 0) {
                    _events.tryEmit("No new channels or bookmarks found in file")
                } else {
                    _events.tryEmit("Imported $channelsAdded channel(s), $bookmarksAdded bookmark(s). Syncing…")
                    refreshAllChannels()
                }
            }.onFailure { err ->
                _events.tryEmit("Import failed: ${err.message}")
            }
        }
    }

    fun setBackgroundSyncEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundSyncEnabled(enabled)
            val interval = preferencesRepository.getCurrentSettings().syncIntervalMinutes
            BackgroundSyncScheduler.applySchedule(context, enabled, interval)
            _events.tryEmit(if (enabled) "Background sync scheduled every ${interval}m" else "Background sync paused")
        }
    }

    fun setSyncIntervalMinutes(context: Context, minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setSyncIntervalMinutes(minutes)
            if (preferencesRepository.getCurrentSettings().backgroundSyncEnabled) {
                BackgroundSyncScheduler.applySchedule(context, true, minutes)
            }
        }
    }

    fun setNotifyFavoritesOnly(favoritesOnly: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotifyFavoritesOnly(favoritesOnly)
        }
    }

    fun triggerBackgroundSyncNow(context: Context) {
        BackgroundSyncScheduler.triggerImmediateSync(context)
        _events.tryEmit("Background sync queued via WorkManager")
    }

    private data class BaseDataBundle(
        val channels: List<ChannelInfo>,
        val unified: List<FeedPost>,
        val favorite: List<FeedPost>,
        val bookmarked: List<FeedPost>,
        val settings: UserSettings
    )

    private data class UiControlBundle(
        val filterMode: FeedFilterMode,
        val selectedChip: String?,
        val searchQuery: String,
        val refreshing: Boolean,
        val adding: Boolean
    )

    private data class ExtraControlBundle(
        val addError: String?,
        val cachedCount: Int,
        val diagnostics: ConnectionDiagnostics,
        val viewer: MediaViewerState?,
        val dlKey: String?
    )

    private data class FeedMetaBundle(
        val audioUrl: String?,
        val unreadCounts: Map<String, Int>,
        val offlineVaultStats: OfflineVaultStats,
        val offlinePrepProgress: OfflinePrepProgress?
    )

    class Factory(
        private val repository: OpenFeedRepository,
        private val preferencesRepository: UserPreferencesRepository,
        private val networkClient: OpenFeedNetworkClient
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OpenFeedViewModel(repository, preferencesRepository, networkClient) as T
        }
    }
}
