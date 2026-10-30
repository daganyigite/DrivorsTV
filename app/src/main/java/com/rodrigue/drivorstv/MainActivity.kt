package com.rodrigue.drivorstv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
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
    private lateinit var prefs: SharedPreferences
    private var apps: List<App> = emptyList()
    private var shows: List<Show> = emptyList()
    private var showsLoadedAt = 0L
    private var featured: List<Show> = emptyList()
    private var featuredIndex = 0
    private var news: List<News> = emptyList()
    private var newsLoadedAt = 0L
    private var newsFilter: String? = null
    private var pendingUpdate: Updater.Release? = null

    private lateinit var scroll: ScrollView
    private lateinit var heroBox: FrameLayout
    private lateinit var heroImage: ImageView
    private lateinit var homeRows: LinearLayout
    private lateinit var appsPage: LinearLayout
    private lateinit var appsGrid: GridLayout
    private lateinit var newsPage: LinearLayout
    private lateinit var newsChips: LinearLayout
    private lateinit var newsList: GridLayout
    private lateinit var selectionRow: LinearLayout
    private lateinit var filmsRow: LinearLayout
    private lateinit var seriesRow: LinearLayout
    private lateinit var filmsTitle: TextView
    private lateinit var seriesTitle: TextView
    private lateinit var myAppsRow: LinearLayout
    private lateinit var heroSource: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroLine: TextView
    private lateinit var heroOpen: TextView
    private lateinit var dots: LinearLayout
    private lateinit var quickPanel: LinearLayout
    private lateinit var updateItem: TextView
    private lateinit var updatePill: TextView
    private lateinit var canalTab: TextView
    private lateinit var detail: FrameLayout
    private val tabs = mutableListOf<TextView>()
    private var currentTab = TAB_HOME

    private val rotate = object : Runnable {
        override fun run() {
            if (featured.size > 1 && !heroOpen.hasFocus()) {
                featuredIndex = (featuredIndex + 1) % featured.size
                showFeatured()
            }
            handler.postDelayed(this, 9000)
        }
    }

    // ---------------------------------------------------------------- cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("drivors", MODE_PRIVATE)
        setContentView(buildScreen())
        fullScreen()
        checkUpdate(manual = false)
    }

    /** Toute la surface de l'écran, sans barre système. */
    @Suppress("DEPRECATION")
    private fun fullScreen() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        if (System.currentTimeMillis() - showsLoadedAt > 3 * 3600_000L) loadShows()
        handler.removeCallbacks(rotate)
        handler.postDelayed(rotate, 9000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(rotate)
    }

    /** Appui sur le bouton Accueil alors qu'on est déjà dans le lanceur. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        hideQuickPanel()
        detail.visibility = View.GONE
        selectTab(TAB_HOME)
        scroll.scrollTo(0, 0)
        tabs[TAB_HOME].requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un écran d'accueil ne se ferme pas avec Retour.
        when {
            detail.visibility == View.VISIBLE -> detail.visibility = View.GONE
            quickPanel.visibility == View.VISIBLE -> hideQuickPanel()
            currentTab != TAB_HOME -> { selectTab(TAB_HOME); tabs[TAB_HOME].requestFocus() }
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
        filmsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        seriesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filmsTitle = rowTitle("Films du moment").apply { setPadding(0, dp(14), 0, dp(8)); visibility = View.GONE }
        seriesTitle = rowTitle("Séries du moment").apply { setPadding(0, dp(14), 0, dp(8)); visibility = View.GONE }
        homeRows.addView(rowTitle("Sélection pour toi"))
        homeRows.addView(hScroll(selectionRow))
        homeRows.addView(rowTitle("Mes applis").apply { setPadding(0, dp(14), 0, dp(8)) })
        homeRows.addView(hScroll(myAppsRow))
        homeRows.addView(filmsTitle)
        homeRows.addView(hScroll(filmsRow))
        homeRows.addView(seriesTitle)
        homeRows.addView(hScroll(seriesRow))
        column.addView(homeRows)

        newsPage = buildNewsPage()
        column.addView(newsPage)

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
        root.addView(quickPanel, FrameLayout.LayoutParams(dp(300), MATCH, Gravity.END))

        detail = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(235, 11, 14, 20))
            visibility = View.GONE
            isClickable = true
        }
        root.addView(detail, FrameLayout.LayoutParams(MATCH, MATCH))
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

        // Pilule : Rechercher, Accueil, Canal+, News, Applis
        val nav = pillBox()
        nav.addView(iconButton(R.drawable.ic_search, "Rechercher") { openSearch() })
        listOf("Accueil", canalLabel(), "News", "Applis").forEachIndexed { i, label ->
            val t = tabView(label, i)
            tabs.add(t)
            nav.addView(t)
        }
        canalTab = tabs[TAB_CANAL]
        canalTab.setOnLongClickListener { chooseCanalApp(); true }
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

        // Pastille visible seulement quand une nouvelle version existe
        updatePill = TextView(this).apply {
            text = "Mise à jour"
            setTextColor(bg)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = rounded(accent, 99)
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { startUpdate() }
            focusEffect(this, ring = true)
        }
        bar.addView(updatePill, wrapParams(end = 14))

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

        selectTab(TAB_HOME)
        return bar
    }

    private fun buildHero(): FrameLayout {
        val box = FrameLayout(this).apply { background = HeroArt(accent) }

        // Image de fond du film / de la série
        heroImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        box.addView(heroImage, FrameLayout.LayoutParams(MATCH, MATCH))

        // Voiles sombres légers : l'image couvre tout l'écran, le texte reste lisible
        box.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xCC0B0E14.toInt(), 0x660B0E14, 0x000B0E14)
            )
        }, FrameLayout.LayoutParams(dp(520), MATCH))
        box.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x000B0E14, 0xFF0B0E14.toInt())
            )
        }, FrameLayout.LayoutParams(MATCH, dp(90), Gravity.BOTTOM))
        box.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB30B0E14.toInt(), 0x000B0E14)
            )
        }, FrameLayout.LayoutParams(MATCH, dp(80), Gravity.TOP))

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(86), 0, 0)
        }
        heroSource = TextView(this).apply {
            setTextColor(fg); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        }
        heroTitle = TextView(this).apply {
            setTextColor(fg); textSize = 38f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(4))
        }
        heroLine = TextView(this).apply {
            setTextColor(Color.parseColor("#E3E7ED")); textSize = 14f
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        heroOpen = TextView(this).apply {
            text = "Regarder"
            setTextColor(bg)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(18), dp(7), dp(18), dp(7))
            background = rounded(fg, 99)
            isFocusable = true
            setOnClickListener { featured.getOrNull(featuredIndex)?.let { watchOrDetail(it) } }
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
        textCol.addView(heroTitle, LinearLayout.LayoutParams(dp(560), WRAP))
        textCol.addView(heroLine, LinearLayout.LayoutParams(dp(520), WRAP))
        textCol.addView(heroOpen, wrapParams().apply { topMargin = dp(14) })
        box.addView(textCol, FrameLayout.LayoutParams(MATCH, MATCH))

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        box.addView(dots, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(36); bottomMargin = dp(18)
        })
        return box
    }

    private fun buildNewsPage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(80), dp(36), dp(28))
            visibility = View.GONE
        }
        page.addView(TextView(this).apply {
            text = "Actualités du moment"
            setTextColor(fg); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })
        newsChips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf<String?>(null) + Content.feeds.map { it.first }
        for (n in names) {
            newsChips.addView(TextView(this).apply {
                text = n ?: "Tout"
                tag = n
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(6), dp(14), dp(6))
                isFocusable = true
                setOnClickListener { newsFilter = n; styleChips(); showNews() }
                setOnFocusChangeListener { _, _ -> styleChips() }
            }, wrapParams(end = 8))
        }
        page.addView(newsChips)
        newsList = GridLayout(this).apply {
            columnCount = 3
            setPadding(0, dp(16), 0, 0)
        }
        page.addView(newsList, LinearLayout.LayoutParams(MATCH, WRAP))
        styleChips()
        return page
    }

    private fun buildQuickPanel(): LinearLayout {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(22), dp(24), dp(22), dp(18))
            visibility = View.GONE
            elevation = dp(12).toFloat()
        }
        p.addView(TextView(this).apply {
            text = "Réglages rapides"
            setTextColor(fg); textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, dp(12))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            list.addView(panelItem(label) { hideQuickPanel(); openSettings(action) })
        }
        list.addView(panelItem("Raccourci Canal+ : changer l'appli") { hideQuickPanel(); chooseCanalApp() })
        updateItem = panelItem("Rechercher une mise à jour") {
            if (pendingUpdate != null) startUpdate() else checkUpdate(manual = true)
        }
        list.addView(updateItem)
        list.addView(TextView(this).apply {
            text = "Drivors TV v" + Updater.currentName(this@MainActivity) +
                "\nFilms et séries : TMDB · Actualités : RFI, France 24"
            setTextColor(muted); textSize = 12f
            setPadding(dp(14), dp(10), 0, 0)
        })
        p.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LinearLayout.LayoutParams(MATCH, 0, 1f))
        return p
    }

    private fun panelItem(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(fg)
        textSize = 15f
        setPadding(dp(14), dp(10), dp(14), dp(10))
        isFocusable = true
        background = rounded(Color.TRANSPARENT, 12)
        setOnFocusChangeListener { _, f ->
            background = rounded(if (f) fg else Color.TRANSPARENT, 12)
            setTextColor(if (f) bg else fg)
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(3) }
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
        canalTab.text = canalLabel()

        myAppsRow.removeAllViews()
        for (a in apps) myAppsRow.addView(roundApp(a, withLabel = false))

        appsGrid.removeAllViews()
        for (a in apps) appsGrid.addView(roundApp(a, withLabel = true))

        fillSelection()
    }

    // ---------------------------------------------------------------- films et séries

    private fun loadShows() {
        Thread {
            val list = try { Content.loadShows(BuildConfig.TMDB_KEY) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (list.isNotEmpty()) {
                    shows = list
                    showsLoadedAt = System.currentTimeMillis()
                    featured = list.take(6)
                    featuredIndex = 0
                }
                fillSelection()
                showFeatured()
            }
        }.start()
    }

    private fun fillSelection() {
        selectionRow.removeAllViews()
        filmsRow.removeAllViews()
        seriesRow.removeAllViews()
        if (shows.isNotEmpty()) {
            for (s in shows.take(20)) selectionRow.addView(posterCard(s))
            val films = shows.filter { it.kind == "Film" }
            val series = shows.filter { it.kind == "Série" && it.tmdbType.isNotEmpty() }
            for (s in films) filmsRow.addView(posterCard(s))
            for (s in series) seriesRow.addView(posterCard(s))
            filmsTitle.visibility = if (films.isEmpty()) View.GONE else View.VISIBLE
            seriesTitle.visibility = if (series.isEmpty()) View.GONE else View.VISIBLE
        } else {
            // Pas encore de films/séries (pas d'internet) : on montre les bannières des applis
            for (a in apps.filter { it.banner != null }) selectionRow.addView(bannerCard(a))
            filmsTitle.visibility = View.GONE
            seriesTitle.visibility = View.GONE
        }
    }

    private fun showFeatured() {
        val s = featured.getOrNull(featuredIndex)
        if (s == null) {
            heroImage.setImageDrawable(null)
            heroSource.text = ""
            heroTitle.text = "Bienvenue"
            heroLine.text = "Les films et séries du moment s'affichent ici dès que la télé est connectée à internet."
            heroOpen.visibility = View.GONE
        } else {
            Images.load(s.backdrop ?: s.poster, heroImage, 1280)
            heroSource.text = s.platform.ifEmpty { "Tendance" }
            heroTitle.text = s.name
            heroLine.text = metaLine(s) + if (s.summary.isNotBlank()) "  |  " + s.summary else ""
            heroOpen.text = if (s.platform.isNotEmpty()) "Regarder sur ${s.platform}" else "Plus d'infos"
            heroOpen.visibility = View.VISIBLE
        }
        dots.removeAllViews()
        featured.indices.forEach { i ->
            val on = i == featuredIndex
            dots.addView(View(this).apply { background = rounded(if (on) fg else Color.parseColor("#6B7587"), 99) },
                LinearLayout.LayoutParams(dp(if (on) 16 else 6), dp(6)).apply { marginStart = dp(6) })
        }
    }

    private fun metaLine(s: Show) =
        listOf(s.kind, s.year, s.genres.take(2).joinToString(" · ")).filter { it.isNotBlank() }.joinToString("  ·  ")

    /** Ouvre l'appli de la plateforme. Renvoie false si elle n'est pas installée. */
    private fun openPlatform(s: Show): Boolean {
        if (s.platform == "Canal+") canalApp()?.let { launch(it); return true }
        val pkgs = Content.platformApps[s.platform] ?: emptyList()
        for (p in pkgs) if (openPackage(p)) return true
        return false
    }

    /** Bouton de la grande bannière : regarder directement, sinon la fiche. */
    private fun watchOrDetail(s: Show) {
        if (s.platform.isEmpty() || !openPlatform(s)) openShow(s)
    }

    /** Fiche du film ou de la série. */
    private fun openShow(s: Show) {
        detail.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), dp(24), dp(28), dp(24))
            background = rounded(panel, 20)
        }
        val poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#232F55"), 12)
            clipToOutline = true
        }
        Images.load(s.poster, poster, 400)
        card.addView(poster, LinearLayout.LayoutParams(dp(180), dp(270)).apply { marginEnd = dp(24) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = s.platform.ifEmpty { "Tendance" }
            setTextColor(accent); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(this).apply {
            text = s.name
            setTextColor(fg); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        })
        col.addView(TextView(this).apply { text = metaLine(s); setTextColor(muted); textSize = 14f })
        col.addView(TextView(this).apply {
            text = s.summary.ifBlank { "Pas de résumé disponible." }
            setTextColor(fg); textSize = 15f
            setLineSpacing(0f, 1.25f)
            maxLines = 7; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(12), 0, 0)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(18), 0, 0) }
        var first: View? = null
        if (s.platform.isNotEmpty()) {
            val watch = actionButton("Regarder sur ${s.platform}") {
                if (!openPlatform(s)) toast("L'appli ${s.platform} n'est pas installée sur la télé")
            }
            buttons.addView(watch, wrapParams(end = 12)); first = watch
        }
        val close = actionButton("Fermer") { detail.visibility = View.GONE }
        buttons.addView(close)
        col.addView(buttons)
        card.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        detail.addView(card, FrameLayout.LayoutParams(dp(760), WRAP, Gravity.CENTER))
        detail.visibility = View.VISIBLE
        val f = first ?: close
        f.post { f.requestFocus() }
    }

    // ---------------------------------------------------------------- actualités

    private fun loadNews(force: Boolean = false) {
        if (!force && news.isNotEmpty() && System.currentTimeMillis() - newsLoadedAt < 20 * 60_000L) {
            showNews(); return
        }
        newsList.removeAllViews()
        newsList.addView(infoText("Chargement des actualités…"))
        Thread {
            val list = try { Content.loadNews() } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (list.isNotEmpty()) { news = list; newsLoadedAt = System.currentTimeMillis() }
                showNews()
            }
        }.start()
    }

    private fun showNews() {
        newsList.removeAllViews()
        val shown = news.filter { newsFilter == null || it.source == newsFilter }
        if (shown.isEmpty()) {
            newsList.addView(infoText(
                if (news.isEmpty()) "Impossible de charger les actualités. Vérifie la connexion internet."
                else "Aucun article pour cette source."
            ))
            return
        }
        // 3 cartes par ligne, sur toute la largeur de l'écran
        val screenDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val w = (screenDp - 72 - 2 * 22) / 3
        shown.forEachIndexed { i, n -> newsList.addView(newsCard(n, w, last = i % 3 == 2)) }
    }

    /** Carte d'article au format RFI : grande photo, rubrique, titre, source et heure. */
    private fun newsCard(n: News, w: Int, last: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            setOnClickListener { openNews(n) }
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#232F55"), 6)
            clipToOutline = true
        }
        Images.load(n.image, img, 700)
        card.addView(img, LinearLayout.LayoutParams(MATCH, dp(w * 9 / 16)))

        val tags = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        if (n.link.contains("podcast", ignoreCase = true)) {
            tags.addView(TextView(this).apply {
                text = "PODCAST"
                setTextColor(Color.parseColor("#3A1820")); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.04f
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = rounded(Color.parseColor("#F4C9D2"), 3)
            }, wrapParams(end = 10))
        }
        tags.addView(TextView(this).apply {
            text = (n.category.ifBlank { n.source }).uppercase()
            setTextColor(muted); textSize = 12f
            letterSpacing = 0.06f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        card.addView(tags)
        card.addView(TextView(this).apply {
            text = n.title
            setTextColor(fg); textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.15f)
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, dp(8))
        })
        card.addView(TextView(this).apply {
            text = listOf(n.source, Content.ago(n.date)).filter { it.isNotBlank() }.joinToString("   |   ")
            setTextColor(muted); textSize = 12f
        })
        card.setOnFocusChangeListener { v, f ->
            v.animate().scaleX(if (f) 1.04f else 1f).scaleY(if (f) 1.04f else 1f).setDuration(120).start()
            v.elevation = if (f) dp(8).toFloat() else 0f
            img.foreground = if (f) GradientDrawable().apply {
                cornerRadius = dp(6).toFloat(); setStroke(dp(3), accent)
            } else null
            v.background = if (f) rounded(Color.parseColor("#1A2030"), 8) else null
        }
        card.layoutParams = GridLayout.LayoutParams().apply {
            width = dp(w)
            setMargins(0, 0, if (last) 0 else dp(22), dp(28))
        }
        return card
    }

    /** Fiche de l'article, lisible directement sur la télé. */
    private fun openNews(n: News) {
        detail.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = rounded(panel, 20)
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#232F55"), 12)
            clipToOutline = true
            visibility = if (n.image != null) View.VISIBLE else View.GONE
        }
        Images.load(n.image, img, 900)
        card.addView(img, LinearLayout.LayoutParams(MATCH, dp(200)).apply { bottomMargin = dp(14) })
        card.addView(TextView(this).apply {
            text = listOf(n.source, Content.ago(n.date)).filter { it.isNotBlank() }.joinToString("  ·  ")
            setTextColor(accent); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = n.title
            setTextColor(fg); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(10))
        })
        card.addView(TextView(this).apply {
            text = n.summary.ifBlank { "Pas de résumé pour cet article." }
            setTextColor(muted); textSize = 16f
            setLineSpacing(0f, 1.25f)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        val close = actionButton("Fermer") { detail.visibility = View.GONE }
        buttons.addView(close, wrapParams(end = 12))
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(n.link))
        if (n.link.startsWith("https://") && view.resolveActivity(packageManager) != null) {
            buttons.addView(actionButton("Lire l'article complet") { tryStart(view) })
        }
        card.addView(buttons)
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(card) }
        detail.addView(sv, FrameLayout.LayoutParams(dp(720), MATCH, Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(30); bottomMargin = dp(30)
        })
        detail.visibility = View.VISIBLE
        close.post { close.requestFocus() }
    }

    private fun styleChips() {
        for (i in 0 until newsChips.childCount) {
            val c = newsChips.getChildAt(i) as TextView
            val active = c.tag == newsFilter
            c.background = rounded(when {
                c.hasFocus() -> fg
                active -> accent
                else -> pill
            }, 99)
            c.setTextColor(if (c.hasFocus() || active) bg else fg)
        }
    }

    // ---------------------------------------------------------------- éléments de l'écran

    private fun posterCard(s: Show): View {
        val card = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#1E2D4A"), 12)
            clipToOutline = true
            contentDescription = "${s.name} sur ${s.platform}"
            isFocusable = true
            setOnClickListener { openShow(s) }
        }
        Images.load(s.poster, card, 300)
        card.contentDescription = s.name + if (s.platform.isNotEmpty()) " sur ${s.platform}" else ""
        focusEffect(card, ring = true, radius = 12)
        card.layoutParams = LinearLayout.LayoutParams(dp(98), dp(140)).apply { marginEnd = dp(12) }
        return card
    }

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
        card.layoutParams = LinearLayout.LayoutParams(dp(165), dp(93)).apply { marginEnd = dp(12) }
        return card
    }

    private fun roundApp(a: App, withLabel: Boolean): View {
        // Pas de fond blanc : l'icône garde son propre fond, découpée en rond
        val circle = FrameLayout(this).apply {
            background = oval(pill, null)
            clipToOutline = true
            isFocusable = true
            contentDescription = a.label
            setOnClickListener { launch(a) }
            setOnLongClickListener { appInfo(a); true }
        }
        val adaptive = Build.VERSION.SDK_INT >= 26 && a.icon is AdaptiveIconDrawable
        if (adaptive) {
            // Icône moderne : son fond remplit tout le rond
            val ai = a.icon as AdaptiveIconDrawable
            val layers = listOfNotNull(ai.background, ai.foreground)
            val full = android.graphics.drawable.LayerDrawable(layers.toTypedArray())
            circle.addView(ImageView(this).apply {
                setImageDrawable(full)
                scaleType = ImageView.ScaleType.CENTER_CROP
                scaleX = 1.45f; scaleY = 1.45f
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        } else {
            // Ancienne icône (souvent avec son propre fond déjà dessiné) : agrandie dans le rond
            circle.addView(ImageView(this).apply {
                setImageDrawable(a.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(dp(62), dp(62), Gravity.CENTER))
        }
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

    private fun actionButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(18), dp(8), dp(18), dp(8))
        isFocusable = true
        background = rounded(pill, 99)
        setTextColor(fg)
        setOnFocusChangeListener { _, f ->
            background = rounded(if (f) fg else pill, 99)
            setTextColor(if (f) bg else fg)
        }
        setOnClickListener { action() }
    }

    private fun infoText(t: String) = TextView(this).apply {
        text = t
        setTextColor(muted)
        textSize = 15f
        setPadding(0, dp(16), 0, dp(16))
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
            TAB_CANAL -> openCanal()
            TAB_NEWS -> { selectTab(TAB_NEWS); loadNews() }
            else -> selectTab(index)
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val home = index == TAB_HOME
        heroBox.visibility = if (home) View.VISIBLE else View.GONE
        homeRows.visibility = if (home) View.VISIBLE else View.GONE
        newsPage.visibility = if (index == TAB_NEWS) View.VISIBLE else View.GONE
        appsPage.visibility = if (index == TAB_APPS) View.VISIBLE else View.GONE
        if (this::scroll.isInitialized) scroll.scrollTo(0, 0)
        styleTabs()
    }

    private fun styleTabs() {
        tabs.forEachIndexed { i, t ->
            val focused = t.hasFocus()
            val current = i == currentTab
            t.background = rounded(when {
                focused -> fg
                current -> Color.parseColor("#3A4556")
                else -> Color.TRANSPARENT
            }, 99)
            t.setTextColor(if (focused) bg else fg)
        }
    }

    private fun showQuickPanel() {
        quickPanel.visibility = View.VISIBLE
        val list = (quickPanel.getChildAt(1) as ScrollView).getChildAt(0) as LinearLayout
        list.getChildAt(0)?.requestFocus()
    }

    private fun hideQuickPanel() {
        if (quickPanel.visibility == View.VISIBLE) quickPanel.visibility = View.GONE
    }

    // ---------------------------------------------------------------- raccourci Canal+

    private fun canalApp(): App? {
        val chosen = prefs.getString("canal_pkg", null)
        if (chosen != null) apps.firstOrNull { it.pkg == chosen }?.let { return it }
        return apps.firstOrNull { it.label.contains("canal", ignoreCase = true) }
    }

    private fun canalLabel(): String {
        val chosen = prefs.getString("canal_pkg", null) ?: return "Canal+"
        return apps.firstOrNull { it.pkg == chosen }?.label ?: "Canal+"
    }

    private fun openCanal() {
        val a = canalApp()
        if (a != null) launch(a) else {
            toast("Choisis l'appli à ouvrir avec ce raccourci")
            chooseCanalApp()
        }
    }

    /** Choisir l'appli ouverte par l'onglet Canal+ (aussi : appui long sur l'onglet). */
    private fun chooseCanalApp() {
        if (apps.isEmpty()) return
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Appli du raccourci Canal+")
            .setItems(labels) { _, i ->
                prefs.edit().putString("canal_pkg", apps[i].pkg).apply()
                canalTab.text = apps[i].label
                toast("Raccourci : ${apps[i].label}")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---------------------------------------------------------------- mise à jour

    private fun checkUpdate(manual: Boolean) {
        if (manual) updateItem.text = "Recherche en cours…"
        Thread {
            val rel = try { Updater.latest() } catch (_: Exception) { null }
            val current = Updater.currentNumber(this)
            runOnUiThread {
                if (rel != null && rel.number > current) {
                    pendingUpdate = rel
                    updatePill.visibility = View.VISIBLE
                    updateItem.text = "Installer la v1.0.${rel.number}"
                    if (manual) toast("Nouvelle version disponible : v1.0.${rel.number}")
                } else {
                    updateItem.text = "Rechercher une mise à jour"
                    if (manual) toast(if (rel == null) "Impossible de vérifier (internet ?)" else "Drivors TV est à jour")
                }
            }
        }.start()
    }

    private fun startUpdate() {
        val rel = pendingUpdate ?: return checkUpdate(manual = true)
        hideQuickPanel()
        Updater.install(this, rel) { msg -> toast(msg) }
    }

    // ---------------------------------------------------------------- actions

    private fun launch(a: App) {
        try { startActivity(a.launch) } catch (e: Exception) { toast("Impossible d'ouvrir ${a.label}") }
    }

    private fun openPackage(p: String): Boolean {
        val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p)
        return i != null && tryStart(i)
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
        for (p in listOf("com.google.android.apps.chromecast.app", "com.google.android.apps.tv.dreamx")) {
            if (openPackage(p)) return
        }
        toast("Aucune appli Maison sur cette télé")
    }

    private fun tryStart(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

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
        const val TAB_HOME = 0
        const val TAB_CANAL = 1
        const val TAB_NEWS = 2
        const val TAB_APPS = 3
    }
}

/** Le décor de la grande bannière quand il n'y a pas encore d'image. */
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
