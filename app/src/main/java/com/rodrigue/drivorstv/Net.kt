package com.rodrigue.drivorstv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
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

/**
 * Charge les images en arrière-plan.
 * - petit cache en mémoire (la télé n'a qu'environ 1 Go de mémoire vive)
 * - copie sur la télé : au prochain démarrage, les images s'affichent sans internet
 * - format léger (RGB_565 : deux fois moins de mémoire, aucune différence visible sur des photos)
 */
object Images {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private var dir: File? = null

    fun init(ctx: Context) {
        dir = File(ctx.cacheDir, "img").apply { mkdirs() }
        pool.execute { prune() }
    }

    private fun fileFor(url: String): File? = dir?.let { d ->
        val md = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        File(d, md.joinToString("") { "%02x".format(it) })
    }

    fun load(url: String?, view: ImageView, maxWidth: Int) {
        view.tag = url
        if (url.isNullOrEmpty()) { view.setImageDrawable(null); return }
        val hit = cache.get(url)
        if (hit != null) { view.setImageBitmap(hit); return }
        view.setImageDrawable(null)
        pool.execute {
            val f = fileFor(url)
            var bytes: ByteArray? = try { if (f != null && f.exists()) f.readBytes() else null } catch (_: Exception) { null }
            if (bytes == null) {
                bytes = try { Net.get(url) } catch (_: Exception) { null }
                if (bytes != null && f != null) try { f.writeBytes(bytes) } catch (_: Exception) {}
            } else {
                f?.setLastModified(System.currentTimeMillis())
            }
            val bmp = bytes?.let { try { decode(it, maxWidth) } catch (_: Throwable) { null } }
            if (bmp != null) cache.put(url, bmp)
            main.post {
                if (view.tag == url && bmp != null) {
                    view.alpha = 0f
                    view.setImageBitmap(bmp)
                    view.animate().alpha(1f).setDuration(220).start()
                }
            }
        }
    }

    /** Garde au plus ~80 Mo d'images sur la télé (les plus anciennes partent d'abord). */
    private fun prune() {
        val files = dir?.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total < 60L * 1024 * 1024) break
            total -= f.length(); f.delete()
        }
    }

    private fun decode(b: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }
}
