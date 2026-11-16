package com.example.data.backup

import com.example.data.local.ChannelEntity
import com.example.data.local.MediaSerializer
import com.example.data.local.PostEntity
import com.example.data.model.FeedSourceType
import com.example.data.model.StarterChannels
import com.example.data.network.UrlRewriter
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

data class ImportedChannelItem(
    val username: String,
    val title: String,
    val isFavorite: Boolean,
    val sourceType: FeedSourceType = FeedSourceType.TELEGRAM,
    val feedUrl: String? = null
)

data class ImportBackupPayload(
    val channels: List<ImportedChannelItem>,
    val bookmarkedPosts: List<PostEntity>
)

object BackupManager {

    private const val SCHEMA_VERSION = 2

    fun exportToJson(
        channels: List<ChannelEntity>,
        bookmarkedPosts: List<PostEntity>
    ): String {
        val root = JSONObject().apply {
            put("app", "OpenFeed")
            put("version", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())

            put("channels", JSONArray().apply {
                channels.forEach { ch ->
                    put(JSONObject().apply {
                        put("username", ch.username)
                        put("title", ch.title)
                        put("description", ch.description)
                        put("avatarUrl", ch.avatarUrl ?: JSONObject.NULL)
                        put("subscribersText", ch.subscribersText ?: JSONObject.NULL)
                        put("isVerified", ch.isVerified)
                        put("isFavorite", ch.isFavorite)
                        put("sourceType", ch.sourceType)
                        put("feedUrl", ch.feedUrl ?: JSONObject.NULL)
                    })
                }
            })

            put("bookmarks", JSONArray().apply {
                bookmarkedPosts.forEach { post ->
                    put(JSONObject().apply {
                        put("uniqueKey", post.uniqueKey)
                        put("channelUsername", post.channelUsername)
                        put("channelTitle", post.channelTitle)
                        put("channelAvatarUrl", post.channelAvatarUrl ?: JSONObject.NULL)
                        put("channelVerified", post.channelVerified)
                        put("sourceType", post.sourceType)
                        put("postId", post.postId)
                        put("postUrl", post.postUrl)
                        put("timestampIso", post.timestampIso)
                        put("timestampEpoch", post.timestampEpoch)
                        put("displayTime", post.displayTime)
                        put("viewsText", post.viewsText ?: JSONObject.NULL)
                        put("text", post.text)
                        put("linksJson", post.linksJson)
                        put("mentionsJson", post.mentionsJson)
                        put("hashtagsJson", post.hashtagsJson)
                        put("forwardedFrom", post.forwardedFrom ?: JSONObject.NULL)
                        put("replyToAuthor", post.replyToAuthor ?: JSONObject.NULL)
                        put("replyToSnippet", post.replyToSnippet ?: JSONObject.NULL)
                        put("mediaJson", post.mediaJson)
                        put("linkPreviewJson", post.linkPreviewJson ?: JSONObject.NULL)
                        put("pollJson", post.pollJson ?: JSONObject.NULL)
                        put("fetchedAt", post.fetchedAt)
                    })
                }
            })
        }
        return root.toString(2)
    }

    fun exportToOpml(channels: List<ChannelEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("""  <head><title>OpenFeed Subscriptions</title></head>""")
        sb.appendLine("""  <body>""")
        channels.forEach { ch ->
            val safeTitle = ch.title
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val url = if (ch.sourceType == FeedSourceType.RSS.name && !ch.feedUrl.isNullOrBlank()) {
                ch.feedUrl
            } else {
                "https://t.me/s/${ch.username}"
            }.replace("&", "&amp;")
            sb.appendLine("""    <outline text="$safeTitle" title="$safeTitle" type="rss" xmlUrl="$url" htmlUrl="$url" />""")
        }
        sb.appendLine("""  </body>""")
        sb.appendLine("""</opml>""")
        return sb.toString()
    }

    fun importFromContent(rawContent: String): ImportBackupPayload {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return ImportBackupPayload(emptyList(), emptyList())
        }

        // 1. Try OpenFeed JSON format
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJsonBackup(trimmed)?.let { return it }
        }

        // 2. Try OPML XML format
        if (trimmed.contains("<opml", ignoreCase = true) || trimmed.contains("<outline", ignoreCase = true)) {
            val opmlChannels = parseOpml(trimmed)
            if (opmlChannels.isNotEmpty()) {
                return ImportBackupPayload(opmlChannels, emptyList())
            }
        }

        // 3. Fallback: Plain text lines containing @username, t.me/channel, or RSS URLs
        val textChannels = parsePlainTextList(trimmed)
        return ImportBackupPayload(textChannels, emptyList())
    }

    private fun parseJsonBackup(raw: String): ImportBackupPayload? {
        return runCatching {
            val channels = mutableListOf<ImportedChannelItem>()
            val bookmarks = mutableListOf<PostEntity>()
            val seen = mutableSetOf<String>()

            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i)
                    when (item) {
                        is String -> parseTokenToItem(item, item, false)?.let { parsed ->
                            if (seen.add(parsed.username.lowercase())) channels.add(parsed)
                        }
                        is JSONObject -> {
                            val feedUrl = item.optStringOrNull("feedUrl")
                            val rawUser = item.optString("username", item.optString("channel", ""))
                            val type = FeedSourceType.entries.firstOrNull {
                                it.name == item.optString("sourceType", "")
                            }
                            if (type == FeedSourceType.RSS && !feedUrl.isNullOrBlank()) {
                                val key = rawUser.ifBlank { StarterChannels.buildRssSourceKey(feedUrl) }
                                if (seen.add(key.lowercase())) {
                                    channels.add(
                                        ImportedChannelItem(
                                            username = key,
                                            title = item.optString("title", feedUrl).ifBlank { feedUrl },
                                            isFavorite = item.optBoolean("isFavorite", false),
                                            sourceType = FeedSourceType.RSS,
                                            feedUrl = feedUrl
                                        )
                                    )
                                }
                            } else {
                                parseTokenToItem(
                                    token = feedUrl ?: rawUser,
                                    title = item.optString("title", ""),
                                    isFavorite = item.optBoolean("isFavorite", false)
                                )?.let { parsed ->
                                    if (seen.add(parsed.username.lowercase())) channels.add(parsed)
                                }
                            }
                        }
                    }
                }
                return@runCatching ImportBackupPayload(channels, bookmarks)
            }

            val root = JSONObject(raw)
            val channelsArr = root.optJSONArray("channels") ?: JSONArray()
            for (i in 0 until channelsArr.length()) {
                val obj = channelsArr.getJSONObject(i)
                val sourceType = FeedSourceType.entries.firstOrNull {
                    it.name == obj.optString("sourceType", "")
                } ?: FeedSourceType.TELEGRAM
                val feedUrl = obj.optStringOrNull("feedUrl")
                val rawUser = obj.optString("username", "")

                if (sourceType == FeedSourceType.RSS && !feedUrl.isNullOrBlank()) {
                    val key = rawUser.ifBlank { StarterChannels.buildRssSourceKey(feedUrl) }
                    if (seen.add(key.lowercase())) {
                        channels.add(
                            ImportedChannelItem(
                                username = key,
                                title = obj.optString("title", feedUrl).ifBlank { feedUrl },
                                isFavorite = obj.optBoolean("isFavorite", false),
                                sourceType = FeedSourceType.RSS,
                                feedUrl = feedUrl
                            )
                        )
                    }
                } else {
                    val u = UrlRewriter.normalizeUsername(rawUser) ?: continue
                    if (seen.add(u.lowercase())) {
                        channels.add(
                            ImportedChannelItem(
                                username = u,
                                title = obj.optString("title", "@$u").ifBlank { "@$u" },
                                isFavorite = obj.optBoolean("isFavorite", false),
                                sourceType = FeedSourceType.TELEGRAM,
                                feedUrl = feedUrl
                            )
                        )
                    }
                }
            }

            val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bookmarksArr.length()) {
                val p = bookmarksArr.getJSONObject(i)
                val channelKey = p.optString("channelUsername", "").trim()
                val sourceTypeStr = p.optString("sourceType", FeedSourceType.TELEGRAM.name)
                val u = if (sourceTypeStr == FeedSourceType.RSS.name && channelKey.startsWith("rss_")) {
                    channelKey
                } else {
                    UrlRewriter.normalizeUsername(channelKey) ?: continue
                }
                val postId = p.optLong("postId", -1L)
                if (postId <= 0L) continue
                val uniqueKey = p.optString("uniqueKey", "${u}_$postId").ifBlank { "${u}_$postId" }
                bookmarks.add(
                    PostEntity(
                        uniqueKey = uniqueKey,
                        channelUsername = u,
                        channelTitle = p.optString("channelTitle", u),
                        channelAvatarUrl = p.optStringOrNull("channelAvatarUrl"),
                        channelVerified = p.optBoolean("channelVerified", false),
                        sourceType = sourceTypeStr,
                        postId = postId,
                        postUrl = p.optString("postUrl", "https://t.me/$u/$postId"),
                        timestampIso = p.optString("timestampIso", ""),
                        timestampEpoch = p.optLong("timestampEpoch", System.currentTimeMillis()),
                        displayTime = p.optString("displayTime", ""),
                        viewsText = p.optStringOrNull("viewsText"),
                        text = p.optString("text", ""),
                        linksJson = p.optString("linksJson", MediaSerializer.encodeStringList(emptyList())),
                        mentionsJson = p.optString("mentionsJson", MediaSerializer.encodeStringList(emptyList())),
                        hashtagsJson = p.optString("hashtagsJson", MediaSerializer.encodeStringList(emptyList())),
                        forwardedFrom = p.optStringOrNull("forwardedFrom"),
                        replyToAuthor = p.optStringOrNull("replyToAuthor"),
                        replyToSnippet = p.optStringOrNull("replyToSnippet"),
                        mediaJson = p.optString("mediaJson", "[]"),
                        linkPreviewJson = p.optStringOrNull("linkPreviewJson"),
                        pollJson = p.optStringOrNull("pollJson"),
                        isBookmarked = true,
                        fetchedAt = p.optLong("fetchedAt", System.currentTimeMillis())
                    )
                )
            }

            ImportBackupPayload(channels, bookmarks)
        }.getOrNull()
    }

    private fun parseOpml(rawXml: String): List<ImportedChannelItem> {
        return runCatching {
            val doc = Jsoup.parse(rawXml, "", Parser.xmlParser())
            val seen = mutableSetOf<String>()
            doc.select("outline").mapNotNull { el ->
                val url = el.attr("xmlUrl").ifBlank { el.attr("htmlUrl") }.ifBlank { el.attr("text") }
                val title = el.attr("title").ifBlank { el.attr("text") }
                val item = parseTokenToItem(url, title, false) ?: return@mapNotNull null
                if (!seen.add(item.username.lowercase())) return@mapNotNull null
                item
            }
        }.getOrDefault(emptyList())
    }

    private fun parsePlainTextList(rawText: String): List<ImportedChannelItem> {
        val seen = mutableSetOf<String>()
        return rawText.split(Regex("[\\s,;]+"))
            .mapNotNull { token ->
                val item = parseTokenToItem(token, "", false) ?: return@mapNotNull null
                if (!seen.add(item.username.lowercase())) return@mapNotNull null
                item
            }
    }

    private fun parseTokenToItem(token: String, title: String, isFavorite: Boolean): ImportedChannelItem? {
        val clean = token.trim()
        if (clean.isEmpty()) return null
        return if (UrlRewriter.isRssFeedUrl(clean)) {
            val key = StarterChannels.buildRssSourceKey(clean)
            ImportedChannelItem(
                username = key,
                title = title.ifBlank { clean },
                isFavorite = isFavorite,
                sourceType = FeedSourceType.RSS,
                feedUrl = clean
            )
        } else {
            val u = UrlRewriter.normalizeUsername(clean) ?: return null
            ImportedChannelItem(
                username = u,
                title = title.ifBlank { "@$u" },
                isFavorite = isFavorite,
                sourceType = FeedSourceType.TELEGRAM,
                feedUrl = "https://t.me/s/$u"
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.takeIf { it.isNotBlank() && it != "null" }
    }
}
