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

/**
 * Понятное объяснение вместо стектрейса, когда порт занят.
 *
 * Частая картина при первом запуске: сервер уже поднят в другом окне терминала
 * (или остался от прошлого раза). Тогда достаточно ответить на /health — и не
 * запускать второй, либо освободить порт, либо взять другой.
 */
async function explainPortBusy(port: number): Promise<void> {
  console.error(`\nПорт ${port} уже занят — сервер не запустился.\n`);

  try {
    const response = await fetch(`http://127.0.0.1:${port}/health`, {
      signal: AbortSignal.timeout(1500),
    });
    const body = (await response.json()) as { ok?: boolean; engine?: string };
    if (body.ok) {
      console.error('  Разбираться не нужно: на этом порту уже работает сервер Амперграда');
      console.error(`  (правила ${body.engine ?? '?'}). Просто пользуйтесь им:`);
      console.error(`    curl -s localhost:${port}/health`);
      console.error('  Второй экземпляр нужен только для другого порта — см. ниже.\n');
    } else {
      console.error('  Порт занят другой программой (ответ на /health не наш).\n');
    }
  } catch {
    console.error('  Порт занят другой программой (на /health никто не отвечает).\n');
  }

  console.error('  Кто держит порт:');
  console.error(`    lsof -nP -iTCP:${port} -sTCP:LISTEN`);
  console.error('  Освободить (номер процесса — из вывода выше):');
  console.error('    kill <PID>        # или kill -9 <PID>, если не отдаёт сам');
  console.error('  Или запустить на другом порту:');
  console.error(`    PORT=${port + 1} npm run server`);
}

async function main(): Promise<void> {
  const env = loadEnv();
  const store: Store = env.databaseUrl ? new PgStore(env.databaseUrl) : new MemoryStore();
  const minter = new Minter(env);
  const app = createApp({ env, store, minter });

  const server = serve({ fetch: app.fetch, port: env.port, hostname: '0.0.0.0' }, (info) => {
    console.log(`АМПЕРГРАД · сервер клейма слушает http://0.0.0.0:${info.port}`);
    console.log(`  правила: ${ENGINE_VERSION}`);
    console.log(`  хранилище: ${env.databaseUrl ? 'postgres' : 'память (только разработка)'}`);
    console.log(`  минт: ${env.mintMode}${env.merkleTree ? '' : ' (Merkle-дерево не задано)'}`);
    console.log(`  клейм без доказательства: ${env.allowUnverifiedClaims ? 'да (только для отладки!)' : 'нет'}`);
  });

  server.on('error', (error: NodeJS.ErrnoException) => {
    if (error.code === 'EADDRINUSE') {
      void explainPortBusy(env.port).then(() => process.exit(1));
      return;
    }
    console.error('Сервер остановлен из-за ошибки:', error);
    process.exit(1);
  });
}

main().catch((error) => {
  console.error('Не удалось запустить сервер:', error);
  process.exit(1);
});
