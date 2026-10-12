package com.ampergrad.game

import com.ampergrad.game.content.BoardSkin
import com.ampergrad.game.content.EconomyRules
import com.ampergrad.game.content.Shop
import com.ampergrad.game.content.Wardrobe
import com.ampergrad.game.content.hexToColorOrNull
import com.ampergrad.game.content.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Экономика монет и гардероб: монеты зарабатываются только игрой, тратятся только
 * на вид, и надетый предмет влияет исключительно на цвета поля.
 */
class WardrobeTest {

    private val shop: Shop by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("cosmetics.json")
            ?: fail("не найден cosmetics.json — выполните npm run content")
        Shop.parse(stream.readBytes().decodeToString())
    }

    @Test
    fun `правила экономики совпадают с каноном`() {
        val economy = shop.economy
        assertEquals(20, economy.coinsPerSolve)
        assertEquals(10, economy.coinsPerStar)
        assertEquals(20, economy.dailyBonus)
        assertEquals(50, economy.coinsForSolve(3))
        assertEquals(30, economy.coinsForSolve(1))
        assertEquals(70, economy.coinsForSolve(3, daily = true))
        // Звёзды вне диапазона не ломают начисление.
        assertEquals(20, economy.coinsForSolve(-5))
        assertEquals(50, economy.coinsForSolve(99))
    }

    @Test
    fun `накоплений за кампанию хватает на самый дорогой предмет`() {
        val economy = shop.economy
        val campaign = (1..75).sumOf { economy.coinsForSolve(3) }
        val mostExpensive = shop.coins.maxOf { it.priceCoins ?: 0 }
        assertTrue(
            campaign >= mostExpensive * 1.5,
            "за кампанию $campaign монет, а предмет стоит $mostExpensive",
        )
        assertTrue(economy.canAfford(campaign, mostExpensive))
        assertFalse(economy.canAfford(mostExpensive - 1, mostExpensive))
    }

    @Test
    fun `за монеты продаётся только внешний вид, и только он покупается`() {
        for (item in shop.items) {
            // Покупные каналы: монеты (в игре) и витрина платформы (вне игры).
            if (item.channel == "reward" || item.channel == "battle-pass") {
                assertFalse(shop.coinPurchasable(item), "${item.id}: не продаётся за монеты")
                assertNull(item.priceCoins, "${item.id}: у награды не должно быть цены в монетах")
            }
            if (item.channel == "coins") {
                assertTrue(shop.coinPurchasable(item), "${item.id}: должен покупаться за монеты")
            }
        }
    }

    @Test
    fun `надетый предмет меняет цвета поля`() {
        val palette = assertNotNull(shop.byId("palette-mercury"))
        val theme = assertNotNull(shop.byId("theme-plateau"))
        val source = assertNotNull(shop.byId("source-gold"))
        val skin = BoardSkin.of(Wardrobe(palette = palette, theme = theme, source = source))

        assertEquals(hexToColorOrNull(palette.colors[0]), skin.current)
        assertEquals(hexToColorOrNull(palette.colors[1]), skin.idle)
        assertEquals(hexToColorOrNull(theme.colors[0]), skin.grid)
        assertEquals(hexToColorOrNull(theme.colors[1]), skin.field)
        assertEquals(hexToColorOrNull(source.colors[0]), skin.source)
    }

    @Test
    fun `без косметики поле выглядит канонически`() {
        assertEquals(BoardSkin.Default, BoardSkin.of(Wardrobe()))
        // Знак мастера (печать, плёнка) на поле не влияет — только в меню.
        val wing = assertNotNull(shop.byId("wing-klemma"))
        assertEquals(BoardSkin.Default, BoardSkin.of(Wardrobe(mark = wing)))
    }

    @Test
    fun `испорченный цвет не ломает вид`() {
        assertNull(hexToColorOrNull("не-цвет"))
        assertNull(hexToColorOrNull("#12"))
        assertNull(hexToColorOrNull(""))
        assertNotNull(hexToColorOrNull("#00E5C7"))
        assertNotNull(hexToColorOrNull("FFC24B"))
    }

    @Test
    fun `резервные правила экономики совпадают с каноном`() {
        // Если контент не попал в сборку, приложение считает монеты по этому резерву.
        val fallback = EconomyRules.Default
        assertEquals(shop.economy, fallback)
    }
}
