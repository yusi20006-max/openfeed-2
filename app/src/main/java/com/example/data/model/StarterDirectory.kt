package com.example.data.model

import android.net.Uri

object StarterChannels {

    fun telegramAvatarFallback(username: String): String =
        "https://t.me/i/userpic/320/$username.jpg"

    fun rssLogoFallback(feedUrl: String): String {
        val host = runCatching { Uri.parse(feedUrl).host }.getOrNull().orEmpty()
            .removePrefix("www.")
            .removePrefix("feeds.")
            .removePrefix("feed.")
        return if (host.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        } else {
            ""
        }
    }

    fun buildRssSourceKey(feedUrl: String): String {
        val uri = runCatching { Uri.parse(feedUrl.trim()) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.") ?: "feed"
        val slug = host.replace(Regex("[^a-z0-9]"), "_")
        val hash = (feedUrl.trim().hashCode().toUInt()).toString(16)
        return "rss_${slug}_$hash"
    }

    /**
     * 50 popular public Persian Telegram channels across News, Tech/AI, Programming,
     * Finance/Crypto, Sports, Culture/Books/Podcasts, and Entertainment.
     */
    val persianTelegramChannels: List<SuggestedChannel> = listOf(
        // News & Journalism (19)
        tg("BBCPersian", "BBC News فارسی", "اخبار", "پوشش فوری اخبار ایران و جهان از بی‌بی‌سی فارسی"),
        tg("iranintltv", "ایران اینترنشنال", "اخبار", "پخش زنده و اخبار لحظه‌ای ایران و جهان"),
        tg("radiofarda", "رادیو فردا", "اخبار", "اخبار، تحلیل و گزارش‌های روز ایران و دنیا"),
        tg("farsivoa", "صدای آمریکا فارسی (VOA)", "اخبار", "خبرها و گزارش‌های بخش فارسی صدای آمریکا"),
        tg("dw_persian", "دویچه وله فارسی", "اخبار", "اخبار و تحلیل‌های بخش فارسی رسانه دویچه‌وله"),
        tg("euronews_pe", "یورونیوز فارسی", "اخبار", "تازه‌ترین خبرهای اروپا، ایران و جهان"),
        tg("VahidOnline", "وحید آنلاین", "اخبار", "بازتاب اخبار شبکه‌های اجتماعی و مستندات آنلاین"),
        tg("khabarfouri", "خبر فوری", "اخبار", "یکی از پرمخاطب‌ترین کانال‌های اخبار لحظه‌ای ایران"),
        tg("akharinkhabar", "آخرین خبر", "اخبار", "پوشش سریع اخبار سیاسی، اجتماعی، اقتصادی و ورزشی"),
        tg("khabaronline_ir", "خبرآنلاین", "اخبار", "پایگاه خبری تحلیلی خبرآنلاین"),
        tg("isna94", "خبرگزاری ایسنا", "اخبار", "اخبار رسمی خبرگزاری دانشجویان ایران"),
        tg("asriran", "عصر ایران", "اخبار", "تازه‌ترین خبرها و گزارش‌های تحلیلی عصر ایران"),
        tg("fararu", "فرارو", "اخبار", "پوشش خبرها و یادداشت‌های تحلیلی روز"),
        tg("Factnameh", "فکت‌نامه", "اخبار", "راستی‌آزمایی ادعاها و اخبار منتشرشده درباره ایران"),
        tg("entekhab_ir", "انتخاب", "اخبار", "پایگاه خبری تحلیلی انتخاب"),
        tg("etemadonline", "اعتمادآنلاین", "اخبار", "اخبار و گزارش‌های روزنامه اعتماد"),
        tg("sharghdaily", "روزنامه شرق", "اخبار", "مطالب و سرمقاله‌های روزنامه شرق"),
        tg("hamshahrionline", "همشهری آنلاین", "اخبار", "اخبار شهری، اجتماعی و فرهنگی همشهری"),
        tg("tasnimnews", "خبرگزاری تسنیم", "اخبار", "پوشش اخبار داخلی و بین‌المللی تسنیم"),

        // Tech & AI (9)
        tg("thezoomit", "زومیت", "فناوری و AI", "بررسی گجت‌ها، موبایل، هوش مصنوعی و اخبار تکنولوژی"),
        tg("Digiato", "دیجیاتو", "فناوری و AI", "جدیدترین اخبار فناوری، استارتاپ‌ها و علم"),
        tg("gadgetnews", "گجت نیوز", "فناوری و AI", "تازه‌های موبایل، خودرو، علم و فناوری"),
        tg("myzoomg", "زومجی", "فناوری و AI", "اخبار بازی‌های ویدیویی، سینما و سرگرمی دیجیتال"),
        tg("peivast", "ماهنامه پیوست", "فناوری و AI", "اقتصاد دیجیتال، فناوری اطلاعات و استارتاپ‌های ایران"),
        tg("shahrsakhtafzar", "شهر سخت‌افزار", "فناوری و AI", "نقد و بررسی سخت‌افزار، پردازنده و کامپیوتر"),
        tg("jadi_net", "جادی (رادیو گیک)", "فناوری و AI", "مطالب فنی، لینوکس، متن‌باز و دنیای گیک"),
        tg("v2rayng_org", "v2rayNG", "فناوری و AI", "اطلاعیه‌ها و نسخه‌های جدید کلاینت متن‌باز اندروید"),
        tg("cafebazaar", "کافه‌بازار", "فناوری و AI", "معرفی اپلیکیشن‌ها، بازی‌ها و تازه‌های اندروید"),

        // Programming & Education (7)
        tg("SabzLearn", "سبزلرن", "برنامه‌نویسی", "آموزش‌های پروژه‌محور وب، فرانت‌اند و پایتون"),
        tg("sokanacademy", "سکان آکادمی", "برنامه‌نویسی", "مقالات، آموزش‌ها و اخبار دنیای برنامه‌نویسی"),
        tg("roocket", "راکت", "برنامه‌نویسی", "تازه‌های توسعه وب، جاوااسکریپت و طراحی نرم‌افزار"),
        tg("quera_ir", "کوئرا", "برنامه‌نویسی", "مسابقات برنامه‌نویسی، الگوریتم و فرصت‌های شغلی"),
        tg("faradars", "فرادرس", "برنامه‌نویسی", "منابع آموزشی دانشگاهی، هوش مصنوعی و مهندسی"),
        tg("maktabkhooneh", "مکتب‌خونه", "برنامه‌نویسی", "دوره‌ها و مطالب علمی و علم داده"),
        tg("PythonIRI", "پایتون ایران", "برنامه‌نویسی", "ترفندها، کتابخانه‌ها و آموزش‌های پایتون"),

        // Finance & Crypto (4)
        tg("arzdigital", "ارزدیجیتال", "اقتصاد و کریپتو", "اخبار لحظه‌ای بیت‌کوین، رمزارزها و تحلیل بازار"),
        tg("mihanblockchain", "میهن بلاکچین", "اقتصاد و کریپتو", "پوشش اخبار کریپتو، وب۳ و آموزش بلاکچین"),
        tg("bourse24ir", "بورس ۲۴", "اقتصاد و کریپتو", "اخبار بازار سرمایه، بورس، طلا و ارز"),
        tg("tejaratnews", "تجارت‌نیوز", "اقتصاد و کریپتو", "گزارش‌های اقتصادی، بازار مسکن، خودرو و مالی"),

        // Sports (5)
        tg("varzesh3", "ورزش سه", "ورزش", "اخبار لحظه‌ای فوتبال ایران و جهان، نتایج و ویدیوها"),
        tg("tarafdari", "طرفداری", "ورزش", "اخبار، تحلیل‌ها و گزارش‌های تخصصی فوتبال"),
        tg("ft360_ir", "فوتبال ۳۶۰", "ورزش", "پوشش مسابقات، آمار و برنامه‌های فوتبال ۳۶۰"),
        tg("perspolis", "باشگاه پرسپولیس", "ورزش", "اخبار رسمی و حواشی باشگاه پرسپولیس"),
        tg("esteghlal", "باشگاه استقلال", "ورزش", "اخبار رسمی و گزارش‌های باشگاه استقلال"),

        // Books, Podcasts & Entertainment (6)
        tg("podcastbplus", "پادکست بی‌پلاس", "فرهنگ و پادکست", "خلاصه کتاب‌های غیرداستانی و یادداشت‌های شنیدنی"),
        tg("channelbpodcast", "پادکست چنل‌بی", "فرهنگ و پادکست", "روایت ماجراهای واقعی و پادکست‌های مستند"),
        tg("taaghche_books", "طاقچه", "فرهنگ و پادکست", "معرفی کتاب، بریده‌های خواندنی و کتاب‌های صوتی"),
        tg("fidibook", "فیدیبو", "فرهنگ و پادکست", "تازه‌های نشر، کتاب الکترونیک و معرفی رمان"),
        tg("gizmiztel", "گیزمیز", "سرگرمی", "مطالب طنز، ویدیوهای وایرال و سرگرمی روز"),
        tg("mamlekate", "مملکته", "سرگرمی", "بازتاب طنزآمیز و اجتماعی توییت‌ها و رویدادهای روز")
    )

    /**
     * 36 curated Persian and global RSS/Atom feeds ready to subscribe in 1 tap.
     */
    val rssFeeds: List<SuggestedChannel> = listOf(
        // Persian Tech & Culture RSS (9)
        rss("https://www.zoomit.ir/feed/", "زومیت (RSS)", "فناوری (FA)", "مقالات و بررسی‌های تخصصی فناوری زومیت"),
        rss("https://digiato.com/feed", "دیجیاتو (RSS)", "فناوری (FA)", "فید رسمی اخبار تکنولوژی و علم دیجیاتو"),
        rss("https://gadgetnews.net/feed/", "گجت نیوز (RSS)", "فناوری (FA)", "اخبار موبایل، تکنولوژی و گجت‌ها"),
        rss("https://www.zoomg.ir/feed/", "زومجی (RSS)", "فناوری (FA)", "تازه‌های بازی، سینما و سرگرمی"),
        rss("https://www.digikala.com/mag/feed/", "دیجی‌کالا مگ", "فناوری (FA)", "بررسی محصول، فناوری و سبک زندگی"),
        rss("https://peivast.com/feed", "ماهنامه پیوست", "فناوری (FA)", "اقتصاد دیجیتال و فناوری اطلاعات"),
        rss("http://feed.shahrsakhtafzar.com/shahrsakhtafzar", "شهر سخت‌افزار", "فناوری (FA)", "تست و بررسی قطعات کامپیوتر و سخت‌افزار"),
        rss("https://www.chetor.com/feed/", "چطور", "آموزش (FA)", "مقالات توسعه فردی، مهارت‌ها و سبک زندگی"),
        rss("https://virgool.io/feed/@virgool", "ویرگول", "فرهنگ (FA)", "نوشته‌ها و یادداشت‌های منتخب نویسندگان فارسی"),

        // Persian News, Economy & Sports RSS (11)
        rss("https://www.khabaronline.ir/rss", "خبرآنلاین (RSS)", "اخبار (FA)", "سرخط خبرهای سیاسی، اقتصادی و اجتماعی"),
        rss("https://www.isna.ir/rss", "ایسنا (RSS)", "اخبار (FA)", "آخرین اخبار خبرگزاری ایسنا"),
        rss("https://www.mehrnews.com/rss", "خبرگزاری مهر (RSS)", "اخبار (FA)", "تازه‌ترین خبرهای ایران و جهان"),
        rss("https://www.tabnak.ir/fa/rss/allnews", "تابناک (RSS)", "اخبار (FA)", "تمام اخبار و تحلیل‌های تابناک"),
        rss("https://www.asriran.com/fa/rss/allnews", "عصر ایران (RSS)", "اخبار (FA)", "خوراک خبری کامل عصر ایران"),
        rss("https://fararu.com/fa/rss/allnews", "فرارو (RSS)", "اخبار (FA)", "آخرین خبرهای سیاسی و اقتصادی فرارو"),
        rss("https://donya-e-eqtesad.com/feeds/", "دنیای اقتصاد", "اقتصاد (FA)", "اخبار بازار، اقتصاد کلان و بورس"),
        rss("https://www.varzesh3.com/rss/all", "ورزش سه (RSS)", "ورزش (FA)", "آخرین اخبار فوتبال و ورزش ایران و جهان"),
        rss("https://feeds.bbci.co.uk/persian/rss.xml", "BBC فارسی (RSS)", "اخبار (FA)", "فید رسمی بخش فارسی بی‌بی‌سی"),
        rss("https://parsi.euronews.com/rss", "یورونیوز فارسی (RSS)", "اخبار (FA)", "تازه‌ترین گزارش‌های یورونیوز به فارسی"),
        rss("https://www.tasnimnews.com/fa/rss/feed/0/8/0/%D9%85%D9%87%D9%85%D8%AA%D8%B1%DB%8C%D9%86-%D8%A7%D8%AE%D8%A8%D8%A7%D8%B1", "تسنیم (RSS)", "اخبار (FA)", "مهم‌ترین اخبار خبرگزاری تسنیم"),

        // Global Tech, Engineering & AI RSS (11)
        rss("https://hnrss.org/frontpage", "Hacker News", "Global Tech", "Top frontpage discussions from Hacker News"),
        rss("https://www.theverge.com/rss/index.xml", "The Verge", "Global Tech", "Technology, science, AI, and culture coverage"),
        rss("https://techcrunch.com/feed/", "TechCrunch", "Global Tech", "Startups, venture capital, and breaking tech news"),
        rss("https://feeds.arstechnica.com/arstechnica/index", "Ars Technica", "Global Tech", "Deep-dive tech, security, and science reporting"),
        rss("https://www.wired.com/feed/rss", "WIRED", "Global Tech", "How technology is changing culture and business"),
        rss("https://android-developers.googleblog.com/atom.xml", "Android Developers", "Engineering", "Official Android, Jetpack Compose & Kotlin updates"),
        rss("https://blog.jetbrains.com/kotlin/feed/", "Kotlin Blog", "Engineering", "Official Kotlin language & ecosystem releases"),
        rss("https://github.blog/feed/", "GitHub Blog", "Engineering", "Open source, developer tools, and engineering"),
        rss("https://9to5google.com/feed/", "9to5Google", "Global Tech", "Android, Pixel, and Google ecosystem news"),
        rss("https://www.xda-developers.com/feed/", "XDA Developers", "Global Tech", "Mobile hardware, Android modding, and computing"),
        rss("https://www.technologyreview.com/feed/", "MIT Tech Review", "Science & AI", "AI breakthroughs, biotech, and future computing"),

        // Global News & Science RSS (5)
        rss("https://openai.com/news/rss.xml", "OpenAI News", "Science & AI", "Research and product announcements from OpenAI"),
        rss("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC World News", "World News", "International breaking news from BBC"),
        rss("https://feeds.npr.org/1001/rss.xml", "NPR News", "World News", "Global news, analysis, and features"),
        rss("https://www.nasa.gov/rss/dyn/breaking_news.rss", "NASA Breaking News", "Science & AI", "Spaceflight, astronomy, and mission updates"),
        rss("https://www.nature.com/nature.rss", "Nature Journal", "Science & AI", "Latest peer-reviewed science news and research")
    )

    val list: List<SuggestedChannel> get() = persianTelegramChannels

    private fun tg(
        username: String,
        title: String,
        category: String,
        description: String
    ) = SuggestedChannel(
        username = username,
        title = title,
        category = category,
        description = description,
        sourceType = FeedSourceType.TELEGRAM,
        feedUrl = "https://t.me/s/$username",
        logoUrl = telegramAvatarFallback(username)
    )

    private fun rss(
        feedUrl: String,
        title: String,
        category: String,
        description: String
    ) = SuggestedChannel(
        username = buildRssSourceKey(feedUrl),
        title = title,
        category = category,
        description = description,
        sourceType = FeedSourceType.RSS,
        feedUrl = feedUrl,
        logoUrl = rssLogoFallback(feedUrl)
    )
}
