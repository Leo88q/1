import com.ampergrad.game.content.En
import com.ampergrad.game.content.Lang
import com.ampergrad.game.content.Ru
import com.ampergrad.game.content.Shop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Витрина сверяется с TS-каноном (файл cosmetics.json из `npm run content`).
 * Главное правило проекта: косметика не влияет на игру — здесь это проверяется.
 */
class ShopTest {

    private val shop: Shop by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("cosmetics.json")
            ?: fail(
                "Не найден cosmetics.json. Сгенерируйте контент:\n" +
                    "  npm run content\n" +
                    "  cp packages/engine/content/cosmetics.json app-android/app/src/test/resources/cosmetics.json",
            )
        Shop.parse(stream.readBytes().decodeToString())
    }

    @Test
    fun `витрина содержит все каналы получения`() {
        assertTrue(shop.items.size >= 18, "предметов: ${shop.items.size}")
        assertTrue(shop.rewards.size >= 6, "награды за игру")
        assertTrue(shop.coins.isNotEmpty(), "предметы за монеты")
        assertTrue(shop.store.isNotEmpty(), "предметы витрины")
        assertTrue(shop.seasonPass.isNotEmpty(), "Battle Pass")
    }

    @Test
    fun `награды за игру бесплатны, платные — только за вид`() {
        for (item in shop.rewards) {
            assertNull(item.priceUsd, "${item.id}: награда не продаётся")
            assertNull(item.priceCoins, "${item.id}: награда не стоит монет")
            assertNotNull(item.standard, "${item.id}: награда должна быть NFT")
            assertEquals("за игру", item.priceLabel(Ru))
        }
        for (item in shop.store) {
            val price = item.priceUsd
            assertNotNull(price, "${item.id}: у товара витрины нет цены")
            assertTrue(price > 0.0, "${item.id}: цена должна быть больше нуля")
        }
        for (item in shop.coins) {
            val coins = item.priceCoins
            assertNotNull(coins, "${item.id}: нет цены в монетах")
            assertTrue(coins > 0, "${item.id}: цена в монетах должна быть больше нуля")
        }
    }

    @Test
    fun `у каждого предмета есть лор и палитра`() {
        for (item in shop.items) {
            assertTrue(item.lore.length > 20, "${item.id}: короткий лор")
            assertTrue(item.colors.size >= 2, "${item.id}: нет палитры")
            assertTrue(item.how.isNotBlank(), "${item.id}: не сказано, как получить")
        }
    }

    @Test
    fun `карточка предмета переведена целиком`() {
        val cyrillic = Regex("[А-Яа-яЁё]")
        for (item in shop.items) {
            assertNotNull(item.nameEn, "${item.id}: нет английского названия")
            assertNotNull(item.howEn, "${item.id}: нет английского способа получения")
            assertNotNull(item.loreEn, "${item.id}: нет английского лора")
            // Русская карточка не меняется, английская — без кириллицы.
            assertEquals(item.name, item.name(Lang.RU), "${item.id}: русское название")
            assertEquals(item.lore, item.lore(Lang.RU), "${item.id}: русский лор")
            for (value in listOf(item.name(Lang.EN), item.how(Lang.EN), item.lore(Lang.EN))) {
                assertFalse(cyrillic.containsMatchIn(value), "${item.id}: «$value» не переведено")
            }
        }
    }

    @Test
    fun `метки редкости и цены переводятся`() {
        val store = shop.store.first()
        assertTrue(store.priceLabel(En).startsWith("$"), "цена витрины в долларах: ${store.priceLabel(En)}")
        assertTrue(ruCoinsExample().priceLabel(Ru).endsWith("монет"), "цена в монетах по-русски")
        assertTrue(ruCoinsExample().priceLabel(En).endsWith("coins"), "цена в монетах по-английски")
        assertEquals("LEGENDARY", legend().rarityLabel(En))
        assertEquals("ЛЕГЕНДАРНЫЙ", legend().rarityLabel(Ru))
    }

    private fun ruCoinsExample() = shop.coins.first()

    private fun legend() = shop.items.first { it.rarity == "legendary" }

    @Test
    fun `типы предметов — только косметика`() {
        val visual = setOf("palette", "theme", "wing", "source", "seal", "registry")
        for (item in shop.items) {
            assertTrue(item.type in visual, "${item.id}: тип ${item.type} может влиять на игру")
        }
    }
}
