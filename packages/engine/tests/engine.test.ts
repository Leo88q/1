import { describe, expect, it } from 'vitest';
import {
  Rng,
  applyRotate,
  applySolution,
  componentCount,
  dailySeed,
  fromVector,
  generateLevel,
  hint,
  isSolved,
  kindOf,
  levelConfig,
  lerpLevel,
  orbitCanonical,
  orbitSize,
  popcount,
  poweredCells,
  rotateCW,
  rotateMask,
  rotationToReach,
  toVector,
} from '../src';

const LEVELS = Array.from({ length: 75 }, (_, i) => i + 1);

describe('ГПСЧ', () => {
  it('детерминирован для одного seed', () => {
    const a = new Rng(12345);
    const b = new Rng(12345);
    const seqA = Array.from({ length: 8 }, () => a.nextU32());
    const seqB = Array.from({ length: 8 }, () => b.nextU32());
    expect(seqA).toEqual(seqB);
    expect(seqA.every((v) => v >= 0 && v <= 0xffffffff)).toBe(true);
  });

  it('разные seed дают разные последовательности', () => {
    const a = new Rng(1).nextU32();
    const b = new Rng(2).nextU32();
    expect(a).not.toBe(b);
  });

  // Пин для Kotlin-порта: значения должны совпадать один-в-один.
  it('даёт зафиксированные значения (контракт с Kotlin)', () => {
    const rng = new Rng(0xa11ce);
    expect(Array.from({ length: 4 }, () => rng.nextU32())).toMatchInlineSnapshot(`
      [
        3191830399,
        2415238474,
        958955856,
        3259582577,
      ]
    `);
  });

  // Пин daily-seed: сервер и клиент обязаны сойтись.
  it('daily seed стабилен', () => {
    expect(dailySeed(20000)).toMatchInlineSnapshot(`654382449`);
    expect(dailySeed(20000)).toBe(dailySeed(20000));
  });
});

describe('фрагменты', () => {
  it('поворот на 4 шага — тождество', () => {
    for (let mask = 1; mask < 16; mask++) {
      expect(rotateMask(mask, 4)).toBe(mask);
      expect(rotateMask(rotateMask(mask, 1), 3)).toBe(mask);
    }
  });

  it('поворот по часовой стрелке переводит N в E', () => {
    expect(rotateCW(1)).toBe(2);
    expect(rotateCW(2)).toBe(4);
    expect(rotateCW(4)).toBe(8);
    expect(rotateCW(8)).toBe(1);
  });

  it('канонизация орбиты и размер орбиты', () => {
    expect(orbitCanonical(1)).toBe(1); // конец
    expect(orbitSize(1)).toBe(4);
    expect(orbitSize(1 | 4)).toBe(2); // прямой
    expect(orbitSize(1 | 2 | 4 | 8)).toBe(1); // крест
    expect(orbitSize(1 | 2 | 4)).toBe(4); // тройник
  });

  it('классифицирует формы', () => {
    expect(kindOf(1)).toBe('cap');
    expect(kindOf(1 | 4)).toBe('straight');
    expect(kindOf(1 | 2)).toBe('elbow');
    expect(kindOf(1 | 2 | 4)).toBe('tee');
    expect(kindOf(15)).toBe('cross');
    expect(() => kindOf(0)).toThrow();
  });

  it('находит поворот до целевой маски', () => {
    for (let mask = 1; mask < 16; mask++) {
      const base = orbitCanonical(mask);
      const r = rotationToReach(base, mask);
      expect(rotateMask(base, r)).toBe(mask);
    }
  });

  it('popcount корректен', () => {
    for (let mask = 1; mask < 16; mask++) {
      expect(popcount(mask)).toBe([1, 2, 4, 8].filter((b) => mask & b).length);
    }
  });
});

describe('генерация кварталов', () => {
  it('каждый уровень решаем и не решён на старте', () => {
    for (const level of LEVELS) {
      const l = generateLevel(level);
      expect(isSolved(l), `уровень ${level} решён на старте`).toBe(false);
      expect(l.parMoves, `уровень ${level}: par = 0`).toBeGreaterThan(0);
      applySolution(l);
      expect(isSolved(l), `уровень ${level} не решается сгенерированным решением`).toBe(true);
    }
  });

  it('все ячейки имеют хотя бы один порт, шина и приёмники на месте', () => {
    for (const level of LEVELS) {
      const l = generateLevel(level);
      expect(l.tiles).toHaveLength(l.size * l.size);
      for (const t of l.tiles) expect(popcount(t.base)).toBeGreaterThan(0);
      expect(l.tiles[l.bus].role).toBe('bus');
      expect(l.receivers.length).toBeGreaterThan(0);
      expect(l.receivers).not.toContain(l.bus);
    }
  });

  it('детерминирован для одного уровня', () => {
    for (const level of [1, 17, 38, 52, 75]) {
      expect(toVector(generateLevel(level))).toEqual(toVector(generateLevel(level)));
    }
  });

  it('сложность растёт по ярусам', () => {
    expect(levelConfig(1).size).toBe(4);
    expect(levelConfig(15).size).toBe(5); // рост внутри яруса
    expect(levelConfig(16).size).toBe(5);
    expect(levelConfig(30).size).toBe(6);
    expect(levelConfig(31).size).toBe(6);
    expect(levelConfig(46).size).toBe(7);
    expect(levelConfig(61).size).toBe(8);
    expect(levelConfig(75).size).toBe(8);
    expect(levelConfig(61).extraDensity).toBeGreaterThan(levelConfig(1).extraDensity);
  });

  it('кресты зафиксированы, остальные вращаются', () => {
    const l = generateLevel(60);
    const crosses = l.tiles.filter((t) => t.kind === 'cross');
    expect(crosses.length).toBeGreaterThan(0);
    for (let i = 0; i < l.tiles.length; i++) {
      const before = l.tiles[i].rot;
      const rotated = applyRotate(l, i, 1);
      expect(rotated).toBe(!l.tiles[i].fixed);
      if (l.tiles[i].fixed) expect(l.tiles[i].rot).toBe(before);
    }
  });

  it('8×8 генерируется быстро', () => {
    const started = performance.now();
    for (let i = 0; i < 50; i++) generateLevel(75 - (i % 15));
    const perLevel = (performance.now() - started) / 50;
    expect(perLevel).toBeLessThan(50);
  });
});

describe('проверка и подсказки', () => {
  it('подсказка доводит квартал до пломбы', () => {
    const l = generateLevel(40);
    let guard = 0;
    while (!isSolved(l)) {
      const h = hint(l);
      expect(h, 'подсказка не найдена').not.toBeNull();
      expect(h!.gain).toBeGreaterThanOrEqual(0);
      l.tiles[h!.index].rot = h!.rot;
      expect(++guard).toBeLessThan(400);
    }
    expect(guard).toBeGreaterThan(0);
  });

  it('подсказка на «застрявшем» квартале ведёт к решению', () => {
    // Все ярусы: подсказка не должна предлагать бессмысленный ход бесконечно.
    for (const level of [2, 35, 50, 74]) {
      const l = generateLevel(level);
      const h = hint(l);
      expect(h).not.toBeNull();
      expect(l.tiles[h!.index].fixed).toBe(false);
      expect(l.tiles[h!.index].rot).not.toBe(h!.rot);
    }
  });

  it('случайные повороты не ломают проверку', () => {
    const l = generateLevel(20);
    const rng = new Rng(7);
    for (let i = 0; i < 30; i++) applyRotate(l, rng.nextInt(l.tiles.length), 1);
    expect(() => isSolved(l)).not.toThrow();
    const powered = poweredCells(l);
    expect(powered[l.bus]).toBe(true);
    expect(powered.filter(Boolean).length).toBeGreaterThanOrEqual(1);
    expect(componentCount(l)).toBeGreaterThanOrEqual(1);
  });

  it('после решения все ячейки под напряжением', () => {
    const l = generateLevel(33);
    applySolution(l);
    expect(poweredCells(l).every(Boolean)).toBe(true);
    expect(componentCount(l)).toBe(1);
  });
});

describe('сериализация (тест-векторы для Kotlin)', () => {
  it('вектор восстанавливается без потерь', () => {
    for (const level of [3, 29, 47, 68]) {
      const l = generateLevel(level);
      const v = toVector(l);
      const restored = fromVector(v);
      expect(toVector(restored)).toEqual(v);
      expect(isSolved(restored)).toBe(false);
      applySolution(restored);
      expect(isSolved(restored)).toBe(true);
    }
  });
});

describe('lerpLevel (хелпер для лидербордов)', () => {
  it('интерполирует сложность', () => {
    expect(lerpLevel(1, 75, 0)).toBe(1);
    expect(lerpLevel(1, 75, 1)).toBe(75);
    expect(lerpLevel(1, 75, 0.5)).toBe(38);
  });
});
