package com.ampergrad.game

import com.ampergrad.game.content.En
import com.ampergrad.game.content.Lang
import com.ampergrad.game.content.Ru
import com.ampergrad.game.content.UiStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Полнота переводов интерфейса.
 *
 * Интерфейс объявлен одним интерфейсом `UiStrings`, поэтому пропущенный ключ —
 * ошибка компиляции. Тест ловит то, что компилятор не видит: английский текст,
 * оставшийся русским, и пустые строки.
 */
class UiStringsTest {

    private val cyrillic = Regex("[А-Яа-яЁё]")

    /** Все строковые свойства перевода (геттеры без параметров). */
    private fun values(t: UiStrings): Map<String, String> =
        t::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get") to (it.invoke(t) as String) }

    @Test
    fun `английский интерфейс не содержит кириллицы`() {
        val offenders = values(En).filter { (_, value) -> cyrillic.containsMatchIn(value) }
        assertTrue(offenders.isEmpty(), "не переведено: ${offenders.keys}")
    }

    @Test
    fun `русский интерфейс действительно русский`() {
        val translated = values(Ru).count { (_, value) -> cyrillic.containsMatchIn(value) }
        // Часть подписей намеренно латиницей (MAINNET, BATTLE PASS, имена вроде «cNFT»).
        assertTrue(translated > 40, "по-русски переведено всего $translated строк")
    }

    @Test
    fun `ни один ключ не пустой`() {
        for (t in listOf(Ru, En)) {
            val empty = values(t).filter { (_, value) -> value.isBlank() }
            assertTrue(empty.isEmpty(), "${t.lang}: пустые строки ${empty.keys}")
        }
    }

    @Test
    fun `параметрические строки собираются на обоих языках`() {
        for (t in listOf(Ru, En)) {
            assertTrue(t.level(7).contains("7"), "${t.lang}: номер уровня потерялся")
            assertTrue(t.startShift(12).contains("12"), "${t.lang}: номер уровня в кнопке")
            assertTrue(t.winStats(19, 19, "01:00", 0).contains("19"), "${t.lang}: статистика победы")
            assertTrue(t.boardRank(3, 10).contains("3"), "${t.lang}: место в рейтинге")
            assertTrue(t.registryProgress(5, 60).contains("5"), "${t.lang}: прогресс реестра")
            assertTrue(t.priceCoins(900).contains("900"), "${t.lang}: цена в монетах")
            assertTrue(t.priceLabel(1.99, null, "store").contains("1.99"), "${t.lang}: цена витрины")
            assertTrue(t.priceLabel(null, 900, "coins").contains("900"), "${t.lang}: монеты")
            assertTrue(t.priceLabel(null, null, "reward").isNotBlank(), "${t.lang}: награда за игру")
            for (order in 1..5) {
                assertTrue(t.tierName(order).isNotBlank(), "${t.lang}: ярус $order без названия")
            }
        }
    }

    @Test
    fun `язык выбирается по системе и явно`() {
        assertEquals(Lang.EN, Lang.of("en"))
        assertEquals(Lang.RU, Lang.of("ru"))
        assertEquals(Lang.RU, Lang.of(null))
        assertEquals(En, UiStrings.of(Lang.EN))
        assertEquals(Ru, UiStrings.of(Lang.RU))
    }

    @Test
    fun `названия ярусов на английском отличаются от русских`() {
        assertFalse(cyrillic.containsMatchIn(En.tierName(1)))
        assertTrue(cyrillic.containsMatchIn(Ru.tierName(1)))
    }
}
