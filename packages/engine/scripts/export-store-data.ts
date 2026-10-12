/**
 * Данные для витрины (магазин dApp Store): кадры игры и каталог.
 *
 * Скрипт сохраняет сцены из настоящего движка — скриншоты рисуются по ним,
 * а не «в фотошопе», поэтому картинки честно показывают игру.
 *
 * Запуск: npm run store:data (из корня репозитория)
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  COSMETICS,
  ENGINE_VERSION,
  QUARTERS,
  TIERS,
  generateLevel,
  poweredCells,
  quarterForLevel,
  registryForLevel,
  solutionMask,
  tierForLevel,
  tileMask,
  type Level,
} from '../src';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(here, '..', '..', '..', 'store', 'data');
mkdirSync(outDir, { recursive: true });

/** Сцена: кадр игры с подписями интерфейса. */
interface Scene {
  id: string;
  title: string;
  subtitle: string;
  stats: { moves: number; par: number; time: string };
  size: number;
  cells: Array<{ x: number; y: number; mask: number; role: 'bus' | 'receiver' | 'line'; powered: boolean }>;
  accent: string;
  registry: { label: string; title: string; text: string } | null;
  stars?: number;
}

/** Поворачивает часть ячеек к решению — кадр «в процессе игры», а не готовый. */
function solveExcept(level: Level, keepUnsolved: number[]): void {
  level.tiles.forEach((tile, index) => {
    if (keepUnsolved.includes(index)) return;
    tile.rot = level.solutionRot[index]!;
  });
}

function buildScene(
  id: string,
  levelNumber: number,
  state: 'solved' | 'playing',
  moves: number,
  time: string,
): Scene {
  const level = generateLevel(levelNumber);
  if (state === 'solved') solveExcept(level, []);
  else solveExcept(level, [level.tiles.length - 1, 2, 1].slice(0, 3));

  const powered = poweredCells(level);
  const tier = tierForLevel(levelNumber);
  const quarter = quarterForLevel(levelNumber);
  const entry = registryForLevel(levelNumber);

  return {
    id,
    title: `УРОВЕНЬ ${levelNumber} · ${quarter?.name ?? ''}`,
    subtitle: tier ? tier.name.toUpperCase() : '',
    stats: { moves, par: level.parMoves, time },
    size: level.size,
    accent: tier?.palette[0] ?? '#00E5C7',
    cells: level.tiles.map((tile, index) => ({
      x: index % level.size,
      y: Math.floor(index / level.size),
      mask: state === 'solved' ? solutionMask(level, index) : tileMask(level, index),
      role: tile.role,
      powered: powered[index] ?? false,
    })),
    registry: entry
      ? {
          label: `РЕЕСТР ${entry.id.replace('reg-', '').toUpperCase()} · ${entry.speaker}`,
          title: entry.title,
          text: entry.text,
        }
      : null,
  };
}

const scenes: Scene[] = [
  buildScene('screenshot-1', 3, 'playing', 4, '00:38'),
  buildScene('screenshot-2', 22, 'solved', 26, '03:40'),
  buildScene('screenshot-3', 63, 'solved', 58, '07:05'),
];
scenes[1]!.stars = 3;
scenes[2]!.stars = 2;

const payload = {
  engineVersion: ENGINE_VERSION,
  generatedAt: new Date().toISOString(),
  tiers: TIERS.map((t) => ({ id: t.id, name: t.name, palette: t.palette })),
  quarters: QUARTERS.slice(0, 6).map((q) => ({ level: q.level, code: q.code, name: q.name })),
  shop: {
    rewards: COSMETICS.filter((c) => c.channel === 'reward').slice(0, 3),
    store: COSMETICS.filter((c) => c.channel === 'store').slice(0, 3),
    seasonPass: COSMETICS.filter((c) => c.channel === 'battle-pass'),
  },
  scenes,
};

const outFile = resolve(outDir, 'scenes.json');
writeFileSync(outFile, JSON.stringify(payload, null, 2) + '\n', 'utf8');
console.log(`Сцены витрины записаны: ${outFile}`);
console.log(`Кадров: ${scenes.length}; предметов в каталоге: ${payload.shop.rewards.length + payload.shop.store.length}`);
