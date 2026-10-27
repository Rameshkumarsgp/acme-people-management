import { formatMinor } from './format-minor';

describe('formatMinor', () => {
  it('groups thousands and shows the decimals of the currency', () => {
    expect(formatMinor(125000050, 2)).toBe('1,250,000.50');
  });

  it('pads amounts below one major unit', () => {
    expect(formatMinor(5, 2)).toBe('0.05');
    expect(formatMinor(0, 2)).toBe('0.00');
  });

  it('shows no decimals for a currency without minor units', () => {
    expect(formatMinor(125000, 0)).toBe('125,000');
  });

  it('handles a currency with three decimals', () => {
    expect(formatMinor(1005, 3)).toBe('1.005');
  });

  it('shows the largest exact amount without rounding it', () => {
    expect(formatMinor(9007199254740991, 2)).toBe('90,071,992,547,409.91');
  });

  it('formats a total beyond the largest exact number from its digits, without rounding', () => {
    expect(formatMinor('18014398509481983', 2)).toBe('180,143,985,094,819.83');
  });
});
