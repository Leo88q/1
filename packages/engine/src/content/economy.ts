/**
 * Экономика монет Амперграда.
 *
 * Монеты — игровая валюта за игру, не покупная: их нельзя купить за деньги,
 * только заработать, и потратить можно лишь на внешний вид (см. docs/04 §2).
 * Никакой связи с призовым фондом и никаких покупных преимуществ: формулы живут
 * в каноне и попадают в клиент через `cosmetics.json` (`npm run content`).
 */

export interface EconomyRules {
  /** Базовая награда за собранный квартал. */
  coinsPerSolve: number;
  /** Добавка за каждую звезду. */
  coinsPerStar: number;
  /** Добавка за челлендж дня (раз в день, поверх базовой). */
  dailyBonus: number;
}

export const ECONOMY: EconomyRules = {
  coinsPerSolve: 20,
  coinsPerStar: 10,
  dailyBonus: 20,
};

/** Монеты за решение: базовые плюс по звезде. 3★ → 50, 1★ → 30. */
export function coinsForSolve(stars: number, daily = false): number {
  const clean = Math.max(0, Math.min(3, Math.floor(stars)));
  return ECONOMY.coinsPerSolve + ECONOMY.coinsPerStar * clean + (daily ? ECONOMY.dailyBonus : 0);
}

/** Хватает ли монет на предмет (цены за монеты лежат в каталоге). */
export function canAfford(coins: number, priceCoins: number | null): boolean {
  return priceCoins !== null && coins >= priceCoins;
}

/**
 * Правило витрины: за монеты продаётся только внешний вид, и только он.
 * Награды за игру не покупаются, товары витрины — не за монеты, Battle Pass — не за монеты.
 */
export function coinPurchasable(channel: string): boolean {
  return channel === 'coins';
}
