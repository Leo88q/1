package com.ampergrad.game.content

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ярус города: имя, правило и звук. */
data class TierInfo(
    val id: String,
    val order: Int,
    val name: String,
    val grid: String,
    val rule: String,
    val ambience: String,
    val levels: IntRange,
)

/** Квартал уровня: код в стиле диспетчерской и короткое имя. */
data class QuarterInfo(val level: Int, val code: String, val name: String)

/** Запись реестра — лор-фрагмент, который открывается за уровень. */
data class RegistryInfo(
    val id: String,
    val season: Int,
    val title: String,
    val speaker: String,
    val text: String,
    val source: String,
) {
    /** Подпись для карточки: «РЕЕСТР S1-01 · Клемма». */
    val label: String
        get() = "РЕЕСТР ${id.removePrefix("reg-").uppercase()} · $speaker"
}

/**
 * Контент игры: ярусы, кварталы и реестр.
 *
 * Файл `assets/lore.json` генерируется из TS-канона (`npm run content`),
 * поэтому клиент и сервер видят одинаковые имена и тексты. Если файла в сборке
 * нет — игра работает как раньше, показывает только номер уровня.
 */
class Lore(
    val tiers: List<TierInfo>,
    val quarters: List<QuarterInfo>,
    val registry: List<RegistryInfo>,
) {
    private val quartersByLevel = quarters.associateBy { it.level }
    private val registryById = registry.associateBy { it.id }

    fun quarter(level: Int): QuarterInfo? = quartersByLevel[level]

    fun tier(level: Int): TierInfo? = tiers.firstOrNull { level in it.levels }

    fun registryEntry(id: String): RegistryInfo? = registryById[id]

    /** Запись реестра за уровень — тот же порядок и та же формула, что в TS-каноне. */
    fun registryForLevel(level: Int): RegistryInfo? {
        if (registry.isEmpty() || tier(level) == null) return null
        val index = ((level - 1) * registry.size) / TOTAL_LEVELS
        return registry[index.coerceIn(0, registry.size - 1)]
    }

    companion object {
        const val TOTAL_LEVELS = 75
        const val ASSET = "lore.json"

        fun parse(json: String): Lore {
            val root = JSONObject(json)

            val tiers = root.getJSONArray("tiers").mapObjects { o ->
                val levels = o.getJSONArray("levels")
                TierInfo(
                    id = o.getString("id"),
                    order = o.getInt("order"),
                    name = o.getString("name"),
                    grid = o.getString("grid"),
                    rule = o.getString("rule"),
                    ambience = o.getString("ambience"),
                    levels = levels.getInt(0)..levels.getInt(1),
                )
            }

            val quarters = root.getJSONArray("quarters").mapObjects { o ->
                QuarterInfo(level = o.getInt("level"), code = o.getString("code"), name = o.getString("name"))
            }

            val registry = root.getJSONArray("registry").mapObjects { o ->
                RegistryInfo(
                    id = o.getString("id"),
                    season = o.getInt("season"),
                    title = o.getString("title"),
                    speaker = o.getString("speaker"),
                    text = o.getString("text"),
                    source = o.getString("source"),
                )
            }

            return Lore(tiers, quarters, registry)
        }

        /** Читает контент из assets; null — если файл не попал в сборку. */
        fun load(context: Context): Lore? = try {
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
        } catch (_: Exception) {
            null
        }
    }
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }
