#!/usr/bin/env node
/**
 * Проверка окружения: «что не так, если ничего не собирается».
 *
 * Запуск (из корня репозитория): npm run doctor
 *
 * Проверяем ровно то, на чём чаще всего спотыкаются при первом запуске:
 *   • версия Node;
 *   • установлены ли пакеты, от которых зависят рабочие части (ядро, сервер, лаборатория);
 *   • сгенерированы ли контент и тест-векторы;
 *   • видит ли Android-сборка SDK и JDK (если собираетесь её собирать).
 *
 * Скрипт ничего не меняет: только смотрит и печатает подсказки. Код возврата — 1,
 * если что-то из обязательного для запуска отсутствует.
 */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
let fails = 0;
let warns = 0;

const ok = (text) => console.log(`  ok    ${text}`);
const bad = (text, hint) => {
  fails += 1;
  console.log(`  НЕТ   ${text}`);
  if (hint) console.log(`        → ${hint}`);
};
const warn = (text, hint) => {
  warns += 1;
  console.log(`  ?     ${text}`);
  if (hint) console.log(`        → ${hint}`);
};

/** Пакет виден из папки, если он лежит в её node_modules или выше (как ищет Node). */
function resolvePackage(from, specifier) {
  const parts = specifier.startsWith('@') ? specifier.split('/').slice(0, 2) : [specifier.split('/')[0]];
  let dir = from;
  for (;;) {
    const candidate = join(dir, 'node_modules', ...parts, 'package.json');
    if (existsSync(candidate)) {
      try {
        return JSON.parse(readFileSync(candidate, 'utf8')).version ?? '?';
      } catch {
        return '?';
      }
    }
    const parent = dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

console.log('АМПЕРГРАД · проверка окружения\n');

// 1. Node
const major = Number(process.versions.node.split('.')[0]);
console.log('Node');
if (major >= 22) ok(`${process.version} (npm ${process.env.npm_config_user_agent?.split(' ')[0]?.replace('npm/', '') ?? '—'})`);
else bad(`${process.version} — нужен 22 или новее`, 'brew install node@22');
console.log();

// 2. Пакеты рабочих частей
console.log('Пакеты (npm ci в корне репозитория)');
const required = {
  'ядро правил': [join(root, 'packages/engine'), ['tsx', 'vitest']],
  'сервер': [join(root, 'server'), ['hono', '@hono/node-server', 'pg', 'tweetnacl', 'bs58', '@metaplex-foundation/umi', '@metaplex-foundation/umi-bundle-defaults', '@solana/web3.js']],
  'лаборатория (веб-прототип)': [join(root, 'packages/lab'), ['vite']],
};
for (const [label, [where, packages]] of Object.entries(required)) {
  const missing = packages.filter((p) => resolvePackage(where, p) === null);
  if (missing.length === 0) ok(`${label}: все пакеты на месте`);
  else
    bad(
      `${label}: не хватает ${missing.join(', ')}`,
      'выполните npm ci в корне репозитория (и перезапустите, если ставили пакеты в подпапке)',
    );
}
console.log();

// 3. Контент и тест-векторы
console.log('Контент и тест-векторы');
const generated = [
  ['packages/engine/content/lore.json', 'npm run content'],
  ['packages/engine/content/cosmetics.json', 'npm run content'],
  ['packages/engine/vectors/levels.json', 'npm run vectors'],
];
for (const [file, command] of generated) {
  const path = join(root, file);
  if (existsSync(path)) {
    const size = Math.round(readFileSync(path).length / 1024);
    ok(`${file} (${size} КБ)`);
  } else {
    bad(`${file} нет`, command);
  }
}
console.log();

// 4. Android (нужно только для сборки приложения)
console.log('Android (только для сборки APK)');
const android = join(root, 'app-android');
if (!existsSync(join(android, 'gradlew'))) {
  warn('нет app-android/gradlew', 'клонируйте репозиторий целиком, не отдельными файлами');
} else if (!process.env.JAVA_HOME && !process.env.JDK_HOME) {
  warn('JAVA_HOME не задан', 'export JAVA_HOME=$(/usr/libexec/java_home -v 21) — нужен JDK 17+');
} else {
  ok(`JAVA_HOME=${process.env.JAVA_HOME}`);
}
if (existsSync(join(android, 'local.properties'))) ok('app-android/local.properties есть (путь к Android SDK)');
else warn('app-android/local.properties нет', `printf 'sdk.dir=%s\\n' "$HOME/Library/Android/sdk" > app-android/local.properties`);
console.log();

// Итог
if (fails === 0) {
  console.log(warns === 0 ? 'Всё на месте. Дальше: npm test, затем npm run dev или npm run server.' : `Готово к запуску на вебе и сервере (замечаний: ${warns}; они про Android).`);
} else {
  console.log(`Не хватает ${fails} ${fails === 1 ? 'пункта' : 'пунктов'} — см. подсказки выше.`);
}
process.exit(fails === 0 ? 0 : 1);
