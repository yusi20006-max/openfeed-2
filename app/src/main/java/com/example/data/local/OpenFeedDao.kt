package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
        ORDER BY postId DESC
        """
    )
    fun observeChannelPosts(username: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE isBookmarked = 1 ORDER BY timestampEpoch DESC, postId DESC")
    fun observeBookmarkedPosts(): Flow<List<PostEntity>>

    @Query("SELECT uniqueKey FROM posts WHERE channelUsername = :username AND isBookmarked = 1")
    suspend fun getBookmarkedKeysForChannel(username: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(posts: List<PostEntity>)

    @Transaction
    suspend fun upsertChannelAndPosts(channel: ChannelEntity, posts: List<PostEntity>) {
        upsertChannel(channel)
        if (posts.isNotEmpty()) {
            val bookmarked = getBookmarkedKeysForChannel(channel.username).toSet()
            val merged = posts.map { post ->
                if (post.uniqueKey in bookmarked) post.copy(isBookmarked = true) else post
            }
            upsertPosts(merged)
        }
    }

    @Query("UPDATE posts SET isBookmarked = :isBookmarked WHERE uniqueKey = :uniqueKey")
    suspend fun setBookmarked(uniqueKey: String, isBookmarked: Boolean)

    @Query("DELETE FROM posts WHERE isBookmarked = 0")
    suspend fun clearNonBookmarkedPosts()

    @Query("SELECT COUNT(*) FROM posts")
    fun observePostCount(): Flow<Int>
}
