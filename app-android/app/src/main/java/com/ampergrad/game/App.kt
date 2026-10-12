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
import com.ampergrad.game.net.DailyScore
import com.ampergrad.game.net.LeaderboardCodec
import com.ampergrad.game.net.PlayProof
import com.ampergrad.game.net.Turn
import com.ampergrad.game.solana.Base58
import com.ampergrad.game.solana.MwaWallet
import com.ampergrad.game.ui.Board
import com.ampergrad.game.ui.Grad
import com.ampergrad.game.ui.Tier
import com.ampergrad.game.content.CosmeticItem
import com.ampergrad.game.content.Lang
import com.ampergrad.game.content.UiStrings
import com.ampergrad.game.content.Lore
import com.ampergrad.game.content.BoardSkin
import com.ampergrad.game.ui.Art
import com.ampergrad.game.ui.ArtTier
import com.ampergrad.game.ui.artTierIndex
import com.ampergrad.game.content.Shop
import com.ampergrad.game.content.Wardrobe
import com.ampergrad.game.content.of
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
    /** Последовательность поворотов: доказательство решения для сервера. */
    val turns = mutableListOf<Turn>()
    /** Место в рейтинге дня (после отправки результата) и текст о результате. */
    var rank by mutableStateOf<Int?>(null)
    var scoreNote by mutableStateOf<String?>(null)
    var scoreBusy by mutableStateOf(false)
    /** Сколько ячеек квартала сейчас под током — для звука «вдоха». */
    var liveCells by mutableIntStateOf(0)
    val startedAt: Long = System.currentTimeMillis()

    /** Доказательство текущей партии: для уровня или для челленджа дня. */
    fun proof(engine: String): PlayProof = if (mode == Mode.DAILY) {
        PlayProof.daily(day = startedAt / 86_400_000L, turns = turns.toList(), engine = engine)
    } else {
        PlayProof.level(levelNumber = levelNumber, turns = turns.toList(), engine = engine)
    }
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
    // Арт поля: спрайты и фоны кладёт в assets gradle-задача syncContent, поэтому
    // в клоне без Node игра всё равно стартует — просто в режиме схемы.
    val art = remember { Art(context) }
    var artReady by remember { mutableStateOf(false) }
    var artOn by remember { mutableStateOf(true) }
    var artBackground by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        art.load()
        artReady = art.ready
        if (!art.ready) art.error?.let { android.util.Log.i("амперград", "арт не поднялся: $it") }
    }
    val claimApi = remember { ClaimClient(BuildConfig.SERVER_URL) }
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(Lang.of(store.lang ?: Lang.systemDefault().code)) }
    var coins by remember { mutableIntStateOf(store.coins) }
    var wardrobe by remember { mutableStateOf(currentWardrobe(store, shop)) }
    val skin = remember(wardrobe) { BoardSkin.of(wardrobe) }
    // Цвет тока из купленной палитры: в арт-режиме среда иначе всегда цвета яруса,
    // и палитра пропадала бы — а монеты за неё уже заплачены.
    val paletteFlow = remember(wardrobe) { if (wardrobe.palette != null) skin.current else null }
    fun refreshWardrobe() {
        wardrobe = currentWardrobe(store, shop)
    }
    val t = remember(lang) { UiStrings.of(lang) }

    var session by remember { mutableStateOf<GameSession?>(null) }
    var menuOpen by remember { mutableStateOf(true) }
    var walletOpen by remember { mutableStateOf(false) }
    var registryOpen by remember { mutableStateOf(false) }
    var shopOpen by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var walletAddress by remember { mutableStateOf(store.walletAddress) }
    var walletError by remember { mutableStateOf<String?>(null) }
    var walletBusy by remember { mutableStateOf(false) }
    var boardOpen by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf<List<DailyScore>>(emptyList()) }
    var boardLoading by remember { mutableStateOf(false) }
    var boardError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        wallet.restore(store.walletAuthToken, store.walletAddress)
        walletAddress = wallet.address
        board = LeaderboardCodec.decode(store.leaderboardJson)
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
        session = GameSession(mode, levelNumber, level).also { it.liveCells = 0 }
        menuOpen = false
        walletOpen = false
        banner = when (mode) {
            Mode.DAILY -> t.bannerDailyStart
            Mode.LEVEL -> t.bannerLevelStart(level.receivers.size)
        }
    }

    /**
     * Отправляет результат челленджа дня в рейтинг.
     *
     * Сервер проверяет подпись кошелька и проигрывает ходы движком, поэтому
     * в рейтинг попадает только честно собранный квартал. Без кошелька игра
     * продолжает работать: результат просто остаётся на устройстве.
     */
    fun submitDaily(current: GameSession) {
        if (current.mode != Mode.DAILY || current.scoreBusy || current.turns.isEmpty()) return
        val proof = current.proof(BuildConfig.ENGINE_VERSION)
        scope.launch {
            current.scoreBusy = true
            current.scoreNote = null
            try {
                val address = walletAddress ?: wallet.connect().also {
                    walletAddress = it
                    store.walletAddress = it
                    store.walletAuthToken = wallet.authToken
                }
                val challenge = claimApi.dailyStart(
                    address = address,
                    day = proof.day ?: 0L,
                    moves = proof.moves,
                    engineVersion = BuildConfig.ENGINE_VERSION,
                )
                val signature = wallet.signMessage(challenge.message.toByteArray())
                val result = claimApi.dailySubmit(
                    address = address,
                    nonce = challenge.nonce,
                    signatureBase58 = Base58.encode(signature),
                    proof = proof,
                )
                current.rank = result.rank
                current.scoreNote = if (result.rank != null) {
                    t.boardRank(result.rank, maxOf(result.leaderboard.size, result.rank))
                } else {
                    t.boardAccepted(result.moves)
                }
                store.leaderboardJson = encodeBoard(result.leaderboard)
            } catch (e: Exception) {
                current.scoreNote = e.message ?: t.boardSendFailed
            } finally {
                current.scoreBusy = false
            }
        }
    }

    fun loadLeaderboard() {
        scope.launch {
            boardLoading = true
            try {
                board = claimApi.leaderboard(System.currentTimeMillis() / 86_400_000L)
                boardError = null
                store.leaderboardJson = encodeBoard(board)
            } catch (e: Exception) {
                boardError = e.message ?: t.boardError
            } finally {
                boardLoading = false
            }
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
        val earned = (shop?.economy ?: com.ampergrad.game.content.EconomyRules.Default)
            .coinsForSolve(stars, daily = current.mode == Mode.DAILY)
        store.addCoins(earned)
        coins = store.coins
        if (current.mode == Mode.LEVEL) {
            store.recordWin(current.levelNumber, stars, current.moves, day)
        } else {
            store.recordWin(0, stars, current.moves, day)
            store.dailyDoneDay = day
            if (store.dailyBestMoves == 0 || current.moves < store.dailyBestMoves) store.dailyBestMoves = current.moves
            if (walletAddress != null) submitDaily(current)
        }
        showBanner("${t.bannerSealed} · ${t.coinsEarned(earned)}")
    }

    fun rotate(current: GameSession, index: Int, dir: Int) {
        if (current.finishedAt != null) return
        val before = current.level.tiles[index].rot
        if (!applyRotate(current.level, index, dir)) {
            showBanner(t.bannerCrossFixed)
            return
        }
        if (current.level.tiles[index].rot != before) {
            current.moves += 1
            current.turns += Turn(index, dir)
        }
        sound.rotate()
        // «Вдох»: если поворот подключил к шине новую ячейку, узел отвечает звуком.
        val live = poweredCells(current.level).count { it }
        if (live > current.liveCells) sound.connect()
        current.liveCells = live
        if (isSolved(current.level)) finish(current)
    }

    fun useHint(current: GameSession) {
        if (current.finishedAt != null) return
        val h = hint(current.level) ?: return
        current.hintsUsed += 1
        current.liveCells = poweredCells(current.level).count { it }
        current.hintIndex = h.index
        current.hintUntil = System.currentTimeMillis() + 2500
        sound.hint()
        showBanner(t.bannerHint)
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
                val proof = current.proof(BuildConfig.ENGINE_VERSION)
                val challenge = claimApi.start(address, BuildConfig.ENGINE_VERSION, proof)
                val signature = wallet.signMessage(challenge.message.toByteArray())
                val result = claimApi.submit(
                    address = address,
                    nonce = challenge.nonce,
                    signatureBase58 = Base58.encode(signature),
                    rewardId = challenge.rewardId,
                    proof = proof,
                )
                store.claimedCount += 1
                store.lastClaimJson = result.txSignature
                // Награда за игру сразу становится доступной в гардеробе.
                store.own(challenge.rewardId)
                refreshWardrobe()
                current.reward = RewardState(
                    message = when (result.status) {
                        "minted" -> t.claimMinted
                        "queued" -> t.claimQueued
                        else -> t.claimStatus(result.status)
                    },
                    assetId = result.assetId,
                )
            } catch (e: Exception) {
                current.reward = RewardState(
                    message = e.message ?: t.claimFailed,
                    isError = true,
                )
            }
        }
    }

    // Арт-ярус текущего квартала и его фон. Ярус берём по номеру уровня (те же
    // 15 уровней на ярус, что в каноне); внутри яруса уровни чередуют два кадра
    // фона, оба держим в памяти — декодирование 720×1290 на каждый переход дорого.
    val currentTier: ArtTier? = if (artReady && artOn) {
        val tiers = art.pack?.tiers ?: emptyList()
        val number = session?.levelNumber
        if (number != null && tiers.isNotEmpty()) tiers.getOrNull(artTierIndex(number, tiers.size)) else null
    } else null
    LaunchedEffect(currentTier?.id, session?.levelNumber) {
        val tier = currentTier
        if (tier == null) {
            artBackground = null
            return@LaunchedEffect
        }
        val variant = (session?.levelNumber ?: 1) % 2
        // Сначала то, что уже в памяти: тогда смена уровня внутри яруса не мигает.
        art.cachedBackground(tier, variant)?.let { artBackground = it }
        // Декодирование кадра 720×1290 — только вне главного потока. Старый фон
        // остаётся на экране, пока не готов новый: пустота замечалась бы сильнее.
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            art.background(tier, variant)
        }
        if (loaded != null) artBackground = loaded
        // Второй кадр яруса грузим заранее: следующий уровень возьмёт его из кэша.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            art.background(tier, 1 - variant)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val current = session
        if (current == null || menuOpen) {
            MenuScreen(
                store = store,
                lore = lore,
                t = t,
                lang = lang,
                onToggleLang = {
                    lang = if (lang == Lang.RU) Lang.EN else Lang.RU
                    store.lang = lang.code
                },
                worn = wardrobe,
                coins = coins,
                walletAddress = walletAddress,
                onPlay = { startLevel(store.level.coerceAtMost(75)) },
                onDaily = { startLevel(DAILY_CONFIG_LEVEL, Mode.DAILY) },
                onWallet = { walletOpen = true },
                onRegistry = { registryOpen = true },
                onShop = { shopOpen = true },
                onBoard = {
                    boardOpen = true
                    loadLeaderboard()
                },
                onReset = {
                    store.reset()
                    session = null
                    walletAddress = null
                    showBanner(t.bannerReset)
                },
            )
        } else {
            GameScreen(
                session = current,
                lore = lore,
                t = t,
                skin = skin,
                banner = banner,
                soundEnabled = sound.enabled,
                art = if (artReady && artOn) art else null,
                artOn = artOn,
                artTier = currentTier,
                background = artBackground,
                artFlow = paletteFlow,
                onRotate = { index, dir -> rotate(current, index, dir) },
                onHint = { useHint(current) },
                onReset = { startLevel(current.levelNumber, current.mode) },
                onMenu = { menuOpen = true },
                onToggleSound = {
                    sound.enabled = !sound.enabled
                    showBanner(if (sound.enabled) t.soundOn else t.soundOff)
                },
                onToggleArt = {
                    if (artReady) {
                        artOn = !artOn
                        showBanner(if (artOn) t.artOn else t.artOff)
                    } else {
                        // Серая кнопка молчала бы — игрок не понял бы, почему вид
                        // не меняется. Говорим прямо: арт не доехал в этой сборке.
                        showBanner(t.artMissing)
                    }
                },
            )
        }

        if (boardOpen) {
            LeaderboardOverlay(
                t = t,
                day = System.currentTimeMillis() / 86_400_000L,
                entries = board,
                loading = boardLoading,
                error = boardError,
                onRefresh = { loadLeaderboard() },
                onClose = { boardOpen = false },
            )
        }

        if (shopOpen) {
            ShopOverlay(
                shop = shop,
                t = t,
                coins = coins,
                store = store,
                onBuy = { item ->
                    val price = item.priceCoins ?: 0
                    if (shop != null && shop.coinPurchasable(item) && coins >= price) {
                        store.addCoins(-price)
                        store.own(item.id)
                        store.equip(item.id, item.type)
                        coins = store.coins
                        refreshWardrobe()
                        showBanner(t.shopPurchased)
                    }
                },
                onEquip = { item ->
                    val current = store.equippedId(item.type)
                    store.equip(if (current == item.id) null else item.id, item.type)
                    refreshWardrobe()
                },
                onClose = { shopOpen = false },
            )
        }

        if (registryOpen) {
            RegistryOverlay(
                lore = lore,
                store = store,
                t = t,
                onClose = { registryOpen = false },
            )
        }

        if (walletOpen) {
            WalletOverlay(
                t = t,
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
                            showBanner(t.bannerWalletConnected(Base58.shorten(address)))
                        } catch (e: Exception) {
                            walletError = e.message ?: t.walletConnectFailed
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
                            walletError = e.message ?: t.walletDisconnectFailed
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
                t = t,
                art = if (artReady && artOn) art else null,
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

/** Что надето сейчас: собираем из прогресса и каталога (косметика = только вид). */
private fun currentWardrobe(store: ProgressStore, shop: Shop?): Wardrobe {
    fun worn(id: String?) = id?.let { shop?.byId(it) }
    return Wardrobe(
        palette = worn(store.equippedPalette),
        theme = worn(store.equippedTheme),
        source = worn(store.equippedSource),
        mark = worn(store.equippedMark),
    )
}

/** Строка «надето: …» для меню: перечисляем слоты, которые заняты. */
private fun wornLabel(worn: Wardrobe, t: UiStrings): String {
    val names = listOfNotNull(
        worn.palette?.name(t.lang),
        worn.theme?.name(t.lang),
        worn.source?.name(t.lang),
        worn.mark?.name(t.lang),
    )
    return if (names.isEmpty()) t.menuNothingWorn else t.menuWearing(names.joinToString(", "))
}

/** Разбор/сборка рейтинга — в net/LeaderboardCodec, здесь короткая обёртка. */
private fun encodeBoard(scores: List<DailyScore>): String = LeaderboardCodec.encode(scores)

/* ─────────────────────────────── Основные экраны ─────────────────────────────── */

@Composable
private fun MenuScreen(
    store: ProgressStore,
    lore: Lore?,
    t: UiStrings,
    lang: Lang,
    onToggleLang: () -> Unit,
    worn: Wardrobe,
    coins: Int,
    walletAddress: String?,
    onPlay: () -> Unit,
    onDaily: () -> Unit,
    onWallet: () -> Unit,
    onRegistry: () -> Unit,
    onShop: () -> Unit,
    onBoard: () -> Unit,
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
            t.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = Grad.Mint,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = t.menuProgress(store.solvedCount(), store.totalStars(), store.streak) +
                " · " + t.menuCoins(coins),
            style = MaterialTheme.typography.bodyMedium,
            color = Grad.InkDim,
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Grad.Mint, contentColor = Color(0xFF04211D)),
        ) {
            Text(t.startShift(store.level.coerceAtMost(75)))
        }
        Text(
            wornLabel(worn, t),
            style = MaterialTheme.typography.labelSmall,
            color = if (worn.isEmpty) Grad.Metal else Grad.Mint,
        )
        Spacer(Modifier.height(10.dp))
        lore?.quarter(store.level.coerceAtMost(75))?.let { quarter ->
            Spacer(Modifier.height(6.dp))
            Text(
                "${quarter.code} · ${quarter.name(lang)}",
                style = MaterialTheme.typography.labelSmall,
                color = Grad.InkDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDaily, modifier = Modifier.fillMaxWidth()) {
            Text(t.dailyChallenge)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWallet, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (walletAddress == null) t.connectWallet
                else t.walletShort(Base58.shorten(walletAddress)),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRegistry, modifier = Modifier.fillMaxWidth()) {
            Text(t.registryButton)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onShop, modifier = Modifier.fillMaxWidth()) {
            Text(t.shopButton)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBoard, modifier = Modifier.fillMaxWidth()) {
            Text(t.boardButton)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset) {
            Text(t.resetButton, color = Grad.InkDim)
        }
        TextButton(onClick = onToggleLang) {
            Text(if (lang == Lang.RU) "ENGLISH" else "РУССКИЙ", color = Grad.Metal)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            t.menuAbout,
            style = MaterialTheme.typography.bodySmall,
            color = Grad.InkDim,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            t.menuEngineNote,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4B5B73),
        )
    }
}

@Composable
private fun GameScreen(
    session: GameSession,
    lore: Lore?,
    t: UiStrings,
    skin: BoardSkin,
    banner: String?,
    soundEnabled: Boolean,
    art: Art?,
    artOn: Boolean,
    artTier: ArtTier?,
    background: androidx.compose.ui.graphics.ImageBitmap?,
    artFlow: Color? = null,
    onRotate: (Int, Int) -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onMenu: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleArt: () -> Unit,
) {
    val tier: Tier = tierOf(session.levelNumber)
    val quarter = lore?.quarter(session.levelNumber)
    // Акцент берём из арта, когда поле нарисовано им: иначе HUD светится одним
    // цветом (старая схематичная палитра), а трубы — другим (палитра Guttercaps).
    val accent = when {
        session.mode == Mode.DAILY -> Grad.Gold
        artTier != null -> artTier.flow
        else -> tier.accent
    }
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
                    text = if (session.mode == Mode.DAILY) t.daily else t.tierName(tier.order),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                Text(
                    text = when {
                        session.mode == Mode.DAILY -> t.commonQuarter(session.level.size)

                        quarter != null -> "${t.level(session.levelNumber)} · ${quarter.name(t.lang)}"

                        else -> "${t.level(session.levelNumber)} · ${session.level.size}×${session.level.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Grad.InkDim,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(t.moves, session.moves.toString())
                Stat(t.time, formatDuration((session.finishedAt ?: System.currentTimeMillis()) - session.startedAt))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, Grad.Line, RoundedCornerShape(16.dp))
                .background(skin.field, RoundedCornerShape(16.dp)),
        ) {
            Board(
                level = session.level,
                powered = powered,
                accent = accent,
                skin = skin,
                hintIndex = if ((session.hintUntil) > System.currentTimeMillis()) session.hintIndex else null,
                solving = session.solving,
                art = art,
                artTier = artTier,
                background = background,
                artFlow = artFlow,
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
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(t.reset) }
            OutlinedButton(onClick = onHint, modifier = Modifier.weight(1f)) { Text(t.hint) }
            OutlinedButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text(t.menu) }
            OutlinedButton(
                onClick = onToggleArt,
                // Подписи, а не символы ▩/▢: этих глифов может не быть в шрифте
                // устройства, и вместо переключателя игрок увидит «тофу».
                modifier = Modifier.width(72.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(if (artOn) t.artShort else t.schemeShort, style = MaterialTheme.typography.labelSmall)
            }
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
    t: UiStrings,
    art: Art?,
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
                    t.seal,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF04211D),
                    modifier = Modifier
                        .background(Grad.Mint, RoundedCornerShape(99.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(t.districtAlive, style = MaterialTheme.typography.titleMedium, color = Color(0xFFEAFFF9))
                Spacer(Modifier.height(10.dp))
                art?.reward(session.stars)?.let { seal ->
                    androidx.compose.foundation.Image(
                        bitmap = seal,
                        contentDescription = t.seal,
                        modifier = Modifier.size(104.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = "★".repeat(session.stars) + "☆".repeat(3 - session.stars),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (session.stars > 0) Grad.Gold else Grad.Metal,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    t.winStats(
                        moves = session.moves,
                        par = session.level.parMoves,
                        time = formatDuration((session.finishedAt ?: System.currentTimeMillis()) - session.startedAt),
                        hints = session.hintsUsed,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Grad.InkDim,
                    textAlign = TextAlign.Center,
                )

                if (session.mode == Mode.DAILY) {
                    val note = when {
                        session.scoreBusy -> t.boardSubmitting
                        session.scoreNote != null -> session.scoreNote
                        walletAddress == null -> t.boardConnect
                        else -> null
                    }
                    if (note != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (session.scoreNote != null) Grad.Gold else Grad.InkDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

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
                                t.registryLabel(entry.code, entry.speaker(t.lang)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Grad.Mint,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "«${entry.text(t.lang)}»",
                                style = MaterialTheme.typography.bodySmall,
                                color = Grad.Ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                t.openEntry(entry.id, entry.title(t.lang)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Grad.InkDim,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (walletAddress == null) {
                    OutlinedButton(onClick = onWallet, modifier = Modifier.fillMaxWidth()) {
                        Text(t.claimNeedsWallet)
                    }
                } else {
                    Button(
                        onClick = onClaim,
                        enabled = !session.reward.busy && session.reward.assetId == null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Grad.Gold, contentColor = Color(0xFF231703)),
                    ) {
                        Text(if (session.reward.busy) t.claimBusy else t.claimButton)
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
                        t.registryRef(Base58.shorten(asset, 6, 6)),
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
                        Text(if (session.mode == Mode.DAILY) t.again else t.nextQuarter)
                    }
                    OutlinedButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text(t.toMenu) }
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
    t: UiStrings,
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
                    Text(t.registryTitle, style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                    Text(
                        t.registryProgress(pages.size, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = Grad.InkDim,
                    )
                }
                TextButton(onClick = onClose) { Text(t.close, color = Grad.InkDim) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                t.registryNote,
                style = MaterialTheme.typography.bodySmall,
                color = Grad.InkDim,
            )
            Spacer(Modifier.height(16.dp))

            if (pages.isEmpty()) {
                Text(
                    t.registryEmpty,
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
                        Text(
                            t.registryLabel(entry.code, entry.speaker(t.lang)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Grad.Mint,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(entry.title(t.lang), style = MaterialTheme.typography.titleMedium, color = Grad.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text("«${entry.text(t.lang)}»", style = MaterialTheme.typography.bodySmall, color = Grad.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t.registryOpenedBy(entry.source(t.lang), entry.id),
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
/** Рейтинг дня: один квартал для всех, сортировка по числу ходов. */
@Composable
private fun LeaderboardOverlay(
    t: UiStrings,
    day: Long,
    entries: List<DailyScore>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
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
                Column {
                    Text(t.boardTitle, style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                    Text(t.boardDay(day), style = MaterialTheme.typography.labelSmall, color = Grad.InkDim)
                }
                TextButton(onClick = onClose) { Text(t.close, color = Grad.InkDim) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                t.boardNote,
                style = MaterialTheme.typography.bodySmall,
                color = Grad.InkDim,
            )
            Spacer(Modifier.height(14.dp))

            when {
                loading -> Text(t.boardLoading, style = MaterialTheme.typography.bodyMedium, color = Grad.Metal)
                error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = Grad.Danger)
                entries.isEmpty() -> Text(
                    t.boardEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grad.Metal,
                )
                else -> entries.forEach { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Grad.Panel),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${row.rank}. ${row.wallet}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.rank == 1) Grad.Gold else Grad.Ink,
                            )
                            Text(
                                t.boardMoves(row.moves),
                                style = MaterialTheme.typography.labelSmall,
                                color = Grad.InkDim,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(t.boardRefresh)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ShopOverlay(
    shop: Shop?,
    t: UiStrings,
    coins: Int,
    store: ProgressStore,
    onBuy: (CosmeticItem) -> Unit,
    onEquip: (CosmeticItem) -> Unit,
    onClose: () -> Unit,
) {
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
                Column {
                    Text(t.shopTitle, style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                    Text(t.coins(coins), style = MaterialTheme.typography.labelSmall, color = Grad.Gold)
                }
                TextButton(onClick = onClose) { Text(t.close, color = Grad.InkDim) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                t.shopNote,
                style = MaterialTheme.typography.bodySmall,
                color = Grad.InkDim,
            )
            Spacer(Modifier.height(4.dp))
            Text(t.shopInUse, style = MaterialTheme.typography.bodySmall, color = Grad.Metal)
            Spacer(Modifier.height(16.dp))

            if (shop == null) {
                Text(
                    t.shopMissing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grad.Metal,
                )
            } else {
                ShopSection(
                    title = t.shopSectionRewards,
                    items = shop.rewards,
                    note = t.shopNoteRewards,
                    t = t,
                    state = { item -> cosmeticState(item, store, coins, shop) },
                    onBuy = onBuy,
                    onEquip = onEquip,
                )
                ShopSection(
                    title = t.shopSectionCoins,
                    items = shop.coins,
                    note = t.shopNoteCoins,
                    t = t,
                    state = { item -> cosmeticState(item, store, coins, shop) },
                    onBuy = onBuy,
                    onEquip = onEquip,
                )
                ShopSection(
                    title = t.shopSectionStore,
                    items = shop.store,
                    note = t.shopNoteStore,
                    t = t,
                    state = { item -> cosmeticState(item, store, coins, shop) },
                    onBuy = onBuy,
                    onEquip = onEquip,
                )
                ShopSection(
                    title = t.shopSectionPass,
                    items = shop.seasonPass,
                    note = t.shopNotePass,
                    t = t,
                    state = { item -> cosmeticState(item, store, coins, shop) },
                    onBuy = onBuy,
                    onEquip = onEquip,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Состояние карточки: что можно сделать с предметом прямо сейчас. */
private enum class CosmeticAction { BUY, EQUIP, UNEQUIP, NONE }

private data class CosmeticState(
    val owned: Boolean,
    val equipped: Boolean,
    val action: CosmeticAction,
    /** Почему кнопки нет: витрина платформы, сезон, награда за игру или нехватка монет. */
    val hint: CosmeticHint? = null,
    /** Сколько монет не хватает (только для CosmeticHint.COINS). */
    val missingCoins: Int = 0,
)

private enum class CosmeticHint { PLATFORM, PASS, REWARD, COINS }

private fun cosmeticState(item: CosmeticItem, store: ProgressStore, coins: Int, shop: Shop?): CosmeticState {
    val owned = store.owns(item.id)
    val equipped = store.equippedId(item.type) == item.id
    val economy = shop?.economy ?: com.ampergrad.game.content.EconomyRules.Default
    return when {
        equipped -> CosmeticState(owned, true, CosmeticAction.UNEQUIP, null)
        owned -> CosmeticState(true, false, CosmeticAction.EQUIP, null)
        shop != null && shop.coinPurchasable(item) && economy.canAfford(coins, item.priceCoins) ->
            CosmeticState(false, false, CosmeticAction.BUY)
        shop != null && shop.coinPurchasable(item) ->
            CosmeticState(
                owned = false,
                equipped = false,
                action = CosmeticAction.NONE,
                hint = CosmeticHint.COINS,
                missingCoins = (item.priceCoins ?: 0) - coins,
            )
        item.channel == "store" -> CosmeticState(false, false, CosmeticAction.NONE, CosmeticHint.PLATFORM)
        item.channel == "battle-pass" -> CosmeticState(false, false, CosmeticAction.NONE, CosmeticHint.PASS)
        else -> CosmeticState(false, false, CosmeticAction.NONE, CosmeticHint.REWARD)
    }
}

@Composable
private fun ShopSection(
    title: String,
    items: List<CosmeticItem>,
    note: String,
    t: UiStrings,
    state: (CosmeticItem) -> CosmeticState,
    onBuy: (CosmeticItem) -> Unit,
    onEquip: (CosmeticItem) -> Unit,
) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelSmall, color = Grad.Gold)
    Spacer(Modifier.height(6.dp))
    Text(note, style = MaterialTheme.typography.bodySmall, color = Grad.InkDim)
    Spacer(Modifier.height(8.dp))
    items.forEach { item ->
        val current = state(item)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (current.equipped) Color(0xFF10202A) else Grad.Panel,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.name(t.lang), style = MaterialTheme.typography.titleMedium, color = Grad.Ink)
                    Text(
                        text = if (current.equipped) t.shopEquipped else item.priceLabel(t),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (current.equipped) Grad.Mint else Grad.Mint,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.rarityLabel(t)} · ${item.how(t.lang)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grad.InkDim,
                )
                Spacer(Modifier.height(6.dp))
                Text(t.quoted(item.lore(t.lang)), style = MaterialTheme.typography.bodySmall, color = Grad.Ink)

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (current.action) {
                        CosmeticAction.BUY -> Button(
                            onClick = { onBuy(item) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Grad.Gold,
                                contentColor = Color(0xFF231703),
                            ),
                        ) {
                            Text("${t.shopBuy} · ${item.priceLabel(t)}")
                        }
                        CosmeticAction.EQUIP -> OutlinedButton(onClick = { onEquip(item) }) {
                            Text(t.shopEquip)
                        }
                        CosmeticAction.UNEQUIP -> OutlinedButton(onClick = { onEquip(item) }) {
                            Text(t.shopUnequip)
                        }
                        CosmeticAction.NONE -> Text(
                            text = when (current.hint) {
                                CosmeticHint.PLATFORM -> t.shopPlatform
                                CosmeticHint.PASS -> t.shopNotePass
                                CosmeticHint.REWARD -> t.shopRewardHint
                                else -> t.shopNotEnough(current.missingCoins)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Grad.InkDim,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun WalletOverlay(
    t: UiStrings,
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
                Text(t.walletTitle, style = MaterialTheme.typography.titleMedium, color = Grad.Mint)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (address == null) {
                        t.walletNote
                    } else {
                        t.walletConnectedShort(Base58.shorten(address, 6, 6))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grad.InkDim,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    t.walletNoKeys,
                    style = MaterialTheme.typography.bodySmall,
                    color = Grad.InkDim,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${if (mainnet) t.networkMainnet else t.networkDevnet} · ${t.walletServer(serverUrl)}",
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
                        Text(if (busy) t.walletConnecting else t.walletConnect)
                    }
                } else {
                    OutlinedButton(onClick = onDisconnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(t.walletDisconnect)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(t.close, color = Grad.InkDim)
                }
            }
        }
    }
}

/* ─────────────────────────────── Мелочи ─────────────────────────────── */

/** Знак гильдии: незамкнутый контур квартала с искрой. */
@Composable
private fun LogoMark() {
    // Знак игры: перекрёсток квартала — ток (бирюза), память (золото), пломба.
    Canvas(Modifier.size(52.dp)) {
        val stroke = 4f
        drawRoundRect(
            color = Grad.Mint,
            topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
            size = Size(size.width * 0.8f, size.height * 0.8f),
            cornerRadius = CornerRadius(size.width * 0.2f),
            style = Stroke(width = stroke),
        )
        val cx = size.width / 2f
        val cy = size.height / 2f
        val span = size.width * 0.27f
        drawLine(Grad.Mint, Offset(cx - span, cy), Offset(cx + span, cy), stroke * 2.6f, StrokeCap.Round)
        drawLine(Grad.Gold, Offset(cx, cy - span), Offset(cx, cy + span), stroke * 2.6f, StrokeCap.Round)
        drawCircle(color = Color(0xFF06080E), radius = size.width * 0.095f, center = Offset(cx, cy))
        drawCircle(
            color = Grad.Ink,
            radius = size.width * 0.095f,
            center = Offset(cx, cy),
            style = Stroke(width = stroke * 0.9f),
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
