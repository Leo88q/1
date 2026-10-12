package com.ampergrad.game.content

import androidx.compose.ui.graphics.Color

/**
 * Гардероб: что надето сейчас и как это выглядит на поле.
 *
 * Правило проекта (docs/04 §2): косметика меняет только вид. Поэтому скин —
 * это набор цветов, а не параметров: правила, время и награды не зависят от того,
 * что надето. Канонические цвета предметов лежат в каталоге (colors из
 * cosmetics.json), а соответствие «предмет → часть поля» описано здесь и
 * проверяется тестом.
 */
data class Wardrobe(
    val palette: CosmeticItem? = null,
    val theme: CosmeticItem? = null,
    val source: CosmeticItem? = null,
    /** Знак мастера: печать или плёнка, показывается в меню. */
    val mark: CosmeticItem? = null,
) {
    val isEmpty: Boolean get() = palette == null && theme == null && source == null && mark == null
}

/** Цвета игрового поля, выведенные из надетой косметики. */
data class BoardSkin(
    /** Цвет тока (живые сегменты). */
    val current: Color,
    /** Обесточенные сегменты. */
    val idle: Color,
    /** Ядро соединения. */
    val hub: Color,
    /** Шина питания (источник). */
    val source: Color,
    /** Ореол шины. */
    val sourceRing: Color,
    /** Линии сетки квартала. */
    val grid: Color,
    /** Фон поля. */
    val field: Color,
) {
    companion object {
        /** Вид по умолчанию — канонические цвета Амперграда. */
        val Default = BoardSkin(
            current = Color(0xFF00E5C7),
            idle = Color(0xFF26334A),
            hub = Color(0xFF16202F),
            source = Color(0xFFFFC24B),
            sourceRing = Color(0xFFFFC24B),
            grid = Color(0xFF1B2637),
            field = Color(0xFF070B13),
        )
    }
}

/**
 * Превращает гардероб в цвета поля.
 *
 * Каждый предмет красит свою часть квартала и ничего больше:
 *  • палитра  — живой ток (colors[0]) и его приглушённая подложка для обесточенных
 *               сегментов (colors[2], а если её нет — затемнённый цвет тока),
 *               яркое ядро соединения (colors[1]);
 *  • тема     — фон квартала (colors[2], иначе канонический фон) и линии сетки —
 *               цвет темы (colors[0]), подмешанный к фону, чтобы разметка оставалась тихой;
 *  • источник — шина питания (colors[0]) и её ореол (colors[1]).
 * Предметы без цветов (или отсутствующие) не меняют вид: поле остаётся каноническим.
 * Ни один цвет не влияет на правила, время и награды.
 */
fun BoardSkin.Companion.of(wardrobe: Wardrobe): BoardSkin {
    var skin = BoardSkin.Default
    wardrobe.palette?.let { item ->
        item.colorAt(0)?.let { skin = skin.copy(current = it) }
        val base = item.colorAt(2) ?: blend(skin.current, BoardSkin.Default.field, 0.72f)
        skin = skin.copy(idle = base, hub = item.colorAt(1) ?: skin.current)
    }
    wardrobe.theme?.let { item ->
        val field = item.colorAt(2) ?: BoardSkin.Default.field
        skin = skin.copy(field = field)
        item.colorAt(0)?.let { skin = skin.copy(grid = blend(field, it, 0.28f)) }
    }
    wardrobe.source?.let { item ->
        item.colorAt(0)?.let { skin = skin.copy(source = it) }
        item.colorAt(1)?.let { skin = skin.copy(sourceRing = it) }
    }
    return skin
}

/** Смешивает два цвета: часть = 0 — первый, часть = 1 — второй. */
fun blend(from: Color, to: Color, part: Float): Color {
    val t = part.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}

/**
 * Косметика применяется к полю только полностью — частичных состояний нет:
 * любой предмет надевается и снимается целиком.
 */
fun CosmeticItem.colorAt(index: Int): Color? {
    val hex = colors.getOrNull(index) ?: return null
    return hexToColorOrNull(hex)
}

/** Разбор #RRGGBB из каталога. Испорченный цвет не роняет игру — поле не меняется. */
fun hexToColorOrNull(hex: String): Color? {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return null
    val value = clean.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}
