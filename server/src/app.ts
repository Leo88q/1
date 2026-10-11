/**
 * HTTP-приложение Амперграда: challenge → подпись → проверка доказательства → минт.
 *
 * Модель безопасности:
 *  • ключ минта живёт только на сервере (KMS/секреты);
 *  • игрок подписывает сообщение (proof of ownership) — без транзакций за SOL;
 *  • честность решения доказывается реплеем ходов тем же ядром (verify.ts);
 *  • nonce одноразовый и с TTL, лимиты на кошелёк и на день.
 */
import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { ENGINE_VERSION } from '@ampergrad/engine';
import type { ServerEnv } from './env';
import { buildMetadata, rewardSvg, type RewardMetadata } from './metadata';
import { Minter } from './mint';
import { REWARDS, evaluate, rewardById, type ClaimCounters } from './rewards';
import { claimMessage, dailyMessage, randomNonce } from './messages';
import type { Store } from './store';
import { decodeAddress, verifyPlayProof, verifySignature, type PlayProof } from './verify';

export interface AppDeps {
  env: ServerEnv;
  store: Store;
  minter: Minter;
  /** Текущее время (мс) — подменяется в тестах. */
  now?: () => number;
}

interface ClaimStartBody {
  address?: string;
  engine?: string;
  rewardId?: string;
  /**
   * Доказательство решения можно прислать сразу со стартом: тогда сервер
   * сначала засчитает подтверждённый прогресс, а потом подберёт награду.
   * Иначе игрок не смог бы забрать первую пломбу (правило требует ≥1 решения).
   */
  proof?: PlayProof;
}

interface ClaimSubmitBody {
  address?: string;
  nonce?: string;
  signature?: string;
  rewardId?: string;
  proof?: PlayProof;
}

interface DailyStartBody {
  address?: string;
  engine?: string;
  day?: number;
  moves?: number;
}

interface DailySubmitBody {
  address?: string;
  nonce?: string;
  signature?: string;
  proof?: PlayProof;
}

const SHORT = (address: string): string => `${address.slice(0, 4)}…${address.slice(-4)}`;

export function createApp(deps: AppDeps): Hono {
  const { env, store, minter } = deps;
  const now = deps.now ?? (() => Date.now());
  const today = (): number => Math.floor(now() / 86_400_000);

  const app = new Hono();
  app.use('*', cors({ origin: '*' }));

  const countersFor = async (address: string, rewardId: string): Promise<ClaimCounters> => {
    const [total, byReward, todayCount] = await Promise.all([
      store.countClaims(address),
      store.countClaimsByReward(address, rewardId),
      store.countClaimsToday(address, today()),
    ]);
    return { total, byReward, today: todayCount };
  };

  app.get('/health', async (c) =>
    c.json({
      ok: true,
      engine: ENGINE_VERSION,
      mintMode: minter.mode,
      tree: env.merkleTree !== null,
      unverifiedClaimsAllowed: env.allowUnverifiedClaims,
      rewards: REWARDS.length,
      minted: await store.totalMinted(),
      time: new Date(now()).toISOString(),
    }),
  );

  app.get('/rewards', (c) =>
    c.json({
      engine: ENGINE_VERSION,
      rewards: REWARDS.map((r) => ({
        id: r.id,
        name: r.name,
        description: r.description,
        kind: r.kind,
        accent: r.accent,
      })),
    }),
  );

  app.get('/rewards/:rewardId', (c) => {
    const reward = rewardById(c.req.param('rewardId'));
    if (!reward) return c.json({ error: 'награда не найдена' }, 404);
    return c.json({ id: reward.id, name: reward.name, description: reward.description, lore: reward.lore });
  });

  app.get('/metadata/:rewardId', (c) => {
    const reward = rewardById(c.req.param('rewardId'));
    if (!reward) return c.json({ error: 'награда не найдена' }, 404);
    const address = c.req.query('address') ?? 'unknown';
    const metadata: RewardMetadata = buildMetadata({
      reward,
      address,
      engineVersion: ENGINE_VERSION,
      baseUrl: env.publicBaseUrl,
      creatorAddress: null,
    });
    return c.json(metadata);
  });

  app.get('/nft/:file', (c) => {
    const file = c.req.param('file');
    const id = file.replace(/\.svg$/i, '');
    const reward = rewardById(id);
    if (!reward) return c.text('не найдено', 404);
    return c.body(rewardSvg(reward), 200, { 'Content-Type': 'image/svg+xml; charset=utf-8' });
  });

  /* ────────────────────────── Клейм награды ────────────────────────── */

  app.post('/claim/start', async (c) => {
    const body = await c.req.json<ClaimStartBody>().catch(() => null);
    if (!body?.address || !decodeAddress(body.address)) {
      return c.json({ error: 'некорректный адрес кошелька' }, 400);
    }
    const engine = body.engine ?? ENGINE_VERSION;
    if (engine !== ENGINE_VERSION) {
      return c.json({ error: `версия правил не поддерживается: ${engine}`, engine: ENGINE_VERSION }, 409);
    }

    const address = body.address;

    // Доказательство решения (если прислано) засчитывается до подбора награды.
    if (body.proof) {
      const proofResult = verifyPlayProof(body.proof, {
        today: today(),
        engineVersion: ENGINE_VERSION,
        allowAnyDay: true,
      });
      if (!proofResult.ok) return c.json({ error: proofResult.reason }, 422);
      if (body.proof.mode === 'daily') {
        await store.recordVerifiedDaily(address, proofResult.day!, body.proof.moves);
        await store.saveDailyScore({ day: proofResult.day!, address, moves: body.proof.moves, at: now() });
      } else {
        await store.recordVerifiedSolve(address, body.proof.level!);
      }
    }

    const progress = await store.getProgress(address);
    const relaxed = !body.proof && env.allowUnverifiedClaims;

    // Выбор награды: запрошенная клиентом или первая доступная по правилам.
    const candidates = body.rewardId ? [rewardById(body.rewardId)].filter((r) => r !== undefined) : REWARDS;
    if (body.rewardId && candidates.length === 0) {
      return c.json({ error: 'награда не найдена' }, 404);
    }

    // Лимиты проверяем сразу на старте: не выдаём задачу, которую всё равно отклоним.
    const totals = await countersFor(address, candidates[0]?.id ?? 'none');
    if (totals.total >= env.maxClaimsPerWallet) {
      return c.json({ error: 'достигнут лимит наград на кошелёк' }, 429);
    }
    if (totals.today >= env.maxClaimsPerWalletPerDay) {
      return c.json({ error: 'достигнут дневной лимит наград' }, 429);
    }

    let reason = 'нет доступных наград';
    for (const reward of candidates) {
      const counters = await countersFor(address, reward.id);
      const verdict = relaxed
        ? counters.byReward === 0
          ? { ok: true }
          : { ok: false, reason: 'эта награда уже в реестре' }
        : evaluate(reward, progress, counters);
      if (!verdict.ok) {
        reason = verdict.reason ?? reason;
        continue;
      }
      const nonce = randomNonce();
      const message = claimMessage({ address, rewardId: reward.id, nonce, issuedAt: new Date(now()) });
      await store.createChallenge({
        nonce,
        address,
        message,
        rewardId: reward.id,
        engine,
        issuedAt: now(),
        usedAt: null,
      });
      return c.json({
        nonce,
        message,
        rewardId: reward.id,
        rewardName: reward.name,
        engine: ENGINE_VERSION,
        expiresInMs: env.challengeTtlMs,
        progress: {
          solvedLevels: progress.solvedLevels.size,
          dailyDays: progress.dailyDays.size,
          bestDailyStreak: progress.bestDailyStreak,
        },
      });
    }

    return c.json({ error: reason, engine: ENGINE_VERSION }, 409);
  });

  app.post('/claim/submit', async (c) => {
    const body = await c.req.json<ClaimSubmitBody>().catch(() => null);
    if (!body?.address || !body.nonce || !body.signature || !decodeAddress(body.address)) {
      return c.json({ error: 'нужны address, nonce и signature' }, 400);
    }

    const challenge = await store.takeChallenge(body.nonce, now(), env.challengeTtlMs);
    if (!challenge) return c.json({ status: 'rejected', reason: 'задача не найдена, истекла или уже использована' }, 410);
    if (challenge.address !== body.address) return c.json({ status: 'rejected', reason: 'задача выдана другому кошельку' }, 403);
    if (body.rewardId && body.rewardId !== challenge.rewardId) {
      return c.json({ status: 'rejected', reason: 'награда не совпадает с задачей' }, 400);
    }
    if (!verifySignature(challenge.address, challenge.message, body.signature)) {
      return c.json({ status: 'rejected', reason: 'подпись не соответствует кошельку' }, 401);
    }

    // Доказательство игры: сервер сам проигрывает ходы.
    if (body.proof) {
      const result = verifyPlayProof(body.proof, {
        today: today(),
        engineVersion: ENGINE_VERSION,
        allowAnyDay: env.allowUnverifiedClaims,
      });
      if (!result.ok) return c.json({ status: 'rejected', reason: result.reason }, 422);
      if (body.proof.mode === 'daily') {
        await store.recordVerifiedDaily(challenge.address, result.day!, body.proof.moves);
        await store.saveDailyScore({ day: result.day!, address: challenge.address, moves: body.proof.moves, at: now() });
      } else {
        await store.recordVerifiedSolve(challenge.address, body.proof.level!);
      }
    } else if (!env.allowUnverifiedClaims) {
      return c.json({ status: 'rejected', reason: 'нужно доказательство решения (proof)' }, 422);
    }

    const reward = rewardById(challenge.rewardId);
    if (!reward) return c.json({ status: 'rejected', reason: 'награда не найдена' }, 404);

    const counters = await countersFor(challenge.address, reward.id);
    if (counters.total >= env.maxClaimsPerWallet) {
      return c.json({ status: 'rejected', reason: 'достигнут лимит наград на кошелёк' }, 429);
    }
    if (counters.today >= env.maxClaimsPerWalletPerDay) {
      return c.json({ status: 'rejected', reason: 'достигнут дневной лимит наград' }, 429);
    }

    const progress = await store.getProgress(challenge.address);
    const relaxed = !body.proof && env.allowUnverifiedClaims;
    if (relaxed) {
      if (counters.byReward > 0) return c.json({ status: 'rejected', reason: 'эта награда уже в реестре' }, 409);
    } else {
      const verdict = evaluate(reward, progress, counters);
      if (!verdict.ok) return c.json({ status: 'rejected', reason: verdict.reason }, 409);
    }

    const uri = env.publicBaseUrl
      ? `${env.publicBaseUrl}/metadata/${reward.id}?address=${challenge.address}`
      : `data:application/json;base64,${Buffer.from(
          JSON.stringify(
            buildMetadata({
              reward,
              address: challenge.address,
              engineVersion: ENGINE_VERSION,
              baseUrl: env.publicBaseUrl,
              creatorAddress: null,
            }),
          ),
        ).toString('base64')}`;

    const mint = await minter.mint({
      leafOwner: challenge.address,
      name: reward.name,
      uri,
      collectionName: 'Амперград · Реестр',
    });

    await store.recordClaim({
      address: challenge.address,
      rewardId: reward.id,
      day: today(),
      assetId: mint.assetId,
      signature: mint.signature,
      at: now(),
    });

    return c.json({
      status: mint.status,
      rewardId: reward.id,
      rewardName: reward.name,
      assetId: mint.assetId,
      txSignature: mint.signature,
      mode: mint.mode,
      error: mint.error ?? null,
      wallet: SHORT(challenge.address),
    });
  });

  /* ────────────────────────── Челлендж дня ────────────────────────── */

  app.post('/daily/start', async (c) => {
    const body = await c.req.json<DailyStartBody>().catch(() => null);
    if (!body?.address || !decodeAddress(body.address)) return c.json({ error: 'некорректный адрес кошелька' }, 400);
    const engine = body.engine ?? ENGINE_VERSION;
    if (engine !== ENGINE_VERSION) return c.json({ error: `версия правил не поддерживается: ${engine}` }, 409);
    if (typeof body.day !== 'number' || typeof body.moves !== 'number') {
      return c.json({ error: 'нужны day и moves' }, 400);
    }

    const nonce = randomNonce();
    const message = dailyMessage({ address: body.address, day: body.day, moves: body.moves, nonce });
    await store.createChallenge({
      nonce,
      address: body.address,
      message,
      rewardId: 'daily-score',
      engine,
      issuedAt: now(),
      usedAt: null,
    });
    return c.json({ nonce, message, expiresInMs: env.challengeTtlMs, day: body.day, moves: body.moves });
  });

  app.post('/daily/submit', async (c) => {
    const body = await c.req.json<DailySubmitBody>().catch(() => null);
    if (!body?.address || !body.nonce || !body.signature || !body.proof) {
      return c.json({ error: 'нужны address, nonce, signature и proof' }, 400);
    }
    const challenge = await store.takeChallenge(body.nonce, now(), env.challengeTtlMs);
    if (!challenge) return c.json({ status: 'rejected', reason: 'задача не найдена, истекла или уже использована' }, 410);
    if (challenge.address !== body.address) return c.json({ status: 'rejected', reason: 'задача выдана другому кошельку' }, 403);
    if (!verifySignature(challenge.address, challenge.message, body.signature)) {
      return c.json({ status: 'rejected', reason: 'подпись не соответствует кошельку' }, 401);
    }

    const result = verifyPlayProof(body.proof, {
      today: today(),
      engineVersion: ENGINE_VERSION,
      allowAnyDay: false,
    });
    if (!result.ok || body.proof.mode !== 'daily') {
      return c.json({ status: 'rejected', reason: result.reason ?? 'это не челлендж дня' }, 422);
    }

    await store.recordVerifiedDaily(challenge.address, result.day!, body.proof.moves);
    await store.saveDailyScore({ day: result.day!, address: challenge.address, moves: body.proof.moves, at: now() });

    const board = await store.dailyLeaderboard(result.day!, 500);
    const rank = board.findIndex((s) => s.address === challenge.address) + 1;

    return c.json({
      status: 'accepted',
      day: result.day,
      moves: body.proof.moves,
      rank: rank > 0 ? rank : null,
      leaderboard: board.slice(0, 10).map((s, i) => ({ rank: i + 1, wallet: SHORT(s.address), moves: s.moves })),
    });
  });

  app.get('/daily/leaderboard', async (c) => {
    const dayParam = c.req.query('day');
    const day = dayParam ? Number(dayParam) : today();
    const limit = Math.min(Number(c.req.query('limit') ?? 10) || 10, 100);
    const board = await store.dailyLeaderboard(day, limit);
    return c.json({
      day,
      engine: ENGINE_VERSION,
      entries: board.map((s, i) => ({ rank: i + 1, wallet: SHORT(s.address), moves: s.moves })),
    });
  });

  return app;
}
