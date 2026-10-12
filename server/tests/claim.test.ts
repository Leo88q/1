import { describe, expect, it } from 'vitest';
import { createApp } from '../src/app';
import { loadEnv } from '../src/env';
import { Minter } from '../src/mint';
import { MemoryStore } from '../src/store';
import { dailyProof, levelProof, createWallet } from './helpers';

const TODAY = 20_365;
const NOW = TODAY * 86_400_000 + 3_600_000;

function buildApp(overrides: Record<string, string> = {}) {
  const env = loadEnv({
    AMPERGRAD_MINT_MODE: 'dry-run',
    AMPERGRAD_ALLOW_UNVERIFIED: 'false',
    ...overrides,
  } as NodeJS.ProcessEnv);
  const store = new MemoryStore();
  const minter = new Minter(env);
  const app = createApp({ env, store, minter, now: () => NOW });
  return { app, store, env };
}

async function post(app: ReturnType<typeof buildApp>['app'], path: string, body: unknown) {
  const response = await app.request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { status: response.status, json: (await response.json()) as Record<string, unknown> };
}

describe('сервер клейма', () => {
  it('отдаёт здоровье с версией правил', async () => {
    const { app } = buildApp();
    const response = await app.request('/health');
    const json = (await response.json()) as Record<string, unknown>;
    expect(response.status).toBe(200);
    expect(json.ok).toBe(true);
    expect(json.engine).toBe('0.1.0');
    expect(json.mintMode).toBe('dry-run');
  });

  it('полный поток: доказательство → задача → подпись → награда', async () => {
    const { app, store } = buildApp();
    const wallet = createWallet();
    const proof = levelProof(1);

    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', proof });
    expect(start.status).toBe(200);
    expect(start.json.rewardId).toBe('plomba-first');
    expect(typeof start.json.nonce).toBe('string');
    expect(typeof start.json.message).toBe('string');

    const message = start.json.message as string;
    const submit = await post(app, '/claim/submit', {
      address: wallet.address,
      nonce: start.json.nonce,
      signature: wallet.sign(message),
      rewardId: start.json.rewardId,
      proof,
    });

    expect(submit.status).toBe(200);
    expect(submit.json.status).toBe('queued'); // dry-run: награда в очереди, ничего не тратится
    expect(submit.json.wallet).toMatch(/…/);

    const progress = await store.getProgress(wallet.address);
    expect(progress.solvedLevels.has(1)).toBe(true);
  });

  it('подпись другого кошелька отклоняется', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const attacker = createWallet();
    const proof = levelProof(1);

    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', proof });
    const submit = await post(app, '/claim/submit', {
      address: attacker.address,
      nonce: start.json.nonce,
      signature: attacker.sign(start.json.message as string),
      rewardId: start.json.rewardId,
      proof,
    });
    expect(submit.status).toBe(403); // задача выдана другому кошельку
  });

  it('неверная подпись отклоняется', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const proof = levelProof(1);
    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', proof });

    const submit = await post(app, '/claim/submit', {
      address: wallet.address,
      nonce: start.json.nonce,
      signature: wallet.sign('поддельное сообщение'),
      rewardId: start.json.rewardId,
      proof,
    });
    expect(submit.status).toBe(401);
  });

  it('nonce одноразовый', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const proof = levelProof(2);
    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', proof });
    const signature = wallet.sign(start.json.message as string);

    const first = await post(app, '/claim/submit', {
      address: wallet.address,
      nonce: start.json.nonce,
      signature,
      rewardId: start.json.rewardId,
      proof,
    });
    const second = await post(app, '/claim/submit', {
      address: wallet.address,
      nonce: start.json.nonce,
      signature,
      rewardId: start.json.rewardId,
      proof,
    });
    expect(first.status).toBe(200);
    expect(second.status).toBe(410);
  });

  it('поддельное доказательство решения отклоняется', async () => {
    const { app, store } = buildApp();
    const wallet = createWallet();
    const honest = levelProof(3);
    const fake = { ...honest, turns: honest.turns.slice(0, Math.max(1, honest.turns.length - 3)) };
    fake.moves = fake.turns.length;

    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', proof: fake });
    expect(start.status).toBe(422);

    const progress = await store.getProgress(wallet.address);
    expect(progress.solvedLevels.size).toBe(0);
  });

  it('чужие ходы (не тот уровень) отклоняются', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const proof = levelProof(7);
    // Тот же набор ходов, но заявлен как уровень 8 — реплей даст другой квартал.
    const forged = { ...proof, level: 8 };
    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', proof: forged });
    expect(start.status).toBe(422);
  });

  it('версия правил проверяется', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.0.9' });
    expect(start.status).toBe(409);
  });

  it('клейм без доказательства запрещён в строгом режиме', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const start = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0' });
    expect(start.status).toBe(409); // нет подтверждённого прогресса

    const relaxed = buildApp({ AMPERGRAD_ALLOW_UNVERIFIED: 'true' });
    const wallet2 = createWallet();
    const start2 = await post(relaxed.app, '/claim/start', { address: wallet2.address, engine: '0.1.0' });
    expect(start2.status).toBe(200);
    const submit2 = await post(relaxed.app, '/claim/submit', {
      address: wallet2.address,
      nonce: start2.json.nonce,
      signature: wallet2.sign(start2.json.message as string),
      rewardId: start2.json.rewardId,
    });
    expect(submit2.status).toBe(200);
  });

  it('одна и та же награда не выдаётся дважды', async () => {
    const { app } = buildApp({ AMPERGRAD_ALLOW_UNVERIFIED: 'true' });
    const wallet = createWallet();

    const first = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0' });
    await post(app, '/claim/submit', {
      address: wallet.address,
      nonce: first.json.nonce,
      signature: wallet.sign(first.json.message as string),
      rewardId: first.json.rewardId,
    });

    const second = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0', rewardId: 'plomba-first' });
    expect(second.status).toBe(409);
    expect(String(second.json.error)).toContain('реестр');
  });

  it('дневной лимит наград работает', async () => {
    const { app } = buildApp({ AMPERGRAD_ALLOW_UNVERIFIED: 'true', AMPERGRAD_MAX_CLAIMS_PER_DAY: '1' });
    const wallet = createWallet();

    const first = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0' });
    const submit = await post(app, '/claim/submit', {
      address: wallet.address,
      nonce: first.json.nonce,
      signature: wallet.sign(first.json.message as string),
      rewardId: first.json.rewardId,
    });
    expect(submit.status).toBe(200);

    const second = await post(app, '/claim/start', { address: wallet.address, engine: '0.1.0' });
    expect(second.status).toBe(429); // дневной лимит: вторая награда — завтра
    expect(String(second.json.error)).toContain('дневной лимит')
  });
});

describe('челлендж дня', () => {
  it('принимает подписанный результат и ведёт лидерборд', async () => {
    const { app, store } = buildApp();
    const wallet = createWallet();
    const proof = dailyProof(TODAY);

    const start = await post(app, '/daily/start', {
      address: wallet.address,
      engine: '0.1.0',
      day: TODAY,
      moves: proof.moves,
    });
    expect(start.status).toBe(200);

    const submit = await post(app, '/daily/submit', {
      address: wallet.address,
      nonce: start.json.nonce,
      signature: wallet.sign(start.json.message as string),
      proof,
    });
    expect(submit.status).toBe(200);
    expect(submit.json.status).toBe('accepted');
    expect(submit.json.rank).toBe(1);

    const board = await app.request(`/daily/leaderboard?day=${TODAY}`);
    const json = (await board.json()) as { entries: Array<{ wallet: string; moves: number }> };
    expect(json.entries).toHaveLength(1);
    expect(json.entries[0].moves).toBe(proof.moves);

    const progress = await store.getProgress(wallet.address);
    expect(progress.dailyDays.has(TODAY)).toBe(true);
    expect(progress.bestDailyStreak).toBe(1);
  });

  it('челлендж дня за старую дату отклоняется', async () => {
    const { app } = buildApp();
    const wallet = createWallet();
    const oldDay = TODAY - 30;
    const proof = dailyProof(oldDay);

    const start = await post(app, '/daily/start', {
      address: wallet.address,
      engine: '0.1.0',
      day: oldDay,
      moves: proof.moves,
    });
    const submit = await post(app, '/daily/submit', {
      address: wallet.address,
      nonce: start.json.nonce,
      signature: wallet.sign(start.json.message as string),
      proof,
    });
    expect(submit.status).toBe(422);
  });
});

describe('метаданные наград', () => {
  it('отдаёт каталог, описание и картинку', async () => {
    const { app } = buildApp();

    const catalog = (await (await app.request('/rewards')).json()) as { rewards: unknown[] };
    expect(catalog.rewards.length).toBeGreaterThan(3);

    const metadata = (await (await app.request('/metadata/plomba-first')).json()) as Record<string, unknown>;
    expect(metadata.name).toBeTruthy();
    expect(String(metadata.description)).toContain('Цикл');

    const svg = await app.request('/nft/plomba-first.svg');
    expect(svg.status).toBe(200);
    expect(svg.headers.get('content-type')).toContain('image/svg+xml');
  });
});

describe('контент: магазин, реестр, названия кварталов', () => {
  it('отдаёт каталог косметики и карточку предмета', async () => {
    const { app } = buildApp();

    const shop = await app.request('/shop');
    expect(shop.status).toBe(200);
    const catalog = (await shop.json()) as {
      items: Array<{ id: string; channel: string; priceUsd: number | null; lore: string }>;
    };
    expect(catalog.items.length).toBeGreaterThanOrEqual(18);

    const rewards = catalog.items.filter((i) => i.channel === 'reward');
    expect(rewards.length).toBeGreaterThanOrEqual(6);
    expect(rewards.every((i) => i.priceUsd === null)).toBe(true);
    expect(catalog.items.every((i) => i.lore.length > 20)).toBe(true);

    const one = await app.request('/shop/source-core');
    expect(one.status).toBe(200);
    expect(((await one.json()) as { name: string }).name).toContain('Ядро');
    expect((await app.request('/shop/нет-такого')).status).toBe(404);
  });

  it('отдаёт реестр сразу на двух языках: все 60 записей переведены', async () => {
    const { app } = buildApp();
    const list = (await (await app.request('/registry')).json()) as {
      count: number;
      entries: Array<{ id: string; titleEn: string; sourceEn: string; speakerEn: string }>;
    };
    expect(list.count).toBe(60);
    expect(list.entries.length).toBe(60);

    const cyrillic = /[А-Яа-яЁё]/;
    for (const entry of list.entries) {
      expect(entry.titleEn?.length, `${entry.id}: нет EN-названия`).toBeGreaterThan(3);
      expect(entry.sourceEn?.length, `${entry.id}: нет EN-источника`).toBeGreaterThan(3);
      for (const value of [entry.titleEn, entry.sourceEn, entry.speakerEn]) {
        expect(cyrillic.test(value), `${entry.id}: «${value}» не переведено`).toBe(false);
      }
    }

    const one = await app.request('/registry/reg-s2-05');
    expect(one.status).toBe(200);
    const entry = (await one.json()) as { title: string; titleEn: string; textEn: string; speakerEn: string };
    expect(entry.title.length).toBeGreaterThan(3);
    expect(entry.titleEn.length).toBeGreaterThan(3);
    expect(entry.textEn.length).toBeGreaterThan(30);
    expect(entry.speakerEn).toBe('Marta Volt');
  });

  it('отдаёт английскую карточку предмета вместе с русской', async () => {
    const { app } = buildApp();
    const catalog = (await (await app.request('/shop')).json()) as {
      items: Array<{ id: string; nameEn: string | null; howEn: string | null; loreEn: string | null }>;
    };

    const cyrillic = /[А-Яа-яЁё]/;
    for (const item of catalog.items) {
      expect(item.nameEn, `${item.id}: нет английского названия`).toBeTruthy();
      expect(item.howEn, `${item.id}: нет английского способа получения`).toBeTruthy();
      expect(item.loreEn, `${item.id}: нет английского лора`).toBeTruthy();
      for (const value of [item.nameEn!, item.howEn!, item.loreEn!]) {
        expect(cyrillic.test(value), `${item.id}: «${value}» не переведено`).toBe(false);
      }
    }
  });

  it('отдаёт реестр и полный текст записи', async () => {
    const { app } = buildApp();

    const registry = await app.request('/registry');
    expect(registry.status).toBe(200);
    const data = (await registry.json()) as { count: number; entries: Array<{ id: string }> };
    expect(data.count).toBe(60);
    expect(data.entries[0]?.id).toBe('reg-s1-01');

    const entry = await app.request('/registry/reg-s3-20');
    expect(entry.status).toBe(200);
    expect(((await entry.json()) as { text: string }).text.length).toBeGreaterThan(30);
    expect((await app.request('/registry/нет-такого')).status).toBe(404);
  });

  it('называет квартал и ярус уровня', async () => {
    const { app } = buildApp();

    const res = await app.request('/levels/16');
    expect(res.status).toBe(200);
    const data = (await res.json()) as {
      quarter: { code: string; name: string; nameEn: string };
      tier: { id: string };
    };
    expect(data.quarter.code).toBe('Р-01');
    expect(data.quarter.name).toBe('Живой металл');
    expect(data.quarter.nameEn).toBe('Living Metal');
    expect(data.tier.id).toBe('mercury');

    expect((await app.request('/levels/0')).status).toBe(400);
    expect((await app.request('/levels/76')).status).toBe(400);
    expect((await app.request('/levels/абв')).status).toBe(400);
  });
});
