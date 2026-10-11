/**
 * Генерация тест-векторов: файл-контракт между TS-ядром (сервер, веб) и
 * Kotlin-портом (Android). Обе реализации обязаны выдавать одинаковые
 * уровни по одинаковым seed — иначе daily-челлендж невозможно проверить.
 *
 * Запуск: npm run vectors (из корня репозитория)
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DAILY_CONFIG_LEVEL, ENGINE_VERSION, Rng, dailySeed, generateLevel, levelSeed, toVector, type LevelVector } from '../src';

const here = dirname(fileURLToPath(import.meta.url));
const outFile = resolve(here, '..', 'vectors', 'levels.json');

const levelNumbers: number[] = [];
for (let l = 1; l <= 75; l++) levelNumbers.push(l);

const levels: LevelVector[] = levelNumbers.map((l) => toVector(generateLevel(l)));

// Пины ГПСЧ: если Kotlin-порт разойдётся с TS, тест укажет причину сразу.
const rngPins = [0xa11ce, 1, 0xdeadbeef].map((seed) => {
  const rng = new Rng(seed);
  return { seed, values: Array.from({ length: 4 }, () => rng.nextU32()) };
});

// Пины хэша строк: seed'ы уровней и дней обязаны совпадать до генерации.
const hashPins = [
  { text: 'ampergrad:level:1', expected: levelSeed(1) },
  { text: 'ampergrad:level:75', expected: levelSeed(75) },
  { text: 'ampergrad:daily:20000', expected: dailySeed(20000) },
];

// Контрольные дни для daily-челленджа (пин, который проверит Kotlin-тест).
const days: Array<{ day: number; seed: number; level: LevelVector }> = [20000, 20001, 20365].map((day) => {
  const seed = dailySeed(day);
  // В векторе храним конфигурацию дня (33), а не runtime-маркер 0:
  // Kotlin-тест передаёт это поле прямо в generateLevel.
  const level = generateLevel(DAILY_CONFIG_LEVEL, seed); // 6×6, как в челлендже дня
  return { day, seed, level: toVector(level) };
});

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(
  outFile,
  JSON.stringify({ engineVersion: ENGINE_VERSION, rngPins, hashPins, levels, daily: days }, null, 2) + '\n',
  'utf8',
);

console.log(`Тест-векторы записаны: ${outFile}`);
console.log(`Уровней: ${levels.length}, daily-контрольных точек: ${days.length}, пинов ГПСЧ: ${rngPins.length}`);
