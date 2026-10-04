package com.aktv.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.*
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.*
import coil.load
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import java.util.concurrent.Executors

class VH(v: View) : RecyclerView.ViewHolder(v)

class MainActivity : AppCompatActivity() {
    data class Channel(val id: String, val name: String, val category: String, val logo: String)
    data class Site(val name: String, val url: String, val c1: Int, val c2: Int)
    data class Movie(val id: String, val title: String, val year: String, val desc: String)
    sealed class Tile {
        data class Mv(val m: Movie) : Tile()
        data class Ch(val c: Channel) : Tile()
        data class St(val s: Site) : Tile()
        object Hint : Tile()
    }
    class Row(val title: String, var tiles: List<Tile>)

    // ➜ ADD MORE WEBSITES HERE (name, link, gradient colours)
    private val SITES = listOf(
        Site("Net77", "https://net77.cc/home", 0xFFE50914.toInt(), 0xFF5A0A12.toInt()),
        Site("AethoFlix", "https://www.aethoflix.world/", 0xFF0A84FF.toInt(), 0xFF5E5CE6.toInt())
    )
    private val BASE = "https://livetgtv.lovable.app/api/public/channels"
    private val UA = "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 Chrome/110 Safari/537.36"
    private val io = Executors.newFixedThreadPool(3)
    private val prefs by lazy { getSharedPreferences("aktv", Context.MODE_PRIVATE) }
    private val dp by lazy { resources.displayMetrics.density }

    private var all = listOf<Channel>(); private var shown = listOf<Channel>()
    private var rows = listOf<Row>(); private val inner = HashMap<Int, TileAdapter>()
    private var favs = setOf<String>(); private var heroTile: Tile? = null; private var animateRows = true
    private var tab = 0; private var current = -1; private var retries = 0
    private var movieRows = arrayOfNulls<Row>(6); private var moviesLoaded = false
    private var isMovie = false; private var swallowUp = false
    private var player: ExoPlayer? = null

    private lateinit var rowsView: RecyclerView; private lateinit var status: TextView
    private lateinit var layer: View; private lateinit var pv: PlayerView; private lateinit var now: TextView
    private lateinit var spinner: View; private lateinit var segLive: TextView; private lateinit var segSites: TextView; private lateinit var segMovies: TextView
    private var hBox: View? = null; private var hBadge: TextView? = null; private var hTitle: TextView? = null
    private var hSub: TextView? = null; private var hArt: View? = null; private var hLogo: ImageView? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        rowsView = findViewById(R.id.rows); status = findViewById(R.id.status)
        layer = findViewById(R.id.playerLayer); pv = findViewById(R.id.playerView)
        now = findViewById(R.id.nowPlaying); spinner = findViewById(R.id.spinner)
        segLive = findViewById(R.id.segLive); segSites = findViewById(R.id.segSites); segMovies = findViewById(R.id.segMovies)
        favs = prefs.getStringSet("favs", emptySet())!!.toSet()
        rowsView.layoutManager = LinearLayoutManager(this); rowsView.adapter = rowAdapter
        listOf(segLive to 0, segSites to 1, segMovies to 2).forEach { (tv, i) ->
            tv.setOnClickListener { switchTab(i) }
            tv.setOnFocusChangeListener { _, f -> if (f) switchTab(i); styleSegs() }
        }
        styleSegs(); loadChannels()

        val splash = findViewById<View>(R.id.splash); val logo = findViewById<View>(R.id.splashLogo)
        logo.scaleX = 0.85f; logo.scaleY = 0.85f
        logo.animate().scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(DecelerateInterpolator()).start()
        splash.postDelayed({ splash.animate().alpha(0f).setDuration(500).withEndAction { splash.visibility = View.GONE; rowsView.requestFocus() }.start() }, 1800)
    }

    // ---------- helpers ----------
    private fun styleSegs() = listOf(segLive to 0, segSites to 1, segMovies to 2).forEach { (tv, i) ->
        tv.isSelected = tab == i; tv.setTextColor(if (tv.isFocused) Color.BLACK else Color.WHITE)
    }
    private fun focusAnim(x: View, f: Boolean) {
        val s = if (f) 1.12f else 1f
        x.animate().setStartDelay(0).scaleX(s).scaleY(s).translationZ(if (f) 30f else 0f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }
    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000; c.setRequestProperty("User-Agent", UA)
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    // ---------- data ----------
    private fun loadChannels() {
        status.text = "Loading channels…"
        io.execute {
            try {
                val arr = JSONObject(get(BASE)).getJSONArray("channels")
                val list = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Channel(o.getString("id"), o.getString("name"), o.optString("category", "Other"), o.optString("logo", ""))
                }
                runOnUiThread { all = list; status.text = ""; refreshRows(); rowsView.post { rowsView.requestFocus() } }
            } catch (e: Exception) { runOnUiThread { status.text = "Couldn't load channels: ${e.message}" } }
        }
    }

    private fun favTiles(): List<Tile> {
        val l = all.filter { it.id in favs }.map { Tile.Ch(it) }
        return if (l.isEmpty()) listOf(Tile.Hint) else l
    }

    private fun refreshRows(animate: Boolean = true) {
        rows = when (tab) {
            0 -> listOf(Row("★ My Favorites", favTiles())) +
                 all.groupBy { it.category }.toSortedMap().map { (k, v) -> Row(k, v.map { Tile.Ch(it) }) }
            1 -> listOf(Row("Websites", SITES.map { Tile.St(it) }))
            else -> movieRows.filterNotNull()
        }
        heroTile = when (tab) {
            0 -> all.firstOrNull()?.let { Tile.Ch(it) }
            1 -> Tile.St(SITES[0])
            else -> rows.firstOrNull()?.tiles?.firstOrNull()
        }
        animateRows = animate; inner.clear()
        rowAdapter.notifyDataSetChanged()
        rowsView.postDelayed({ animateRows = false }, 1200)
    }

    private fun toggleFav(c: Channel) {
        val s = favs.toMutableSet(); val added = s.add(c.id); if (!added) s.remove(c.id)
        favs = s; prefs.edit().putStringSet("favs", s).apply()
        Toast.makeText(this, if (added) "Added to Favorites ♥" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
        for ((ri, ad) in inner) if (ri > 0) {
            val idx = rows[ri].tiles.indexOfFirst { it is Tile.Ch && it.c.id == c.id }
            if (idx >= 0) ad.notifyItemChanged(idx, "fav")
        }
        rows[0].tiles = favTiles(); animateRows = false; rowAdapter.notifyItemChanged(1)
    }

    private fun switchTab(t: Int) {
        if (t == tab) return
        tab = t; styleSegs()
        rowsView.animate().alpha(0f).setDuration(160).withEndAction {
            if (t == 2 && !moviesLoaded) loadMovies()
            status.text = if (t == 2 && movieRows.all { it == null }) "Loading free movies…" else ""
            refreshRows(); rowsView.scrollToPosition(0)
            rowsView.animate().alpha(1f).setDuration(300).start()
        }.start()
    }

    // ---------- hero banner ----------
    private fun bindHero(v: View) {
        hBox = v.findViewById(R.id.heroBox); hBadge = v.findViewById(R.id.heroBadge); hTitle = v.findViewById(R.id.heroTitle)
        hSub = v.findViewById(R.id.heroSub); hArt = v.findViewById(R.id.heroArt); hLogo = v.findViewById(R.id.heroLogo)
        showHero(heroTile)
    }
    private fun showHero(t: Tile?) {
        val art = GradientDrawable().apply { cornerRadius = 32 * dp }
        when (t) {
            is Tile.Ch -> {
                hBadge?.text = "● LIVE NOW"; hTitle?.text = t.c.name
                hSub?.text = "${t.c.category}  •  OK to watch  •  hold OK to favorite"
                art.setColor(Color.parseColor("#26FFFFFF")); hLogo?.load(t.c.logo)
            }
            is Tile.St -> {
                hBadge?.text = "WEBSITE"; hTitle?.text = t.s.name
                hSub?.text = "Opens in the built-in browser  •  arrows move the pointer, OK clicks"
                art.colors = intArrayOf(t.s.c1, t.s.c2); art.orientation = GradientDrawable.Orientation.TL_BR; hLogo?.setImageDrawable(null)
            }
            is Tile.Mv -> {
                hBadge?.text = "FREE TO WATCH"; hTitle?.text = t.m.title
                hSub?.text = (listOf(t.m.year) + t.m.desc.take(150)).filter { it.isNotBlank() }.joinToString("  •  ")
                art.setColor(Color.parseColor("#26FFFFFF")); hLogo?.load("https://archive.org/services/img/${t.m.id}")
            }
            else -> { hBadge?.text = ""; hTitle?.text = "AK TV"; hSub?.text = "by Akshansh Sahu"; art.setColor(Color.parseColor("#26FFFFFF")); hLogo?.setImageResource(R.drawable.ic_mark) }
        }
        hArt?.background = art
    }
    private fun updateHero(t: Tile) {
        heroTile = t
        val box = hBox ?: return
        box.animate().alpha(0.15f).setDuration(90).withEndAction { showHero(t); box.animate().alpha(1f).setDuration(260).start() }.start()
    }

    // ---------- playback (same engine that worked) ----------
    private fun hexToB64Url(hex: String): String {
        val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    private fun play(index: Int) {
        if (shown.isEmpty()) return
        isMovie = false; pv.useController = false
        current = (index + shown.size) % shown.size
        val ch = shown[current]
        if (layer.visibility != View.VISIBLE) { layer.alpha = 0f; layer.visibility = View.VISIBLE; layer.animate().alpha(1f).setDuration(250).start() }
        spinner.visibility = View.VISIBLE; now.text = ch.name
        releasePlayer()
        io.execute {
            try {
                val o = JSONObject(get("$BASE/${ch.id}"))
                val url = o.getJSONArray("sources").getString(0); val drm = o.optJSONObject("drm")
                runOnUiThread { if (shown.getOrNull(current)?.id == ch.id) start(url, drm) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show(); spinner.visibility = View.GONE }
            }
        }
    }
    private fun start(url: String, drm: JSONObject?) {
        val token = Regex("__hdnea__=([^&]+)").find(url)?.groupValues?.get(1)
        val hdrs = HashMap<String, String>(); if (token != null) hdrs["Cookie"] = "__hdnea__=$token"
        val http = DefaultHttpDataSource.Factory().setUserAgent("plaYtv/7.1.5 (Linux;Android 13) ExoPlayerLib/2.11.7")
            .setDefaultRequestProperties(hdrs).setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(15000)
        val factory = DashMediaSource.Factory(http)
        if (drm != null && drm.has("keyId")) {
            val lic = """{"keys":[{"kty":"oct","k":"${hexToB64Url(drm.getString("key"))}","kid":"${hexToB64Url(drm.getString("keyId"))}"}],"type":"temporary"}"""
            val mgr = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true).build(LocalMediaDrmCallback(lic.toByteArray()))
            factory.setDrmSessionManagerProvider { mgr }
        }
        val item = MediaItem.Builder().setUri(url)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(8000).build()).build()
        val p = ExoPlayer.Builder(this).build()
        p.setMediaSource(factory.createMediaSource(item)); p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) { spinner.visibility = View.GONE; retries = 0 }
                if (s == Player.STATE_BUFFERING) spinner.visibility = View.VISIBLE
            }
            override fun onPlayerError(e: PlaybackException) {
                if (retries++ < 3) play(current) else {
                    spinner.visibility = View.GONE
                    val c = e.cause
                    val extra = if (c is HttpDataSource.InvalidResponseCodeException) " HTTP ${c.responseCode}" else ""
                    Toast.makeText(this@MainActivity, "Playback error: ${e.errorCodeName}$extra", Toast.LENGTH_LONG).show()
                }
            }
        })
        pv.player = p; player = p; p.prepare()
        now.alpha = 1f; now.postDelayed({ if (player === p) now.animate().alpha(0f).setDuration(500).start() }, 4000)
    }
    private fun releasePlayer() { pv.player = null; player?.release(); player = null }
    private fun closePlayer() {
        releasePlayer(); retries = 0; swallowUp = true
        if (layer.visibility == View.VISIBLE) layer.animate().alpha(0f).setDuration(200).withEndAction { layer.visibility = View.GONE; rowsView.requestFocus() }.start()
    }
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP && swallowUp) { swallowUp = false; return true }
        if (layer.visibility == View.VISIBLE) {
            if (e.keyCode == KeyEvent.KEYCODE_BACK) { if (e.action == KeyEvent.ACTION_DOWN) closePlayer(); return true }
            if (!isMovie && e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { retries = 0; play(current - 1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { retries = 0; play(current + 1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { now.alpha = 1f; return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }
    override fun onStop() { super.onStop(); releasePlayer(); layer.visibility = View.GONE }

    // ---------- free movies (Internet Archive) ----------
    private val QUERIES = listOf(
        "Popular Movies" to "collection:feature_films AND mediatype:movies",
        "Film Noir" to "collection:film_noir AND mediatype:movies",
        "Sci-Fi & Horror" to "collection:SciFi_Horror AND mediatype:movies",
        "Comedy" to "collection:comedy_films AND mediatype:movies",
        "Classic TV Series" to "collection:classic_tv AND mediatype:movies",
        "Cartoons & Animation" to "collection:animationandcartoons AND mediatype:movies"
    )
    private fun clean(o: Any?): String {
        val t = if (o is JSONArray) o.optString(0) else o?.toString() ?: ""
        return t.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }
    private fun loadMovies() {
        moviesLoaded = true
        QUERIES.forEachIndexed { i, (title, q) ->
            io.execute {
                try {
                    val url = "https://archive.org/advancedsearch.php?q=${URLEncoder.encode(q, "UTF-8")}" +
                        "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&fl%5B%5D=description" +
                        "&sort%5B%5D=downloads+desc&rows=30&page=1&output=json"
                    val docs = JSONObject(get(url)).getJSONObject("response").getJSONArray("docs")
                    val list = (0 until docs.length()).map {
                        val d = docs.getJSONObject(it)
                        Tile.Mv(Movie(d.getString("identifier"), clean(d.opt("title")).ifBlank { "Untitled" }, clean(d.opt("year")), clean(d.opt("description"))))
                    }
                    runOnUiThread {
                        if (list.isNotEmpty()) {
                            val first = movieRows.all { it == null }
                            movieRows[i] = Row(title, list); status.text = ""
                            if (tab == 2) refreshRows(first)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { if (tab == 2 && movieRows.all { it == null }) status.text = "Couldn't load movies: ${e.message}" }
                }
            }
        }
    }
    private fun showMovie(m: Movie) {
        val d = android.app.Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_movie)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.findViewById<ImageView>(R.id.dPoster).load("https://archive.org/services/img/${m.id}")
        d.findViewById<TextView>(R.id.dTitle).text = m.title
        d.findViewById<TextView>(R.id.dMeta).text = listOf(m.year, "Free  •  via Internet Archive").filter { it.isNotBlank() }.joinToString("  •  ")
        d.findViewById<TextView>(R.id.dDesc).text = m.desc.ifBlank { "No description available." }
        d.findViewById<View>(R.id.dPlay).setOnClickListener { d.dismiss(); playMovie(m) }
        d.findViewById<View>(R.id.dClose).setOnClickListener { d.dismiss() }
        d.show()
    }
    private fun playMovie(m: Movie) {
        isMovie = true
        if (layer.visibility != View.VISIBLE) { layer.alpha = 0f; layer.visibility = View.VISIBLE; layer.animate().alpha(1f).setDuration(250).start() }
        spinner.visibility = View.VISIBLE; now.text = m.title; releasePlayer()
        io.execute {
            try {
                val files = JSONObject(get("https://archive.org/metadata/${m.id}")).getJSONArray("files")
                var pref: String? = null; var any: String? = null
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i); val n = f.optString("name"); val fmt = f.optString("format")
                    if (!n.endsWith(".mp4", true)) continue
                    if (any == null) any = n
                    if (pref == null && (fmt.contains("264") || fmt.contains("MPEG4"))) pref = n
                }
                val file = pref ?: any ?: throw Exception("no playable video file")
                val url = "https://archive.org/download/${m.id}/" + Uri.encode(file, "/")
                runOnUiThread { startVideo(url) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Can't play this one: ${e.message}", Toast.LENGTH_LONG).show(); spinner.visibility = View.GONE }
            }
        }
    }
    private fun startVideo(url: String) {
        val p = ExoPlayer.Builder(this).build()
        p.setMediaItem(MediaItem.fromUri(url)); p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                spinner.visibility = if (s == Player.STATE_BUFFERING || s == Player.STATE_IDLE) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(e: PlaybackException) {
                spinner.visibility = View.GONE
                val c = e.cause
                val extra = if (c is HttpDataSource.InvalidResponseCodeException) " HTTP ${c.responseCode}" else ""
                Toast.makeText(this@MainActivity, "Playback error: ${e.errorCodeName}$extra", Toast.LENGTH_LONG).show()
            }
        })
        pv.useController = true; pv.controllerShowTimeoutMs = 4000
        pv.player = p; player = p; p.prepare()
        pv.isFocusable = true; pv.requestFocus()
        now.alpha = 1f; now.postDelayed({ if (player === p) now.animate().alpha(0f).setDuration(500).start() }, 4000)
    }

    // ---------- adapters ----------
    private inner class TileAdapter(val row: Row) : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = row.tiles.size
        override fun getItemViewType(i: Int) = when (row.tiles[i]) { is Tile.Ch -> 0; is Tile.St -> 1; is Tile.Mv -> 3; else -> 2 }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val l = when (t) { 0 -> R.layout.item_tile; 1 -> R.layout.item_site_tile; 3 -> R.layout.item_poster; else -> R.layout.item_hint }
            return VH(LayoutInflater.from(p.context).inflate(l, p, false))
        }
        override fun onBindViewHolder(h: VH, i: Int, payloads: MutableList<Any>) {
            val t = row.tiles[i]
            if (payloads.isNotEmpty() && t is Tile.Ch) { h.itemView.findViewById<View>(R.id.fav).visibility = if (t.c.id in favs) View.VISIBLE else View.GONE; return }
            super.onBindViewHolder(h, i, payloads)
        }
        override fun onBindViewHolder(h: VH, i: Int) {
            val v = h.itemView
            when (val t = row.tiles[i]) {
                is Tile.Ch -> {
                    v.findViewById<TextView>(R.id.name).text = t.c.name
                    v.findViewById<ImageView>(R.id.logo).load(t.c.logo)
                    v.findViewById<View>(R.id.fav).visibility = if (t.c.id in favs) View.VISIBLE else View.GONE
                    v.setOnFocusChangeListener { x, f -> focusAnim(x, f); if (f) updateHero(t) }
                    v.setOnClickListener {
                        shown = row.tiles.filterIsInstance<Tile.Ch>().map { it.c }
                        retries = 0; play(shown.indexOfFirst { it.id == t.c.id })
                    }
                    v.setOnLongClickListener { toggleFav(t.c); true }
                }
                is Tile.St -> {
                    v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.s.c1, t.s.c2)).apply { cornerRadius = 22 * dp }
                    v.findViewById<TextView>(R.id.siteName).text = t.s.name
                    v.findViewById<TextView>(R.id.siteUrl).text = t.s.url.removePrefix("https://")
                    v.setOnFocusChangeListener { x, f -> focusAnim(x, f); if (f) updateHero(t) }
                    v.setOnClickListener { startActivity(Intent(this@MainActivity, BrowserActivity::class.java).putExtra("url", t.s.url)) }
                }
                is Tile.Mv -> {
                    v.findViewById<TextView>(R.id.pTitle).text = t.m.title
                    v.findViewById<ImageView>(R.id.poster).load("https://archive.org/services/img/${t.m.id}")
                    v.setOnFocusChangeListener { x, f -> focusAnim(x, f); if (f) updateHero(t) }
                    v.setOnClickListener { showMovie(t.m) }
                }
                else -> {}
            }
        }
    }

    private val rowAdapter = object : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = if (rows.isEmpty()) 0 else rows.size + 1
        override fun getItemViewType(i: Int) = if (i == 0) 0 else 1
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(if (t == 0) R.layout.item_hero else R.layout.item_row, p, false))
        override fun onBindViewHolder(h: VH, i: Int) {
            val v = h.itemView
            if (i == 0) { bindHero(v); return }
            val row = rows[i - 1]
            v.findViewById<TextView>(R.id.rowTitle).text = row.title
            val rv = v.findViewById<RecyclerView>(R.id.rowList)
            if (rv.layoutManager == null) rv.layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            val ad = TileAdapter(row); inner[i - 1] = ad; rv.adapter = ad
            if (animateRows) {
                v.alpha = 0f; v.translationY = 60f
                v.animate().setStartDelay(minOf(i, 4) * 80L).alpha(1f).translationY(0f).setDuration(420).setInterpolator(DecelerateInterpolator()).start()
            } else { v.animate().cancel(); v.alpha = 1f; v.translationY = 0f }
        }
    }
}
