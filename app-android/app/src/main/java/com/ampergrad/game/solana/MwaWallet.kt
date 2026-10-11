package com.ampergrad.game.solana

import android.net.Uri
import androidx.activity.ComponentActivity
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload

/** Ошибка кошелька с человекочитаемым текстом для интерфейса. */
class WalletException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Обёртка над Mobile Wallet Adapter 2.x.
 *
 * Один и тот же код работает со всеми MWA-кошельками: Seed Vault Wallet (Seeker),
 * Phantom, Solflare, Backpack. Приложение не знает, какой кошелёк выбран —
 * это и есть смысл MWA.
 *
 * Важно: приложение НИКОГДА не просит подписывать транзакции за SOL.
 * Единственная подпись — proof of ownership кошелька (сообщение с nonce сервера).
 * Минт награды выполняет сервер за свой счёт.
 */
class MwaWallet(
    private val activity: ComponentActivity,
    useMainnet: Boolean,
) {
    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://ampergrad.game"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Амперград",
        ),
    ).apply {
        // Сеть задаётся до первой авторизации: смена сбрасывает authToken.
        blockchain = if (useMainnet) Solana.Mainnet else Solana.Devnet
    }

    private val sender = ActivityResultSender(activity)

    /** Адрес подключённого кошелька (base58), null — не подключён. */
    var address: String? = null
        private set

    /** Токен авторизации MWA: позволяет не показывать окно подтверждения каждый раз. */
    var authToken: String? = adapter.authToken

    /** Восстановление сохранённой сессии при запуске приложения. */
    fun restore(storedAuthToken: String?, storedAddress: String?) {
        adapter.authToken = storedAuthToken
        authToken = storedAuthToken
        address = storedAddress
    }

    /** Подключение кошелька. Возвращает адрес в base58. */
    suspend fun connect(): String {
        val result = adapter.transact(sender) { auth ->
            Pair(auth.authToken, auth.accounts.first().publicKey)
        }
        return when (result) {
            is TransactionResult.Success -> {
                val payload = result.successPayload
                    ?: throw WalletException("Кошелёк не вернул данные авторизации")
                authToken = payload.first
                // authToken доступен и через сам адаптер: сохраняем оба.
                adapter.authToken?.let { authToken = it }
                val addr = Base58.encode(payload.second)
                address = addr
                addr
            }
            is TransactionResult.NoWalletFound ->
                throw WalletException("Не найден MWA-кошелёк. Установите Phantom, Solflare или используйте Seed Vault Wallet на Seeker.")
            is TransactionResult.Failure ->
                throw WalletException(result.message.ifBlank { "Кошелёк отклонил подключение" }, result.e)
        }
    }

    /**
     * Подпись сообщения (proof of ownership). Возвращает 64-байтовую подпись.
     * Сервер проверяет её своим Ed25519 и только потом минтит награду.
     */
    suspend fun signMessage(message: ByteArray): ByteArray {
        val result = adapter.transact(sender) { auth ->
            signMessagesDetached(arrayOf(message), arrayOf(auth.accounts.first().publicKey))
        }
        return when (result) {
            is TransactionResult.Success -> {
                val signed = result.successPayload?.messages?.firstOrNull()
                    ?: throw WalletException("Кошелёк вернул пустой ответ на подпись")
                signed.signatures.firstOrNull()
                    ?: throw WalletException("Кошелёк не вернул подпись")
            }
            is TransactionResult.NoWalletFound ->
                throw WalletException("MWA-кошелёк не найден")
            is TransactionResult.Failure ->
                throw WalletException(result.message.ifBlank { "Кошелёк отклонил подпись" }, result.e)
        }
    }

    /** Отключение: кошелёк аннулирует выданный authToken. */
    suspend fun disconnect() {
        val result = adapter.disconnect(sender)
        if (result is TransactionResult.Failure) {
            throw WalletException(result.message.ifBlank { "Не удалось отключить кошелёк" }, result.e)
        }
        authToken = null
        address = null
    }
}
