/**
 * Точка входа сервера.
 *
 * Переменные окружения — см. server/.env.example.
 * Без DATABASE_URL поднимается MemoryStore (только для разработки: данные теряются при перезапуске).
 * Без AMPERGRAD_MINT_MODE=devnet|mainnet минт работает в режиме dry-run: награда «в очереди»,
 * приложение показывает честный статус, ничего не тратится.
 */
import { serve } from '@hono/node-server';
import { ENGINE_VERSION } from '@ampergrad/engine';
import { createApp } from './app';
import { loadEnv } from './env';
import { Minter } from './mint';
import { MemoryStore, type Store } from './store';
import { PgStore } from './store-pg';

async function main(): Promise<void> {
  const env = loadEnv();
  const store: Store = env.databaseUrl ? new PgStore(env.databaseUrl) : new MemoryStore();
  const minter = new Minter(env);
  const app = createApp({ env, store, minter });

  serve({ fetch: app.fetch, port: env.port, hostname: '0.0.0.0' }, (info) => {
    console.log(`АМПЕРГРАД · сервер клейма слушает http://0.0.0.0:${info.port}`);
    console.log(`  правила: ${ENGINE_VERSION}`);
    console.log(`  хранилище: ${env.databaseUrl ? 'postgres' : 'память (только разработка)'}`);
    console.log(`  минт: ${env.mintMode}${env.merkleTree ? '' : ' (Merkle-дерево не задано)'}`);
    console.log(`  клейм без доказательства: ${env.allowUnverifiedClaims ? 'да (только для отладки!)' : 'нет'}`);
  });
}

main().catch((error) => {
  console.error('Не удалось запустить сервер:', error);
  process.exit(1);
});
