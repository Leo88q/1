/**
 * Фрагменты магистрали («тайлы») и операции над ними.
 *
 * Порты кодируются битовой маской: N=1, E=2, S=4, W=8 (по часовой стрелке от севера).
 * Форма фрагмента однозначно определяется маской с точностью до поворота.
 */

export const N = 1;
export const E = 2;
export const S = 4;
export const W = 8;

/** Смещения соседей по битам портов. */
const DELTA: ReadonlyArray<readonly [number, number]> = [
  [0, -1], // N
  [1, 0], // E
  [0, 1], // S
  [-1, 0], // W
];

export function deltaForBit(bit: number): readonly [number, number] {
  const idx = Math.log2(bit) | 0;
  return DELTA[idx];
}

export function bitForIndex(idx: number): number {
  return 1 << idx;
}

/** Поворот маски на 90° по часовой стрелке. */
export function rotateCW(mask: number): number {
  return ((mask << 1) | (mask >>> 3)) & 0xf;
}

/** Поворот маски на r шагов по часовой стрелке (r = 0..3). */
export function rotateMask(mask: number, r: number): number {
  let m = mask;
  for (let i = 0; i < ((r % 4) + 4) % 4; i++) m = rotateCW(m);
  return m;
}

export function popcount(mask: number): number {
  let m = mask & 0xf;
  let c = 0;
  while (m) {
    c += m & 1;
    m >>>= 1;
  }
  return c;
}

export type TileKind = 'cap' | 'straight' | 'elbow' | 'tee' | 'cross';

/** Форма фрагмента по маске. */
export function kindOf(mask: number): TileKind {
  const pc = popcount(mask);
  if (pc === 1) return 'cap';
  if (pc === 2) return mask === (N | S) || mask === (E | W) ? 'straight' : 'elbow';
  if (pc === 3) return 'tee';
  if (pc === 4) return 'cross';
  throw new Error(`Недопустимая маска фрагмента: ${mask}`);
}

/** Каноническое представление орбиты поворотов (минимальная маска). */
export function orbitCanonical(mask: number): number {
  let best = mask;
  for (let r = 1; r < 4; r++) {
    const m = rotateMask(mask, r);
    if (m < best) best = m;
  }
  return best;
}

/** Число различных поворотов (4 — несимметричный, 2 — прямой, 1 — крест). */
export function orbitSize(mask: number): number {
  const set = new Set<number>();
  for (let r = 0; r < 4; r++) set.add(rotateMask(mask, r));
  return set.size;
}

/** Минимальный поворот r, при котором база совпадает с целевой маской. */
export function rotationToReach(base: number, target: number): number {
  for (let r = 0; r < 4; r++) {
    if (rotateMask(base, r) === target) return r;
  }
  throw new Error(`Маски ${base} и ${target} не совместимы`);
}

/** Кратчайшее расстояние между двумя поворотами по циклу периода period. */
export function circularDistance(a: number, b: number, period = 4): number {
  const d = (((b - a) % period) + period) % period;
  return Math.min(d, period - d);
}

/** Текстовый вид маски — для тестов и логов. */
export function maskToString(mask: number): string {
  return (
    (mask & N ? 'N' : '-') + (mask & E ? 'E' : '-') + (mask & S ? 'S' : '-') + (mask & W ? 'W' : '-')
  );
}
