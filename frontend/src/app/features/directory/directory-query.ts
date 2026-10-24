import { ParamMap, Params } from '@angular/router';
import {
  DEFAULT_DIRECTORY_QUERY,
  DirectoryQuery,
  DirectoryStatus,
} from '../../core/employees/employee-api';

export const PAGE_SIZES = [10, 20, 50, 100];

const STATUSES: DirectoryStatus[] = ['ACTIVE', 'FORMER', 'ALL'];
const SORT_FIELDS = ['fullName', 'department', 'jobTitle', 'country', 'hireDate', 'salary'];

/** Reads the directory's state from the address. Anything the server would refuse falls back to the default. */
export function readQuery(params: ParamMap): DirectoryQuery {
  const defaults = DEFAULT_DIRECTORY_QUERY;
  const currency = params.get('currency') ?? '';
  return {
    q: params.get('q') ?? '',
    countryCode: params.get('countryCode') ?? '',
    departmentId: wholeNumber(params.get('departmentId'), 1),
    jobTitleId: wholeNumber(params.get('jobTitleId'), 1),
    currency,
    status: STATUSES.find((status) => status === params.get('status')) ?? defaults.status,
    sort: readSort(params.get('sort'), currency),
    page: wholeNumber(params.get('page'), 0) ?? defaults.page,
    size: PAGE_SIZES.find((size) => size === Number(params.get('size'))) ?? defaults.size,
  };
}

/** Writes the state as address parameters, leaving out what is the default so the address stays short. */
export function toQueryParams(query: DirectoryQuery): Params {
  const defaults = DEFAULT_DIRECTORY_QUERY;
  const params: Params = {};
  for (const key of Object.keys(defaults) as (keyof DirectoryQuery)[]) {
    if (query[key] !== defaults[key]) {
      params[key] = query[key];
    }
  }
  return params;
}

/** A filter or sort change shows the first page again. A salary sort needs a currency, so it goes with it. */
export function withFilter(query: DirectoryQuery, change: Partial<DirectoryQuery>): DirectoryQuery {
  const changed = { ...query, ...change, page: 0 };
  return changed.currency === '' && sortField(changed.sort) === 'salary'
    ? { ...changed, sort: DEFAULT_DIRECTORY_QUERY.sort }
    : changed;
}

export function withPage(query: DirectoryQuery, page: number, size: number): DirectoryQuery {
  return size === query.size ? { ...query, page } : { ...query, page: 0, size };
}

export function sortField(sort: string): string {
  return sort.split(',')[0];
}

export function sortDirection(sort: string): 'asc' | 'desc' {
  return sort.split(',')[1] === 'desc' ? 'desc' : 'asc';
}

function wholeNumber(text: string | null, minimum: number): number | null {
  return text !== null && /^\d+$/.test(text) && Number(text) >= minimum ? Number(text) : null;
}

function readSort(text: string | null, currency: string): string {
  if (text === null) {
    return DEFAULT_DIRECTORY_QUERY.sort;
  }
  const [field, direction = 'asc'] = text.split(',');
  const known = SORT_FIELDS.includes(field) && (direction === 'asc' || direction === 'desc');
  return known && (field !== 'salary' || currency !== '')
    ? `${field},${direction}`
    : DEFAULT_DIRECTORY_QUERY.sort;
}
