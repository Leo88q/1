package com.ampergrad.game

import com.ampergrad.game.engine.DAILY_CONFIG_LEVEL
import com.ampergrad.game.engine.applyRotate
import com.ampergrad.game.engine.dailySeed
import com.ampergrad.game.engine.generateLevel
import com.ampergrad.game.engine.isSolved
import com.ampergrad.game.engine.orbitSize
import com.ampergrad.game.net.DailyScore
import com.ampergrad.game.net.LeaderboardCodec
import com.ampergrad.game.net.PlayProof
import com.ampergrad.game.net.Turn
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Доказательство решения — то, что сервер проигрывает своим ядром
 * (server/src/verify.ts). Здесь проверяем две вещи: формат совпадает с серверным
 * контрактом, а сами ходы действительно собирают квартал в Kotlin-ядре.
 */
class PlayProofTest {

    /** Последовательность поворотов к решению — та же арифметика, что на сервере. */
    private fun solveTurns(levelNumber: Int, seed: Int? = null): List<Turn> {
        val level = if (seed == null) generateLevel(levelNumber) else generateLevel(levelNumber, seed)
        val turns = mutableListOf<Turn>()
        for (i in level.tiles.indices) {
            val tile = level.tiles[i]
            if (tile.fixed) continue
            val period = orbitSize(tile.base)
            var steps = ((level.solutionRot[i] - tile.rot) % period + period) % period
            while (steps > 0) {
                turns += Turn(i, 1)
                steps -= 1
            }
        }
        return turns
    }

    private fun replay(turns: List<Turn>, levelNumber: Int, seed: Int? = null): Boolean {
        val level = if (seed == null) generateLevel(levelNumber) else generateLevel(levelNumber, seed)
        for (turn in turns) {
            if (level.tiles[turn.index].fixed) return false
            applyRotate(level, turn.index, turn.dir)
        }
        return isSolved(level)
    }

    @Test
    fun `ходы уровня действительно собирают квартал`() {
        for (levelNumber in listOf(1, 22, 63, 75)) {
            val turns = solveTurns(levelNumber)
            assertTrue(turns.isNotEmpty(), "уровень $levelNumber: пустой план")
            assertTrue(replay(turns, levelNumber), "уровень $levelNumber: ходы не приводят к пломбе")
        }
    }

    @Test
    fun `формат уровня совпадает с серверным контрактом`() {
        val turns = solveTurns(9)
        val json = PlayProof.level(levelNumber = 9, turns = turns, engine = "0.1.0").toJson()

        assertEquals("level", json.getString("mode"))
        assertEquals(9, json.getInt("level"))
        assertEquals("0.1.0", json.getString("engine"))
        assertEquals(turns.size, json.getInt("moves"), "moves обязан совпадать с числом ходов")
        assertFalse(json.has("day"), "у уровня нет дня")

        val array = json.getJSONArray("turns")
        assertEquals(turns.size, array.length())
        val first = array.getJSONArray(0)
        assertEquals(2, first.length(), "ход — пара [индекс, направление]")
        assertEquals(turns[0].index, first.getInt(0))
        assertEquals(turns[0].dir, first.getInt(1))
    }

    @Test
    fun `формат дня использует общий seed и не содержит уровень`() {
        val day = 20_365L
        val turns = solveTurns(DAILY_CONFIG_LEVEL, dailySeed(day))
        val json = PlayProof.daily(day = day, turns = turns, engine = "0.1.0").toJson()

        assertEquals("daily", json.getString("mode"))
        assertEquals(day, json.getLong("day"))
        assertFalse(json.has("level"), "у челленджа дня нет номера уровня")
        assertTrue(replay(turns, DAILY_CONFIG_LEVEL, dailySeed(day)), "ходы дня не собирают квартал")
    }

    @Test
    fun `поворот неподвижного креста сервер отклонит и здесь тоже`() {
        val level = generateLevel(1)
        val fixedIndex = level.tiles.indexOfFirst { it.fixed }
        if (fixedIndex >= 0) {
            assertFalse(applyRotate(level, fixedIndex, 1), "крест не должен вращаться")
        }
    }

    @Test
    fun `рейтинг переживает запись и чтение`() {
        val board = listOf(
            DailyScore(rank = 1, wallet = "7xKX…9fQm", moves = 21),
            DailyScore(rank = 2, wallet = "3aBc…Z1xy", moves = 24),
        )
        val restored = LeaderboardCodec.decode(LeaderboardCodec.encode(board))
        assertEquals(board, restored)
    }

    @Test
    fun `битый рейтинг не ломает экран`() {
        assertEquals(emptyList(), LeaderboardCodec.decode(null))
        assertEquals(emptyList(), LeaderboardCodec.decode(""))
        assertEquals(emptyList(), LeaderboardCodec.decode("{не json}"))
    }

    @Test
    fun `json доказательства разбирается серверной формой`() {
        // Обратная проверка: то, что приложение отправит, сервер сможет прочитать.
        val json = PlayProof.level(1, solveTurns(1), "0.1.0").toJson().toString()
        val parsed = JSONObject(json)
        assertEquals("level", parsed.getString("mode"))
        assertEquals(parsed.getInt("moves"), parsed.getJSONArray("turns").length())
    }
}
