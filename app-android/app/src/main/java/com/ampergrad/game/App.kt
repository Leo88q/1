package com.ampergrad.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ampergrad.game.data.ProgressStore
import com.ampergrad.game.engine.DAILY_CONFIG_LEVEL
import com.ampergrad.game.engine.Level
import com.ampergrad.game.engine.applyRotate
import com.ampergrad.game.engine.dailySeed
import com.ampergrad.game.engine.generateLevel
import com.ampergrad.game.engine.hint
import com.ampergrad.game.engine.isSolved
import com.ampergrad.game.engine.poweredCells
import com.ampergrad.game.net.ClaimClient
import com.ampergrad.game.solana.Base58
import com.ampergrad.game.solana.MwaWallet
import com.ampergrad.game.ui.Board
import com.ampergrad.game.ui.Grad
import com.ampergrad.game.ui.Tier
import com.ampergrad.game.content.CosmeticItem
import com.ampergrad.game.content.Lore
import com.ampergrad.game.content.Shop
import com.ampergrad.game.ui.tierOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Mode { LEVEL, DAILY }

/** Состояние одной смены (квартала). */
class GameSession(val mode: Mode, val levelNumber: Int, val level: Level) {
    var moves by mutableIntStateOf(0)
    var hintsUsed by mutableIntStateOf(0)
    var finishedAt by mutableStateOf<Long?>(null)
    var stars by mutableIntStateOf(0)
    var solving by mutableStateOf(false)
    var hintIndex by mutableStateOf<Int?>(null)
    var hintUntil by mutableLongStateOf(0L)
    var reward by mutableStateOf(RewardState())
    val startedAt: Long = System.currentTimeMillis()
}

data class RewardState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val assetId: String? = null,
)

/**
 * Экран приложения: игра работает офлайн, кошелёк нужен только для клейма награды.
 */
@Composable
fun AmpergradApp(activity: ComponentActivity, wallet: MwaWallet, sound: SoundKit) {
    val context = LocalContext.current
    val store = remember { ProgressStore(context) }
    val lore = remember { Lore.load(context) }
    val shop = remember { Shop.load(context) }
    val claimApi = remember { ClaimClient(BuildConfig.SERVER_URL) }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<GameSession?>(null) }
    var menuOpen by remember { mutableStateOf(true) }
    var walletOpen by remember { mutableStateOf(false) }
    var registryOpen by remember { mutableStateOf(false) }
    var shopOpen by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var walletAddress by remember { mutableStateOf(store.walletAddress) }
    var walletError by remember { mutableStateOf<String?>(null) }
    var walletBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        wallet.restore(store.walletAuthToken, store.walletAddress)
        walletAddress = wallet.address
    }

    fun showBanner(text: String) {
        banner = text
        scope.launch {
            delay(2600)
            if (banner == text) banner = null
        }
    }

    fun startLevel(levelNumber: Int, mode: Mode = Mode.LEVEL) {
        val seed = if (mode == Mode.DAILY) dailySeed(System.currentTimeMillis() / 86_400_000L) else null
        val configLevel = if (mode == Mode.DAILY) DAILY_CONFIG_LEVEL else levelNumber
        val level = if (mode == Mode.DAILY) generateLevel(configLevel, seed!!) else generateLevel(configLevel)
        if (mode == Mode.DAILY) level.level = 0
        session = GameSession(mode, levelNumber, level)
        menuOpen = false
        walletOpen = false
        banner = when (mode) {
            Mode.DAILY -> "Челлендж дня: тот же квартал для всех мастеров Амперграда"
            Mode.LEVEL -> "Подай ток на ${level.receivers.size} приёмник(а): все ячейки квартала должны быть подключены к шине"
        }
    }

    fun finish(current: GameSession) {
        if (current.finishedAt != null) return
        val now = System.currentTimeMillis()
        current.finishedAt = now
        current.solving = true
        sound.solved()
        val par = current.level.parMoves
        var stars = when {
            current.moves <= par -> 3
            current.moves <= (par * 1.5).toInt() + 1 -> 2
            else -> 1
        }
        if (current.hintsUsed > 0) stars = minOf(stars, 2)
        current.stars = stars
        val day = now / 86_400_000L
        if (current.mode == Mode.LEVEL) {
            store.recordWin(current.levelNumber, stars, current.moves, day)
        } else {
            store.recordWin(0, stars, current.moves, day)
            store.dailyDoneDay = day
            if (store.dailyBestMoves == 0 || current.moves < store.dailyBestMoves) store.dailyBestMoves = current.moves
        }
        showBanner("Пломба. Квартал дышит светом.")
    }

    fun rotate(current: GameSession, index: Int, dir: Int) {
        if (current.finishedAt != null) return
        val before = current.level.tiles[index].rot
        if (!applyRotate(current.level, index, dir)) {
            showBanner("Клемма: «Крест не вращается, мастер. Регламент 2.7»")
            return
        }
        if (current.level.tiles[index].rot != before) current.moves += 1
        sound.rotate()
        if (isSolved(current.level)) finish(current)
    }

    fun useHint(current: GameSession) {
        if (current.finishedAt != null) return
        val h = hint(current.level) ?: return
        current.hintsUsed += 1
        current.hintIndex = h.index
        current.hintUntil = System.currentTimeMillis() + 2500
        sound.hint()
        showBanner("Клемма: «Позиция 4.2 — доверните фрагмент до упора»")
        scope.launch {
            delay(2600)
            if (current.hintIndex == h.index) current.hintIndex = null
        }
    }

    fun claimReward(current: GameSession) {
        if (current.reward.busy) return
        scope.launch {
            current.reward = current.reward.copy(busy = true, message = null)
            try {
                val address = walletAddress ?: wallet.connect().also {
                    walletAddress = it
                    store.walletAddress = it
                    store.walletAuthToken = wallet.authToken
                }
                val challenge = claimApi.start(address, BuildConfig.ENGINE_VERSION)
                val signature = wallet.signMessage(challenge.message.toByteArray())
                val result = claimApi.submit(
                    address = address,
                    nonce = challenge.nonce,
                    signatureBase58 = Base58.encode(signature),
                    rewardId = challenge.rewardId,
                )
                store.claimedCount += 1
                store.lastClaimJson = result.txSignature
                current.reward = RewardState(
                    message = when (result.status) {
                        "minted" -> "Пломба записана в реестр города"
                        "queued" -> "Награда в очереди: минт выполнится в ближайшем батче"
                        else -> "Статус: ${result.status}"
                    },
                    assetId = result.assetId,
                )
            } catch (e: Exception) {
                current.reward = RewardState(message = e.message ?: "Не удалось забрать награду", isError = true)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val current = session
        if (current == null || menuOpen) {
            MenuScreen(
                store = store,
                lore = lore,
                walletAddress = walletAddress,
                onPlay = { startLevel(store.level.coerceAtMost(75)) },
                onDaily = { startLevel(DAILY_CONFIG_LEVEL, Mode.DAILY) },
                onWallet = { walletOpen = true },
                onRegistry = { registryOpen = true },
                onShop = { shopOpen = true },
                onReset = {
                    store.reset()
                    session = null
                    walletAddress = null
                    showBanner("Реестр очищен. Город начинается заново.")
                },
            )
        } else {
            GameScreen(
                session = current,
                lore = lore,
                banner = banner,
                soundEnabled = sound.enabled,
                onRotate = { index, dir -> rotate(current, index, dir) },
                onHint = { useHint(current) },
                onReset = { startLevel(current.levelNumber, current.mode) },
                onMenu = { menuOpen = true },
                onToggleSound = {
                    sound.enabled = !sound.enabled
                    showBanner(if (sound.enabled) "Звук включён" else "Звук выключен")
                },
            )
        }

        if (shopOpen) {
            ShopOverlay(shop = shop, onClose = { shopOpen = false })
        }

        if (registryOpen) {
            RegistryOverlay(
                lore = lore,
                store = store,
                onClose = { registryOpen = false },
            )
        }

        if (walletOpen) {
            WalletOverlay(
                address = walletAddress,
                busy = walletBusy,
                error = walletError,
                mainnet = !BuildConfig.DEBUG,
                serverUrl = BuildConfig.SERVER_URL,
                onConnect = {
                    scope.launch {
                        walletBusy = true
                        walletError = null
                        try {
                            val address = wallet.connect()
                            walletAddress = address
                            store.walletAddress = address
                            store.walletAuthToken = wallet.authToken
                            showBanner("Кошелёк подключён: ${Base58.shorten(address)}")
                        } catch (e: Exception) {
                            walletError = e.message ?: "Не удалось подключить кошелёк"
                        } finally {
                            walletBusy = false
                        }
                    }
                },
                onDisconnect = {
                    scope.launch {
                        walletBusy = true
                        walletError = null
                        try {
                            wallet.disconnect()
                            walletAddress = null
                            store.walletAddress = null
                            store.walletAuthToken = null
                        } catch (e: Exception) {
                            walletError = e.message ?: "Не удалось отключить кошелёк"
                        } finally {
                            walletBusy = false
                        }
                    }
                },
                onClose = { walletOpen = false },
            )
        }

        current?.takeIf { it.finishedAt != null }?.let { finished ->
            WinOverlay(
                session = finished,
                lore = lore,
                walletAddress = walletAddress,
                onNext = {
                    if (finished.mode == Mode.DAILY) startLevel(DAILY_CONFIG_LEVEL, Mode.DAILY)
                    else startLevel(finished.levelNumber + 1)
                },
                onMenu = { menuOpen = true },
                onClaim = { claimReward(finished) },
                onWallet = { walletOpen = true },
            )
        }
    }
}

/* ─────────────────────────────── Основные экраны ─────────────────────────────── */

@Composable
private fun MenuScreen(
    store: ProgressStore,
    lore: Lore?,
    walletAddress: String?,
    onPlay: () -> Unit,
    onDaily: () -> Unit,
    onWallet: () -> Unit,
    onRegistry: () -> Unit,
    onShop: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        LogoMark()
        Spacer(Modifier.height(12.dp))
        Text("АМПЕРГРАД", style = MaterialTheme.typography.displaySmall, color = Color(0xFFEAFFF9))
        Text(
            "ВЕРНИ ГОРОДУ ТОК",
            style = MaterialTheme.typography.labelSmall,
            color = Grad.Mint,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Собранных кварталов: ${store.solvedCount()} · пломб: ${store.totalStars()} · стрик: ${store.streak} дн.",
            style = MaterialTheme.typography.bodyMedium,
            color = Grad.InkDim,
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Grad.Mint, contentColor = Color(0xFF04211D)),
        ) {
            Text("НАЧАТЬ СМЕНУ · УРОВЕНЬ ${store.level.coerceAtMost(75)}")
        }
        lore?.quarter(store.level.coerceAtMost(75))?.let { quarter ->
            Spacer(Modifier.height(6.dp))
            Text(
                "${quarter.code} · ${quarter.name}",
                style = MaterialTheme.typography.labelSmall,
                color = Grad.InkDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDaily, modifier = Modifier.fillMaxWidth()) {
            Text("ЧЕЛЛЕНДЖ ДНЯ")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWallet, modifier = Modifier.fillMaxWidth()) {
            Text(if (walletAddress == null) "ПОДКЛЮЧИТЬ КОШЕЛЁК" else "КОШЕЛЁК · ${Base58.shorten(walletAddress)}")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRegistry, modifier = Modifier.fillMaxWidth()) {
            Text("РЕЕСТР ГОРОДА")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onShop, modifier = Modifier.fillMaxWidth()) {
            Text("ВИТРИНА")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset) {
            Text("НАЧАТЬ ЗАНОВО", color = Grad.InkDim)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Гильдия Ключа: свет — это ресурс, а память — плата. " +
                "Награды за кварталы — сжатые NFT (cNFT) в реестре города; игра не требует токенов " +
                "и не просит подписывать транзакции за SOL.",
            style = MaterialTheme.typography.bodySmall,
            color = Grad.InkDim,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Движок правил: собственный код проекта. Идея жанра — свободная; код PipeWalker (MIT) " +
                "не используется, ассеты — оригинальные.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4B5B73),
        )
    }
}

@Composable
private fun GameScreen(
    session: GameSession,
    lore: Lore?,
    banner: String?,
    soundEnabled: Boolean,
    onRotate: (Int, Int) -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onMenu: () -> Unit,
    onToggleSound: () -> Unit,
) {
    val tier: Tier = tierOf(session.levelNumber)
    val quarter = lore?.quarter(session.levelNumber)
    val accent = if (session.mode == Mode.DAILY) Grad.Gold else tier.accent
    val powered = poweredCells(session.level)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = if (session.mode == Mode.DAILY) "ЧЕЛЛЕНДЖ ДНЯ" else tier.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                Text(
                    text = when {
                        session.mode == Mode.DAILY ->
                            "ОБЩИЙ КВАРТАЛ ${session.level.size}×${session.level.size}"

                        quarter != null ->
                            "УРОВЕНЬ ${session.levelNumber} · ${quarter.name}"

                        else -> "УРОВЕНЬ ${session.levelNumber} · КВАРТАЛ ${session.level.size}×${session.level.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Grad.InkDim,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("ХОДЫ", session.moves.toString())
                Stat("ВРЕМЯ", formatDuration((session.finishedAt ?: System.currentTimeMillis()) - session.startedAt))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, Grad.Line, RoundedCornerShape(16.dp))
                .background(Color(0xFF070B13), RoundedCornerShape(16.dp)),
        ) {
            Board(
                level = session.level,
                powered = powered,
                accent = accent,
                hintIndex = if ((session.hintUntil) > System.currentTimeMillis()) session.hintIndex else null,
                solving = session.solving,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                onRotate = onRotate,
            )
            banner?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color(0xDD060A12), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Grad.Ink,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("СБРОС") }
            OutlinedButton(onClick = onHint, modifier = Modifier.weight(1f)) { Text("ПОДСКАЗКА") }
            OutlinedButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text("МЕНЮ") }
            OutlinedButton(onClick = onToggleSound, modifier = Modifier.width(56.dp)) {
                Text(if (soundEnabled) "♪" else "×")
            }
        }
    }
}

@Composable
private fun Stat(key: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(key, style = MaterialTheme.typography.labelSmall, color = Grad.InkDim)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Grad.Ink)
    }
}

@Composable
private fun WinOverlay(
    session: GameSession,
    lore: Lore?,
    walletAddress: String?,
    onNext: () -> Unit,
    onMenu: () -> Unit,
    onClaim: () -> Unit,
    onWallet: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD903050A))
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Grad.Panel),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "ПЛОМБА",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF04211D),
                    modifier = Modifier
                        .background(Grad.Mint, RoundedCornerShape(99.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Квартал дышит светом", style = MaterialTheme.typography.titleMedium, color = Color(0xFFEAFFF9))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "★".repeat(session.stars) + "☆".repeat(3 - session.stars),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (session.stars > 0) Grad.Gold else Grad.Metal,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Ходы: ${session.moves} (план ${session.level.parMoves}) · " +
                        "Время: ${formatDuration((session.finishedAt ?: System.currentTimeMillis()) - session.startedAt)} · " +
                        "Подсказки: ${session.hintsUsed}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grad.InkDim,
                    textAlign = TextAlign.Center,
                )

                if (session.mode == Mode.LEVEL) {
                    lore?.registryForLevel(session.levelNumber)?.let { entry ->
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A1018), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Grad.Mint,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "«${entry.text}»",
                                style = MaterialTheme.typography.bodySmall,
                                color = Grad.Ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Открыта как ${entry.id} · ${entry.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Grad.InkDim,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (walletAddress == null) {
                    OutlinedButton(onClick = onWallet, modifier = Modifier.fillMaxWidth()) {
                        Text("ЗАБРАТЬ ПЛОМБУ (НУЖЕН КОШЕЛЁК)")
                    }
                } else {
                    Button(
                        onClick = onClaim,
                        enabled = !session.reward.busy && session.reward.assetId == null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Grad.Gold, contentColor = Color(0xFF231703)),
                    ) {
                        Text(if (session.reward.busy) "ЗАПИСЬ В РЕЕСТР…" else "ЗАБРАТЬ ПЛОМБУ В РЕЕСТР")
                    }
                }
                session.reward.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (session.reward.isError) Grad.Danger else Grad.Mint,
                        textAlign = TextAlign.Center,
                    )
                }
                session.reward.assetId?.let { asset ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Реестр: ${Base58.shorten(asset, 6, 6)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Grad.InkDim,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Grad.Mint, contentColor = Color(0xFF04211D)),
                    ) {
                        Text(if (session.mode == Mode.DAILY) "ЕЩЁ РАЗ" else "СЛЕДУЮЩИЙ КВАРТАЛ")
                    }
                    OutlinedButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text("В МЕНЮ") }
                }
            }
        }
    }
}

/** Реестр города: страницы, открытые за пройденные уровни. */
@Composable
private fun RegistryOverlay(
    lore: Lore?,
    store: ProgressStore,
    onClose: () -> Unit,
) {
    val pages = (1 until store.level.coerceAtMost(Lore.TOTAL_LEVELS + 1)).mapNotNull { level ->
        lore?.registryForLevel(level)
    }
    val total = lore?.registry?.size ?: 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF203050A))
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("РЕЕСТР ГОРОДА", style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                    Text(
                        "открыто ${pages.size} из $total",
                        style = MaterialTheme.typography.labelSmall,
                        color = Grad.InkDim,
                    )
                }
                TextButton(onClick = onClose) { Text("ЗАКРЫТЬ", color = Grad.InkDim) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Гильдия не выдаёт реестр целиком: страницы открываются за собранные кварталы. " +
                    "Каждая — с подписью того, кто говорил.",
                style = MaterialTheme.typography.bodySmall,
                color = Grad.InkDim,
            )
            Spacer(Modifier.height(16.dp))

            if (pages.isEmpty()) {
                Text(
                    "Пока пусто. Соберите первый квартал — откроется первая страница.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grad.Metal,
                )
            }

            pages.forEach { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Grad.Panel),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.label, style = MaterialTheme.typography.labelSmall, color = Grad.Mint)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, color = Grad.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text("«${entry.text}»", style = MaterialTheme.typography.bodySmall, color = Grad.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Открыта: ${entry.source} · ${entry.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Grad.InkDim,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Витрина косметики: только вид. Каталог — общий с сервером (/shop). */
@Composable
private fun ShopOverlay(shop: Shop?, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF203050A))
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ВИТРИНА", style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                TextButton(onClick = onClose) { Text("ЗАКРЫТЬ", color = Grad.InkDim) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Косметика меняет только вид: цвет тока, тему поля, источник, печать. " +
                    "Правила, время и награды она не трогает — ни один предмет не даёт преимущества.",
                style = MaterialTheme.typography.bodySmall,
                color = Grad.InkDim,
            )
            Spacer(Modifier.height(16.dp))

            if (shop == null) {
                Text(
                    "Каталог не попал в сборку. Выполните npm run content и пересоберите приложение.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grad.Metal,
                )
            } else {
                ShopSection("НАГРАДЫ ЗА ИГРУ", shop.rewards, "забрать пломбой в реестре")
                ShopSection("ЗА МОНЕТЫ", shop.coins, "монеты зарабатываются в игре, не покупаются")
                ShopSection("ВИТРИНА", shop.store, "покупки идут в фонд сезона и призы")
                ShopSection("BATTLE PASS", shop.seasonPass, "сезонный трек, 8 недель")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ShopSection(title: String, items: List<CosmeticItem>, note: String) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelSmall, color = Grad.Gold)
    Spacer(Modifier.height(6.dp))
    Text(note, style = MaterialTheme.typography.bodySmall, color = Grad.InkDim)
    Spacer(Modifier.height(8.dp))
    items.forEach { item ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Grad.Panel),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = Grad.Ink)
                    Text(item.priceLabel, style = MaterialTheme.typography.labelSmall, color = Grad.Mint)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.rarityLabel} · ${item.how}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grad.InkDim,
                )
                Spacer(Modifier.height(6.dp))
                Text("«${item.lore}»", style = MaterialTheme.typography.bodySmall, color = Grad.Ink)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun WalletOverlay(
    address: String?,
    busy: Boolean,
    error: String?,
    mainnet: Boolean,
    serverUrl: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD903050A))
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Grad.Panel),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("КОШЕЛЁК", style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (address == null) {
                        "Кошелёк подключается только для того, чтобы забрать награду. " +
                            "Игра не требует токенов и не подписывает транзакции за SOL."
                    } else {
                        "Подключено: ${Base58.shorten(address, 6, 6)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grad.InkDim,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Работает с Seed Vault Wallet на Seeker, Phantom, Solflare, Backpack — " +
                        "подключение идёт через Mobile Wallet Adapter, поэтому приложение не зависит от конкретного кошелька.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grad.InkDim,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Сеть: ${if (mainnet) "mainnet-beta" else "devnet"} · Сервер клейма: $serverUrl",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4B5B73),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Grad.Danger)
                }
                Spacer(Modifier.height(16.dp))
                if (address == null) {
                    Button(
                        onClick = onConnect,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Grad.Mint, contentColor = Color(0xFF04211D)),
                    ) {
                        Text(if (busy) "ОЖИДАНИЕ КОШЕЛЬКА…" else "ПОДКЛЮЧИТЬ КОШЕЛЁК")
                    }
                } else {
                    OutlinedButton(onClick = onDisconnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("ОТКЛЮЧИТЬ")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("ЗАКРЫТЬ", color = Grad.InkDim)
                }
            }
        }
    }
}

/* ─────────────────────────────── Мелочи ─────────────────────────────── */

/** Знак гильдии: незамкнутый контур квартала с искрой. */
@Composable
private fun LogoMark() {
    Canvas(Modifier.size(52.dp)) {
        val stroke = 3f
        drawRoundRect(
            color = Grad.Mint,
            topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
            size = Size(size.width * 0.8f, size.height * 0.8f),
            cornerRadius = CornerRadius(size.width * 0.18f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = Grad.Mint,
            start = Offset(size.width * 0.5f, size.height * 0.2f),
            end = Offset(size.width * 0.5f, size.height * 0.42f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Grad.Gold,
            start = Offset(size.width * 0.42f, size.height * 0.46f),
            end = Offset(size.width * 0.62f, size.height * 0.46f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Grad.Gold,
            start = Offset(size.width * 0.52f, size.height * 0.46f),
            end = Offset(size.width * 0.44f, size.height * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
