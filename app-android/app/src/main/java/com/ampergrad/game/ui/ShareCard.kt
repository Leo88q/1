package com.ampergrad.game.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.ampergrad.game.content.UiStrings
import com.ampergrad.game.engine.Level
import com.ampergrad.game.engine.Role
import java.io.File

/**
 * Карточка квартала: рисуется в картинку и уходит в мессенджер через
 * системный экран «Поделиться» (см. «виральность» в docs/04-liveops-roadmap.md).
 *
 * Поле на карточке рисуется тем же кодом, что и в игре (детали труб, вода,
 * служебные метки), — поэтому карточка не врёт о том, как выглядит квартал,
 * и не требует отдельного арта.
 */

/** Фаза воды на карточке: поток стоит, но рябь и блики видны. */
private const val FIELD_PHASE = 0.35f

private const val CARD_BG = 0xFF06080E.toInt()
private const val CARD_PANEL = 0xFF0D1421.toInt()
private const val CARD_LINE = 0xFF1B2637.toInt()
private const val CARD_INK = 0xFFD7E3F4.toInt()
private const val CARD_INK_DIM = 0xFF6F8199.toInt()
private const val CARD_MINT = 0xFF00E5C7.toInt()
private const val CARD_GOLD = 0xFFFFC24B.toInt()
private const val CARD_METAL = 0xFF26334A.toInt()

/**
 * Рисует карточку квартала. Размер берётся из раскладки (shareCardLayout),
 * поэтому вызывающий код не считает пиксели сам.
 */
fun renderShareCard(
    art: Art?,
    tier: ArtTier?,
    level: Level,
    powered: BooleanArray,
    data: ShareCardData,
    strings: UiStrings,
): ImageBitmap {
    val layout = shareCardLayout(level.size)
    val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
    val canvas = NativeCanvas(bitmap)

    canvas.drawColor(CARD_BG)
    // Кадр победы — атмосфера, а не поверхность: он почти погашен, как в игре.
    art?.screen("victory")?.let { drawCover(canvas, it.asAndroidBitmap(), layout, alpha = 0.30f) }
    drawFrame(canvas, layout)
    drawHeader(canvas, layout, data, strings, art)
    drawField(canvas, layout, art, tier, level, powered)
    drawResult(canvas, layout, data, strings)
    drawFooter(canvas, layout, data, strings)
    return bitmap.asImageBitmap()
}

/** Фон на всю карточку: масштабируем с обрезкой, чтобы не было полос. */
private fun drawCover(canvas: NativeCanvas, image: Bitmap, layout: ShareCardLayout, alpha: Float) {
    val scale = maxOf(layout.width / image.width.toFloat(), layout.height / image.height.toFloat())
    val w = image.width * scale
    val h = image.height * scale
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
    canvas.drawBitmap(
        image,
        null,
        RectF((layout.width - w) / 2f, (layout.height - h) / 2f, (layout.width + w) / 2f, (layout.height + h) / 2f),
        paint,
    )
}

/** Тонкая рамка по краю: карточка выглядит собранной, а не как скриншот. */
private fun drawFrame(canvas: NativeCanvas, layout: ShareCardLayout) {
    val inset = layout.width * 0.024f
    val radius = layout.width * 0.03f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_MINT
        alpha = 70
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(RectF(inset, inset, layout.width - inset, layout.height - inset), radius, radius, paint)
}

private fun drawHeader(
    canvas: NativeCanvas,
    layout: ShareCardLayout,
    data: ShareCardData,
    strings: UiStrings,
    art: Art?,
) {
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_MINT
        textSize = layout.height * 0.052f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.22f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("АМПЕРГРАД", layout.width * 0.5f, layout.titleBaseline, title)

    val left = layout.width * 0.0667f
    val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_INK_DIM
        textSize = layout.height * 0.024f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.12f
    }
    sub.textAlign = Paint.Align.LEFT
    canvas.drawText(data.tier, left, layout.subtitleBaseline, sub)
    sub.textAlign = Paint.Align.RIGHT
    canvas.drawText(
        if (data.daily) strings.daily else strings.cardQuarter(data.levelNumber, data.quarter),
        layout.width - left,
        layout.subtitleBaseline,
        sub,
    )

    // Медальон пломбы: та же картинка, что на экране победы.
    val seal = art?.seal(data.sealKey)?.asAndroidBitmap()
    if (seal != null) {
        val box = RectF(layout.sealLeft, layout.sealTop, layout.sealLeft + layout.sealSize, layout.sealTop + layout.sealSize)
        canvas.drawBitmap(seal, null, box, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}

/** Поле квартала: плита, трубы, вода, служебные метки — как в игре. */
private fun drawField(
    canvas: NativeCanvas,
    layout: ShareCardLayout,
    art: Art?,
    tier: ArtTier?,
    level: Level,
    powered: BooleanArray,
) {
    val radius = layout.cell * 0.16f
    val box = RectF(layout.boardLeft, layout.boardTop, layout.boardLeft + layout.board, layout.boardTop + layout.board)
    canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_PANEL })
    canvas.drawRoundRect(
        box,
        radius,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CARD_LINE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        },
    )
    val field = renderField(art, tier, level, powered, layout)
    if (field != null) canvas.drawBitmap(field, layout.boardLeft, layout.boardTop, null)
}

/**
 * Поле рисуется compose-кодом игры в отдельную картинку: те же детали труб,
 * та же вода и те же служебные метки, что видит игрок на экране.
 */
private fun renderField(
    art: Art?,
    tier: ArtTier?,
    level: Level,
    powered: BooleanArray,
    layout: ShareCardLayout,
): Bitmap? {
    if (art == null || tier == null || !art.ready) return null
    val side = layout.board
    val cell = layout.cell
    val flow = tier.flow
    val dirs = flowDirections(level, powered)
    val image = ImageBitmap(side.toInt(), side.toInt())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(side, side)) {
        for (i in level.tiles.indices) {
            val x = i % level.size
            val y = i / level.size
            val cx = x * cell + cell * 0.5f
            val cy = y * cell + cell * 0.5f
            val tile = level.tiles[i]
            // Плита-указатель под трубой: на неподвижной картинке она и говорит, куда течёт.
            val arrow = dirs[i]?.let(::arrowFor)
            if (arrow != null) {
                art.prop(arrow)?.let { drawAt(it, cx, cy, cell, alpha = ARROW_ALPHA) }
            }
            drawArtTile(art, level, i, setForCell(tier.sets, x, y, i), cx, cy, cell, tile.rot.toFloat())
            if (powered[i]) drawArtFlow(level, i, cx, cy, cell, flow, FIELD_PHASE, dirs[i])
            if (tile.role == Role.RECEIVER) {
                drawArtReceiver(art, level, i, cx, cy, cell, powered[i], flow, FIELD_PHASE)
            }
            val decor = decorForCell(tile)
            if (decor != null) {
                art.prop(decor)?.let { drawAt(it, cx, cy, cell * decorScale(decor)) }
            }
        }
    }
    return image.asAndroidBitmap()
}

/** Звёзды и строка результата: ходы против плана, время, подсказки. */
private fun drawResult(canvas: NativeCanvas, layout: ShareCardLayout, data: ShareCardData, strings: UiStrings) {
    val stars = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (data.stars > 0) CARD_GOLD else CARD_METAL
        textSize = layout.height * 0.042f
        textAlign = Paint.Align.CENTER
    }
    val filled = data.stars.coerceIn(0, 3)
    canvas.drawText("★".repeat(filled) + "☆".repeat(3 - filled), layout.width * 0.5f, layout.starsBaseline, stars)

    val stats = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_INK
        textSize = layout.height * 0.026f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.08f
        textAlign = Paint.Align.CENTER
    }
    val line = buildString {
        append(strings.cardMoves).append(' ').append(data.moves)
        append(" · ").append(strings.cardPar).append(' ').append(data.par)
        append(" · ").append(strings.cardTime).append(' ').append(data.time)
        if (data.hints > 0) append(" · ").append(strings.cardHints).append(' ').append(data.hints)
    }
    canvas.drawText(line, layout.width * 0.5f, layout.statsBaseline, stats)
}

/** Подвал: чем квартал запечатан и кто его запечатал. */
private fun drawFooter(canvas: NativeCanvas, layout: ShareCardLayout, data: ShareCardData, strings: UiStrings) {
    val left = layout.width * 0.0667f
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_LINE
        strokeWidth = 2f
    }
    canvas.drawLine(left, layout.dividerY, layout.width - left, layout.dividerY, line)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = layout.height * 0.022f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.1f
    }
    paint.color = CARD_MINT
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(data.sealTitle, left, layout.footerBaseline, paint)

    paint.color = CARD_INK_DIM
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText(data.player ?: strings.cardNoWallet, layout.width - left, layout.footerBaseline, paint)
}

/**
 * Отдаёт карточку системе: PNG в кэш, ссылку — через FileProvider, чтобы
 * мессенджер не получил file:// (на Android 26+ он просто не откроется).
 *
 * Возвращает false, если поделиться не вышло, — вызывающий код скажет об этом
 * игроку, а не промолчит.
 */
fun shareQuarterCard(context: Context, card: ImageBitmap, caption: String): Boolean = try {
    val dir = File(context.cacheDir, "share")
    dir.mkdirs()
    val file = File(dir, "ampergrad-quarter.png")
    file.outputStream().use { card.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, caption).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}
