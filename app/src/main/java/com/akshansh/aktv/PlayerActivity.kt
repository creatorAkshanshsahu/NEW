package com.akshansh.aktv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var errorView: TextView
    private var url: String? = null
    private var resumePosition = 0L
    private var isLive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        goImmersive()

        url = intent.getStringExtra("url")
        isLive = intent.getStringExtra("movieId")?.startsWith("live-") == true
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        playerView = findViewById(R.id.player)
        titleView = findViewById(R.id.playerTitle)
        errorView = findViewById(R.id.playerError)
        titleView.text = intent.getStringExtra("title") ?: "AK TV"

        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                titleView.animate().alpha(if (visibility == View.VISIBLE) 1f else 0f)
                    .setDuration(200).start()
            }
        )
        errorView.setOnClickListener { retryPlayback() }

        // Fade the title away a few seconds after playback starts.
        titleView.postDelayed({
            if (!playerView.isControllerFullyVisible) titleView.animate().alpha(0f).setDuration(400).start()
        }, 4000)
    }

    override fun onStart() {
        super.onStart()
        if (!url.isNullOrBlank()) initPlayer()
    }

    private fun initPlayer() {
        val u = url ?: return
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) AKTV/1.3")

        val item = MediaItem.Builder().setUri(u).apply {
            if (u.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build().also { p ->
                playerView.player = p
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorView.text = "Couldn't play this stream.\n\nPress OK or tap to retry"
                        errorView.visibility = View.VISIBLE
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) errorView.visibility = View.GONE
                    }
                })
                p.setMediaItem(item)
                if (resumePosition > 0 && !isLive) p.seekTo(resumePosition)
                p.prepare()
                p.playWhenReady = true
            }
    }

    private fun retryPlayback() {
        errorView.visibility = View.GONE
        player?.let {
            it.seekToDefaultPosition()
            it.prepare()
            it.playWhenReady = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::errorView.isInitialized && errorView.visibility == View.VISIBLE &&
            event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            retryPlayback()
            return true
        }
        if (::playerView.isInitialized && playerView.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        player?.let {
            if (!isLive) resumePosition = it.currentPosition
            it.release()
        }
        player = null
    }
}
