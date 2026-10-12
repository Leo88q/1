import { describe, expect, it } from 'vitest';
import {
  COSMETICS,
  QUARTERS,
  REGISTRY,
  SPEAKERS_EN,
  TIERS,
  cosmeticById,
  quarterForLevel,
  registryById,
  registryForLevel,
  speakerForLang,
  storeItems,
  tierForLevel,
} from '../src';

describe('лор-реестр и магазин (M3)', () => {
  it('ярусы покрывают уровни 1–75 без дыр и пересечений', () => {
    const covered: number[] = [];
    for (const tier of TIERS) {
      for (let l = tier.levels[0]; l <= tier.levels[1]; l++) covered.push(l);
    }
    expect(covered).toEqual(Array.from({ length: 75 }, (_, i) => i + 1));
  });

  it('у каждого уровня есть квартал с именем и кодом яруса', () => {
    expect(QUARTERS).toHaveLength(75);
    const codes = new Set<string>();
    for (let l = 1; l <= 75; l++) {
      const quarter = quarterForLevel(l);
      expect(quarter, `уровень ${l}`).toBeDefined();
      expect(quarter!.name.length).toBeGreaterThan(2);
      expect(quarter!.code).toMatch(/^[ПРТДЯ]-\d{2}$/);
      codes.add(quarter!.code);
    }
    expect(codes.size).toBe(75);
  });

  it('имена кварталов не повторяются ни в одном языке', () => {
    const names = QUARTERS.map((q) => q.name);
    expect(new Set(names).size).toBe(names.length);
    const namesEn = QUARTERS.map((q) => q.nameEn);
    expect(new Set(namesEn).size).toBe(namesEn.length);
  });

  it('английские имена кварталов переведены, а не скопированы', () => {
    const cyrillic = /[А-Яа-яЁё]/;
    for (const quarter of QUARTERS) {
      expect(quarter.nameEn.length, `уровень ${quarter.level}`).toBeGreaterThan(3);
      expect(cyrillic.test(quarter.nameEn), `${quarter.level}: ${quarter.nameEn}`).toBe(false);
    }
    const copies = QUARTERS.filter((q) => q.nameEn === q.name).map((q) => q.level);
    expect(copies).toEqual([]);
  });

  it('реестр: 60 записей, три сезона по 20, идентификаторы уникальны', () => {
    expect(REGISTRY).toHaveLength(60);
    expect(new Set(REGISTRY.map((r) => r.id)).size).toBe(60);
    for (const season of [1, 2, 3] as const) {
      expect(REGISTRY.filter((r) => r.season === season)).toHaveLength(20);
    }
    for (const entry of REGISTRY) {
      expect(entry.id).toMatch(/^reg-s[123]-\d{2}$/);
      expect(entry.text.length).toBeGreaterThan(30);
      expect(entry.speaker.length).toBeGreaterThan(2);
      expect(entry.source.length).toBeGreaterThan(3);
      expect(entry.tiers.length).toBeGreaterThan(0);
      for (const tierId of entry.tiers) {
        expect(TIERS.some((t) => t.id === tierId), `${entry.id}: ярус ${tierId}`).toBe(true);
      }
    }
    expect(registryById('reg-s1-01')?.speaker).toBe('Клемма');
    expect(registryById('нет-такого')).toBeUndefined();
  });

  it('реестр переведён: у каждой записи английские поля без кириллицы', () => {
    const cyrillic = /[А-Яа-яЁё]/;
    for (const entry of REGISTRY) {
      expect(entry.titleEn.length, entry.id).toBeGreaterThan(3);
      expect(entry.textEn.length, entry.id).toBeGreaterThan(30);
      expect(entry.sourceEn.length, entry.id).toBeGreaterThan(3);
      expect(cyrillic.test(entry.titleEn), `${entry.id}: ${entry.titleEn}`).toBe(false);
      expect(cyrillic.test(entry.textEn), `${entry.id}: ${entry.textEn}`).toBe(false);
      expect(cyrillic.test(entry.sourceEn), `${entry.id}: ${entry.sourceEn}`).toBe(false);
      expect(entry.titleEn === entry.title, `${entry.id}: название не переведено`).toBe(false);
      expect(entry.textEn === entry.text, `${entry.id}: текст не переведён`).toBe(false);
    }
    // Английские тексты уникальны — это перевод, а не копия одной фразы.
    expect(new Set(REGISTRY.map((r) => r.textEn)).size).toBe(REGISTRY.length);
  });

  it('голоса города переведены и словарь полный', () => {
    const speakers = new Set(REGISTRY.map((r) => r.speaker));
    for (const speaker of speakers) {
      expect(SPEAKERS_EN[speaker], `нет перевода для «${speaker}»`).toBeTruthy();
      expect(speakerForLang(speaker, 'en')).not.toBe(speaker);
      expect(speakerForLang(speaker, 'ru')).toBe(speaker);
    }
    expect(Object.keys(SPEAKERS_EN).sort()).toEqual([...speakers].sort());
  });

  it('магазин: наградные предметы бесплатны, платные — только косметика', () => {
    expect(COSMETICS.length).toBeGreaterThanOrEqual(18);
    expect(new Set(COSMETICS.map((c) => c.id)).size).toBe(COSMETICS.length);
    for (const item of COSMETICS) {
      expect(item.lore.length, item.id).toBeGreaterThan(20);
      expect(item.colors.length, item.id).toBeGreaterThanOrEqual(2);
      if (item.channel === 'reward') {
        expect(item.priceUsd, item.id).toBeNull();
        expect(item.priceCoins, item.id).toBeNull();
        expect(item.standard, item.id).not.toBeNull();
      }
      if (item.channel === 'store') {
        expect(item.priceUsd, item.id).toBeGreaterThan(0);
        expect(item.priceCoins, item.id).toBeNull();
      }
      if (item.channel === 'coins') {
        expect(item.priceCoins, item.id).toBeGreaterThan(0);
        expect(item.priceUsd, item.id).toBeNull();
      }
    }
  });

  it('каждая награда сервера имеет карточку в магазине (один идентификатор)', () => {
    // Идентификаторы наград, которые выдаёт сервер (server/src/rewards.ts).
    const serverRewards = [
      'plomba-first',
      'plomba-ten',
      'film-mercury',
      'mark-old-diode',
      'sign-daily',
      'sign-streak-seven',
    ];
    for (const id of serverRewards) {
      const item = cosmeticById(id);
      expect(item, `нет карточки для ${id}`).toBeDefined();
      expect(item!.channel).toBe('reward');
    }
  });

  it('в витрине нет ничего, что даёт преимущество: только косметика', () => {
    const visual = new Set(['palette', 'theme', 'wing', 'source', 'seal', 'registry']);
    for (const item of storeItems()) {
      expect(visual.has(item.type), item.id).toBe(true);
    }
  });

  it('реестр выдаётся по уровням по порядку и собирается целиком', () => {
    const seen: string[] = [];
    for (let l = 1; l <= 75; l++) {
      const entry = registryForLevel(l);
      expect(entry, `уровень ${l}`).toBeDefined();
      seen.push(entry!.id);
    }
    expect(seen[0]).toBe('reg-s1-01');
    expect(seen[74]).toBe('reg-s3-20');
    expect(new Set(seen).size).toBe(REGISTRY.length);
    // Порядок не откатывается назад: страницы открываются последовательно.
    const order = REGISTRY.map((r) => r.id);
    for (let i = 1; i < seen.length; i++) {
      expect(order.indexOf(seen[i]!)).toBeGreaterThanOrEqual(order.indexOf(seen[i - 1]!));
    }
  });

});
