/**
 * Канонический лор «Амперграда» как данные: ярусы, кварталы уровней и реестр.
 *
 * Источник истины для клиента (Kotlin получает этот же файл через JSON-выгрузку),
 * сервера (метаданные cNFT, страницы реестра) и веб-лаборатории.
 * Правила тона — docs/05, раздел 9: служебная документация, без восторгов.
 */

/** Ярус города: тематический блок из 15 уровней. */
export interface Tier {
  id: string;
  /** Номер яруса сверху вниз, 1–5. */
  order: number;
  name: string;
  /** Английское имя яруса (интерфейс приложения держит своё — см. UiStrings). */
  nameEn: string;
  /** Диапазон уровней включительно. */
  levels: [number, number];
  /** Рост сетки внутри яруса — для текстов интерфейса. */
  grid: string;
  /** Новое правило, которое вводит ярус. */
  rule: string;
  /** Звуковая подложка (одна на ярус). */
  ambience: string;
  /** Базовые цвета яруса (неоновая палитра). */
  palette: [string, string, string];
}

export const TIERS: Tier[] = [
  {
    id: 'plateau',
    order: 1,
    name: 'Плато Первого Контакта',
    nameEn: 'First Contact Plateau',
    levels: [1, 15],
    grid: '4×4 → 5×5',
    rule: 'туториал: колено, магистраль, поворот',
    ambience: 'гул трансформатора, реклама сверху',
    palette: ['#00E5C7', '#7DE9FF', '#FFC24B'],
  },
  {
    id: 'mercury',
    order: 2,
    name: 'Ртутный ярус',
    nameEn: 'Mercury Tier',
    levels: [16, 30],
    grid: '5×5 → 6×6',
    rule: 'тройники, кресты, отвлекающие тупики',
    ambience: 'мастерские, радиопомехи Марты',
    palette: ['#00E5C7', '#9AA7FF', '#FF3D81'],
  },
  {
    id: 'terminal',
    order: 3,
    name: 'Терминальный ярус',
    nameEn: 'Terminal Tier',
    levels: [31, 45],
    grid: '6×6 → 7×7',
    rule: 'два приёмника, лимит ходов на звезду',
    ambience: 'старые терминалы связи, перестук реле',
    palette: ['#00E5C7', '#6CFFB0', '#FFC24B'],
  },
  {
    id: 'old-diode',
    order: 4,
    name: 'Старый Диод',
    nameEn: 'The Old Diode',
    levels: [46, 60],
    grid: '7×7',
    rule: '«мёртвые» ячейки (залитые стеклом), два источника',
    ambience: 'почти тишина, редкие детские считалки в эфире',
    palette: ['#FF3D81', '#9AA7FF', '#00E5C7'],
  },
  {
    id: 'melter',
    order: 5,
    name: 'Плавильня (Ядро)',
    nameEn: 'The Melter (Core)',
    levels: [61, 75],
    grid: '7×7 → 8×8',
    rule: 'всё вместе + секретные «записи»',
    ambience: 'ритм массива, низкий гул на дне',
    palette: ['#FFC24B', '#FF3D81', '#00E5C7'],
  },
];

/** Всего уровней в основной кампании. */
export const TOTAL_LEVELS = 75;

/** Квартал одного уровня: короткое имя (ru/en) и код для интерфейса. */
export interface Quarter {
  level: number;
  /** Код в стиле диспетчерской: ярус + номер внутри яруса. */
  code: string;
  name: string;
  /** Английское имя — интерфейс игры двуязычный (см. UiStrings в приложении). */
  nameEn: string;
}

const QUARTER_NAMES: Record<string, string[]> = {
  plateau: [
    'Пробный контур',
    'Пальмовая крыша',
    'Лицензионный свет',
    'Реклама 4-А',
    'Сухой контакт',
    'Щелчок реле',
    'Резервная линия',
    'Смотровая площадка',
    'Первая пломба',
    'Гостевой контур',
    'Тихий стояк',
    'Фонарь над проспектом',
    'Кабель под газоном',
    'Второй вход',
    'Сдача практики',
  ],
  mercury: [
    'Живой металл',
    'Подпольная мастерская',
    'Мастерская Марты',
    'Ртутный жёлоб',
    'Тройник гильдии',
    'Литой крест',
    'Тупик на Виадуке',
    'Латунный ярус',
    'Гараж «Катушка»',
    'Провод под кожей',
    'Хомутово поле',
    'Ремонт без сметы',
    'Сплав и опока',
    'Термопара',
    'Смена мастера',
  ],
  terminal: [
    'Старый порт',
    'Коммутатор 12',
    'Диспетчерская',
    'Грузовая линия',
    'Пакетный узел',
    'Ретранслятор',
    'Порт трёх реле',
    'Стрелка семафора',
    'Сортировочный',
    'Дальняя ветка',
    'Телетайп',
    'Частотный мост',
    'Развязка',
    'Ночная смена',
    'Обратный рейс',
  ],
  'old-diode': [
    'Стеклянный зал',
    'Класс первого вольт-мастера',
    'Музей отключений',
    'Залитая ячейка',
    'Архив без страницы',
    'Две шины',
    'Тёмное крыло',
    'Детская считалка',
    'Кабинет 46',
    'Суд над светом',
    'Пыль на контактах',
    'Именная доска',
    'Старая пломба',
    'Прощальное реле',
    'Дверь без ручки',
  ],
  melter: [
    'Дно массива',
    'Первое дыхание',
    'Ритм под полом',
    'Гальваническая жила',
    'Токовый след',
    'Стихший ярус',
    'Последний контур',
    'Плавильная чаша',
    'Обратный счёт',
    'Жила № 7',
    'Свет и память',
    'Три подъёма',
    'Ход без возврата',
    'Ядро слушает',
    'Полное включение',
  ],
};


const QUARTER_NAMES_EN: Record<string, string[]> = {
  plateau: [
    'Trial Circuit',
    'Palm Roof',
    'Licensed Light',
    'Billboard 4-A',
    'Dry Contact',
    'Relay Click',
    'Backup Line',
    'Observation Deck',
    'First Seal',
    'Guest Circuit',
    'Quiet Riser',
    'Lamp Over the Avenue',
    'Cable Under the Lawn',
    'Second Entrance',
    'Practice Handover',
  ],
  mercury: [
    'Living Metal',
    'Backroom Workshop',
    "Marta's Workshop",
    'Mercury Chute',
    'Guild Tee',
    'Cast Cross',
    'Viaduct Dead End',
    'Brass Tier',
    'Coil Garage',
    'Wire Under Skin',
    'Clamp Field',
    'Unscheduled Repair',
    'Alloy and Mould',
    'Thermocouple',
    'Shift Change',
  ],
  terminal: [
    'Old Port',
    'Switchboard 12',
    'Dispatch Room',
    'Freight Line',
    'Packet Node',
    'Repeater',
    'Three-Relay Port',
    'Semaphore Switch',
    'Sorting Yard',
    'Far Branch',
    'Teletype',
    'Frequency Bridge',
    'Interchange',
    'Night Shift',
    'Return Run',
  ],
  'old-diode': [
    'Glass Hall',
    "First Volt-Master's Class",
    'Shutdown Museum',
    'Poured Cell',
    'Archive Missing a Page',
    'Two Buses',
    'Dark Wing',
    "Children's Counting Rhyme",
    'Office 46',
    'Trial of Light',
    'Dust on Contacts',
    'Board of Names',
    'Old Seal',
    'Parting Relay',
    'Door Without a Handle',
  ],
  melter: [
    'Bottom of the Array',
    'First Breath',
    'Rhythm Under the Floor',
    'Galvanic Vein',
    'Current Trace',
    'Quiet Tier',
    'Last Circuit',
    'Melting Basin',
    'Countdown',
    'Vein No. 7',
    'Light and Memory',
    'Three Ascents',
    'No Way Back',
    'The Core Listens',
    'Full Power',
  ],
};

const TIER_CODES: Record<string, string> = {
  plateau: 'П',
  mercury: 'Р',
  terminal: 'Т',
  'old-diode': 'Д',
  melter: 'Я',
};

function buildQuarters(): Quarter[] {
  const out: Quarter[] = [];
  for (const tier of TIERS) {
    const names = QUARTER_NAMES[tier.id] ?? [];
    const namesEn = QUARTER_NAMES_EN[tier.id] ?? [];
    const [from] = tier.levels;
    names.forEach((name, i) => {
      out.push({
        level: from + i,
        code: `${TIER_CODES[tier.id] ?? '?'}-${String(i + 1).padStart(2, '0')}`,
        name,
        nameEn: namesEn[i] ?? name,
      });
    });
  }
  return out;
}

/** 75 кварталов — по одному на уровень. */
export const QUARTERS: Quarter[] = buildQuarters();

/** Запись реестра: короткий лор-фрагмент, который выдаётся как награда. */
export interface RegistryEntry {
  /** Идентификатор для метаданных и минта: латиница, стабильный. */
  id: string;
  season: 1 | 2 | 3;
  title: string;
  /** Английское название — интерфейс игры двуязычный. */
  titleEn: string;
  /** Кто «говорит»: Клемма, Марта Вольт, Хриза, Крючники, эфир, Плавильня. */
  speaker: string;
  text: string;
  /** Английский текст записи (тон тот же: служебная документация, без восторгов). */
  textEn: string;
  /** Как запись попадает к игроку (для текста награды). */
  source: string;
  sourceEn: string;
  /** Ярусы, к которым запись привязана, — для подбора награды. */
  tiers: string[];
}

/** Голоса города по-английски: имена собственные не переводятся, «эфир» — подпись. */
export const SPEAKERS_EN: Record<string, string> = {
  Клемма: 'Klemma',
  'Марта Вольт': 'Marta Volt',
  Хриза: 'Khriza',
  Крючники: 'the Hookmen',
  эфир: 'the air',
  Плавильня: 'the Melter',
};

/** Имя говорящего для английского интерфейса (нет перевода — остаётся как есть). */
export function speakerForLang(speaker: string, lang: 'ru' | 'en'): string {
  return lang === 'en' ? SPEAKERS_EN[speaker] ?? speaker : speaker;
}

export const REGISTRY: RegistryEntry[] = [
  // Сезон 1 «Три квартала»
  { id: 'reg-s1-01', season: 1, title: 'Регламент 1.0', speaker: 'Клемма', text: 'Позиция 1.0: свет выдаётся по норме. Норма — приложение к норме. Приложение — в архиве. Архив отключён.', source: 'первое решение уровня', titleEn: 'Regulation 1.0', textEn: 'Clause 1.0: light is issued by quota. The quota is an appendix to the quota. The appendix is in the archive. The archive is offline.', sourceEn: 'first solve of any level', tiers: ['plateau'] },
  { id: 'reg-s1-02', season: 1, title: 'Пальмовая крыша', speaker: 'эфир', text: 'Наверху свет по лицензии. Лицензия истекла в прошлом цикле, но платили вперёд, поэтому всё ещё светло.', source: 'ярус 1, 5-й квартал', titleEn: 'Palm Roof', textEn: 'Up top the light is licensed. The licence expired a cycle ago, but it was paid forward, so it is still bright.', sourceEn: 'tier 1, 5th district', tiers: ['plateau'] },
  { id: 'reg-s1-03', season: 1, title: 'Щелчок реле', speaker: 'Клемма', text: 'Позиция 4.2: при перегрузке — сброс. Щелчок реле слышно от сброса и до сброса. Считайте его гудком.', source: 'серия «сухой ход»', titleEn: 'Relay Click', textEn: 'Clause 4.2: on overload, reset. The relay click is heard from reset to reset. Treat it as a horn.', sourceEn: 'the dry-move streak', tiers: ['plateau'] },
  { id: 'reg-s1-04', season: 1, title: 'Сухой контакт', speaker: 'Марта Вольт', text: 'Не доверяй линии, которая выглядит законченной. В Ртутном ярусе всё законченное — либо чужое, либо отключено.', source: 'ярус 2, первый тройник', titleEn: 'Dry Contact', textEn: 'Never trust a line that looks finished. In the Mercury Tier anything finished is either somebody else\'s line or switched off.', sourceEn: 'tier 2, your first tee', tiers: ['mercury'] },
  { id: 'reg-s1-05', season: 1, title: 'Заказ на три квартала', speaker: 'Хриза', text: 'Оплата по факту пломбы. Факт — в реестре. Реестр ведёт тот, кто платит. Это не вопрос, это порядок.', source: 'уровень 16', titleEn: 'Order for Three Districts', textEn: 'Payment on sealing. The fact is in the registry. The registry is kept by whoever pays. This is not a question, it is procedure.', sourceEn: 'level 16', tiers: ['mercury'] },
  { id: 'reg-s1-06', season: 1, title: 'Мастерская Марты', speaker: 'Марта Вольт', text: 'Мастерская закрыта с того дня, как сверху отключили первую линию. Инструмент на месте. Полотенце сухое — значит, заходили.', source: 'ярус 2, 5-й квартал', titleEn: 'The Workshop of Marta', textEn: 'The workshop closed the day the first line was cut from above. The tools are in place. The towel is dry, which means somebody came by.', sourceEn: 'tier 2, 5th district', tiers: ['mercury'] },
  { id: 'reg-s1-07', season: 1, title: 'Крест гильдии', speaker: 'Клемма', text: 'Позиция 7.1: крест не поворачивается. Позиция 7.2: если кажется, что крест повернулся, — смотрите позицию 7.1.', source: 'уровни с фиксированными ячейками', titleEn: 'Guild Crossing', textEn: 'Clause 7.1: a crossing does not turn. Clause 7.2: if a crossing appears to have turned, see clause 7.1.', sourceEn: 'levels with fixed cells', tiers: ['mercury'] },
  { id: 'reg-s1-08', season: 1, title: 'Живой металл', speaker: 'эфир', text: 'Ртуть не течёт вверх. Ртуть ждёт, пока вы решите, что ток идёт вниз. Это разные вещи, и обе верные.', source: 'ярус 2, 10-й квартал', titleEn: 'Living Metal', textEn: 'Mercury does not flow upward. Mercury waits until you decide the current runs down. These are different things and both are true.', sourceEn: 'tier 2, 10th district', tiers: ['mercury'] },
  { id: 'reg-s1-09', season: 1, title: 'Пломба 04', speaker: 'Клемма', text: 'Пломба поставлена. Квартал вспоминает. Вспоминает вслух. Не отвечайте ему — это запись, а не разговор.', source: '10 решённых уровней', titleEn: 'Seal 04', textEn: 'The seal is set. The district remembers. It remembers out loud. Do not answer it: that is a recording, not a conversation.', sourceEn: '10 sealed levels', tiers: ['plateau', 'mercury'] },
  { id: 'reg-s1-10', season: 1, title: 'Ночная смена терминала', speaker: 'Клемма', text: 'Терминальный ярус работает по расписанию, которое никто не составлял. Смены совпадают с отключениями — или наоборот.', source: 'ярус 3', titleEn: 'Terminal Night Shift', textEn: 'The Terminal Tier runs on a schedule nobody wrote. The shifts line up with the shutdowns, or the other way round.', sourceEn: 'tier 3', tiers: ['terminal'] },
  { id: 'reg-s1-11', season: 1, title: 'Диспетчерская', speaker: 'Хриза', text: 'Мне не нужен ваш ток, вольт-мастер. Мне нужно, чтобы линия шла через мой квартал. Ток дорисуем в отчёте.', source: 'уровень 31', titleEn: 'Dispatch Room', textEn: 'I do not need your current, volt-master. I need the line to run through my district. We will draw the current in the report.', sourceEn: 'level 31', tiers: ['terminal'] },
  { id: 'reg-s1-12', season: 1, title: 'Лишняя страница', speaker: 'Клемма', text: 'В архиве не хватает одной страницы. Нумерация сплошная, кроме одного места. Место совпадает с первой датой отключений.', source: '15 уровней яруса 3', titleEn: 'The Missing Page', textEn: 'The archive is one page short. The numbering is continuous except in one place. That place matches the first shutdown date.', sourceEn: 'all 15 levels of tier 3', tiers: ['terminal'] },
  { id: 'reg-s1-13', season: 1, title: 'Считалка', speaker: 'эфир', text: 'Раз, два — светлые дома. Три, четыре — нас не было в списке. Пять, шесть — в списке есть. Семь, восемь — список взвесили.', source: 'daily-челлендж', titleEn: 'Counting Rhyme', textEn: 'One, two, the bright houses. Three, four, we were not on the list. Five, six, now we are. Seven, eight, the list was weighed.', sourceEn: 'the daily challenge', tiers: ['plateau', 'mercury', 'terminal'] },
  { id: 'reg-s1-14', season: 1, title: 'Суд над светом', speaker: 'Крючники', text: 'Мы не против тока. Мы против возврата. Каждый включённый квартал — это вексель, который город подписал чужим именем.', source: 'уровень 46', titleEn: 'The Trial of Light', textEn: 'We are not against current. We are against return. Every district switched on is a note the city signed in another name.', sourceEn: 'level 46', tiers: ['old-diode'] },
  { id: 'reg-s1-15', season: 1, title: 'Дверь без ручки', speaker: 'Марта Вольт', text: 'Я в Старом Диоде. Здесь учат не проводить свет, а держать его. Если читаешь это — я уже держу слишком долго.', source: 'ярус 4, 5-й квартал', titleEn: 'Door Without a Handle', textEn: 'I am in the Old Diode. Here they teach you not to run light but to hold it. If you are reading this, I have been holding too long.', sourceEn: 'tier 4, 5th district', tiers: ['old-diode'] },
  { id: 'reg-s1-16', season: 1, title: 'Залитая ячейка', speaker: 'Клемма', text: 'Ячейка 22 залита стеклом по решению гильдии. Причина решения — в ячейке 22. Доступ к ячейке 22 — по решению гильдии.', source: 'ярус 4', titleEn: 'The Poured Cell', textEn: 'Cell 22 was poured with glass by guild decision. The reason for the decision is inside cell 22. Access to cell 22 requires a guild decision.', sourceEn: 'tier 4', tiers: ['old-diode'] },
  { id: 'reg-s1-17', season: 1, title: 'Доска имён', speaker: 'эфир', text: 'На доске выбиты первые вольт-мастера. Последние три строки закрашены. Краска свежая. Мастеров было больше.', source: 'ярус 4, 10-й квартал', titleEn: 'Board of Names', textEn: 'The board carries the first volt-masters. The last three lines are painted over. The paint is fresh. There were more masters.', sourceEn: 'tier 4, 10th district', tiers: ['old-diode'] },
  { id: 'reg-s1-18', season: 1, title: 'Отчёт за сезон', speaker: 'Хриза', text: 'Три квартала приняты. Премия — по курсу биржи на дату приёмки. Дата приёмки — сегодня. Курс — завтра.', source: 'ярус 4 завершён', titleEn: 'Season Report', textEn: 'Three districts accepted. The bonus follows the exchange rate on the acceptance date. The acceptance date is today. The rate is tomorrow.', sourceEn: 'tier 4 completed', tiers: ['old-diode'] },
  { id: 'reg-s1-19', season: 1, title: 'Направление отключений', speaker: 'Клемма', text: 'Сверила две записи. Отключение шло сверху вниз. Первым погас ярус, который отвечает за учёт. Это не совпадение, это порядок.', source: 'все 60 уровней ярусов 1–4', titleEn: 'Direction of Shutdowns', textEn: 'Cross-checked two records. The shutdown went from the top down. The first to go dark was the tier that keeps the accounts. Not a coincidence: procedure.', sourceEn: 'all 60 levels of tiers 1-4', tiers: ['plateau', 'mercury', 'terminal', 'old-diode'] },
  { id: 'reg-s1-20', season: 1, title: 'Первая запись сезона', speaker: 'Плавильня', text: 'Свет приходит с шагами. Считать шаги бесполезно. Считать шаги — нужно.', source: 'сезон 1, финальный день', titleEn: 'First Entry of the Season', textEn: 'Light arrives with footsteps. Counting the footsteps is useless. Counting the footsteps is required.', sourceEn: 'season 1, final day', tiers: ['melter'] },

  // Сезон 2 «Долг»
  { id: 'reg-s2-01', season: 2, title: 'Выработка', speaker: 'Крючники', text: 'Свет в жилах массива не природный. Его вырабатывали люди. Их дни записаны в ватт-часах, их имена — нигде.', source: 'сезон 2, любой уровень 61+', titleEn: 'Yield', textEn: 'The light in the veins of the array is not natural. People produced it. Their days are recorded in watt-hours; their names are nowhere.', sourceEn: 'season 2, any level 61+', tiers: ['melter'] },
  { id: 'reg-s2-02', season: 2, title: 'Госпиталь', speaker: 'Клемма', text: 'Госпиталь яруса 2 числится оплаченным. Способ оплаты: «выработка персонала». Персонала нет. Госпиталь подключён к сети.', source: 'ярус 2, повторное прохождение', titleEn: 'The Hospital', textEn: 'The tier 2 hospital is listed as paid. Method of payment: staff output. There is no staff. The hospital is connected to the grid.', sourceEn: 'tier 2, replay', tiers: ['mercury'] },
  { id: 'reg-s2-03', season: 2, title: 'Счёт за ток', speaker: 'Хриза', text: 'Долг — это не то, что вы должны. Долг — это то, что должны вам, но записано на меня. Учитесь разнице, пока есть кому учить.', source: 'уровень 62', titleEn: 'The Current Bill', textEn: 'A debt is not what you owe. A debt is what you are owed, written down against me. Learn the difference while there is still someone to teach.', sourceEn: 'level 62', tiers: ['melter'] },
  { id: 'reg-s2-04', season: 2, title: 'Подпольный реестр', speaker: 'Крючники', text: 'Мы ведём вторую копию реестра. Первая — наверху. Вторая — в стене диспетчерской. Стена отсырела, но цифры целы.', source: 'daily-стрик 3 дня', titleEn: 'Underground Registry', textEn: 'We keep a second copy of the registry. The first is upstairs. The second is in the wall of the dispatch room. The wall is damp; the figures are intact.', sourceEn: 'daily streak of 3', tiers: ['terminal'] },
  { id: 'reg-s2-05', season: 2, title: 'Радиопомехи', speaker: 'Марта Вольт', text: 'Помехи идут не от передатчика. Идут из массива. Если слышишь помехи ровно ритмом — это не помехи.', source: 'все daily сезона 2', titleEn: 'Radio Noise', textEn: 'The noise does not come from a transmitter. It comes from the array. If the noise has a steady rhythm, it is not noise.', sourceEn: 'every daily of season 2', tiers: ['melter'] },
  { id: 'reg-s2-06', season: 2, title: 'Оплата днями', speaker: 'эфир', text: 'Обменный пункт: один включённый квартал — двадцать четыре часа одного человека. Курс не менялся. Пункт закрыт из-за курса.', source: 'сезон 2, 5-й день', titleEn: 'Paid in Days', textEn: 'Exchange point: one district switched on equals twenty-four hours of one person. The rate never changed. The point closed because of the rate.', sourceEn: 'season 2, day 5', tiers: ['terminal'] },
  { id: 'reg-s2-07', season: 2, title: 'Пломба без имени', speaker: 'Клемма', text: 'Найдена пломба 197-го квартала. Квартал не существует. Пломба подписана. Подпись стёрта, но свежая.', source: 'ярус 3 без подсказок', titleEn: 'Seal Without a Name', textEn: 'A seal of district 197 was found. District 197 does not exist. The seal is signed. The signature is rubbed out but fresh.', sourceEn: 'tier 3 without hints', tiers: ['terminal'] },
  { id: 'reg-s2-08', season: 2, title: 'Стекло и память', speaker: 'эфир', text: 'Старый Диод залит стеклом там, где помнит. Стекло держит свет, а свет держит запись. Отсюда правило: не вскрывать.', source: 'ярус 4', titleEn: 'Glass and Memory', textEn: 'The Old Diode is poured with glass where it remembers. The glass holds the light and the light holds the record. Hence the rule: do not open.', sourceEn: 'tier 4', tiers: ['old-diode'] },
  { id: 'reg-s2-09', season: 2, title: 'Смена курса', speaker: 'Хриза', text: 'Курс биржи на сегодня — молчание. Утром был свет. К вечеру курс вырос. Информация дороже тока, в этом и смысл биржи.', source: 'уровень 63', titleEn: 'Change of Rate', textEn: 'Today the exchange rate is silence. In the morning there was light. By evening the rate climbed. Information costs more than current; that is the point of an exchange.', sourceEn: 'level 63', tiers: ['melter'] },
  { id: 'reg-s2-10', season: 2, title: 'Класс без учителя', speaker: 'Марта Вольт', text: 'В классе 46-го яруса стоит парта. За партой никого. Мел лежит так, будто им только что писали. Пишите ровно.', source: 'уровень 46 пройден дважды', titleEn: 'Class Without a Teacher', textEn: 'There is a desk in the tier 46 classroom. Nobody sits at it. The chalk lies as if it was just used. Write evenly.', sourceEn: 'level 46 solved twice', tiers: ['old-diode'] },
  { id: 'reg-s2-11', season: 2, title: 'Долг гильдии', speaker: 'Клемма', text: 'Гильдия должна массиву около миллиарда циклов. Массив должен гильдии одного человека. Меньшая сумма важнее.', source: 'сезон 2, 10-й день', titleEn: 'The Guild Debt', textEn: 'The guild owes the array about a billion cycles. The array owes the guild one person. The smaller sum matters more.', sourceEn: 'season 2, day 10', tiers: ['melter'] },
  { id: 'reg-s2-12', season: 2, title: 'Стенограмма', speaker: 'Крючники', text: 'В диспетчерской стенограмма заседания. Кто-то сказал: «сверху вниз, чтобы не успели посчитать». Стенограмма обрывается.', source: 'ярус 2, секретный квартал', titleEn: 'Transcript', textEn: 'A meeting transcript sits in the dispatch room. Someone said: from the top down, so nobody has time to count. The transcript breaks off there.', sourceEn: 'tier 2, secret district', tiers: ['mercury'] },
  { id: 'reg-s2-13', season: 2, title: 'Ритм массива', speaker: 'Плавильня', text: 'Ваш пульс совпадает с ритмом массива. Это не тревожный признак. Это приглашение.', source: 'сезон 2, финальная неделя', titleEn: 'Rhythm of the Array', textEn: 'Your pulse matches the rhythm of the array. This is not a warning sign. It is an invitation.', sourceEn: 'season 2, final week', tiers: ['melter'] },
  { id: 'reg-s2-14', season: 2, title: 'Оправдание', speaker: 'Хриза', text: 'Я не гасила город. Я гасила счета. Разница в том, кому вести учёт после.', source: 'сезон 2, стрик 7 дней', titleEn: 'Justification', textEn: 'I did not shut down the city. I shut down the accounts. The difference is who keeps the books afterwards.', sourceEn: 'season 2, streak of 7', tiers: ['melter'] },
  { id: 'reg-s2-15', season: 2, title: 'Полная смета', speaker: 'Клемма', text: 'Смета восстановлена. Всего отключено 214 кварталов. Из них жилых — 11. Остальные: память.', source: 'сезон 2, 15-й день', titleEn: 'Full Estimate', textEn: 'The estimate is restored. 214 districts were shut down in total. Eleven were residential. The rest were memory.', sourceEn: 'season 2, day 15', tiers: ['melter'] },
  { id: 'reg-s2-16', season: 2, title: 'Носилки', speaker: 'эфир', text: 'В порту стоит гроб, оформленный как груз. В декларации: «оборудование, возврату не подлежит». Порт закрыт.', source: 'ярус 3, ночная смена', titleEn: 'The Cargo', textEn: 'A coffin stands in the port, declared as cargo. The manifest says: equipment, not returnable. The port is closed.', sourceEn: 'tier 3, night shift', tiers: ['terminal'] },
  { id: 'reg-s2-17', season: 2, title: 'Молчание биржи', speaker: 'Хриза', text: 'Сегодня торги не открылись. Все стороны согласились: пока не будет света, курс определять не на что.', source: 'сезон 2, любой daily подряд 3 дня', titleEn: 'The Exchange Is Silent', textEn: 'Trading did not open today. All sides agreed: with no light there is nothing to set a rate against.', sourceEn: 'any 3 dailies in a row', tiers: ['mercury', 'terminal'] },
  { id: 'reg-s2-18', season: 2, title: 'Второй ключ', speaker: 'Крючники', text: 'У гильдии Ключа два ключа. Один от двери. Второй — от решения не открывать дверь.', source: 'ярус 4, без подсказок', titleEn: 'The Second Key', textEn: 'The Key Guild holds two keys. One opens the door. The second is for the decision not to open it.', sourceEn: 'tier 4, without hints', tiers: ['old-diode'] },
  { id: 'reg-s2-19', season: 2, title: 'Список павших', speaker: 'Клемма', text: 'Список павших вольт-мастеров восстановлен. Ваше имя — последняя строка. Дата рядом с ней пустая.', source: 'сезон 2 завершён', titleEn: 'List of the Fallen', textEn: 'The list of fallen volt-masters is restored. Your name is the last line. The date beside it is empty.', sourceEn: 'season 2 completed', tiers: ['melter'] },
  { id: 'reg-s2-20', season: 2, title: 'Приглашение вниз', speaker: 'Плавильня', text: 'Осталось три подъёма. Каждый следующий требует больше. Это не угроза, это тариф.', source: 'сезон 2, финальный день', titleEn: 'Invitation Down', textEn: 'Three ascents remain. Each next one costs more. This is not a threat, it is a tariff.', sourceEn: 'season 2, final day', tiers: ['melter'] },

  // Сезон 3 «Плавильня»
  { id: 'reg-s3-01', season: 3, title: 'Дно массива', speaker: 'эфир', text: 'На дне нет темноты. Есть свет, который никуда не идёт. Он ждёт проводника.', source: 'сезон 3, уровень 61', titleEn: 'Bottom of the Array', textEn: 'There is no darkness at the bottom. There is light that goes nowhere. It is waiting for a conductor.', sourceEn: 'season 3, level 61', tiers: ['melter'] },
  { id: 'reg-s3-02', season: 3, title: 'Жила номер семь', speaker: 'Клемма', text: 'Жила № 7 обнаружена. Проектная документация: «не существует». Текущее состояние: работает.', source: 'ярус 5, 3-й квартал', titleEn: 'Vein Number Seven', textEn: 'Vein No. 7 located. Design documentation: does not exist. Current state: operating.', sourceEn: 'tier 5, 3rd district', tiers: ['melter'] },
  { id: 'reg-s3-03', season: 3, title: 'Полное включение', speaker: 'Плавильня', text: 'Полное включение вернёт свет. Память останется в жилах. Город будет жить, но не вспомнит, чем платил.', source: 'сезон 3, уровень 65', titleEn: 'Full Power', textEn: 'Full power will bring the light back. The memory stays in the veins. The city will live and will not remember what it paid with.', sourceEn: 'season 3, level 65', tiers: ['melter'] },
  { id: 'reg-s3-04', season: 3, title: 'Полумрак', speaker: 'Марта Вольт', text: 'Полумрак — это не трусость. Это договор: город живёт, память спит, никто никому не должен. Пока.', source: 'сезон 3, уровень 66', titleEn: 'Half-Light', textEn: 'Half-light is not cowardice. It is an agreement: the city lives, the memory sleeps, nobody owes anybody. For now.', sourceEn: 'season 3, level 66', tiers: ['melter'] },
  { id: 'reg-s3-05', season: 3, title: 'Крючникам', speaker: 'Крючники', text: 'Отдайте ток нам — город уснёт. Уснёт не как отключённый, а как уставший. Память уцелеет. Свет подождёт.', source: 'сезон 3, уровень 67', titleEn: 'To the Hookmen', textEn: 'Give us the current and the city sleeps. Not the sleep of a shutdown, the sleep of the tired. The memory survives. The light can wait.', sourceEn: 'season 3, level 67', tiers: ['melter'] },
  { id: 'reg-s3-06', season: 3, title: 'Считалка на дне', speaker: 'эфир', text: 'Раз, два — мы вас ждали. Три, четыре — мы вас знали. Пять, шесть — вас не счесть. Семь, восемь — вас в списке нет.', source: 'сезон 3, 5-й день', titleEn: 'Rhyme at the Bottom', textEn: 'One, two, we waited for you. Three, four, we knew you. Five, six, you cannot be counted. Seven, eight, you are not on the list.', sourceEn: 'season 3, day 5', tiers: ['melter'] },
  { id: 'reg-s3-07', season: 3, title: 'Страница найдена', speaker: 'Клемма', text: 'Пропавшая страница обнаружена в ячейке 22. Содержание: список тех, кто голосовал «за». Первая строка — подпись гильдии.', source: 'ярус 4, повтор', titleEn: 'The Page Found', textEn: 'The missing page was found in cell 22. Contents: the list of those who voted yes. The first line is the guild signature.', sourceEn: 'tier 4, replay', tiers: ['old-diode'] },
  { id: 'reg-s3-08', season: 3, title: 'Стекло треснуло', speaker: 'эфир', text: 'Стекло в зале 46-го яруса треснуло без причины. Под трещиной — тёплый контакт. Причина найдена. Причина была тёплой.', source: 'ярус 4, 8-й квартал', titleEn: 'The Glass Cracked', textEn: 'The glass in the tier 46 hall cracked for no reason. Under the crack there is a warm contact. The reason is found. The reason was warm.', sourceEn: 'tier 4, 8th district', tiers: ['old-diode'] },
  { id: 'reg-s3-09', season: 3, title: 'Три подъёма', speaker: 'Плавильня', text: 'Первый подъём — тело. Второй — память. Третий — выбор. Четвёртого не бывает, и это тоже тариф.', source: 'сезон 3, уровень 70', titleEn: 'Three Ascents', textEn: 'The first ascent is the body. The second is the memory. The third is the choice. There is no fourth, and that too is a tariff.', sourceEn: 'season 3, level 70', tiers: ['melter'] },
  { id: 'reg-s3-10', season: 3, title: 'Ядро слушает', speaker: 'Клемма', text: 'Позиция 1.0: свет выдаётся по норме. Норма пересмотрена. Приложение: вы. Подпись: отсутствует.', source: 'сезон 3, 10-й день', titleEn: 'The Core Listens', textEn: 'Clause 1.0: light is issued by quota. The quota has been revised. Appendix: you. Signature: missing.', sourceEn: 'season 3, day 10', tiers: ['melter'] },
  { id: 'reg-s3-11', season: 3, title: 'Отказ', speaker: 'Хриза', text: 'Я отказываюсь участвовать в финале. Финал — это учёт без меня. Учёт без меня — уже не биржа.', source: 'сезон 3, 11-й день', titleEn: 'Refusal', textEn: 'I decline to take part in the finale. A finale is bookkeeping without me. Bookkeeping without me is no longer an exchange.', sourceEn: 'season 3, day 11', tiers: ['melter'] },
  { id: 'reg-s3-12', season: 3, title: 'Именная доска', speaker: 'Марта Вольт', text: 'На доске закрашены три строки. Я стёрла краску. Это были мои ученики. Теперь они есть.', source: 'ярус 4, без подсказок', titleEn: 'Board of Names, Repainted', textEn: 'Three lines on the board were painted over. I rubbed the paint off. They were my students. Now they are here.', sourceEn: 'tier 4, without hints', tiers: ['old-diode'] },
  { id: 'reg-s3-13', season: 3, title: 'Первое дыхание', speaker: 'эфир', text: 'Когда массив вдохнул первый раз, реклама на Плато выключилась сама. Больше она не включалась.', source: 'сезон 3, уровень 72', titleEn: 'First Breath', textEn: 'When the array drew its first breath, the billboards on the Plateau switched off by themselves. They never came back on.', sourceEn: 'season 3, level 72', tiers: ['melter'] },
  { id: 'reg-s3-14', season: 3, title: 'Обратный счёт', speaker: 'Клемма', text: 'Если вы читаете это, пломба поставлена, а счётчик не остановлен. Счётчик массива не останавливается никогда.', source: 'сезон 3, стрик 14 дней', titleEn: 'Countdown', textEn: 'If you are reading this, the seal is set and the counter has not stopped. The counter of the array never stops.', sourceEn: 'season 3, streak of 14', tiers: ['melter'] },
  { id: 'reg-s3-15', season: 3, title: 'Паспорт практиканта', speaker: 'эфир', text: 'Найдены документы последнего практиканта гильдии. Отметка о сдаче практики: пусто. Отметка о взыскании: пусто.', source: 'сезон 3, 15-й день', titleEn: 'Apprentice Papers', textEn: 'Papers of the last guild apprentice were found. Mark for completed practice: blank. Mark for penalties: blank.', sourceEn: 'season 3, day 15', tiers: ['melter'] },
  { id: 'reg-s3-16', season: 3, title: 'Долг возвращён', speaker: 'Крючники', text: 'Мы не просим свет. Мы просим запись. Запись — тоже валюта, просто её не принимают в бирже.', source: 'сезон 3, 16-й день', titleEn: 'The Debt Repaid', textEn: 'We are not asking for light. We are asking for the record. A record is currency too, it is simply not accepted at the exchange.', sourceEn: 'season 3, day 16', tiers: ['melter'] },
  { id: 'reg-s3-17', season: 3, title: 'Жилы говорят', speaker: 'Марта Вольт', text: 'Жилы отвечают на ток не сразу. Между импульсом и ответом есть тишина длиной в чей-то день. Кто-то этим днём поработал.', source: 'сезон 3, уровень 73', titleEn: 'The Veins Answer', textEn: 'The veins answer the current late. Between the pulse and the answer sits a silence one day long. Somebody worked that day.', sourceEn: 'season 3, level 73', tiers: ['melter'] },
  { id: 'reg-s3-18', season: 3, title: 'Смета без строк', speaker: 'Клемма', text: 'Смета сезона три состоит из одной строки: «вернуть». Позиция без цифры считается бессрочной.', source: 'сезон 3 завершён', titleEn: 'Estimate Without Lines', textEn: 'The season three estimate consists of one line: return. A position with no figure is considered open-ended.', sourceEn: 'season 3 completed', tiers: ['melter'] },
  { id: 'reg-s3-19', season: 3, title: 'Последний контур', speaker: 'эфир', text: 'Последний контур города — не кабель. Это решение. Оно лежит на дне, и его нельзя замкнуть за игрока.', source: 'все 75 уровней', titleEn: 'The Last Circuit', textEn: 'The last circuit of the city is not a cable. It is a decision. It lies at the bottom and it cannot be closed for the player.', sourceEn: 'all 75 levels', tiers: ['plateau', 'mercury', 'terminal', 'old-diode', 'melter'] },
  { id: 'reg-s3-20', season: 3, title: 'Запись без даты', speaker: 'Плавильня', text: 'Свет вернулся. Память осталась. Выбор сделан кем-то другим, и он был верным. Впишите дату сами.', source: 'сезон 3, финальный день', titleEn: 'Entry Without a Date', textEn: 'The light is back. The memory stayed. The choice was made by someone else and it was the right one. Write the date yourself.', sourceEn: 'season 3, final day', tiers: ['melter'] },
];

/** Ярус, которому принадлежит уровень. */
export function tierForLevel(level: number): Tier | undefined {
  return TIERS.find((t) => level >= t.levels[0] && level <= t.levels[1]);
}

/** Квартал уровня. */
export function quarterForLevel(level: number): Quarter | undefined {
  return QUARTERS.find((q) => q.level === level);
}

/** Запись реестра по идентификатору (он же используется в минте). */
export function registryById(id: string): RegistryEntry | undefined {
  return REGISTRY.find((r) => r.id === id);
}

/** Запись реестра за уровень: страницы выдаются по порядку, от первой до последней.
 *  За 75 уровней игрок собирает весь реестр (60 записей) без покупок. */
export function registryForLevel(level: number): RegistryEntry | undefined {
  if (!tierForLevel(level)) return undefined;
  const index = Math.floor(((level - 1) * REGISTRY.length) / TOTAL_LEVELS);
  return REGISTRY[Math.min(index, REGISTRY.length - 1)];
}
