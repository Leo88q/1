/**
 * Конфигурация сервера: всё через переменные окружения.
 * Секретов в коде нет — приватный ключ минта приходит из окружения/KMS.
 */
export interface ServerEnv {
  port: number;
  /** Режим минта: dry-run (ничего не отправляем), devnet, mainnet. */
  mintMode: 'dry-run' | 'devnet' | 'mainnet';
  /** RPC-эндпоинт (Helius и т.п.) — нужен только для реального минта. */
  rpcUrl: string | null;
  /** Приватный ключ минта (массив байт в JSON) — только на сервере, в KMS. */
  payerSecret: number[] | null;
  /** Адрес Merkle-дерева Bubblegum для наград. */
  merkleTree: string | null;
  /** Публичный базовый URL (для метаданных наград). */
  publicBaseUrl: string | null;
  /** Строка подключения к Postgres; без неё используется память (только для разработки). */
  databaseUrl: string | null;
  /** Разрешить клейм без доказательства решения (только devnet/разработка!). */
  allowUnverifiedClaims: boolean;
  /** Сколько живёт выданный nonce, мс. */
  challengeTtlMs: number;
  /** Лимиты анти-спама. */
  maxClaimsPerWallet: number;
  maxClaimsPerWalletPerDay: number;
}

function bool(value: string | undefined, fallback = false): boolean {
  if (value === undefined) return fallback;
  return ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
}

function jsonArray(value: string | undefined): number[] | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed) && parsed.every((n) => typeof n === 'number')) return parsed as number[];
    return null;
  } catch {
    return null;
  }
}

export function loadEnv(source: NodeJS.ProcessEnv = process.env): ServerEnv {
  const mintModeRaw = (source.AMPERGRAD_MINT_MODE ?? 'dry-run').toLowerCase();
  const mintMode = (['dry-run', 'devnet', 'mainnet'].includes(mintModeRaw)
    ? mintModeRaw
    : 'dry-run') as ServerEnv['mintMode'];

  return {
    port: Number(source.PORT ?? 8787),
    mintMode,
    rpcUrl: source.AMPERGRAD_RPC_URL ?? null,
    payerSecret: jsonArray(source.AMPERGRAD_PAYER_SECRET),
    merkleTree: source.AMPERGRAD_MERKLE_TREE ?? null,
    publicBaseUrl: source.AMPERGRAD_PUBLIC_URL ?? null,
    databaseUrl: source.DATABASE_URL ?? null,
    allowUnverifiedClaims: bool(source.AMPERGRAD_ALLOW_UNVERIFIED, mintMode === 'dry-run'),
    challengeTtlMs: Number(source.AMPERGRAD_CHALLENGE_TTL_MS ?? 5 * 60 * 1000),
    maxClaimsPerWallet: Number(source.AMPERGRAD_MAX_CLAIMS ?? 8),
    maxClaimsPerWalletPerDay: Number(source.AMPERGRAD_MAX_CLAIMS_PER_DAY ?? 3),
  };
}
