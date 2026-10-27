import { formatMinor } from './format-minor';

interface CurrencyDigits {
  code: string;
  minorUnitDigits: number;
}

/** An amount with its currency code, such as "1,250,000.50 INR". Without the currency's digits it shows the raw amount. */
export function formatMoney(
  amountMinor: number | string,
  currencyCode: string,
  currencies: readonly CurrencyDigits[] | undefined,
): string {
  const currency = currencies?.find((candidate) => candidate.code === currencyCode);
  return currency
    ? `${formatMinor(amountMinor, currency.minorUnitDigits)} ${currency.code}`
    : `${amountMinor} ${currencyCode}`;
}
