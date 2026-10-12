package com.ampergrad.game.net

import org.json.JSONArray
import org.json.JSONObject

/**
 * Рейтинг дня сохраняется на устройстве, чтобы экран открывался мгновенно и
 * работал без сети. Формат — тот же, что отдаёт сервер (`entries`).
 */
object LeaderboardCodec {

    fun encode(scores: List<DailyScore>): String {
        val array = JSONArray()
        for (score in scores) {
            array.put(
                JSONObject()
                    .put("rank", score.rank)
                    .put("wallet", score.wallet)
                    .put("moves", score.moves),
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<DailyScore> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                DailyScore(
                    rank = row.optInt("rank", index + 1),
                    wallet = row.optString("wallet", "—"),
                    moves = row.optInt("moves", 0),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
