/**
 * Минт наград.
 *
 * Режимы (AMPERGRAD_MINT_MODE):
 *   dry-run — ничего не отправляется в сеть (разработка, тесты, демо);
 *   devnet  — реальный минт в devnet (проверка потока до мейннета);
 *   mainnet — продакшен.
 *
 * Приватный ключ минта НИКОГДА не попадает в приложение: только сервер/секреты.
 * Игрок не платит ничего — все комиссии и rent берёт на себя проект.
 */
import { createUmi } from '@metaplex-foundation/umi-bundle-defaults';
import { createTree, mplBubblegum, mintV1, parseLeafFromMintV1Transaction } from '@metaplex-foundation/mpl-bubblegum';
import { generateSigner, keypairIdentity, publicKey, type Umi } from '@metaplex-foundation/umi';
import type { ServerEnv } from './env';

export interface MintRequest {
  /** Кошелёк игрока (base58). */
  leafOwner: string;
  name: string;
  /** URI метаданных (наш сервер отдаёт /metadata/:rewardId). */
  uri: string;
  /** Название коллекции (для группировки в кошельках). */
  collectionName: string;
}

export interface MintResult {
  status: 'minted' | 'queued' | 'failed';
  assetId: string | null;
  signature: string | null;
  mode: ServerEnv['mintMode'];
  error?: string;
}

export class Minter {
  private umi: Umi | null = null;

  constructor(private readonly env: ServerEnv) {}

  get mode(): ServerEnv['mintMode'] {
    return this.env.mintMode;
  }

  private ready(): { ok: true } | { ok: false; reason: string } {
    if (this.env.mintMode === 'dry-run') return { ok: true };
    if (!this.env.rpcUrl) return { ok: false, reason: 'AMPERGRAD_RPC_URL не задан' };
    if (!this.env.payerSecret) return { ok: false, reason: 'AMPERGRAD_PAYER_SECRET не задан' };
    if (!this.env.merkleTree) return { ok: false, reason: 'AMPERGRAD_MERKLE_TREE не задан' };
    return { ok: true };
  }

  private connection(): Umi {
    if (this.umi) return this.umi;
    if (!this.env.rpcUrl || !this.env.payerSecret) throw new Error('RPC или ключ минта не настроены');
    const umi = createUmi(this.env.rpcUrl).use(mplBubblegum());
    umi.use(keypairIdentity(umi.eddsa.createKeypairFromSecretKey(Uint8Array.from(this.env.payerSecret))));
    this.umi = umi;
    return umi;
  }

  async mint(request: MintRequest): Promise<MintResult> {
    const readiness = this.ready();
    if (!readiness.ok) {
      return { status: 'failed', assetId: null, signature: null, mode: this.env.mintMode, error: readiness.reason };
    }

    // В dry-run награда «в очереди»: приложение показывает честный статус.
    if (this.env.mintMode === 'dry-run') {
      return { status: 'queued', assetId: null, signature: null, mode: 'dry-run' };
    }

    try {
      const umi = this.connection();
      const merkleTree = publicKey(this.env.merkleTree!);

      const builder = mintV1(umi, {
        merkleTree,
        leafOwner: publicKey(request.leafOwner),
        metadata: {
          name: request.name,
          uri: request.uri,
          sellerFeeBasisPoints: 500,
          collection: { __option: 'None' },
          creators: [
            {
              address: umi.identity.publicKey,
              verified: true,
              share: 100,
            },
          ],
        },
      });

      const { signature } = await builder.sendAndConfirm(umi, { confirm: { commitment: 'confirmed' } });
      const signatureBase58 = bs58Encode(signature);

      // Asset id нужен приложению, чтобы показать «запись в реестре».
      let assetId: string | null = null;
      try {
        const leaf = await parseLeafFromMintV1Transaction(umi, signature);
        assetId = leaf.id.toString();
      } catch {
        assetId = null; // не критично: награда выдана, id подтянется при чтении через DAS
      }

      return { status: 'minted', assetId, signature: signatureBase58, mode: this.env.mintMode };
    } catch (error) {
      return {
        status: 'failed',
        assetId: null,
        signature: null,
        mode: this.env.mintMode,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /** Создание Merkle-дерева — отдельная операция (скрипт scripts/create-tree.ts). */
  async createTree(params: { maxDepth: number; maxBufferSize: number; canopyDepth: number }): Promise<string> {
    if (!this.env.rpcUrl || !this.env.payerSecret) throw new Error('RPC или ключ минта не настроены');
    const umi = this.connection();
    const merkleTree = generateSigner(umi);
    const builder = await createTree(umi, {
      merkleTree,
      maxDepth: params.maxDepth,
      maxBufferSize: params.maxBufferSize,
      canopyDepth: params.canopyDepth,
      public: false,
    });
    await builder.sendAndConfirm(umi);
    return merkleTree.publicKey.toString();
  }
}

/** Кодирование подписи: в проекте используется bs58 (как в MWA и web3.js). */
function bs58Encode(bytes: Uint8Array): string {
  const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let zeros = 0;
  while (zeros < bytes.length && bytes[zeros] === 0) zeros++;
  const digits: number[] = [];
  for (let i = zeros; i < bytes.length; i++) {
    let carry = bytes[i];
    for (let j = 0; j < digits.length; j++) {
      carry += digits[j] * 256;
      digits[j] = carry % 58;
      carry = (carry / 58) | 0;
    }
    while (carry > 0) {
      digits.push(carry % 58);
      carry = (carry / 58) | 0;
    }
  }
  let out = '1'.repeat(zeros);
  for (let i = digits.length - 1; i >= 0; i--) out += ALPHABET[digits[i]];
  return out;
}
