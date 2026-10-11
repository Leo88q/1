/**
 * Тестовые помощники: «кошелёк» и честная последовательность ходов,
 * которую можно отправить на сервер как доказательство решения.
 */
import nacl from 'tweetnacl';
import bs58 from 'bs58';
import {
  DAILY_CONFIG_LEVEL,
  applyRotate,
  dailySeed,
  generateLevel,
  isSolved,
  levelSeed,
  type Level,
} from '@ampergrad/engine';
import type { PlayProof } from '../src/verify';

export interface TestWallet {
  address: string;
  secretKey: Uint8Array;
  sign: (message: string) => string;
}

export function createWallet(): TestWallet {
  const keyPair = nacl.sign.keyPair();
  return {
    address: bs58.encode(keyPair.publicKey),
    secretKey: keyPair.secretKey,
    sign(message: string): string {
      const signature = nacl.sign.detached(new TextEncoder().encode(message), keyPair.secretKey);
      return bs58.encode(signature);
    },
  };
}

/**
 * Строит последовательность ходов, приводящую квартал к пломбе:
 * каждый фрагмент доводится до сгенерированного решения по часовой стрелке.
 */
export function solveLevel(level: Level): Array<[number, number]> {
  const turns: Array<[number, number]> = [];
  for (let i = 0; i < level.tiles.length; i++) {
    const tile = level.tiles[i];
    if (tile.fixed) continue;
    const steps = ((level.solutionRot[i] - tile.rot) % 4 + 4) % 4;
    for (let s = 0; s < steps; s++) turns.push([i, 1]);
  }
  // Проверка: ходы действительно собирают квартал (иначе тест бессмысленный).
  const replay = generateLevel(level.level === 0 ? DAILY_CONFIG_LEVEL : level.level, level.seed);
  if (level.level === 0) replay.level = 0;
  for (const [index, dir] of turns) applyRotate(replay, index, dir);
  if (!isSolved(replay)) throw new Error('помощник собрал некорректную последовательность ходов');
  return turns;
}

export function levelProof(levelNumber: number): PlayProof {
  const level = generateLevel(levelNumber);
  const turns = solveLevel(level);
  return { mode: 'level', level: levelNumber, turns, moves: turns.length, engine: '0.1.0' };
}

export function dailyProof(day: number): PlayProof {
  const level = generateLevel(DAILY_CONFIG_LEVEL, dailySeed(day));
  level.level = 0;
  const turns = solveLevel(level);
  return { mode: 'daily', day, turns, moves: turns.length, engine: '0.1.0' };
}

export function seedOfLevel(levelNumber: number): number {
  return levelSeed(levelNumber);
}
