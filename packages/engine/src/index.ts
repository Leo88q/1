export * from './prng';
export * from './tiles';
export * from './level';

/** Версия правил. Меняется при любом изменении генератора: старые уровни
 *  перестают совпадать с новыми, поэтому версия уходит в метаданные и в DAS. */
export const ENGINE_VERSION = '0.1.0';
