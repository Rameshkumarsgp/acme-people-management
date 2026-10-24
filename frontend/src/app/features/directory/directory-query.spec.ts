import { convertToParamMap } from '@angular/router';
import { DEFAULT_DIRECTORY_QUERY, DirectoryQuery } from '../../core/employees/employee-api';
import {
  readQuery,
  sortDirection,
  sortField,
  toQueryParams,
  withFilter,
  withPage,
} from './directory-query';

const FULL: DirectoryQuery = {
  q: 'rao',
  countryCode: 'IN',
  departmentId: 3,
  jobTitleId: 12,
  currency: 'INR',
  status: 'ALL',
  sort: 'salary,desc',
  page: 2,
  size: 50,
};

describe('readQuery', () => {
  it('gives the defaults when the address has no parameters', () => {
    expect(readQuery(convertToParamMap({}))).toEqual(DEFAULT_DIRECTORY_QUERY);
  });

  it('reads every value from the address', () => {
    const params = convertToParamMap({
      q: 'rao',
      countryCode: 'IN',
      departmentId: '3',
      jobTitleId: '12',
      currency: 'INR',
      status: 'ALL',
      sort: 'salary,desc',
      page: '2',
      size: '50',
    });

    expect(readQuery(params)).toEqual(FULL);
  });

  it('ignores ids, pages and sizes that are not whole numbers or not allowed', () => {
    const params = convertToParamMap({
      departmentId: '0',
      jobTitleId: '0',
      page: '-1',
      size: '7',
    });

    expect(readQuery(params)).toEqual(DEFAULT_DIRECTORY_QUERY);
  });

  it('ignores an id that is not a whole number', () => {
    const params = convertToParamMap({ departmentId: 'x', jobTitleId: '1.5' });

    expect(readQuery(params)).toEqual(DEFAULT_DIRECTORY_QUERY);
  });

  it('ignores a status that is not one of the three', () => {
    expect(readQuery(convertToParamMap({ status: 'retired' })).status).toBe('ACTIVE');
  });

  it('ignores a sort field or direction the server does not know', () => {
    expect(readQuery(convertToParamMap({ sort: 'password,asc' })).sort).toBe('fullName,asc');
    expect(readQuery(convertToParamMap({ sort: 'hireDate,sideways' })).sort).toBe('fullName,asc');
  });

  it('reads a sort without a direction as ascending', () => {
    expect(readQuery(convertToParamMap({ sort: 'hireDate' })).sort).toBe('hireDate,asc');
  });

  it('falls back to the default sort for salary without a currency, which the server would refuse', () => {
    expect(readQuery(convertToParamMap({ sort: 'salary,desc' })).sort).toBe('fullName,asc');
    expect(readQuery(convertToParamMap({ sort: 'salary,desc', currency: 'INR' })).sort).toBe(
      'salary,desc',
    );
  });
});

describe('toQueryParams', () => {
  it('leaves out every value that is the default', () => {
    expect(toQueryParams(DEFAULT_DIRECTORY_QUERY)).toEqual({});
  });

  it('writes every value that is not the default', () => {
    expect(toQueryParams(FULL)).toEqual({
      q: 'rao',
      countryCode: 'IN',
      departmentId: 3,
      jobTitleId: 12,
      currency: 'INR',
      status: 'ALL',
      sort: 'salary,desc',
      page: 2,
      size: 50,
    });
  });

  it('reads back what it wrote', () => {
    const written = toQueryParams(FULL);
    const asStrings = Object.fromEntries(Object.entries(written).map(([k, v]) => [k, String(v)]));

    expect(readQuery(convertToParamMap(asStrings))).toEqual(FULL);
  });
});

describe('withFilter', () => {
  it('sets the filter and goes back to the first page', () => {
    const changed = withFilter({ ...DEFAULT_DIRECTORY_QUERY, page: 3 }, { countryCode: 'IN' });

    expect(changed.countryCode).toBe('IN');
    expect(changed.page).toBe(0);
  });

  it('goes back to the first page when the sort changes', () => {
    expect(
      withFilter({ ...DEFAULT_DIRECTORY_QUERY, page: 3 }, { sort: 'hireDate,desc' }).page,
    ).toBe(0);
  });

  it('drops a salary sort when the currency is cleared', () => {
    const changed = withFilter(FULL, { currency: '' });

    expect(changed.currency).toBe('');
    expect(changed.sort).toBe('fullName,asc');
  });

  it('keeps a salary sort when another filter changes', () => {
    expect(withFilter(FULL, { countryCode: 'US' }).sort).toBe('salary,desc');
  });
});

describe('withPage', () => {
  it('moves to the page and keeps the size', () => {
    expect(withPage(FULL, 4, 50)).toEqual({ ...FULL, page: 4 });
  });

  it('goes back to the first page when the size changes', () => {
    expect(withPage(FULL, 4, 100)).toEqual({ ...FULL, page: 0, size: 100 });
  });
});

describe('sortField and sortDirection', () => {
  it('splits a sort into its field and direction', () => {
    expect(sortField('salary,desc')).toBe('salary');
    expect(sortDirection('salary,desc')).toBe('desc');
    expect(sortDirection('fullName,asc')).toBe('asc');
  });
});
