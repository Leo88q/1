/**
 * Слой арта игры: спрайты труб, фоны ярусов, пломбы наград.
 *
 * Контракт — `public/art/web.json`, его собирает из исходного арта
 * `packages/lab/scripts/build-art.py` (см. `art/README.md`). Здесь только загрузка
 * и кэш картинок: рендер живёт в `main.ts`, чтобы правила и вид не смешивались.
 *
 * Ключевое для стыковки: у каждого спрайта в манифесте указана **маска движка**
 * (N=1, E=2, S=4, W=8), в положении которой он нарисован. Спрайт ставится в ячейку
 * и поворачивается на тот же угол, что и маска фрагмента, — поэтому детали соседних
 * ячеек визуально стыкуются без подгонки.
 */

export interface SpriteRef {
  /** Путь внутри `public/art` (например, `tiles/zinc-elbow.webp`). */
  file: string;
  /** Маска движка, в положении которой нарисован спрайт. */
  mask: number;
}

export interface TierArt {
  id: string;
  /** Имя для HUD (канон лора: «Плавильня (Ядро)» и так далее). */
  name: string;
  /** Наборы материалов поля: один или два (второй — разнообразие внутри яруса). */
  sets: string[];
  /** Фоны яруса: [a, b]. */
  bg: string[];
  /** Цвет среды (того, что течёт по трубе), он же акцент HUD. */
  flow: string;
  accent: string;
}

export interface ArtPack {
  tile: number;
  sets: Record<string, Record<string, SpriteRef>>;
  round: Record<string, { file: string }>;
  tiers: TierArt[];
  screens: Record<string, string>;
  /** Пломбы за число звёзд: "1" | "2" | "3" → файл. */
  rewards: Record<string, string>;
  /** Особые пломбы: не за звёзды, а за то, как запечатан квартал (см. chooseSeal). */
  seals: Record<string, string>;
}

export class Art {
  pack: ArtPack | null = null;
  /** Почему арт не загрузился — показываем в консоли, а не молчим. */
  error: string | null = null;
  private base = '/art/';
  private images = new Map<string, HTMLImageElement>();

  /** Загружает манифест и прогревает спрайты: поле рисуется без мигания. */
  async load(base = '/art/'): Promise<boolean> {
    this.base = base;
    try {
      const res = await fetch(`${base}web.json`);
      if (!res.ok) throw new Error(`web.json: HTTP ${res.status}`);
      this.pack = (await res.json()) as ArtPack;
      const files = new Set<string>();
      for (const set of Object.values(this.pack.sets)) {
        for (const sprite of Object.values(set)) files.add(sprite.file);
      }
      for (const round of Object.values(this.pack.round)) files.add(round.file);
      for (const file of Object.values(this.pack.rewards)) files.add(file);
      for (const file of Object.values(this.pack.seals ?? {})) files.add(file);
      // Ошибки отдельных картинок не роняют загрузку: наборов много, и отсутствие
      // одного металла ещё не значит «не играть». Но если не встал канонный набор,
      // поле оказалось бы пустым при включённом арт-режиме — это провал целиком.
      await Promise.all([...files].map((file) => this.image(file).catch(() => null)));
      if (!this.usable()) {
        this.error = 'спрайты из web.json не загрузились — рисуем схему';
        this.pack = null;
        return false;
      }
      return true;
    } catch (err) {
      this.error = err instanceof Error ? err.message : String(err);
      return false;
    }
  }

  /** Картинка по пути из манифеста; грузится один раз и кэшируется. */
  image(file: string): Promise<HTMLImageElement> {
    const cached = this.images.get(file);
    if (cached) {
      return cached.complete ? Promise.resolve(cached) : new Promise((resolve, reject) => {
        cached.addEventListener('load', () => resolve(cached), { once: true });
        cached.addEventListener('error', () => reject(new Error(file)), { once: true });
      });
    }
    const img = new Image();
    img.decoding = 'async';
    this.images.set(file, img);
    const done = new Promise<HTMLImageElement>((resolve, reject) => {
      img.addEventListener('load', () => resolve(img), { once: true });
      img.addEventListener('error', () => reject(new Error(`не загрузился: ${file}`)), { once: true });
    });
    img.src = this.base + file;
    return done;
  }

  /**
   * Артом можно рисовать: загружен канонный набор и обе круглые детали.
   *
   * Проверка не «web.json прочитан», а «есть чем рисовать». Контракт может лежать
   * на месте, а картинки — не встать (не тот формат, битый ассет, сеть): тогда
   * поле выглядело бы пустым, но играбельным. Такой арт считаем нерабочим.
   */
  usable(): boolean {
    const pack = this.pack;
    if (!pack) return false;
    const base = pack.sets['base'];
    if (!base) return false;
    const ok = (file: string): boolean => {
      const img = this.images.get(file);
      return !!img && img.complete && img.naturalWidth > 0;
    };
    if (!Object.values(base).every((ref) => ok(ref.file))) return false;
    return Object.values(pack.round).every((ref) => ok(ref.file));
  }

  /** Уже загруженная картинка или null — рендер не ждёт сеть. */
  get(file: string | undefined): HTMLImageElement | null {
    if (!file) return null;
    const img = this.images.get(file);
    return img && img.complete && img.naturalWidth > 0 ? img : null;
  }

  /** Спрайт детали: набор, вид детали (straight/elbow/tee/cross/stub) → ссылка. */
  sprite(set: string, piece: string): SpriteRef | null {
    const pack = this.pack;
    if (!pack) return null;
    const pieces = pack.sets[set];
    if (!pieces) return null;
    return pieces[piece] ?? null;
  }

  tier(index: number): TierArt | null {
    return this.pack?.tiers[index] ?? null;
  }
}
