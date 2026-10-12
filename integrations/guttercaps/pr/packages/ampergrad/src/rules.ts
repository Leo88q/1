/**
 * Правила «Амперграда» — механизм внутри GUTTERCAPS.
 *
 * Квартал — квадратная сетка фрагментов магистрали. У каждого фрагмента есть
 * базовая маска портов (N=1, E=2, S=4, W=8) и текущий поворот 0..3. Задача игрока:
 * довернуть фрагменты так, чтобы **все ячейки квартала были подключены к шине**
 * (шина — источник тока, фиксированная ячейка). Приёмники — видимые цели: это те же
 * подключённые ячейки, но подсвеченные как часы квартала.
 *
 * Здесь нет ни кошелька, ни денег, ни $$CG: модуль только считает правила. Экономика
 * GUTTERCAPS этим модулем не затрагивается (см. docs/12-ampergrad-mechanism.md).
 */
import { N, E, S, W, orbitSize, circularDistance, rotateMask } from './tiles.ts';

/** Квартал в том виде, в каком он приходит из контента (JSON канона). */
export interface LevelDef {
  /** Номер квартала; для челленджа дня — номер пилотного уровня. */
  level: number;
  /** Seed генерации: одинаков на клиенте, сервере и в витрине. */
  seed: number;
  /** Сторона сетки: ячейка (x, y) лежит по индексу y * size + x. */
  size: number;
  /** Индекс шины (источника тока). */
  bus: number;
  /** Индексы приёмников (визуальные цели квартала). */
  receivers: number[];
  /** Базовые маски фрагментов (канонические орбиты поворотов). */
  base: number[];
  /** Повороты на старте. */
  startRot: number[];
  /** Повороты, соответствующие заложенному решению. */
  solutionRot: number[];
  /** 1 — крест: все повороты эквивалентны, ячейка не вращается. */
  fixed: number[];
  /** План по ходам (минимальное число поворотов от старта до решения). */
  parMoves: number;
}

/** Рабочее состояние квартала: то, что крутит игрок. */
export interface BoardState {
  size: number;
  bus: number;
  receivers: number[];
  base: number[];
  rot: number[];
  fixed: boolean[];
}

/** Копия квартала из контента в рабочее состояние. */
export function boardFromLevel(level: LevelDef): BoardState {
  return {
    size: level.size,
    bus: level.bus,
    receivers: [...level.receivers],
    base: [...level.base],
    rot: [...level.startRot],
    fixed: level.fixed.map((f) => f === 1),
  };
}

/** Текущая маска ячейки с учётом поворота. */
export function tileMask(board: BoardState, index: number): number {
  return rotateMask(board.base[index], board.rot[index]);
}

/** Поворот ячейки. dir: +1 по часовой, -1 против. Кресты не вращаются. */
export function rotate(board: BoardState, index: number, dir = 1): boolean {
  if (index < 0 || index >= board.rot.length || board.fixed[index]) return false;
  board.rot[index] = (((board.rot[index] + dir) % 4) + 4) % 4;
  return true;
}

/** Свежая копия состояния — чтобы история ходов не делила массивы с текущей. */
export function cloneBoard(board: BoardState): BoardState {
  return { ...board, receivers: [...board.receivers], base: [...board.base], rot: [...board.rot], fixed: [...board.fixed] };
}

/**
 * Корень связности для каждой ячейки: две ячейки соединены, если у обеих есть
 * порт на общей грани (N↔S, E↔W). Рёбра смотрим один раз — только E и S.
 */
function componentRoots(board: BoardState): Int32Array {
  const size = board.size;
  const parent = new Int32Array(board.base.length);
  for (let i = 0; i < parent.length; i++) parent[i] = i;
  const find = (i: number): number => {
    let r = i;
    while (parent[r] !== r) r = parent[r];
    while (parent[i] !== r) {
      const next = parent[i];
      parent[i] = r;
      i = next;
    }
    return r;
  };

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const mask = tileMask(board, i);
      if (mask & E && x + 1 < size && tileMask(board, i + 1) & W) parent[find(i + 1)] = find(i);
      if (mask & S && y + 1 < size && tileMask(board, i + size) & N) parent[find(i + size)] = find(i);
    }
  }
  return parent.map((_, i) => find(i)) as Int32Array;
}

/** Ячейки, подключённые к шине. */
export function poweredCells(board: BoardState): boolean[] {
  const roots = componentRoots(board);
  const busRoot = roots[board.bus];
  return Array.from(roots, (r) => r === busRoot);
}

/** Число несвязанных групп в квартале: 1 — квартал собран. */
export function componentCount(board: BoardState): number {
  return new Set(componentRoots(board)).size;
}

/** Пломба квартала: все ячейки подключены к шине. */
export function isSolved(board: BoardState): boolean {
  return poweredCells(board).every(Boolean);
}

/** Сколько приёмников уже под током (для часов квартала и обучения). */
export function receiversPowered(board: BoardState): number {
  const powered = poweredCells(board);
  return board.receivers.reduce((n, i) => n + (powered[i] ? 1 : 0), 0);
}

/** Минимальное число ходов от текущего состояния до заданного решения. */
export function parMoves(board: BoardState, solutionRot: number[]): number {
  let total = 0;
  for (let i = 0; i < board.rot.length; i++) {
    if (board.fixed[i]) continue;
    const period = orbitSize(board.base[i]); // 4 — обычный фрагмент, 2 — прямая, 1 — крест
    total += circularDistance(board.rot[i] % period, solutionRot[i] % period, period);
  }
  return total;
}

/** Ячейка, которую решение советует довернуть следующей (для подсказки). */
export function nextHint(board: BoardState, solutionRot: number[]): number | null {
  for (let i = 0; i < board.rot.length; i++) {
    if (board.fixed[i] || board.rot[i] === solutionRot[i]) continue;
    return i;
  }
  return null;
}

/**
 * Проверка контента: структура квартала и то, что заложенное решение действительно
 * собирает квартал. Возвращает список замечаний — пустой список значит «контент годен».
 */
export function validateLevel(level: LevelDef): string[] {
  const problems: string[] = [];
  const cells = level.size * level.size;
  const arrays: Array<[string, unknown[]]> = [
    ['base', level.base],
    ['startRot', level.startRot],
    ['solutionRot', level.solutionRot],
    ['fixed', level.fixed],
  ];
  for (const [name, arr] of arrays) {
    if (!Array.isArray(arr) || arr.length !== cells) problems.push(`${name}: ожидалось ${cells} значений`);
  }
  if (level.bus < 0 || level.bus >= cells) problems.push(`bus ${level.bus} вне сетки`);
  for (const r of level.receivers) {
    if (r < 0 || r >= cells) problems.push(`приёмник ${r} вне сетки`);
  }
  if (problems.length > 0) return problems;

  for (let i = 0; i < cells; i++) {
    if (![1, 3, 5, 7, 15].includes(level.base[i])) {
      problems.push(`ячейка ${i}: недопустимая маска ${level.base[i]} (канон — 1, 3, 5, 7, 15)`);
    }
    for (const [name, arr] of [['startRot', level.startRot], ['solutionRot', level.solutionRot]] as const) {
      if (arr[i] < 0 || arr[i] > 3) problems.push(`ячейка ${i}: поворот ${name} вне 0..3`);
    }
  }
  if (problems.length > 0) return problems;

  const solved: BoardState = {
    size: level.size,
    bus: level.bus,
    receivers: level.receivers,
    base: level.base,
    rot: level.solutionRot,
    fixed: level.fixed.map((f) => f === 1),
  };
  if (!isSolved(solved)) problems.push('заложенное решение не собирает квартал: есть ячейки без тока');

  const start: BoardState = { ...solved, rot: level.startRot };
  if (isSolved(start)) problems.push('стартовая раскладка уже собрана — игроку нечего делать');

  return problems;
}

export const PORT_BITS = { N, E, S, W } as const;
