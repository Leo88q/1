package com.ampergrad.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import kotlin.math.max

/**
 * Звук игры: сэмплы, синтезированные для проекта (`app-android/tools/make_sounds.py`),
 * плюс запасной путь на ToneGenerator, если ресурс не загрузился (например, звук
 * выключен в системе или испорчен файл).
 *
 * Канон звука (docs/05 §7): гул массива фоном, «смачный» щелчок реле на поворот,
 * «вдох» при подключении узла, «выдох» при пломбе, служебный сигнал подсказки.
 * Все сэмплы — оригинальные, синтезированы из математики: вопросов по лицензиям
 * на аудио нет.
 */
class SoundKit(context: Context) {

    private val appContext = context.applicationContext

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stopAmbience() else startAmbience()
        }

    private var pool: SoundPool? = null
    private var hum: MediaPlayer? = null
    private var fallback: ToneGenerator? = null

    private val ids = mutableMapOf<Int, Int>() // resId -> soundId

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        pool = runCatching {
            SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attributes)
                .build()
        }.getOrNull()
    }

    /* ─────────────────────────────── Сэмплы ─────────────────────────────── */

    private fun play(resId: Int, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled) return
        val soundPool = pool ?: return
        val soundId = ids.getOrPut(resId) { soundPool.load(appContext, resId, 1) }
        // load() асинхронен: до готовности play() вернёт 0 — тогда тихо молчим.
        soundPool.play(soundId, volume, volume, 1, 0, rate)
    }

    /** Щелчок реле при повороте фрагмента — основной «фидбек» игры. */
    fun rotate() {
        // Крошечная вариация высоты, чтобы серия поворотов не звучала как метроном.
        val rate = 0.96f + (System.nanoTime() % 9).toFloat() / 100f
        play(R.raw.sfx_relay_click, volume = 0.9f, rate = rate)
    }

    /** «Вдох»: узел подключился к шине. */
    fun connect() = play(R.raw.sfx_connect, volume = 0.7f)

    /** «Выдох»: квартал собран. */
    fun solved() = play(R.raw.sfx_seal, volume = 0.9f)

    /** Сигнал Клеммы при подсказке. */
    fun hint() = play(R.raw.sfx_hint, volume = 0.5f)

    /* ─────────────────────────────── Фон ─────────────────────────────── */

    /** Гул массива: тихий бесшовный фон, только на игровом экране. */
    fun startAmbience() {
        if (!enabled || hum != null) return
        hum = runCatching {
            MediaPlayer.create(appContext, R.raw.amb_array_hum)?.apply {
                isLooping = true
                setVolume(0.35f, 0.35f)
                start()
            }
        }.getOrNull()
        if (hum == null) fallbackTone()
    }

    fun stopAmbience() {
        runCatching { hum?.stop() }
        runCatching { hum?.release() }
        hum = null
        runCatching { fallback?.release() }
        fallback = null
    }

    /** Если фон не открылся — держим хотя бы тихий тон, чтобы тишина не «ломалась». */
    private fun fallbackTone() {
        if (!enabled) return
        fallback = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 22) }.getOrNull()
        fallback?.startTone(ToneGenerator.TONE_CDMA_LOW_L, 60_000)
    }

    fun release() {
        stopAmbience()
        runCatching { pool?.release() }
        pool = null
        runCatching { fallback?.release() }
        fallback = null
    }

    /** Ограничение громкости для будущих длинных треков (дБ относительно максимума). */
    fun volumeDb(): Int = max(-20, -10)
}
