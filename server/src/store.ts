/**
 * Хранилище: челленджи (nonce), проверенный прогресс кошельков, клеймы, лидерборд.
 *
 * MemoryStore — для разработки и тестов; PgStore — для продакшена (schema.sql).
 * Логика правил живёт в rewards.ts/daily.ts и не зависит от реализации хранилища.
 */

export interface Challenge {
  nonce: string;
  address: string;
  message: string;
  rewardId: string;
  engine: string;
  issuedAt: number;
  usedAt: number | null;
}

export interface WalletProgress {
  address: string;
  /** Номера уровней (обычной смены), решения которых подтверждены сервером. */
  solvedLevels: Set<number>;
  /** Дни (unix-day), в которые решён челлендж дня. */
  dailyDays: Set<number>;
  /** Максимальная серия дней подряд с решённым daily. */
  bestDailyStreak: number;
  /** Лучшее число ходов в daily по дням. */
  dailyMoves: Map<number, number>;
}

export function emptyProgress(address: string): WalletProgress {
  return {
    address,
    solvedLevels: new Set(),
    dailyDays: new Set(),
    bestDailyStreak: 0,
    dailyMoves: new Map(),
  };
}

export interface ClaimRecord {
  address: string;
  rewardId: string;
  day: number;
  assetId: string | null;
  signature: string | null;
  at: number;
}

export interface DailyScore {
  day: number;
  address: string;
  moves: number;
  at: number;
}

export interface Store {
  createChallenge(challenge: Challenge): Promise<void>;
  /** Забирает челлендж, если он не использован и не истёк (иначе null). */
  takeChallenge(nonce: string, now: number, ttlMs: number): Promise<Challenge | null>;

  getProgress(address: string): Promise<WalletProgress>;
  recordVerifiedSolve(address: string, level: number): Promise<void>;
  recordVerifiedDaily(address: string, day: number, moves: number): Promise<void>;

  recordClaim(record: ClaimRecord): Promise<void>;
  countClaims(address: string): Promise<number>;
  countClaimsByReward(address: string, rewardId: string): Promise<number>;
  countClaimsToday(address: string, day: number): Promise<number>;
  totalMinted(): Promise<number>;

  saveDailyScore(score: DailyScore): Promise<void>;
  dailyLeaderboard(day: number, limit: number): Promise<DailyScore[]>;
}

/* ────────────────────────────── Память ────────────────────────────── */

export class MemoryStore implements Store {
  private challenges = new Map<string, Challenge>();
  private progress = new Map<string, WalletProgress>();
  private claims: ClaimRecord[] = [];
  private scores: DailyScore[] = [];

  async createChallenge(challenge: Challenge): Promise<void> {
    this.challenges.set(challenge.nonce, challenge);
  }

  async takeChallenge(nonce: string, now: number, ttlMs: number): Promise<Challenge | null> {
    const challenge = this.challenges.get(nonce);
    if (!challenge) return null;
    if (challenge.usedAt !== null) return null;
    if (now - challenge.issuedAt > ttlMs) return null;
    challenge.usedAt = now;
    return challenge;
  }

  async getProgress(address: string): Promise<WalletProgress> {
    const existing = this.progress.get(address);
    if (existing) return existing;
    const created = emptyProgress(address);
    this.progress.set(address, created);
    return created;
  }

  async recordVerifiedSolve(address: string, level: number): Promise<void> {
    const progress = await this.getProgress(address);
    progress.solvedLevels.add(level);
  }

  async recordVerifiedDaily(address: string, day: number, moves: number): Promise<void> {
    const progress = await this.getProgress(address);
    progress.dailyDays.add(day);
    const previous = progress.dailyMoves.get(day);
    if (previous === undefined || moves < previous) progress.dailyMoves.set(day, moves);
    progress.bestDailyStreak = Math.max(progress.bestDailyStreak, streakOf(progress.dailyDays));
  }

  async recordClaim(record: ClaimRecord): Promise<void> {
    this.claims.push(record);
  }

  async countClaims(address: string): Promise<number> {
    return this.claims.filter((c) => c.address === address).length;
  }

  async countClaimsByReward(address: string, rewardId: string): Promise<number> {
    return this.claims.filter((c) => c.address === address && c.rewardId === rewardId).length;
  }

  async countClaimsToday(address: string, day: number): Promise<number> {
    return this.claims.filter((c) => c.address === address && c.day === day).length;
  }

  async totalMinted(): Promise<number> {
    return this.claims.filter((c) => c.assetId !== null).length;
  }

  async saveDailyScore(score: DailyScore): Promise<void> {
    const existing = this.scores.find((s) => s.day === score.day && s.address === score.address);
    if (!existing) {
      this.scores.push(score);
    } else if (score.moves < existing.moves) {
      existing.moves = score.moves;
      existing.at = score.at;
    }
  }

  async dailyLeaderboard(day: number, limit: number): Promise<DailyScore[]> {
    return this.scores
      .filter((s) => s.day === day)
      .sort((a, b) => a.moves - b.moves || a.at - b.at)
      .slice(0, limit);
  }
}

export function streakOf(days: Set<number>): number {
  if (days.size === 0) return 0;
  const sorted = [...days].sort((a, b) => a - b);
  let best = 1;
  let current = 1;
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i] === sorted[i - 1] + 1) current++;
    else current = 1;
    if (current > best) best = current;
  }
  return best;
}
