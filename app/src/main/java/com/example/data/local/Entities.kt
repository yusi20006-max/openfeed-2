package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.ChannelInfo
import com.example.data.model.FeedPost

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val username: String,
    val title: String,
    val description: String,
    val avatarUrl: String?,
    val subscribersText: String?,
    val isVerified: Boolean,
    val isFavorite: Boolean,
    val lastFetchedAt: Long,
    val lastTransportUsed: String?,
    val lastLatencyMs: Long?
) {
    fun toDomain(): ChannelInfo = ChannelInfo(
        username = username,
        title = title,
        description = description,
        avatarUrl = avatarUrl,
        subscribersText = subscribersText,
        isVerified = isVerified,
        isFavorite = isFavorite,
        lastFetchedAt = lastFetchedAt,
        lastTransportUsed = lastTransportUsed,
        lastLatencyMs = lastLatencyMs
    )
}

@Entity(
    tableName = "posts",
    indices = [
        Index(value = ["channelUsername"]),
        Index(value = ["timestampEpoch"]),
        Index(value = ["isBookmarked"])
    ]
)
data class PostEntity(
    @PrimaryKey val uniqueKey: String,
    val channelUsername: String,
    val channelTitle: String,
    val channelAvatarUrl: String?,
    val channelVerified: Boolean,
    val postId: Long,
    val postUrl: String,
    val timestampIso: String,
    val timestampEpoch: Long,
    val displayTime: String,
    val viewsText: String?,
    val text: String,
    val linksJson: String,
    val mentionsJson: String,
    val hashtagsJson: String,
    val forwardedFrom: String?,
    val replyToAuthor: String?,
    val replyToSnippet: String?,
    val mediaJson: String,
    val linkPreviewJson: String?,
    val pollJson: String?,
    val isBookmarked: Boolean,
    val fetchedAt: Long
) {
    fun toDomain(): FeedPost = FeedPost(
        uniqueKey = uniqueKey,
        channelUsername = channelUsername,
        channelTitle = channelTitle,
        channelAvatarUrl = channelAvatarUrl,
        channelVerified = channelVerified,
        postId = postId,
        postUrl = postUrl,
        timestampIso = timestampIso,
        timestampEpoch = timestampEpoch,
        displayTime = displayTime,
        viewsText = viewsText,
        text = text,
        links = MediaSerializer.decodeStringList(linksJson),
        mentions = MediaSerializer.decodeStringList(mentionsJson),
        hashtags = MediaSerializer.decodeStringList(hashtagsJson),
        forwardedFrom = forwardedFrom,
        replyToAuthor = replyToAuthor,
        replyToSnippet = replyToSnippet,
        media = MediaSerializer.decodeMedia(mediaJson),
        linkPreview = MediaSerializer.decodeLinkPreview(linkPreviewJson),
        poll = MediaSerializer.decodePoll(pollJson),
        isBookmarked = isBookmarked,
        fetchedAt = fetchedAt
    )

    companion object {
        fun fromDomain(post: FeedPost): PostEntity = PostEntity(
            uniqueKey = post.uniqueKey,
            channelUsername = post.channelUsername,
            channelTitle = post.channelTitle,
            channelAvatarUrl = post.channelAvatarUrl,
            channelVerified = post.channelVerified,
            postId = post.postId,
            postUrl = post.postUrl,
            timestampIso = post.timestampIso,
            timestampEpoch = post.timestampEpoch,
            displayTime = post.displayTime,
            viewsText = post.viewsText,
            text = post.text,
            linksJson = MediaSerializer.encodeStringList(post.links),
            mentionsJson = MediaSerializer.encodeStringList(post.mentions),
            hashtagsJson = MediaSerializer.encodeStringList(post.hashtags),
            forwardedFrom = post.forwardedFrom,
            replyToAuthor = post.replyToAuthor,
            replyToSnippet = post.replyToSnippet,
            mediaJson = MediaSerializer.encodeMedia(post.media),
            linkPreviewJson = MediaSerializer.encodeLinkPreview(post.linkPreview),
            pollJson = MediaSerializer.encodePoll(post.poll),
            isBookmarked = post.isBookmarked,
            fetchedAt = post.fetchedAt
        )
    }
}
