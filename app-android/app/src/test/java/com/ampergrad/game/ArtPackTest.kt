package com.ampergrad.game

import androidx.compose.ui.graphics.Color
import com.ampergrad.game.ui.Art
import com.ampergrad.game.ui.ArtPack
import com.ampergrad.game.ui.artTierIndex
import com.ampergrad.game.ui.packIsUsable
import com.ampergrad.game.ui.colorOfHex
import com.ampergrad.game.ui.piecesForMask
import com.ampergrad.game.ui.rewardForStars
import com.ampergrad.game.ui.setForCell
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.test.assertTrue

/**
 * Контракт арта на стороне Android: `assets/art/web.json` (тот же файл, что читает
 * веб-лаборатория) и правила выбора детали под маску.
 *
 * Проверяем не «файл разобрался», а то, что игра увидит на поле: под каждую маску
 * канона (1, 3, 5, 7, 15) есть спрайт, у каждого яруса есть два фона, а у награды
 * за 1–3 звезды — свой медальон. Если арт-каталог собран (`syncContent`), сверяем
 * ещё и наличие файлов на диске.
 */
class ArtPackTest {

    private val pack: ArtPack by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("web.json")
            ?: fail(
                "Не найден web.json. Соберите арт и подложите контракт:\n" +
                    "  npm run art:web\n" +
                    "  cp packages/lab/public/art/web.json app-android/app/src/test/resources/web.json",
            )
        Art.parse(stream.readBytes().decodeToString())
    }

    private val masksOfKind = mapOf("stub" to 1, "straight" to 5, "elbow" to 3, "tee" to 7, "cross" to 15)

    /**
     * Все файлы контракта: по ним проверяем, что арт «подъемный» — то есть
     * декодировано всё, без чего поле окажется пустым при рабочем web.json.
     */
    private fun allFiles(): Set<String> {
        val files = HashSet<String>()
        for (pieces in pack.sets.values) for (ref in pieces.values) files += ref.file
        files += pack.round.values
        files += pack.rewards.values
        return files
    }

    @Test
    fun `арт рабочий, только если декодирован канонный набор и круглые детали`() {
        val files = allFiles()
        assertTrue(packIsUsable(pack, files), "при полном наборе файлов арт обязан рисоваться")
        assertFalse(
            packIsUsable(pack, files - requireNotNull(pack.round["mouth"])),
            "без приёмника поле пустое — арт не должен считаться рабочим",
        )
        val base = requireNotNull(pack.sets["base"])
        val piece = requireNotNull(base["elbow"])
        assertFalse(
            packIsUsable(pack, files - piece.file),
            "без детали канонного набора арт нерабочий: поле было бы пустым",
        )
        assertFalse(packIsUsable(pack, emptySet()), "ничего не декодировалось — рисуем схему")
    }

    @Test
    fun `в контракте пять ярусов, у каждого два фона и цвет среды`() {
        assertEquals(5, pack.tiers.size, "ярусов должно быть пять — по 15 уровней на ярус")
        assertEquals(
            // Порядок и состав — как в каноне лора: пятый ярус «Плавильня», а не
            // «Жилы массива» (под именем из арта HUD расходился бы с реестром).
            listOf("plateau", "mercury", "terminal", "old-diode", "melter"),
            pack.tiers.map { it.id },
            "порядок ярусов задаёт и фон, и металл поля",
        )
        for (tier in pack.tiers) {
            assertEquals(2, tier.bg.size, "у яруса ${tier.id} должно быть два кадра фона (a и b)")
            assertTrue(tier.sets.isNotEmpty(), "у яруса ${tier.id} нет наборов металла")
            assertTrue(
                colorOfHex(tier.flowHex) != Color.Transparent,
                "цвет среды яруса ${tier.id} не разобрался: ${tier.flowHex}",
            )
            for (set in tier.sets) {
                assertTrue(pack.sets.containsKey(set), "набор $set из яруса ${tier.id} отсутствует в контракте")
            }
        }
    }

    @Test
    fun `под каждую маску канона есть спрайт в каждом наборе`() {
        for ((set, pieces) in pack.sets) {
            for ((piece, mask) in masksOfKind) {
                val ref = assertNotNull(pieces[piece], "в наборе $set нет детали $piece")
                assertEquals(mask, ref.mask, "у $set/$piece должна быть маска $mask")
            }
        }
        // Круглые детали: приёмник (открытый конец) и шина (колпак).
        for (name in listOf("mouth", "cap")) {
            assertTrue(pack.round.containsKey(name), "в контракте нет круглой детали $name")
        }
    }

    @Test
    fun `у награды за каждое число звёзд есть свой медальон`() {
        for (stars in 1..3) {
            assertTrue(pack.rewards.containsKey(stars), "нет пломбы за $stars звезды")
            assertEquals(stars, rewardForStars(stars))
        }
        assertEquals(1, rewardForStars(0), "без звёзд пломба тоже должна быть")
        assertEquals(3, rewardForStars(9), "звёзд больше трёх не бывает — считаем максимум")
    }

    @Test
    fun `деталь подбирается по числу портов маски`() {
        assertEquals(listOf("stub"), piecesForMask(1), "один порт — тупик")
        assertEquals(listOf("stub"), piecesForMask(8), "тупик поворачивается вместе с маской")
        assertEquals(listOf("straight", "straight2"), piecesForMask(5), "прямая север-юг")
        assertEquals(
            listOf("straight", "straight2"),
            piecesForMask(10),
            "прямая восток-запад — та же деталь, другой поворот",
        )
        assertEquals(listOf("elbow"), piecesForMask(3), "два порта под углом — отвод")
        assertEquals(listOf("elbow"), piecesForMask(12), "отвод в другом повороте")
        assertEquals(listOf("tee"), piecesForMask(7), "три порта — тройник")
        assertEquals(listOf("tee"), piecesForMask(14), "тройник в другом повороте")
        assertEquals(listOf("cross"), piecesForMask(15), "четыре порта — крест")
    }

    @Test
    fun `материал ячейки берётся из наборов яруса`() {
        val sets = listOf("zinc", "zinc2")
        val seen = (0 until 16).map { setForCell(sets, it % 4, it / 4, it) }.toSet()
        assertEquals(setOf("zinc", "zinc2"), seen, "оба металла яруса должны встречаться на поле")
        assertEquals("chrome", setForCell(listOf("chrome"), 3, 3, 11), "единственный набор — всегда он")
        assertEquals("base", setForCell(emptyList(), 0, 0, 0), "без наборов остаётся канон")
    }

    @Test
    fun `номер яруса считается по номеру уровня`() {
        assertEquals(0, artTierIndex(1, 5))
        assertEquals(0, artTierIndex(15, 5), "15-й уровень ещё Плато")
        assertEquals(1, artTierIndex(16, 5), "16-й открывает Ртутный ярус")
        assertEquals(4, artTierIndex(75, 5), "75-й — последний квартал Жил")
        assertEquals(0, artTierIndex(0, 5), "нулевой уровень не должен падать")
        assertEquals(4, artTierIndex(999, 5), "за пределами канона берём последний ярус")
    }

    @Test
    fun `цвет среды яруса из палитры Guttercaps`() {
        val flows = pack.tiers.associate { it.id to it.flowHex }
        assertEquals("#B6FF3C", flows["plateau"], "acid")
        assertEquals("#16E5D9", flows["mercury"], "cyan")
        assertEquals("#FF7A1A", flows["terminal"], "orange")
        // Пятый ярус — тот же acid: два яруса одного цвета допустимы, цвет среды
        // подсказывает материал, а не номер.
        assertEquals("#B6FF3C", flows["melter"], "acid")
        for (tier in pack.tiers) {
            assertTrue(
                colorOfHex(tier.flowHex) != Color.Transparent,
                "ярус ${tier.id}: цвет ${tier.flowHex} не разобрался",
            )
        }
    }

    @Test
    fun `цвет разбирается из канона палитры`() {
        assertEquals(Color(0xFFB6FF3C), colorOfHex("#B6FF3C"), "acid")
        assertEquals(Color(0xFFFF2E8A), colorOfHex("#FF2E8A"), "magenta")
        assertEquals(Color.Transparent, colorOfHex("нет цвета"), "мусор не должен ломать рендер")
        assertEquals(Color.Transparent, colorOfHex("#ABC"), "короткая запись не поддерживается")
    }

    @Test
    fun `файлы спрайтов на месте, если арт собран`() {
        val assets = listOf(
            File("src/main/assets/art"),
            File("app/src/main/assets/art"),
        ).firstOrNull { it.isDirectory } ?: return // арт не собран — игра стартует схемой
        val missing = ArrayList<String>()
        for (pieces in pack.sets.values) {
            for (ref in pieces.values) if (!File(assets, ref.file).isFile) missing += ref.file
        }
        for (file in pack.round.values) if (!File(assets, file).isFile) missing += file
        for (file in pack.rewards.values) if (!File(assets, file).isFile) missing += file
        for (tier in pack.tiers) {
            for (bg in tier.bg) if (!File(assets, bg).isFile) missing += bg
        }
        assertTrue(missing.isEmpty(), "в assets/art нет файлов: ${missing.take(5)}")
    }
}
