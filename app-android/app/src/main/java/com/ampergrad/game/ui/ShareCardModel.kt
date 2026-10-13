package com.ampergrad.game.ui

import kotlin.math.floor

/**
 * Карточка квартала: то, чем игрок делится после смены (см. «виральность»
 * в docs/04-liveops-roadmap.md). На карточке — сам квартал: поле, пломба,
 * звёзды, ходы против плана и время.
 *
 * Здесь только данные и раскладка — ни канвы, ни Android, поэтому раскладку
 * проверяет обычный unit-тест (ShareCardTest), а не только глаза на устройстве.
 */

/** Размер карточки: 3:4 — то, что мессенджеры не обрезают в предпросмотре. */
const val CARD_WIDTH = 1080

const val CARD_HEIGHT = 1440

/** Что попадает на карточку. */
data class ShareCardData(
    /** Номер квартала (для челленджа дня — номер уровня, по которому он собран). */
    val levelNumber: Int,
    /** Имя квартала на языке игрока. */
    val quarter: String,
    /** Имя яруса на языке игрока. */
    val tier: String,
    val stars: Int,
    val moves: Int,
    val par: Int,
    val time: String,
    val hints: Int,
    /** Короткий адрес кошелька; null — игрок ещё не подключил кошелёк. */
    val player: String?,
    /** Ключ пломбы (`resin`, `crack`, `magma` или число звёзд) — медальон на карточке. */
    val sealKey: String,
    /** Подпись пломбы («ПЛОМБА · С ТРЕЩИНОЙ»). */
    val sealTitle: String,
    /** Челлендж дня: тогда вместо номера квартала пишем «ЧЕЛЛЕНДЖ ДНЯ». */
    val daily: Boolean,
)

/**
 * Раскладка карточки в пикселях.
 *
 * Поле считается первым: сторона клетки целая (иначе между деталями трубы
 * остаются щели — на карточке трубы должны стыковаться так же, как в игре),
 * а само поле вписано и по ширине, и по высоте, чтобы подвал не наехал.
 */
data class ShareCardLayout(
    val width: Int,
    val height: Int,
    /** Сторона поля. */
    val board: Float,
    val boardLeft: Float,
    val boardTop: Float,
    /** Сторона клетки поля — целое число пикселей. */
    val cell: Float,
    /** Пломба: правый верхний угол шапки. */
    val sealSize: Float,
    val sealLeft: Float,
    val sealTop: Float,
    val titleBaseline: Float,
    val subtitleBaseline: Float,
    val starsBaseline: Float,
    val statsBaseline: Float,
    val dividerY: Float,
    val footerBaseline: Float,
)

/** Раскладка карточки под поле размером [size]×[size]. */
fun shareCardLayout(
    size: Int,
    width: Int = CARD_WIDTH,
    height: Int = CARD_HEIGHT,
): ShareCardLayout {
    val margin = width * 0.0667f
    val cells = size.coerceIn(3, 12)
    // Поле не выше 60% карточки: иначе звёзды и подвал не помещаются.
    val room = minOf(width - margin * 2f, height * 0.60f)
    val cell = floor(room / cells)
    val board = cell * cells
    val boardLeft = (width - board) / 2f
    val boardTop = height * 0.16f
    val boardBottom = boardTop + board
    val sealSize = width * 0.155f
    return ShareCardLayout(
        width = width,
        height = height,
        board = board,
        boardLeft = boardLeft,
        boardTop = boardTop,
        cell = cell,
        sealSize = sealSize,
        sealLeft = width - margin - sealSize,
        sealTop = height * 0.022f,
        titleBaseline = height * 0.072f,
        subtitleBaseline = height * 0.126f,
        starsBaseline = boardBottom + height * 0.062f,
        statsBaseline = boardBottom + height * 0.104f,
        dividerY = boardBottom + height * 0.146f,
        footerBaseline = boardBottom + height * 0.201f,
    )
}
