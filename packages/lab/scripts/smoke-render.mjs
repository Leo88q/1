#!/usr/bin/env node
/**
 * Смоук-тест рендера игры: прогоняем собранную сборку в Node с подставным DOM,
 * подставным canvas и настоящими файлами ассетов из `public/art`.
 *
 * Зачем: проверки контракта (`check-web-art.mjs`) говорят, что ассеты на месте, но
 * не говорят, что игра их **рисует**. Здесь мы запускаем тот же бандл, что уезжает
 * в браузер, стартуем уровень и смотрим, что реально попало в canvas: спрайты труб,
 * цвет среды яруса, повороты. Ловит и битые пути, и выбранный не тот спрайт, и
 * падение в рендере — без браузера, поэтому гоняется в CI.
 *
 * Запуск: npm run build && node packages/lab/scripts/smoke-render.mjs
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const HERE = import.meta.dirname;
const LAB = resolve(HERE, '..');
const PUBLIC_ART = join(LAB, 'public', 'art');
const DIST = join(LAB, 'dist', 'assets');

if (!existsSync(DIST)) {
  console.error('нет сборки: сначала `npm run build` (или `npm run dev` для игры вживую)');
  process.exit(1);
}
const bundle = readdirSync(DIST).find((f) => f.endsWith('.js'));
if (!bundle) {
  console.error('в dist/assets нет js-сборки');
  process.exit(1);
}

const web = JSON.parse(readFileSync(join(PUBLIC_ART, 'web.json'), 'utf8'));
const assetFiles = new Set();
for (const pieces of Object.values(web.sets)) for (const ref of Object.values(pieces)) assetFiles.add(ref.file);
for (const ref of Object.values(web.round)) assetFiles.add(ref.file);
for (const tier of web.tiers) for (const bg of tier.bg) assetFiles.add(bg);
for (const rel of Object.values(web.screens)) assetFiles.add(rel);
for (const rel of Object.values(web.rewards)) assetFiles.add(rel);
for (const rel of Object.values(web.seals ?? {})) assetFiles.add(rel);
for (const rel of Object.values(web.props ?? {})) assetFiles.add(rel);

/* ───────────────────────────── Подставной DOM ───────────────────────────── */

class ClassList {
  constructor() {
    this.set = new Set();
  }
  add(...names) {
    names.forEach((n) => this.set.add(n));
  }
  remove(...names) {
    names.forEach((n) => this.set.delete(n));
  }
  toggle(name, force) {
    const on = force ?? !this.set.has(name);
    if (on) this.set.add(name);
    else this.set.delete(name);
    return on;
  }
  contains(name) {
    return this.set.has(name);
  }
}

const elements = new Map();
function element(id) {
  if (elements.has(id)) return elements.get(id);
  const el = {
    id,
    tagName: id === 'board' ? 'CANVAS' : 'DIV',
    classList: new ClassList(),
    style: { setProperty() {}, removeProperty() {}, getPropertyValue: () => '' },
    dataset: {},
    textContent: '',
    innerHTML: '',
    title: '',
    width: 0,
    height: 0,
    addEventListener() {},
    removeEventListener() {},
    setPointerCapture() {},
    appendChild() {},
    focus() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 700, right: 390, bottom: 700 }),
  };
  elements.set(id, el);
  return el;
}

/** Записи о том, что рендер сделал с canvas: вызовы и установки свойств. */
const events = [];
const images = new Map(); // объект Image → путь
const failedImages = [];

function recordingContext() {
  const gradient = { addColorStop() {} };
  const target = {};
  return new Proxy(target, {
    get(_t, prop) {
      if (prop === 'createLinearGradient' || prop === 'createRadialGradient') return () => gradient;
      if (prop === 'canvas') return element('board');
      if (prop === 'measureText') return () => ({ width: 10 });
      if (typeof prop === 'symbol') return undefined;
      return (...args) => {
        if (prop === 'drawImage') {
          events.push({ kind: 'drawImage', src: images.get(args[0]) ?? null, args: args.slice(1) });
        } else if (prop === 'rotate' || prop === 'arc' || prop === 'lineTo') {
          events.push({ kind: prop, args });
        } else {
          events.push({ kind: prop });
        }
      };
    },
    set(_t, prop, value) {
      events.push({ kind: 'set', prop, value });
      return true;
    },
  });
}

const canvasEl = element('board');
canvasEl.getContext = () => recordingContext();
canvasEl.setPointerCapture = () => {};

class FakeImage {
  constructor() {
    this.complete = false;
    this.naturalWidth = 0;
    this.naturalHeight = 0;
    this.decoding = 'sync';
    this.listeners = new Map();
  }
  addEventListener(type, fn) {
    const list = this.listeners.get(type) ?? [];
    list.push(fn);
    this.listeners.set(type, list);
  }
  fire(type) {
    for (const fn of this.listeners.get(type) ?? []) fn();
  }
  set src(value) {
    this._src = value;
    const rel = value.replace(/^\/art\//, '');
    images.set(this, rel);
    if (existsSync(join(PUBLIC_ART, rel))) {
      this.complete = true;
      this.naturalWidth = 256;
      this.naturalHeight = 256;
      setTimeout(() => this.fire('load'), 0);
    } else {
      failedImages.push(rel);
      setTimeout(() => this.fire('error'), 0);
    }
  }
  get src() {
    return this._src;
  }
}

const rafQueue = [];
globalThis.window = {
  devicePixelRatio: 2,
  addEventListener() {},
  removeEventListener() {},
  setTimeout: (fn, ms) => setTimeout(fn, ms),
  clearTimeout: (id) => clearTimeout(id),
  requestAnimationFrame: (cb) => {
    rafQueue.push(cb);
    return rafQueue.length;
  },
};
globalThis.document = {
  getElementById: (id) => element(id),
  querySelector: () => null,
  querySelectorAll: () => [],
  documentElement: { style: { setProperty() {}, removeProperty() {} } },
  head: element('head'),
  body: element('body'),
  addEventListener() {},
  createElement: () => element('tmp'),
};
globalThis.localStorage = {
  store: new Map(),
  getItem(key) {
    return this.store.get(key) ?? null;
  },
  setItem(key, value) {
    this.store.set(key, String(value));
  },
  removeItem(key) {
    this.store.delete(key);
  },
};
globalThis.Image = FakeImage;
globalThis.MutationObserver = class {
  observe() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
};
globalThis.CustomEvent = class {
  constructor(type, init) {
    this.type = type;
    Object.assign(this, init);
  }
};
globalThis.requestAnimationFrame = globalThis.window.requestAnimationFrame;
globalThis.fetch = async (url) => {
  const rel = String(url).replace(/^\/art\//, '');
  const path = join(PUBLIC_ART, rel);
  if (!existsSync(path)) return { ok: false, status: 404, json: async () => ({}) };
  return { ok: true, status: 200, json: async () => JSON.parse(readFileSync(path, 'utf8')) };
};

/* ───────────────────────────────── Прогон ───────────────────────────────── */

const tick = () => new Promise((r) => setTimeout(r, 20));
function frames(count) {
  for (let i = 0; i < count; i++) {
    const cb = rafQueue.shift();
    if (cb) cb(16 * (i + 1));
  }
}

try {
  await import(pathToFileURL(join(DIST, bundle)).href);
} catch (err) {
  console.error('✗ сборка упала при запуске:', err.message);
  process.exit(1);
}

await tick();
const api = globalThis.window.ampergrad;
if (!api?.startLevel) {
  console.error('✗ игра не выставила window.ampergrad — рендер не инициализировался');
  process.exit(1);
}

api.startLevel(1);
await tick();
frames(4);
await tick();

/* ─────────────────────────────── Проверки ─────────────────────────────── */

const problems = [];
if (failedImages.length > 0) problems.push(`не загрузились файлы: ${failedImages.join(', ')}`);

const drawn = events.filter((e) => e.kind === 'drawImage' && e.src);
const unknown = drawn.filter((e) => !assetFiles.has(e.src));
if (unknown.length > 0) problems.push(`рисуются файлы не из манифеста: ${[...new Set(unknown.map((e) => e.src))].join(', ')}`);

// Уровень 1 — квартал 4×4: каждая ячейка обязана получить спрайт.
if (drawn.length < 16) problems.push(`спрайтов нарисовано ${drawn.length}, а в квартале 4×4 нужно минимум 16`);

const rotations = events.filter((e) => e.kind === 'rotate' && Math.abs(e.args[0]) > 0.01);
if (rotations.length === 0) problems.push('ни один спрайт не повёрнут — сетка собрана без поворотов (неверная маска?)');

const flow = web.tiers[0].flow.toLowerCase();
const flowUsed = events.some((e) => e.kind === 'set' && e.prop === 'strokeStyle' && String(e.value).toLowerCase() === flow);
if (!flowUsed) problems.push(`цвет среды яруса ${flow} в рендер не попал`);

const bgUsed = drawn.some((e) => e.src?.startsWith('back/'));
if (!bgUsed) problems.push('фон яруса не нарисован');

const sprites = new Set(drawn.filter((e) => e.src?.startsWith('tiles/')).map((e) => e.src));
if (sprites.size < 3) problems.push(`за уровень нарисовано всего ${sprites.size} разных деталей — похоже, набор не подхватился`);

if (problems.length > 0) {
  console.error('смоук-тест рендера не прошёл:');
  for (const p of problems) console.error(`  ✗ ${p}`);
  process.exit(1);
}

console.log(
  `смоук-тест ок: нарисовано ${drawn.length} картинок (${sprites.size} разных деталей), ` +
    `поворотов ${rotations.length}, среда ${web.tiers[0].flow}, фон яруса на месте`,
);
process.exit(0);
