package com.carlospinan.guardavideo.source

/**
 * Known sites, mirrored from the web app's list (frontend/src/lib/providers.ts, the source of
 * truth): what the URL field says about a link, and the in-app "Sitios compatibles" screen.
 */
enum class Access { Public, Login, Drm }

/** Same groups as the web's "Sitios compatibles" card. */
enum class ProviderCategory(val label: String) {
    Video("Plataformas de video"),
    Social("Redes sociales"),
    Live("Directos y gaming"),
    Music("Música"),
    Talks("Charlas y trabajo"),
    News("Noticias"),
    Tv("Televisión pública (Europa y Latinoamérica)"),
    Asia("Asia"),
    East("Rusia y Europa del Este"),
}

data class Provider(val name: String, val domains: List<String>, val access: Access, val category: ProviderCategory)

private fun p(name: String, vararg domains: String, access: Access = Access.Public) =
    Provider(name, domains.toList(), access, ProviderCategory.Video)

/** Puts [providers] in [category] (keeps the list readable, one block per group). */
private fun group(category: ProviderCategory, vararg providers: Provider): List<Provider> =
    providers.map { it.copy(category = category) }

val PROVIDERS: List<Provider> = listOf(
    group(
        ProviderCategory.Video,
        p("YouTube", "youtube.com", "youtu.be", "youtube-nocookie.com"),
        p("Vimeo", "vimeo.com"),
        p("Dailymotion", "dailymotion.com", "dai.ly"),
        p("Rumble", "rumble.com"),
        p("Odysee", "odysee.com"),
        p("Internet Archive", "archive.org"),
        p("Streamable", "streamable.com"),
        p("Imgur", "imgur.com"),
        p("Patreon", "patreon.com", access = Access.Login),
        p("Nebula", "nebula.tv", access = Access.Login),
        p("Floatplane", "floatplane.com", access = Access.Login),
        p("Dropout", "dropout.tv", access = Access.Login),
        p("Netflix", "netflix.com", access = Access.Drm),
        p("Disney+", "disneyplus.com", access = Access.Drm),
        p("Prime Video", "primevideo.com", access = Access.Drm),
        p("Max", "max.com", "hbomax.com", access = Access.Drm),
        p("Hulu", "hulu.com", access = Access.Drm),
        p("Apple TV+", "tv.apple.com", access = Access.Drm),
        p("Crunchyroll", "crunchyroll.com", access = Access.Drm),
    ),
    group(
        ProviderCategory.Social,
        p("TikTok", "tiktok.com"),
        p("X / Twitter", "x.com", "twitter.com"),
        p("Facebook", "facebook.com", "fb.watch"),
        p("Reddit", "reddit.com", "redd.it"),
        p("Tumblr", "tumblr.com"),
        p("Pinterest", "pinterest.com", "pin.it"),
        p("Bluesky", "bsky.app"),
        p("LinkedIn", "linkedin.com"),
        p("9GAG", "9gag.com"),
        p("Snapchat Spotlight", "snapchat.com"),
        p("Instagram", "instagram.com", access = Access.Login),
    ),
    group(
        ProviderCategory.Live,
        p("Twitch", "twitch.tv"),
        p("Kick", "kick.com"),
        p("SOOP (AfreecaTV)", "sooplive.co.kr", "afreecatv.com"),
    ),
    group(
        ProviderCategory.Music,
        p("SoundCloud", "soundcloud.com"),
        p("Bandcamp", "bandcamp.com"),
        p("Mixcloud", "mixcloud.com"),
        p("Vevo", "vevo.com"),
        p("Spotify", "spotify.com", access = Access.Drm),
    ),
    group(
        ProviderCategory.Talks,
        p("TED", "ted.com"),
        p("Loom", "loom.com"),
        p("Wistia", "wistia.com"),
        p("Vidyard", "vidyard.com"),
        p("Substack", "substack.com", access = Access.Login),
    ),
    group(
        ProviderCategory.News,
        p("CNN", "cnn.com"),
        p("BBC", "bbc.com", "bbc.co.uk"),
        p("Al Jazeera", "aljazeera.com"),
        p("CBS News", "cbsnews.com"),
        p("NBC News", "nbcnews.com"),
        p("ABC News", "abcnews.go.com"),
        p("Fox News", "foxnews.com"),
    ),
    group(
        ProviderCategory.Tv,
        p("RTVE", "rtve.es"),
        p("ARD Mediathek", "ardmediathek.de"),
        p("ZDF", "zdf.de"),
        p("Arte", "arte.tv"),
        p("Rai", "rai.it", "raiplay.it"),
        p("SVT Play", "svtplay.se"),
        p("NRK", "nrk.no"),
        p("DR", "dr.dk"),
        p("Globo", "globo.com"),
        p("Caracol TV", "caracoltv.com"),
        p("Mediaset / Telecinco", "mediaset.it", "telecinco.es"),
    ),
    group(
        ProviderCategory.Asia,
        p("Bilibili", "bilibili.com", "b23.tv"),
        p("Niconico", "nicovideo.jp"),
        p("Youku", "youku.com"),
        p("iQIYI", "iq.com", "iqiyi.com"),
        p("Weibo", "weibo.com"),
        p("Douyin", "douyin.com"),
        p("Xiaohongshu", "xiaohongshu.com"),
        p("Naver TV", "naver.com"),
        p("Kakao TV", "kakao.com"),
        p("NHK", "nhk.or.jp"),
    ),
    group(
        ProviderCategory.East,
        p("VK", "vk.com", "vkvideo.ru"),
        p("OK.ru", "ok.ru"),
        p("Rutube", "rutube.ru"),
    ),
).flatten()

/** Exact domain or a subdomain of it ("m.youtube.com" yes, "notyoutube.com" no). */
fun findProvider(hostname: String): Provider? {
    val host = hostname.lowercase().trimEnd('.')
    return PROVIDERS.firstOrNull { prov -> prov.domains.any { host == it || host.endsWith(".$it") } }
}
