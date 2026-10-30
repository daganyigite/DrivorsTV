package com.rodrigue.drivorstv

import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Une série qui sort en ce moment sur une plateforme de streaming. */
data class Show(
    val id: Int,
    val name: String,
    val platform: String,
    val poster: String?,
    val genres: List<String>,
    val summary: String,
    val weight: Int,
    var backdrop: String? = null
)

/** Un article d'actualité. */
data class News(
    val source: String,
    val title: String,
    val link: String,
    val summary: String,
    val image: String?,
    val date: Date?
)

object Content {

    /** Plateformes affichées, et l'appli Android TV qui va avec. */
    val platformApps = linkedMapOf(
        "Netflix" to listOf("com.netflix.ninja"),
        "Prime Video" to listOf("com.amazon.amazonvideo.livingroom"),
        "Disney+" to listOf("com.disney.disneyplus"),
        "Apple TV+" to listOf("com.apple.atve.androidtv.appletv"),
        "HBO Max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "Max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "Paramount+" to listOf("com.cbs.ott"),
        "Hulu" to listOf("com.hulu.livingroomplus"),
        "Peacock" to listOf("com.peacocktv.peacockandroid")
    )

    /** Séries sorties hier et aujourd'hui sur les grandes plateformes (source : TVmaze, sans clé). */
    fun loadShows(): List<Show> {
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
        val list = found.values.filter { it.poster != null }.sortedByDescending { it.weight }.take(24)
        // Grande image de fond pour les 6 premières
        for (s in list.take(6)) {
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
        // Même article dans deux flux RFI : on garde le premier
        return all.distinctBy { it.title.lowercase() }
            .sortedByDescending { it.date?.time ?: 0L }
            .take(80)
    }

    private fun parseRss(source: String, bytes: ByteArray): List<News> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(bytes.inputStream(), null)
        val out = mutableListOf<News>()
        var inItem = false
        var title = ""; var link = ""; var desc = ""; var image: String? = null; var date: Date? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item" -> { inItem = true; title = ""; link = ""; desc = ""; image = null; date = null }
                    "title" -> if (inItem) title = p.nextText()
                    "link" -> if (inItem && link.isEmpty()) link = p.nextText()
                    "description" -> if (inItem) desc = p.nextText()
                    "pubDate" -> if (inItem) date = parseDate(p.nextText())
                    "enclosure", "content", "thumbnail" -> if (inItem && image == null) {
                        val u = p.getAttributeValue(null, "url")
                        val type = p.getAttributeValue(null, "type") ?: "image"
                        if (u != null && u.startsWith("https://") && (type.startsWith("image") || p.name != "enclosure")) image = u
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "item") {
                inItem = false
                if (title.isNotBlank()) out += News(source, clean(title), link.trim(), clean(desc), image, date)
            }
            ev = p.next()
        }
        return out
    }

    private fun parseDate(s: String): Date? = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(s.trim())
    } catch (_: Exception) { null }

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
