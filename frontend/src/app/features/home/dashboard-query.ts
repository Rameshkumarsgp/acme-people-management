import { ParamMap, Params } from '@angular/router';
import { DirectoryStatus } from '../../core/employees/employee-api';
import { ExtremesOrder, InsightDimension, InsightFilters } from '../../core/insights/insights-api';

/** The dashboard's whole state, kept in the address so Back from an employee returns to the same view. */
export interface DashboardQuery extends InsightFilters {
  dimension: InsightDimension;
  rank: ExtremesOrder;
  rankCurrency: string;
}

export const DEFAULT_DASHBOARD_QUERY: DashboardQuery = {
  dimension: 'COUNTRY',
  countryCode: '',
  departmentId: null,
  jobTitleId: null,
  currency: '',
  status: 'ACTIVE',
  rank: 'HIGHEST',
  rankCurrency: '',
};

export const DIMENSIONS: { value: InsightDimension; label: string }[] = [
  { value: 'OVERALL', label: 'Overall' },
  { value: 'COUNTRY', label: 'Country' },
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'JOB_TITLE', label: 'Job title' },
  { value: 'TITLE_COUNTRY', label: 'Job title in country' },
  { value: 'TENURE', label: 'Tenure' },
];

const STATUSES: DirectoryStatus[] = ['ACTIVE', 'FORMER', 'ALL'];
const ORDERS: ExtremesOrder[] = ['HIGHEST', 'LOWEST'];

/** Reads the dashboard's state from the address. Anything the server would refuse falls back to the default. */
export function readDashboardQuery(params: ParamMap): DashboardQuery {
  const defaults = DEFAULT_DASHBOARD_QUERY;
  return {
    dimension:
      DIMENSIONS.find((dimension) => dimension.value === params.get('dimension'))?.value ??
      defaults.dimension,
    countryCode: params.get('countryCode') ?? '',
    departmentId: wholeNumber(params.get('departmentId')),
    jobTitleId: wholeNumber(params.get('jobTitleId')),
    currency: params.get('currency') ?? '',
    status: STATUSES.find((status) => status === params.get('status')) ?? defaults.status,
    rank: ORDERS.find((order) => order === params.get('rank')) ?? defaults.rank,
    rankCurrency: params.get('rankCurrency') ?? '',
  };
}

/** Writes the state as address parameters, leaving out what is the default so the address stays short. */
export function toDashboardParams(query: DashboardQuery): Params {
  const defaults = DEFAULT_DASHBOARD_QUERY;
  const params: Params = {};
  for (const key of Object.keys(defaults) as (keyof DashboardQuery)[]) {
    if (query[key] !== defaults[key]) {
      params[key] = query[key];
    }
  }
  return params;
}

function wholeNumber(text: string | null): number | null {
  return text !== null && /^\d+$/.test(text) && Number(text) >= 1 ? Number(text) : null;
}
