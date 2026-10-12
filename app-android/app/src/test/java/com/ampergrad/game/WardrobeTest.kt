package com.ampergrad.game

import androidx.compose.ui.graphics.Color
import com.ampergrad.game.content.BoardSkin
import com.ampergrad.game.content.CosmeticItem
import com.ampergrad.game.content.blend
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

        // Палитра: ток, ядро и тёмная подложка для обесточенных сегментов.
        assertEquals(hexToColorOrNull(palette.colors[0]), skin.current)
        assertEquals(hexToColorOrNull(palette.colors[1]), skin.hub)
        assertEquals(hexToColorOrNull(palette.colors[2]), skin.idle)
        assertTrue(luminance(skin.idle) < luminance(skin.current), "подложка должна быть тише тока")
        // Тема: фон квартала как есть, сетка — тихая примесь акцента к фону.
        assertEquals(hexToColorOrNull(theme.colors[2]), skin.field)
        assertEquals(
            blend(skin.field, assertNotNull(hexToColorOrNull(theme.colors[0])), 0.28f),
            skin.grid,
        )
        assertTrue(skin.grid != skin.field, "тема должна быть видна на поле")
        // Источник: шина и её ореол.
        assertEquals(hexToColorOrNull(source.colors[0]), skin.source)
        assertEquals(hexToColorOrNull(source.colors[1]), skin.sourceRing)
    }

    @Test
    fun `предмет из двух цветов не спорит с каноном`() {
        // В каталоге попадаются предметы без третьего (тёмного) цвета — поле не должно пострадать.
        val palette = CosmeticItem(
            id = "check-palette",
            name = "Проверочная палитра",
            type = "palette",
            rarity = "rare",
            channel = "coins",
            priceUsd = null,
            priceCoins = 100,
            how = "тест",
            lore = "тест",
            colors = listOf("#00E5C7", "#FFC24B"),
            standard = "cNFT",
        )
        val theme = CosmeticItem(
            id = "check-theme",
            name = "Проверочная тема",
            type = "theme",
            rarity = "rare",
            channel = "coins",
            priceUsd = null,
            priceCoins = 100,
            how = "тест",
            lore = "тест",
            colors = listOf("#FF3D81", "#9AA7FF"),
            standard = "cNFT",
        )
        val skin = BoardSkin.of(Wardrobe(palette = palette, theme = theme))
        assertEquals(hexToColorOrNull("#00E5C7"), skin.current)
        assertEquals(hexToColorOrNull("#FFC24B"), skin.hub)
        assertEquals(blend(skin.current, BoardSkin.Default.field, 0.72f), skin.idle)
        assertTrue(luminance(skin.idle) < luminance(skin.current), "подложка темнее тока")
        // Тёмной основы у темы нет — фон остаётся каноническим, сетка берёт цвет темы.
        assertEquals(BoardSkin.Default.field, skin.field)
        assertEquals(blend(BoardSkin.Default.field, assertNotNull(hexToColorOrNull("#FF3D81")), 0.28f), skin.grid)
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

    /** Светимость цвета: нужна, чтобы проверить «тихость» подложки и сетки. */
    private fun luminance(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    @Test
    fun `резервные правила экономики совпадают с каноном`() {
        // Если контент не попал в сборку, приложение считает монеты по этому резерву.
        val fallback = EconomyRules.Default
        assertEquals(shop.economy, fallback)
    }
}
