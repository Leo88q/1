/**
 * Метаданные наград.
 *
 * Пока арт — процедурный SVG на нашем домене (финальный арт заменит его без
 * перевыпуска: URI метаданных остаётся тем же). SVG отдаёт эндпоинт
 * /nft/:rewardId.svg; кошельки, которые не умеют SVG, покажут имя и атрибуты.
 */
import type { RewardDefinition } from './rewards';

export interface RewardMetadata {
  name: string;
  symbol: string;
  description: string;
  image: string;
  external_url: string;
  attributes: Array<{ trait_type: string; value: string | number }>;
  properties: {
    category: string;
    creators: Array<{ address: string; share: number }>;
  };
}

export function buildMetadata(params: {
  reward: RewardDefinition;
  address: string;
  engineVersion: string;
  baseUrl: string | null;
  creatorAddress?: string | null;
}): RewardMetadata {
  const { reward, address, engineVersion, baseUrl, creatorAddress } = params;
  const short = `${address.slice(0, 4)}…${address.slice(-4)}`;
  return {
    name: `${reward.name}`,
    symbol: 'AMPG',
    description: `${reward.description}\n\n${reward.lore}\n\nМастер: ${short}`,
    image: baseUrl ? `${baseUrl}/nft/${reward.id}.svg` : `https://ampergrad.game/nft/${reward.id}.svg`,
    external_url: baseUrl ?? 'https://ampergrad.game',
    attributes: [
      { trait_type: 'Награда', value: reward.id },
      { trait_type: 'Тип', value: reward.kind },
      { trait_type: 'Версия правил', value: engineVersion },
      { trait_type: 'Кошелёк', value: short },
    ],
    properties: {
      category: 'image',
      creators: creatorAddress ? [{ address: creatorAddress, share: 100 }] : [],
    },
  };
}

/** Процедурная картинка награды: контур квартала, искра и цвет яруса. */
export function rewardSvg(reward: RewardDefinition): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#0B0F1A"/><stop offset="1" stop-color="#05070C"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" fill="url(#bg)"/>
  <g stroke="${reward.accent}" stroke-width="8" fill="none" opacity="0.95">
    <rect x="120" y="120" width="272" height="272" rx="42"/>
    <path d="M256 96 L256 210"/>
    <path d="M256 302 L256 416"/>
    <path d="M96 256 L210 256"/>
    <path d="M302 256 L416 256"/>
  </g>
  <path d="M236 214 L286 214 L262 262 L292 262 L228 330 L248 262 L218 262 Z" fill="#FFC24B" opacity="0.92"/>
  <text x="256" y="452" fill="#8FA3BF" font-family="monospace" font-size="24" text-anchor="middle">${reward.id}</text>
</svg>`;
}
