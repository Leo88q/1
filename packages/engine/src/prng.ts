/**
 * Детерминированный генератор псевдослучайных чисел (mulberry32).
 *
 * Требование проекта: одна и та же последовательность обязана получаться
 * в TS (клиент веб-прототипа, сервер) и в Kotlin (Android-приложение).
 * Поэтому здесь используется только 32-битная целочисленная арифметика,
 * которая переносится в Kotlin один-в-один (Int/UInt + ushr).
 */
export class Rng {
  private a: number;

  constructor(seed: number) {
    // Приводим seed к uint32, как это делает Kotlin (seed.toUInt().toInt())
    this.a = seed >>> 0;
  }

  /** Следующее 32-битное беззнаковое число. */
  nextU32(): number {
    let t = (this.a = (this.a + 0x6d2b79f5) >>> 0);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t = (t ^ (t + Math.imul(t ^ (t >>> 7), t | 61))) >>> 0;
    return (t ^ (t >>> 14)) >>> 0;
  }

  /** Целое в диапазоне [0, n). */
  nextInt(n: number): number {
    if (n <= 0) throw new Error('nextInt: n must be > 0');
    return this.nextU32() % n;
  }

  /** Перемешивание на месте (Fisher–Yates). */
  shuffle<T>(items: T[]): T[] {
    for (let i = items.length - 1; i > 0; i--) {
      const j = this.nextInt(i + 1);
      const tmp = items[i];
      items[i] = items[j];
      items[j] = tmp;
    }
    return items;
  }
}

/** Стабильный хэш строки в uint32 (для seed из названия режима). */
export function hashString(text: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h >>> 0;
}
