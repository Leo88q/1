package com.ampergrad.game.content

import java.util.Locale

/** Язык интерфейса. */
enum class Lang(val code: String) {
    RU("ru"),
    EN("en");

    companion object {
        fun of(code: String?): Lang = if (code == EN.code) EN else RU

        /** Язык по умолчанию — системный; русский, если система на русском. */
        fun systemDefault(): Lang = if (Locale.getDefault().language == "ru") RU else EN
    }
}

/**
 * Тексты интерфейса. Лор (имена кварталов, записи реестра) — канон города и
 * живёт в content/lore.json: его перевод — отдельная задача контента, а не
 * интерфейса (см. store/checklist.md).
 */
interface UiStrings {
    val lang: Lang

    /* Меню */
    val tagline: String
    fun startShift(level: Int): String
    val dailyChallenge: String
    val connectWallet: String
    fun walletShort(address: String): String
    val registryButton: String
    val shopButton: String
    val boardButton: String
    val resetButton: String
    val menuAbout: String
    val menuEngineNote: String
    fun menuProgress(solved: Int, stars: Int, streak: Int): String
    /** Название яруса по порядку (1–5). */
    fun tierName(order: Int): String
    val soundOn: String
    val soundOff: String
    val artOn: String
    val artOff: String
    /** Короткие подписи кнопки вида (в строку управления внизу). */
    val artShort: String
    val schemeShort: String
    /** Арт не доехал в этой сборке (пояснение, а не молчание). */
    val artMissing: String

    /* Игровой экран */
    val moves: String
    val time: String
    val daily: String
    fun level(n: Int): String
    fun commonQuarter(size: Int): String
    val reset: String
    val hint: String
    val menu: String

    /* Подсказки и баннеры */
    val bannerDailyStart: String
    fun bannerLevelStart(receivers: Int): String
    val bannerSealed: String
    val bannerSealedLong: String
    val bannerCrossFixed: String
    val bannerHint: String
    val bannerReset: String
    fun bannerWalletConnected(short: String): String

    /* Победа */
    val seal: String
    val districtAlive: String
    fun winStats(moves: Int, par: Int, time: String, hints: Int): String
    val claimNeedsWallet: String
    val claimBusy: String
    val claimButton: String
    val claimMinted: String
    val claimQueued: String
    fun claimStatus(status: String): String
    val claimFailed: String
    fun registryRef(short: String): String
    val nextQuarter: String
    val again: String
    val toMenu: String
    fun openEntry(id: String, title: String): String

    /* Рейтинг дня */
    val boardTitle: String
    fun boardDay(day: Long): String
    val boardNote: String
    val boardLoading: String
    val boardEmpty: String
    val boardError: String
    val boardRefresh: String
    fun boardMoves(moves: Int): String
    val boardSubmitting: String
    val boardSubmittingShort: String
    val boardConnect: String
    fun boardRank(rank: Int, total: Int): String
    fun boardAccepted(moves: Int): String
    val boardSendFailed: String

    /* Реестр */
    val registryTitle: String
    fun registryProgress(open: Int, total: Int): String
    val registryNote: String
    val registryEmpty: String
    fun registryOpenedBy(source: String, id: String): String
    /** Подпись страницы реестра: «РЕЕСТР S1-01 · Клемма» / «REGISTRY S1-01 · Klemma». */
    fun registryLabel(code: String, speaker: String): String
    val close: String

    /* Витрина */
    val shopTitle: String
    val shopNote: String
    val shopMissing: String
    val shopSectionRewards: String
    val shopSectionCoins: String
    val shopSectionStore: String
    val shopSectionPass: String
    val shopNoteRewards: String
    val shopNoteCoins: String
    val shopNoteStore: String
    val shopNotePass: String
    /* Монеты и покупки */
    fun coins(amount: Int): String
    val shopOwned: String
    val shopEquip: String
    val shopUnequip: String
    val shopEquipped: String
    val shopBuy: String
    fun shopNotEnough(need: Int): String
    val shopPurchased: String
    val shopInUse: String
    val shopPlatform: String
    val shopRewardHint: String
    fun menuCoins(amount: Int): String
    /** Оформление лор-строки карточки: «…» или “…” по языку. */
    fun quoted(text: String): String
    val menuNothingWorn: String
    fun menuWearing(what: String): String
    fun coinsEarned(amount: Int): String

    val priceReward: String
    val priceSeason: String
    fun priceCoins(coins: Int): String
    fun priceUsd(amount: String): String
    val rarityCommon: String
    val rarityRare: String
    val rarityEpic: String
    val rarityLegendary: String

    /** Метка редкости по строке из каталога. */
    fun rarity(rarity: String): String = when (rarity) {
        "common" -> rarityCommon
        "rare" -> rarityRare
        "epic" -> rarityEpic
        else -> rarityLegendary
    }

    /** Цена карточки: доллары витрины, монеты игры или способ получения. */
    fun priceLabel(priceUsd: Double?, priceCoins: Int?, channel: String): String = when {
        priceUsd != null -> priceUsd(String.format(java.util.Locale.US, "%.2f", priceUsd))
        priceCoins != null -> priceCoins(priceCoins)
        channel == "reward" -> priceReward
        else -> priceSeason
    }

    /* Кошелёк */
    val walletTitle: String
    val walletNote: String
    val walletNoKeys: String
    val walletMissing: String
    val walletConnect: String
    val walletConnecting: String
    val walletDisconnect: String
    fun walletConnectedShort(short: String): String
    val walletConnectFailed: String
    val walletDisconnectFailed: String
    fun walletServer(url: String): String
    val networkMainnet: String
    val networkDevnet: String

    companion object {
        fun of(lang: Lang): UiStrings = if (lang == Lang.EN) En else Ru
    }
}

object Ru : UiStrings {
    override val lang = Lang.RU

    override val tagline = "ВЕРНИ ГОРОДУ ТОК"
    override fun startShift(level: Int) = "НАЧАТЬ СМЕНУ · УРОВЕНЬ $level"
    override val dailyChallenge = "ЧЕЛЛЕНДЖ ДНЯ"
    override val connectWallet = "ПОДКЛЮЧИТЬ КОШЕЛЁК"
    override fun walletShort(address: String) = "КОШЕЛЁК · $address"
    override val registryButton = "РЕЕСТР ГОРОДА"
    override val shopButton = "ВИТРИНА"
    override val boardButton = "РЕЙТИНГ ДНЯ"
    override val resetButton = "НАЧАТЬ ЗАНОВО"
    override val menuAbout =
        "Гильдия Ключа: свет — это ресурс, а память — плата. Награды за кварталы — " +
            "сжатые NFT (cNFT) в реестре города; игра не требует токенов и не просит " +
            "подписывать транзакции за SOL."
    override val menuEngineNote =
        "Движок правил: собственный код проекта. Идея жанра — свободная; код PipeWalker (MIT) " +
            "не используется, ассеты — оригинальные."
    override fun menuProgress(solved: Int, stars: Int, streak: Int) =
        "Собранных кварталов: $solved · пломб: $stars · стрик: $streak дн."
    override fun tierName(order: Int) = when (order) {
        1 -> "ПЛАТО ПЕРВОГО КОНТАКТА"
        2 -> "РТУТНЫЙ ЯРУС"
        3 -> "ТЕРМИНАЛЬНЫЙ ЯРУС"
        4 -> "СТАРЫЙ ДИОД"
        else -> "ПЛАВИЛЬНЯ"
    }
    override val soundOn = "Звук включён"
    override val soundOff = "Звук выключен"
    override val artOn = "Трубы из арта"
    override val artOff = "Схема поля"
    override val artShort = "ТРУБЫ"
    override val schemeShort = "СХЕМА"
    override val artMissing = "Арт не попал в сборку — поле остаётся схемой"

    override val moves = "ХОДЫ"
    override val time = "ВРЕМЯ"
    override val daily = "ЧЕЛЛЕНДЖ ДНЯ"
    override fun level(n: Int) = "УРОВЕНЬ $n"
    override fun commonQuarter(size: Int) = "ОБЩИЙ КВАРТАЛ ${size}×$size"
    override val reset = "СБРОС"
    override val hint = "ПОДСКАЗКА"
    override val menu = "МЕНЮ"

    override val bannerDailyStart = "Челлендж дня: тот же квартал для всех мастеров Амперграда"
    override fun bannerLevelStart(receivers: Int) =
        "Подай ток на $receivers приёмник(а): все ячейки квартала должны быть подключены к шине"
    override val bannerSealed = "Пломба. Квартал дышит светом."
    override val bannerSealedLong = "Пломба. Квартал дышит светом."
    override val bannerCrossFixed = "Клемма: «Крест не вращается, мастер. Регламент 2.7»"
    override val bannerHint = "Клемма: «Позиция 4.2 — доверните фрагмент до упора»"
    override val bannerReset = "Реестр очищен. Город начинается заново."
    override fun bannerWalletConnected(short: String) = "Кошелёк подключён: $short"

    override val seal = "ПЛОМБА"
    override val districtAlive = "Квартал дышит светом"
    override fun winStats(moves: Int, par: Int, time: String, hints: Int) =
        "Ходы: $moves (план $par) · Время: $time · Подсказки: $hints"
    override val claimNeedsWallet = "ЗАБРАТЬ ПЛОМБУ (НУЖЕН КОШЕЛЁК)"
    override val claimBusy = "ЗАПИСЬ В РЕЕСТР…"
    override val claimButton = "ЗАБРАТЬ ПЛОМБУ В РЕЕСТР"
    override val claimMinted = "Пломба записана в реестр города"
    override val claimQueued = "Награда в очереди: минт выполнится в ближайшем батче"
    override fun claimStatus(status: String) = "Статус: $status"
    override val claimFailed = "Не удалось забрать награду"
    override fun registryRef(short: String) = "Реестр: $short"
    override val nextQuarter = "СЛЕДУЮЩИЙ КВАРТАЛ"
    override val again = "ЕЩЁ РАЗ"
    override val toMenu = "В МЕНЮ"
    override fun openEntry(id: String, title: String) = "Открыта как $id · $title"

    override val boardTitle = "РЕЙТИНГ ДНЯ"
    override fun boardDay(day: Long) = "день $day"
    override val boardNote =
        "Квартал дня одинаков для всех мастеров. Сервер проигрывает ходы движком, " +
            "поэтому в рейтинге только честные решения."
    override val boardLoading = "Запрашиваю реестр…"
    override val boardEmpty =
        "Сегодня ещё никто не отправил результат. Соберите квартал дня — и город запишет вас первым."
    override val boardError = "Рейтинг недоступен"
    override val boardRefresh = "ОБНОВИТЬ"
    override fun boardMoves(moves: Int) = "$moves ход."
    override val boardSubmitting = "Отправляю результат в рейтинг…"
    override val boardSubmittingShort = "Отправляю результат в рейтинг…"
    override val boardConnect = "Подключите кошелёк, чтобы попасть в рейтинг дня"
    override fun boardRank(rank: Int, total: Int) = "Рейтинг дня: место $rank из $total"
    override fun boardAccepted(moves: Int) = "Результат дня принят: $moves ходов"
    override val boardSendFailed = "Не удалось отправить результат дня"

    override val registryTitle = "РЕЕСТР ГОРОДА"
    override fun registryProgress(open: Int, total: Int) = "открыто $open из $total"
    override val registryNote =
        "Гильдия не выдаёт реестр целиком: страницы открываются за собранные кварталы. " +
            "Каждая — с подписью того, кто говорил."
    override val registryEmpty = "Пока пусто. Соберите первый квартал — откроется первая страница."
    override fun registryOpenedBy(source: String, id: String) = "Открыта: $source · $id"
    override fun registryLabel(code: String, speaker: String) = "РЕЕСТР $code · $speaker"
    override val close = "ЗАКРЫТЬ"

    override val shopTitle = "ВИТРИНА"
    override val shopNote =
        "Косметика меняет только вид: цвет тока, тему поля, источник, печать. " +
            "Правила, время и награды она не трогает — ни один предмет не даёт преимущества."
    override val shopMissing = "Каталог не попал в сборку. Выполните npm run content и пересоберите приложение."
    override val shopSectionRewards = "НАГРАДЫ ЗА ИГРУ"
    override val shopSectionCoins = "ЗА МОНЕТЫ"
    override val shopSectionStore = "ВИТРИНА"
    override val shopSectionPass = "BATTLE PASS"
    override val shopNoteRewards = "забрать пломбой в реестре"
    override val shopNoteCoins = "монеты зарабатываются в игре, не покупаются"
    override val shopNoteStore = "покупки идут в фонд сезона и призы"
    override val shopNotePass = "сезонный трек, 8 недель"
    override fun coins(amount: Int) = "$amount монет"
    override val shopOwned = "КУПЛЕНО"
    override val shopEquip = "НАДЕТЬ"
    override val shopUnequip = "СНЯТЬ"
    override val shopEquipped = "НАДЕТО"
    override val shopBuy = "КУПИТЬ"
    override fun shopNotEnough(need: Int) = "не хватает $need монет"
    override val shopPurchased = "Куплено. Предмет сразу надет."
    override val shopInUse = "Носить можно палитру, тему, источник и один знак одновременно."
    override val shopPlatform = "витрина платформы"
    override val shopRewardHint = "выдаётся за игру, покупке не подлежит"
    override fun menuCoins(amount: Int) = "монет: $amount"
    override fun quoted(text: String) = "«$text»"
    override val menuNothingWorn = "надето: канонический вид"
    override fun menuWearing(what: String) = "надето: $what"
    override fun coinsEarned(amount: Int) = "+$amount монет"

    override val priceReward = "за игру"
    override val priceSeason = "по сезону"
    override fun priceCoins(coins: Int) = "$coins монет"
    override fun priceUsd(amount: String) = "\$$amount"
    override val rarityCommon = "ОБЫЧНЫЙ"
    override val rarityRare = "РЕДКИЙ"
    override val rarityEpic = "ЭПИЧЕСКИЙ"
    override val rarityLegendary = "ЛЕГЕНДАРНЫЙ"

    override val walletTitle = "КОШЕЛЁК"
    override val walletNote =
        "Кошелёк подключается только для того, чтобы забрать награду. " +
            "Игра не требует токенов и не подписывает транзакции за SOL."
    override val walletNoKeys =
        "Работает с Seed Vault Wallet на Seeker, Phantom, Solflare, Backpack — подключение " +
            "идёт через Mobile Wallet Adapter, поэтому приложение не зависит от конкретного кошелька."
    override val walletMissing = "Кошелёк не подключён."
    override val walletConnect = "ПОДКЛЮЧИТЬ КОШЕЛЁК"
    override val walletConnecting = "ОЖИДАНИЕ КОШЕЛЬКА…"
    override val walletDisconnect = "ОТКЛЮЧИТЬ"
    override fun walletConnectedShort(short: String) = "Подключено: $short"
    override val walletConnectFailed = "Не удалось подключить кошелёк"
    override val walletDisconnectFailed = "Не удалось отключить кошелёк"
    override fun walletServer(url: String) = "Сервер клейма: $url"
    override val networkMainnet = "MAINNET"
    override val networkDevnet = "DEVNET"
}

object En : UiStrings {
    override val lang = Lang.EN

    override val tagline = "BRING THE CURRENT BACK"
    override fun startShift(level: Int) = "START THE SHIFT · LEVEL $level"
    override val dailyChallenge = "DAILY CHALLENGE"
    override val connectWallet = "CONNECT WALLET"
    override fun walletShort(address: String) = "WALLET · $address"
    override val registryButton = "CITY REGISTRY"
    override val shopButton = "SHOP"
    override val boardButton = "DAILY BOARD"
    override val resetButton = "START OVER"
    override val menuAbout =
        "The Key Guild: light is a resource and memory is the price. District rewards are " +
            "compressed NFTs (cNFTs) in the city registry; the game needs no token and never " +
            "asks you to sign a SOL transaction."
    override val menuEngineNote =
        "Rules engine: our own code. The genre idea is free; PipeWalker code (MIT) is not used, " +
            "all assets are original."
    override fun menuProgress(solved: Int, stars: Int, streak: Int) =
        "Districts sealed: $solved · seals: $stars · streak: $streak d."
    override fun tierName(order: Int) = when (order) {
        1 -> "FIRST CONTACT PLATEAU"
        2 -> "MERCURY TIER"
        3 -> "TERMINAL TIER"
        4 -> "THE OLD DIODE"
        else -> "THE MELTER"
    }
    override val soundOn = "Sound on"
    override val soundOff = "Sound off"
    override val artOn = "Pipes from art"
    override val artOff = "Field scheme"
    override val artShort = "PIPES"
    override val schemeShort = "SCHEME"
    override val artMissing = "Art missing from this build — field stays schematic"

    override val moves = "MOVES"
    override val time = "TIME"
    override val daily = "DAILY CHALLENGE"
    override fun level(n: Int) = "LEVEL $n"
    override fun commonQuarter(size: Int) = "SHARED DISTRICT ${size}×$size"
    override val reset = "RESET"
    override val hint = "HINT"
    override val menu = "MENU"

    override val bannerDailyStart = "Daily challenge: the same district for every master of Ampergrad"
    override fun bannerLevelStart(receivers: Int) =
        "Power $receivers receiver(s): every cell of the district must be connected to the bus"
    override val bannerSealed = "Sealed. The district breathes light."
    override val bannerSealedLong = "Sealed. The district breathes light."
    override val bannerCrossFixed = "Klemma: “Crossings do not turn, master. Regulation 2.7”"
    override val bannerHint = "Klemma: “Position 4.2 — turn the piece until it stops”"
    override val bannerReset = "Registry cleared. The city starts over."
    override fun bannerWalletConnected(short: String) = "Wallet connected: $short"

    override val seal = "SEALED"
    override val districtAlive = "The district breathes light"
    override fun winStats(moves: Int, par: Int, time: String, hints: Int) =
        "Moves: $moves (par $par) · Time: $time · Hints: $hints"
    override val claimNeedsWallet = "CLAIM THE SEAL (WALLET NEEDED)"
    override val claimBusy = "WRITING TO THE REGISTRY…"
    override val claimButton = "CLAIM THE SEAL INTO THE REGISTRY"
    override val claimMinted = "The seal is written into the city registry"
    override val claimQueued = "Reward queued: the mint runs in the next batch"
    override fun claimStatus(status: String) = "Status: $status"
    override val claimFailed = "Could not claim the reward"
    override fun registryRef(short: String) = "Registry: $short"
    override val nextQuarter = "NEXT DISTRICT"
    override val again = "AGAIN"
    override val toMenu = "TO MENU"
    override fun openEntry(id: String, title: String) = "Opened as $id · $title"

    override val boardTitle = "DAILY BOARD"
    override fun boardDay(day: Long) = "day $day"
    override val boardNote =
        "The daily district is the same for everyone. The server replays every move with the " +
            "rules engine, so the board only holds honest solves."
    override val boardLoading = "Requesting the registry…"
    override val boardEmpty =
        "Nobody has submitted a result today. Seal the daily district and the city writes you in first."
    override val boardError = "The board is unavailable"
    override val boardRefresh = "REFRESH"
    override fun boardMoves(moves: Int) = "$moves mv."
    override val boardSubmitting = "Submitting to the board…"
    override val boardSubmittingShort = "Submitting to the board…"
    override val boardConnect = "Connect a wallet to enter the daily board"
    override fun boardRank(rank: Int, total: Int) = "Daily rank: $rank of $total"
    override fun boardAccepted(moves: Int) = "Daily result accepted: $moves moves"
    override val boardSendFailed = "Could not submit the daily result"

    override val registryTitle = "CITY REGISTRY"
    override fun registryProgress(open: Int, total: Int) = "opened $open of $total"
    override val registryNote =
        "The Guild never hands out the registry at once: pages open as you seal districts. " +
            "Every page is signed by whoever spoke it."
    override val registryEmpty = "Empty so far. Seal your first district to open the first page."
    override fun registryOpenedBy(source: String, id: String) = "Opened by: $source · $id"
    override fun registryLabel(code: String, speaker: String) = "REGISTRY $code · $speaker"
    override val close = "CLOSE"

    override val shopTitle = "SHOP"
    override val shopNote =
        "Cosmetics change appearance only: current colour, field theme, power source, seal. " +
            "They never touch rules, time or rewards — nothing here gives an advantage."
    override val shopMissing = "The catalogue is missing from this build. Run npm run content and rebuild."
    override val shopSectionRewards = "REWARDS FOR PLAY"
    override val shopSectionCoins = "FOR COINS"
    override val shopSectionStore = "STORE"
    override val shopSectionPass = "BATTLE PASS"
    override val shopNoteRewards = "claim with a seal in the registry"
    override val shopNoteCoins = "coins are earned in game, never bought"
    override val shopNoteStore = "purchases fund the season pool and prizes"
    override val shopNotePass = "season track, 8 weeks"
    override fun coins(amount: Int) = "$amount coins"
    override val shopOwned = "OWNED"
    override val shopEquip = "WEAR"
    override val shopUnequip = "REMOVE"
    override val shopEquipped = "WORN"
    override val shopBuy = "BUY"
    override fun shopNotEnough(need: Int) = "$need coins short"
    override val shopPurchased = "Bought. Worn right away."
    override val shopInUse = "You can wear a palette, a theme, a source and one mark at the same time."
    override val shopPlatform = "platform store"
    override val shopRewardHint = "earned by playing, not for sale"
    override fun menuCoins(amount: Int) = "coins: $amount"
    override fun quoted(text: String) = "\u201C$text\u201D"
    override val menuNothingWorn = "worn: the canonical look"
    override fun menuWearing(what: String) = "worn: $what"
    override fun coinsEarned(amount: Int) = "+$amount coins"

    override val priceReward = "for play"
    override val priceSeason = "seasonal"
    override fun priceCoins(coins: Int) = "$coins coins"
    override fun priceUsd(amount: String) = "\$$amount"
    override val rarityCommon = "COMMON"
    override val rarityRare = "RARE"
    override val rarityEpic = "EPIC"
    override val rarityLegendary = "LEGENDARY"

    override val walletTitle = "WALLET"
    override val walletNote =
        "The wallet is only used to claim a reward. The game needs no token and never signs " +
            "a SOL transaction."
    override val walletNoKeys =
        "Works with Seed Vault Wallet on Seeker, Phantom, Solflare, Backpack — the connection " +
            "goes through Mobile Wallet Adapter, so the app does not depend on one wallet."
    override val walletMissing = "No wallet connected."
    override val walletConnect = "CONNECT WALLET"
    override val walletConnecting = "WAITING FOR THE WALLET…"
    override val walletDisconnect = "DISCONNECT"
    override fun walletConnectedShort(short: String) = "Connected: $short"
    override val walletConnectFailed = "Could not connect the wallet"
    override val walletDisconnectFailed = "Could not disconnect the wallet"
    override fun walletServer(url: String) = "Claim server: $url"
    override val networkMainnet = "MAINNET"
    override val networkDevnet = "DEVNET"
}
