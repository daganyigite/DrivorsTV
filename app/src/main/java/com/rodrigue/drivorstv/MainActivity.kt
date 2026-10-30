package com.rodrigue.drivorstv

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Drivors TV : écran d'accueil pour la télé.
 * Tout l'écran est construit dans le code (pas de fichiers de mise en page),
 * pour qu'on puisse l'améliorer facilement au fur et à mesure.
 */
class MainActivity : Activity() {

    data class App(
        val label: String,
        val pkg: String,
        val icon: Drawable,
        val banner: Drawable?,
        val launch: Intent
    )

    // Couleurs de la maquette
    private val bg = Color.parseColor("#0B0E14")
    private val pill = Color.parseColor("#1F2638")
    private val panel = Color.parseColor("#161B23")
    private val fg = Color.parseColor("#F2F4F7")
    private val muted = Color.parseColor("#C9D0DB")
    private val accent = Color.parseColor("#6EA8FE")

    private val handler = Handler(Looper.getMainLooper())
    private var apps: List<App> = emptyList()
    private var featured: List<App> = emptyList()
    private var featuredIndex = 0

    private lateinit var scroll: ScrollView
    private lateinit var heroBox: FrameLayout
    private lateinit var homeRows: LinearLayout
    private lateinit var appsPage: LinearLayout
    private lateinit var appsGrid: GridLayout
    private lateinit var selectionRow: LinearLayout
    private lateinit var myAppsRow: LinearLayout
    private lateinit var heroSource: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroLine: TextView
    private lateinit var heroBanner: ImageView
    private lateinit var heroOpen: TextView
    private lateinit var dots: LinearLayout
    private lateinit var quickPanel: LinearLayout
    private val tabs = mutableListOf<TextView>()
    private var currentTab = 0

    private val rotate = object : Runnable {
        override fun run() {
            if (featured.size > 1 && !heroOpen.hasFocus()) {
                featuredIndex = (featuredIndex + 1) % featured.size
                showFeatured()
            }
            handler.postDelayed(this, 8000)
        }
    }

    // ---------------------------------------------------------------- cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        handler.removeCallbacks(rotate)
        handler.postDelayed(rotate, 8000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(rotate)
    }

    /** Appui sur le bouton Accueil alors qu'on est déjà dans le lanceur. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        hideQuickPanel()
        selectTab(0)
        scroll.scrollTo(0, 0)
        tabs[0].requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un écran d'accueil ne se ferme pas avec Retour.
        when {
            quickPanel.visibility == View.VISIBLE -> hideQuickPanel()
            currentTab != 0 -> { selectTab(0); tabs[0].requestFocus() }
            else -> scroll.smoothScrollTo(0, 0)
        }
    }

    // ---------------------------------------------------------------- construction de l'écran

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(bg) }

        scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isFocusable = false
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(column)

        heroBox = buildHero()
        column.addView(heroBox, LinearLayout.LayoutParams(MATCH, dp(300)))

        homeRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), 0, dp(36), dp(28))
        }
        selectionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        myAppsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        homeRows.addView(rowTitle("Sélection pour toi"))
        homeRows.addView(hScroll(selectionRow))
        homeRows.addView(rowTitle("Mes applis").apply { setPadding(0, dp(14), 0, dp(8)) })
        homeRows.addView(hScroll(myAppsRow))
        column.addView(homeRows)

        appsPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(84), dp(36), dp(28))
            visibility = View.GONE
        }
        appsPage.addView(rowTitle("Toutes les applis"))
        appsGrid = GridLayout(this).apply { columnCount = 8 }
        appsPage.addView(appsGrid)
        column.addView(appsPage)

        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(buildTopBar(), FrameLayout.LayoutParams(MATCH, WRAP))

        quickPanel = buildQuickPanel()
        root.addView(quickPanel, FrameLayout.LayoutParams(dp(280), MATCH, Gravity.END))
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(36), dp(18), dp(36), 0)
        }

        // Photo de profil (initiale)
        bar.addView(TextView(this).apply {
            text = "R"
            setTextColor(fg)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = oval(Color.parseColor("#2A3240"), Color.parseColor("#3A4556"))
        }, LinearLayout.LayoutParams(dp(33), dp(33)).apply { marginEnd = dp(10) })

        // Pilule : Rechercher, Accueil, Direct, Applis
        val nav = pillBox()
        nav.addView(iconButton(R.drawable.ic_search, "Rechercher") { openSearch() })
        listOf("Accueil", "Direct", "Applis").forEachIndexed { i, label ->
            val t = tabView(label, i)
            tabs.add(t)
            nav.addView(t)
        }
        bar.addView(nav, wrapParams(end = 10))

        // Pilule : 3 icônes rapides
        val quick = pillBox()
        quick.addView(iconButton(R.drawable.ic_sparkle, "Assistant") { openAssistant() })
        quick.addView(iconButton(R.drawable.ic_home, "Maison connectée") { openHomeApp() })
        quick.addView(iconButton(R.drawable.ic_gear, "Paramètres") { openSettings(Settings.ACTION_SETTINGS) })
        bar.addView(quick, wrapParams(end = 10))

        // Rond seul : Réglages rapides
        bar.addView(iconButton(R.drawable.ic_sliders, "Réglages rapides", size = 39, filled = true) {
            showQuickPanel()
        })

        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        bar.addView(TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            setTextColor(fg)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }, wrapParams(end = 14))
        bar.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(muted)
            textSize = 14f
        })

        selectTab(0)
        return bar
    }

    private fun buildHero(): FrameLayout {
        val box = FrameLayout(this).apply { background = HeroArt(accent) }

        heroBanner = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#232F55"), 14)
            clipToOutline = true
            elevation = dp(6).toFloat()
        }
        box.addView(heroBanner, FrameLayout.LayoutParams(dp(300), dp(169), Gravity.END or Gravity.TOP).apply {
            topMargin = dp(80); marginEnd = dp(86)
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(96), 0, 0)
        }
        heroSource = TextView(this).apply {
            setTextColor(fg); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        }
        heroTitle = TextView(this).apply {
            setTextColor(fg); textSize = 40f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; setPadding(0, dp(4), 0, dp(4))
        }
        heroLine = TextView(this).apply { setTextColor(Color.parseColor("#E3E7ED")); textSize = 15f }
        heroOpen = TextView(this).apply {
            text = "Ouvrir"
            setTextColor(bg)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(18), dp(7), dp(18), dp(7))
            background = rounded(fg, 99)
            isFocusable = true
            setOnClickListener { featured.getOrNull(featuredIndex)?.let { launch(it) } }
            setOnKeyListener { _, code, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN || featured.size < 2) return@setOnKeyListener false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { featuredIndex = (featuredIndex + 1) % featured.size; showFeatured(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { featuredIndex = (featuredIndex - 1 + featured.size) % featured.size; showFeatured(); true }
                    else -> false
                }
            }
            focusEffect(this, ring = true)
        }
        textCol.addView(heroSource)
        textCol.addView(heroTitle, LinearLayout.LayoutParams(dp(520), WRAP))
        textCol.addView(heroLine)
        textCol.addView(heroOpen, wrapParams().apply { topMargin = dp(14) })
        box.addView(textCol, FrameLayout.LayoutParams(MATCH, MATCH))

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        box.addView(dots, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(36); bottomMargin = dp(18)
        })
        return box
    }

    private fun buildQuickPanel(): LinearLayout {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(22), dp(28), dp(22), dp(22))
            visibility = View.GONE
            elevation = dp(12).toFloat()
        }
        p.addView(TextView(this).apply {
            text = "Réglages rapides"
            setTextColor(fg); textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, dp(16))
        })
        val items = listOf(
            "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
            "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "Son" to Settings.ACTION_SOUND_SETTINGS,
            "Image et écran" to Settings.ACTION_DISPLAY_SETTINGS,
            "Mise en veille" to Settings.ACTION_DREAM_SETTINGS,
            "Date et heure" to Settings.ACTION_DATE_SETTINGS,
            "Applications" to Settings.ACTION_APPLICATION_SETTINGS,
            "Tous les paramètres" to Settings.ACTION_SETTINGS
        )
        for ((label, action) in items) {
            p.addView(TextView(this).apply {
                text = label
                setTextColor(fg)
                textSize = 15f
                setPadding(dp(14), dp(11), dp(14), dp(11))
                isFocusable = true
                background = rounded(Color.TRANSPARENT, 12)
                setOnFocusChangeListener { _, f ->
                    background = rounded(if (f) fg else Color.TRANSPARENT, 12)
                    setTextColor(if (f) bg else fg)
                }
                setOnClickListener { hideQuickPanel(); openSettings(action) }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })
        }
        return p
    }

    // ---------------------------------------------------------------- données : les applis

    private fun loadApps(): List<App> {
        val pm = packageManager
        val found = LinkedHashMap<String, App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
            @Suppress("DEPRECATION")
            val list = pm.queryIntentActivities(query, 0)
            for (ri in list) {
                val ai = ri.activityInfo
                if (ai.packageName == packageName || found.containsKey(ai.packageName)) continue
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(cat)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                found[ai.packageName] = App(ri.loadLabel(pm).toString(), ai.packageName, ri.loadIcon(pm), banner, intent)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun refreshApps() {
        apps = loadApps()
        featured = apps.filter { it.banner != null }.take(5)
        if (featuredIndex >= featured.size) featuredIndex = 0

        selectionRow.removeAllViews()
        for (a in apps.filter { it.banner != null }) selectionRow.addView(bannerCard(a))

        myAppsRow.removeAllViews()
        for (a in apps) myAppsRow.addView(roundApp(a, withLabel = false))

        appsGrid.removeAllViews()
        for (a in apps) appsGrid.addView(roundApp(a, withLabel = true))

        showFeatured()
    }

    private fun showFeatured() {
        val a = featured.getOrNull(featuredIndex)
        if (a == null) {
            heroSource.text = ""
            heroTitle.text = "Bienvenue"
            heroLine.text = "Tes applis sont juste en dessous"
            heroBanner.visibility = View.GONE
            heroOpen.visibility = View.GONE
        } else {
            heroSource.text = "À la une"
            heroTitle.text = a.label
            heroLine.text = "Appuie sur OK pour ouvrir  ·  ◀ ▶ pour changer"
            heroBanner.setImageDrawable(a.banner)
            heroBanner.visibility = View.VISIBLE
            heroOpen.visibility = View.VISIBLE
        }
        dots.removeAllViews()
        featured.indices.forEach { i ->
            val on = i == featuredIndex
            dots.addView(View(this).apply { background = rounded(if (on) fg else Color.parseColor("#6B7587"), 99) },
                LinearLayout.LayoutParams(dp(if (on) 16 else 6), dp(6)).apply { marginStart = dp(6) })
        }
    }

    // ---------------------------------------------------------------- éléments de l'écran

    private fun bannerCard(a: App): View {
        val card = ImageView(this).apply {
            setImageDrawable(a.banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#1E2D4A"), 12)
            clipToOutline = true
            contentDescription = a.label
            isFocusable = true
            setOnClickListener { launch(a) }
            setOnLongClickListener { appInfo(a); true }
        }
        focusEffect(card, ring = true, radius = 12)
        return card.also {
            it.layoutParams = LinearLayout.LayoutParams(dp(165), dp(93)).apply { marginEnd = dp(12) }
        }
    }

    private fun roundApp(a: App, withLabel: Boolean): View {
        val circle = FrameLayout(this).apply {
            background = oval(Color.WHITE, null)
            isFocusable = true
            contentDescription = a.label
            setOnClickListener { launch(a) }
            setOnLongClickListener { appInfo(a); true }
        }
        circle.addView(ImageView(this).apply {
            setImageDrawable(a.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        focusEffect(circle, ring = true, oval = true)

        if (!withLabel) {
            circle.layoutParams = LinearLayout.LayoutParams(dp(69), dp(69)).apply {
                marginEnd = dp(16); topMargin = dp(6); bottomMargin = dp(6)
            }
            return circle
        }
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        cell.addView(circle, LinearLayout.LayoutParams(dp(69), dp(69)))
        cell.addView(TextView(this).apply {
            text = a.label
            setTextColor(muted)
            textSize = 11f
            maxLines = 1
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(92), WRAP).apply { topMargin = dp(6) })
        return cell
    }

    private fun tabView(label: String, index: Int): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(15), dp(7), dp(15), dp(7))
        isFocusable = true
        setOnClickListener { onTab(index) }
        setOnFocusChangeListener { _, _ -> styleTabs() }
    }

    private fun iconButton(icon: Int, label: String, size: Int = 32, filled: Boolean = false, action: () -> Unit): View {
        val v = ImageView(this).apply {
            setImageResource(icon)
            contentDescription = label
            setColorFilter(fg)
            val pad = dp(if (size > 34) 10 else 7)
            setPadding(pad, pad, pad, pad)
            background = oval(if (filled) pill else Color.TRANSPARENT, null)
            isFocusable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { _, f ->
                background = oval(if (f) fg else if (filled) pill else Color.TRANSPARENT, null)
                setColorFilter(if (f) bg else fg)
            }
        }
        v.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        return v
    }

    private fun pillBox() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(pill, 99)
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun rowTitle(t: String) = TextView(this).apply {
        text = t
        setTextColor(muted)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun hScroll(content: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        clipChildren = false
        addView(content)
    }

    // ---------------------------------------------------------------- onglets et panneau

    private fun onTab(index: Int) {
        when (index) {
            1 -> openLiveTv()
            else -> selectTab(index)
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val home = index == 0
        heroBox.visibility = if (home) View.VISIBLE else View.GONE
        homeRows.visibility = if (home) View.VISIBLE else View.GONE
        appsPage.visibility = if (index == 2) View.VISIBLE else View.GONE
        if (this::scroll.isInitialized) scroll.scrollTo(0, 0)
        styleTabs()
    }

    private fun styleTabs() {
        tabs.forEachIndexed { i, t ->
            val active = i == currentTab || t.hasFocus()
            t.background = rounded(if (active) fg else Color.TRANSPARENT, 99)
            t.setTextColor(if (active) bg else fg)
            if (t.hasFocus() && i != currentTab) t.background = rounded(muted, 99)
        }
    }

    private fun showQuickPanel() {
        quickPanel.visibility = View.VISIBLE
        quickPanel.getChildAt(1)?.requestFocus()
    }

    private fun hideQuickPanel() {
        if (quickPanel.visibility == View.VISIBLE) quickPanel.visibility = View.GONE
    }

    // ---------------------------------------------------------------- actions

    private fun launch(a: App) {
        try { startActivity(a.launch) } catch (e: Exception) { toast("Impossible d'ouvrir ${a.label}") }
    }

    private fun appInfo(a: App) {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
    }

    private fun openSettings(action: String) {
        if (!tryStart(Intent(action))) tryStart(Intent(Settings.ACTION_SETTINGS))
    }

    private fun openSearch() {
        if (!tryStart(Intent(Intent.ACTION_ASSIST)) &&
            !tryStart(Intent("android.search.action.GLOBAL_SEARCH"))
        ) toast("Recherche indisponible")
    }

    private fun openAssistant() {
        if (!tryStart(Intent(Intent.ACTION_VOICE_COMMAND)) && !tryStart(Intent(Intent.ACTION_ASSIST)))
            toast("Assistant indisponible")
    }

    private fun openHomeApp() {
        val pkgs = listOf("com.google.android.apps.chromecast.app", "com.google.android.apps.tv.dreamx")
        for (p in pkgs) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p)
            if (i != null && tryStart(i)) return
        }
        toast("Aucune appli Maison sur cette télé")
    }

    /** Onglet Direct : ouvre l'appli des chaînes TV de la télé. */
    private fun openLiveTv() {
        val pkgs = listOf("com.mediatek.wwtv.tvcenter", "com.google.android.tv", "com.android.tv", "com.droidlogic.tvinput")
        for (p in pkgs) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p)
            if (i != null && tryStart(i)) return
        }
        toast("Appli TV en direct introuvable")
    }

    private fun tryStart(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- petits outils

    /** Zoom + contour quand l'élément est sélectionné à la télécommande. */
    private fun focusEffect(v: View, ring: Boolean, radius: Int = 99, oval: Boolean = false) {
        val prev = v.onFocusChangeListener
        v.setOnFocusChangeListener { view, f ->
            prev?.onFocusChange(view, f)
            view.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(120).start()
            view.elevation = if (f) dp(8).toFloat() else 0f
            if (ring) {
                view.foreground = if (f) GradientDrawable().apply {
                    if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radius).toFloat()
                    setStroke(dp(3), accent)
                } else null
            }
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun oval(color: Int, stroke: Int?) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun wrapParams(end: Int = 0) = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(end) }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/** Le décor de la grande bannière (les cercles de la maquette). */
class HeroArt(private val accent: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        c.drawColor(Color.parseColor("#141A2A"))
        val cx = w * 0.766f
        val cy = h * 0.525f
        circle(c, cx, cy, h * 0.75f, Color.parseColor("#1B2440"), 255)
        circle(c, cx, cy, h * 0.525f, Color.parseColor("#232F55"), 255)
        circle(c, cx, cy, h * 0.3f, accent, 217)
        circle(c, w * 0.914f, h * 0.175f, h * 0.115f, Color.parseColor("#F5A524"), 230)
        p.color = Color.parseColor("#0B0E14"); p.alpha = 150
        c.drawRect(0f, h * 0.8f, w, h, p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        p.color = color; p.alpha = alpha
        c.drawCircle(x, y, r, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
