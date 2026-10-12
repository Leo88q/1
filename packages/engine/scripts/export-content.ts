/**
 * Выгрузка контента (лора и косметики) в JSON для клиентов, которые не читают TS:
 * Android-приложение получает эти файлы в assets, сервер отдаёт их API.
 *
 * Запуск: npm run content (из корня репозитория)
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { COSMETICS, COSMETICS_EN, ECONOMY, ENGINE_VERSION, QUARTERS, REGISTRY, TIERS } from '../src';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(here, '..', 'content');
mkdirSync(outDir, { recursive: true });

function write(name: string, value: unknown): void {
  const file = resolve(outDir, name);
  writeFileSync(file, JSON.stringify(value, null, 2) + '\n', 'utf8');
  console.log(`записано: ${file}`);
}

write('lore.json', {
  engineVersion: ENGINE_VERSION,
  tiers: TIERS,
  quarters: QUARTERS,
  registry: REGISTRY,
});

// Английские строки подмешиваем в карточку: приложение берёт nameEn/howEn/loreEn,
// когда игрок выбрал английский интерфейс.
write('cosmetics.json', {
  engineVersion: ENGINE_VERSION,
  economy: ECONOMY,
  cosmetics: COSMETICS.map((item) => ({
    ...item,
    nameEn: COSMETICS_EN[item.id]?.name ?? null,
    howEn: COSMETICS_EN[item.id]?.how ?? null,
    loreEn: COSMETICS_EN[item.id]?.lore ?? null,
  })),
});

console.log(`Тир: ${TIERS.length} ярусов, кварталов ${QUARTERS.length}, записей реестра ${REGISTRY.length}.`);
console.log(`Магазин: ${COSMETICS.length} предметов.`);
