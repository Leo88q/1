package com.ampergrad.game.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ampergrad.game.engine.E
import com.ampergrad.game.engine.N
import com.ampergrad.game.engine.S
import com.ampergrad.game.engine.W
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Арт поля: спрайты труб, фоны ярусов и пломбы-награды.
 *
 * Источник тот же, что у веб-лаборатории: `packages/lab/public/art` (его собирает
 * из исходников `art/` скрипт `packages/lab/scripts/build-art.py`). Gradle-задача
 * `syncContent` кладёт каталог в `assets/art`, поэтому игра и витрина показывают
 * одну и ту же картинку, а не две похожие.
 *
 * Ключевое для стыковки: у каждого спрайта в манифесте указана **маска движка**
 * (N=1, E=2, S=4, W=8), в положении которой он нарисован. Спрайт ставится в ячейку
 * и поворачивается на тот же угол, что и маска фрагмента, — поэтому детали соседних
 * ячеек визуально стыкуются без подгонки.
 */
data class SpriteRef(val file: String, val mask: Int)

/** Ярус в терминах арта: наборы металла, фоны (a/b) и цвет среды. */
data class ArtTier(
    val id: String,
    val name: String,
    val sets: List<String>,
    val bg: List<String>,
    val flowHex: String,
) {
    val flow: Color get() = colorOfHex(flowHex)
}

/** Контракт арта: `assets/art/web.json`. */
data class ArtPack(
    val tile: Int,
    val sets: Map<String, Map<String, SpriteRef>>,
    val round: Map<String, String>,
    val tiers: List<ArtTier>,
    val screens: Map<String, String>,
    val rewards: Map<Int, String>,
    /** Особые пломбы: не за звёзды, а за то, как запечатан квартал (см. chooseSeal). */
    val seals: Map<String, String> = emptyMap(),
    /** Служебные детали поля: метки особенных клеток (см. decorForCell). */
    val props: Map<String, String> = emptyMap(),
)

/** «#RRGGBB» → цвет Compose; неразобранная строка даёт прозрачный, а не падение. */
fun colorOfHex(hex: String): Color {
    val value = hex.removePrefix("#")
    if (value.length != 6) return Color.Transparent
    val rgb = value.toLongOrNull(16) ?: return Color.Transparent
    return Color(0xFF000000L or rgb)
}

/**
 * Какая деталь рисует маску фрагмента. Считаем по числу портов — как `kindOf` в
 * движке: 1 — тупик (`stub`), 2 порта навстречу — прямая, 2 под углом — отвод,
 * 3 — тройник, 4 — крест. У прямой два варианта, поэтому в наборе лежат и
 * `straight`, и `straight2` (второй есть только у канонного набора; рендер, как и
 * веб-версия, берёт первый — второй лежит запасом под разнообразие поля).
 */
fun piecesForMask(mask: Int): List<String> = when (Integer.bitCount(mask and 0xF)) {
    1 -> listOf("stub")
    2 -> if (mask == (N or S) || mask == (E or W)) listOf("straight", "straight2") else listOf("elbow")
    3 -> listOf("tee")
    4 -> listOf("cross")
    else -> listOf("straight", "straight2")
}

/** Набор материала для ячейки: у яруса их один или два — второй даёт разнообразие поля. */
fun setForCell(sets: List<String>, x: Int, y: Int, index: Int): String =
    if (sets.size <= 1) sets.firstOrNull() ?: "base" else sets[(x + y + index) % sets.size]

/** Номер яруса арта по номеру уровня: 15 уровней на ярус, как в каноне. */
fun artTierIndex(levelNumber: Int, tiers: Int): Int = ((levelNumber - 1) / 15).coerceIn(0, tiers - 1)

/** Пломба за число звёзд: 3 — чисто, 2 — с подсказкой, 1 — с трудом. */
fun rewardForStars(stars: Int): Int = stars.coerceIn(1, 3)

/**
 * Какая пломба достанется за квартал.
 *
 * Кроме трёх пломб по числу звёзд есть три особых — за то, как именно квартал
 * запечатан: подсказка означает «заделано смолой», ходов втрое больше плана —
 * пломба с трещиной, три звезды в Плавильне — плавленая. Правила те же, что в
 * веб-версии (chooseSeal в main.ts), иначе игрок видел бы разные медальоны.
 *
 * Порядок важен: подсказка перекрывает всё остальное.
 */
fun chooseSeal(stars: Int, hints: Int, moves: Int, par: Int, tier: String): String = when {
    hints > 0 -> "resin"
    moves > par * 3 -> "crack"
    stars == 3 && tier == "melter" -> "magma"
    else -> rewardForStars(stars).toString()
}

/**
 * Артом можно рисовать: декодирован канонный набор и обе круглые детали.
 *
 * Проверяем не «web.json прочитан», а «есть чем рисовать»: если файл контракта на
 * месте, а картинки не декодировались (не тот формат, битый ассет, не хватило
 * памяти), поле оказалось бы пустым — но играбельным с виду. Такой арт считаем
 * нерабочим, и игра честно уходит в схему.
 */
fun packIsUsable(pack: ArtPack, decoded: Set<String>): Boolean {
    val pieces = pack.sets["base"] ?: return false
    if (pieces.values.any { it.file !in decoded }) return false
    return pack.round.values.all { it in decoded }
}

class Art(private val context: Context, private val base: String = "art/") {

    var pack: ArtPack? = null
        private set

    /** Почему арт не поднялся — в лог, а не молчание. */
    var error: String? = null
        private set

    private val images = HashMap<String, ImageBitmap>()
    // Оба кадра фона текущего яруса: уровни внутри яруса чередуют их, и держать
    // два дешевле, чем декодировать 720×1290 при каждом переходе (фон мигал бы).
    private val backgrounds = arrayOfNulls<ImageBitmap>(2)
    private var backgroundsFor: String? = null

    val ready: Boolean get() = pack != null && decodedAll

    /** Все спрайты, названные в контракте, реально декодировались. */
    private val decodedAll: Boolean get() = pack?.let { packIsUsable(it, images.keys) } ?: false

    /** Читает контракт и раскладывает спрайты в память (декодирование — вне главного потока). */
    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("$base" + "web.json").bufferedReader().use { it.readText() }
            val parsed = parse(json)
            images.clear()
            for (set in parsed.sets.values) {
                for (sprite in set.values) decode(sprite.file)?.let { images[sprite.file] = it }
            }
            for (file in parsed.round.values) decode(file)?.let { images[file] = it }
            for (file in parsed.rewards.values) decode(file)?.let { images[file] = it }
            for (file in parsed.seals.values) decode(file)?.let { images[file] = it }
            for (file in parsed.props.values) decode(file)?.let { images[file] = it }
            // Фоны экранов (меню, победа, витрина): их заметно сразу, поэтому
            // греем вместе с остальным, а не по первому показу.
            for (file in parsed.screens.values) decode(file)?.let { images[file] = it }
            pack = parsed
            error = if (decodedAll) null else "спрайты из web.json не декодировались — рисуем схему"
        } catch (err: Exception) {
            error = err.message ?: err.toString()
        }
    }

    /** Спрайт детали набора; null — если арт не нашли (тогда рисуется схема). */
    fun sprite(set: String, piece: String): ImageBitmap? {
        val ref = pack?.sets?.get(set)?.get(piece) ?: return null
        return images[ref.file]
    }

    fun round(name: String): ImageBitmap? = images[pack?.round?.get(name) ?: return null]

    fun reward(stars: Int): ImageBitmap? = images[pack?.rewards?.get(rewardForStars(stars)) ?: return null]

    /** Пломба по ключу: сначала особые, потом по числу звёзд (см. chooseSeal). */
    fun seal(key: String): ImageBitmap? {
        val loaded = pack ?: return null
        val file = loaded.seals[key] ?: loaded.rewards[key.toIntOrNull() ?: return null] ?: return null
        return images[file]
    }

    /** Служебная деталь поля: метка особенности клетки (см. decorForCell). */
    fun prop(key: String): ImageBitmap? = images[pack?.props?.get(key) ?: return null]

    /** Фон экрана: меню, победа или витрина. */
    fun screen(name: String): ImageBitmap? = images[pack?.screens?.get(name) ?: return null]

    /**
     * Фон яруса `variant` (0 или 1 — кадры a/b). Оба кадра яруса держим в памяти и
     * отдаём синхронно, если уже декодировали: иначе поле мигает пустотой на
     * каждом переходе между уровнями. Декодирование — только вне главного потока.
     */
    fun background(tier: ArtTier, variant: Int): ImageBitmap? {
        val files = tier.bg
        if (files.isEmpty()) return null
        val slot = variant % files.size
        val img = decode(files[slot]) ?: return null
        if (backgroundsFor != tier.id) {
            backgroundsFor = tier.id
            backgrounds[0] = null
            backgrounds[1] = null
        }
        backgrounds[slot] = img
        return img
    }

    /** Уже декодированный кадр фона — без чтения с диска: с главного потока можно. */
    fun cachedBackground(tier: ArtTier, variant: Int): ImageBitmap? {
        if (backgroundsFor != tier.id || tier.bg.isEmpty()) return null
        return backgrounds[variant % tier.bg.size]
    }

    private fun decode(file: String): ImageBitmap? = try {
        context.assets.open("$base$file").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Разбор контракта. Отдельно от загрузки — так его проверяет unit-тест. */
        fun parse(json: String): ArtPack {
            val root = JSONObject(json)
            val sets = HashMap<String, Map<String, SpriteRef>>()
            val setsJson = root.getJSONObject("sets")
            for (setName in setsJson.keys()) {
                val pieces = HashMap<String, SpriteRef>()
                val piecesJson = setsJson.getJSONObject(setName)
                for (piece in piecesJson.keys()) {
                    val obj = piecesJson.getJSONObject(piece)
                    pieces[piece] = SpriteRef(obj.getString("file"), obj.optInt("mask", 0))
                }
                sets[setName] = pieces
            }

            val round = HashMap<String, String>()
            val roundJson = root.getJSONObject("round")
            for (name in roundJson.keys()) round[name] = roundJson.getJSONObject(name).getString("file")

            val tiers = ArrayList<ArtTier>()
            val tiersJson = root.getJSONArray("tiers")
            for (i in 0 until tiersJson.length()) {
                val obj = tiersJson.getJSONObject(i)
                tiers += ArtTier(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    sets = obj.getJSONArray("sets").mapStrings(),
                    bg = obj.getJSONArray("bg").mapStrings(),
                    flowHex = obj.optString("flow", "#B6FF3C"),
                )
            }

            val screens = HashMap<String, String>()
            val screensJson = root.getJSONObject("screens")
            for (name in screensJson.keys()) screens[name] = screensJson.getString(name)

            val rewards = HashMap<Int, String>()
            val rewardsJson = root.getJSONObject("rewards")
            for (name in rewardsJson.keys()) rewards[name.toIntOrNull() ?: continue] = rewardsJson.getString(name)

            val seals = HashMap<String, String>()
            val sealsJson = root.optJSONObject("seals")
            if (sealsJson != null) {
                for (name in sealsJson.keys()) seals[name] = sealsJson.getString(name)
            }

            val props = HashMap<String, String>()
            val propsJson = root.optJSONObject("props")
            if (propsJson != null) {
                for (name in propsJson.keys()) props[name] = propsJson.getString(name)
            }

            return ArtPack(
                tile = root.optInt("tile", 256),
                sets = sets,
                round = round,
                tiers = tiers,
                screens = screens,
                rewards = rewards,
                seals = seals,
                props = props,
            )
        }
    }
}

private fun org.json.JSONArray.mapStrings(): List<String> = (0 until length()).map { getString(it) }
