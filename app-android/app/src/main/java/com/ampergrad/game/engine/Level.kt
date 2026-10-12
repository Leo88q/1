package com.ampergrad.game.engine

import kotlin.math.roundToInt

/**
 * Ядро головоломки — порт packages/engine/src/level.ts.
 *
 * Правило победы: все ячейки квартала подключены к шине («пломба»).
 * Генератор гарантирует существование решения: сначала связный остов
 * магистралей, затем дополнительные рёбра, затем фрагменты разворачиваются.
 */

enum class Role { BUS, RECEIVER, LINE }

class Tile(
    val kind: TileKind,
    /** Базовая (каноническая) маска фрагмента. */
    val base: Int,
    /** Текущий поворот: 0..3. */
    var rot: Int,
    /** Кресты не вращаются. */
    val fixed: Boolean,
    val role: Role,
)

class Level(
    var level: Int,
    val seed: Int,
    val size: Int,
    val tiles: MutableList<Tile>,
    val bus: Int,
    val receivers: List<Int>,
    val solutionRot: List<Int>,
    var parMoves: Int,
)

data class LevelConfig(val size: Int, val extraDensity: Double, val receivers: Int)

/** Настройки сложности: 5 ярусов по 15 уровней. */
fun levelConfig(level: Int): LevelConfig {
    val sizes = intArrayOf(4, 5, 6, 7, 8)
    val density = doubleArrayOf(0.0, 0.08, 0.14, 0.18, 0.22)
    val tier = ((level - 1) / 15).coerceIn(0, sizes.size - 1)
    val inTier = (level - 1) % 15
    val size = minOf(8, sizes[tier] + if (inTier >= 10) 1 else 0)
    return LevelConfig(size = size, extraDensity = density[tier], receivers = 1 + size / 3)
}

/** Seed уровня: одинаков на клиенте и на сервере. */
fun levelSeed(level: Int): Int = hashString("ampergrad:level:$level")

/** Seed ежедневного челленджа: одинаков для всех игроков в этот день. */
fun dailySeed(unixDay: Long): Int = hashString("ampergrad:daily:$unixDay")

/** Номер уровня для челленджа дня (6×6), как в канонических тест-векторах. */
const val DAILY_CONFIG_LEVEL = 33

fun generateLevel(level: Int, seed: Int = levelSeed(level)): Level {
    val cfg = levelConfig(level)
    val rng = Rng(seed)
    val size = cfg.size
    val cells = size * size

    // 1. Остов магистралей: randomized DFS (змейка с возвратом).
    val ports = IntArray(cells)
    val visited = BooleanArray(cells)
    val stack = ArrayDeque<Int>()
    val startCell = rng.nextInt(cells)
    stack.addLast(startCell)
    visited[startCell] = true
    while (stack.isNotEmpty()) {
        val cur = stack.last()
        val cx = cur % size
        val cy = cur / size
        val options = ArrayList<Int>(4)
        for (d in 0..3) {
            val (dx, dy) = DELTA[d]
            val nx = cx + dx
            val ny = cy + dy
            if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
            val ni = ny * size + nx
            if (visited[ni]) continue
            options.add(d)
        }
        if (options.isEmpty()) {
            stack.removeLast()
            continue
        }
        val d = options[rng.nextInt(options.size)]
        val (dx, dy) = DELTA[d]
        val ni = (cy + dy) * size + (cx + dx)
        ports[cur] = ports[cur] or (1 shl d)
        ports[ni] = ports[ni] or (1 shl ((d + 2) % 4))
        visited[ni] = true
        stack.addLast(ni)
    }

    // 2. Дополнительные рёбра: тройники, кресты и ложные развилки.
    val extraEdges = (cells * cfg.extraDensity).roundToInt()
    repeat(extraEdges) {
        val cur = rng.nextInt(cells)
        val cx = cur % size
        val cy = cur / size
        val options = ArrayList<Int>(4)
        for (d in 0..3) {
            val bit = 1 shl d
            if (ports[cur] and bit != 0) continue
            val (dx, dy) = DELTA[d]
            val nx = cx + dx
            val ny = cy + dy
            if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
            val ni = ny * size + nx
            if (ports[ni] and (1 shl ((d + 2) % 4)) != 0) continue
            options.add(d)
        }
        if (options.isEmpty()) return@repeat
        val d = options[rng.nextInt(options.size)]
        val (dx, dy) = DELTA[d]
        val ni = (cy + dy) * size + (cx + dx)
        ports[cur] = ports[cur] or (1 shl d)
        ports[ni] = ports[ni] or (1 shl ((d + 2) % 4))
    }

    // 3. Шина и приёмники.
    val busCandidates = ArrayList<Int>()
    for (i in 0 until cells) {
        val pc = listOf(N, E, S, W).count { ports[i] and it != 0 }
        if (pc <= 2) busCandidates.add(i)
    }
    val bus = if (busCandidates.isNotEmpty()) busCandidates[rng.nextInt(busCandidates.size)] else rng.nextInt(cells)

    val receiverPool = (0 until cells).filter { it != bus }.toMutableList()
    rng.shuffle(receiverPool)
    val receivers = receiverPool.take(maxOf(1, minOf(cfg.receivers, receiverPool.size)))
    val receiversSet = receivers.toHashSet()

    // 4. Фрагменты: базовая маска + случайный стартовый поворот.
    val tiles = ArrayList<Tile>(cells)
    val solutionRot = ArrayList<Int>(cells)
    for (i in 0 until cells) {
        val solutionMask = ports[i]
        val base = orbitCanonical(solutionMask)
        val solRot = rotationToReach(base, solutionMask)
        val fixed = orbitSize(base) == 1
        var rot = rng.nextInt(4) // важно: вызов ГПСЧ происходит всегда (совпадение с TS)
        if (fixed) {
            rot = 0
        } else if (rot == solRot) {
            rot = (rot + 1 + rng.nextInt(3)) % 4
        }
        val role = when {
            i == bus -> Role.BUS
            receiversSet.contains(i) -> Role.RECEIVER
            else -> Role.LINE
        }
        tiles.add(Tile(kindOf(base), base, rot, fixed, role))
        solutionRot.add(if (fixed) 0 else solRot)
    }

    val level0 = Level(level, seed, size, tiles, bus, receivers, solutionRot, 0)

    // Гарантия: стартовое состояние не является решённым.
    if (isSolved(level0)) {
        val movable = tiles.indices.filter { !tiles[it].fixed }
        val pick = movable[rng.nextInt(movable.size)]
        tiles[pick].rot = (tiles[pick].rot + 1) % 4
    }

    level0.parMoves = parMoves(level0)
    return level0
}

/** Минимальное число ходов относительно сгенерированного решения. */
fun parMoves(level: Level): Int {
    var total = 0
    for (i in level.tiles.indices) {
        val t = level.tiles[i]
        if (t.fixed) continue
        val period = orbitSize(t.base)
        total += circularDistance(t.rot % period, level.solutionRot[i] % period, period)
    }
    return total
}

/** Поворот фрагмента. dir: +1 по часовой, -1 против. true — поворот выполнен. */
fun applyRotate(level: Level, index: Int, dir: Int = 1): Boolean {
    val t = level.tiles.getOrNull(index) ?: return false
    if (t.fixed) return false
    t.rot = (((t.rot + dir) % 4) + 4) % 4
    return true
}

/** Текущая маска фрагмента с учётом поворота. */
fun tileMask(level: Level, index: Int): Int =
    rotateMask(level.tiles[index].base, level.tiles[index].rot)

private class UnionFind(size: Int) {
    private val parent = IntArray(size) { it }

    fun find(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        var cur = i
        while (parent[cur] != r) {
            val next = parent[cur]
            parent[cur] = r
            cur = next
        }
        return r
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }
}

private fun buildUnion(level: Level): UnionFind {
    val size = level.size
    val uf = UnionFind(level.tiles.size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val i = y * size + x
            val mask = tileMask(level, i)
            if (mask and E != 0 && x + 1 < size) {
                if (tileMask(level, i + 1) and W != 0) uf.union(i, i + 1)
            }
            if (mask and S != 0 && y + 1 < size) {
                if (tileMask(level, i + size) and N != 0) uf.union(i, i + size)
            }
        }
    }
    return uf
}

/** Число несвязанных групп в квартале. */
fun componentCount(level: Level): Int {
    val uf = buildUnion(level)
    return level.tiles.indices.map { uf.find(it) }.toHashSet().size
}

/** Ячейки, подключённые к шине. */
fun poweredCells(level: Level): BooleanArray {
    val uf = buildUnion(level)
    val busRoot = uf.find(level.bus)
    return BooleanArray(level.tiles.size) { uf.find(it) == busRoot }
}

/** Пломба: все ячейки квартала подключены к шине. */
fun isSolved(level: Level): Boolean {
    val uf = buildUnion(level)
    val busRoot = uf.find(level.bus)
    for (i in level.tiles.indices) if (uf.find(i) != busRoot) return false
    return true
}

data class Hint(val index: Int, val rot: Int, val gain: Int)

/**
 * Подсказка: сначала поворот, сразу уменьшающий число групп; иначе — ход,
 * приближающий фрагмент к сгенерированному решению (см. комментарий в TS-версии).
 */
fun hint(level: Level): Hint? {
    if (isSolved(level)) return null
    val current = componentCount(level)
    var immediate: Hint? = null
    var towardSolution: Hint? = null

    for (i in level.tiles.indices) {
        val t = level.tiles[i]
        if (t.fixed) continue
        val period = orbitSize(t.base)
        val original = t.rot
        for (r in 0..3) {
            if (r == original) continue
            t.rot = r
            val gain = current - componentCount(level)
            val best = immediate
            if (best == null || gain > best.gain) immediate = Hint(i, r, gain)
            if (original % period != level.solutionRot[i] % period && r % period == level.solutionRot[i] % period) {
                val bestToward = towardSolution
                if (bestToward == null || gain > bestToward.gain) towardSolution = Hint(i, r, gain)
            }
            t.rot = original
        }
    }

    val best = immediate
    return if (best != null && best.gain > 0) best else (towardSolution ?: best)
}

/** Приведение квартала к сгенерированному решению (тесты, отладка). */
fun applySolution(level: Level) {
    for (i in level.tiles.indices) {
        if (!level.tiles[i].fixed) level.tiles[i].rot = level.solutionRot[i]
    }
}

/** Маска решения для ячейки. */
fun solutionMask(level: Level, index: Int): Int =
    rotateMask(level.tiles[index].base, level.solutionRot[index])
