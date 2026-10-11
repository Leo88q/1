package com.ampergrad.game.solana

/**
 * Base58 (алфавит Bitcoin) — нужен для отображения адреса кошелька
 * и отправки подписи на сервер. Своя реализация, чтобы не тянуть библиотеку.
 */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) zeros++

        // Перевод в 58-ричную систему «в столбик».
        val size = ((input.size - zeros) * 138 / 100) + 1
        val buffer = IntArray(size)
        var length = 0
        for (i in zeros until input.size) {
            var carry = input[i].toInt() and 0xFF
            var j = 0
            var k = size - 1
            while ((carry != 0 || j < length) && k >= 0) {
                carry += 256 * buffer[k]
                buffer[k] = carry % 58
                carry /= 58
                j++
                k--
            }
            length = j
        }

        var start = size - length
        while (start < size && buffer[start] == 0) start++

        val sb = StringBuilder()
        repeat(zeros) { sb.append(ALPHABET[0]) }
        for (i in start until size) sb.append(ALPHABET[buffer[i]])
        return sb.toString()
    }

    /** Короткий вид адреса для интерфейса: AbCd…WxYz */
    fun shorten(address: String, head: Int = 4, tail: Int = 4): String =
        if (address.length <= head + tail + 1) address else "${address.take(head)}…${address.takeLast(tail)}"
}
