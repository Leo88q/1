/**
 * Сквозная проверка потока клейма без сети и без блокчейна:
 *   доказательство решения → /claim/start → подпись кошелька → /claim/submit.
 *
 * Запуск (из server/): npm run simulate
 * Полезно, чтобы проверить сервер после изменений правил и понять формат запросов.
 */
import { createApp } from '../src/app';
import { loadEnv } from '../src/env';
import { Minter } from '../src/mint';
import { MemoryStore } from '../src/store';
import { createWallet, levelProof } from '../tests/helpers';

async function main(): Promise<void> {
  const env = loadEnv({ AMPERGRAD_MINT_MODE: 'dry-run', AMPERGRAD_ALLOW_UNVERIFIED: 'false' } as NodeJS.ProcessEnv);
  const store = new MemoryStore();
  const minter = new Minter(env);
  const now = Date.now();
  const app = createApp({ env, store, minter, now: () => now });

  const wallet = createWallet();
  console.log(`Кошелёк: ${wallet.address}`);

  const proof = levelProof(1);
  console.log(`Доказательство: уровень 1, ${proof.moves} ходов, версия правил ${proof.engine}`);

  const startResponse = await app.request('/claim/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ address: wallet.address, engine: proof.engine, proof }),
  });
  const start = (await startResponse.json()) as Record<string, unknown>;
  console.log(`\nPOST /claim/start → ${startResponse.status}`);
  console.log(`  награда: ${start.rewardId} (${start.rewardName})`);
  console.log(`  сообщение для подписи:\n${String(start.message).split('\n').map((l) => '    ' + l).join('\n')}`);

  const signature = wallet.sign(start.message as string);
  console.log(`  подпись: ${signature.slice(0, 24)}…`);

  const submitResponse = await app.request('/claim/submit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      address: wallet.address,
      nonce: start.nonce,
      signature,
      rewardId: start.rewardId,
      proof,
    }),
  });
  const submit = (await submitResponse.json()) as Record<string, unknown>;
  console.log(`\nPOST /claim/submit → ${submitResponse.status}`);
  console.log(`  статус: ${submit.status} (режим минта: ${submit.mode})`);
  console.log(`  запись в реестре: ${submit.assetId ?? '—'}`);

  const progress = await store.getProgress(wallet.address);
  console.log(`\nПодтверждённый прогресс кошелька: уровни ${[...progress.solvedLevels].join(', ') || '—'}`);

  const health = (await (await app.request('/health')).json()) as Record<string, unknown>;
  console.log(`\nВсего минтов (в этом процессе): ${health.minted}`);
}

main().catch((error) => {
  console.error('Симуляция не удалась:', error);
  process.exit(1);
});
