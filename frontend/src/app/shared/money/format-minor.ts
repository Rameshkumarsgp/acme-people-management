/**
 * Turns integer minor units into text such as "1,250,000.50". It splits the digits as text instead of
 * dividing, so a large amount is never rounded by floating point.
 */
export function formatMinor(amountMinor: number, minorUnitDigits: number): string {
  const digits = String(amountMinor).padStart(minorUnitDigits + 1, '0');
  const splitAt = digits.length - minorUnitDigits;
  const whole = digits.slice(0, splitAt).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return minorUnitDigits === 0 ? whole : `${whole}.${digits.slice(splitAt)}`;
}
