/**
 * Магазин косметики «Амперграда»: только внешний вид, никаких преимуществ.
 *
 * Канон: docs/04 §2 и docs/05 §8. Цены — ориентиры для теста, реальные значения
 * задаются в магазине платформы. Игрок НИЧЕГО не платит за награды за игру.
 */

/**
 * Цены за монеты сбалансированы под кампанию: 75 уровней дают примерно
 * 3750 монет при трёх звёздах (50 за квартал) плюс дейлики. Самый дорогой
 * предмет за монеты — сезонный, около 40 собранных кварталов.
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
    priceCoins: 450,
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
    priceCoins: 900,
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
    priceCoins: 700,
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
    priceCoins: 2000,
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

/**
 * Английские строки магазина. Листинг dApp Store и интерфейс игры идут на двух
 * языках, поэтому карточка предмета переводится целиком: название, способ
 * получения и лор-строка. Ключ — id предмета; если перевода нет, клиент
 * показывает русскую строку (так карточка не остаётся пустой).
 */
export interface CosmeticEn {
  name: string;
  how: string;
  lore: string;
}

export const COSMETICS_EN: Record<string, CosmeticEn> = {
  'plomba-first': {
    name: 'The First Seal',
    how: 'clear the first level',
    lore: 'A guild seal for the first closed circuit. On the back — a date you never chose.',
  },
  'plomba-ten': {
    name: 'Dry Run ×10',
    how: 'clear 10 levels',
    lore: 'Ten districts with no overload. Klemma marks shifts like this in silence.',
  },
  'film-mercury': {
    name: 'Mercury Tier Film',
    how: 'clear 15 levels',
    lore: 'A peeled layer of living metal. It keeps its shape as long as someone is watching.',
  },
  'mark-old-diode': {
    name: 'Old Diode Mark',
    how: 'reach level 46',
    lore: 'A mark of the historic sector. Wearing it counts as bad taste — that is its main value.',
  },
  'sign-daily': {
    name: 'Seal of the Day',
    how: 'finish the daily challenge',
    lore: 'One day, confirmed by witnesses. The witnesses are other players, and they stay quiet.',
  },
  'sign-streak-seven': {
    name: 'Seal of Seven Days',
    how: 'a 7-day streak',
    lore: 'The guild respects a streak: seven days is a habit already, and a habit is a debt.',
  },
  'palette-terminal': {
    name: 'Terminal Palette',
    how: 'coin shop',
    lore: 'Colours of an old teletype: yellow on green, noise on the frame.',
  },
  'palette-mercury': {
    name: 'Mercury Palette',
    how: 'coin shop',
    lore: 'The colour of living metal. In daylight it looks like water, in the dark like a cable.',
  },
  'theme-plateau': {
    name: 'Plateau Theme',
    how: 'coin shop',
    lore: 'The upper tier: palms, ads, light by licence. The licence expired long ago.',
  },
  'source-gold': {
    name: 'Golden Bus',
    how: 'coin shop (seasonal offer)',
    lore: 'An animated source. The first volt-master carried one like this, back when he still carried his own tools.',
  },
  'palette-melter': {
    name: 'Melter Palette',
    how: 'platform store',
    lore: 'Colours of the array floor: heat, glass, reflections. Current seems to run slower on this palette — an impression only.',
  },
  'theme-old-diode': {
    name: 'Old Diode Theme',
    how: 'platform store',
    lore: 'The historic sector: glass, dust, light almost switched off. Comes with silence instead of a track.',
  },
  'wing-marta': {
    name: 'Film of Marta',
    how: 'platform store',
    lore: 'Left in the workshop together with the tool. Marta never takes things out of closed workshops.',
  },
  'source-core': {
    name: 'Core Source',
    how: 'platform store, limited run',
    lore: 'A premium source with the living rhythm of the array. The rhythm syncs with the pulse of the player — roughly.',
  },
  'wing-klemma': {
    name: 'Film of Klemma',
    how: 'platform store',
    lore: 'A service film of the dispatcher. Held to the light it reads as regulations, but only clauses 1.0 and 4.2.',
  },
  'pass-s1-free': {
    name: 'Season 1: Regulation Track',
    how: 'free track of season 1',
    lore: 'Twelve registry pages issued on schedule. The schedule is an appendix to the regulations.',
  },
  'pass-s1-paid': {
    name: 'Season 1: Three Districts Track',
    how: 'paid track of season 1',
    lore: 'All twenty entries of the season plus a page that is not in the archive. The copy is official.',
  },
  'seal-guild-s1': {
    name: 'Guild Seal, Season 1',
    how: 'level 100 of the free track or top-100 of the leaderboard',
    lore: 'Not for sale. Issued to those who closed the season before the counter was switched off.',
  },
};

/** Английская карточка предмета, если перевод есть. */
export function cosmeticEnById(id: string): CosmeticEn | undefined {
  return COSMETICS_EN[id];
}

export function cosmeticById(id: string): Cosmetic | undefined {
  return COSMETICS.find((c) => c.id === id);
}

/** Предметы витрины — то, что показывается в магазине (без наград за игру). */
export function storeItems(): Cosmetic[] {
  return COSMETICS.filter((c) => c.channel === 'store');
}

/** Проверка принципа «косметика не влияет на игру»: ни у одного предмета нет параметров силы. */
export const COSMETICS_ARE_VISUAL_ONLY = true;
