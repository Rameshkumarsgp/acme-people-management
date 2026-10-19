import { parseMajorToMinor } from './parse-major-to-minor';

describe('parseMajorToMinor', () => {
  it('converts a two-decimal amount to minor units exactly', () => {
    expect(parseMajorToMinor('1250000.50', 2)).toEqual({ ok: true, amountMinor: 125000050 });
  });

  it('accepts thousands separators', () => {
    expect(parseMajorToMinor('1,250,000.50', 2)).toEqual({ ok: true, amountMinor: 125000050 });
  });

  it('adds the missing decimals', () => {
    expect(parseMajorToMinor('1250000', 2)).toEqual({ ok: true, amountMinor: 125000000 });
    expect(parseMajorToMinor('10.5', 2)).toEqual({ ok: true, amountMinor: 1050 });
  });

  it('is exact where multiplying a float is not', () => {
    // 8.2 * 100 is 819.9999999999999 and 0.29 * 100 is 28.999999999999996 in floating point.
    expect(parseMajorToMinor('8.2', 2)).toEqual({ ok: true, amountMinor: 820 });
    expect(parseMajorToMinor('0.29', 2)).toEqual({ ok: true, amountMinor: 29 });
  });

  it('handles a currency with three decimals', () => {
    expect(parseMajorToMinor('1.005', 3)).toEqual({ ok: true, amountMinor: 1005 });
  });

  it('needs no decimals for a currency without minor units, such as JPY', () => {
    expect(parseMajorToMinor('125,000', 0)).toEqual({ ok: true, amountMinor: 125000 });
  });

  it('rejects any decimals for a currency without minor units', () => {
    expect(parseMajorToMinor('10.5', 0)).toEqual({ ok: false, reason: 'too-many-decimals' });
    expect(parseMajorToMinor('10.0', 0)).toEqual({ ok: false, reason: 'too-many-decimals' });
  });

  it('rejects more decimals than the currency allows', () => {
    expect(parseMajorToMinor('10.505', 2)).toEqual({ ok: false, reason: 'too-many-decimals' });
  });

  it.each(['', '  ', 'abc', '1,25', '1.', '.5', '-5', '1e3', '1 000'])(
    'rejects "%s" as not an amount',
    (text) => {
      expect(parseMajorToMinor(text, 2)).toEqual({ ok: false, reason: 'not-a-number' });
    },
  );

  it('rejects zero', () => {
    expect(parseMajorToMinor('0.00', 2)).toEqual({ ok: false, reason: 'not-positive' });
  });

  it('accepts the largest amount a JavaScript number holds exactly and rejects one more', () => {
    expect(parseMajorToMinor('90071992547409.91', 2)).toEqual({
      ok: true,
      amountMinor: 9007199254740991,
    });
    expect(parseMajorToMinor('90071992547409.92', 2)).toEqual({ ok: false, reason: 'too-large' });
  });
});
