package com.watergenius.water

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

internal class GameAudio(private val context: Context) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attributes).build()
    private val ids = listOf("click", "rotate", "bloom", "hint", "star_sound", "win", "stream").associateWith { name ->
        context.assets.openFd("$name.ogg").use { pool.load(it, 1) }
    }
    private var music: MediaPlayer? = null
    private var track: String? = null
    private var streamId = 0
    private var active = false
    private var musicEnabled = true
    private var effectsEnabled = true
    private var water = false

    fun configure(trackName: String?, musicOn: Boolean, effectsOn: Boolean, waterOn: Boolean) {
        if (track != trackName) {
            music?.release()
            music = null
            track = trackName
        }
        musicEnabled = musicOn
        effectsEnabled = effectsOn
        water = waterOn
        update()
    }

    fun setForeground(foreground: Boolean) {
        if (!foreground) pool.autoPause() else pool.autoResume()
        active = foreground
        update()
    }

    fun play(name: String) {
        if (active && effectsEnabled) ids[name]?.let { pool.play(it, .47f, .47f, 1, 0, 1f) }
    }

    private fun update() {
        val selected = track
        if (selected != null && active && musicEnabled) {
            if (music == null) {
                music = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    context.assets.openFd("$selected.ogg").use {
                        setDataSource(it.fileDescriptor, it.startOffset, it.length)
                    }
                    isLooping = true
                    setVolume(.47f, .47f)
                    setOnPreparedListener { if (active && musicEnabled && music === it) it.start() }
                    prepareAsync()
                }
            } else runCatching { music?.start() }
        } else runCatching { if (music?.isPlaying == true) music?.pause() }
        if (active && effectsEnabled && water) {
            if (streamId == 0) streamId = pool.play(ids.getValue("stream"), .23f, .23f, 0, -1, 1f)
        } else if (streamId != 0) {
            pool.stop(streamId)
            streamId = 0
        }
    }

    fun close() {
        music?.release()
        pool.release()
    }
}
