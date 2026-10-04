package com.akshansh.aktv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private enum class Tab { MOVIES, LIVE }

    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var loader: ProgressBar
    private lateinit var moviesTab: TextView
    private lateinit var liveTab: TextView
    private lateinit var chips: RecyclerView
    private lateinit var movieGrid: RecyclerView
    private lateinit var channelGrid: RecyclerView
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private lateinit var retry: TextView

    private lateinit var movieAdapter: MovieAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var chipAdapter: ChipAdapter

    private var tab = Tab.MOVIES
    private var defaultMovies = emptyList<Movie>()
    private var currentMovieQuery = ""
    private var channels = emptyList<Channel>()
    private var category: String? = null

    private var loadJob: Job? = null
    private var debounceJob: Job? = null
    private var playJob: Job? = null
    private var suppressSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        goImmersive()
        applySafeInsets(findViewById(R.id.root))

        search = findViewById(R.id.search)
        status = findViewById(R.id.status)
        loader = findViewById(R.id.loader)
        moviesTab = findViewById(R.id.moviesTab)
        liveTab = findViewById(R.id.liveTab)
        chips = findViewById(R.id.chips)
        movieGrid = findViewById(R.id.grid)
        channelGrid = findViewById(R.id.channelGrid)
        emptyState = findViewById(R.id.emptyState)
        emptyText = findViewById(R.id.emptyText)
        retry = findViewById(R.id.retry)

        movieAdapter = MovieAdapter { play(it) }
        channelAdapter = ChannelAdapter { playChannel(it) }
        chipAdapter = ChipAdapter { c ->
            category = c
            applyChannelFilter()
        }

        setupGrid(movieGrid, movieAdapter, 150)
        setupGrid(channelGrid, channelAdapter, 165)

        chips.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        chips.adapter = chipAdapter
        chips.itemAnimator = null

        moviesTab.setOnClickListener { selectTab(Tab.MOVIES) }
        liveTab.setOnClickListener { selectTab(Tab.LIVE) }
        retry.setOnClickListener { reload() }

        // Live TV filters instantly on the device; movies search is debounced.
        search.doAfterTextChanged { text ->
            if (suppressSearch) return@doAfterTextChanged
            val q = text?.toString()?.trim().orEmpty()
            if (tab == Tab.LIVE) {
                applyChannelFilter()
            } else {
                debounceJob?.cancel()
                debounceJob = lifecycleScope.launch {
                    delay(500)
                    if (q != currentMovieQuery) loadMovies(q)
                }
            }
        }
        search.setOnEditorActionListener { _, _, _ ->
            debounceJob?.cancel()
            if (tab == Tab.MOVIES) loadMovies(search.text.toString().trim())
            hideKeyboard()
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (search.text.isNotEmpty()) {
                    search.setText("")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val saved = getSharedPreferences("aktv", Context.MODE_PRIVATE).getString("tab", "MOVIES")
        selectTab(if (saved == "LIVE") Tab.LIVE else Tab.MOVIES)
    }

    // ---------------------------------------------------------------- setup

    private fun setupGrid(grid: RecyclerView, adapter: RecyclerView.Adapter<*>, minCellDp: Int) {
        grid.layoutManager = GridLayoutManager(this, spanFor(minCellDp))
        grid.adapter = adapter
        grid.setHasFixedSize(true)
        grid.setItemViewCacheSize(12)
        grid.itemAnimator = null
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
    }

    private fun spanFor(minCellDp: Int): Int {
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        return max(2, (widthDp / minCellDp).toInt())
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    // ----------------------------------------------------------------- tabs

    private fun selectTab(t: Tab) {
        tab = t
        loadJob?.cancel()
        debounceJob?.cancel()
        setLoading(false)
        showEmpty(false)

        moviesTab.isSelected = t == Tab.MOVIES
        liveTab.isSelected = t == Tab.LIVE

        suppressSearch = true
        search.setText("")
        suppressSearch = false
        search.hint = if (t == Tab.MOVIES) "Search movies" else "Search channels"

        movieGrid.visibility = if (t == Tab.MOVIES) View.VISIBLE else View.GONE
        channelGrid.visibility = if (t == Tab.LIVE) View.VISIBLE else View.GONE
        chips.visibility = if (t == Tab.LIVE && chipAdapter.itemCount > 1) View.VISIBLE else View.INVISIBLE

        getSharedPreferences("aktv", Context.MODE_PRIVATE).edit().putString("tab", t.name).apply()

        when (t) {
            Tab.MOVIES ->
                if (defaultMovies.isNotEmpty()) {
                    currentMovieQuery = ""
                    showMovies(defaultMovies)
                } else loadMovies("")
            Tab.LIVE ->
                if (channels.isNotEmpty()) applyChannelFilter() else loadChannels()
        }
    }

    private fun reload() {
        if (tab == Tab.MOVIES) loadMovies(search.text.toString().trim()) else loadChannels()
    }

    // --------------------------------------------------------------- movies

    private fun loadMovies(term: String) {
        if (term.isBlank() && defaultMovies.isNotEmpty()) {
            currentMovieQuery = ""
            showMovies(defaultMovies)
            return
        }
        loadJob?.cancel()
        showEmpty(false)
        setLoading(true, if (term.isBlank()) "Loading movies…" else "Searching…")
        loadJob = lifecycleScope.launch {
            try {
                val result = ArchiveApi.search(term)
                if (term.isBlank()) defaultMovies = result
                currentMovieQuery = term
                showMovies(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Couldn't load movies.\nCheck your connection and try again.")
            }
        }
    }

    private fun showMovies(list: List<Movie>) {
        setLoading(false)
        movieAdapter.submitList(list) { movieGrid.scrollToPosition(0) }
        fadeIn(movieGrid)
        status.text = "${list.size} titles"
        if (list.isEmpty()) showEmpty(true, "No movies found", false) else showEmpty(false)
    }

    // ------------------------------------------------------------- live tv

    private fun loadChannels() {
        loadJob?.cancel()
        showEmpty(false)
        setLoading(true, "Loading channels…")
        loadJob = lifecycleScope.launch {
            try {
                channels = LiveTvApi.search("")
                val cats = channels.mapNotNull { it.category?.trim() }
                    .filter { it.isNotEmpty() }.distinct().sorted()
                category = null
                chipAdapter.submit(cats)
                chips.visibility = if (cats.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                applyChannelFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Couldn't load Live TV.\nCheck your connection and try again.")
            }
        }
    }

    private fun applyChannelFilter() {
        setLoading(false)
        val q = search.text.toString().trim()
        val cat = category
        val list = channels.filter { c ->
            (cat == null || c.category.equals(cat, true)) &&
                (q.isEmpty() || c.name.contains(q, true) || c.category?.contains(q, true) == true)
        }
        channelAdapter.submitList(list) {
            if (q.isEmpty()) channelGrid.scrollToPosition(0)
        }
        status.text = "${list.size} channels"
        if (list.isEmpty()) showEmpty(true, "No channels found", false) else showEmpty(false)
    }

    // ------------------------------------------------------------- playback

    private fun play(movie: Movie) {
        if (playJob?.isActive == true) return
        setLoading(true, "Preparing ${movie.title}…")
        playJob = lifecycleScope.launch {
            try {
                val resolved = ArchiveApi.resolvePlayableUrl(movie)
                setLoading(false)
                status.text = "${movieAdapter.itemCount} titles"
                if (resolved.videoUrl.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "No playable video file found.", Toast.LENGTH_LONG).show()
                } else {
                    startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra("title", resolved.title)
                        putExtra("url", resolved.videoUrl)
                        putExtra("movieId", resolved.id)
                    })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                status.text = "${movieAdapter.itemCount} titles"
                Toast.makeText(this@MainActivity, "Couldn't open this movie. Try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun playChannel(channel: Channel) {
        val url = channel.streamUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "No stream available for this channel.", Toast.LENGTH_LONG).show()
            return
        }
        val target = if (url.contains("/embed/", ignoreCase = true)) WebPlayerActivity::class.java
        else PlayerActivity::class.java
        startActivity(Intent(this, target).apply {
            putExtra("title", channel.name)
            putExtra("url", url)
            putExtra("movieId", "live-${channel.id}")
        })
    }

    // ------------------------------------------------------------ UI helpers

    private fun setLoading(on: Boolean, message: String? = null) {
        loader.visibility = if (on) View.VISIBLE else View.GONE
        if (message != null) status.text = message
    }

    private fun showError(message: String) {
        setLoading(false)
        status.text = ""
        showEmpty(true, message, true)
    }

    private fun showEmpty(show: Boolean, message: String = "", canRetry: Boolean = false) {
        emptyState.visibility = if (show) View.VISIBLE else View.GONE
        emptyText.text = message
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
        if (show && canRetry) retry.requestFocus()
    }

    private fun fadeIn(v: View) {
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(220).start()
    }
}
