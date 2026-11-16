package com.example.data.network

import android.net.Uri
import com.example.data.model.ChannelInfo
import com.example.data.model.FeedPost
import com.example.data.model.FeedSourceType
import com.example.data.model.LinkPreview
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.model.StarterChannels
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object RssFeedParser {

    fun looksLikeHtmlPage(content: String): Boolean {
        val head = content.trimStart().take(600).lowercase(Locale.US)
        val hasFeedRoot = head.contains("<rss") || head.contains("<feed") || head.contains("<rdf:rdf")
        if (hasFeedRoot) return false
        return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<head>")
    }

    fun discoverFeedUrlFromHtml(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val linkEl = doc.select("link[rel~=alternate][href]").firstOrNull { el ->
            val type = el.attr("type").lowercase(Locale.US)
            type.contains("rss") || type.contains("atom") || type.contains("xml")
        }
        val discovered = linkEl?.absUrl("href")?.trim()?.takeIf { it.startsWith("http") }
            ?: linkEl?.attr("href")?.trim()?.takeIf { it.startsWith("http") }
        return discovered?.let { UrlRewriter.toDirectTelegramUrl(it) }
    }

    fun parse(
        xml: String,
        sourceKey: String,
        feedUrl: String,
        transportUsed: String,
        latencyMs: Long,
        fallbackTitle: String? = null,
        fallbackLogoUrl: String? = null,
        existingIsFavorite: Boolean = false
    ): ParsedChannelPage {
        val doc = Jsoup.parse(xml, feedUrl, Parser.xmlParser())
        val now = System.currentTimeMillis()
        val isAtom = doc.selectFirst("feed") != null && doc.selectFirst("channel") == null

        val channelEl = if (isAtom) doc.selectFirst("feed") else doc.selectFirst("channel")
        val rawTitle = channelEl?.selectFirst("> title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackTitle
            ?: Uri.parse(feedUrl).host
            ?: "RSS Feed"

        val description = if (isAtom) {
            channelEl?.selectFirst("> subtitle")?.text()?.trim().orEmpty()
        } else {
            channelEl?.selectFirst("> description")?.text()?.trim().orEmpty()
        }

        val siteLink = if (isAtom) {
            channelEl?.select("link")?.firstOrNull {
                val rel = it.attr("rel")
                rel.isBlank() || rel == "alternate"
            }?.attr("href")?.trim()
        } else {
            channelEl?.selectFirst("> link")?.text()?.trim()
        }?.takeIf { it.startsWith("http") } ?: feedUrl

        val xmlLogo = if (isAtom) {
            channelEl?.selectFirst("> logo, > icon")?.text()?.trim()
        } else {
            channelEl?.selectFirst("> image > url")?.text()?.trim()
                ?: channelEl?.selectFirst("itunes|image, itunes\\:image")?.attr("href")?.trim()
        }?.takeIf { it.startsWith("http") }

        val resolvedLogo = xmlLogo
            ?: fallbackLogoUrl?.takeIf { it.isNotBlank() }
            ?: StarterChannels.rssLogoFallback(siteLink)

        val channel = ChannelInfo(
            username = sourceKey,
            title = rawTitle,
            description = stripHtmlToText(description),
            avatarUrl = resolvedLogo,
            subscribersText = Uri.parse(siteLink).host?.removePrefix("www."),
            isVerified = false,
            isFavorite = existingIsFavorite,
            sourceType = FeedSourceType.RSS,
            feedUrl = feedUrl,
            lastFetchedAt = now,
            lastTransportUsed = transportUsed,
            lastLatencyMs = latencyMs
        )

        val itemElements = if (isAtom) {
            doc.select("feed > entry")
        } else {
            doc.select("channel > item, item")
        }

        val posts = itemElements.mapIndexedNotNull { index, itemEl ->
            parseItem(
                itemEl = itemEl,
                isAtom = isAtom,
                channel = channel,
                siteLink = siteLink,
                fallbackOrder = index,
                fetchedAt = now
            )
        }.sortedByDescending { it.timestampEpoch }

        return ParsedChannelPage(
            channel = channel,
            posts = posts,
            olderBeforeId = null
        )
    }

    private fun parseItem(
        itemEl: Element,
        isAtom: Boolean,
        channel: ChannelInfo,
        siteLink: String,
        fallbackOrder: Int,
        fetchedAt: Long
    ): FeedPost? {
        val title = itemEl.selectFirst("> title")?.text()?.trim().orEmpty()
        val link = if (isAtom) {
            itemEl.select("link").firstOrNull {
                val rel = it.attr("rel")
                rel.isBlank() || rel == "alternate"
            }?.attr("href")?.trim()
        } else {
            itemEl.selectFirst("> link")?.text()?.trim()
        }?.takeIf { it.isNotBlank() } ?: siteLink

        val guid = if (isAtom) {
            itemEl.selectFirst("> id")?.text()?.trim()
        } else {
            itemEl.selectFirst("> guid")?.text()?.trim()
        } ?: link

        val rawHtmlBody = if (isAtom) {
            itemEl.selectFirst("> content")?.text()
                ?: itemEl.selectFirst("> summary")?.text()
                ?: ""
        } else {
            itemEl.selectFirst("content\\:encoded, encoded")?.text()
                ?: itemEl.selectFirst("> description")?.text()
                ?: ""
        }

        val cleanBody = stripHtmlToText(rawHtmlBody)
        if (title.isBlank() && cleanBody.isBlank()) return null

        val rawDate = if (isAtom) {
            itemEl.selectFirst("> published, > updated")?.text()?.trim()
        } else {
            itemEl.selectFirst("> pubDate, dc\\:date")?.text()?.trim()
        }.orEmpty()

        val epoch = parseRssTimestamp(rawDate) ?: (fetchedAt - fallbackOrder * 60_000L)
        val displayTime = formatDisplayTime(epoch)
        val postId = abs((guid + title).hashCode().toLong()).coerceAtLeast(1L)

        val mediaList = extractItemMedia(itemEl, rawHtmlBody)
        val author = itemEl.selectFirst("> author > name, > author, dc\\:creator")?.text()?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("@") }

        val combinedText = buildString {
            if (title.isNotBlank()) append(title)
            if (cleanBody.isNotBlank() && !cleanBody.equals(title, ignoreCase = true)) {
                if (isNotEmpty()) append("\n\n")
                append(cleanBody.take(900))
                if (cleanBody.length > 900) append("…")
            }
        }

        val hostLabel = Uri.parse(link).host?.removePrefix("www.")
        val linkPreview = if (link.startsWith("http")) {
            LinkPreview(
                siteName = hostLabel,
                title = title.ifBlank { hostLabel },
                description = cleanBody.take(180).takeIf { it.isNotBlank() },
                url = link,
                imageUrl = mediaList.firstOrNull { it.type == MediaType.PHOTO }?.url
            )
        } else null

        // Avoid showing duplicate thumbnail in both photo grid & link preview if only 1 image
        val attachments = if (mediaList.size == 1 && mediaList[0].type == MediaType.PHOTO && linkPreview != null) {
            mediaList
        } else {
            mediaList
        }

        return FeedPost(
            uniqueKey = "${channel.username}_$postId",
            channelUsername = channel.username,
            channelTitle = channel.title,
            channelAvatarUrl = channel.avatarUrl,
            channelVerified = false,
            sourceType = FeedSourceType.RSS,
            postId = postId,
            postUrl = link,
            timestampIso = rawDate,
            timestampEpoch = epoch,
            displayTime = displayTime,
            viewsText = hostLabel,
            text = combinedText,
            links = listOf(link),
            mentions = emptyList(),
            hashtags = emptyList(),
            forwardedFrom = author,
            replyToAuthor = null,
            replyToSnippet = null,
            media = attachments,
            linkPreview = linkPreview,
            poll = null,
            isBookmarked = false,
            fetchedAt = fetchedAt
        )
    }

    private fun extractItemMedia(itemEl: Element, rawHtmlBody: String): List<MediaAttachment> {
        val result = mutableListOf<MediaAttachment>()
        val seen = mutableSetOf<String>()

        fun addMedia(type: MediaType, url: String, title: String? = null) {
            val clean = url.trim()
            if (clean.startsWith("http") && seen.add(clean)) {
                result.add(
                    MediaAttachment(
                        type = type,
                        url = clean,
                        bridgeUrl = clean,
                        title = title
                    )
                )
            }
        }

        itemEl.select("enclosure, media\\:content, media\\:thumbnail").forEach { el ->
            val url = el.attr("url").trim()
            val mime = el.attr("type").lowercase(Locale.US)
            val medium = el.attr("medium").lowercase(Locale.US)
            when {
                mime.startsWith("audio/") || medium == "audio" -> addMedia(MediaType.AUDIO, url, "Podcast / Audio")
                mime.startsWith("video/") || medium == "video" -> addMedia(MediaType.VIDEO, url)
                mime.startsWith("image/") || medium == "image" || el.tagName().contains("thumbnail") ->
                    addMedia(MediaType.PHOTO, url)
            }
        }

        if (result.none { it.type == MediaType.PHOTO } && rawHtmlBody.contains("<img", ignoreCase = true)) {
            val htmlDoc = Jsoup.parseBodyFragment(rawHtmlBody)
            htmlDoc.selectFirst("img[src]")?.attr("src")?.let { src ->
                if (!src.contains("1x1") && !src.contains("pixel")) {
                    addMedia(MediaType.PHOTO, src)
                }
            }
        }

        return result
    }

    private fun stripHtmlToText(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("script, style").remove()
        doc.select("br, p, li").before("\n")
        return doc.text()
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun parseRssTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw.trim())?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun formatDisplayTime(epochMs: Long): String {
        val fmt = SimpleDateFormat("MMM d • HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }
}
