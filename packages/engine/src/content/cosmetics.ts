/**
 * Магазин косметики «Амперграда»: только внешний вид, никаких преимуществ.
 *
 * Канон: docs/04 §2 и docs/05 §8. Цены — ориентиры для теста, реальные значения
 * задаются в магазине платформы. Игрок НИЧЕГО не платит за награды за игру.
 */

export type CosmeticType = 'palette' | 'theme' | 'wing' | 'source' | 'seal' | 'registry';
export type CosmeticRarity = 'common' | 'rare' | 'epic' | 'legendary';
/** Как предмет существует: бесплатно из игры, за монеты или за деньги в магазине платформы. */
export type CosmeticChannel = 'reward' | 'coins' | 'store' | 'battle-pass';

export interface Cosmetic {
  id: string;
  name: string;
  type: CosmeticType;
  rarity: CosmeticRarity;
  channel: CosmeticChannel;
  /** Цена в долларах для витрины платформы; null — не продаётся. */
  priceUsd: number | null;
  /** Цена в монетах за игру (зарабатываются, не покупаются). */
  priceCoins: number | null;
  /** Как получить — текст для карточки. */
  how: string;
  /** Лор-строка: каждый предмет — запись реестра, а не просто скин. */
  lore: string;
  /** Палитра предмета (для рендера). */
  colors: string[];
  /** Стандарт награды, когда предмет выдаётся как NFT. */
  standard: 'cNFT' | 'Core' | null;
}

export const COSMETICS: Cosmetic[] = [
  // Награды за игру: игрок не платит никогда.
  {
    id: 'plomba-first',
    name: 'Первая пломба',
    type: 'seal',
    rarity: 'common',
    channel: 'reward',
    priceUsd: null,
    priceCoins: null,
    how: 'решить первый уровень',
    lore: 'Печать гильдии за первый замкнутый контур. На обороте — дата, которую вы не выбирали.',
    colors: ['#00E5C7', '#7DE9FF'],
    standard: 'cNFT',
  },
  {
    id: 'plomba-ten',
    name: 'Сухой ход ×10',
    type: 'seal',
    rarity: 'rare',
    channel: 'reward',
    priceUsd: null,
    priceCoins: null,
    how: '10 решённых уровней',
    lore: 'Десять кварталов без перегрузки. Клемма отмечает такие смены молча.',
    colors: ['#00E5C7', '#FFC24B'],
    standard: 'cNFT',
  },
  {
    id: 'film-mercury',
    name: 'Плёнка Ртутного яруса',
    type: 'wing',
    rarity: 'rare',
    channel: 'reward',
    priceUsd: null,
    priceCoins: null,
    how: '15 решённых уровней',
    lore: 'Отслоившаяся плёнка живого металла. Держит форму, пока за ней наблюдают.',
    colors: ['#9AA7FF', '#00E5C7'],
    standard: 'cNFT',
  },
  {
    id: 'mark-old-diode',
    name: 'Знак Старого Диода',
    type: 'seal',
    rarity: 'epic',
    channel: 'reward',
    priceUsd: null,
    priceCoins: null,
    how: 'дойти до 46 уровня',
    lore: 'Метка исторического сектора. Носить её считается дурным тоном — и это её главная ценность.',
    colors: ['#FF3D81', '#9AA7FF'],
    standard: 'cNFT',
  },
  {
    id: 'sign-daily',
    name: 'Печать дня',
    type: 'seal',
    rarity: 'common',
    channel: 'reward',
    priceUsd: null,
    priceCoins: null,
    how: 'закрыть челлендж дня',
    lore: 'Один день, подтверждённый свидетелями. Свидетели — другие игроки, и они молчат.',
    colors: ['#FFC24B', '#00E5C7'],
    standard: 'cNFT',
  },
  {
    id: 'sign-streak-seven',
    name: 'Печать семи дней',
    type: 'seal',
    rarity: 'epic',
    channel: 'reward',
    priceUsd: null,
    priceCoins: null,
    how: 'серия 7 дней подряд',
    lore: 'Гильдия уважает серию: она считает, что семь дней — это уже привычка, а привычка — долг.',
    colors: ['#FF3D81', '#FFC24B'],
    standard: 'cNFT',
  },

  // За монеты (зарабатываются в игре, не покупаются за деньги).
  {
    id: 'palette-terminal',
    name: 'Палитра «Терминал»',
    type: 'palette',
    rarity: 'common',
    channel: 'coins',
    priceUsd: null,
    priceCoins: 600,
    how: 'магазин за монеты',
    lore: 'Цвета старого телетайпа: жёлтый на зелёном, шум на кадре.',
    colors: ['#6CFFB0', '#FFC24B', '#06080E'],
    standard: 'cNFT',
  },
  {
    id: 'palette-mercury',
    name: 'Палитра «Ртуть»',
    type: 'palette',
    rarity: 'rare',
    channel: 'coins',
    priceUsd: null,
    priceCoins: 1200,
    how: 'магазин за монеты',
    lore: 'Цвет живого металла. На солнце выглядит как вода, в темноте — как кабель.',
    colors: ['#9AA7FF', '#00E5C7', '#0B0F18'],
    standard: 'cNFT',
  },
  {
    id: 'theme-plateau',
    name: 'Тема «Плато»',
    type: 'theme',
    rarity: 'common',
    channel: 'coins',
    priceUsd: null,
    priceCoins: 900,
    how: 'магазин за монеты',
    lore: 'Верхний ярус: пальмы, реклама, свет по лицензии. Лицензия давно истекла.',
    colors: ['#7DE9FF', '#FFC24B', '#0E1520'],
    standard: 'cNFT',
  },
  {
    id: 'source-gold',
    name: 'Золотая шина',
    type: 'source',
    rarity: 'legendary',
    channel: 'coins',
    priceUsd: null,
    priceCoins: 5000,
    how: 'магазин за монеты (сезонное предложение)',
    lore: 'Анимированный источник. Такой источник был на первом вольт-мастере, когда тот ещё носил инструмент сам.',
    colors: ['#FFC24B', '#FFF3C4', '#06080E'],
    standard: 'cNFT',
  },

  // Витрина платформы (реальные деньги) — только внешний вид.
  {
    id: 'palette-melter',
    name: 'Палитра «Плавильня»',
    type: 'palette',
    rarity: 'epic',
    channel: 'store',
    priceUsd: 1.99,
    priceCoins: null,
    how: 'витрина',
    lore: 'Цвета дна массива: жар, стекло, отсветы. Ток по такой палитре идёт медленнее — это только впечатление.',
    colors: ['#FFC24B', '#FF3D81', '#1A0E12'],
    standard: 'cNFT',
  },
  {
    id: 'theme-old-diode',
    name: 'Тема «Старый Диод»',
    type: 'theme',
    rarity: 'epic',
    channel: 'store',
    priceUsd: 2.99,
    priceCoins: null,
    how: 'витрина',
    lore: 'Исторический сектор: стекло, пыль, почти выключенный свет. В комплекте — тишина вместо трека.',
    colors: ['#FF3D81', '#9AA7FF', '#0A0A12'],
    standard: 'cNFT',
  },
  {
    id: 'wing-marta',
    name: 'Плёнка Марты',
    type: 'wing',
    rarity: 'epic',
    channel: 'store',
    priceUsd: 3.99,
    priceCoins: null,
    how: 'витрина',
    lore: 'Оставлена в мастерской вместе с инструментом. Марта не забирает вещи из закрытых мастерских.',
    colors: ['#00E5C7', '#FF3D81'],
    standard: 'cNFT',
  },
  {
    id: 'source-core',
    name: 'Источник «Ядро»',
    type: 'source',
    rarity: 'legendary',
    channel: 'store',
    priceUsd: 9.99,
    priceCoins: null,
    how: 'витрина, лимитированный тираж',
    lore: 'Премиальный источник с живым ритмом массива. Ритм синхронизируется с пульсом игрока — по ощущениям.',
    colors: ['#FFC24B', '#FF3D81', '#00E5C7'],
    standard: 'Core',
  },

  {
    id: 'wing-klemma',
    name: 'Плёнка Клеммы',
    type: 'wing',
    rarity: 'rare',
    channel: 'store',
    priceUsd: 2.99,
    priceCoins: null,
    how: 'витрина',
    lore: 'Служебная плёнка диспетчера. На просвет читается регламент, но только позиции 1.0 и 4.2.',
    colors: ['#7DE9FF', '#00E5C7'],
    standard: 'cNFT',
  },

  // Battle Pass: сезонный трек.
  {
    id: 'pass-s1-free',
    name: 'Сезон 1: трек «Регламент»',
    type: 'registry',
    rarity: 'common',
    channel: 'battle-pass',
    priceUsd: null,
    priceCoins: null,
    how: 'бесплатный трек сезона 1',
    lore: 'Двенадцать страниц реестра, выданных по расписанию. Расписание — приложение к регламенту.',
    colors: ['#00E5C7', '#7DE9FF'],
    standard: 'cNFT',
  },
  {
    id: 'pass-s1-paid',
    name: 'Сезон 1: трек «Три квартала»',
    type: 'registry',
    rarity: 'epic',
    channel: 'battle-pass',
    priceUsd: 4.99,
    priceCoins: null,
    how: 'платный трек сезона 1',
    lore: 'Все двадцать записей сезона плюс страница, которой нет в архиве. Копия — официальная.',
    colors: ['#FFC24B', '#9AA7FF'],
    standard: 'cNFT',
  },
  {
    id: 'seal-guild-s1',
    name: 'Печать гильдии, сезон 1',
    type: 'seal',
    rarity: 'legendary',
    channel: 'battle-pass',
    priceUsd: null,
    priceCoins: null,
    how: '100-й уровень бесплатного трека или топ-100 лидерборда',
    lore: 'Не продаётся. Выдаётся тем, кто закрыл сезон до отключения счётчика.',
    colors: ['#FFF3C4', '#FF3D81', '#00E5C7'],
    standard: 'cNFT',
  },
];

export function cosmeticById(id: string): Cosmetic | undefined {
  return COSMETICS.find((c) => c.id === id);
}

/** Предметы витрины — то, что показывается в магазине (без наград за игру). */
export function storeItems(): Cosmetic[] {
  return COSMETICS.filter((c) => c.channel === 'store');
}

/** Проверка принципа «косметика не влияет на игру»: ни у одного предмета нет параметров силы. */
export const COSMETICS_ARE_VISUAL_ONLY = true;
