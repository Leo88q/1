package com.ampergrad.game

import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.math.max

/**
 * Звук прототипа: короткие тоны через ToneGenerator.
 * Финальная версия получит нормальные сэмплы (щелчок реле, гул трансформатора),
 * но для беты важно, чтобы поворот фрагмента «щёлкал» — это половина удовольствия.
 */
class SoundKit {

    var enabled: Boolean = true
    private var tone: ToneGenerator? = null

    private fun generator(): ToneGenerator? {
        if (!enabled) return null
        if (tone == null) {
            tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull()
        }
        return tone
    }

    fun rotate() {
        generator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
    }

    fun connect() {
        generator()?.startTone(ToneGenerator.TONE_PROP_ACK, 60)
    }

    fun solved() {
        val gen = generator() ?: return
        gen.startTone(ToneGenerator.TONE_PROP_PROMPT, 180)
    }

    fun hint() {
        generator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }

    /** Ограничение громкости для будущих сэмплов. */
    fun volumeDb(): Int = max(-20, -10)
}
