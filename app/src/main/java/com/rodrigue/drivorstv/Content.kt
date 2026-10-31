package com.rodrigue.drivorstv

import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Un film ou une série du moment. */
data class Show(
    val id: Int,
    val name: String,
    var platform: String,
    val poster: String?,
    val genres: List<String>,
    val summary: String,
    val weight: Int,
    var backdrop: String? = null,
    val kind: String = "Série",
    val year: String = "",
    val tmdbType: String = ""
)

/** Un article d'actualité. */
data class News(
    val source: String,
    val title: String,
    val link: String,
    val summary: String,
    val image: String?,
    val date: Date?,
    val category: String = ""
)

/** Une vidéo YouTube récente d'une chaîne suivie. */
data class Video(val id: String, val title: String, val channel: String, val date: Long) {
    val thumb get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

object Content {

    /** Chaînes YouTube choisies par Rodrigue (flux publics de YouTube, sans clé). */
    val youtubeChannels = listOf(
        "UCJy0lX8ThZ7lCtst7JnegWQ", // Jojol
        "UC-pdFydi9ooxEfATh3KoiIw", // STEVEN
        "UCgoxc-VKU0cw9BcM9z_qokg", // Frandroid
        "UCPQsSFJurluaMOG1qL0Y7sA", // PP World
        "UCck5J0M6YKnXCrnuXkCFuvw", // Himra officiel
        "UCwWADQnhqYBVFmuzeYmbIlg", // Tobeh
        "UCAz-755tH3m8_BwaluzdJwQ", // Numerama
        "UC46ytp7_ZW9X92vjqHKVuuA", // Faiz Dev
        "UCZ5qxuvquQABNtjcawNMowA", // DaddyFamtech
        "UC1ndVn8sa4oTbW09P5ICOog", // amazing mac TV
        "UCE_M8A5yxnLfW0KghEeajjw"  // Apple
    )

    /** Les vidéos les plus récentes de toutes ces chaînes (sans les Shorts). */
    fun loadYoutube(): List<Video> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val jobs = youtubeChannels.map { id ->
            pool.submit<List<Video>> {
                try { parseYoutube(Net.get("https://www.youtube.com/feeds/videos.xml?channel_id=$id", 3_000_000)) }
                catch (_: Exception) { emptyList() }
            }
        }
        val all = jobs.flatMap { try { it.get() } catch (_: Exception) { emptyList() } }
        pool.shutdown()
        return all.distinctBy { it.id }.sortedByDescending { it.date }.take(30)
    }

    private fun parseYoutube(bytes: ByteArray): List<Video> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(bytes.inputStream(), null)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val out = mutableListOf<Video>()
        var inEntry = false
        var id = ""; var title = ""; var channel = ""; var link = ""; var date = 0L
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "entry" -> { inEntry = true; id = ""; title = ""; channel = ""; link = ""; date = 0L }
                    "videoId" -> if (inEntry) id = p.nextText()
                    "title" -> if (inEntry && title.isEmpty()) title = p.nextText()
                    "name" -> if (inEntry) channel = p.nextText()
                    "link" -> if (inEntry) link = p.getAttributeValue(null, "href") ?: ""
                    "published" -> if (inEntry) date = try { iso.parse(p.nextText().take(19))?.time ?: 0L } catch (_: Exception) { 0L }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "entry") {
                inEntry = false
                if (id.isNotEmpty() && !link.contains("/shorts/")) out += Video(id, title, channel, date)
            }
            ev = p.next()
        }
        return out
    }

    fun videosToJson(l: List<Video>): String = JSONArray().apply {
        for (v in l) put(JSONObject().put("id", v.id).put("title", v.title).put("channel", v.channel).put("date", v.date))
    }.toString()

    fun videosFromJson(t: String): List<Video> = try {
        val a = JSONArray(t)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Video(o.optString("id"), o.optString("title"), o.optString("channel"), o.optLong("date"))
        }.filter { it.id.isNotEmpty() }
    } catch (_: Exception) { emptyList() }


    /** Plateformes reconnues, et l'appli Android TV qui va avec. */
    val platformApps = linkedMapOf(
        "Netflix" to listOf("com.netflix.ninja"),
        "Prime Video" to listOf("com.amazon.amazonvideo.livingroom"),
        "Disney+" to listOf("com.disney.disneyplus"),
        "Apple TV+" to listOf("com.apple.atve.androidtv.appletv"),
        "HBO Max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "Max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "Paramount+" to listOf("com.cbs.ott"),
        "Hulu" to listOf("com.hulu.livingroomplus"),
        "Peacock" to listOf("com.peacocktv.peacockandroid"),
        "Canal+" to listOf("com.canal.android.canal"),
        "Crunchyroll" to listOf("com.crunchyroll.crunchyroid")
    )

    /** Nom de plateforme TMDB -> nom affiché. */
    private fun platformName(p: String): String? {
        val n = p.lowercase()
        return when {
            "netflix" in n -> "Netflix"
            "prime video" in n || "amazon video" in n -> "Prime Video"
            "disney" in n -> "Disney+"
            "apple tv" in n -> "Apple TV+"
            "canal" in n -> "Canal+"
            "crunchyroll" in n -> "Crunchyroll"
            n == "max" || "hbo" in n -> "HBO Max"
            "paramount" in n -> "Paramount+"
            else -> null
        }
    }

    /** Films et séries du moment : TMDB en français si une clé est fournie, sinon TVmaze. */
    fun loadShows(tmdbKey: String): List<Show> {
        if (tmdbKey.isNotBlank()) {
            try {
                val l = loadTmdb(tmdbKey.trim())
                if (l.isNotEmpty()) return l
            } catch (_: Exception) {}
        }
        return loadTvmaze()
    }

    // ------------------------------------------------------------ TMDB (français)

    private fun loadTmdb(key: String): List<Show> {
        val bearer = key.length > 40 // jeton « API Read Access Token » ou simple clé API
        fun call(path: String): JSONObject {
            val sep = if ('?' in path) '&' else '?'
            val url = "https://api.themoviedb.org/3/$path" + (if (bearer) "" else "${sep}api_key=$key")
            val h = if (bearer) mapOf("Authorization" to "Bearer $key", "accept" to "application/json") else emptyMap()
            return JSONObject(String(Net.get(url, headers = h)))
        }

        val genres = HashMap<Int, String>()
        for (t in listOf("movie", "tv")) {
            try {
                val a = call("genre/$t/list?language=fr-FR").optJSONArray("genres") ?: continue
                for (i in 0 until a.length()) genres[a.getJSONObject(i).optInt("id")] = a.getJSONObject(i).optString("name")
            } catch (_: Exception) {}
        }

        val found = LinkedHashMap<String, Show>()
        for (page in 1..2) {
            val res = call("trending/all/day?language=fr-FR&page=$page").optJSONArray("results") ?: continue
            for (i in 0 until res.length()) {
                val r = res.getJSONObject(i)
                val type = r.optString("media_type")
                if (type != "movie" && type != "tv") continue
                val poster = r.optString("poster_path").takeIf { it.startsWith("/") } ?: continue
                val ids = r.optJSONArray("genre_ids")
                val movie = type == "movie"
                found[type + r.optInt("id")] = Show(
                    id = r.optInt("id"),
                    name = r.optString(if (movie) "title" else "name"),
                    platform = "",
                    poster = "https://image.tmdb.org/t/p/w342$poster",
                    genres = (0 until (ids?.length() ?: 0)).mapNotNull { genres[ids!!.optInt(it)] },
                    summary = r.optString("overview"),
                    weight = r.optDouble("popularity", 0.0).toInt(),
                    backdrop = r.optString("backdrop_path").takeIf { it.startsWith("/") }
                        ?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    kind = if (movie) "Film" else "Série",
                    year = r.optString(if (movie) "release_date" else "first_air_date").take(4),
                    tmdbType = type
                )
            }
        }
        val all = found.values.toList()

        // Sur quelle plateforme le regarder (Côte d'Ivoire, sinon France)
        for (s in all.take(20)) {
            try {
                val res = call("${s.tmdbType}/${s.id}/watch/providers").optJSONObject("results") ?: continue
                for (region in listOf("CI", "FR")) {
                    val flat = res.optJSONObject(region)?.optJSONArray("flatrate") ?: continue
                    for (i in 0 until flat.length()) {
                        val n = platformName(flat.getJSONObject(i).optString("provider_name"))
                        if (n != null) { s.platform = n; break }
                    }
                    if (s.platform.isNotEmpty()) break
                }
            } catch (_: Exception) {}
        }

        // La grande bannière : 6 titres avec image de fond, si possible de plateformes différentes
        val withBack = all.filter { it.backdrop != null }
        val picks = (withBack.filter { it.platform.isNotEmpty() }.distinctBy { it.platform } + withBack)
            .distinct().take(6)
        return picks + (all - picks.toSet())
    }

    /** Les séries les plus populaires d'une plateforme (ex. Crunchyroll = 283), en français. */
    fun loadProvider(key: String, providerId: Int, platform: String): List<Show> {
        if (key.isBlank()) return emptyList()
        val k = key.trim()
        val bearer = k.length > 40
        val base = "https://api.themoviedb.org/3/discover/tv?language=fr-FR&sort_by=popularity.desc" +
            "&with_watch_providers=$providerId&watch_region=FR"
        val url = base + if (bearer) "" else "&api_key=$k"
        val h = if (bearer) mapOf("Authorization" to "Bearer $k") else emptyMap()
        val res = JSONObject(String(Net.get(url, headers = h))).optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Show>()
        for (i in 0 until res.length()) {
            val r = res.getJSONObject(i)
            val back = r.optString("backdrop_path").takeIf { it.startsWith("/") } ?: continue
            out += Show(
                id = r.optInt("id"), name = r.optString("name"), platform = platform,
                poster = r.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" },
                genres = emptyList(), summary = r.optString("overview"),
                weight = r.optDouble("popularity", 0.0).toInt(),
                backdrop = "https://image.tmdb.org/t/p/w780$back",
                kind = "Série", year = r.optString("first_air_date").take(4), tmdbType = "tv"
            )
        }
        return out
    }

    // ------------------------------------------------------------ TVmaze (secours, en anglais)

    private fun loadTvmaze(): List<Show> {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val found = LinkedHashMap<Int, Show>()
        for (d in listOf(now, now - 86_400_000L, now - 2 * 86_400_000L)) {
            val arr = try {
                JSONArray(String(Net.get("https://api.tvmaze.com/schedule/web?date=" + day.format(Date(d)))))
            } catch (_: Exception) { continue }
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i).optJSONObject("_embedded")?.optJSONObject("show") ?: continue
                val platform = s.optJSONObject("webChannel")?.optString("name") ?: continue
                if (!platformApps.containsKey(platform)) continue
                val id = s.optInt("id")
                if (found.containsKey(id)) continue
                val img = s.optJSONObject("image")
                val genres = s.optJSONArray("genres")
                found[id] = Show(
                    id = id,
                    name = s.optString("name"),
                    platform = if (platform == "Max") "HBO Max" else platform,
                    poster = img?.optString("medium")?.takeIf { it.startsWith("https://") },
                    genres = (0 until (genres?.length() ?: 0)).map { genres!!.optString(it) },
                    summary = clean(s.optString("summary")),
                    weight = s.optInt("weight")
                )
            }
        }
        val sorted = found.values.filter { it.poster != null }.sortedByDescending { it.weight }.take(24)
        val picks = (sorted.distinctBy { it.platform } + sorted).distinct().take(6)
        val list = picks + (sorted - picks.toSet())
        for (s in picks) {
            try {
                val imgs = JSONArray(String(Net.get("https://api.tvmaze.com/shows/${s.id}/images")))
                var best: String? = null
                for (i in 0 until imgs.length()) {
                    val im = imgs.getJSONObject(i)
                    if (im.optString("type") != "background") continue
                    val url = im.optJSONObject("resolutions")?.optJSONObject("original")?.optString("url")
                    if (url != null && url.startsWith("https://")) { best = url; if (im.optBoolean("main")) break }
                }
                s.backdrop = best
            } catch (_: Exception) {}
        }
        return list
    }

    // ------------------------------------------------------------ Actualités

    val feeds = listOf(
        "RFI Afrique" to "https://www.rfi.fr/fr/afrique/rss",
        "RFI" to "https://www.rfi.fr/fr/rss",
        "France 24" to "https://www.france24.com/fr/rss"
    )

    fun loadNews(): List<News> {
        val all = mutableListOf<News>()
        for ((source, url) in feeds) {
            try { all += parseRss(source, Net.get(url, 5_000_000)) } catch (_: Exception) {}
        }
        return all.distinctBy { it.title.lowercase() }
            .sortedByDescending { it.date?.time ?: 0L }
            .take(60)
    }

    private fun parseRss(source: String, bytes: ByteArray): List<News> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(bytes.inputStream(), null)
        val out = mutableListOf<News>()
        var inItem = false
        var title = ""; var link = ""; var desc = ""; var cat = ""
        var image: String? = null; var date: Date? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item" -> { inItem = true; title = ""; link = ""; desc = ""; cat = ""; image = null; date = null }
                    "title" -> if (inItem) title = p.nextText()
                    "link" -> if (inItem && link.isEmpty()) link = p.nextText()
                    "description" -> if (inItem) desc = p.nextText()
                    "category" -> if (inItem && cat.isEmpty()) cat = p.nextText()
                    "pubDate" -> if (inItem) date = parseDate(p.nextText())
                    "enclosure", "content", "thumbnail" -> if (inItem && image == null) {
                        val u = p.getAttributeValue(null, "url")
                        val type = p.getAttributeValue(null, "type") ?: "image"
                        if (u != null && u.startsWith("https://") && (type.startsWith("image") || p.name != "enclosure")) image = u
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "item") {
                inItem = false
                if (title.isNotBlank()) out += News(source, clean(title), link.trim(), clean(desc), image, date, clean(cat))
            }
            ev = p.next()
        }
        return out
    }

    private fun parseDate(s: String): Date? = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(s.trim())
    } catch (_: Exception) { null }

    // ------------------------------------------------------------ Copie locale (démarrage instantané)

    fun showsToJson(l: List<Show>): String = JSONArray().apply {
        for (s in l) put(JSONObject()
            .put("id", s.id).put("name", s.name).put("platform", s.platform)
            .put("poster", s.poster ?: "").put("genres", JSONArray(s.genres))
            .put("summary", s.summary).put("weight", s.weight).put("backdrop", s.backdrop ?: "")
            .put("kind", s.kind).put("year", s.year).put("type", s.tmdbType))
    }.toString()

    fun showsFromJson(t: String): List<Show> = try {
        val a = JSONArray(t)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val g = o.optJSONArray("genres")
            Show(
                id = o.optInt("id"), name = o.optString("name"), platform = o.optString("platform"),
                poster = o.optString("poster").ifEmpty { null },
                genres = (0 until (g?.length() ?: 0)).map { g!!.optString(it) },
                summary = o.optString("summary"), weight = o.optInt("weight"),
                backdrop = o.optString("backdrop").ifEmpty { null },
                kind = o.optString("kind", "Série"), year = o.optString("year"), tmdbType = o.optString("type")
            )
        }
    } catch (_: Exception) { emptyList() }

    fun newsToJson(l: List<News>): String = JSONArray().apply {
        for (n in l) put(JSONObject()
            .put("source", n.source).put("title", n.title).put("link", n.link).put("summary", n.summary)
            .put("image", n.image ?: "").put("date", n.date?.time ?: 0L).put("category", n.category))
    }.toString()

    fun newsFromJson(t: String): List<News> = try {
        val a = JSONArray(t)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val d = o.optLong("date")
            News(
                o.optString("source"), o.optString("title"), o.optString("link"), o.optString("summary"),
                o.optString("image").ifEmpty { null }, if (d > 0) Date(d) else null, o.optString("category")
            )
        }
    } catch (_: Exception) { emptyList() }

    @Suppress("DEPRECATION")
    fun clean(html: String): String =
        Html.fromHtml(html).toString().replace(Regex("\\s+"), " ").trim()

    fun ago(d: Date?): String {
        if (d == null) return ""
        val min = (System.currentTimeMillis() - d.time) / 60_000
        return when {
            min < 1 -> "à l'instant"
            min < 60 -> "il y a $min min"
            min < 24 * 60 -> "il y a ${min / 60} h"
            else -> "il y a ${min / (24 * 60)} j"
        }
    }
}
