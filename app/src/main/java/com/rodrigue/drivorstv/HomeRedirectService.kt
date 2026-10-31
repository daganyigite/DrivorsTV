package com.rodrigue.drivorstv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Bouton Accueil -> Drivors TV.
 * Google TV ne laisse pas un autre lanceur prendre le bouton Accueil. Ce service voit seulement
 * quand l'écran d'accueil Google s'ouvre (rien d'autre : il est limité à ces applis-là) et
 * ramène aussitôt sur Drivors TV, si l'option est activée dans les Réglages rapides.
 */
class HomeRedirectService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in GOOGLE_HOMES) return
        if (!getSharedPreferences("drivors", MODE_PRIVATE).getBoolean(PREF, false)) return
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    companion object {
        const val PREF = "home_lock"
        val GOOGLE_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"
        )
    }
}
