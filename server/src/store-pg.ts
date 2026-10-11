/**
 * Postgres-хранилище (schema.sql). Используется, когда задан DATABASE_URL.
 * Интерфейс совпадает с MemoryStore — код правил от хранилища не зависит.
 */
import { Pool } from 'pg';
import {
  emptyProgress,
  streakOf,
  type Challenge,
  type ClaimRecord,
  type DailyScore,
  type Store,
  type WalletProgress,
} from './store';

export class PgStore implements Store {
  private readonly pool: Pool;

  constructor(connectionString: string) {
    this.pool = new Pool({ connectionString, max: 8 });
  }

  async close(): Promise<void> {
    await this.pool.end();
  }

  async createChallenge(challenge: Challenge): Promise<void> {
    await this.pool.query(
      `insert into challenges (nonce, address, message, reward_id, engine, issued_at, used_at)
       values ($1, $2, $3, $4, $5, to_timestamp($6 / 1000.0), null)`,
      [challenge.nonce, challenge.address, challenge.message, challenge.rewardId, challenge.engine, challenge.issuedAt],
    );
  }

  async takeChallenge(nonce: string, now: number, ttlMs: number): Promise<Challenge | null> {
    const result = await this.pool.query(
      `update challenges
          set used_at = to_timestamp($2 / 1000.0)
        where nonce = $1
          and used_at is null
          and issued_at > to_timestamp($2 / 1000.0) - ($3::bigint * interval '1 millisecond')
      returning nonce, address, message, reward_id, engine, issued_at`,
      [nonce, now, ttlMs],
    );
    const row = result.rows[0];
    if (!row) return null;
    return {
      nonce: row.nonce,
      address: row.address,
      message: row.message,
      rewardId: row.reward_id,
      engine: row.engine,
      issuedAt: new Date(row.issued_at).getTime(),
      usedAt: now,
    };
  }

  async getProgress(address: string): Promise<WalletProgress> {
    const [levels, dailies] = await Promise.all([
      this.pool.query('select level from wallet_solves where address = $1', [address]),
      this.pool.query('select day, moves from wallet_dailies where address = $1', [address]),
    ]);
    const progress = emptyProgress(address);
    for (const row of levels.rows) progress.solvedLevels.add(Number(row.level));
    for (const row of dailies.rows) {
      progress.dailyDays.add(Number(row.day));
      progress.dailyMoves.set(Number(row.day), Number(row.moves));
    }
    progress.bestDailyStreak = streakOf(progress.dailyDays);
    return progress;
  }

  async recordVerifiedSolve(address: string, level: number): Promise<void> {
    await this.pool.query(
      'insert into wallet_solves (address, level) values ($1, $2) on conflict do nothing',
      [address, level],
    );
  }

  async recordVerifiedDaily(address: string, day: number, moves: number): Promise<void> {
    await this.pool.query(
      `insert into wallet_dailies (address, day, moves) values ($1, $2, $3)
       on conflict (address, day) do update set moves = least(wallet_dailies.moves, excluded.moves)`,
      [address, day, moves],
    );
  }

  async recordClaim(record: ClaimRecord): Promise<void> {
    await this.pool.query(
      `insert into claims (address, reward_id, day, asset_id, signature)
       values ($1, $2, $3, $4, $5)`,
      [record.address, record.rewardId, record.day, record.assetId, record.signature],
    );
  }

  async countClaims(address: string): Promise<number> {
    const result = await this.pool.query('select count(*)::int as count from claims where address = $1', [address]);
    return result.rows[0]?.count ?? 0;
  }

  async countClaimsByReward(address: string, rewardId: string): Promise<number> {
    const result = await this.pool.query(
      'select count(*)::int as count from claims where address = $1 and reward_id = $2',
      [address, rewardId],
    );
    return result.rows[0]?.count ?? 0;
  }

  async countClaimsToday(address: string, day: number): Promise<number> {
    const result = await this.pool.query(
      'select count(*)::int as count from claims where address = $1 and day = $2',
      [address, day],
    );
    return result.rows[0]?.count ?? 0;
  }

  async totalMinted(): Promise<number> {
    const result = await this.pool.query('select count(*)::int as count from claims where asset_id is not null');
    return result.rows[0]?.count ?? 0;
  }

  async saveDailyScore(score: DailyScore): Promise<void> {
    await this.pool.query(
      `insert into daily_scores (day, address, moves) values ($1, $2, $3)
       on conflict (day, address) do update set moves = least(daily_scores.moves, excluded.moves)`,
      [score.day, score.address, score.moves],
    );
  }

  async dailyLeaderboard(day: number, limit: number): Promise<DailyScore[]> {
    const result = await this.pool.query(
      'select day, address, moves, extract(epoch from at) * 1000 as at from daily_scores where day = $1 order by moves asc, at asc limit $2',
      [day, limit],
    );
    return result.rows.map((row) => ({
      day: Number(row.day),
      address: row.address,
      moves: Number(row.moves),
      at: Number(row.at),
    }));
  }
}
