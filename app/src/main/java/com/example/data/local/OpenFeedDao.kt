package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class PostFlagState(
    val uniqueKey: String,
    val isBookmarked: Boolean,
    val isRead: Boolean
)

data class ChannelUnreadCount(
    val channelUsername: String,
    val unreadCount: Int
)

@Dao
interface OpenFeedDao {

    @Query("SELECT * FROM channels ORDER BY isFavorite DESC, lastFetchedAt DESC")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE username = :username LIMIT 1")
    fun observeChannel(username: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE username = :username LIMIT 1")
    suspend fun getChannel(username: String): ChannelEntity?

    @Query("SELECT * FROM channels")
    suspend fun getAllChannelsSync(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: ChannelEntity)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE username = :username")
    suspend fun setFavorite(username: String, isFavorite: Boolean)

    @Query("DELETE FROM channels WHERE username = :username")
    suspend fun deleteChannel(username: String)

    @Query("DELETE FROM posts WHERE channelUsername = :username AND isBookmarked = 0")
    suspend fun deleteUnbookmarkedPostsForChannel(username: String)

    @Query(
        """
        SELECT p.* FROM posts p
        INNER JOIN channels c ON p.channelUsername = c.username
        ORDER BY p.timestampEpoch DESC, p.postId DESC
        LIMIT 300
        """
    )
    fun observeUnifiedFeed(): Flow<List<PostEntity>>

    @Query(
        """
        SELECT p.* FROM posts p
        INNER JOIN channels c ON p.channelUsername = c.username
        WHERE c.isFavorite = 1
        ORDER BY p.timestampEpoch DESC, p.postId DESC
        LIMIT 300
        """
    )
    fun observeFavoriteFeed(): Flow<List<PostEntity>>

    @Query(
        """
        SELECT * FROM posts
        WHERE channelUsername = :username
        ORDER BY timestampEpoch DESC, postId DESC
        """
    )
    fun observeChannelPosts(username: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE isBookmarked = 1 ORDER BY timestampEpoch DESC, postId DESC")
    fun observeBookmarkedPosts(): Flow<List<PostEntity>>

    @Query("SELECT uniqueKey FROM posts WHERE channelUsername = :username AND isBookmarked = 1")
    suspend fun getBookmarkedKeysForChannel(username: String): List<String>

    @Query("SELECT uniqueKey, isBookmarked, isRead FROM posts WHERE channelUsername = :username")
    suspend fun getPostFlagsForChannel(username: String): List<PostFlagState>

    @Query(
        """
        SELECT p.channelUsername, COUNT(*) AS unreadCount
        FROM posts p
        INNER JOIN channels c ON p.channelUsername = c.username
        WHERE p.isRead = 0
        GROUP BY p.channelUsername
        """
    )
    fun observeUnreadCountsByChannel(): Flow<List<ChannelUnreadCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(posts: List<PostEntity>)

    @Transaction
    suspend fun upsertChannelAndPosts(channel: ChannelEntity, posts: List<PostEntity>) {
        upsertChannel(channel)
        if (posts.isNotEmpty()) {
            val existingFlags = getPostFlagsForChannel(channel.username).associateBy { it.uniqueKey }
            val merged = posts.map { post ->
                val flags = existingFlags[post.uniqueKey]
                if (flags != null) {
                    post.copy(
                        isBookmarked = post.isBookmarked || flags.isBookmarked,
                        isRead = post.isRead || flags.isRead
                    )
                } else {
                    post
                }
            }
            upsertPosts(merged)
        }
    }

    @Query("UPDATE posts SET isBookmarked = :isBookmarked WHERE uniqueKey = :uniqueKey")
    suspend fun setBookmarked(uniqueKey: String, isBookmarked: Boolean)

    @Query("UPDATE posts SET isRead = 1 WHERE uniqueKey = :uniqueKey")
    suspend fun markPostAsRead(uniqueKey: String)

    @Query("UPDATE posts SET isRead = 1 WHERE channelUsername = :username")
    suspend fun markChannelAsRead(username: String)

    @Query("UPDATE posts SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM posts WHERE isBookmarked = 0")
    suspend fun clearNonBookmarkedPosts()

    @Query("SELECT * FROM posts WHERE isBookmarked = 1 ORDER BY timestampEpoch DESC, postId DESC")
    suspend fun getAllBookmarkedPostsSync(): List<PostEntity>

    @Query("SELECT MAX(postId) FROM posts WHERE channelUsername = :username")
    suspend fun getMaxPostIdForChannel(username: String): Long?

    @Query("SELECT COUNT(*) FROM posts")
    fun observePostCount(): Flow<Int>

    @Query("SELECT * FROM posts ORDER BY timestampEpoch DESC, postId DESC LIMIT :limit")
    suspend fun getRecentPostsSync(limit: Int): List<PostEntity>

    @Query("SELECT * FROM cached_articles WHERE url = :url LIMIT 1")
    suspend fun getCachedArticle(url: String): CachedArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedArticle(article: CachedArticleEntity)

    @Query("SELECT COUNT(*) FROM cached_articles")
    fun observeCachedArticleCount(): Flow<Int>

    @Query("SELECT url FROM cached_articles")
    suspend fun getAllCachedArticleUrlsSync(): List<String>

    @Query("SELECT * FROM cached_articles WHERE url IN (:urls)")
    suspend fun getCachedArticlesByUrls(urls: List<String>): List<CachedArticleEntity>

    @Query("DELETE FROM cached_articles WHERE url NOT IN (:keepUrls)")
    suspend fun deleteCachedArticlesExcept(keepUrls: List<String>)

    @Query("DELETE FROM cached_articles")
    suspend fun deleteAllCachedArticles()
}
