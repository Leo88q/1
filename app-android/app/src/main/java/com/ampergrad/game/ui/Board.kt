package com.ampergrad.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ampergrad.game.engine.E
import com.ampergrad.game.engine.Level
import com.ampergrad.game.engine.N
import com.ampergrad.game.engine.Role
import com.ampergrad.game.engine.S
import com.ampergrad.game.engine.W
import com.ampergrad.game.engine.tileMask
import com.ampergrad.game.content.BoardSkin
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Игровое поле: магистрали квартала.
 *
 * Управление: тап — поворот по часовой, долгое нажатие — против часовой.
 * (На Seeker то же самое делает тап; долгое нажатие оставлено как «доводка».)
 */
@Composable
fun Board(
    level: Level,
    powered: BooleanArray,
    accent: Color,
    skin: BoardSkin,
    hintIndex: Int?,
    solving: Boolean,
    // Арт поля. Если он не приехал (`art == null`) или не загрузился, рисуется
    // схема: правила видны одинаково, отличается только картинка.
    art: Art? = null,
    artTier: ArtTier? = null,
    background: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    onRotate: (index: Int, dir: Int) -> Unit,
) {
    val artTiles = if (art != null && art.ready && artTier != null) art to artTier else null
    // Визуальные углы (в четвертях оборота) с доводкой к логическому повороту.
    val angles = remember(level) { level.tiles.map { it.rot.toFloat() }.toMutableStateList() }
    var revision by remember(level) { mutableIntStateOf(0) }
    var burstFrames by remember(level) { mutableIntStateOf(0) }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse-value",
    )

    // Анимация доводки: запускается при каждом повороте и завершается сама.
    LaunchedEffect(level, revision) {
        var guard = 0
        while (guard < 240) {
            guard++
            var moving = false
            withFrameNanos { }
            for (i in angles.indices) {
                val target = level.tiles[i].rot.toFloat()
                var diff = ((target - angles[i]) % 4f + 4f) % 4f
                if (diff > 2f) diff -= 4f
                if (abs(diff) > 0.02f) {
                    angles[i] = angles[i] + diff * 0.3f
                    moving = true
                } else if (angles[i] != target) {
                    angles[i] = target
                    moving = true
                }
            }
            if (!moving) break
        }
    }

    // Волна пломбы: короткая анимация после победы.
    LaunchedEffect(level, solving) {
        if (!solving) return@LaunchedEffect
        while (burstFrames < 90) {
            withFrameNanos { }
            burstFrames++
        }
    }

    Canvas(
        modifier = modifier.pointerInput(level) {
            detectTapGestures(
                onTap = { offset ->
                    cellAt(offset, size, level.size)?.let { index ->
                        onRotate(index, 1)
                        revision++
                    }
                },
                onLongPress = { offset ->
                    cellAt(offset, size, level.size)?.let { index ->
                        onRotate(index, -1)
                        revision++
                    }
                },
            )
        },
    ) {
        val gridSize = level.size
        val cell = min(size.width, size.height) / gridSize
        val ox = (size.width - cell * gridSize) / 2f
        val oy = (size.height - cell * gridSize) / 2f
        val burst = if (solving) min(1f, burstFrames / 60f) else 0f

        if (artTiles != null) {
            drawArtBackground(background, cell * gridSize, ox, oy, cell, size)
        }
        drawGrid(ox, oy, cell, gridSize, skin, withArt = artTiles != null)

        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val index = y * gridSize + x
                val tile = level.tiles[index]
                val cx = ox + x * cell + cell / 2f
                val cy = oy + y * cell + cell / 2f

                // Корпус ячейки.
                val body = cell * 0.42f
                drawRoundRect(
                    color = if (tile.role == Role.BUS) accent.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.015f),
                    topLeft = Offset(cx - body, cy - body),
                    size = Size(body * 2, body * 2),
                    cornerRadius = CornerRadius(cell * 0.16f),
                )

                if (artTiles != null) {
                    val (artPack, tier) = artTiles
                    val setName = setForCell(tier.sets, x, y, index)
                    drawArtTile(artPack, level, index, setName, cx, cy, cell, angles[index])
                    if (powered[index]) {
                        drawArtFlow(level, index, cx, cy, cell, angles[index], tier.flow, pulse)
                    }
                    if (tile.role == Role.RECEIVER) {
                        drawArtReceiver(artPack, level, index, cx, cy, cell, powered[index], tier.flow, pulse)
                    }
                    if (tile.role == Role.BUS) drawArtBus(artPack, cx, cy, cell, accent, pulse)
                } else {
                    drawTile(level, index, cx, cy, cell, angles[index], powered[index], burst, skin)
                    if (tile.role == Role.BUS) drawBus(cx, cy, cell, accent, skin, pulse)
                    if (tile.role == Role.RECEIVER) drawReceiver(cx, cy, cell, powered[index])
                }

                if (hintIndex == index) {
                    drawCircle(
                        color = Grad.Danger.copy(alpha = 0.35f + 0.35f * kotlin.math.sin(pulse * 2 * Math.PI).toFloat()),
                        radius = cell * 0.46f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(
    ox: Float,
    oy: Float,
    cell: Float,
    gridSize: Int,
    skin: BoardSkin,
    withArt: Boolean = false,
) {
    val line = skin.grid.copy(alpha = if (withArt) 0.12f else 0.35f)
    for (i in 0..gridSize) {
        val x = ox + i * cell
        drawLine(line, Offset(x, oy), Offset(x, oy + gridSize * cell), strokeWidth = 1f)
        val y = oy + i * cell
        drawLine(line, Offset(ox, y), Offset(ox + gridSize * cell, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawTile(
    level: Level,
    index: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    angle: Float,
    on: Boolean,
    burst: Float,
    skin: BoardSkin,
) {
    val tile = level.tiles[index]
    val len = cell * 0.5f
    val width = maxOf(4f, cell * 0.12f)
    val color = if (on) skin.current else skin.idle
    val glow = if (on) 1f + burst else 0f

    rotate(degrees = angle * 90f, pivot = Offset(cx, cy)) {
        val ports = listOf(N to -90f, E to 0f, S to 90f, W to 180f)
        for ((bit, degrees) in ports) {
            if (tile.base and bit == 0) continue
            val rad = degrees * (Math.PI / 180f).toFloat()
            val start = Offset(cx, cy)
            val end = Offset(cx + cos(rad) * len, cy + sin(rad) * len)
            if (glow > 0f) {
                drawLine(color.copy(alpha = 0.14f * glow), start, end, strokeWidth = width * 3.2f, cap = StrokeCap.Round)
                drawLine(color.copy(alpha = 0.32f * glow), start, end, strokeWidth = width * 1.9f, cap = StrokeCap.Round)
            }
            drawLine(color, start, end, strokeWidth = width, cap = StrokeCap.Round)
        }
        // Ядро соединения.
        drawCircle(if (on) Color(0xFFEAFFF9) else skin.hub, radius = width * 0.62f, center = Offset(cx, cy))
    }
}

private fun DrawScope.drawBus(cx: Float, cy: Float, cell: Float, accent: Color, skin: BoardSkin, pulse: Float) {
    drawCircle(skin.sourceRing.copy(alpha = 0.25f + 0.35f * pulse), radius = cell * 0.3f, center = Offset(cx, cy), style = Stroke(width = 2f))
    val start = -90f
    val sweep = 360f * pulse
    drawArc(
        color = skin.source,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(cx - cell * 0.2f, cy - cell * 0.2f),
        size = Size(cell * 0.4f, cell * 0.4f),
        style = Stroke(width = 3f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawReceiver(cx: Float, cy: Float, cell: Float, on: Boolean) {
    val d = cell * 0.3f
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        drawRect(
            color = if (on) Grad.Gold else Grad.Metal,
            topLeft = Offset(cx - d, cy - d),
            size = Size(d * 2, d * 2),
            style = Stroke(width = 2.4f),
        )
    }
}

/**
 * Фон яруса под полем: растягиваем по ширине, центрируем и гасим затемнением —
 * иначе трубы теряются на светлых участках кадра.
 */
private fun DrawScope.drawArtBackground(
    image: ImageBitmap?,
    board: Float,
    ox: Float,
    oy: Float,
    cell: Float,
    area: Size,
) {
    val top = oy - cell * 0.1f
    val height = board + cell * 0.2f
    if (image != null) {
        val scale = maxOf(area.width / image.width, height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(((area.width - w) / 2f).toInt(), (top + (height - h) / 2f).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
        )
    }
    val plate = cell * 0.16f
    drawRoundRect(
        color = Color(0x0F0A090D),
        topLeft = Offset(ox - plate / 2f, top),
        size = Size(board + plate, height),
        cornerRadius = CornerRadius(plate),
    )
}

/** Деталь трубы из арта: спрайт нарисован в положении своей маски, поэтому его
 *  поворот равен повороту маски фрагмента — детали соседей стыкуются. */
private fun DrawScope.drawArtTile(
    art: Art,
    level: Level,
    index: Int,
    setName: String,
    cx: Float,
    cy: Float,
    cell: Float,
    angle: Float,
) {
    val tile = level.tiles[index]
    val piece = piecesForMask(tile.base).firstOrNull() ?: "straight"
    val image = art.sprite(setName, piece) ?: art.sprite("base", piece) ?: return
    // Поворот вокруг центра ячейки на угол фрагмента: спрайт нарисован в положении
    // своей маски, поэтому поворот на угол маски и есть стыковка с соседями.
    // Без scale/rotate-блоков: прямоугольник назначения задаём сами.
    val span = cell.toInt()
    val half = span / 2
    withTransform({
        translate(cx, cy)
        rotate(angle * 90f, Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(-half, -half),
            dstSize = IntSize(span, span),
        )
    }
}

/** Ток: цвет среды яруса по портам фрагмента — мягкая полоса и бегущий пунктир. */
private fun DrawScope.drawArtFlow(
    level: Level,
    index: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    angle: Float,
    flow: Color,
    phase: Float,
) {
    val tile = level.tiles[index]
    rotate(degrees = angle * 90f, pivot = Offset(cx, cy)) {
        val ports = listOf(N to -90f, E to 0f, S to 90f, W to 180f)
        for ((bit, degrees) in ports) {
            if (tile.base and bit == 0) continue
            val rad = degrees * (Math.PI / 180f).toFloat()
            val start = Offset(cx, cy)
            val end = Offset(cx + cos(rad) * cell * 0.5f, cy + sin(rad) * cell * 0.5f)
            drawLine(flow.copy(alpha = 0.22f), start, end, strokeWidth = cell * 0.13f, cap = StrokeCap.Round)
            drawLine(
                color = flow.copy(alpha = 0.85f),
                start = start,
                end = end,
                strokeWidth = cell * 0.05f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.34f), -phase * cell * 0.9f),
            )
        }
        drawCircle(Color.White.copy(alpha = 0.55f), radius = cell * 0.055f, center = Offset(cx, cy))
    }
}

/** Приёмник: горловина открытого конца, а когда ток дошёл — струя по её порту. */
private fun DrawScope.drawArtReceiver(
    art: Art,
    level: Level,
    index: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    on: Boolean,
    flow: Color,
    phase: Float,
) {
    val mouth = art.round("mouth")
    val mask = tileMask(level, index)
    val spin = when {
        mask and N != 0 -> 180f
        mask and E != 0 -> -90f
        mask and W != 0 -> 90f
        else -> 0f
    }
    // Горловина: тот же прямоугольник назначения, что в вебе (−0.47…0.47 клетки),
    // поэтому приёмник встаёт на место без подгонки масштаба.
    val half = cell * 0.47f
    withTransform({
        translate(cx, cy)
        rotate(spin, Offset.Zero)
    }) {
        if (mouth != null) {
            drawImage(
                image = mouth,
                srcOffset = IntOffset(0, 0),
                srcSize = IntSize(mouth.width, mouth.height),
                dstOffset = IntOffset((-half).toInt(), (-half).toInt()),
                dstSize = IntSize((half * 2f).toInt(), (half * 2f).toInt()),
            )
        }
        if (on) {
            drawLine(
                color = flow.copy(alpha = 0.7f),
                start = Offset(0f, cell * 0.1f),
                end = Offset(0f, -cell * 0.52f),
                strokeWidth = cell * 0.09f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.08f, cell * 0.26f), -phase * cell * 1.4f),
            )
        }
    }
}

/** Шина: колпак источника поверх детали и пульсирующее кольцо. */
private fun DrawScope.drawArtBus(
    art: Art,
    cx: Float,
    cy: Float,
    cell: Float,
    accent: Color,
    phase: Float,
) {
    art.round("cap")?.let { cap ->
        val half = cell * 0.43f
        drawImage(
            image = cap,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(cap.width, cap.height),
            dstOffset = IntOffset((cx - half).toInt(), (cy - half).toInt()),
            dstSize = IntSize((half * 2f).toInt(), (half * 2f).toInt()),
        )
    }
    drawCircle(
        color = accent.copy(alpha = 0.25f + 0.35f * phase),
        radius = cell * 0.34f,
        center = Offset(cx, cy),
        style = Stroke(width = 2f),
    )
}

private fun cellAt(offset: Offset, area: IntSize, gridSize: Int): Int? {
    val cell = min(area.width, area.height).toFloat() / gridSize
    if (cell <= 0f) return null
    val ox = (area.width - cell * gridSize) / 2f
    val oy = (area.height - cell * gridSize) / 2f
    val gx = ((offset.x - ox) / cell).toInt()
    val gy = ((offset.y - oy) / cell).toInt()
    if (gx !in 0 until gridSize || gy !in 0 until gridSize) return null
    return gy * gridSize + gx
}
