package com.example.data.repository

import android.content.Context
import com.example.data.local.CachedArticleEntity
import com.example.data.local.ChannelEntity
import com.example.data.local.OfflineMediaVault
import com.example.data.local.OpenFeedDao
import com.example.data.local.PostEntity
import com.example.data.model.ChannelInfo
import com.example.data.model.ConnectionDiagnostics
import com.example.data.model.FeedPost
import com.example.data.model.FeedSourceType
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.model.OfflineVaultStats
import com.example.data.model.StarterChannels
import com.example.data.model.UserSettings
import com.example.data.network.DownloadResult
import com.example.data.network.ExtractedArticle
import com.example.data.network.OpenFeedNetworkClient
import com.example.data.network.UrlRewriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class OpenFeedRepository(
    private val dao: OpenFeedDao,
    private val networkClient: OpenFeedNetworkClient,
    private val offlineMediaVault: OfflineMediaVault? = null,
    private val settingsProvider: () -> UserSettings = { UserSettings() }
) {
    private val backgroundOfflineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val channelsFlow: Flow<List<ChannelInfo>> = dao.observeChannels().map { list ->
        list.map { it.toDomain() }
    }

    val unifiedFeedFlow: Flow<List<FeedPost>> = dao.observeUnifiedFeed().map { list ->
        list.map { it.toDomain() }
    }

    val favoriteFeedFlow: Flow<List<FeedPost>> = dao.observeFavoriteFeed().map { list ->
        list.map { it.toDomain() }
    }

    val bookmarkedPostsFlow: Flow<List<FeedPost>> = dao.observeBookmarkedPosts().map { list ->
        list.map { it.toDomain() }
    }

    val unreadCountsFlow: Flow<Map<String, Int>> = dao.observeUnreadCountsByChannel().map { list ->
        list.associate { it.channelUsername to it.unreadCount }
    }

    val cachedPostCountFlow: Flow<Int> = dao.observePostCount()

    val offlineStatsFlow: Flow<OfflineVaultStats> = combine(
        offlineMediaVault?.diskStatsFlow ?: flowOf(com.example.data.local.VaultDiskStats()),
        dao.observeCachedArticleCount()
    ) { diskStats, articleCount ->
        OfflineVaultStats(
            photoCount = diskStats.photoCount,
            totalBytes = diskStats.totalBytes,
            cachedArticleCount = articleCount
        )
    }

    fun observeChannel(username: String): Flow<ChannelInfo?> =
        dao.observeChannel(username).map { it?.toDomain() }

    fun observeChannelPosts(username: String): Flow<List<FeedPost>> =
        dao.observeChannelPosts(username).map { list -> list.map { it.toDomain() } }

    suspend fun addFeedFromInput(rawInput: String, favorite: Boolean = false): ChannelInfo {
        val trimmed = rawInput.trim()
        if (UrlRewriter.isRssFeedUrl(trimmed)) {
            return addAndFetchRss(feedUrl = trimmed, favorite = favorite)
        }
        val normalized = UrlRewriter.normalizeUsername(trimmed)
            ?: throw IllegalArgumentException("Enter a valid @username, t.me/channel, or RSS feed URL")
        return addAndFetchChannel(normalized, favorite = favorite)
    }

    suspend fun addAndFetchChannel(
        username: String,
        favorite: Boolean = false,
        autoPrefetch: Boolean = true
    ): ChannelInfo {
        val existing = dao.getChannel(username)
        val parsed = networkClient.fetchChannelPage(
            username = username,
            beforeId = null,
            existingIsFavorite = existing?.isFavorite ?: favorite
        )
        val channelEntity = ChannelEntity(
            username = parsed.channel.username,
            title = parsed.channel.title,
            description = parsed.channel.description,
            avatarUrl = parsed.channel.avatarUrl,
            subscribersText = parsed.channel.subscribersText,
            isVerified = parsed.channel.isVerified,
            isFavorite = parsed.channel.isFavorite,
            sourceType = FeedSourceType.TELEGRAM.name,
            feedUrl = "https://t.me/s/${parsed.channel.username}",
            lastFetchedAt = parsed.channel.lastFetchedAt,
            lastTransportUsed = parsed.channel.lastTransportUsed,
            lastLatencyMs = parsed.channel.lastLatencyMs
        )
        val postEntities = parsed.posts.map { PostEntity.fromDomain(it) }
        dao.upsertChannelAndPosts(channelEntity, postEntities)
        if (autoPrefetch) {
            triggerAutoPrefetchAsync(parsed.posts.take(25))
        }
        return parsed.channel.copy(
            sourceType = FeedSourceType.TELEGRAM,
            feedUrl = channelEntity.feedUrl
        )
    }

    suspend fun addAndFetchRss(
        feedUrl: String,
        fallbackTitle: String? = null,
        fallbackLogoUrl: String? = null,
        favorite: Boolean = false,
        autoPrefetch: Boolean = true
    ): ChannelInfo {
        val cleanUrl = feedUrl.trim()
        val sourceKey = StarterChannels.buildRssSourceKey(cleanUrl)
        val existing = dao.getChannel(sourceKey)
        val parsed = networkClient.fetchRssFeed(
            sourceKey = sourceKey,
            feedUrl = cleanUrl,
            fallbackTitle = existing?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            fallbackLogoUrl = existing?.avatarUrl?.takeIf { !it.isNullOrBlank() } ?: fallbackLogoUrl,
            existingIsFavorite = existing?.isFavorite ?: favorite
        )
        val resolvedKey = parsed.channel.username
        val resolvedExisting = if (resolvedKey != sourceKey) dao.getChannel(resolvedKey) else existing
        val resolvedFavorite = resolvedExisting?.isFavorite ?: existing?.isFavorite ?: parsed.channel.isFavorite
        val resolvedFeedUrl = parsed.channel.feedUrl ?: cleanUrl
        val channelEntity = ChannelEntity(
            username = resolvedKey,
            title = parsed.channel.title,
            description = parsed.channel.description,
            avatarUrl = parsed.channel.avatarUrl,
            subscribersText = parsed.channel.subscribersText,
            isVerified = false,
            isFavorite = resolvedFavorite,
            sourceType = FeedSourceType.RSS.name,
            feedUrl = resolvedFeedUrl,
            lastFetchedAt = parsed.channel.lastFetchedAt,
            lastTransportUsed = parsed.channel.lastTransportUsed,
            lastLatencyMs = parsed.channel.lastLatencyMs
        )
        val postEntities = parsed.posts.map { PostEntity.fromDomain(it) }
        dao.upsertChannelAndPosts(channelEntity, postEntities)
        if (autoPrefetch) {
            triggerAutoPrefetchAsync(parsed.posts.take(25))
        }
        return parsed.channel.copy(
            isFavorite = resolvedFavorite,
            feedUrl = resolvedFeedUrl
        )
    }

    suspend fun refreshChannelByKey(username: String, autoPrefetch: Boolean = true): ChannelInfo {
        val existing = dao.getChannel(username)
        return if (existing?.sourceType == FeedSourceType.RSS.name && !existing.feedUrl.isNullOrBlank()) {
            addAndFetchRss(
                feedUrl = existing.feedUrl,
                fallbackTitle = existing.title,
                fallbackLogoUrl = existing.avatarUrl,
                favorite = existing.isFavorite,
                autoPrefetch = autoPrefetch
            )
        } else {
            addAndFetchChannel(username, existing?.isFavorite ?: false, autoPrefetch = autoPrefetch)
        }
    }

    suspend fun fetchOlderPosts(username: String, beforeId: Long): Long? {
        val existing = dao.getChannel(username)
        if (existing?.sourceType == FeedSourceType.RSS.name) return null
        val parsed = networkClient.fetchChannelPage(
            username = username,
            beforeId = beforeId,
            existingIsFavorite = existing?.isFavorite ?: false
        )
        val existingFlags = dao.getPostFlagsForChannel(username).associateBy { it.uniqueKey }
        val entities = parsed.posts.map { post ->
            val entity = PostEntity.fromDomain(post)
            val flags = existingFlags[entity.uniqueKey]
            if (flags != null) {
                entity.copy(
                    isBookmarked = entity.isBookmarked || flags.isBookmarked,
                    isRead = entity.isRead || flags.isRead
                )
            } else {
                entity
            }
        }
        if (entities.isNotEmpty()) {
            dao.upsertPosts(entities)
            triggerAutoPrefetchAsync(parsed.posts.take(20))
        }
        return parsed.olderBeforeId
    }

    suspend fun refreshAllChannels(): Pair<Int, Int> = coroutineScope {
        val channels = dao.getAllChannelsSync()
        if (channels.isEmpty()) return@coroutineScope 0 to 0

        val results = channels.map { channel ->
            async {
                runCatching {
                    if (channel.sourceType == FeedSourceType.RSS.name && !channel.feedUrl.isNullOrBlank()) {
                        addAndFetchRss(
                            feedUrl = channel.feedUrl,
                            fallbackTitle = channel.title,
                            fallbackLogoUrl = channel.avatarUrl,
                            favorite = channel.isFavorite,
                            autoPrefetch = false
                        )
                    } else {
                        addAndFetchChannel(channel.username, channel.isFavorite, autoPrefetch = false)
                    }
                }.isSuccess
            }
        }.awaitAll()

        val succeeded = results.count { it }
        val failed = results.size - succeeded

        if (succeeded > 0) {
            triggerAutoPrefetchLatestAsync()
        }

        succeeded to failed
    }

    suspend fun autoPrefetchLatestIfAllowed() {
        val settings = settingsProvider()
        val vault = offlineMediaVault ?: return
        if (!vault.shouldAutoPrefetch(settings.offlinePrefetchPolicy)) return
        val recentPosts = dao.getRecentPostsSync(60).map { it.toDomain() }
        if (recentPosts.isEmpty()) return
        prefetchOfflineForPosts(
            posts = recentPosts,
            includeArticles = settings.offlineCacheFullRssArticles,
            maxCacheMb = settings.offlineMaxCacheMb,
            settings = settings,
            maxArticlesPerBatch = 20
        )
    }

    suspend fun prepareOfflineSnapshotNow(
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Pair<Int, Int> {
        val settings = settingsProvider()
        val bookmarked = dao.getAllBookmarkedPostsSync().map { it.toDomain() }
        val recent = dao.getRecentPostsSync(80).map { it.toDomain() }
        val combined = (bookmarked + recent).distinctBy { it.uniqueKey }
        if (combined.isEmpty()) return 0 to 0

        return prefetchOfflineForPosts(
            posts = combined,
            includeArticles = settings.offlineCacheFullRssArticles,
            maxCacheMb = settings.offlineMaxCacheMb,
            settings = settings,
            maxArticlesPerBatch = 40,
            onProgress = onProgress
        )
    }

    private fun triggerAutoPrefetchAsync(posts: List<FeedPost>) {
        val vault = offlineMediaVault ?: return
        val settings = settingsProvider()
        if (!vault.shouldAutoPrefetch(settings.offlinePrefetchPolicy) || posts.isEmpty()) return
        backgroundOfflineScope.launch {
            runCatching {
                prefetchOfflineForPosts(
                    posts = posts,
                    includeArticles = settings.offlineCacheFullRssArticles,
                    maxCacheMb = settings.offlineMaxCacheMb,
                    settings = settings,
                    maxArticlesPerBatch = 15
                )
            }
        }
    }

    private fun triggerAutoPrefetchLatestAsync() {
        val vault = offlineMediaVault ?: return
        val settings = settingsProvider()
        if (!vault.shouldAutoPrefetch(settings.offlinePrefetchPolicy)) return
        backgroundOfflineScope.launch {
            runCatching {
                autoPrefetchLatestIfAllowed()
            }
        }
    }

    private suspend fun prefetchOfflineForPosts(
        posts: List<FeedPost>,
        includeArticles: Boolean,
        maxCacheMb: Int,
        settings: UserSettings,
        maxArticlesPerBatch: Int = 30,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Pair<Int, Int> = coroutineScope {
        val vault = offlineMediaVault
        val imageCandidates = if (vault != null) {
            OfflineMediaVault.extractPhotoCandidates(posts)
        } else {
            emptyList()
        }

        val articleUrls = if (includeArticles) {
            val rawUrls = posts.mapNotNull { post ->
                val raw = when {
                    post.sourceType == FeedSourceType.RSS && post.postUrl.startsWith("http") -> post.postUrl
                    post.linkPreview?.url?.startsWith("http") == true -> post.linkPreview.url
                    else -> null
                }
                raw?.let { OfflineMediaVault.canonicalUrl(it) }
            }.distinct()
            val alreadyCached = dao.getAllCachedArticleUrlsSync().toSet()
            rawUrls.filter { it !in alreadyCached }.take(maxArticlesPerBatch)
        } else {
            emptyList()
        }

        val totalWork = imageCandidates.size + articleUrls.size
        if (totalWork == 0) {
            onProgress?.invoke(0, 0)
            return@coroutineScope 0 to 0
        }

        val completedCounter = AtomicInteger(0)
        val cachedPhotosCount = AtomicInteger(0)
        val cachedArticlesCount = AtomicInteger(0)
        onProgress?.invoke(0, totalWork)

        val semaphore = Semaphore(4)

        val imageJobs = if (vault != null) {
            imageCandidates.map { candidate ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val ok = vault.cacheImage(
                            httpClient = networkClient.httpClient,
                            primaryUrl = candidate.primaryUrl,
                            fallbackUrl = candidate.fallbackUrl,
                            settings = settings
                        )
                        if (ok) cachedPhotosCount.incrementAndGet()
                        val done = completedCounter.incrementAndGet()
                        onProgress?.invoke(done, totalWork)
                    }
                }
            }
        } else {
            emptyList()
        }

        val articleJobs = articleUrls.map { cleanUrl ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        val article = networkClient.fetchArticleContent(cleanUrl)
                        dao.upsertCachedArticle(CachedArticleEntity.fromDomain(article, canonicalUrl = cleanUrl))
                        if (!article.leadImageUrl.isNullOrBlank() && vault != null) {
                            vault.cacheImage(
                                httpClient = networkClient.httpClient,
                                primaryUrl = article.leadImageUrl,
                                settings = settings
                            )
                        }
                        cachedArticlesCount.incrementAndGet()
                    }
                    val done = completedCounter.incrementAndGet()
                    onProgress?.invoke(done, totalWork)
                }
            }
        }

        (imageJobs + articleJobs).awaitAll()

        if (vault != null) {
            val pinnedUrls = collectBookmarkedPinnedImageUrls()
            vault.trimToMaxBytes(maxCacheMb.toLong() * 1024L * 1024L, pinnedUrls)
        }

        cachedPhotosCount.get() to cachedArticlesCount.get()
    }

    private suspend fun collectBookmarkedPinnedImageUrls(): Set<String> {
        val bookmarked = dao.getAllBookmarkedPostsSync().map { it.toDomain() }
        val pinned = HashSet<String>()
        for (candidate in OfflineMediaVault.extractPhotoCandidates(bookmarked)) {
            pinned.add(OfflineMediaVault.canonicalUrl(candidate.primaryUrl))
        }
        val articleUrls = bookmarked.mapNotNull { post ->
            val raw = when {
                post.sourceType == FeedSourceType.RSS && post.postUrl.startsWith("http") -> post.postUrl
                post.linkPreview?.url?.startsWith("http") == true -> post.linkPreview.url
                else -> null
            }
            raw?.let { OfflineMediaVault.canonicalUrl(it) }
        }.distinct()
        if (articleUrls.isNotEmpty()) {
            articleUrls.chunked(500).forEach { chunk ->
                dao.getCachedArticlesByUrls(chunk).forEach { entity ->
                    entity.leadImageUrl?.takeIf { it.isNotBlank() }?.let { img ->
                        pinned.add(OfflineMediaVault.canonicalUrl(img))
                    }
                }
            }
        }
        return pinned
    }

    suspend fun toggleFavorite(username: String, currentFavorite: Boolean) {
        dao.setFavorite(username, !currentFavorite)
    }

    suspend fun removeChannel(username: String) {
        dao.deleteUnbookmarkedPostsForChannel(username)
        dao.deleteChannel(username)
    }

    suspend fun toggleBookmark(post: FeedPost) {
        val willBookmark = !post.isBookmarked
        dao.setBookmarked(post.uniqueKey, willBookmark)
        if (willBookmark) {
            pinPostForOfflineAsync(post)
        }
    }

    private fun pinPostForOfflineAsync(post: FeedPost) {
        val settings = settingsProvider()
        backgroundOfflineScope.launch {
            runCatching {
                prefetchOfflineForPosts(
                    posts = listOf(post),
                    includeArticles = settings.offlineCacheFullRssArticles,
                    maxCacheMb = settings.offlineMaxCacheMb,
                    settings = settings,
                    maxArticlesPerBatch = 1
                )
            }
        }
    }

    suspend fun markPostAsRead(uniqueKey: String) {
        dao.markPostAsRead(uniqueKey)
    }

    suspend fun markChannelAsRead(username: String) {
        dao.markChannelAsRead(username)
    }

    suspend fun markAllAsRead() {
        dao.markAllAsRead()
    }

    suspend fun clearNonBookmarkedCache() {
        dao.clearNonBookmarkedPosts()
        val bookmarked = dao.getAllBookmarkedPostsSync().map { it.toDomain() }
        val keepArticleUrls = bookmarked.mapNotNull { post ->
            val raw = when {
                post.sourceType == FeedSourceType.RSS && post.postUrl.startsWith("http") -> post.postUrl
                post.linkPreview?.url?.startsWith("http") == true -> post.linkPreview.url
                else -> null
            }
            raw?.let { OfflineMediaVault.canonicalUrl(it) }
        }.distinct()

        if (keepArticleUrls.isEmpty()) {
            dao.deleteAllCachedArticles()
        } else if (keepArticleUrls.size <= 900) {
            dao.deleteCachedArticlesExcept(keepArticleUrls)
        } else {
            val allCached = dao.getAllCachedArticleUrlsSync()
            val keepSet = keepArticleUrls.toHashSet()
            val toDelete = allCached.filter { it !in keepSet }
            if (toDelete.size == allCached.size) {
                dao.deleteAllCachedArticles()
            } else {
                dao.deleteCachedArticlesExcept(keepArticleUrls.take(900))
            }
        }

        val pinnedImageUrls = collectBookmarkedPinnedImageUrls()
        offlineMediaVault?.clearNonPinned(pinnedImageUrls)
    }

    suspend fun getChannelsSync(): List<ChannelInfo> =
        dao.getAllChannelsSync().map { it.toDomain() }

    suspend fun getMaxPostIdForChannel(username: String): Long? =
        dao.getMaxPostIdForChannel(username)

    suspend fun exportBackupJson(): String {
        val channels = dao.getAllChannelsSync()
        val bookmarks = dao.getAllBookmarkedPostsSync()
        return com.example.data.backup.BackupManager.exportToJson(channels, bookmarks)
    }

    suspend fun exportBackupOpml(): String {
        val channels = dao.getAllChannelsSync()
        return com.example.data.backup.BackupManager.exportToOpml(channels)
    }

    suspend fun importBackup(rawContent: String): Pair<Int, Int> {
        val payload = com.example.data.backup.BackupManager.importFromContent(rawContent)
        var addedChannels = 0
        for (item in payload.channels) {
            val existing = dao.getChannel(item.username)
            if (existing == null) {
                val logoFallback = if (item.sourceType == FeedSourceType.RSS && !item.feedUrl.isNullOrBlank()) {
                    StarterChannels.rssLogoFallback(item.feedUrl)
                } else {
                    StarterChannels.telegramAvatarFallback(item.username)
                }
                dao.upsertChannel(
                    ChannelEntity(
                        username = item.username,
                        title = item.title,
                        description = "",
                        avatarUrl = logoFallback.takeIf { it.isNotBlank() },
                        subscribersText = null,
                        isVerified = false,
                        isFavorite = item.isFavorite,
                        sourceType = item.sourceType.name,
                        feedUrl = item.feedUrl,
                        lastFetchedAt = 0L,
                        lastTransportUsed = "Imported",
                        lastLatencyMs = null
                    )
                )
                addedChannels++
            } else if (item.isFavorite && !existing.isFavorite) {
                dao.setFavorite(item.username, true)
            }
        }
        if (payload.bookmarkedPosts.isNotEmpty()) {
            dao.upsertPosts(payload.bookmarkedPosts)
        }
        return addedChannels to payload.bookmarkedPosts.size
    }

    suspend fun runDiagnostics(): ConnectionDiagnostics {
        return networkClient.runDiagnostics()
    }

    suspend fun fetchArticle(url: String, forceRefresh: Boolean = false): ExtractedArticle {
        val cleanUrl = OfflineMediaVault.canonicalUrl(url)
        val cached = dao.getCachedArticle(cleanUrl)
        if (!forceRefresh && cached != null) {
            val domain = cached.toDomain()
            if (domain.blocks.isNotEmpty()) {
                val lead = domain.leadImageUrl
                if (!lead.isNullOrBlank() && offlineMediaVault != null && !offlineMediaVault.isCached(lead)) {
                    backgroundOfflineScope.launch {
                        runCatching {
                            offlineMediaVault.cacheImage(
                                httpClient = networkClient.httpClient,
                                primaryUrl = lead,
                                settings = settingsProvider()
                            )
                        }
                    }
                }
                return domain
            }
        }

        val networkAttempt = runCatching {
            networkClient.fetchArticleContent(cleanUrl)
        }
        return networkAttempt.fold(
            onSuccess = { article ->
                runCatching {
                    dao.upsertCachedArticle(CachedArticleEntity.fromDomain(article, canonicalUrl = cleanUrl))
                    if (!article.leadImageUrl.isNullOrBlank() && offlineMediaVault != null) {
                        offlineMediaVault.cacheImage(
                            httpClient = networkClient.httpClient,
                            primaryUrl = article.leadImageUrl,
                            settings = settingsProvider()
                        )
                    }
                }
                article
            },
            onFailure = { err ->
                if (cached != null) {
                    cached.toDomain()
                } else {
                    throw err
                }
            }
        )
    }

    suspend fun downloadAttachment(
        context: Context,
        post: FeedPost,
        attachment: MediaAttachment
    ): DownloadResult {
        val (ext, mime) = when (attachment.type) {
            MediaType.PHOTO -> "jpg" to "image/jpeg"
            MediaType.VIDEO, MediaType.GIF -> "mp4" to "video/mp4"
            MediaType.VOICE -> "ogg" to "audio/ogg"
            MediaType.AUDIO -> "mp3" to "audio/mpeg"
            MediaType.DOCUMENT -> {
                val docExt = attachment.title?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotBlank() && it.length <= 6 } ?: "bin"
                docExt to "application/octet-stream"
            }
        }
        val baseTitle = attachment.title
            ?.takeIf { it.isNotBlank() && attachment.type == MediaType.DOCUMENT }
            ?: "${post.channelUsername}_${post.postId}_${System.currentTimeMillis() % 10000}.$ext"

        return networkClient.downloadMedia(
            context = context,
            directUrl = attachment.url,
            bridgeUrl = attachment.bridgeUrl,
            fileName = baseTitle,
            mimeType = mime
        )
    }
}
