#!/usr/bin/env node
/**
 * Проверка контракта арта: `public/art/web.json` против исходников в `art/`.
 *
 * Ловит то, что иначе вылезет только у игрока: ассет не собрали после правки арта,
 * у спрайта разошлась маска, у яруса нет фона или не нашлось детали под маску
 * уровня (тупик, отвод, прямая, тройник, крест). Без pillow и без браузера —
 * только чтение файлов, поэтому гоняется в CI на каждый push.
 *
 * Запуск: node packages/lab/scripts/check-web-art.mjs
 */
import { readFileSync, existsSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const HERE = import.meta.dirname;
const LAB = resolve(HERE, '..');
const REPO = resolve(LAB, '../..');
const PUBLIC_ART = join(LAB, 'public', 'art');
const WEB_JSON = join(PUBLIC_ART, 'web.json');
const MANIFEST = join(REPO, 'art', 'tiles', 'manifest.json');
// Канон лора: оттуда же, откуда имена кварталов в игре. Идентификаторы ярусов в
// арте обязаны совпадать — иначе HUD назовёт пятый ярус «ЖИЛЫ МАССИВА», а реестр
// и Android-клиент — «ПЛАВИЛЬНЯ». Расхождение уже случалось, поэтому проверяем.
const LORE = join(REPO, 'packages', 'engine', 'content', 'lore.json');

const problems = [];
const note = (msg) => problems.push(msg);

function readJson(path) {
  if (!existsSync(path)) {
    note(`нет файла: ${path.replace(REPO + '/', '')}`);
    return null;
  }
  return JSON.parse(readFileSync(path, 'utf8'));
}

const web = readJson(WEB_JSON);
const manifest = readJson(MANIFEST);
if (!web || !manifest) report();

/** Файл ассета: существует и не пустой. */
function assetOk(rel) {
  const abs = join(PUBLIC_ART, rel);
  if (!existsSync(abs)) {
    note(`ассет не собран: art/${rel} (запусти packages/lab/scripts/build-art.py)`);
    return null;
  }
  const size = statSync(abs).size;
  if (size < 1024) note(`подозрительно маленький ассет: art/${rel} (${size} Б)`);
  return size;
}

// 1. Спрайты: файл на месте, маска совпадает с исходным манифестом арта.
let sprites = 0;
for (const [setName, pieces] of Object.entries(web.sets)) {
  for (const [piece, ref] of Object.entries(pieces)) {
    sprites += 1;
    const size = assetOk(ref.file);
    if (size === null) continue;
    if (piece === 'stub') {
      // Заглушка — производная: её собирают из прямой трубы, поэтому маска у неё
      // своя (1 — единственный порт), а исходник обязан существовать.
      if (ref.mask !== 1) note(`заглушка ${setName}-stub: маска должна быть 1, а в web.json ${ref.mask}`);
      if (!manifest[`${setName}-straight`]) note(`заглушка ${setName}-stub собрана не из прямой трубы`);
      continue;
    }
    const canonical = manifest[`${setName}-${piece}`];
    if (!canonical) {
      note(`в art/tiles/manifest.json нет ${setName}-${piece} — из чего собран спрайт?`);
      continue;
    }
    if (canonical.mask !== ref.mask) {
      note(`маска разошлась: ${setName}-${piece} — в web.json ${ref.mask}, в арте ${canonical.mask}`);
    }
  }
}

// 2. Что вообще есть в арте, но не попало в веб: набор существует, но потерялся.
for (const [name, meta] of Object.entries(manifest)) {
  if (!meta.mask) continue;
  if (!web.sets[meta.set]) note(`набор ${meta.set} есть в арте, но не собран в web.json`);
}

// 3. Круглые детали.
for (const key of ['mouth', 'cap']) {
  const ref = web.round[key];
  if (!ref) note(`в web.json нет круглой детали «${key}»`);
  else assetOk(ref.file);
}

// 4. Ярусы: пять штук, у каждого фон и полный набор деталей под все маски уровней.
if (web.tiers.length !== 5) note(`ярусов должно быть 5, а в web.json ${web.tiers.length}`);
const MASKS = { 1: 'stub', 3: 'elbow', 5: 'straight', 7: 'tee', 15: 'cross' };
for (const tier of web.tiers) {
  if (!tier.bg?.length) note(`у яруса ${tier.id} нет фона`);
  for (const bg of tier.bg ?? []) assetOk(bg);
  if (!tier.flow?.startsWith('#')) note(`у яруса ${tier.id} не задан цвет среды`);
  for (const setName of tier.sets) {
    if (!web.sets[setName]) {
      note(`ярус ${tier.id} ссылается на несуществующий набор ${setName}`);
      continue;
    }
    for (const [mask, piece] of Object.entries(MASKS)) {
      if (!web.sets[setName][piece]) {
        note(`набор ${setName}: нет детали ${piece} под маску ${mask} — уровень с такой ячейкой не нарисуется`);
      }
    }
  }
}

// 5. Ярусы арта == ярусы канона лора: тот же состав и тот же порядок.
if (existsSync(LORE)) {
  const lore = readJson(LORE);
  const canon = (lore?.tiers ?? []).map((t) => t.id);
  const art = web.tiers.map((t) => t.id);
  if (canon.length !== art.length || canon.join(',') !== art.join(',')) {
    note(`ярусы арта (${art.join(', ')}) не совпадают с каноном лора (${canon.join(', ')}) — пересоберите: npm run art:web`);
  } else {
    for (const tier of web.tiers) {
      const canonTier = (lore.tiers ?? []).find((t) => t.id === tier.id);
      if (!canonTier) continue;
      const canonName = String(canonTier.name ?? '').toUpperCase();
      if (canonName && canonName !== tier.name) {
        note(`ярус ${tier.id}: в арте «${tier.name}», в каноне «${canonName}» — пересоберите: npm run art:web`);
      }
    }
  }
} else {
  note(`нет канона лора (${LORE.replace(REPO + '/', '')}) — выполните npm run content`);
}

// 6. Экраны и пломбы.
for (const [key, rel] of Object.entries(web.screens)) assetOk(rel);
for (const stars of ['1', '2', '3']) {
  const rel = web.rewards[stars];
  if (!rel) note(`нет пломбы за ${stars} звезды`);
  else assetOk(rel);
}

report();

function report() {
  if (problems.length > 0) {
    console.error('арт игры разошёлся с исходниками:');
    for (const p of problems) console.error(`  ✗ ${p}`);
    process.exit(1);
  }
  const size = statSync(WEB_JSON).size;
  console.log(
    `веб-арт ок: ${Object.keys(web.sets).length} наборов, ${sprites} спрайтов, ` +
      `${web.tiers.length} ярусов, ${Object.keys(web.screens).length} экрана, ` +
      `${Object.keys(web.rewards).length} пломбы (web.json ${size} Б)`,
  );
  process.exit(0);
}
