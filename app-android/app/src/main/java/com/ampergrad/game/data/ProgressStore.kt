package com.ampergrad.game.data

import android.content.Context
import org.json.JSONObject

/**
 * Локальный прогресс и сессия кошелька.
 *
 * Кошелёк: адрес и MWA authToken. Резервное копирование приложения отключено
 * (см. data_extraction_rules.xml), чтобы токен не уезжал в чужие бэкапы.
 * Игровой прогресс — локальный: игра работает офлайн, сеть нужна только для клейма.
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("ampergrad", Context.MODE_PRIVATE)

    var level: Int
        get() = prefs.getInt("level", 1)
        set(value) = prefs.edit().putInt("level", value).apply()

    fun stars(levelNumber: Int): Int = prefs.getInt("stars_$levelNumber", 0)

    fun bestMoves(levelNumber: Int): Int = prefs.getInt("moves_$levelNumber", 0)

    fun solvedCount(): Int = prefs.getInt("solved_count", 0)

    fun totalStars(): Int = prefs.getInt("total_stars", 0)

    /** Записывает победу: уровень, звёзды, лучшие ходы, стрик дней. */
    fun recordWin(levelNumber: Int, stars: Int, moves: Int, day: Long) {
        val previousStars = stars(levelNumber)
        val previousMoves = bestMoves(levelNumber)
        val editor = prefs.edit()
        if (previousStars == 0) editor.putInt("solved_count", solvedCount() + 1)
        if (stars > previousStars) {
            editor.putInt("total_stars", totalStars() + (stars - previousStars))
            editor.putInt("stars_$levelNumber", stars)
        }
        if (previousMoves == 0 || moves < previousMoves) editor.putInt("moves_$levelNumber", moves)
        if (levelNumber + 1 > level) editor.putInt("level", levelNumber + 1)
        editor.apply()
        touchDay(day)
    }

    /** Стрик: серия дней, в которые игрок собирал хотя бы одну пломбу. */
    private fun touchDay(day: Long) {
        val last = prefs.getLong("last_day", -1)
        val streak = when {
            last == day -> prefs.getInt("streak", 0)
            last == day - 1 -> prefs.getInt("streak", 0) + 1
            else -> 1
        }
        prefs.edit().putLong("last_day", day).putInt("streak", streak).apply()
    }

    val streak: Int
        get() {
            val last = prefs.getLong("last_day", -1)
            val today = System.currentTimeMillis() / 86_400_000L
            return if (last == today || last == today - 1) prefs.getInt("streak", 0) else 0
        }

    /** Челлендж дня: отметка о решении (unix-день). */
    var dailyDoneDay: Long
        get() = prefs.getLong("daily_done_day", -1)
        set(value) = prefs.edit().putLong("daily_done_day", value).apply()

    var dailyBestMoves: Int
        get() = prefs.getInt("daily_best_moves", 0)
        set(value) = prefs.edit().putInt("daily_best_moves", value).apply()

    /** Язык интерфейса: "ru" или "en". Пусто — берём системный. */
    var lang: String?
        get() = prefs.getString("lang", null)
        set(value) = prefs.edit().putString("lang", value).apply()

    /** Последний загруженный рейтинг дня (JSON) — экран открывается мгновенно. */
    var leaderboardJson: String?
        get() = prefs.getString("leaderboard", null)
        set(value) = prefs.edit().putString("leaderboard", value).apply()

    /** Кошелёк. */
    var walletAddress: String?
        get() = prefs.getString("wallet_address", null)
        set(value) = prefs.edit().putString("wallet_address", value).apply()

    var walletAuthToken: String?
        get() = prefs.getString("wallet_auth_token", null)
        set(value) = prefs.edit().putString("wallet_auth_token", value).apply()

    /** Клеймы: сколько наград уже забрано (для честного прогресса и анти-спама). */
    var claimedCount: Int
        get() = prefs.getInt("claimed_count", 0)
        set(value) = prefs.edit().putInt("claimed_count", value).apply()

    var lastClaimJson: String?
        get() = prefs.getString("last_claim", null)
        set(value) = prefs.edit().putString("last_claim", value).apply()

    fun exportStars(): String = JSONObject().apply {
        for (i in 1..75) {
            val s = stars(i)
            if (s > 0) put(i.toString(), s)
        }
    }.toString()

    fun reset() {
        prefs.edit().clear().apply()
    }
}
