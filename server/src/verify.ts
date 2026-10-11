/**
 * Проверка подписи кошелька (Ed25519) и replay-proof решения квартала.
 *
 * Ключевой принцип: подпись доказывает владение кошельком, а честность игры
 * доказывает ПОВТОРНОЕ ПРОИГРЫВАНИЕ ходов движком на сервере. Клиент присылает
 * seed и последовательность поворотов — сервер проигрывает их тем же ядром.
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

export function decodeAddress(address: string): Uint8Array | null {
  try {
    const bytes = bs58.decode(address);
    return bytes.length === 32 ? bytes : null;
  } catch {
    return null;
  }
}

/** Проверка detached-подписи сообщения (то, что подписывает кошелёк в приложении). */
export function verifySignature(address: string, message: string, signatureBase58: string): boolean {
  const publicKey = decodeAddress(address);
  if (!publicKey) return false;
  let signature: Uint8Array;
  try {
    signature = bs58.decode(signatureBase58);
  } catch {
    return false;
  }
  if (signature.length !== nacl.sign.signatureLength) return false;
  const messageBytes = new TextEncoder().encode(message);
  return nacl.sign.detached.verify(messageBytes, signature, publicKey);
}

export interface PlayProof {
  mode: 'level' | 'daily';
  /** Номер уровня для mode='level'. */
  level?: number;
  /** Unix-день для mode='daily'. */
  day?: number;
  /** Последовательность поворотов: [индекс ячейки, направление (+1/-1)]. */
  turns: Array<[number, number]>;
  /** Заявленное число ходов (должно совпадать с длиной turns). */
  moves: number;
  /** Версия правил, с которой играл клиент. */
  engine: string;
}

export interface ProofResult {
  ok: boolean;
  reason?: string;
  level?: Level;
  /** Допустимое окно по времени: daily проверяется только за «сегодня±1». */
  day?: number;
}

export interface ProofOptions {
  /** Unix-день сервера («сейчас»). */
  today: number;
  /** Ожидаемая версия правил (ENGINE_VERSION). */
  engineVersion: string;
  /** Разрешить произвольные дни (для тестов и повторной проверки истории). */
  allowAnyDay?: boolean;
}

/** Проверяет, что присланные ходы действительно собирают квартал. */
export function verifyPlayProof(proof: PlayProof, options: ProofOptions): ProofResult {
  if (proof.engine !== options.engineVersion) {
    return { ok: false, reason: `версия правил не совпадает: ${proof.engine} != ${options.engineVersion}` };
  }
  if (!Array.isArray(proof.turns) || proof.turns.length === 0) {
    return { ok: false, reason: 'пустая последовательность ходов' };
  }
  if (proof.moves !== proof.turns.length) {
    return { ok: false, reason: 'число ходов не совпадает с последовательностью' };
  }

  let seed: number;
  let configLevel: number;
  let day: number | undefined;

  if (proof.mode === 'daily') {
    if (typeof proof.day !== 'number' || !Number.isFinite(proof.day)) {
      return { ok: false, reason: 'не указан день челленджа' };
    }
    day = proof.day;
    if (!options.allowAnyDay && Math.abs(day - options.today) > 1) {
      return { ok: false, reason: 'челлендж дня принимается только в течение суток' };
    }
    seed = dailySeed(day);
    configLevel = DAILY_CONFIG_LEVEL;
  } else {
    if (typeof proof.level !== 'number' || proof.level < 1 || proof.level > 75) {
      return { ok: false, reason: 'некорректный номер уровня' };
    }
    seed = levelSeed(proof.level);
    configLevel = proof.level;
  }

  const level = generateLevel(configLevel, seed);
  if (proof.mode === 'daily') level.level = 0;

  for (const turn of proof.turns) {
    if (!Array.isArray(turn) || turn.length !== 2) return { ok: false, reason: 'повреждённая запись хода' };
    const [index, dir] = turn;
    const tile = level.tiles[index];
    if (!tile) return { ok: false, reason: `ход за пределами квартала: ${index}` };
    if (tile.fixed) return { ok: false, reason: `поворот неподвижного фрагмента: ${index}` };
    if (dir !== 1 && dir !== -1) return { ok: false, reason: `некорректное направление: ${dir}` };
    applyRotate(level, index, dir);
  }

  if (!isSolved(level)) {
    return { ok: false, reason: 'присланные ходы не приводят к пломбе' };
  }

  return { ok: true, level, day };
}

/** Проверка решения «на решение» — для тестов и отладки (без ходов). */
export function levelSolvedAtConfig(levelNumber: number): boolean {
  const level = generateLevel(levelNumber);
  return isSolved(level);
}
