import com.ampergrad.game.content.Lore
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Контент сверяется с TS-каноном: файл lore.json генерируется `npm run content`
 * и подкладывается в тестовые ресурсы (в CI это делает workflow).
 */
class LoreTest {

    private val lore: Lore by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("lore.json")
            ?: fail(
                "Не найден lore.json. Сгенерируйте контент:\n" +
                    "  npm run content\n" +
                    "  cp packages/engine/content/lore.json app-android/app/src/test/resources/lore.json",
            )
        val text = stream.readBytes().decodeToString()
        // Версия правил в контенте обязана присутствовать — по ней сервер сверяет клиента.
        assertTrue(JSONObject(text).has("engineVersion"), "в контенте нет engineVersion")
        Lore.parse(text)
    }

    @Test
    fun `контент содержит пять ярусов, 75 кварталов и 60 записей`() {
        assertEquals(5, lore.tiers.size)
        assertEquals(75, lore.quarters.size)
        assertEquals(60, lore.registry.size)
    }

    @Test
    fun `ярусы покрывают уровни 1-75 без дыр`() {
        val covered = mutableListOf<Int>()
        for (tier in lore.tiers) covered += tier.levels.toList()
        assertEquals((1..75).toList(), covered)
    }

    @Test
    fun `у каждого уровня есть квартал, а имя квартала не пустое`() {
        for (level in 1..75) {
            val quarter = assertNotNull(lore.quarter(level), "нет квартала для уровня $level")
            assertTrue(quarter.name.isNotBlank(), "пустое имя у ${quarter.code}")
            assertTrue(quarter.code.matches(Regex("^[ПРТДЯ]-\\d{2}$")), "код ${quarter.code}")
        }
    }

    @Test
    fun `реестр открывается по порядку и покрывает все 60 записей`() {
        val seen = mutableListOf<String>()
        for (level in 1..75) {
            val entry = assertNotNull(lore.registryForLevel(level), "нет записи для уровня $level")
            seen += entry.id
        }
        assertEquals("reg-s1-01", seen.first())
        assertEquals("reg-s3-20", seen.last())
        assertEquals(60, seen.toSet().size)
    }

    @Test
    fun `тексты реестра непустые и с указанием источника`() {
        for (entry in lore.registry) {
            assertTrue(entry.text.length > 30, "короткий текст у ${entry.id}")
            assertTrue(entry.source.isNotBlank(), "нет источника у ${entry.id}")
            assertTrue(entry.label.startsWith("РЕЕСТР "), "подпись ${entry.label}")
        }
        assertEquals("Клемма", lore.registryEntry("reg-s1-01")?.speaker)
    }
}
