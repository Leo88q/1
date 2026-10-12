package com.ampergrad.game.content

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Витрина косметики. Каталог — тот же, что у сервера (`/shop`), выгружен из
 * TS-канона в `assets/cosmetics.json`, поэтому приложение показывает витрину
 * даже без сети.
 *
 * Правило проекта: косметика меняет только вид. Ни один предмет не влияет
 * на правила, время и награды — это проверяется тестом.
 */
data class CosmeticItem(
    val id: String,
    val name: String,
    val type: String,
    val rarity: String,
    /** Канал получения: reward (за игру), coins, store (витрина), battle-pass. */
    val channel: String,
    val priceUsd: Double?,
    val priceCoins: Int?,
    val how: String,
    val lore: String,
    val colors: List<String>,
    val standard: String?,
) {
    val rarityLabel: String
        get() = when (rarity) {
            "common" -> "ОБЫЧНЫЙ"
            "rare" -> "РЕДКИЙ"
            "epic" -> "ЭПИЧЕСКИЙ"
            else -> "ЛЕГЕНДАРНЫЙ"
        }

    /** Цена для карточки: деньги витрины, монеты игры или способ получения. */
    val priceLabel: String
        get() = when {
            priceUsd != null -> "$${String.format("%.2f", priceUsd)}"
            priceCoins != null -> "$priceCoins монет"
            channel == "reward" -> "за игру"
            else -> "по сезону"
        }
}

class Shop(val items: List<CosmeticItem>) {

    val rewards: List<CosmeticItem> get() = items.filter { it.channel == "reward" }
    val coins: List<CosmeticItem> get() = items.filter { it.channel == "coins" }
    val store: List<CosmeticItem> get() = items.filter { it.channel == "store" }
    val seasonPass: List<CosmeticItem> get() = items.filter { it.channel == "battle-pass" }

    companion object {
        const val ASSET = "cosmetics.json"

        fun parse(json: String): Shop {
            val array = JSONObject(json).getJSONArray("cosmetics")
            val items = (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                CosmeticItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    type = o.getString("type"),
                    rarity = o.getString("rarity"),
                    channel = o.getString("channel"),
                    priceUsd = if (o.isNull("priceUsd")) null else o.getDouble("priceUsd"),
                    priceCoins = if (o.isNull("priceCoins")) null else o.getInt("priceCoins"),
                    how = o.getString("how"),
                    lore = o.getString("lore"),
                    colors = o.getJSONArray("colors").mapStrings(),
                    standard = if (o.isNull("standard")) null else o.getString("standard"),
                )
            }
            return Shop(items)
        }

        fun load(context: Context): Shop? = try {
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
        } catch (_: Exception) {
            null
        }
    }
}

private fun JSONArray.mapStrings(): List<String> = (0 until length()).map { getString(it) }
