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
}
