/**
 * Канонические сообщения, которые подписывает кошелёк.
 * Формируются только сервером и передаются клиенту целиком — так тексты
 * не рассинхронизируются между Kotlin, вебом и сервером.
 */

export function claimMessage(params: {
  address: string;
  rewardId: string;
  nonce: string;
  issuedAt: Date;
}): string {
  return [
    'АМПЕРГРАД · реестр города',
    `Кошелёк: ${params.address}`,
    `Награда: ${params.rewardId}`,
    `Задача: ${params.nonce}`,
    `Выдано: ${params.issuedAt.toISOString()}`,
    'Подпись подтверждает владение кошельком и ничего не списывает.',
  ].join('\n');
}

export function dailyMessage(params: {
  address: string;
  day: number;
  moves: number;
  nonce: string;
}): string {
  return [
    'АМПЕРГРАД · челлендж дня',
    `Кошелёк: ${params.address}`,
    `День: ${params.day}`,
    `Ходы: ${params.moves}`,
    `Задача: ${params.nonce}`,
    'Подпись подтверждает результат и ничего не списывает.',
  ].join('\n');
}

export function randomNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}
