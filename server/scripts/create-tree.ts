/**
 * Создание Merkle-дерева Bubblegum для наград.
 *
 * Запуск (из server/):
 *   AMPERGRAD_MINT_MODE=devnet AMPERGRAD_RPC_URL=... AMPERGRAD_PAYER_SECRET=... \
 *   npm run tree -- --max-depth=14 --canopy=11
 *
 * Для mainnet требуется явный флаг --yes-mainnet: покупка дерева — это реальные SOL.
 * Ориентир стоимости: дерево на 2^14 листьев с canopy 11 — заметно дешевле
 * миллионного (в публичных расчётах сообщества 1 млн листьев ≈ 8.5 SOL);
 * точную цену считайте на devnet перед мейннетом.
 */
import { loadEnv } from '../src/env';
import { Minter } from '../src/mint';

function arg(name: string, fallback: number): number {
  const found = process.argv.find((a) => a.startsWith(`--${name}=`));
  if (!found) return fallback;
  const value = Number(found.split('=')[1]);
  return Number.isFinite(value) ? value : fallback;
}

async function main(): Promise<void> {
  const env = loadEnv();

  if (!env.rpcUrl || !env.payerSecret) {
    console.error('Нужны AMPERGRAD_RPC_URL и AMPERGRAD_PAYER_SECRET (см. .env.example)');
    process.exit(2);
  }
  if (env.mintMode === 'mainnet' && !process.argv.includes('--yes-mainnet')) {
    console.error('Mainnet: покупка дерева тратит реальные SOL. Добавьте --yes-mainnet, если это осознанное решение.');
    process.exit(2);
  }

  const maxDepth = arg('max-depth', 14);
  const maxBufferSize = arg('buffer', 64);
  const canopyDepth = arg('canopy', 11);

  console.log(`Создаю дерево: глубина ${maxDepth} (≈ ${2 ** maxDepth} наград), буфер ${maxBufferSize}, canopy ${canopyDepth}`);
  console.log(`Режим: ${env.mintMode}, RPC: ${env.rpcUrl}`);

  const minter = new Minter(env);
  const address = await minter.createTree({ maxDepth, maxBufferSize, canopyDepth });

  console.log('Готово. Merkle-дерево наград:');
  console.log(address);
  console.log('Пропишите его в окружение: AMPERGRAD_MERKLE_TREE=' + address);
}

main().catch((error) => {
  console.error('Не удалось создать дерево:', error);
  process.exit(1);
});
