package com.ampergrad.game.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale

/**
 * Фон экрана из арта: меню, победа, витрина.
 *
 * Сделан модификатором, а не обёрткой, чтобы не ломать иерархию экранов: фон
 * навешивается на уже существующий корень, а содержимое остаётся как было.
 *
 * Кадры нарисованы под стать городу — яркие, с граффити, — поэтому сверху идёт
 * затемнение: иначе подписи и кнопки на них не читаются. Цвет и сила затемнения
 * приходят снаружи, чтобы надетый игроком вид квартала был заметен и здесь.
 *
 * Если арт в сборку не попал, модификатор ничего не делает: экран остаётся на
 * своём обычном фоне, и это не ошибка.
 */
@Composable
fun Modifier.screenBackdrop(
    art: Art?,
    name: String,
    /** Насколько гасим фон: 0 — кадр как есть, 1 — совсем чёрный. */
    dim: Float = 0.74f,
    tint: Color = Color(0xFF06080E),
): Modifier {
    val image = art?.screen(name) ?: return this
    val painter = remember(image) { BitmapPainter(image) }
    return this
        .paint(painter = painter, contentScale = ContentScale.Crop)
        // Затемнение — отдельным слоем поверх кадра: сам кадр не трогаем.
        .background(tint.copy(alpha = dim.coerceIn(0f, 1f)))
}
