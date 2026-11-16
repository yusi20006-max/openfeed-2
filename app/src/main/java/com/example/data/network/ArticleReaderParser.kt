package com.example.data.network

import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale
import kotlin.math.max

sealed interface ArticleBlock {
    data class Heading(val text: String, val level: Int) : ArticleBlock
    data class Paragraph(val text: String) : ArticleBlock
    data class Quote(val text: String) : ArticleBlock
    data class CodeBlock(val text: String) : ArticleBlock
}

data class ExtractedArticle(
    val url: String,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val publishedTime: String?,
    val leadImageUrl: String?,
    val blocks: List<ArticleBlock>,
    val estimatedReadMinutes: Int
)

object ArticleReaderParser {

    private val noiseSelector = listOf(
        "script", "style", "noscript", "iframe", "svg", "canvas",
        "nav", "footer", "aside", "form", "button",
        "[role=navigation]", "[role=banner]", "[role=complementary]", "[role=contentinfo]",
        ".nav", ".navbar", ".menu", ".sidebar", ".widget", ".footer", ".header",
        ".ads", ".ad", ".advert", ".advertisement", ".sponsored",
        ".cookie", ".cookie-banner", ".comments", ".comment-list", "#comments",
        ".share", ".social-share", ".related", ".related-posts", ".breadcrumb", ".breadcrumbs",
        ".google-src-text"
    ).joinToString(", ")

    private val candidateSelectors = listOf(
        "article",
        "[itemprop=articleBody]",
        "[role=main] article",
        "main article",
        ".article-body",
        ".article-content",
        ".post-content",
        ".entry-content",
        ".story-body",
        ".content-body",
        ".news-body",
        "main",
        "[role=main]"
    )

    fun parse(html: String, sourceUrl: String): ExtractedArticle {
        val doc = Jsoup.parse(html, sourceUrl)

        val ogTitle = doc.selectFirst("meta[property=og:title], meta[name=twitter:title]")
            ?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
        val h1Title = doc.selectFirst("article h1, main h1, h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
        val docTitle = doc.title().trim().takeIf { it.isNotBlank() }
        val resolvedTitle = ogTitle ?: h1Title ?: docTitle ?: (Uri.parse(sourceUrl).host ?: sourceUrl)

        val siteName = doc.selectFirst("meta[property=og:site_name]")
            ?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: Uri.parse(sourceUrl).host?.removePrefix("www.")

        val byline = doc.selectFirst("meta[name=author], meta[property=article:author]")
            ?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("[rel=author], .author-name, .byline, [itemprop=author]")
                ?.text()?.trim()?.takeIf { it.isNotBlank() && it.length <= 80 }

        val publishedRaw = doc.selectFirst("meta[property=article:published_time], meta[name=pubdate], time[datetime]")
            ?.let { el ->
                if (el.tagName().equals("meta", ignoreCase = true)) el.attr("content") else el.attr("datetime")
            }?.trim()?.takeIf { it.isNotBlank() }
            ?.let { raw -> formatShortDate(raw) }

        val metaImage = doc.selectFirst("meta[property=og:image], meta[name=twitter:image]")
            ?.let { el ->
                el.absUrl("content").takeIf { it.startsWith("http") }
                    ?: el.attr("content").trim().takeIf { it.startsWith("http") }
            }

        // Remove scripts, styles, navs, ads, Google Translate overlays before selecting body blocks
        doc.select(noiseSelector).remove()

        val rootContainer = pickBestContainer(doc.body()) ?: doc.body()
        val bodyImage = rootContainer?.select("img[src]")
            ?.firstOrNull { img ->
                val src = img.absUrl("src").ifBlank { img.attr("src") }
                src.startsWith("http") &&
                    !src.contains("avatar", ignoreCase = true) &&
                    !src.contains("icon", ignoreCase = true) &&
                    !src.contains("logo", ignoreCase = true)
            }?.let { img ->
                img.absUrl("src").ifBlank { img.attr("src") }
            }

        val rawLeadImage = metaImage ?: bodyImage
        val cleanLeadImage = rawLeadImage?.let { UrlRewriter.toDirectTelegramUrl(it) }

        val blocks = extractBlocks(rootContainer, resolvedTitle)
        val totalWords = blocks.sumOf { block ->
            val t = when (block) {
                is ArticleBlock.Heading -> block.text
                is ArticleBlock.Paragraph -> block.text
                is ArticleBlock.Quote -> block.text
                is ArticleBlock.CodeBlock -> block.text
            }
            t.split(Regex("\\s+")).count { it.isNotBlank() }
        }
        val readMinutes = max(1, (totalWords + 179) / 180)

        return ExtractedArticle(
            url = sourceUrl,
            title = resolvedTitle,
            byline = byline,
            siteName = siteName,
            publishedTime = publishedRaw,
            leadImageUrl = cleanLeadImage,
            blocks = blocks,
            estimatedReadMinutes = readMinutes
        )
    }

    private fun pickBestContainer(body: Element?): Element? {
        if (body == null) return null
        var bestElement: Element? = null
        var bestScore = 0

        for (selector in candidateSelectors) {
            val candidates = body.select(selector)
            for (el in candidates) {
                val pTextLen = el.select("p").sumOf { p ->
                    val len = p.text().trim().length
                    if (len >= 25) len else 0
                }
                val bonus = if (el.tagName().equals("article", ignoreCase = true)) 250 else 0
                val linkLen = el.select("a").sumOf { it.text().trim().length }
                val score = pTextLen + bonus - (linkLen / 3)
                if (score > bestScore && pTextLen >= 80) {
                    bestScore = score
                    bestElement = el
                }
            }
        }
        return bestElement ?: body
    }

    private fun extractBlocks(container: Element?, articleTitle: String): List<ArticleBlock> {
        if (container == null) return emptyList()
        val blocks = mutableListOf<ArticleBlock>()
        val seen = HashSet<String>()

        fun addUnique(keyText: String, block: ArticleBlock) {
            val normalized = keyText.trim()
            if (normalized.isEmpty()) return
            if (seen.add(normalized)) {
                blocks.add(block)
            }
        }

        val elements = container.select("h1, h2, h3, h4, p, blockquote, pre, li")
        for (el in elements) {
            // Avoid double-extracting a <p> inside <blockquote> or <li> inside nested handled elements
            if (el.tagName().equals("p", ignoreCase = true) && el.parents().any { it.tagName() == "blockquote" || it.tagName() == "li" }) {
                continue
            }

            val tag = el.tagName().lowercase(Locale.US)
            val text = cleanText(el.text())
            if (text.isBlank()) continue

            when (tag) {
                "h1" -> {
                    if (!text.equals(articleTitle, ignoreCase = true) && text.length >= 4) {
                        addUnique(text, ArticleBlock.Heading(text, 1))
                    }
                }
                "h2" -> if (text.length >= 3) addUnique(text, ArticleBlock.Heading(text, 2))
                "h3", "h4" -> if (text.length >= 3) addUnique(text, ArticleBlock.Heading(text, 3))
                "blockquote" -> if (text.length >= 15) addUnique(text, ArticleBlock.Quote(text))
                "pre" -> {
                    val rawPre = el.wholeText().trim()
                    if (rawPre.length >= 8) {
                        addUnique(rawPre, ArticleBlock.CodeBlock(rawPre))
                    }
                }
                "li" -> {
                    val parentTag = el.parent()?.tagName()?.lowercase(Locale.US)
                    if ((parentTag == "ul" || parentTag == "ol") && text.length >= 15 && el.select("a").size <= 2) {
                        addUnique("• $text", ArticleBlock.Paragraph("• $text"))
                    }
                }
                else -> {
                    // <p>
                    if (text.length >= 20) {
                        addUnique(text, ArticleBlock.Paragraph(text))
                    }
                }
            }
        }
        return blocks
    }

    private fun cleanText(raw: String): String {
        return raw.replace('\u00A0', ' ')
            .replace(Regex("[ \\t\\r\\n]+"), " ")
            .trim()
    }

    private fun formatShortDate(iso: String): String {
        val datePart = iso.substringBefore("T").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        return datePart ?: iso.take(16)
    }
}
