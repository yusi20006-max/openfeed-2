package com.example.data.repository

import android.content.Context
import com.example.data.local.ChannelEntity
import com.example.data.local.OpenFeedDao
import com.example.data.local.PostEntity
import com.example.data.model.ChannelInfo
import com.example.data.model.ConnectionDiagnostics
import com.example.data.model.FeedPost
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.network.DownloadResult
import com.example.data.network.OpenFeedNetworkClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OpenFeedRepository(
    private val dao: OpenFeedDao,
    private val networkClient: OpenFeedNetworkClient
) {
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

    val cachedPostCountFlow: Flow<Int> = dao.observePostCount()

    fun observeChannel(username: String): Flow<ChannelInfo?> =
        dao.observeChannel(username).map { it?.toDomain() }

    fun observeChannelPosts(username: String): Flow<List<FeedPost>> =
        dao.observeChannelPosts(username).map { list -> list.map { it.toDomain() } }

    suspend fun addAndFetchChannel(username: String, favorite: Boolean = false): ChannelInfo {
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
            lastFetchedAt = parsed.channel.lastFetchedAt,
            lastTransportUsed = parsed.channel.lastTransportUsed,
            lastLatencyMs = parsed.channel.lastLatencyMs
        )
        val postEntities = parsed.posts.map { PostEntity.fromDomain(it) }
        dao.upsertChannelAndPosts(channelEntity, postEntities)
        return parsed.channel
    }

    suspend fun fetchOlderPosts(username: String, beforeId: Long): Long? {
        val existing = dao.getChannel(username)
        val parsed = networkClient.fetchChannelPage(
            username = username,
            beforeId = beforeId,
            existingIsFavorite = existing?.isFavorite ?: false
        )
        val bookmarked = dao.getBookmarkedKeysForChannel(username).toSet()
        val entities = parsed.posts.map { post ->
            val entity = PostEntity.fromDomain(post)
            if (entity.uniqueKey in bookmarked) entity.copy(isBookmarked = true) else entity
        }
        if (entities.isNotEmpty()) {
            dao.upsertPosts(entities)
        }
        return parsed.olderBeforeId
    }

    suspend fun refreshAllChannels(): Pair<Int, Int> = coroutineScope {
        val channels = dao.getAllChannelsSync()
        if (channels.isEmpty()) return@coroutineScope 0 to 0

        val results = channels.map { channel ->
            async {
                runCatching {
                    addAndFetchChannel(channel.username, channel.isFavorite)
                }.isSuccess
            }
        }.awaitAll()

        val succeeded = results.count { it }
        val failed = results.size - succeeded
        succeeded to failed
    }

    suspend fun toggleFavorite(username: String, currentFavorite: Boolean) {
        dao.setFavorite(username, !currentFavorite)
    }

    suspend fun removeChannel(username: String) {
        dao.deleteUnbookmarkedPostsForChannel(username)
        dao.deleteChannel(username)
    }

    suspend fun toggleBookmark(post: FeedPost) {
        dao.setBookmarked(post.uniqueKey, !post.isBookmarked)
    }

    suspend fun clearNonBookmarkedCache() {
        dao.clearNonBookmarkedPosts()
    }

    suspend fun runDiagnostics(): ConnectionDiagnostics {
        return networkClient.runDiagnostics()
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
