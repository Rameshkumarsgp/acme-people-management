import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { AmountError, parseMajorToMinor } from '../../shared/money/parse-major-to-minor';

/**
 * Checks the typed salary against the decimals of the chosen currency. An empty field is left to the
 * required validator, and nothing is judged until a currency is chosen.
 */
export function salaryAmountValidator(minorUnitDigits: () => number | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const text = String(control.value ?? '');
    const digits = minorUnitDigits();
    if (text.trim() === '' || digits === null) {
      return null;
    }
    const parsed = parseMajorToMinor(text, digits);
    return parsed.ok ? null : { amount: parsed.reason };
  };
}

export function describeAmountError(reason: AmountError, minorUnitDigits: number | null): string {
  switch (reason) {
    case 'not-a-number':
      return 'Enter an amount such as 1,250,000.50.';
    case 'too-many-decimals':
      return minorUnitDigits === 0
        ? 'This currency has no decimal places.'
        : `Use at most ${minorUnitDigits} decimal places.`;
    case 'not-positive':
      return 'The amount must be greater than zero.';
    case 'too-large':
      return 'The amount is too large.';
  }
}
