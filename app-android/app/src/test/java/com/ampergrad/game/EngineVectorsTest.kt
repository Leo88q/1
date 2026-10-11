package com.ampergrad.game

import com.ampergrad.game.engine.applySolution
import com.ampergrad.game.engine.dailySeed
import com.ampergrad.game.engine.generateLevel
import com.ampergrad.game.engine.isSolved
import com.ampergrad.game.engine.levelConfig
import com.ampergrad.game.engine.DAILY_CONFIG_LEVEL
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Контракт между TS-каноном (packages/engine) и Kotlin-портом.
 * Файл app/src/test/resources/levels.json генерируется командой `npm run vectors`
 * и копируется в модуль приложения (в CI это делает workflow).
 */
class EngineVectorsTest {

    private val vectors: JSONObject by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("levels.json")
            ?: fail(
                "Не найден levels.json. Сгенерируйте векторы из корня репозитория:\n" +
                    "  npm run vectors\n" +
                    "  cp packages/engine/vectors/levels.json app-android/app/src/test/resources/levels.json",
            )
        JSONObject(stream.readBytes().decodeToString())
    }

    private fun checkLevel(vector: JSONObject, label: String) {
        val level = generateLevel(vector.getInt("level"), vector.getLong("seed").toInt())

        assertEquals(vector.getInt("size"), level.size, "$label: сторона квартала")
        assertEquals(vector.getLong("seed").toInt(), level.seed, "$label: seed")
        assertEquals(vector.getInt("bus"), level.bus, "$label: шина")
        assertEquals(vector.getJSONArray("receivers").toIntList(), level.receivers, "$label: приёмники")

        val base = vector.getJSONArray("base").toIntList()
        val startRot = vector.getJSONArray("startRot").toIntList()
        val solutionRot = vector.getJSONArray("solutionRot").toIntList()
        val fixed = vector.getJSONArray("fixed").toIntList()

        assertEquals(base.size, level.tiles.size, "$label: число ячеек")
        for (i in level.tiles.indices) {
            val tile = level.tiles[i]
            assertEquals(base[i], tile.base, "$label: маска ячейки $i")
            assertEquals(startRot[i], tile.rot, "$label: стартовый поворот ячейки $i")
            assertEquals(solutionRot[i], level.solutionRot[i], "$label: решение ячейки $i")
            assertEquals(fixed[i] == 1, tile.fixed, "$label: фиксированность ячейки $i")
        }
        assertEquals(vector.getInt("parMoves"), level.parMoves, "$label: план ходов")

        // Ключевые свойства генератора, а не только совпадение чисел.
        assertFalse(isSolved(level), "$label: квартал решён на старте")
        applySolution(level)
        assertTrue(isSolved(level), "$label: сгенерированное решение не сходится")
    }

    @Test
    fun `ГПСЧ совпадает с каноном`() {
        val pins = vectors.getJSONArray("rngPins")
        assertTrue(pins.length() > 0, "нет пинов ГПСЧ")
        for (i in 0 until pins.length()) {
            val pin = pins.getJSONObject(i)
            val seed = pin.getLong("seed").toInt()
            val rng = com.ampergrad.game.engine.Rng(seed)
            val values = pin.getJSONArray("values")
            for (j in 0 until values.length()) {
                assertEquals(values.getLong(j), rng.nextU32(), "seed=${pin.getLong("seed")}, шаг $j")
            }
        }
    }

    @Test
    fun `хэш строк совпадает с каноном`() {
        val pins = vectors.getJSONArray("hashPins")
        for (i in 0 until pins.length()) {
            val pin = pins.getJSONObject(i)
            val actual = com.ampergrad.game.engine.hashString(pin.getString("text")).toLong() and 0xFFFFFFFFL
            assertEquals(pin.getLong("expected"), actual, "хэш «${pin.getString("text")}»")
        }
    }

    @Test
    fun `все 75 уровней совпадают с каноном`() {
        val levels = vectors.getJSONArray("levels")
        assertEquals(75, levels.length())
        for (i in 0 until levels.length()) {
            val vector = levels.getJSONObject(i)
            checkLevel(vector, "уровень ${vector.getInt("level")}")
        }
    }

    @Test
    fun `челлендж дня совпадает с каноном`() {
        val daily = vectors.getJSONArray("daily")
        assertTrue(daily.length() > 0)
        for (i in 0 until daily.length()) {
            val entry = daily.getJSONObject(i)
            val day = entry.getLong("day")
            assertEquals(entry.getLong("seed").toInt(), dailySeed(day), "seed дня $day")
            val vector = entry.getJSONObject("level")
            assertEquals(DAILY_CONFIG_LEVEL, vector.getInt("level"), "конфигурация дня $day")
            checkLevel(vector, "челлендж дня $day")
        }
    }
}

private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }
