/**
 * Sitios conocidos, para orientar al usuario. NO decide qué descargador se usa: el backend manda
 * cualquier URL que no sea .m3u8 a yt-dlp (más de 1.700 sitios). Esta lista es la parte curada:
 * extractores que yt-dlp no marca como rotos y que no usan DRM.
 *
 * Si un sitio deja de funcionar: `pip install -U yt-dlp`; casi siempre es un cambio del sitio.
 */

export type Access = "public" | "login" | "drm";
export type Category = "video" | "social" | "live" | "music" | "talks" | "news" | "asia" | "tv" | "east";

export interface Provider {
  name: string;
  /** Dominio registrable; también cubre subdominios (m., www., tv., ...). */
  domains: string[];
  category: Category;
  /** public = funciona sin cuenta; login = necesita sesión (aún no soportado); drm = imposible. */
  access: Access;
}

export const CATEGORIES: { id: Category; label: string }[] = [
  { id: "video", label: "Plataformas de video" },
  { id: "social", label: "Redes sociales" },
  { id: "live", label: "Directos y gaming" },
  { id: "music", label: "Música" },
  { id: "talks", label: "Charlas y trabajo" },
  { id: "news", label: "Noticias" },
  { id: "tv", label: "Televisión pública (Europa y Latinoamérica)" },
  { id: "asia", label: "Asia" },
  { id: "east", label: "Rusia y Europa del Este" },
];

const p = (name: string, category: Category, domains: string[], access: Access = "public"): Provider => ({
  name,
  domains,
  category,
  access,
});

export const PROVIDERS: Provider[] = [
  p("YouTube", "video", ["youtube.com", "youtu.be", "youtube-nocookie.com"]),
  p("Vimeo", "video", ["vimeo.com"]),
  p("Dailymotion", "video", ["dailymotion.com", "dai.ly"]),
  p("Rumble", "video", ["rumble.com"]),
  p("Odysee", "video", ["odysee.com"]),
  p("PeerTube", "video", []), // cada instancia tiene su dominio
  p("Internet Archive", "video", ["archive.org"]),
  p("Streamable", "video", ["streamable.com"]),
  p("Imgur", "video", ["imgur.com"]),

  p("TikTok", "social", ["tiktok.com"]),
  p("X / Twitter", "social", ["x.com", "twitter.com"]),
  p("Facebook", "social", ["facebook.com", "fb.watch"]),
  p("Reddit", "social", ["reddit.com", "redd.it"]),
  p("Tumblr", "social", ["tumblr.com"]),
  p("Pinterest", "social", ["pinterest.com", "pin.it"]),
  p("Bluesky", "social", ["bsky.app"]),
  p("LinkedIn", "social", ["linkedin.com"]),
  p("9GAG", "social", ["9gag.com"]),
  p("Snapchat Spotlight", "social", ["snapchat.com"]),

  p("Twitch", "live", ["twitch.tv"]),
  p("Kick", "live", ["kick.com"]),
  p("SOOP (AfreecaTV)", "live", ["sooplive.co.kr", "afreecatv.com"]),

  p("SoundCloud", "music", ["soundcloud.com"]),
  p("Bandcamp", "music", ["bandcamp.com"]),
  p("Mixcloud", "music", ["mixcloud.com"]),
  p("Vevo", "music", ["vevo.com"]),

  p("TED", "talks", ["ted.com"]),
  p("Loom", "talks", ["loom.com"]),
  p("Wistia", "talks", ["wistia.com"]),
  p("Vidyard", "talks", ["vidyard.com"]),

  p("CNN", "news", ["cnn.com"]),
  p("BBC", "news", ["bbc.com", "bbc.co.uk"]),
  p("Al Jazeera", "news", ["aljazeera.com"]),
  p("CBS News", "news", ["cbsnews.com"]),
  p("NBC News", "news", ["nbcnews.com"]),
  p("ABC News", "news", ["abcnews.go.com"]),
  p("Fox News", "news", ["foxnews.com"]),

  p("RTVE", "tv", ["rtve.es"]),
  p("ARD Mediathek", "tv", ["ardmediathek.de"]),
  p("ZDF", "tv", ["zdf.de"]),
  p("Arte", "tv", ["arte.tv"]),
  p("Rai", "tv", ["rai.it", "raiplay.it"]),
  p("SVT Play", "tv", ["svtplay.se"]),
  p("NRK", "tv", ["nrk.no"]),
  p("DR", "tv", ["dr.dk"]),
  p("Globo", "tv", ["globo.com"]),
  p("Caracol TV", "tv", ["caracoltv.com"]),
  p("Mediaset / Telecinco", "tv", ["mediaset.it", "telecinco.es"]),

  p("Bilibili", "asia", ["bilibili.com", "b23.tv"]),
  p("Niconico", "asia", ["nicovideo.jp"]),
  p("Youku", "asia", ["youku.com"]),
  p("iQIYI", "asia", ["iq.com", "iqiyi.com"]),
  p("Weibo", "asia", ["weibo.com"]),
  p("Douyin", "asia", ["douyin.com"]),
  p("Xiaohongshu", "asia", ["xiaohongshu.com"]),
  p("Naver TV", "asia", ["naver.com"]),
  p("Kakao TV", "asia", ["kakao.com"]),
  p("NHK", "asia", ["nhk.or.jp"]),

  p("VK", "east", ["vk.com", "vkvideo.ru"]),
  p("OK.ru", "east", ["ok.ru"]),
  p("Rutube", "east", ["rutube.ru"]),

  // Sin DRM, pero piden sesión: haría falta pasar cookies a yt-dlp (no implementado).
  p("Instagram", "social", ["instagram.com"], "login"),
  p("Patreon", "video", ["patreon.com"], "login"),
  p("Nebula", "video", ["nebula.tv"], "login"),
  p("Floatplane", "video", ["floatplane.com"], "login"),
  p("Dropout", "video", ["dropout.tv"], "login"),
  p("Substack", "talks", ["substack.com"], "login"),

  // Protegidos con DRM (Widevine/FairPlay): el proyecto no los evade (ver CLAUDE.md).
  p("Netflix", "video", ["netflix.com"], "drm"),
  p("Disney+", "video", ["disneyplus.com"], "drm"),
  p("Prime Video", "video", ["primevideo.com"], "drm"),
  p("Max", "video", ["max.com", "hbomax.com"], "drm"),
  p("Hulu", "video", ["hulu.com"], "drm"),
  p("Apple TV+", "video", ["tv.apple.com"], "drm"),
  p("Spotify", "music", ["spotify.com"], "drm"),
  p("Crunchyroll", "video", ["crunchyroll.com"], "drm"),
];

/** Proveedor del host: coincide el dominio exacto o un subdominio suyo (no "notyoutube.com"). */
export function findProvider(hostname: string): Provider | undefined {
  const host = hostname.toLowerCase().replace(/\.$/, "");
  return PROVIDERS.find((prov) => prov.domains.some((d) => host === d || host.endsWith(`.${d}`)));
}
