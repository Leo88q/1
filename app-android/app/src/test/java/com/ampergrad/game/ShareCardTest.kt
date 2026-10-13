package com.ampergrad.game

import com.ampergrad.game.ui.CARD_HEIGHT
import com.ampergrad.game.ui.CARD_WIDTH
import com.ampergrad.game.ui.shareCardLayout
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раскладка карточки квартала.
 *
 * Картинку рисует устройство (там нужна канва), а геометрия — помещается ли
 * поле, не наезжает ли подвал, целая ли клетка — считается чистой функцией,
 * поэтому проверяется здесь: иначе такую ошибку заметили бы только глазами
 * на телефоне, когда карточка уже ушла в мессенджер.
 */
class ShareCardTest {

    /** Все размеры полей игры: ярусы ходят от 4×4 до 9×9. */
    private val sizes = 4..9

    @Test
    fun `поле вписано в карточку на любом размере квартала`() {
        for (size in sizes) {
            val layout = shareCardLayout(size)
            assertTrue(layout.board > 0f, "поле $size×$size нулевое")
            assertTrue(layout.boardLeft >= 0f, "поле $size×$size вылезло слева")
            assertTrue(layout.boardLeft + layout.board <= layout.width, "поле $size×$size вылезло справа")
            assertTrue(layout.boardTop + layout.board <= layout.height, "поле $size×$size вылезло снизу")
        }
    }

    @Test
    fun `сторона клетки целая — детали труб стыкуются`() {
        for (size in sizes) {
            val layout = shareCardLayout(size)
            assertEquals(floor(layout.cell), layout.cell, "клетка $size×$size не целая")
            assertEquals(layout.cell * size, layout.board, "поле $size×$size не кратно клетке")
        }
    }

    @Test
    fun `подписи идут сверху вниз и не наезжают друг на друга`() {
        val layout = shareCardLayout(7)
        val order = listOf(
            layout.titleBaseline,
            layout.subtitleBaseline,
            layout.boardTop,
            layout.boardTop + layout.board,
            layout.starsBaseline,
            layout.statsBaseline,
            layout.dividerY,
            layout.footerBaseline,
        )
        assertEquals(order.sorted(), order, "подписи карточки перепутаны")
        for (size in sizes) {
            val l = shareCardLayout(size)
            assertTrue(l.footerBaseline < l.height - 40f, "подвал $size×$size уехал за край")
            assertTrue(l.dividerY < l.footerBaseline, "линия $size×$size ниже подвала")
        }
    }

    @Test
    fun `пломба лежит в шапке и не перекрывает поле`() {
        for (size in sizes) {
            val layout = shareCardLayout(size)
            assertTrue(layout.sealTop >= 0f, "пломба $size×$size выше карточки")
            assertTrue(layout.sealTop + layout.sealSize <= layout.boardTop, "пломба $size×$size наехала на поле")
            assertTrue(layout.sealLeft + layout.sealSize <= layout.width, "пломба $size×$size за правым краем")
        }
    }

    @Test
    fun `карточка 3 на 4 — её не обрежет предпросмотр мессенджера`() {
        assertEquals(1080, CARD_WIDTH)
        assertEquals(1440, CARD_HEIGHT)
    }
}
