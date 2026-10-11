package com.ampergrad.game.engine

/**
 * Порт mulberry32 из packages/engine/src/prng.ts — один-в-один.
 *
 * Это критично: ежедневный челлендж генерируется и на клиенте, и на сервере
 * из одного seed. Любое расхождение = сервер отвергнет честный результат.
 * Соответствие проверяется тестом EngineVectorsTest по файлу vectors/levels.json.
 */
class Rng(seed: Int) {

    private var a: Int = seed

    /** Следующее 32-битное беззнаковое число (в Long, чтобы не терять старший бит). */
    fun nextU32(): Long {
        var t = a + 0x6D2B79F5
        a = t
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL)
    }

    /** Целое в диапазоне [0, n). */
    fun nextInt(n: Int): Int {
        require(n > 0) { "nextInt: n must be > 0" }
        return (nextU32() % n.toLong()).toInt()
    }

    /** Перемешивание на месте (Fisher–Yates). */
    fun <T> shuffle(items: MutableList<T>): MutableList<T> {
        for (i in items.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = items[i]
            items[i] = items[j]
            items[j] = tmp
        }
        return items
    }
}

/** Стабильный хэш строки в Int (FNV-1a по UTF-16 code units, как в TS-версии). */
fun hashString(text: String): Int {
    var h = 0x811C9DC5.toInt()
    for (ch in text) {
        h = h xor ch.code
        h *= 0x01000193
    }
    return h
}
