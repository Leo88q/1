/**
 * ASCII-рендер квартала — быстрый способ глазами проверить генератор
 * без графики (удобно в терминале и CI-логах).
 *
 * Запуск: npx tsx scripts/ascii.ts [номер уровня] [--solved] [--daily]
 */
import { applySolution, generateLevel, isSolved, tileMask, componentCount } from '../src';

const args = process.argv.slice(2);
const levelNumber = Number(args.find((a) => /^\d+$/.test(a)) ?? 1);
const solved = args.includes('--solved');

const glyph = (mask: number): string =>
  ({
    1: '╵',
    2: '╶',
    3: '╰',
    4: '╷',
    5: '│',
    6: '╭',
    7: '├',
    8: '╴',
    9: '╯',
    10: '─',
    11: '┴',
    12: '╮',
    13: '┤',
    14: '┬',
    15: '┼',
  })[mask] ?? '?';

const level = generateLevel(levelNumber);
if (solved) applySolution(level);

console.log(
  `Уровень ${levelNumber}: квартал ${level.size}×${level.size}, seed ${level.seed}, план ${level.parMoves} ходов, ` +
    `групп ${componentCount(level)}, пломба: ${isSolved(level) ? 'да' : 'нет'}`,
);

for (let y = 0; y < level.size; y++) {
  let line = '';
  for (let x = 0; x < level.size; x++) {
    const i = y * level.size + x;
    const role = level.tiles[i].role;
    const mark = role === 'bus' ? '*' : role === 'receiver' ? '◆' : glyph(tileMask(level, i));
    line += mark + ' ';
  }
  console.log(line);
}
console.log('* шина, ◆ приёмник');
