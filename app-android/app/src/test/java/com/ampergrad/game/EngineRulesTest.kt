package com.ampergrad.game

import com.ampergrad.game.engine.DAILY_CONFIG_LEVEL
import com.ampergrad.game.engine.E
import com.ampergrad.game.engine.N
import com.ampergrad.game.engine.Rng
import com.ampergrad.game.engine.S
import com.ampergrad.game.engine.W
import com.ampergrad.game.engine.applyRotate
import com.ampergrad.game.engine.applySolution
import com.ampergrad.game.engine.componentCount
import com.ampergrad.game.engine.dailySeed
import com.ampergrad.game.engine.generateLevel
import com.ampergrad.game.engine.hint
import com.ampergrad.game.engine.isSolved
import com.ampergrad.game.engine.kindOf
import com.ampergrad.game.engine.levelConfig
import com.ampergrad.game.engine.orbitCanonical
import com.ampergrad.game.engine.orbitSize
import com.ampergrad.game.engine.popcount
import com.ampergrad.game.engine.poweredCells
import com.ampergrad.game.engine.rotateCW
import com.ampergrad.game.engine.rotateMask
import com.ampergrad.game.engine.rotationToReach
import com.ampergrad.game.engine.tileMask
import com.ampergrad.game.engine.TileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Правила игры на стороне Kotlin: те же свойства, что проверены в TS-каноне. */
class EngineRulesTest {

    @Test
    fun `поворот на 4 шага — тождество`() {
        for (mask in 1..15) {
            assertEquals(mask, rotateMask(mask, 4))
            assertEquals(mask, rotateMask(rotateMask(mask, 1), 3))
        }
    }

    @Test
    fun `поворот по часовой стрелке переводит N в E`() {
        assertEquals(E, rotateCW(N))
        assertEquals(S, rotateCW(E))
        assertEquals(W, rotateCW(S))
        assertEquals(N, rotateCW(W))
    }

    @Test
    fun `канонизация орбиты`() {
        assertEquals(1, orbitCanonical(N))
        assertEquals(4, orbitSize(N))
        assertEquals(2, orbitSize(N or S))
        assertEquals(1, orbitSize(N or E or S or W))
        for (mask in 1..15) {
            val r = rotationToReach(orbitCanonical(mask), mask)
            assertEquals(mask, rotateMask(orbitCanonical(mask), r))
        }
    }

    @Test
    fun `классификация форм`() {
        assertEquals(TileKind.CAP, kindOf(N))
        assertEquals(TileKind.STRAIGHT, kindOf(N or S))
        assertEquals(TileKind.ELBOW, kindOf(N or E))
        assertEquals(TileKind.TEE, kindOf(N or E or S))
        assertEquals(TileKind.CROSS, kindOf(15))
        assertFailsWith<IllegalArgumentException> { kindOf(0) }
    }

    @Test
    fun `popcount корректен`() {
        for (mask in 1..15) {
            assertEquals(listOf(N, E, S, W).count { mask and it != 0 }, popcount(mask))
        }
    }

    @Test
    fun `все уровни решаемы и не решены на старте`() {
        for (levelNumber in 1..75) {
            val level = generateLevel(levelNumber)
            assertFalse(isSolved(level), "уровень $levelNumber решён на старте")
            assertTrue(level.parMoves > 0, "уровень $levelNumber: план = 0")
            applySolution(level)
            assertTrue(isSolved(level), "уровень $levelNumber не сходится с решением")
        }
    }

    @Test
    fun `сложность растёт по ярусам`() {
        assertEquals(4, levelConfig(1).size)
        assertEquals(5, levelConfig(16).size)
        assertEquals(6, levelConfig(31).size)
        assertEquals(7, levelConfig(46).size)
        assertEquals(8, levelConfig(61).size)
        assertTrue(levelConfig(61).extraDensity > levelConfig(1).extraDensity)
    }

    @Test
    fun `кресты зафиксированы, остальные вращаются`() {
        val level = generateLevel(60)
        var crosses = 0
        for (i in level.tiles.indices) {
            val tile = level.tiles[i]
            if (tile.kind == TileKind.CROSS) crosses++
            val before = tile.rot
            val rotated = applyRotate(level, i, 1)
            assertEquals(!tile.fixed, rotated)
            if (tile.fixed) assertEquals(before, tile.rot)
        }
        assertTrue(crosses > 0, "на 60-м уровне не оказалось крестов")
    }

    @Test
    fun `подсказка доводит квартал до пломбы`() {
        val level = generateLevel(40)
        var guard = 0
        while (!isSolved(level)) {
            val h = hint(level)
            assertNotNull(h, "подсказка не найдена")
            level.tiles[h.index].rot = h.rot
            guard++
            if (guard > 400) fail("подсказка зациклилась")
        }
        assertTrue(guard > 0)
    }

    @Test
    fun `после решения все ячейки под напряжением`() {
        val level = generateLevel(33)
        applySolution(level)
        assertTrue(poweredCells(level).all { it })
        assertEquals(1, componentCount(level))
    }

    @Test
    fun `челлендж дня детерминирован`() {
        val seed = dailySeed(20000)
        val a = generateLevel(DAILY_CONFIG_LEVEL, seed)
        val b = generateLevel(DAILY_CONFIG_LEVEL, seed)
        assertEquals(a.tiles.map { it.base }, b.tiles.map { it.base })
        assertEquals(a.tiles.map { it.rot }, b.tiles.map { it.rot })
        assertNotEquals(dailySeed(20000), dailySeed(20001))
    }

    @Test
    fun `случайные повороты не ломают проверку`() {
        val level = generateLevel(20)
        val rng = Rng(7)
        repeat(30) { applyRotate(level, rng.nextInt(level.tiles.size), 1) }
        val powered = poweredCells(level)
        assertTrue(powered[level.bus], "шина обязана быть под напряжением")
        assertTrue(powered.count { it } >= 1)
        assertTrue(componentCount(level) >= 1)
        assertTrue(level.tiles.indices.all { tileMask(level, it) in 1..15 })
    }
}
