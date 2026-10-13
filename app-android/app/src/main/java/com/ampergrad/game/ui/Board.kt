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
import com.ampergrad.game.engine.Tile
import com.ampergrad.game.engine.TileKind
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
    // Цвет среды из косметики (палитра игрока), если она надета. Без него
    // купленная палитра была бы видна только в схеме — а арт включён по умолчанию.
    artFlow: Color? = null,
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
        val flowDirs = if (artTiles != null) flowDirections(level, powered) else emptyMap()

        if (artTiles != null) {
            drawArtBackground(background, cell * gridSize, ox, oy, cell, size, skin.field)
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
                    val flow = artFlow ?: tier.flow
                    // Плита-указатель кладётся под трубу: видно, куда течёт среда.
                    val arrow = flowDirs[index]?.let(::arrowFor)
                    if (arrow != null) {
                        artPack.prop(arrow)?.let { plate ->
                            drawAt(plate, cx, cy, cell, alpha = ARROW_ALPHA)
                        }
                    }
                    val setName = setForCell(tier.sets, x, y, index)
                    drawArtTile(artPack, level, index, setName, cx, cy, cell, angles[index])
                    if (powered[index]) {
                        drawArtFlow(level, index, cx, cy, cell, angles[index], flow, pulse)
                    }
                    if (tile.role == Role.RECEIVER) {
                        drawArtReceiver(artPack, level, index, cx, cy, cell, powered[index], flow, pulse)
                    }
                    // Шина красится «источником» из гардероба (канон — золото),
                    // поэтому покупка источника видна и поверх арта.
                    if (tile.role == Role.BUS) drawArtBus(artPack, cx, cy, cell, skin, pulse)
                    // Служебная деталь: помечаем то, что игрок иначе не прочитает, —
                    // неповоротную клетку, тупик, шину, собранный стык.
                    val decor = decorForCell(tile)
                    if (decor != null) {
                        artPack.prop(decor)?.let { part ->
                            drawAt(part, cx, cy, cell * decorScale(decor))
                        }
                    }
                } else {
                    drawTile(level, index, cx, cy, cell, angles[index], powered[index], burst, skin)
                    if (tile.role == Role.BUS) drawBus(cx, cy, cell, accent, skin, pulse)
                    if (tile.role == Role.RECEIVER) drawReceiver(cx, cy, cell, powered[index], accent)
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

private fun DrawScope.drawReceiver(cx: Float, cy: Float, cell: Float, on: Boolean, accent: Color) {
    val d = cell * 0.3f
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        drawRect(
            // Под током — цвет среды квартала, без тока — оранжевый «внимание».
            color = if (on) accent else Grad.Warn,
            topLeft = Offset(cx - d, cy - d),
            size = Size(d * 2, d * 2),
            style = Stroke(width = 2.4f),
        )
    }
}

/**
 * Какая служебная деталь лежит на клетке поверх трубы.
 *
 * Особенные клетки должны читаться сразу: неповоротную игрок иначе долбит
 * впустую, тупик без заглушки выглядит недоделкой, а шина без маховика — просто
 * трубой. Правила те же, что в веб-лаборатории (decorForCell в main.ts),
 * иначе поле в приложении и в вебе выглядело бы по-разному.
 *
 * Порядок важен: шина важнее всего, приёмник заглушкой не закрываем — он
 * открытый конец, из которого среда уходит в квартал.
 */
internal fun decorForCell(tile: Tile): String? = when {
    tile.role == Role.BUS -> "wheel"
    tile.fixed -> "flange"
    tile.kind == TileKind.CAP && tile.role != Role.RECEIVER -> "blind"
    tile.kind == TileKind.STRAIGHT -> "coupling"
    else -> null
}

/** Размер служебной детали относительно клетки. */
internal fun decorScale(key: String): Float = when (key) {
    "wheel" -> 0.52f
    "flange" -> 0.66f
    "blind" -> 0.60f
    "coupling" -> 0.42f
    else -> 0.5f
}

/** Плита-указатель под трубой: бит направления → спрайт. */
internal fun arrowFor(bit: Int): String? = when (bit) {
    N -> "arrow-up"
    E -> "arrow-right"
    S -> "arrow-down"
    W -> "arrow-left"
    else -> null
}

/** Указатель гасим: он подсказка под трубой, а не знак поверх неё. */
private const val ARROW_ALPHA = 0.24f

/**
 * Куда течёт среда: от шины по подключённым клеткам (обход в ширину).
 *
 * Возвращает направление **движения** — из клетки-родителя в клетку, поэтому
 * стрелка смотрит от шины к приёмникам. Сеть маленькая (до 8×8), поэтому
 * считаем каждый кадр: дешевле, чем следить за изменениями.
 */
private fun flowDirections(level: Level, powered: BooleanArray): Map<Int, Int> {
    val size = level.size
    val dirs = HashMap<Int, Int>()
    val seen = BooleanArray(level.tiles.size)
    val queue = ArrayDeque<Int>()
    seen[level.bus] = true
    queue += level.bus
    val steps = arrayOf(N to -size, E to 1, S to size, W to -1)
    while (queue.isNotEmpty()) {
        val at = queue.removeFirst()
        val x = at % size
        val mask = tileMask(level, at)
        for ((bit, step) in steps) {
            if (mask and bit == 0) continue
            if (bit == E && x + 1 >= size) continue
            if (bit == W && x == 0) continue
            val to = at + step
            if (to < 0 || to >= level.tiles.size) continue
            if (seen[to] || !powered[to]) continue
            val back = when (bit) { N -> S; S -> N; E -> W; else -> E }
            if (tileMask(level, to) and back == 0) continue
            seen[to] = true
            dirs[to] = bit
            queue += to
        }
    }
    return dirs
}

/** Кладёт картинку в клетку: центр и сторона в пикселях поля. */
private fun DrawScope.drawAt(image: ImageBitmap, cx: Float, cy: Float, side: Float, alpha: Float = 1f) {
    drawImage(
        image = image,
        dstOffset = IntOffset((cx - side / 2f).toInt(), (cy - side / 2f).toInt()),
        dstSize = IntSize(side.toInt(), side.toInt()),
        alpha = alpha,
    )
}

/**
 * Кадр яруса почти погашен: это атмосфера квартала, а не поверхность, по которой
 * ведут трубы. На граффити метки читаются лучше, чем контур трубы, — играть было
 * тяжело. Дальше поле накрыто однотонной плитой, и фон остаётся за её краем.
 */
private const val BG_AMBIENCE = 0.28f

/** Плита поля: ровный тёмный лист — трубы и ток читаются на любом ярусе. */
private const val PLATE_ALPHA = 0.95f

/** Кромка плиты: обозначает границу квартала, не споря с трубами. */
private val PLATE_EDGE = Color(0x247E9CC4)

private fun DrawScope.drawArtBackground(
    image: ImageBitmap?,
    board: Float,
    ox: Float,
    oy: Float,
    cell: Float,
    area: Size,
    tint: Color,
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
            alpha = BG_AMBIENCE,
        )
    }
    // Плита: почти непрозрачная, поэтому поле однотонное. Одновременно несёт
    // купленную тему квартала — иначе «тема поля» была бы видна только в схеме.
    val plate = cell * 0.16f
    drawRoundRect(
        color = tint.copy(alpha = PLATE_ALPHA),
        topLeft = Offset(ox - plate / 2f, top),
        size = Size(board + plate, height),
        cornerRadius = CornerRadius(plate),
    )
    drawRoundRect(
        color = PLATE_EDGE,
        topLeft = Offset(ox - plate / 2f, top),
        size = Size(board + plate, height),
        cornerRadius = CornerRadius(plate),
        style = Stroke(width = 1f),
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
    // Размер задаём трансформацией, а не dstSize: тот целочисленный, и округление
    // cell вниз оставляло бы между соседними деталями зазор в долю пикселя — трубы
    // перестали бы стыковаться. Лишний пиксель сверху перекрывает сглаженный край,
    // как это делает веб-рендер (там -cell/2-0.5 … cell+1).
    withTransform({
        translate(cx, cy)
        rotate(angle * 90f, Offset.Zero)
        scale((cell + 1f) / image.width, (cell + 1f) / image.height, Offset.Zero)
    }) {
        drawImage(
            image = image,
            dstOffset = IntOffset(-image.width / 2, -image.height / 2),
            dstSize = IntSize(image.width, image.height),
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
    // Горловина: те же пропорции, что в вебе (0.94 клетки), но размер задан
    // трансформацией — иначе округляется и деталь садится с зазором.
    withTransform({
        translate(cx, cy)
        rotate(spin, Offset.Zero)
    }) {
        if (mouth != null) {
            val k = cell * 0.94f / mouth.width
            withTransform({ scale(k, k, Offset.Zero) }) {
                drawImage(
                    image = mouth,
                    dstOffset = IntOffset(-mouth.width / 2, -mouth.height / 2),
                    dstSize = IntSize(mouth.width, mouth.height),
                )
            }
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
    // Отметка цели: пока приёмник не под током, обводим его оранжевым палитры —
    // так же, как в веб-лаборатории. Раньше здесь была бронза (#FFB040), которой
    // нет в палитре Guttercaps, и в арт-режиме цель не была видна вовсе.
    if (!on) {
        val d = cell * 0.36f
        rotate(degrees = 45f, pivot = Offset(cx, cy)) {
            drawRect(
                color = Grad.Warn,
                topLeft = Offset(cx - d, cy - d),
                size = Size(d * 2f, d * 2f),
                style = Stroke(width = 2f),
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
    skin: BoardSkin,
    phase: Float,
) {
    art.round("cap")?.let { cap ->
        withTransform({
            translate(cx, cy)
            scale(cell * 0.86f / cap.width, cell * 0.86f / cap.height, Offset.Zero)
        }) {
            drawImage(
                image = cap,
                dstOffset = IntOffset(-cap.width / 2, -cap.height / 2),
                dstSize = IntSize(cap.width, cap.height),
            )
        }
    }
    // Кольцо — это ореол шины, его и красит купленный «источник»: каноническая
    // шина и так золотая, поэтому цвет самого колпака предмет не меняет.
    drawCircle(
        color = skin.sourceRing.copy(alpha = 0.25f + 0.35f * phase),
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
