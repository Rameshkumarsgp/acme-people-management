import { formatMoney } from './format-money';

const CURRENCIES = [
  { code: 'INR', minorUnitDigits: 2 },
  { code: 'JPY', minorUnitDigits: 0 },
];

describe('formatMoney', () => {
  it('shows the amount with the decimals of its currency and the currency code', () => {
    expect(formatMoney(125000050, 'INR', CURRENCIES)).toBe('1,250,000.50 INR');
    expect(formatMoney(125000, 'JPY', CURRENCIES)).toBe('125,000 JPY');
  });

  it('keeps the digits of a total given as text', () => {
    expect(formatMoney('18014398509481983', 'INR', CURRENCIES)).toBe('180,143,985,094,819.83 INR');
  });

  it('shows the raw amount and code while the currencies are not loaded or the code is unknown', () => {
    expect(formatMoney(125000050, 'INR', undefined)).toBe('125000050 INR');
    expect(formatMoney(5, 'XXX', CURRENCIES)).toBe('5 XXX');
  });
});
