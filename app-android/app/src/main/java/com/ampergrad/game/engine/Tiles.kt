package com.ampergrad.game.engine

/**
 * Фрагменты магистрали и операции над ними.
 * Порты: N=1, E=2, S=4, W=8 (по часовой стрелке от севера). Порт TS-версии.
 */

const val N = 1
const val E = 2
const val S = 4
const val W = 8

/** Смещения соседей по индексу бита (0=N, 1=E, 2=S, 3=W). */
val DELTA: Array<Pair<Int, Int>> = arrayOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

/** Поворот маски на 90° по часовой стрелке. */
fun rotateCW(mask: Int): Int = ((mask shl 1) or (mask ushr 3)) and 0xF

/** Поворот маски на r шагов по часовой стрелке (r = 0..3). */
fun rotateMask(mask: Int, r: Int): Int {
    var m = mask
    repeat(((r % 4) + 4) % 4) { m = rotateCW(m) }
    return m
}

fun popcount(mask: Int): Int {
    var m = mask and 0xF
    var c = 0
    while (m != 0) {
        c += m and 1
        m = m ushr 1
    }
    return c
}

enum class TileKind { CAP, STRAIGHT, ELBOW, TEE, CROSS }

/** Форма фрагмента по маске. */
fun kindOf(mask: Int): TileKind = when (popcount(mask)) {
    1 -> TileKind.CAP
    2 -> if (mask == (N or S) || mask == (E or W)) TileKind.STRAIGHT else TileKind.ELBOW
    3 -> TileKind.TEE
    4 -> TileKind.CROSS
    else -> throw IllegalArgumentException("Недопустимая маска фрагмента: $mask")
}

/** Каноническое представление орбиты поворотов (минимальная маска). */
fun orbitCanonical(mask: Int): Int {
    var best = mask
    for (r in 1..3) {
        val m = rotateMask(mask, r)
        if (m < best) best = m
    }
    return best
}

/** Число различных поворотов (4 — несимметричный, 2 — прямой, 1 — крест). */
fun orbitSize(mask: Int): Int {
    val set = HashSet<Int>()
    for (r in 0..3) set.add(rotateMask(mask, r))
    return set.size
}

/** Минимальный поворот r, при котором база совпадает с целевой маской. */
fun rotationToReach(base: Int, target: Int): Int {
    for (r in 0..3) if (rotateMask(base, r) == target) return r
    throw IllegalArgumentException("Маски $base и $target не совместимы")
}

/** Кратчайшее расстояние между двумя поворотами по циклу периода period. */
fun circularDistance(a: Int, b: Int, period: Int = 4): Int {
    val d = (((b - a) % period) + period) % period
    return minOf(d, period - d)
}

/** Текстовый вид маски — для тестов и логов. */
fun maskToString(mask: Int): String =
    (if (mask and N != 0) "N" else "-") +
        (if (mask and E != 0) "E" else "-") +
        (if (mask and S != 0) "S" else "-") +
        (if (mask and W != 0) "W" else "-")
