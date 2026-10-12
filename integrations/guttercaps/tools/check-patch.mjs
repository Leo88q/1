#!/usr/bin/env node
/**
 * Сверяет патч `patch/ampergrad-mechanism.patch` с содержимым `pr/`.
 *
 * Патч — то, что владелец кладёт в монорепо GUTTERCAPS через `git am`. Если пакет
 * поправили, а патч не пересобрали (или наоборот), в чужой репозиторий уедет
 * устаревший код — и заметят это уже там. Здесь ловим это у себя.
 *
 * Проверка не гоняет git: в патче у каждого файла есть строка `index 0000000..<sha>`,
 * где sha — хеш git-блоба содержимого. Считаем такой же хеш у файла в `pr/` и
 * сравниваем; для коротких хешей (текстовые файлы git печатает сокращённо)
 * сверяем префикс. Плюс сверяем состав файлов — патч не должен содержать лишнего.
 *
 * Запуск: node integrations/guttercaps/tools/check-patch.mjs
 */
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

const HERE = import.meta.dirname;
const ROOT = resolve(HERE, '../../..');
const PR = join(ROOT, 'integrations/guttercaps/pr');
const PATCH = join(ROOT, 'integrations/guttercaps/patch/ampergrad-mechanism.patch');

/** Хеш git-блоба: sha1("blob <длина>\0" + содержимое), как считает сам git. */
function blobSha(bytes) {
  const header = Buffer.from(`blob ${bytes.length}\0`, 'utf8');
  return createHash('sha1').update(header).update(bytes).digest('hex');
}

function filesIn(dir, base = dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const abs = join(dir, entry);
    if (statSync(abs).isDirectory()) out.push(...filesIn(abs, base));
    else out.push(resolve(abs).slice(base.length + 1));
  }
  return out;
}

const patchFiles = new Map(); // путь → ожидаемый sha (может быть сокращён)
for (const line of readFileSync(PATCH, 'utf8').split('\n')) {
  const diff = /^diff --git a\/(.+?) b\/(.+)$/.exec(line);
  if (diff) {
    patchFiles.set(diff[2], null);
    continue;
  }
  const index = /^index [0-9a-f]+\.\.([0-9a-f]+)/.exec(line);
  if (index && patchFiles.size > 0) {
    const last = [...patchFiles.keys()].pop();
    patchFiles.set(last, index[1]);
  }
}

const problems = [];
for (const [path, expected] of patchFiles) {
  if (!expected) {
    problems.push(`${path}: в патче нет строки index — файл описан не полностью`);
    continue;
  }
  let bytes;
  try {
    bytes = readFileSync(join(PR, path));
  } catch {
    problems.push(`${path}: есть в патче, но нет в pr/`);
    continue;
  }
  const actual = blobSha(bytes);
  if (!actual.startsWith(expected)) {
    problems.push(`${path}: содержимое разошлось — в патче ${expected}, в pr/ ${actual.slice(0, expected.length)}`);
  }
}

const inPr = new Set(filesIn(PR));
for (const path of inPr) {
  if (!patchFiles.has(path)) problems.push(`${path}: есть в pr/, но нет в патче`);
}

if (problems.length > 0) {
  console.error('патч и пакет разошлись — пересоберите патч (см. integrations/guttercaps/README.md):');
  for (const p of problems) console.error(`  ✗ ${p}`);
  process.exit(1);
}

const total = [...patchFiles.values()].reduce((n, sha) => n + (sha ? 1 : 0), 0);
console.log(`патч совпадает с пакетом: ${total} файлов, sha-блобы сходятся`);
