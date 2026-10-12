/**
 * АМПЕРГРАД — играбельный прототип (веб-лаборатория).
 *
 * Задача этого приложения: проверять правила, ритм и визуальный язык до того,
 * как те же правила уедут в Kotlin-приложение для Seeker. Ядро берётся
 * из @ampergrad/engine — один и тот же код, что потом пойдёт на сервер.
 */
import './style.css';

import {
  applyRotate,
  dailySeed,
  generateLevel,
  hint as findHint,
  isSolved,
  kindOf,
  levelConfig,
  poweredCells,
  tileMask,
  E,
  N,
  S,
  W,
  type Level,
} from '@ampergrad/engine';
import { Art, type TierArt } from './art';

/* ────────────────────────────── Ярусы и палитра ───────────────────────────── */

/**
 * Ярусы. Имена — по канону лора (`packages/engine/content/lore.json`), цвета — из
 * палитры арта (Guttercaps): acid, cyan, orange. Если арт не загрузился, эти
 * значения работают сами: игра остаётся играбельной в схематичном режиме.
 */
const TIERS: TierArt[] = [
  { id: 'plateau', name: 'ПЛАТО ПЕРВОГО КОНТАКТА', sets: ['zinc', 'zinc2'], bg: [], flow: '#B6FF3C', accent: '#B6FF3C' },
  { id: 'mercury', name: 'РТУТНЫЙ ЯРУС', sets: ['chrome'], bg: [], flow: '#16E5D9', accent: '#16E5D9' },
  { id: 'terminal', name: 'ТЕРМИНАЛЬНЫЙ ЯРУС', sets: ['rust', 'rust2'], bg: [], flow: '#FF7A1A', accent: '#FF7A1A' },
  { id: 'old-diode', name: 'СТАРЫЙ ДИОД', sets: ['cast'], bg: [], flow: '#FFB040', accent: '#FFB040' },
  { id: 'melter', name: 'ПЛАВИЛЬНЯ (ЯДРО)', sets: ['glass'], bg: [], flow: '#B6FF3C', accent: '#B6FF3C' },
];

/** Ярус уровня: арт-данные, если арт загружен, иначе встроенный набор. */
function tierOf(level: number): TierArt {
  const idx = Math.max(0, Math.min(TIERS.length - 1, Math.floor((level - 1) / 15)));
  return art.tier(idx) ?? TIERS[idx];
}

/* ─────────────────────────────────── Звук ─────────────────────────────────── */

class Sfx {
  private ctx: AudioContext | null = null;
  enabled = true;

  private ensure(): AudioContext | null {
    if (!this.enabled) return null;
    if (!this.ctx) {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return null;
      this.ctx = new Ctor();
    }
    if (this.ctx.state === 'suspended') void this.ctx.resume();
    return this.ctx;
  }

  private blip(freq: number, dur: number, type: OscillatorType, gain = 0.05, slide = 0) {
    const ctx = this.ensure();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const amp = ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, ctx.currentTime);
    if (slide) osc.frequency.linearRampToValueAtTime(freq + slide, ctx.currentTime + dur);
    amp.gain.setValueAtTime(gain, ctx.currentTime);
    amp.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + dur);
    osc.connect(amp).connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + dur + 0.02);
  }

  rotate() {
    this.blip(180 + Math.random() * 30, 0.045, 'square', 0.035, -40);
  }

  connect() {
    this.blip(660, 0.09, 'sine', 0.04, 60);
  }

  solved() {
    [523, 659, 784, 1046].forEach((f, i) => setTimeout(() => this.blip(f, 0.22, 'triangle', 0.05), i * 110));
  }

  hint() {
    this.blip(880, 0.12, 'sine', 0.035, -120);
  }
}

/* ──────────────────────────────── Прогресс ──────────────────────────────── */

interface Progress {
  level: number;
  stars: Record<number, number>;
  bestMoves: Record<number, number>;
}

const STORAGE_KEY = 'ampergrad.progress.v1';

function loadProgress(): Progress {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Progress;
      return { level: parsed.level ?? 1, stars: parsed.stars ?? {}, bestMoves: parsed.bestMoves ?? {} };
    }
  } catch {
    /* повреждённое хранилище — начинаем заново */
  }
  return { level: 1, stars: {}, bestMoves: {} };
}

function saveProgress(p: Progress) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(p));
  } catch {
    /* приватный режим — играем без сохранения */
  }
}

/* ──────────────────────────────── Арт ──────────────────────────────── */

/**
 * Арт включается сам, когда рядом лежат ассеты (`public/art`, их собирает
 * `packages/lab/scripts/build-art.py`). Режим «схема» остаётся: он полезен, чтобы
 * видеть чистые маски, и он же спасает, если картинки не отдались.
 */
const art = new Art();
const RENDER_KEY = 'ampergrad.render.v1';

type RenderMode = 'art' | 'scheme';
let renderMode: RenderMode = 'art';
try {
  if (localStorage.getItem(RENDER_KEY) === 'scheme') renderMode = 'scheme';
} catch {
  /* приватный режим — играем с артом */
}

function artReady(): boolean {
  return renderMode === 'art' && art.usable();
}

/** Какой деталью нарисована ячейка: тупик (маска 1) — половина прямой трубы. */
function pieceForMask(mask: number): string {
  const kind = kindOf(mask);
  return kind === 'cap' ? 'stub' : kind;
}

/** Набор материалов ячейки: в ярусе с двумя наборами они чередуются по полю. */
function setForCell(sets: string[], x: number, y: number, index: number): string {
  if (sets.length === 0) return 'base';
  return sets[(x + y + index) % sets.length];
}

/**
 * Какая пломба достанется за квартал.
 *
 * Кроме трёх пломб по числу звёзд есть три особых — за то, как именно квартал
 * запечатан: подсказка означает «заделано смолой», ходов втрое больше плана —
 * пломба с трещиной, три звезды в Плавильне — плавленая. Правила те же, что в
 * Android-клиенте (chooseSeal в ui/Art.kt), иначе игрок видел бы разные медальоны.
 *
 * Порядок важен: подсказка перекрывает всё остальное.
 */
function chooseSeal(stars: number, hints: number, moves: number, par: number, tier: string): string {
  if (hints > 0) return 'resin';
  if (moves > par * 3) return 'crack';
  if (stars === 3 && tier === 'melter') return 'magma';
  return String(Math.max(1, Math.min(3, stars)));
}

/** Файл пломбы: сначала особые, потом по числу звёзд. */
/** Подпись над медальоном: особая пломба объясняет себя, иначе это просто другая картинка. */
const SEAL_TITLES: Record<string, string> = {
  resin: 'ПЛОМБА · ЗАДЕЛАНО СМОЛОЙ',
  crack: 'ПЛОМБА · С ТРЕЩИНОЙ',
  magma: 'ПЛОМБА · ПЛАВЛЕНАЯ',
};

function sealTitle(key: string): string {
  return SEAL_TITLES[key] ?? 'ПЛОМБА';
}

function sealFile(key: string): string | undefined {
  const pack = art.pack;
  if (!pack) return undefined;
  return pack.seals?.[key] ?? pack.rewards[key];
}

/**
 * Картинка фона по размеру холста (cover), но без размытия: кадр яруса нарисован
 * портретом 720×1290, и на широком окне честный cover растягивал бы его вдвое —
 * поле превращалось в размытый фрагмент граффити. Поэтому увеличивать можно не
 * больше MAX_BG_ZOOM, дальше фон просто обрамляем цветом (letterbox).
 */
const MAX_BG_ZOOM = 1.25;

function drawCover(img: HTMLImageElement, rect: DOMRect) {
  ctx.fillStyle = '#04060b';
  ctx.fillRect(0, 0, rect.width, rect.height);
  const cover = Math.max(rect.width / img.naturalWidth, rect.height / img.naturalHeight);
  const scale = Math.min(cover, MAX_BG_ZOOM);
  const w = img.naturalWidth * scale;
  const h = img.naturalHeight * scale;
  ctx.drawImage(img, (rect.width - w) / 2, (rect.height - h) / 2, w, h);
}

/** Фоновый кадр под открытый оверлей: меню, победа, магазин. */
function setScreen(name: 'menu' | 'victory' | 'shop' | null) {
  const file = name ? art.pack?.screens[name] : undefined;
  const img = art.get(file);
  document.documentElement.style.setProperty('--art-screen', img ? `url("${artImageUrl(file!)}")` : 'none');
}

function artImageUrl(file: string): string {
  return `/art/${file}`;
}

/* ──────────────────────────────── Разметка ─────────────────────────────── */

const app = document.getElementById('app')!;
app.innerHTML = `
  <div class="shell">
    <header class="hud">
      <div class="hud__left">
        <div class="hud__tier" id="tierName">ПЛАТО ПЕРВОГО КОНТАКТА</div>
        <div class="hud__level" id="levelLabel">УРОВЕНЬ 1 · КВАРТАЛ 4×4</div>
      </div>
      <div class="hud__right">
        <div class="stat"><span class="stat__k">ХОДЫ</span><span class="stat__v" id="moves">0</span></div>
        <div class="stat"><span class="stat__k">ВРЕМЯ</span><span class="stat__v" id="time">0:00</span></div>
      </div>
    </header>

    <main class="board-wrap">
      <canvas id="board"></canvas>
      <div class="banner" id="banner"></div>
    </main>

    <footer class="controls">
      <button class="btn" id="btnReset">Сброс</button>
      <button class="btn" id="btnHint">Подсказка</button>
      <button class="btn btn--ghost" id="btnMenu">Меню</button>
      <button class="btn btn--ghost" id="btnArt" title="Вид поля (клавиша A)">Вид: арт</button>
      <button class="btn btn--icon" id="btnSound" title="Звук">♪</button>
    </footer>
  </div>

  <div class="overlay" id="menuOverlay">
    <div class="card">
      <div class="logo">
        <span class="logo__mark"></span>
        <h1>АМПЕРГРАД</h1>
      </div>
      <p class="tagline">Верни городу ток</p>
      <p class="brief" id="menuBrief"></p>
      <div class="menu-actions">
        <button class="btn btn--primary" id="btnPlay">Продолжить</button>
        <button class="btn" id="btnDaily">Челлендж дня</button>
        <button class="btn btn--ghost" id="btnRestart">Начать заново</button>
      </div>
      <!-- Прыжок по кварталам: в лаборатории смотрят арт и правила, а не
           проходят семьдесят пять уровней подряд, чтобы дойти до Плавильни. -->
      <div class="menu-jump">
        <label for="jumpLevel">Квартал</label>
        <input id="jumpLevel" type="number" min="1" max="75" step="1" value="1" />
        <button class="btn btn--ghost" id="btnJump">Перейти</button>
      </div>
      <p class="footnote">
        Прототип правил. Ядро — общий код с Android-приложением и сервером.<br />
        Код PipeWalker не используется; проект вдохновлён жанром.
      </p>
    </div>
  </div>

  <div class="overlay" id="winOverlay">
    <div class="card card--win">
      <div class="win__label" id="winSealLabel">ПЛОМБА</div>
      <div class="seal" id="winSeal"></div>
      <h2 id="winTitle">Квартал дышит светом</h2>
      <div class="stars" id="winStars"></div>
      <div class="win__stats" id="winStats"></div>
      <div class="menu-actions">
        <button class="btn btn--primary" id="btnNext">Следующий квартал</button>
        <button class="btn btn--ghost" id="btnWinMenu">В меню</button>
      </div>
    </div>
  </div>
`;

const $ = <T extends HTMLElement>(id: string): T => document.getElementById(id) as T;

const canvas = $<HTMLCanvasElement>('board');
const ctx = canvas.getContext('2d')!;
const banner = $<HTMLDivElement>('banner');

/* ──────────────────────────────── Состояние ─────────────────────────────── */

const sfx = new Sfx();
let progress = loadProgress();

interface Session {
  mode: 'level' | 'daily';
  levelNumber: number;
  level: Level;
  moves: number;
  hintsUsed: number;
  startedAt: number;
  finishedAt: number | null;
  hintCells: Map<number, number>; // индекс → время окончания подсветки
  angle: number[]; // визуальный угол в четвертях оборота
  particles: Array<{ x: number; y: number; vx: number; vy: number; life: number; hue: string }>;
  solvedAt: number | null;
}

let session: Session | null = null;
let view: 'menu' | 'game' | 'win' = 'menu';

function startLevel(levelNumber: number, mode: 'level' | 'daily' = 'level') {
  // Для челленджа дня берём конфигурацию 6×6 (уровень 33-го яруса) с seed дня.
  const configLevel = mode === 'daily' ? 33 : levelNumber;
  const seed = mode === 'daily' ? dailySeed(Math.floor(Date.now() / 86_400_000)) : undefined;
  const level = generateLevel(configLevel, seed);
  level.level = mode === 'daily' ? 0 : levelNumber;
  session = {
    mode,
    levelNumber,
    level,
    moves: 0,
    hintsUsed: 0,
    startedAt: performance.now(),
    finishedAt: null,
    hintCells: new Map(),
    angle: level.tiles.map((t) => t.rot),
    particles: [],
    solvedAt: null,
  };
  view = 'game';
  $('menuOverlay').classList.remove('is-open');
  $('winOverlay').classList.remove('is-open');
  $('tierName').textContent = mode === 'daily' ? 'ЧЕЛЛЕНДЖ ДНЯ' : tierOf(levelNumber).name;
  $('tierName').style.color = mode === 'daily' ? '#ffc24b' : tierOf(levelNumber).accent;
  $('levelLabel').textContent =
    mode === 'daily'
      ? `ОБЩИЙ КВАРТАЛ ${level.size}×${level.size} · SEED ДНЯ`
      : `УРОВЕНЬ ${levelNumber} · КВАРТАЛ ${level.size}×${level.size}`;
  banner.textContent = `Подай ток на ${level.receivers.length} приёмник(а): все ячейки квартала должны быть подключены к шине`;
  banner.classList.add('is-visible');
  setTimeout(() => banner.classList.remove('is-visible'), 3200);

  // Фон яруса тянем заранее: первый кадр поля уже с картинкой, без мигания.
  const tier = tierOf(configLevel);
  const bgFile = tier.bg.length ? tier.bg[(configLevel - 1) % tier.bg.length] : undefined;
  if (bgFile) void art.image(bgFile).catch(() => null);
}

function refreshMenu() {
  const solved = Object.keys(progress.stars).length;
  $('menuBrief').textContent =
    solved > 0
      ? `Собрано кварталов: ${solved} · пломб: ${Object.values(progress.stars).reduce((a, b) => a + b, 0)}`
      : 'Ртутный ярус ждёт мастера. Ток не терпит суеты.';
  $('btnPlay').textContent = progress.level > 1 ? `Продолжить · уровень ${progress.level}` : 'Начать смену';
}

/* ──────────────────────────── Ввод (поворот) ──────────────────────────── */

interface Pending {
  index: number;
  timer: number;
  consumed: boolean;
  x: number;
  y: number;
}

let pending: Pending | null = null;

function tileAt(clientX: number, clientY: number): { index: number; x: number; y: number } | null {
  if (!session) return null;
  const rect = canvas.getBoundingClientRect();
  const size = session.level.size;
  const cell = geometry().cell;
  const ox = geometry().ox;
  const oy = geometry().oy;
  const x = clientX - rect.left;
  const y = clientY - rect.top;
  const gx = Math.floor((x - ox) / cell);
  const gy = Math.floor((y - oy) / cell);
  if (gx < 0 || gy < 0 || gx >= size || gy >= size) return null;
  return { index: gy * size + gx, x: ox + gx * cell + cell / 2, y: oy + gy * cell + cell / 2 };
}

function rotate(index: number, dir: 1 | -1) {
  if (!session || session.finishedAt) return;
  const before = session.level.tiles[index].rot;
  if (!applyRotate(session.level, index, dir)) return;
  session.moves++;
  sfx.rotate();
  if (session.level.tiles[index].rot !== before) {
    // корректируем визуальный угол в ту же сторону, чтобы не было «прыжка»
    session.angle[index] = session.angle[index] + dir;
  }
  if (isSolved(session.level)) finish();
}

canvas.addEventListener('pointerdown', (ev) => {
  const hit = tileAt(ev.clientX, ev.clientY);
  if (!hit) return;
  canvas.setPointerCapture(ev.pointerId);
  const timer = window.setTimeout(() => {
    if (pending && !pending.consumed) {
      pending.consumed = true;
      rotate(pending.index, -1); // долгое удержание — против часовой
    }
  }, 420);
  pending = { index: hit.index, timer, consumed: false, x: hit.x, y: hit.y };
});

canvas.addEventListener('pointerup', () => {
  if (!pending) return;
  clearTimeout(pending.timer);
  if (!pending.consumed) rotate(pending.index, 1);
  pending = null;
});

canvas.addEventListener('pointercancel', () => {
  if (pending) clearTimeout(pending.timer);
  pending = null;
});

canvas.addEventListener('contextmenu', (ev) => {
  ev.preventDefault();
  const hit = tileAt(ev.clientX, ev.clientY);
  if (hit) rotate(hit.index, -1);
});

/* ──────────────────────────── Победа и награды ──────────────────────────── */

function finish() {
  if (!session) return;
  session.finishedAt = performance.now();
  session.solvedAt = performance.now();
  const seconds = (session.finishedAt - session.startedAt) / 1000;
  const par = session.level.parMoves;
  let stars = session.moves <= par ? 3 : session.moves <= Math.ceil(par * 1.5) ? 2 : 1;
  if (session.hintsUsed > 0) stars = Math.min(stars, 2);
  sfx.solved();

  // Пломба на экране победы: медальон арта — по числу звёзд или особый.
  const sealKey = chooseSeal(
    stars,
    session.hintsUsed,
    session.moves,
    par,
    tierOf(session.mode === 'daily' ? 33 : session.levelNumber).id,
  );
  const sealPath = sealFile(sealKey);
  const seal = art.get(sealPath);
  $<HTMLElement>('winSealLabel').textContent = sealTitle(sealKey);
  const sealEl = $<HTMLDivElement>('winSeal');
  if (seal) {
    sealEl.style.backgroundImage = `url("${artImageUrl(sealPath!)}")`;
    sealEl.classList.add('is-on');
  } else {
    sealEl.classList.remove('is-on');
  }
  setScreen('victory');

  // Разлёт искр
  const { ox, oy, cell } = geometry();
  const sparkColor = tierOf(session.mode === 'daily' ? 33 : session.levelNumber).flow;
  const size = session.level.size;
  for (let i = 0; i < 140; i++) {
    const x = ox + Math.random() * cell * size;
    const y = oy + Math.random() * cell * size;
    const a = Math.random() * Math.PI * 2;
    const sp = 0.05 + Math.random() * 0.35;
    session.particles.push({
      x,
      y,
      vx: Math.cos(a) * sp,
      vy: Math.sin(a) * sp - 0.15,
      life: 1,
      hue: Math.random() < 0.75 ? sparkColor : '#ffffff',
    });
  }

  if (session.mode === 'level') {
    const n = session.levelNumber;
    progress.stars[n] = Math.max(progress.stars[n] ?? 0, stars);
    progress.bestMoves[n] = Math.min(progress.bestMoves[n] ?? Infinity, session.moves);
    if (n + 1 > progress.level) progress.level = n + 1;
    saveProgress(progress);
  }

  view = 'win';
  $('winStars').innerHTML = [0, 1, 2]
    .map((i) => `<span class="star ${i < stars ? 'is-on' : ''}">★</span>`)
    .join('');
  $('winStats').innerHTML = `
    <div><span>ХОДЫ</span><b>${session.moves}</b> <i>план ${par}</i></div>
    <div><span>ВРЕМЯ</span><b>${formatTime(seconds)}</b></div>
    <div><span>ПОДСКАЗКИ</span><b>${session.hintsUsed}</b></div>`;
  $('btnNext').textContent = session.mode === 'daily' ? 'Ещё раз' : 'Следующий квартал';
  setTimeout(() => $('winOverlay').classList.add('is-open'), 900);
}

function formatTime(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/* ──────────────────────────────── Геометрия ─────────────────────────────── */

function geometry() {
  const rect = canvas.getBoundingClientRect();
  const size = session?.level.size ?? 4;
  const pad = 18;
  const avail = Math.min(rect.width, rect.height) - pad * 2;
  const cell = Math.floor(Math.min(avail / size, 150));
  const board = cell * size;
  return {
    ox: Math.round((rect.width - board) / 2),
    oy: Math.round((rect.height - board) / 2),
    cell,
    board,
    size,
  };
}

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const rect = canvas.getBoundingClientRect();
  const w = Math.max(1, Math.floor(rect.width * dpr));
  const h = Math.max(1, Math.floor(rect.height * dpr));
  if (canvas.width === w && canvas.height === h) {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return;
  }
  canvas.width = w;
  canvas.height = h;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}

window.addEventListener('resize', resize);
// Холст меняет размер не только вместе с окном: колонка игры, шрифты, панель
// закладок — отсюда наблюдатель, иначе поле остаётся на старом размере.
if (typeof ResizeObserver !== 'undefined') new ResizeObserver(() => resize()).observe(canvas);

/* ──────────────────────────────── Рендер ──────────────────────────────── */

function draw(now: number) {
  const rect = canvas.getBoundingClientRect();
  // Если буфер холста разошёлся с его CSS-размером, всё поле уезжает за край и
  // видно один увеличенный фон. Проверяем каждый кадр — это дёшево.
  const dprNow = Math.min(window.devicePixelRatio || 1, 2);
  if (
    canvas.width !== Math.max(1, Math.floor(rect.width * dprNow)) ||
    canvas.height !== Math.max(1, Math.floor(rect.height * dprNow))
  ) {
    resize();
  }
  const levelIndex = session ? (session.mode === 'daily' ? 33 : session.levelNumber) : 1;
  const tier = tierOf(levelIndex);
  const accent = session ? (session.mode === 'daily' ? '#ffc24b' : tier.accent) : tier.accent;
  const artMode = artReady();
  const flow = artMode ? tier.flow : '#00e5c7';

  // Фон: кадр яруса из арта, иначе — прежний градиент.
  const bgFile = tier.bg.length ? tier.bg[(levelIndex - 1) % tier.bg.length] : undefined;
  const bgImg = artMode ? art.get(bgFile) : null;
  if (bgImg) {
    drawCover(bgImg, rect);
    ctx.fillStyle = 'rgba(4,6,11,0.52)'; // притеняем: трубы и ток должны читаться
    ctx.fillRect(0, 0, rect.width, rect.height);
  } else {
    const bg = ctx.createLinearGradient(0, 0, 0, rect.height);
    bg.addColorStop(0, '#070b13');
    bg.addColorStop(1, '#04060b');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, rect.width, rect.height);
  }

  if (!session) return;
  const { ox, oy, cell, size } = geometry();
  const level = session.level;
  const powered = session.finishedAt ? level.tiles.map(() => true) : poweredCells(level);
  const solvedT = session.solvedAt ? Math.min(1, (now - session.solvedAt) / 800) : 0;

  // Сетка-подложка: в арт-режиме деликатнее — трубы сами читаются как поле.
  ctx.save();
  ctx.strokeStyle = artMode ? 'rgba(190,210,235,0.055)' : 'rgba(90,120,160,0.10)';
  ctx.lineWidth = 1;
  for (let i = 0; i <= size; i++) {
    ctx.beginPath();
    ctx.moveTo(ox + i * cell, oy);
    ctx.lineTo(ox + i * cell, oy + size * cell);
    ctx.moveTo(ox, oy + i * cell);
    ctx.lineTo(ox + size * cell, oy + i * cell);
    ctx.stroke();
  }
  ctx.restore();

  // Плавная доводка углов: движемся к цели по кратчайшей дуге,
  // не «перескакивая» через 0/4 четверти.
  for (let i = 0; i < level.tiles.length; i++) {
    const target = level.tiles[i].rot;
    let diff = (((target - session.angle[i]) % 4) + 4) % 4;
    if (diff > 2) diff -= 4;
    if (Math.abs(diff) < 0.02) session.angle[i] = target;
    else session.angle[i] += diff * 0.25;
  }

  const flowPhase = (now % 2200) / 2200;
  const PORTHOLE: Record<number, readonly [number, number]> = {
    1: [0, -1],
    2: [1, 0],
    4: [0, 1],
    8: [-1, 0],
  };

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const tile = level.tiles[i];
      const cx = ox + x * cell + cell / 2;
      const cy = oy + y * cell + cell / 2;
      const on = powered[i];
      const rot = (session.angle[i] * Math.PI) / 2;

      if (artMode) {
        // Деталь трубы из арта: спрайт нарисован в положении своей маски, поэтому
        // его поворот равен повороту маски фрагмента — детали соседей стыкуются.
        const setName = setForCell(tier.sets, x, y, i);
        const sprite = art.sprite(setName, pieceForMask(tile.base));
        const img = art.get(sprite?.file);
        if (img) {
          ctx.save();
          ctx.translate(cx, cy);
          ctx.rotate(rot);
          ctx.drawImage(img, -cell / 2 - 0.5, -cell / 2 - 0.5, cell + 1, cell + 1);
          ctx.restore();
        }

        // Приёмник — открытый конец трубы: горловина смотрит по своему порту.
        // (Порядок поворотов тот же, что в макете поля: art/tools/mock_board.py.)
        if (tile.role === 'receiver') {
          const mouth = art.get(art.pack?.round.mouth?.file);
          const mask = tileMask(level, i);
          const spin = mask & 1 ? Math.PI : mask & 2 ? -Math.PI / 2 : mask & 8 ? Math.PI / 2 : 0;
          if (mouth) {
            ctx.save();
            ctx.translate(cx, cy);
            ctx.rotate(spin);
            ctx.drawImage(mouth, -cell * 0.47, -cell * 0.47, cell * 0.94, cell * 0.94);
            ctx.restore();
          }
        }

        // Шина — колпак поверх детали: отсюда среда уходит в квартал.
        if (tile.role === 'bus') {
          const cap = art.get(art.pack?.round.cap?.file);
          if (cap) {
            ctx.save();
            ctx.translate(cx, cy);
            ctx.drawImage(cap, -cell * 0.43, -cell * 0.43, cell * 0.86, cell * 0.86);
            ctx.restore();
          }
        }
      } else {
        // Схема: тело ячейки, сегменты по маске и ядро соединения.
        ctx.save();
        ctx.fillStyle = tile.role === 'bus' ? 'rgba(0,229,199,0.05)' : 'rgba(255,255,255,0.015)';
        const r = cell * 0.42;
        roundRect(ctx, cx - r, cy - r, r * 2, r * 2, cell * 0.16);
        ctx.fill();
        ctx.restore();

        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(rot);
        const len = cell * 0.5;
        const width = Math.max(4, cell * 0.12);
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        for (const [bit, ang] of [
          [N, -Math.PI / 2],
          [E, 0],
          [S, Math.PI / 2],
          [W, Math.PI],
        ] as const) {
          if (!(tile.base & bit)) continue;
          ctx.beginPath();
          ctx.moveTo(0, 0);
          ctx.lineTo(Math.cos(ang) * len, Math.sin(ang) * len);
          ctx.strokeStyle = on ? flow : '#26334a';
          ctx.lineWidth = width;
          ctx.shadowColor = on ? flow : 'transparent';
          ctx.shadowBlur = on ? cell * 0.28 + solvedT * cell * 0.3 : 0;
          ctx.stroke();
        }
        ctx.beginPath();
        ctx.arc(0, 0, width * 0.62, 0, Math.PI * 2);
        ctx.fillStyle = on ? '#eafff9' : '#16202f';
        ctx.fill();
        ctx.restore();
      }

      // Среда внутри трубы: узкая светящаяся полоса от центра к портам плюс
      // ядро соединения. Так же нарисован макет поля, поэтому вид совпадает.
      if (on) {
        ctx.save();
        ctx.lineCap = 'round';
        ctx.strokeStyle = flow;
        ctx.shadowColor = flow;
        ctx.shadowBlur = cell * (0.2 + solvedT * 0.3);
        ctx.globalAlpha = 0.52 + 0.22 * Math.sin(flowPhase * Math.PI * 2 + (x + y) * 0.7);
        for (const bit of [N, E, S, W]) {
          if (!(tile.base & bit)) continue;
          const [dx, dy] = PORTHOLE[bit];
          const ang = rot + Math.atan2(dy, dx);
          ctx.beginPath();
          ctx.moveTo(cx, cy);
          ctx.lineTo(cx + Math.cos(ang) * cell * 0.5, cy + Math.sin(ang) * cell * 0.5);
          ctx.lineWidth = Math.max(3, cell * 0.075);
          ctx.stroke();
        }
        ctx.globalAlpha = 0.8;
        ctx.beginPath();
        ctx.arc(cx, cy, Math.max(2.5, cell * 0.06), 0, Math.PI * 2);
        ctx.fillStyle = flow;
        ctx.fill();
        ctx.restore();
      }

      // Шина: пульсирующее кольцо источника тока.
      if (tile.role === 'bus') {
        const pulse = 0.75 + 0.25 * Math.sin(now / 320);
        ctx.save();
        ctx.strokeStyle = accent;
        ctx.lineWidth = 2;
        ctx.shadowColor = accent;
        ctx.shadowBlur = 14 * pulse;
        ctx.beginPath();
        ctx.arc(cx, cy, cell * 0.32, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx, cy, cell * 0.22, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * ((now / 1400) % 1));
        ctx.stroke();
        ctx.restore();
      }

      // Приёмник: отметка цели (в арт-режиме — рамка поверх горловины).
      if (tile.role === 'receiver') {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(Math.PI / 4);
        const d = cell * (artMode ? 0.36 : 0.3);
        ctx.strokeStyle = on ? accent : artMode ? 'rgba(255,176,64,0.55)' : '#3a4a63';
        ctx.lineWidth = 2;
        ctx.shadowColor = on ? accent : 'transparent';
        ctx.shadowBlur = on ? 16 : 0;
        ctx.strokeRect(-d, -d, d * 2, d * 2);
        ctx.restore();
      }

      // Подсветка подсказки
      const hintUntil = session.hintCells.get(i);
      if (hintUntil && hintUntil > now) {
        ctx.save();
        ctx.strokeStyle = '#ff2e8a';
        ctx.lineWidth = 3;
        ctx.globalAlpha = 0.5 + 0.5 * Math.sin(now / 160);
        ctx.beginPath();
        ctx.arc(cx, cy, cell * 0.46, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }
    }
  }

  // Искры победы
  if (session.particles.length > 0) {
    ctx.save();
    for (const p of session.particles) {
      ctx.globalAlpha = Math.max(0, p.life);
      ctx.fillStyle = p.hue;
      ctx.fillRect(p.x, p.y, 2.5, 2.5);
      p.x += p.vx * 6;
      p.y += p.vy * 6;
      p.vy += 0.012;
      p.life -= 0.012;
    }
    ctx.restore();
    session.particles = session.particles.filter((p) => p.life > 0);
  }

  // HUD-цифры
  const seconds = ((session.finishedAt ?? now) - session.startedAt) / 1000;
  $('moves').textContent = String(session.moves);
  $('time').textContent = formatTime(seconds);
}

function roundRect(c: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  c.beginPath();
  c.moveTo(x + r, y);
  c.arcTo(x + w, y, x + w, y + h, r);
  c.arcTo(x + w, y + h, x, y + h, r);
  c.arcTo(x, y + h, x, y, r);
  c.arcTo(x, y, x + w, y, r);
  c.closePath();
}

/* ──────────────────────────────── Кнопки ──────────────────────────────── */

function openMenu() {
  view = 'menu';
  refreshMenu();
  $('menuOverlay').classList.add('is-open');
  $('winOverlay').classList.remove('is-open');
  setScreen('menu');
  setRenderLabel();
}

$('btnPlay').addEventListener('click', () => startLevel(progress.level));
$('btnDaily').addEventListener('click', () => startLevel(33, 'daily'));
$('btnRestart').addEventListener('click', () => {
  progress = { level: 1, stars: {}, bestMoves: {} };
  saveProgress(progress);
  refreshMenu();
});
$('btnJump').addEventListener('click', () => {
  const raw = Number($<HTMLInputElement>('jumpLevel').value);
  if (!Number.isFinite(raw)) return;
  startLevel(Math.min(75, Math.max(1, Math.round(raw))));
});
$('jumpLevel').addEventListener('keydown', (event) => {
  if ((event as KeyboardEvent).key === 'Enter') $<HTMLButtonElement>('btnJump').click();
});
$('btnReset').addEventListener('click', () => {
  if (!session) return;
  if (session.mode === 'level') startLevel(session.levelNumber);
  else startLevel(session.levelNumber, 'daily');
});
$('btnHint').addEventListener('click', () => {
  if (!session || session.finishedAt) return;
  const h = findHint(session.level);
  if (!h) return;
  session.hintsUsed++;
  session.hintCells.set(h.index, performance.now() + 2000);
  sfx.hint();
  banner.textContent = 'Клемма: «Позиция 4.2 — доверните фрагмент до упора, мастер»';
  banner.classList.add('is-visible');
  setTimeout(() => banner.classList.remove('is-visible'), 2200);
});
$('btnMenu').addEventListener('click', openMenu);
$('btnWinMenu').addEventListener('click', openMenu);
$('btnNext').addEventListener('click', () => {
  if (!session) return;
  if (session.mode === 'daily') startLevel(33, 'daily');
  else startLevel(session.levelNumber + 1);
});
/** Подпись кнопки вида: сразу видно, какой режим включён. */
function setRenderLabel() {
  const ready = art.pack !== null;
  const label = renderMode === 'scheme' ? 'Вид: схема' : ready ? 'Вид: арт' : 'Вид: арт (нет)';
  $('btnArt').textContent = label;
  $('btnArt').title = ready
    ? 'Вид поля: трубы из арта или чистая схема (клавиша A)'
    : 'Ассеты арта не найдены — собираются скриптом packages/lab/scripts/build-art.py';
}

function toggleRender() {
  renderMode = renderMode === 'art' ? 'scheme' : 'art';
  try {
    localStorage.setItem(RENDER_KEY, renderMode);
  } catch {
    /* приватный режим — просто переключаем на время сессии */
  }
  setRenderLabel();
  if (art.pack) setScreen(view === 'menu' ? 'menu' : null);
}

$('btnArt').addEventListener('click', toggleRender);

$('btnSound').addEventListener('click', () => {
  sfx.enabled = !sfx.enabled;
  $('btnSound').classList.toggle('is-off', !sfx.enabled);
});

window.addEventListener('keydown', (ev) => {
  if (ev.key === 'r' || ev.key === 'R') $('btnReset').click();
  if (ev.key === 'h' || ev.key === 'H') $('btnHint').click();
  if (ev.key === 'a' || ev.key === 'A') toggleRender();
  if (ev.key === 'd' || ev.key === 'D') showDiagnostics();
  if (ev.key === 'Escape') openMenu();
});

/**
 * Служебная строка состояния (клавиша D): размеры холста, плотность пикселей,
 * арт и геометрия поля. Когда поле выглядит «просто увеличенным фоном», эти
 * числа говорят, что именно разошлось — буфер холста, арт или вёрстка.
 */
function showDiagnostics(): void {
  const rect = canvas.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const geo = geometry();
  const lines = [
    `холст: CSS ${Math.round(rect.width)}×${Math.round(rect.height)}, буфер ${canvas.width}×${canvas.height}, dpr ${dpr}`,
    `поле: ${geo.size}×${geo.size}, ячейка ${geo.cell}, сдвиг ${geo.ox},${geo.oy}`,
    `арт: ${art.pack ? 'загружен' : `нет (${art.error ?? 'ещё грузится'})`}, вид «${renderMode}»`,
  ];
  banner.textContent = lines.join(' · ');
  banner.classList.add('is-visible');
  setTimeout(() => banner.classList.remove('is-visible'), 9000);
  console.log('[амперград] ' + lines.join('\n'));
}

/* ──────────────────────────────── Цикл ──────────────────────────────── */

function loop(now: number) {
  draw(now);
  requestAnimationFrame(loop);
}

resize();
refreshMenu();
setRenderLabel();
openMenu();
requestAnimationFrame(loop);

// Арт грузим после старта: игра открывается мгновенно, картинки догоняют.
void art.load().then((ok) => {
  if (!ok) {
    console.warn('арт не загрузился, играем схемой:', art.error);
    // Молчать нельзя: игрок видит пустое поле и думает, что игра сломана.
    banner.textContent = `Арт не загрузился (${art.error}) — играем схемой. Проверь, что папка packages/lab/public/art на месте.`;
    banner.classList.add('is-visible');
    setTimeout(() => banner.classList.remove('is-visible'), 8000);
  }
  setRenderLabel();
  if (view === 'menu') setScreen('menu');
});

// Служебный экспорт для отладки в консоли (например, чтобы проверить уровень).
(window as unknown as { ampergrad: unknown }).ampergrad = { startLevel, generateLevel, levelConfig, tileMask, E, N, S, W };
