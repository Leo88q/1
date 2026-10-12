# АМПЕРГРАД / AMPERGRAD

> Слоган: «Верни городу ток». Логическая головоломка на соединение цепей для Solana Mobile (Seeker) и Android.

# Игра-пазл на Solana Mobile (Seeker) — проектная документация

Логическая головоломка на соединение цепей (жанр PipeWalker/Net) для Solana dApp Store и Android:
неоновый минимализм, сессии 1–3 минуты, бесплатный вход без кошелька, NFT-награды как косметика.

## Документы

| Файл | О чём |
|---|---|
| [01. Название, мир, история](docs/01-ip-naming-lore.md) | 4 IP-концепта с лором, критерии проверки названия, рекомендация |
| [02. Лицензия PipeWalker](docs/02-legal-license.md) | Аудит лицензий (важный нюанс: GPLv3 в старых тегах), что можно/нельзя, гигиена |
| [03. Технический план](docs/03-tech-plan.md) | Стек, архитектура, ядро пазла, cNFT/кошелёк, сборка APK и публикация в dApp Store |
| [04. Live-ops и роадмап](docs/04-liveops-roadmap.md) | Удержание, косметика, монетизация, виральность, 10-недельный план, бюджет, риски |
| [05. Библия мира Амперград](docs/05-ampergrad-bible.md) | Выбранный концепт: мир, персонажи, 3 сезона, структура 75 уровней, визуал, NFT-предметы, тон |

## Ключевые решения (кратко)

0. **Название: «АМПЕРГРАД» / AMPERGRAD** (город внутри аккумуляторной горы, свет = память). Библия — `docs/05`.
1. **Своё ядро + своё имя.** Механика поворота фрагментов авторским правом не охраняется, а код ProjectWalker ограничен лицензией: берём MIT-версию (`v1.0+`) или пишем своё — но бренд обязан быть своим.
2. **Стек:** нативный Kotlin + Jetpack Compose, кошелёк — Mobile Wallet Adapter 2.x (Phantom и Seed Vault одним флоу), ядро пазла — детерминированный Kotlin-порт TS-канона с общими тест-векторами. Unity/C++-форк отклонены (см. `docs/03`).
3. **cNFT минтит сервер**, игрок только подписывает сообщение. Ключи — в KMS, не в APK.
4. **Без обязательного токена.** Монетизация: косметика, Battle Pass, rewarded video. Призовой фонд — из выручки, не из депозитов.
5. **Ежедневный челлендж** — общий seed для всех + серверная верификация решения (а не подписи результата).

## Что уже работает

```bash
npm install
npm test              # 30 тестов ядра (генератор, повороты, пломба, подсказки, лор)
npm run vectors       # тест-векторы для Kotlin-порта
npm run content       # JSON-выгрузка лора и косметики для клиента
npm run dev           # играбельный прототип на http://localhost:5173
npx tsx packages/engine/scripts/ascii.ts 1 --solved   # ASCII-рендер квартала
```

- `packages/engine` — каноническое ядро правил: ГПСЧ (переносимый в Kotlin), тайлы,
  генератор связных кварталов (остов + дополнительные рёбра), проверка пломбы,
  число ходов-плана, подсказки, сериализация уровней.
- `packages/engine/src/content` — канон контента: 5 ярусов, 75 кварталов с
  именами и кодами, 60 записей реестра (по 2–4 строки, с подписью говорящего)
  и магазин косметики (18 предметов: награды за игру, монеты, витрина, Battle
  Pass). Лор двуязычный: у каждой записи есть русский и английский текст, у
  кварталов и ярусов — английские имена. Клиент получает это как
  `content/lore.json` (`npm run content`).
- `packages/lab` — играбельный прототип «Амперграда»: поворот тапом (долгое
  удержание — против часовой), таймер, ходы, звёзды, подсказка, челлендж дня,
  прогресс в localStorage, неоновый рендер на canvas.
- Реестр и магазин двуязычны на всех уровнях: канон (`REGISTRY` + `SPEAKERS_EN`,
  `COSMETICS_EN`), API (`/registry`, `/shop`) и клиенты (Kotlin, витрина) —
  русский и английский текст идут вместе, тесты следят за полнотой перевода.

## Попробовать на своей машине (macOS)

Для игры в браузере и для сервера нужен только **Node 22** (нет — `brew install node@22`).
Для сборки APK дополнительно нужны **JDK 21** и **Android SDK** — их проще всего
получить вместе с Android Studio, но можно и через Homebrew (см. ниже).

```bash
# 1. Код: репозиторий публичный, вся работа — в ветке arena/77b06826-1
mkdir -p ~/Desktop && cd ~/Desktop
git clone -b arena/77b06826-1 --single-branch https://github.com/Leo88q/1.git Amperograd
cd Amperograd

# 2. Зависимости и контент
node -v            # ждём v22.x
npm ci             # установка строго по package-lock.json
npm run vectors    # тест-векторы ядра (сверка Kotlin с каноном)
npm run content    # 75 кварталов, 60 записей реестра, 18 предметов магазина
npm run doctor     # проверка окружения: что на месте, а что нет
npm test           # 41 тест ядра + 19 тестов сервера
```

Дальше — два терминала. Первый: сервер клейма и контента.

```bash
cd ~/Desktop/Amperograd
npm run server     # http://0.0.0.0:8787, хранилище — память (Postgres не нужен)
curl -s localhost:8787/health
curl -s localhost:8787/shop | head -c 300
```

По умолчанию минт работает в режиме `dry-run`: заявка встаёт «в очередь», ничего
не тратится и кошелёк не нужен. Реальный devnet-минт включается переменными
окружения (`AMPERGRAD_MINT_MODE=devnet`, `AMPERGRAD_RPC_URL`, `AMPERGRAD_PAYER_SECRET`,
`AMPERGRAD_MERKLE_TREE`) — см. `server/.env.example`.

Второй терминал: играбельный прототип в браузере.

```bash
cd ~/Desktop/Amperograd
npm run dev        # http://localhost:5173 — тап по ячейке поворачивает по часовой,
                   # долгое удержание — против; кнопка «Челлендж дня» рядом
```

Сборка приложения (если нужен APK на телефоне или эмуляторе):

```bash
# один раз: JDK 21 и Android SDK
brew install --cask temurin@21 android-studio
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # Gradle берёт JVM отсюда
# в Android Studio: Settings → SDK Manager → Android 37 (compileSdk 37) и build-tools 37.0.0
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > ~/Desktop/Amperograd/app-android/local.properties

cd ~/Desktop/Amperograd/app-android
./gradlew :app:testDebugUnitTest   # 50 тестов: правила, векторы ядра, лор, витрина, звук
./gradlew :app:assembleDebug       # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug        # на подключённый телефон или запущенный эмулятор
```

Контент и тест-векторы копируются в ресурсы модуля задачей `syncContent` перед
сборкой, поэтому свежий клон собирается без дополнительных шагов.

Чтобы приложение ходило в локальный сервер, адрес задаётся при сборке. В отладочной
сборке разрешён http (файл `app-android/app/src/debug/AndroidManifest.xml`), в
релизной — только https, как требует витрина:

```bash
# эмулятор: 10.0.2.2 — это хост-машина
./gradlew :app:assembleDebug -PserverUrl=http://10.0.2.2:8787

# телефон по USB: сначала проброс порта, потом адрес localhost
adb reverse tcp:8787 tcp:8787
./gradlew :app:assembleDebug -PserverUrl=http://127.0.0.1:8787

# телефон по Wi-Fi: IP Mac в локальной сети
./gradlew :app:assembleDebug -PserverUrl=http://$(ipconfig getifaddr en0):8787
```

Игра работает и без сервера: кошелёк, клейм и рейтинг дня просто остаются
недоступными. Готовая debug-сборка лежит в релизе `android-build`
(страница Releases репозитория) — её можно поставить через `adb install -r app-debug.apk`
без всякой сборки.

### Если что-то не собирается

Не понимаете, чего не хватает, — начните с `npm run doctor`: он перечисляет пакеты,
контент, векторы и (для Android) SDK с JDK.

| Симптом | Причина и решение |
|---|---|
| `Cannot find package '@solana/web3.js'` в тестах сервера | часть версий npm не ставит пакеты, объявленные **только** как peer-зависимости (в локе такие помечены `peer: true`). В `server/package.json` зависимость объявлена явно, поэтому `git pull` + `rm -rf node_modules && npm ci` лечит. Разовая заплатка без обновления кода: `npm i -w @ampergrad/server @solana/web3.js` |
| `npm warn install-scripts … allowScripts` | предупреждение npm 11.7+ о заблокированных установочных скриптах. Платформенные бинарники esbuild приходят отдельным пакетом, сборка не ломается. Хотите тишины: `npm install-scripts approve esbuild` |
| `vitest: not found`, `tsx: not found` | пакеты не установлены: `npm ci` в корне (не в подпапке) |
| Градл: `SDK location not found` | `printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > app-android/local.properties` |
| Градл: `Unsupported class file major version` | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| `port 8787 already in use` | `PORT=8788 npm run server` и тот же порт в `-PserverUrl` |

## Android-приложение

`app-android/` — нативное приложение на Kotlin + Jetpack Compose (минималистичный
неон, 60 fps на Seeker):

```bash
cd app-android
./gradlew :app:testDebugUnitTest   # 50 тестов: правила, векторы ядра, лор, витрина, звук
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # подписывается, если заданы переменные окружения
```

- Магазин двуязычен: название, способ получения и лор каждого предмета есть и
  по-русски, и по-английски (канон `COSMETICS_EN`); интерфейс берёт строку по языку.
- Экономика монет: за собранный квартал начисляются монеты (20 + 10 за звезду,
  +20 за челлендж дня — канон `packages/engine/src/content/economy.ts`), тратятся
  только на внешний вид в витрине; покупка за монеты доступна прямо в приложении,
  предмет сразу надевается.
- Звук — **оригинальные синтезированные сэмплы**: щелчок реле на поворот,
  «вдох» при подключении узла, «выдох» при пломбе, служебный сигнал подсказки и
  бесшовный гул массива фоном (`app-android/tools/make_sounds.py`, ~370 КБ WAV).
- Интерфейс — **русский и английский** (переключатель в меню, по умолчанию язык
  системы): строки в `content/UiStrings.kt`, имена кварталов — в каноне контента.
- Кошелёк: **Mobile Wallet Adapter 2.x** (`mobile-wallet-adapter-clientlib-ktx:2.2.0`).
  Приложение запускает системный выбор кошелька, поэтому **Phantom и Seed Vault
  работают одним и тем же кодом**; игрок подписывает только сообщение (никаких
  транзакций и комиссий со стороны игрока).
- Адрес сервера задаётся при сборке: `-PserverUrl=https://...` (по умолчанию —
  заглушка `https://api.ampergrad.example`, игра работает офлайн и без него).
  Отладочная сборка дополнительно разрешает http — чтобы подключаться к
  локальному серверу (`src/debug/AndroidManifest.xml`); релиз ходит только по https.
- Подпись релиза: переменные `AMPERGRAD_KEYSTORE` (путь), `AMPERGRAD_KEYSTORE_PASSWORD`,
  `AMPERGRAD_KEY_ALIAS`, `AMPERGRAD_KEY_PASSWORD`. В CI — секрет
  `AMPERGRAD_KEYSTORE_B64` (keystore в base64) и остальные три.
- Клейм и результат дня отправляются с **доказательством решения**: приложение
  записывает последовательность поворотов, сервер проигрывает её ядром
  (`server/src/verify.ts`). Подпись кошелька подтверждает владение, ходы — честность.
- Клиент и сервер говорят на одной версии правил (`ENGINE_VERSION` в BuildConfig).
  Тест-векторы берутся из `packages/engine/vectors/levels.json`; задача Gradle
  `syncContent` сама копирует их и контент в ресурсы модуля, поэтому локально
  достаточно `./gradlew :app:testDebugUnitTest`.

Сборка APK идёт в GitHub Actions (`.github/workflows/android.yml`): сначала тесты
TS-ядра, затем Kotlin-тесты и APK; результат — артефакты `ampergrad-apk`,
`test-report`, `level-vectors`.

## Сервер

`server/` — выдача наград (cNFT) и челлендж дня: Hono + TypeScript + Postgres,
минт через `mpl-bubblegum`, проверка решения — проигрыванием ходов ядром.
Подробности, переменные окружения и запуск — в `server/README.md`.

```bash
npm run server:dev    # http://0.0.0.0:8787 (без DATABASE_URL — хранилище в памяти)
npm test              # тесты ядра + сервера (18 тестов: подписи, лимиты, daily, контент)
```

Каталог контента отдаётся и по сети: `GET /shop`, `GET /registry/:id`,
`GET /levels/:level` (название квартала и ярус) — клиент может сверять имена,
не пересобираясь. Карточки магазина приходят сразу на двух языках
(`name`/`how`/`lore` и `nameEn`/`howEn`/`loreEn`), как и записи реестра
(`title`/`source`/`speaker` и `titleEn`/`sourceEn`/`speakerEn`, текст — в
`/registry/:entryId`).

## Витрина (dApp Store)

`store/` — всё для публикации: тексты листинга (`listing.md`), политика
конфиденциальности (`privacy.md`), чек-лист публикации (`checklist.md`) и ассеты
(иконка, фичер-график, скриншоты). Скриншоты рисуются по настоящим кадрам
движка: `npm run store:data && npm run store:assets`.

APK для устройства: GitHub → Releases → **android-build** (или артефакт
`ampergrad-apk` в Actions).

## Статус

- [x] Аудит лицензии и репозитория-донора
- [x] 4 IP-концепта и рекомендация по названию
- [x] Выбор названия: «АМПЕРГРАД» (проверка на занятость первичная — чисто)
- [x] Ядро правил + тесты + тест-векторы для Kotlin
- [x] Играбельный прототип (веб-лаборатория)
- [x] Android-приложение (Kotlin + Compose, MWA) и сборка APK в CI
- [x] Сервер: challenge/verify/daily/claim + сухой прогон минта (devnet — по флагу)
- [ ] Финальная юридическая проверка ТЗ/домены/ники
- [x] Контент: 75 уровней с именами кварталов, каталог косметики
- [x] Лор на двух языках: реестр из 60 записей, кварталы, ярусы и карточки магазина переведены (проверяется тестами)
- [x] Витрина косметики в приложении: покупка за монеты, надевание, применение на поле
- [ ] Closed beta на Seeker (плейтест на устройстве, оба кошелька)
- [ ] Devnet-прогон минта с реальным деревом Bubblegum
- [x] Материалы витрины: иконка, фичер-график, скриншоты, тексты, чек-лист
- [ ] Сабмит в dApp Store
