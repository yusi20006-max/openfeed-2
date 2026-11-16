package com.example

import com.example.data.model.BridgeLangProfile
import com.example.data.model.MediaType
import com.example.data.network.TelegramHtmlParser
import com.example.data.network.UrlRewriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenFeedParserTest {

    @Test
    fun urlRewriter_normalizesHandlesAndLinks() {
        assertEquals("durov", UrlRewriter.normalizeUsername("@durov"))
        assertEquals("durov", UrlRewriter.normalizeUsername("https://t.me/s/durov/123"))
        assertEquals("v2rayng_org", UrlRewriter.normalizeUsername("t.me/v2rayng_org"))
        assertEquals("bbcpersian", UrlRewriter.normalizeUsername("https://t-me.translate.goog/s/bbcpersian?_x_tr_sl=auto"))
        assertNull(UrlRewriter.normalizeUsername("ab"))
    }

    @Test
    fun urlRewriter_proxiesAndRestoresTelegramCdnUrls() {
        val direct = "https://cdn4.telesco.pe/file/sample.jpg?size=large"
        val proxied = UrlRewriter.toTranslateProxyUrl(direct, BridgeLangProfile.PRESERVE_ORIGINAL)
        assertTrue(proxied.startsWith("https://cdn4-telesco-pe.translate.goog/file/sample.jpg"))
        assertTrue(proxied.contains("_x_tr_pto=wapp"))

        val restored = UrlRewriter.toDirectTelegramUrl(proxied)
        assertEquals(direct, restored)
    }

    @Test
    fun telegramHtmlParser_parsesChannelAndPostsCleanly() {
        val sampleHtml = """
            <html><body>
              <div class="tgme_channel_info">
                <div class="tgme_channel_info_header">
                  <i class="tgme_page_photo_image"><img src="https://cdn4-telesco-pe.translate.goog/file/avatar.jpg?_x_tr_sl=auto"/></i>
                  <div class="tgme_channel_info_header_title"><span>OpenFeed Test</span><i class="verified-icon"></i></div>
                </div>
                <div class="tgme_channel_info_counter">
                  <span class="counter_value">42.5K</span>
                  <span class="counter_type">subscribers</span>
                </div>
                <div class="tgme_channel_info_description">Standalone feed reader</div>
              </div>
              <div class="tgme_widget_message_wrap">
                <div class="tgme_widget_message" data-post="openfeed_test/105">
                  <a class="tgme_widget_message_photo_wrap" style="background-image:url('https://cdn4-telesco-pe.translate.goog/file/photo1.jpg?_x_tr_sl=auto')"></a>
                  <div class="tgme_widget_message_text">سلام دنیا <span class="google-src-text">Hello world</span><br>Check @telegram #android</div>
                  <div class="tgme_widget_message_document_wrap">
                    <a href="https://cdn4.telesco.pe/file/app.apk">
                      <div class="tgme_widget_message_document_title">openfeed.apk</div>
                      <div class="tgme_widget_message_document_extra">4.2 MB</div>
                    </a>
                  </div>
                  <div class="tgme_widget_message_footer">
                    <span class="tgme_widget_message_views">12.3K</span>
                    <a class="tgme_widget_message_date" href="https://t.me/openfeed_test/105">
                      <time datetime="2026-11-15T10:00:00+00:00">10:00</time>
                    </a>
                  </div>
                </div>
              </div>
              <a class="tme_messages_more" data-before="104" href="/s/openfeed_test?before=104">Load more</a>
            </body></html>
        """.trimIndent()

        val parsed = TelegramHtmlParser.parse(
            html = sampleHtml,
            username = "openfeed_test",
            transportUsed = "Bridge • DoH",
            latencyMs = 110L,
            bridgeProfile = BridgeLangProfile.PRESERVE_ORIGINAL
        )

        assertEquals("OpenFeed Test", parsed.channel.title)
        assertTrue(parsed.channel.isVerified)
        assertEquals("42.5K", parsed.channel.subscribersText)
        assertEquals(104L, parsed.olderBeforeId)
        assertEquals(1, parsed.posts.size)

        val post = parsed.posts.first()
        assertEquals(105L, post.postId)
        assertEquals("12.3K", post.viewsText)
        assertTrue(post.text.contains("سلام دنیا"))
        assertTrue(!post.text.contains("Hello world")) // google-src-text stripped
        assertTrue(post.mentions.contains("@telegram"))
        assertTrue(post.hashtags.contains("#android"))

        val photo = post.media.firstOrNull { it.type == MediaType.PHOTO }
        assertNotNull(photo)
        assertEquals("https://cdn4.telesco.pe/file/photo1.jpg", photo?.url)

        val doc = post.media.firstOrNull { it.type == MediaType.DOCUMENT }
        assertNotNull(doc)
        assertEquals("openfeed.apk", doc?.title)
        assertEquals("4.2 MB", doc?.meta)
    }

    @Test
    fun backupManager_roundTripsJsonAndOpml() {
        val channels = listOf(
            com.example.data.local.ChannelEntity(
                username = "durov",
                title = "Pavel Durov",
                description = "Notes",
                avatarUrl = null,
                subscribersText = "1M",
                isVerified = true,
                isFavorite = true,
                lastFetchedAt = 1000L,
                lastTransportUsed = "Bridge",
                lastLatencyMs = 80L
            )
        )
        val bookmarks = listOf(
            com.example.data.local.PostEntity(
                uniqueKey = "durov_42",
                channelUsername = "durov",
                channelTitle = "Pavel Durov",
                channelAvatarUrl = null,
                channelVerified = true,
                postId = 42L,
                postUrl = "https://t.me/durov/42",
                timestampIso = "2026-11-15T10:00:00+00:00",
                timestampEpoch = 1000L,
                displayTime = "Nov 15",
                viewsText = "100K",
                text = "Saved note",
                linksJson = "[]",
                mentionsJson = "[]",
                hashtagsJson = "[]",
                forwardedFrom = null,
                replyToAuthor = null,
                replyToSnippet = null,
                mediaJson = "[]",
                linkPreviewJson = null,
                pollJson = null,
                isBookmarked = true,
                fetchedAt = 1000L
            )
        )

        val json = com.example.data.backup.BackupManager.exportToJson(channels, bookmarks)
        val importedJson = com.example.data.backup.BackupManager.importFromContent(json)
        assertEquals(1, importedJson.channels.size)
        assertEquals("durov", importedJson.channels.first().username)
        assertTrue(importedJson.channels.first().isFavorite)
        assertEquals(1, importedJson.bookmarkedPosts.size)
        assertEquals("Saved note", importedJson.bookmarkedPosts.first().text)

        val opml = com.example.data.backup.BackupManager.exportToOpml(channels)
        val importedOpml = com.example.data.backup.BackupManager.importFromContent(opml)
        assertEquals(1, importedOpml.channels.size)
        assertEquals("durov", importedOpml.channels.first().username)
    }

    @Test
    fun deepLinkParser_handlesPublicAndTgLinks() {
        val publicIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://t.me/s/v2rayng_org/55")
        )
        val r1 = com.example.data.network.DeepLinkParser.parseIntent(publicIntent)
        assertTrue(r1 is com.example.data.network.DeepLinkResult.OpenChannel)
        val t1 = (r1 as com.example.data.network.DeepLinkResult.OpenChannel).target
        assertEquals("v2rayng_org", t1.username)
        assertEquals(55L, t1.postId)

        val tgIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("tg://resolve?domain=telegram&post=12")
        )
        val r2 = com.example.data.network.DeepLinkParser.parseIntent(tgIntent)
        assertTrue(r2 is com.example.data.network.DeepLinkResult.OpenChannel)
        assertEquals("telegram", (r2 as com.example.data.network.DeepLinkResult.OpenChannel).target.username)

        val inviteIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://t.me/+AbCdEfGhIjK")
        )
        val r3 = com.example.data.network.DeepLinkParser.parseIntent(inviteIntent)
        assertTrue(r3 is com.example.data.network.DeepLinkResult.PrivateInviteUnsupported)
    }

    @Test
    fun starterDirectory_has50PersianTelegramAndAtLeast36RssWithLogos() {
        val tgList = com.example.data.model.StarterChannels.persianTelegramChannels
        val rssList = com.example.data.model.StarterChannels.rssFeeds

        assertEquals(50, tgList.size)
        assertEquals(50, tgList.map { it.username.lowercase() }.distinct().size)
        assertTrue(tgList.all { !it.logoUrl.isNullOrBlank() })

        assertTrue(rssList.size >= 36)
        assertEquals(rssList.size, rssList.map { it.username.lowercase() }.distinct().size)
        assertTrue(rssList.all { !it.feedUrl.isNullOrBlank() && !it.logoUrl.isNullOrBlank() })
    }

    @Test
    fun urlRewriter_distinguishesRssUrlsFromTelegram() {
        assertTrue(UrlRewriter.isRssFeedUrl("https://www.zoomit.ir/feed/"))
        assertTrue(UrlRewriter.isRssFeedUrl("https://feeds.bbci.co.uk/persian/rss.xml"))
        assertNull(UrlRewriter.normalizeUsername("https://www.zoomit.ir/feed/"))
        assertEquals("thezoomit", UrlRewriter.normalizeUsername("https://t.me/s/thezoomit"))
    }

    @Test
    fun rssFeedParser_parsesRss20AndAtomFeeds() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Zoomit Tech</title>
                <link>https://www.zoomit.ir</link>
                <description>Persian Tech News</description>
                <image>
                  <url>https://www.zoomit.ir/static/logo.png</url>
                </image>
                <item>
                  <title>Android 16 Released</title>
                  <link>https://www.zoomit.ir/mobile/123</link>
                  <guid>zoomit-123</guid>
                  <pubDate>Fri, 15 Nov 2026 10:00:00 +0000</pubDate>
                  <description><![CDATA[<p>New Jetpack Compose updates.</p><img src="https://www.zoomit.ir/img/123.jpg"/>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsedRss = com.example.data.network.RssFeedParser.parse(
            xml = rssXml,
            sourceKey = "rss_zoomit_1",
            feedUrl = "https://www.zoomit.ir/feed/",
            transportUsed = "RSS • DoH",
            latencyMs = 95L
        )
        assertEquals("Zoomit Tech", parsedRss.channel.title)
        assertEquals("https://www.zoomit.ir/static/logo.png", parsedRss.channel.avatarUrl)
        assertEquals(com.example.data.model.FeedSourceType.RSS, parsedRss.channel.sourceType)
        assertEquals(1, parsedRss.posts.size)
        val item = parsedRss.posts.first()
        assertTrue(item.text.contains("Android 16 Released"))
        assertTrue(item.text.contains("New Jetpack Compose updates."))
        assertEquals("https://www.zoomit.ir/img/123.jpg", item.media.firstOrNull()?.url)

        val atomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Android Developers</title>
              <subtitle>Official Android Blog</subtitle>
              <icon>https://developer.android.com/favicon.ico</icon>
              <link rel="alternate" href="https://android-developers.googleblog.com"/>
              <entry>
                <id>tag:blogger.com,2026:post-1</id>
                <title>Kotlin Multiplatform</title>
                <published>2026-11-15T12:00:00Z</published>
                <link rel="alternate" href="https://android-developers.googleblog.com/kmp"/>
                <summary>Shared business logic in Kotlin.</summary>
              </entry>
            </feed>
        """.trimIndent()

        val parsedAtom = com.example.data.network.RssFeedParser.parse(
            xml = atomXml,
            sourceKey = "rss_android_1",
            feedUrl = "https://android-developers.googleblog.com/atom.xml",
            transportUsed = "RSS",
            latencyMs = 70L
        )
        assertEquals("Android Developers", parsedAtom.channel.title)
        assertEquals("https://developer.android.com/favicon.ico", parsedAtom.channel.avatarUrl)
        assertEquals(1, parsedAtom.posts.size)
        assertEquals("https://android-developers.googleblog.com/kmp", parsedAtom.posts.first().postUrl)
    }

    @Test
    fun backupManager_roundTripsRssFeedsInJsonAndOpml() {
        val rssEntity = com.example.data.local.ChannelEntity(
            username = "rss_zoomit_ir_abcd",
            title = "Zoomit RSS",
            description = "Tech",
            avatarUrl = "https://www.zoomit.ir/logo.png",
            subscribersText = "zoomit.ir",
            isVerified = false,
            isFavorite = true,
            sourceType = com.example.data.model.FeedSourceType.RSS.name,
            feedUrl = "https://www.zoomit.ir/feed/",
            lastFetchedAt = 1000L,
            lastTransportUsed = "RSS",
            lastLatencyMs = 50L
        )

        val json = com.example.data.backup.BackupManager.exportToJson(listOf(rssEntity), emptyList())
        val importedJson = com.example.data.backup.BackupManager.importFromContent(json)
        assertEquals(1, importedJson.channels.size)
        assertEquals(com.example.data.model.FeedSourceType.RSS, importedJson.channels.first().sourceType)
        assertEquals("https://www.zoomit.ir/feed/", importedJson.channels.first().feedUrl)

        val opml = com.example.data.backup.BackupManager.exportToOpml(listOf(rssEntity))
        val importedOpml = com.example.data.backup.BackupManager.importFromContent(opml)
        assertEquals(1, importedOpml.channels.size)
        assertEquals(com.example.data.model.FeedSourceType.RSS, importedOpml.channels.first().sourceType)
        assertEquals("https://www.zoomit.ir/feed/", importedOpml.channels.first().feedUrl)
    }

    @Test
    fun rssDiscovery_extractsFeedUrlFromHtmlAndMirrorRewritesRssUrl() {
        val homepageHtml = """
            <!doctype html>
            <html>
              <head>
                <title>Example Tech Blog</title>
                <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed/articles.xml" />
              </head>
              <body><p>Hello world</p></body>
            </html>
        """.trimIndent()

        assertTrue(com.example.data.network.RssFeedParser.looksLikeHtmlPage(homepageHtml))
        val discovered = com.example.data.network.RssFeedParser.discoverFeedUrlFromHtml(
            html = homepageHtml,
            baseUrl = "https://example.org/"
        )
        assertEquals("https://example.org/feed/articles.xml", discovered)

        val mirrorRss = UrlRewriter.buildCustomMirrorRssUrl(
            mirrorBase = "https://my-worker.workers.dev",
            feedUrl = "https://www.zoomit.ir/feed/"
        )
        assertTrue(mirrorRss?.startsWith("https://my-worker.workers.dev/?url=") == true)
    }

    @Test
    fun deepLinkParser_handlesRssNotificationExtras() {
        val rssKey = com.example.data.model.StarterChannels.buildRssSourceKey("https://www.zoomit.ir/feed/")
        val notifIntent = android.content.Intent("android.intent.action.MAIN").apply {
            putExtra(com.example.data.network.DeepLinkParser.EXTRA_CHANNEL_USERNAME, rssKey)
        }
        val result = com.example.data.network.DeepLinkParser.parseIntent(notifIntent)
        assertTrue(result is com.example.data.network.DeepLinkResult.OpenChannel)
        assertEquals(rssKey, (result as com.example.data.network.DeepLinkResult.OpenChannel).target.username)
    }

    @Test
    fun articleReaderParser_extractsCleanContentAndStripsNoise() {
        val sampleHtml = """
            <!doctype html>
            <html>
              <head>
                <title>Site Chrome Title</title>
                <meta property="og:title" content="گزارش کامل فناوری جدید" />
                <meta property="og:site_name" content="Zoomit" />
                <meta property="og:image" content="https://www.zoomit.ir/hero.jpg" />
                <meta name="author" content="سارا محمدی" />
                <meta property="article:published_time" content="2026-11-15T14:30:00Z" />
              </head>
              <body>
                <nav><a href="/home">خانه</a><a href="/about">درباره ما</a></nav>
                <article>
                  <h1>گزارش کامل فناوری جدید</h1>
                  <p>این مقاله به بررسی معماری مدرن اپلیکیشن‌های اندروید با جت‌پک کامپوز و بهینه‌سازی مصرف باتری می‌پردازد.</p>
                  <h2>جزئیات پیاده‌سازی</h2>
                  <p>استفاده از حالت مطالعه داخلی به کاربران اجازه می‌دهد بدون تبلیغات مزاحم، متن کامل خبر را با فونت مناسب مطالعه کنند.</p>
                  <blockquote>حالت مطالعه تمیز سرعت خواندن مقالات طولانی را به‌شکل محسوسی افزایش می‌دهد.</blockquote>
                  <pre>val theme = OpenFeedTheme(dark = true)</pre>
                </article>
                <footer>تمامی حقوق محفوظ است و کپی برداری ممنوع می باشد.</footer>
              </body>
            </html>
        """.trimIndent()

        val article = com.example.data.network.ArticleReaderParser.parse(
            html = sampleHtml,
            sourceUrl = "https://www.zoomit.ir/tech/123"
        )

        assertEquals("گزارش کامل فناوری جدید", article.title)
        assertEquals("Zoomit", article.siteName)
        assertEquals("سارا محمدی", article.byline)
        assertEquals("2026-11-15", article.publishedTime)
        assertEquals("https://www.zoomit.ir/hero.jpg", article.leadImageUrl)
        assertTrue(article.blocks.any { it is com.example.data.network.ArticleBlock.Heading && it.text == "جزئیات پیاده‌سازی" })
        assertTrue(article.blocks.any { it is com.example.data.network.ArticleBlock.Paragraph && it.text.contains("جت‌پک کامپوز") })
        assertTrue(article.blocks.any { it is com.example.data.network.ArticleBlock.Quote })
        assertTrue(article.blocks.any { it is com.example.data.network.ArticleBlock.CodeBlock })
        assertTrue(article.blocks.none {
            when (it) {
                is com.example.data.network.ArticleBlock.Paragraph -> it.text.contains("تمامی حقوق محفوظ")
                else -> false
            }
        })
        assertTrue(article.estimatedReadMinutes >= 1)
    }

    @Test
    fun offlineMediaVault_excludesVideosAndCanonicalizesBridgeUrls() {
        val post = com.example.data.model.FeedPost(
            uniqueKey = "test_1",
            channelUsername = "test_ch",
            channelTitle = "Test Channel",
            channelAvatarUrl = "https://cdn4-telesco-pe.translate.goog/file/avatar.jpg?_x_tr_sl=auto",
            channelVerified = false,
            postId = 1L,
            postUrl = "https://t.me/test_ch/1",
            timestampIso = "2026-11-15T12:00:00Z",
            timestampEpoch = 1000L,
            displayTime = "12:00",
            viewsText = null,
            text = "News with photo and video",
            links = emptyList(),
            mentions = emptyList(),
            hashtags = emptyList(),
            forwardedFrom = null,
            replyToAuthor = null,
            replyToSnippet = null,
            media = listOf(
                com.example.data.model.MediaAttachment(
                    type = MediaType.PHOTO,
                    url = "https://cdn4.telesco.pe/file/photo_a.jpg",
                    bridgeUrl = "https://cdn4-telesco-pe.translate.goog/file/photo_a.jpg?_x_tr_pto=wapp"
                ),
                com.example.data.model.MediaAttachment(
                    type = MediaType.VIDEO,
                    url = "https://cdn4.telesco.pe/file/clip.mp4",
                    bridgeUrl = "https://cdn4-telesco-pe.translate.goog/file/clip.mp4",
                    thumbnailUrl = "https://cdn4.telesco.pe/file/video_thumb.jpg"
                ),
                com.example.data.model.MediaAttachment(
                    type = MediaType.GIF,
                    url = "https://cdn4.telesco.pe/file/anim.mp4",
                    bridgeUrl = ""
                ),
                com.example.data.model.MediaAttachment(
                    type = MediaType.AUDIO,
                    url = "https://cdn4.telesco.pe/file/song.mp3",
                    bridgeUrl = ""
                ),
                com.example.data.model.MediaAttachment(
                    type = MediaType.DOCUMENT,
                    url = "https://cdn4.telesco.pe/file/doc.pdf",
                    bridgeUrl = ""
                )
            ),
            linkPreview = com.example.data.model.LinkPreview(
                title = "Article",
                url = "https://example.org/news/1",
                imageUrl = "https://example.org/cover.jpg"
            ),
            poll = null,
            isBookmarked = false,
            fetchedAt = 1000L
        )

        val candidates = com.example.data.local.OfflineMediaVault.extractPhotoCandidates(listOf(post))
        val urls = candidates.map { com.example.data.local.OfflineMediaVault.canonicalUrl(it.primaryUrl) }

        assertEquals(3, urls.size)
        assertTrue(urls.contains("https://cdn4.telesco.pe/file/avatar.jpg"))
        assertTrue(urls.contains("https://cdn4.telesco.pe/file/photo_a.jpg"))
        assertTrue(urls.contains("https://example.org/cover.jpg"))
        assertTrue(urls.none { it.contains("clip.mp4") || it.contains("anim.mp4") || it.contains("song.mp3") || it.contains("doc.pdf") || it.contains("video_thumb") })
    }

    @Test
    fun offlineMediaVault_preservesPinnedFilesOnTrimAndClear() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = com.example.data.local.OfflineMediaVault(context)
        val vaultDir = java.io.File(context.filesDir, "offline_photos").apply { mkdirs() }
        vaultDir.listFiles()?.forEach { it.delete() }

        val pinnedUrl = "https://cdn4.telesco.pe/file/pinned.jpg"
        val oldUrl = "https://cdn4.telesco.pe/file/old.jpg"
        val newUrl = "https://cdn4.telesco.pe/file/new.jpg"

        val pinnedFile = java.io.File(vaultDir, "${com.example.data.local.OfflineMediaVault.sha256Hex(pinnedUrl)}.img")
        val oldFile = java.io.File(vaultDir, "${com.example.data.local.OfflineMediaVault.sha256Hex(oldUrl)}.img")
        val newFile = java.io.File(vaultDir, "${com.example.data.local.OfflineMediaVault.sha256Hex(newUrl)}.img")

        pinnedFile.writeBytes(ByteArray(400) { 1 })
        oldFile.writeBytes(ByteArray(400) { 2 })
        newFile.writeBytes(ByteArray(400) { 3 })
        oldFile.setLastModified(1000L)
        newFile.setLastModified(5000L)
        pinnedFile.setLastModified(500L)

        // Trim to 850 bytes: oldFile (400B, oldest non-pinned) should be evicted while pinned + new remain
        vault.trimToMaxBytes(maxBytes = 850L, pinnedUrls = setOf(pinnedUrl))
        assertTrue(pinnedFile.exists())
        assertTrue(!oldFile.exists())
        assertTrue(newFile.exists())

        // Resolve via Translate Bridge variant of pinnedUrl -> resolves to same local file
        val resolved = vault.resolveLocalFile("https://cdn4-telesco-pe.translate.goog/file/pinned.jpg?_x_tr_pto=wapp")
        assertNotNull(resolved)
        assertEquals(pinnedFile.absolutePath, resolved?.absolutePath)

        // Clear non-pinned: newFile deleted, pinnedFile remains
        vault.clearNonPinned(setOf(pinnedUrl))
        assertTrue(pinnedFile.exists())
        assertTrue(!newFile.exists())
    }

    @Test
    fun mediaSerializer_roundTripsArticleBlocks() {
        val blocks = listOf(
            com.example.data.network.ArticleBlock.Heading("Section 1", 2),
            com.example.data.network.ArticleBlock.Paragraph("متن آزمایشی فارسی برای ذخیره آفلاین."),
            com.example.data.network.ArticleBlock.Quote("نقل‌قول مهم"),
            com.example.data.network.ArticleBlock.CodeBlock("val ok = true")
        )
        val encoded = com.example.data.local.MediaSerializer.encodeArticleBlocks(blocks)
        val decoded = com.example.data.local.MediaSerializer.decodeArticleBlocks(encoded)
        assertEquals(blocks, decoded)
    }
}
