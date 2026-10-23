import { FormControl } from '@angular/forms';
import { describeAmountError, salaryAmountValidator } from './salary-amount.validator';

describe('salaryAmountValidator', () => {
  function check(text: string, digits: number | null) {
    return salaryAmountValidator(() => digits)(new FormControl(text));
  }

  it('accepts an amount that fits the currency', () => {
    expect(check('1,250,000.50', 2)).toBeNull();
  });

  it('leaves an empty field to the required validator', () => {
    expect(check('', 2)).toBeNull();
  });

  it('waits for a currency before judging the decimals', () => {
    expect(check('10.505', null)).toBeNull();
  });

  it('reports why an amount is not valid', () => {
    expect(check('10.505', 2)).toEqual({ amount: 'too-many-decimals' });
    expect(check('abc', 2)).toEqual({ amount: 'not-a-number' });
  });
});

describe('describeAmountError', () => {
  it('names the decimal places a currency allows', () => {
    expect(describeAmountError('too-many-decimals', 2)).toBe('Use at most 2 decimal places.');
  });

  it('says when a currency has no decimal places', () => {
    expect(describeAmountError('too-many-decimals', 0)).toBe(
      'This currency has no decimal places.',
    );
  });

  it('explains each of the other reasons', () => {
    expect(describeAmountError('not-a-number', 2)).toBe('Enter an amount such as 1,250,000.50.');
    expect(describeAmountError('not-positive', 2)).toBe('The amount must be greater than zero.');
    expect(describeAmountError('too-large', 2)).toBe('The amount is too large.');
  });
});
