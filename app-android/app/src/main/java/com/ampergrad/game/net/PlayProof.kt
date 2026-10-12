package com.ampergrad.game.net

import org.json.JSONArray
import org.json.JSONObject

/** Один ход: индекс ячейки и направление поворота (+1 по часовой, -1 против). */
data class Turn(val index: Int, val dir: Int)

/**
 * Доказательство решения: последовательность поворотов, которую приложение
 * отправляет серверу вместе с заявкой на награду.
 *
 * Сервер не верит клиенту на слово: он проигрывает эти ходы тем же ядром
 * (`server/src/verify.ts`) и убеждается, что квартал действительно собран.
 * Поэтому подпись кошелька подтверждает владение, а эти ходы — честность игры.
 *
 * Формат совпадает с `PlayProof` на сервере:
 *   { mode, level?, day?, turns: [[индекс, направление]], moves, engine }
 */
data class PlayProof(
    val mode: String,
    val level: Int?,
    val day: Long?,
    val turns: List<Turn>,
    val engine: String,
) {
    val moves: Int get() = turns.size

    fun toJson(): JSONObject {
        val turnsJson = JSONArray()
        for (turn in turns) {
            turnsJson.put(JSONArray().put(turn.index).put(turn.dir))
        }
        return JSONObject()
            .put("mode", mode)
            .put("turns", turnsJson)
            .put("moves", moves)
            .put("engine", engine)
            .apply {
                if (level != null) put("level", level)
                if (day != null) put("day", day)
            }
    }

    companion object {
        fun level(levelNumber: Int, turns: List<Turn>, engine: String): PlayProof =
            PlayProof(mode = "level", level = levelNumber, day = null, turns = turns, engine = engine)

        fun daily(day: Long, turns: List<Turn>, engine: String): PlayProof =
            PlayProof(mode = "daily", level = null, day = day, turns = turns, engine = engine)
    }
}
