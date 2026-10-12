package com.ampergrad.game.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Данные для подписи, полученные от сервера. */
data class ClaimChallenge(val nonce: String, val message: String, val rewardId: String)

/** Результат клейма. */
data class ClaimResult(val status: String, val assetId: String?, val txSignature: String?)

/** Данные для подписи при отправке результата дня. */
data class DailyChallenge(val nonce: String, val message: String, val day: Long, val moves: Int)

/** Одна строка рейтинга дня. */
data class DailyScore(val rank: Int, val wallet: String, val moves: Int)

/** Результат отправки дня: место игрока и топ рейтинга. */
data class DailyResult(
    val status: String,
    val rank: Int?,
    val moves: Int,
    val leaderboard: List<DailyScore>,
)

class ClaimException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Клиент сервера клейма (M2).
 *
 * Контракт:
 *   POST /claim/start    {address, engine, proof?}                    -> {nonce, message, rewardId}
 *   POST /claim/submit   {address, nonce, signature, rewardId, proof?} -> {status, assetId, txSignature}
 *   POST /daily/start    {address, day, moves, engine}                -> {nonce, message, day, moves}
 *   POST /daily/submit   {address, nonce, signature, proof}           -> {status, rank, leaderboard}
 *   GET  /daily/leaderboard?day=                                       -> {day, entries}
 *
 * Proof (последовательность ходов) серверный режим требует: без него клейм
 * отклоняется, если сервер работает не в отладочном режиме.
 *
 * Приложение не тратит SOL игрока: подпись — только подтверждение владения
 * кошельком, минт cNFT выполняет сервер своим ключом (см. docs/03-tech-plan.md).
 */
class ClaimClient(private val baseUrl: String) {

    suspend fun start(address: String, engineVersion: String, proof: PlayProof?): ClaimChallenge = post(
        path = "/claim/start",
        body = JSONObject().put("address", address).put("engine", engineVersion).apply {
            if (proof != null) put("proof", proof.toJson())
        },
    ) { json ->
        ClaimChallenge(
            nonce = json.getString("nonce"),
            message = json.getString("message"),
            rewardId = json.optString("rewardId", "plomba-first"),
        )
    }

    suspend fun submit(
        address: String,
        nonce: String,
        signatureBase58: String,
        rewardId: String,
        proof: PlayProof?,
    ): ClaimResult = post(
        path = "/claim/submit",
        body = JSONObject()
            .put("address", address)
            .put("nonce", nonce)
            .put("signature", signatureBase58)
            .put("rewardId", rewardId)
            .apply { if (proof != null) put("proof", proof.toJson()) },
    ) { json ->
        ClaimResult(
            status = json.optString("status", "unknown"),
            assetId = json.optString("assetId").takeIf { it.isNotEmpty() && it != "null" },
            txSignature = json.optString("txSignature").takeIf { it.isNotEmpty() && it != "null" },
        )
    }

    suspend fun dailyStart(address: String, day: Long, moves: Int, engineVersion: String): DailyChallenge = post(
        path = "/daily/start",
        body = JSONObject()
            .put("address", address)
            .put("day", day)
            .put("moves", moves)
            .put("engine", engineVersion),
    ) { json ->
        DailyChallenge(
            nonce = json.getString("nonce"),
            message = json.getString("message"),
            day = json.optLong("day", day),
            moves = json.optInt("moves", moves),
        )
    }

    suspend fun dailySubmit(
        address: String,
        nonce: String,
        signatureBase58: String,
        proof: PlayProof,
    ): DailyResult = post(
        path = "/daily/submit",
        body = JSONObject()
            .put("address", address)
            .put("nonce", nonce)
            .put("signature", signatureBase58)
            .put("proof", proof.toJson()),
    ) { json ->
        DailyResult(
            status = json.optString("status", "unknown"),
            rank = json.optInt("rank", 0).takeIf { it > 0 },
            moves = json.optInt("moves", proof.moves),
            leaderboard = parseBoard(json.optJSONArray("leaderboard")),
        )
    }

    suspend fun leaderboard(day: Long): List<DailyScore> = get("/daily/leaderboard?day=$day&limit=10") { json ->
        parseBoard(json.optJSONArray("entries"))
    }

    private fun parseBoard(array: org.json.JSONArray?): List<DailyScore> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            DailyScore(
                rank = row.optInt("rank", index + 1),
                wallet = row.optString("wallet", "—"),
                moves = row.optInt("moves", 0),
            )
        }
    }

    private suspend fun <T> post(path: String, body: JSONObject, parse: (JSONObject) -> T): T =
        request(path, "POST", body, parse)

    private suspend fun <T> get(path: String, parse: (JSONObject) -> T): T =
        request(path, "GET", null, parse)

    private suspend fun <T> request(
        path: String,
        method: String,
        body: JSONObject?,
        parse: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = URL(baseUrl.trimEnd('/') + path)
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                .orEmpty()

            if (code !in 200..299) {
                throw ClaimException("Сервер ответил $code: ${text.take(300)}")
            }
            parse(JSONObject(text))
        } catch (e: ClaimException) {
            throw e
        } catch (e: Exception) {
            throw ClaimException("Сервер клейма недоступен: ${e.message ?: e::class.simpleName}", e)
        } finally {
            connection?.disconnect()
        }
    }
}
