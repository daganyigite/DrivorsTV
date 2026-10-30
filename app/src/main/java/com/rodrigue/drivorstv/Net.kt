package com.rodrigue.drivorstv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Téléchargements simples (https uniquement). */
object Net {
    fun get(url: String, maxBytes: Int = 15_000_000, headers: Map<String, String> = emptyMap()): ByteArray {
        var current = url
        repeat(5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "DrivorsTV")
            c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw Exception("Redirection vide")
                current = URL(URL(current), loc).toString()
                if (!current.startsWith("https://")) throw Exception("Lien non sécurisé")
                return@repeat
            }
            if (code != 200) { c.disconnect(); throw Exception("HTTP $code") }
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw Exception("Fichier trop gros")
                }
                out.toByteArray()
            }
            c.disconnect()
            return bytes
        }
        throw Exception("Trop de redirections")
    }
}

/** Charge les images en arrière-plan, avec un petit cache en mémoire. */
object Images {
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String?, view: ImageView, maxWidth: Int) {
        view.tag = url
        if (url.isNullOrEmpty()) { view.setImageDrawable(null); return }
        val hit = cache.get(url)
        if (hit != null) { view.setImageBitmap(hit); return }
        view.setImageDrawable(null)
        pool.execute {
            val bmp = try { decode(Net.get(url), maxWidth) } catch (_: Exception) { null }
            if (bmp != null) cache.put(url, bmp)
            main.post { if (view.tag == url && bmp != null) view.setImageBitmap(bmp) }
        }
    }

    private fun decode(b: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
