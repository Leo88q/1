# Чек-лист публикации в dApp Store

Отметка ✅ — сделано и проверено в репозитории/CI. Отметка 👤 — требует владельца
проекта (плейтест, KYC, кошелёк, деньги, юрист).

## Готово в репозитории

- ✅ **APK (не AAB).** CI собирает `app-debug.apk` и подписанный release-APK.
  Скачивание: GitHub → Releases → тег `android-build` (обычная ссылка, доступна
  из любой сети) либо артефакт `ampergrad-apk` в Actions.
- ✅ **Отдельный ключ подписи.** В репозитории ключа нет; CI берёт
  `AMPERGRAD_KEYSTORE_B64` из секретов. Debug- и release-подпись разные, что
  обязательно для dApp Store (ключ Google Play не подходит).
- ✅ **Package id:** `com.ampergrad.game` (debug-вариант — `…game.debug`, чтобы
  обе сборки жили рядом).
- ✅ **Иконка и графика:** `store/assets/icon-512.png`,
  `feature-graphic-1024x500.png`, четыре скриншота 1080×1920 (четвёртый — вид поля с косметикой за монеты).
- ✅ **Тексты листинга:** `store/listing.md` (описание, категория, честный
  раздел про Solana и награды).
- ✅ **Политика конфиденциальности (черновик):** `store/privacy.md`.
- ✅ **Безопасность данных:** `android:allowBackup="false"`,
  `dataExtractionRules` — authToken кошелька не попадает в бэкапы.
- ✅ **Никаких токенов/депозитов:** в игре нет продажи токена, стейкинга и
  обещаний доходности; витрина продаёт только косметику.
- ✅ **Проверенные сборки:** `assembleDebug`, `assembleRelease`, 26 unit-тестов
  (правила + сверка с TS-каноном + контент) — зелёные в CI.

## Требуется от владельца проекта 👤

1. **Плейтест на устройстве (Seeker или Android 26+).**
   - Проверить подключение кошелька: **Seed Vault Wallet** (на Seeker) и
     **Phantom** — оба через один MWA-флоу.
   - Проверить клейм награды на devnet-сервере (см. `server/README.md`).
   - Проверить поворот долгим удержанием, звук, экран «Реестр города», «Витрину».
2. **KYC/KYB на publish.solanamobile.com** (для физлица — документ и селфи;
   для юрлица — документы компании).
3. **Publisher wallet** (Phantom/Solflare/Backpack) и пополнение **~0.2 SOL** на
   комиссии и ArDrive.
4. **ArDrive:** загрузить APK и ассеты, получить storage cost по их калькулятору.
5. **Сабмит:** категория Puzzle, иконка, фичер-график, скриншоты, описание,
   privacy policy (ссылка на опубликованный документ), контакт.
   Подписать все on-chain запросы портала (App NFT + Release NFT) — пропущенная
   подпись = потерянный ассет.
6. **Devnet-прогон минта:** создать дерево Bubblegum
   (`server/scripts/create-tree.ts`), прописать `AMPERGRAD_MERKLE_TREE` и
   `AMPERGRAD_MINT_MODE=devnet`, проверить реальный минт награды.
7. **Юрпроверка:** ТЗ, домены, ники, тексты листинга, политика
   конфиденциальности, правила сезона и призового фонда (если сезон с призами
   объявлен).
8. **Ответ и ревью:** dApp Store проверяет 3–5 рабочих дней; обновления дальше —
   через `@solana-mobile/dapp-store-cli` (минтится только новый Release NFT).

## Обновление версии

1. Поднять `versionCode`/`versionName` в `app-android/app/build.gradle.kts`.
2. Если менялись правила движка — поднять `ENGINE_VERSION` (`packages/engine/src/index.ts`,
   тот же номер в `BuildConfig.ENGINE_VERSION`), выполнить `npm run vectors` и
   `npm run content`, закоммитить.
3. Push в рабочую ветку → CI соберёт новый APK → взять файл из Release.
4. В портале: «New Version» → загрузить APK → подписать запросы.

## Внутренние проверки перед сабмитом

```bash
npm test                     # ядро (30) + сервер (17)
npm run typecheck            # типы всех пакетов
npm run vectors && npm run content && npm run store:data
python3 store/render_assets.py   # пересобрать ассеты (нужен pillow)
npx tsx server/scripts/simulate-claim.ts  # сквозной флоу клейма без сети
```
