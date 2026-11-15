package com.example.data.network

import com.example.data.model.BridgeLangProfile
import com.example.data.model.ChannelInfo
import com.example.data.model.FeedPost
import com.example.data.model.LinkPreview
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.model.PollData
import com.example.data.model.PollOption
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ParsedChannelPage(
    val channel: ChannelInfo,
    val posts: List<FeedPost>,
    val olderBeforeId: Long?
)

object TelegramHtmlParser {

    private val bgImageRegex = Regex("""background-image:\s*url\(['"]?([^'")]+)['"]?\)""", RegexOption.IGNORE_CASE)
    private val mentionRegex = Regex("""@[A-Za-z][A-Za-z0-9_]{3,}""")
    private val hashtagRegex = Regex("""#[\p{L}\p{N}_]+""")

    fun parse(
        html: String,
        username: String,
        transportUsed: String,
        latencyMs: Long,
        bridgeProfile: BridgeLangProfile,
        existingIsFavorite: Boolean = false
    ): ParsedChannelPage {
        val doc = Jsoup.parse(html, "https://t.me/s/$username")
        // Remove Google Translate injected helper nodes so text doesn't get duplicated
        doc.select("script, style, .google-src-text, #gt-nvframe, .skiptranslate").remove()

        val channelTitle = doc.selectFirst(".tgme_channel_info_header_title span, .tgme_channel_info_header_title")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: "@$username"

        val description = doc.selectFirst(".tgme_channel_info_description")
            ?.let { extractCleanText(it) } ?: ""

        val rawAvatar = doc.selectFirst(".tgme_channel_info_header .tgme_page_photo_image img, .tgme_page_photo_image img")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val avatarUrl = rawAvatar?.let { UrlRewriter.toDirectTelegramUrl(it) }

        val subscribersText = doc.select(".tgme_channel_info_counter").firstOrNull { counter ->
            val type = counter.selectFirst(".counter_type")?.text()?.lowercase(Locale.US) ?: ""
            type.contains("subscriber") || type.contains("member") || type.contains("مشترک") || type.contains("عضو")
        }?.selectFirst(".counter_value")?.text()?.trim()

        val isVerified = doc.selectFirst(".tgme_channel_info_header .verified-icon") != null

        val now = System.currentTimeMillis()
        val channel = ChannelInfo(
            username = username,
            title = channelTitle,
            description = description,
            avatarUrl = avatarUrl,
            subscribersText = subscribersText,
            isVerified = isVerified,
            isFavorite = existingIsFavorite,
            lastFetchedAt = now,
            lastTransportUsed = transportUsed,
            lastLatencyMs = latencyMs
        )

        val messageElements = doc.select(".tgme_widget_message_wrap .tgme_widget_message")
        val posts = mutableListOf<FeedPost>()

        for (msgEl in messageElements) {
            if (msgEl.hasClass("service_message")) continue
            val post = parseMessageElement(
                msgEl = msgEl,
                channel = channel,
                bridgeProfile = bridgeProfile,
                fetchedAt = now
            )
            if (post != null) {
                posts.add(post)
            }
        }

        val olderBeforeId = extractOlderBeforeId(doc, posts)

        return ParsedChannelPage(
            channel = channel,
            posts = posts.sortedByDescending { it.postId },
            olderBeforeId = olderBeforeId
        )
    }

    private fun parseMessageElement(
        msgEl: Element,
        channel: ChannelInfo,
        bridgeProfile: BridgeLangProfile,
        fetchedAt: Long
    ): FeedPost? {
        val dataPost = msgEl.attr("data-post").trim()
        val dateLinkEl = msgEl.selectFirst("a.tgme_widget_message_date")
        val rawDateHref = dateLinkEl?.attr("href")?.trim().orEmpty()

        val postId = dataPost.substringAfterLast("/", "").toLongOrNull()
            ?: rawDateHref.substringBefore("?").substringAfterLast("/", "").toLongOrNull()
            ?: return null

        val timeEl = msgEl.selectFirst("a.tgme_widget_message_date time[datetime]")
        val timestampIso = timeEl?.attr("datetime")?.trim().orEmpty()
        val timestampEpoch = parseIsoTimestamp(timestampIso) ?: fetchedAt
        val displayTime = formatDisplayTime(timestampEpoch, timeEl?.text()?.trim().orEmpty())

        val viewsText = msgEl.selectFirst(".tgme_widget_message_views")?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val textEl = msgEl.selectFirst(".tgme_widget_message_text")
        val text = textEl?.let { extractCleanText(it) } ?: ""

        val links = textEl?.select("a[href]")?.mapNotNull { link ->
            val href = UrlRewriter.toDirectTelegramUrl(link.attr("href").trim())
            href.takeIf { it.startsWith("http") }
        }?.distinct().orEmpty()

        val mentions = mentionRegex.findAll(text).map { it.value }.distinct().toList()
        val hashtags = hashtagRegex.findAll(text).map { it.value }.distinct().toList()

        val forwardedFrom = msgEl.selectFirst(".tgme_widget_message_forwarded_from_name")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val replyEl = msgEl.selectFirst(".tgme_widget_message_reply")
        val replyToAuthor = replyEl?.selectFirst(".tgme_widget_message_author_name, .tgme_widget_message_author")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val replyToSnippet = replyEl?.selectFirst(".tgme_widget_message_metatext, .tgme_widget_message_text")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val mediaList = parseMediaAttachments(msgEl, bridgeProfile)
        val linkPreview = parseLinkPreview(msgEl)
        val poll = parsePoll(msgEl)

        if (text.isBlank() && mediaList.isEmpty() && linkPreview == null && poll == null) {
            return null
        }

        val canonicalPostUrl = "https://t.me/${channel.username}/$postId"

        return FeedPost(
            uniqueKey = "${channel.username}_$postId",
            channelUsername = channel.username,
            channelTitle = channel.title,
            channelAvatarUrl = channel.avatarUrl,
            channelVerified = channel.isVerified,
            postId = postId,
            postUrl = canonicalPostUrl,
            timestampIso = timestampIso,
            timestampEpoch = timestampEpoch,
            displayTime = displayTime,
            viewsText = viewsText,
            text = text,
            links = links,
            mentions = mentions,
            hashtags = hashtags,
            forwardedFrom = forwardedFrom,
            replyToAuthor = replyToAuthor,
            replyToSnippet = replyToSnippet,
            media = mediaList,
            linkPreview = linkPreview,
            poll = poll,
            isBookmarked = false,
            fetchedAt = fetchedAt
        )
    }

    private fun parseMediaAttachments(
        msgEl: Element,
        bridgeProfile: BridgeLangProfile
    ): List<MediaAttachment> {
        val attachments = mutableListOf<MediaAttachment>()
        val seenUrls = mutableSetOf<String>()

        // 1. Photos (single or grouped album)
        msgEl.select("a.tgme_widget_message_photo_wrap").forEach { photoEl ->
            val style = photoEl.attr("style")
            val match = bgImageRegex.find(style)
            val rawUrl = match?.groupValues?.getOrNull(1)?.trim()
                ?: photoEl.selectFirst("img")?.attr("src")?.trim()
            if (!rawUrl.isNullOrBlank()) {
                val direct = UrlRewriter.toDirectTelegramUrl(rawUrl)
                if (seenUrls.add(direct)) {
                    attachments.add(
                        MediaAttachment(
                            type = MediaType.PHOTO,
                            url = direct,
                            bridgeUrl = UrlRewriter.toTranslateProxyUrl(direct, bridgeProfile)
                        )
                    )
                }
            }
        }

        // 2. Videos & GIFs
        msgEl.select(".tgme_widget_message_video_player, .tgme_widget_message_roundvideo_player").forEach { videoWrap ->
            val videoEl = videoWrap.selectFirst("video[src]")
            val rawVideoUrl = videoEl?.attr("src")?.trim()
                ?: videoWrap.selectFirst("source[src]")?.attr("src")?.trim()
            val thumbStyle = videoWrap.selectFirst("i.tgme_widget_message_video_thumb")?.attr("style").orEmpty()
            val rawThumb = bgImageRegex.find(thumbStyle)?.groupValues?.getOrNull(1)?.trim()
            val duration = videoWrap.selectFirst(".message_video_duration")?.text()?.trim()
            val isGif = videoWrap.selectFirst(".tgme_widget_message_gif_label") != null ||
                videoWrap.hasClass("tgme_widget_message_gif")

            if (!rawVideoUrl.isNullOrBlank()) {
                val direct = UrlRewriter.toDirectTelegramUrl(rawVideoUrl)
                val directThumb = rawThumb?.let { UrlRewriter.toDirectTelegramUrl(it) }
                if (seenUrls.add(direct)) {
                    attachments.add(
                        MediaAttachment(
                            type = if (isGif) MediaType.GIF else MediaType.VIDEO,
                            url = direct,
                            bridgeUrl = UrlRewriter.toTranslateProxyUrl(direct, bridgeProfile),
                            thumbnailUrl = directThumb,
                            thumbnailBridgeUrl = directThumb?.let { UrlRewriter.toTranslateProxyUrl(it, bridgeProfile) },
                            meta = duration
                        )
                    )
                }
            } else if (!rawThumb.isNullOrBlank()) {
                val directThumb = UrlRewriter.toDirectTelegramUrl(rawThumb)
                if (seenUrls.add("thumb_$directThumb")) {
                    attachments.add(
                        MediaAttachment(
                            type = MediaType.PHOTO,
                            url = directThumb,
                            bridgeUrl = UrlRewriter.toTranslateProxyUrl(directThumb, bridgeProfile),
                            title = "Video Preview",
                            meta = duration
                        )
                    )
                }
            }
        }

        // 3. Voice & Audio messages
        msgEl.select(".tgme_widget_message_voice_player, .tgme_widget_message_audio_wrap").forEach { audioWrap ->
            val audioUrl = audioWrap.selectFirst("audio[src]")?.attr("src")?.trim()
            val duration = audioWrap.selectFirst(".tgme_widget_message_voice_duration")?.text()?.trim()
            val title = audioWrap.selectFirst(".tgme_widget_message_audio_title")?.text()?.trim()
                ?: "Voice Message"
            val isVoice = audioWrap.hasClass("tgme_widget_message_voice_player") ||
                audioWrap.selectFirst(".tgme_widget_message_voice") != null

            if (!audioUrl.isNullOrBlank()) {
                val direct = UrlRewriter.toDirectTelegramUrl(audioUrl)
                if (seenUrls.add(direct)) {
                    attachments.add(
                        MediaAttachment(
                            type = if (isVoice) MediaType.VOICE else MediaType.AUDIO,
                            url = direct,
                            bridgeUrl = UrlRewriter.toTranslateProxyUrl(direct, bridgeProfile),
                            title = title,
                            meta = duration
                        )
                    )
                }
            }
        }

        // 4. Documents / Files
        msgEl.select(".tgme_widget_message_document_wrap").forEach { docWrap ->
            val title = docWrap.selectFirst(".tgme_widget_message_document_title")?.text()?.trim() ?: "Document"
            val extra = docWrap.selectFirst(".tgme_widget_message_document_extra")?.text()?.trim()
            val href = docWrap.selectFirst("a[href]")?.attr("href")?.trim()
                ?: docWrap.parents().firstOrNull { it.tagName() == "a" }?.attr("href")?.trim()

            if (!href.isNullOrBlank()) {
                val direct = UrlRewriter.toDirectTelegramUrl(href)
                if (seenUrls.add("doc_$direct")) {
                    attachments.add(
                        MediaAttachment(
                            type = MediaType.DOCUMENT,
                            url = direct,
                            bridgeUrl = UrlRewriter.toTranslateProxyUrl(direct, bridgeProfile),
                            title = title,
                            meta = extra
                        )
                    )
                }
            }
        }

        return attachments
    }

    private fun parseLinkPreview(msgEl: Element): LinkPreview? {
        val previewEl = msgEl.selectFirst("a.tgme_widget_message_link_preview") ?: return null
        val href = previewEl.attr("href").trim().takeIf { it.isNotEmpty() } ?: return null
        val siteName = previewEl.selectFirst(".link_preview_site_name")?.text()?.trim()
        val title = previewEl.selectFirst(".link_preview_title")?.text()?.trim()
        val description = previewEl.selectFirst(".link_preview_description")?.text()?.trim()
        val imgStyle = previewEl.selectFirst(".link_preview_image, .link_preview_right_image")?.attr("style").orEmpty()
        val imgUrl = bgImageRegex.find(imgStyle)?.groupValues?.getOrNull(1)?.trim()
            ?.let { UrlRewriter.toDirectTelegramUrl(it) }

        if (title.isNullOrBlank() && description.isNullOrBlank()) return null

        return LinkPreview(
            siteName = siteName,
            title = title,
            description = description,
            url = UrlRewriter.toDirectTelegramUrl(href),
            imageUrl = imgUrl
        )
    }

    private fun parsePoll(msgEl: Element): PollData? {
        val pollEl = msgEl.selectFirst(".tgme_widget_message_poll") ?: return null
        val question = pollEl.selectFirst(".tgme_widget_message_poll_question")?.text()?.trim()
            ?: return null
        val totalVotes = pollEl.selectFirst(".tgme_widget_message_poll_votes")?.text()?.trim()
        val options = pollEl.select(".tgme_widget_message_poll_option").mapNotNull { optEl ->
            val optText = optEl.selectFirst(".tgme_widget_message_poll_option_text")?.text()?.trim()
                ?: return@mapNotNull null
            val percentText = optEl.selectFirst(".tgme_widget_message_poll_option_percent")?.text()?.trim() ?: "0%"
            val percentFloat = (percentText.removeSuffix("%").toFloatOrNull() ?: 0f) / 100f
            PollOption(
                text = optText,
                percentText = percentText,
                percentFloat = percentFloat.coerceIn(0f, 1f)
            )
        }
        if (options.isEmpty()) return null
        return PollData(question = question, options = options, totalVotes = totalVotes)
    }

    private fun extractOlderBeforeId(doc: org.jsoup.nodes.Document, posts: List<FeedPost>): Long? {
        val moreLink = doc.selectFirst("a.tme_messages_more")
        val dataBefore = moreLink?.attr("data-before")?.toLongOrNull()
        if (dataBefore != null && dataBefore > 0) return dataBefore

        val hrefBefore = moreLink?.attr("href")?.let { href ->
            Regex("[?&]before=(\\d+)").find(href)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
        if (hrefBefore != null && hrefBefore > 0) return hrefBefore

        val minId = posts.minOfOrNull { it.postId }
        return if (minId != null && minId > 1) minId else null
    }

    private fun extractCleanText(element: Element): String {
        val sb = StringBuilder()
        fun walk(node: org.jsoup.nodes.Node) {
            when (node) {
                is TextNode -> sb.append(node.wholeText)
                is Element -> {
                    when (node.tagName().lowercase(Locale.US)) {
                        "br" -> sb.append("\n")
                        "p", "div" -> {
                            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                            node.childNodes().forEach { walk(it) }
                            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                        }
                        "blockquote" -> {
                            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                            sb.append("❝ ")
                            node.childNodes().forEach { walk(it) }
                            if (!sb.endsWith("\n")) sb.append("\n")
                        }
                        else -> node.childNodes().forEach { walk(it) }
                    }
                }
            }
        }
        element.childNodes().forEach { walk(it) }
        return sb.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun parseIsoTimestamp(iso: String): Long? {
        if (iso.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun formatDisplayTime(epochMs: Long, fallback: String): String {
        if (epochMs <= 0L) return fallback
        val fmt = SimpleDateFormat("MMM d • HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }
}
