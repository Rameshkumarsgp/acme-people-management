const MAX_AMOUNT_MINOR = 9007199254740991n;

/** Digits with optional thousands groups, then an optional fraction. No sign, no exponent. */
const AMOUNT_PATTERN = /^(\d{1,3}(,\d{3})+|\d+)(\.\d+)?$/;

export type AmountError = 'not-a-number' | 'too-many-decimals' | 'not-positive' | 'too-large';

export type ParsedAmount = { ok: true; amountMinor: number } | { ok: false; reason: AmountError };

/**
 * Turns what HR typed ("1,250,000.50") into integer minor units. It works on the digits as text and
 * never multiplies a float, so the result is exact. The largest amount is the largest integer a
 * JavaScript number holds exactly, which is also the limit the API enforces.
 */
export function parseMajorToMinor(text: string, minorUnitDigits: number): ParsedAmount {
  const trimmed = text.trim();
  if (!AMOUNT_PATTERN.test(trimmed)) {
    return { ok: false, reason: 'not-a-number' };
  }
  const [whole, fraction = ''] = trimmed.replaceAll(',', '').split('.');
  if (fraction.length > minorUnitDigits) {
    return { ok: false, reason: 'too-many-decimals' };
  }
  const amountMinor = BigInt(whole + fraction.padEnd(minorUnitDigits, '0'));
  if (amountMinor <= 0n) {
    return { ok: false, reason: 'not-positive' };
  }
  if (amountMinor > MAX_AMOUNT_MINOR) {
    return { ok: false, reason: 'too-large' };
  }
  return { ok: true, amountMinor: Number(amountMinor) };
}
