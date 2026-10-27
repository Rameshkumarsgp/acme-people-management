import { convertToParamMap } from '@angular/router';
import {
  DEFAULT_DASHBOARD_QUERY,
  DashboardQuery,
  readDashboardQuery,
  toDashboardParams,
} from './dashboard-query';

describe('dashboard query', () => {
  it('shows the active employees by country, ranked from the highest, when the address is empty', () => {
    expect(readDashboardQuery(convertToParamMap({}))).toEqual(DEFAULT_DASHBOARD_QUERY);
  });

  it('reads every parameter', () => {
    const query = readDashboardQuery(
      convertToParamMap({
        dimension: 'TENURE',
        countryCode: 'IN',
        departmentId: '3',
        jobTitleId: '12',
        currency: 'INR',
        status: 'ALL',
        rank: 'LOWEST',
        rankCurrency: 'USD',
      }),
    );

    expect(query).toEqual({
      dimension: 'TENURE',
      countryCode: 'IN',
      departmentId: 3,
      jobTitleId: 12,
      currency: 'INR',
      status: 'ALL',
      rank: 'LOWEST',
      rankCurrency: 'USD',
    });
  });

  it('falls back to the default for a value the server would refuse', () => {
    const query = readDashboardQuery(
      convertToParamMap({
        dimension: 'SALARY',
        status: 'GONE',
        rank: 'MIDDLE',
        departmentId: 'x',
        jobTitleId: '0',
      }),
    );

    expect(query).toEqual(DEFAULT_DASHBOARD_QUERY);
  });

  it('leaves the defaults out of the address and writes the rest', () => {
    expect(toDashboardParams(DEFAULT_DASHBOARD_QUERY)).toEqual({});

    const query: DashboardQuery = {
      ...DEFAULT_DASHBOARD_QUERY,
      dimension: 'JOB_TITLE',
      departmentId: 3,
      rank: 'LOWEST',
    };
    expect(toDashboardParams(query)).toEqual({
      dimension: 'JOB_TITLE',
      departmentId: 3,
      rank: 'LOWEST',
    });
  });
});
