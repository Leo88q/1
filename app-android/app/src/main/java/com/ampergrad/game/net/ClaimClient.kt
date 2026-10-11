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

class ClaimException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Клиент сервера клейма (M2).
 *
 * Контракт:
 *   POST /claim/start   {"address":"...", "engine":"0.1.0"} -> {"nonce","message","rewardId"}
 *   POST /claim/submit  {"address","nonce","signature","rewardId"} -> {"status","assetId","txSignature"}
 *
 * Приложение не тратит SOL игрока: подпись — только подтверждение владения кошельком,
 * минт cNFT выполняет сервер своим ключом (см. docs/03-tech-plan.md).
 */
class ClaimClient(private val baseUrl: String) {

    suspend fun start(address: String, engineVersion: String): ClaimChallenge = post(
        path = "/claim/start",
        body = JSONObject().put("address", address).put("engine", engineVersion),
    ) { json ->
        ClaimChallenge(
            nonce = json.getString("nonce"),
            message = json.getString("message"),
            rewardId = json.optString("rewardId", "plomba-first"),
        )
    }

    suspend fun submit(address: String, nonce: String, signatureBase58: String, rewardId: String): ClaimResult = post(
        path = "/claim/submit",
        body = JSONObject()
            .put("address", address)
            .put("nonce", nonce)
            .put("signature", signatureBase58)
            .put("rewardId", rewardId),
    ) { json ->
        ClaimResult(
            status = json.optString("status", "unknown"),
            assetId = json.optString("assetId", null),
            txSignature = json.optString("txSignature", null),
        )
    }

    private suspend fun <T> post(path: String, body: JSONObject, parse: (JSONObject) -> T): T =
        withContext(Dispatchers.IO) {
            val url = URL(baseUrl.trimEnd('/') + path)
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }

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
