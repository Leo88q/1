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
import { ENGINE_VERSION, dailySeed, generateLevel, toVector, type LevelVector } from '../src';

const here = dirname(fileURLToPath(import.meta.url));
const outFile = resolve(here, '..', 'vectors', 'levels.json');

const levelNumbers: number[] = [];
for (let l = 1; l <= 75; l++) levelNumbers.push(l);

const levels: LevelVector[] = levelNumbers.map((l) => toVector(generateLevel(l)));

// Контрольные дни для daily-челленджа (пин, который проверит Kotlin-тест).
const days: Array<{ day: number; seed: number; level: LevelVector }> = [20000, 20001, 20365].map((day) => {
  const seed = dailySeed(day);
  const level = generateLevel(33, seed); // 6×6, как в челлендже дня
  level.level = 0;
  return { day, seed, level: toVector(level) };
});

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(
  outFile,
  JSON.stringify({ engineVersion: ENGINE_VERSION, levels, daily: days }, null, 2) + '\n',
  'utf8',
);

console.log(`Тест-векторы записаны: ${outFile}`);
console.log(`Уровней: ${levels.length}, daily-контрольных точек: ${days.length}`);
