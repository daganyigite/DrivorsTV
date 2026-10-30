package com.rodrigue.drivorstv

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/** Mise à jour de Drivors TV directement depuis l'app, à partir des versions publiées sur GitHub. */
object Updater {
    private const val REPO = "daganyigite/DrivorsTV"

    data class Release(val number: Int, val apkUrl: String)

    fun currentNumber(ctx: Context): Int = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (_: Exception) { 0 }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    /** Dernière version publiée (à appeler hors du fil principal). */
    fun latest(): Release? {
        val j = JSONObject(String(Net.get("https://api.github.com/repos/$REPO/releases/latest")))
        val n = j.optString("tag_name").substringAfterLast('.').toIntOrNull() ?: return null
        val assets = j.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) return Release(n, a.optString("browser_download_url"))
        }
        return null
    }

    /** Télécharge puis installe. La télé demande une confirmation à l'écran. */
    fun install(activity: Activity, rel: Release, status: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            status("Autorise Drivors TV à installer des applis, puis relance la mise à jour.")
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName))
                )
            } catch (_: Exception) {}
            return
        }
        status("Téléchargement de la v1.0.${rel.number}…")
        Thread {
            try {
                val bytes = Net.get(rel.apkUrl, 60_000_000)
                val file = File(activity.cacheDir, "DrivorsTV-update.apk")
                file.writeBytes(bytes)
                val installer = activity.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("DrivorsTV.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val flags = if (Build.VERSION.SDK_INT >= 31)
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    else PendingIntent.FLAG_UPDATE_CURRENT
                    val pi = PendingIntent.getBroadcast(
                        activity, id, Intent(activity, InstallReceiver::class.java), flags
                    )
                    session.commit(pi.intentSender)
                }
                activity.runOnUiThread { status("Installation… confirme sur l'écran de la télé.") }
            } catch (e: Exception) {
                activity.runOnUiThread { status("Échec de la mise à jour : ${e.message}") }
            }
        }.start()
    }
}

/** Reçoit la réponse de l'installateur Android. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(confirm) } catch (_: Exception) {}
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(
                ctx,
                "Mise à jour non installée : " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
