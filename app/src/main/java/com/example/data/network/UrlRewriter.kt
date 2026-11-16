package com.example.data.network

import android.net.Uri
import com.example.data.model.BridgeLangProfile
import com.example.data.model.MediaAttachment
import com.example.data.model.TransportMode

object UrlRewriter {

    private val usernameRegex = Regex("^[A-Za-z][A-Za-z0-9_]{3,63}$")

    fun isRssFeedUrl(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) return false
        val host = runCatching { Uri.parse(trimmed).host?.lowercase() }.getOrNull() ?: return false
        return host !in setOf("t.me", "www.t.me", "telegram.me", "www.telegram.me", "t-me.translate.goog")
    }

    fun normalizeUsername(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        if (isRssFeedUrl(trimmed)) return null

        val candidate = when {
            trimmed.startsWith("@") -> trimmed.removePrefix("@")
            trimmed.contains("://") || trimmed.startsWith("t.me/") || trimmed.startsWith("telegram.me/") -> {
                val normalizedUrl = if (trimmed.contains("://")) trimmed else "https://$trimmed"
                val uri = runCatching { Uri.parse(normalizedUrl) }.getOrNull()
                val segments = uri?.pathSegments?.filter { it.isNotBlank() }.orEmpty()
                when {
                    segments.isEmpty() -> ""
                    segments[0].equals("s", ignoreCase = true) && segments.size >= 2 -> segments[1]
                    else -> segments[0]
                }
            }
            else -> trimmed
        }.substringBefore("?").substringBefore("#").trim('/')

        return candidate.takeIf { usernameRegex.matches(it) }
    }

    fun buildDirectChannelUrl(username: String, beforeId: Long? = null): String {
        val base = "https://t.me/s/$username"
        return if (beforeId != null && beforeId > 0) "$base?before=$beforeId" else base
    }

    fun buildTranslateBridgeChannelUrl(
        username: String,
        beforeId: Long? = null,
        profile: BridgeLangProfile
    ): String {
        val builder = Uri.parse("https://t-me.translate.goog/s/$username").buildUpon()
            .appendQueryParameter("_x_tr_sl", profile.sl)
            .appendQueryParameter("_x_tr_tl", profile.tl)
            .appendQueryParameter("_x_tr_hl", "en")
            .appendQueryParameter("_x_tr_pto", "wapp")
        if (beforeId != null && beforeId > 0) {
            builder.appendQueryParameter("before", beforeId.toString())
        }
        return builder.build().toString()
    }

    fun buildCustomMirrorUrl(mirrorBase: String, username: String, beforeId: Long? = null): String? {
        val clean = mirrorBase.trim().trimEnd('/')
        if (clean.isEmpty()) return null
        val withHttps = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
        val base = if (withHttps.contains("{channel}")) {
            withHttps.replace("{channel}", username)
        } else {
            "$withHttps/s/$username"
        }
        return if (beforeId != null && beforeId > 0) {
            val sep = if (base.contains("?")) "&" else "?"
            "$base${sep}before=$beforeId"
        } else {
            base
        }
    }

    fun buildCustomMirrorRssUrl(mirrorBase: String, feedUrl: String): String? {
        val clean = mirrorBase.trim().trimEnd('/')
        if (clean.isEmpty()) return null
        val withHttps = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
        val encoded = Uri.encode(feedUrl.trim())
        return when {
            withHttps.contains("{url}") -> withHttps.replace("{url}", encoded)
            withHttps.contains("?") -> "$withHttps&url=$encoded"
            else -> "$withHttps/?url=$encoded"
        }
    }

    fun toDirectTelegramUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return rawUrl
        val host = uri.host ?: return rawUrl

        val restoredHost = if (host.endsWith(".translate.goog")) {
            val encodedHost = host.removeSuffix(".translate.goog")
            restoreOriginalHost(encodedHost)
        } else {
            host
        }

        val cleanBuilder = uri.buildUpon().scheme("https").authority(restoredHost).clearQuery()
        for (name in uri.queryParameterNames) {
            if (!name.startsWith("_x_tr_")) {
                uri.getQueryParameters(name).forEach { value ->
                    cleanBuilder.appendQueryParameter(name, value)
                }
            }
        }
        return cleanBuilder.build().toString()
    }

    fun isTelegramOwnedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        return h == "t.me" || h.endsWith(".t.me") ||
            h == "telegram.me" || h.endsWith(".telegram.me") ||
            h == "telegram.org" || h.endsWith(".telegram.org") ||
            h.endsWith("telesco.pe")
    }

    fun toTranslateProxyUrl(
        rawUrl: String,
        profile: BridgeLangProfile = BridgeLangProfile.PRESERVE_ORIGINAL,
        forceAnyHost: Boolean = false
    ): String {
        if (rawUrl.isBlank()) return rawUrl
        val directUrl = toDirectTelegramUrl(rawUrl)
        val uri = runCatching { Uri.parse(directUrl) }.getOrNull() ?: return rawUrl
        val host = uri.host ?: return rawUrl
        if (host.endsWith(".translate.goog")) return rawUrl
        if (!forceAnyHost && !isTelegramOwnedHost(host)) return rawUrl

        val proxiedHost = host.replace("-", "--").replace(".", "-") + ".translate.goog"
        val builder = uri.buildUpon()
            .scheme("https")
            .authority(proxiedHost)
            .appendQueryParameter("_x_tr_sl", profile.sl)
            .appendQueryParameter("_x_tr_tl", profile.tl)
            .appendQueryParameter("_x_tr_hl", "en")
            .appendQueryParameter("_x_tr_pto", "wapp")
        return builder.build().toString()
    }

    fun resolvePreferredUrl(
        directUrl: String?,
        bridgeUrl: String?,
        mode: TransportMode
    ): String? {
        return when (mode) {
            TransportMode.DIRECT -> directUrl ?: bridgeUrl
            TransportMode.TRANSLATE_BRIDGE, TransportMode.AUTO -> bridgeUrl?.takeIf { it.isNotBlank() } ?: directUrl
            TransportMode.CUSTOM_MIRROR -> bridgeUrl?.takeIf { it.isNotBlank() } ?: directUrl
        }
    }

    fun resolveMediaUrl(attachment: MediaAttachment, mode: TransportMode): String {
        return when (mode) {
            TransportMode.DIRECT -> attachment.url.ifBlank { attachment.bridgeUrl }
            TransportMode.TRANSLATE_BRIDGE, TransportMode.AUTO, TransportMode.CUSTOM_MIRROR ->
                attachment.bridgeUrl.ifBlank { attachment.url }
        }
    }

    private fun restoreOriginalHost(encodedHost: String): String {
        val placeholder = "\u0000"
        return encodedHost
            .replace("--", placeholder)
            .replace("-", ".")
            .replace(placeholder, "-")
    }
}
