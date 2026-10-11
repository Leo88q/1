/**
 * Каталог наград и правила допуска.
 *
 * Правила проверяются на сервере по ПОДТВЕРЖДЁННОМУ прогрессу кошелька
 * (см. verify.ts: ходы игрока проигрываются движком). Клиент не может
 * «попросить награду» — он может только заявить доказательство решения.
 */
import type { WalletProgress } from './store';

export interface ClaimCounters {
  total: number;
  byReward: number;
  today: number;
}

export interface RewardDefinition {
  id: string;
  name: string;
  description: string;
  /** Короткая лор-запись, которая попадает в метаданные NFT. */
  lore: string;
  /** Тип: достижение, косметика, знак сезона. */
  kind: 'achievement' | 'cosmetic' | 'badge';
  /** Цвет для картинки-плейсхолдера. */
  accent: string;
  eligible: (progress: WalletProgress, counters: ClaimCounters) => { ok: boolean; reason?: string };
}

export const REWARDS: RewardDefinition[] = [
  {
    id: 'plomba-first',
    name: 'Пломба · первый квартал',
    description: 'Первая собранная цепь в Ртутном ярусе.',
    lore:
      'Цикл 431, реестр 1.1. «Мне сказали: три квартала, ничего сложного. Я открыл первый — и в жилах услышал свой голос.»',
    kind: 'achievement',
    accent: '#00E5C7',
    eligible: (progress, counters) =>
      counters.byReward > 0
        ? { ok: false, reason: 'эта пломба уже в реестре' }
        : progress.solvedLevels.size >= 1
          ? { ok: true }
          : { ok: false, reason: 'нужно подтверждённое решение хотя бы одного квартала' },
  },
  {
    id: 'plomba-ten',
    name: 'Пломба · десять кварталов',
    description: 'Десять подтверждённых решений.',
    lore: 'Цикл 434, реестр 6.2. «Десять пломб — и город начал отвечать: свет мигает в такт шагам мастера.»',
    kind: 'achievement',
    accent: '#2AD9C4',
    eligible: (progress, counters) =>
      counters.byReward > 0
        ? { ok: false, reason: 'эта пломба уже в реестре' }
        : progress.solvedLevels.size >= 10
          ? { ok: true }
          : { ok: false, reason: `подтверждено ${progress.solvedLevels.size} из 10 кварталов` },
  },
  {
    id: 'film-mercury',
    name: 'Плёнка Ртутного яруса',
    description: 'Косметика поля: ртутная палитра и влажный блик.',
    lore: 'Квитанция мастерской: «Плёнка взята со старого трансформатора. Блестит как совесть гильдии.»',
    kind: 'cosmetic',
    accent: '#5CC8FF',
    eligible: (progress, counters) =>
      counters.byReward > 0
        ? { ok: false, reason: 'плёнка уже выдана' }
        : progress.solvedLevels.size >= 15
          ? { ok: true }
          : { ok: false, reason: `нужно 15 кварталов, подтверждено ${progress.solvedLevels.size}` },
  },
  {
    id: 'mark-old-diode',
    name: 'Метка Старого Диода',
    description: 'Знак за работу в отключённом секторе.',
    lore: 'Цикл 402, реестр 12.4. «Свет — это не услуга. Это счёт, который предъявят потомкам.»',
    kind: 'badge',
    accent: '#FFC24B',
    eligible: (progress, counters) => {
      if (counters.byReward > 0) return { ok: false, reason: 'метка уже выдана' };
      const deep = [...progress.solvedLevels].some((l) => l >= 46);
      return deep ? { ok: true } : { ok: false, reason: 'нужен подтверждённый квартал Старого Диода (уровень 46+)' };
    },
  },
  {
    id: 'sign-daily',
    name: 'Печать челленджа',
    description: 'Знак за решённый общий квартал дня.',
    lore: 'Интерком: «Один квартал — для всех. Город смотрит, как мастера повторяют один и тот же путь.»',
    kind: 'badge',
    accent: '#FF3D81',
    eligible: (progress, counters) =>
      counters.byReward > 0
        ? { ok: false, reason: 'печать уже выдана' }
        : progress.dailyDays.size >= 1
          ? { ok: true }
          : { ok: false, reason: 'нужен подтверждённый челлендж дня' },
  },
  {
    id: 'sign-streak-seven',
    name: 'Знак семи дней',
    description: 'Семь дней подряд с решённым челленджем.',
    lore: 'Клемма: «Регламент 7.7 — серия семь дней. Не спрашивайте, зачем. Просто не прерывайте.»',
    kind: 'badge',
    accent: '#FFD166',
    eligible: (progress, counters) =>
      counters.byReward > 0
        ? { ok: false, reason: 'знак уже выдан' }
        : progress.bestDailyStreak >= 7
          ? { ok: true }
          : { ok: false, reason: `лучшая серия ${progress.bestDailyStreak} из 7 дней` },
  },
];

export function rewardById(id: string): RewardDefinition | undefined {
  return REWARDS.find((r) => r.id === id);
}

/** Первая доступная награда — чтобы клиент мог просто «забрать пломбу». */
export function firstEligible(
  progress: WalletProgress,
  countersFor: (rewardId: string) => ClaimCounters,
): RewardDefinition | null {
  for (const reward of REWARDS) {
    if (reward.eligible(progress, countersFor(reward.id)).ok) return reward;
  }
  return null;
}

export function evaluate(
  reward: RewardDefinition,
  progress: WalletProgress,
  counters: ClaimCounters,
): { ok: boolean; reason?: string } {
  return reward.eligible(progress, counters);
}
