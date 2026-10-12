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
    /** Метка редкости на языке интерфейса. */
    fun rarityLabel(t: UiStrings): String = t.rarity(rarity)

    /** Цена для карточки: деньги витрины, монеты игры или способ получения. */
    fun priceLabel(t: UiStrings): String = t.priceLabel(priceUsd, priceCoins, channel)
}

/** Правила экономики монет (канон: packages/engine/src/content/economy.ts). */
data class EconomyRules(
    val coinsPerSolve: Int = 20,
    val coinsPerStar: Int = 10,
    val dailyBonus: Int = 20,
) {
    /** Монеты за решение: базовые плюс по звезде, для челленджа дня — с добавкой. */
    fun coinsForSolve(stars: Int, daily: Boolean = false): Int {
        val clean = stars.coerceIn(0, 3)
        return coinsPerSolve + coinsPerStar * clean + if (daily) dailyBonus else 0
    }

    fun canAfford(coins: Int, priceCoins: Int?): Boolean = priceCoins != null && coins >= priceCoins

    companion object {
        /** Резерв на случай, если контент не попал в сборку: числа совпадают с каноном. */
        val Default = EconomyRules()
    }
}

class Shop(
    val items: List<CosmeticItem>,
    val economy: EconomyRules = EconomyRules.Default,
) {

    val rewards: List<CosmeticItem> get() = items.filter { it.channel == "reward" }
    val coins: List<CosmeticItem> get() = items.filter { it.channel == "coins" }
    val store: List<CosmeticItem> get() = items.filter { it.channel == "store" }
    val seasonPass: List<CosmeticItem> get() = items.filter { it.channel == "battle-pass" }

    /** Предмет по идентификатору. */
    fun byId(id: String): CosmeticItem? = items.firstOrNull { it.id == id }

    /** За монеты продаётся только внешний вид (канон: coinPurchasable). */
    fun coinPurchasable(item: CosmeticItem): Boolean = item.channel == "coins" && item.priceCoins != null

    companion object {
        const val ASSET = "cosmetics.json"

        fun parse(json: String): Shop {
            val root = JSONObject(json)
            val economy = root.optJSONObject("economy")?.let { e ->
                EconomyRules(
                    coinsPerSolve = e.optInt("coinsPerSolve", EconomyRules.Default.coinsPerSolve),
                    coinsPerStar = e.optInt("coinsPerStar", EconomyRules.Default.coinsPerStar),
                    dailyBonus = e.optInt("dailyBonus", EconomyRules.Default.dailyBonus),
                )
            } ?: EconomyRules.Default
            val array = root.getJSONArray("cosmetics")
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
            return Shop(items, economy)
        }

        fun load(context: Context): Shop? = try {
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
        } catch (_: Exception) {
            null
        }
    }
}

private fun JSONArray.mapStrings(): List<String> = (0 until length()).map { getString(it) }
