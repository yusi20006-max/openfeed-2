package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.BridgeLangProfile
import com.example.data.model.ChannelInfo
import com.example.data.model.ConnectionDiagnostics
import com.example.data.model.DohProvider
import com.example.data.model.FeedPost
import com.example.data.model.MediaAttachment
import com.example.data.model.ThemeMode
import com.example.data.model.TransportMode
import com.example.data.model.UserSettings
import com.example.data.network.OpenFeedNetworkClient
import com.example.data.network.UrlRewriter
import com.example.data.repository.OpenFeedRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedFilterMode {
    ALL,
    FAVORITES
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
    val activeAudioUrl: String? = null
)

class OpenFeedViewModel(
    private val repository: OpenFeedRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val networkClient: OpenFeedNetworkClient
) : ViewModel() {

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

    private val loadingOlderChannels = MutableStateFlow<Set<String>>(emptySet())
    private val channelOlderCursorMap = MutableStateFlow<Map<String, Long?>>(emptyMap())

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

    private val extraControlFlow = combine(
        addChannelErrorFlow,
        repository.cachedPostCountFlow,
        diagnosticsFlow,
        activeMediaViewerFlow,
        downloadingKeyFlow
    ) { addError, count, diag, viewer, dlKey ->
        ExtraControlBundle(addError, count, diag, viewer, dlKey)
    }

    val uiState: StateFlow<FeedUiState> = combine(
        baseDataFlow,
        uiControlFlow,
        extraControlFlow,
        activeAudioUrlFlow
    ) { base, ctrl, extra, audioUrl ->
        val rawFeed = when (ctrl.filterMode) {
            FeedFilterMode.ALL -> base.unified
            FeedFilterMode.FAVORITES -> base.favorite
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
            activeAudioUrl = audioUrl
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
        val normalized = UrlRewriter.normalizeUsername(rawInput)
        if (normalized == null) {
            addChannelErrorFlow.value = "Enter a valid @username or t.me/channel link"
            return
        }

        viewModelScope.launch {
            isAddingChannelFlow.value = true
            addChannelErrorFlow.value = null
            runCatching {
                repository.addAndFetchChannel(normalized, favorite = markFavorite)
            }.onSuccess { info ->
                isAddingChannelFlow.value = false
                _events.tryEmit("Synced @${info.username} (${info.lastTransportUsed ?: "OK"})")
                onAdded?.invoke(info.username)
            }.onFailure { err ->
                isAddingChannelFlow.value = false
                addChannelErrorFlow.value = err.message ?: "Failed to fetch @$normalized"
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
                    ok == 0 && fail == 0 -> _events.tryEmit("Add a channel to start reading")
                    fail == 0 -> _events.tryEmit("Updated $ok channel${if (ok == 1) "" else "s"}")
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
                repository.addAndFetchChannel(username)
            }.onSuccess { info ->
                isRefreshingFlow.value = false
                _events.tryEmit("@${info.username} refreshed • ${info.lastLatencyMs ?: 0}ms")
            }.onFailure { err ->
                isRefreshingFlow.value = false
                _events.tryEmit("Could not refresh @$username: ${err.message}")
            }
        }
    }

    fun loadOlderPosts(username: String, currentPosts: List<FeedPost>) {
        if (username in loadingOlderChannels.value) return
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
        viewModelScope.launch {
            repository.removeChannel(username)
            if (selectedChipFlow.value == username) {
                selectedChipFlow.value = null
            }
            _events.tryEmit("Removed @$username")
        }
    }

    fun toggleBookmark(post: FeedPost) {
        viewModelScope.launch {
            repository.toggleBookmark(post)
            val msg = if (!post.isBookmarked) "Saved for offline reading" else "Removed from Saved"
            _events.tryEmit(msg)
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

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setAutoLoadMedia(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoLoadMedia(enabled) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setFontScale(scale) }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearNonBookmarkedCache()
            _events.tryEmit("Cleared non-bookmarked cached posts")
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
