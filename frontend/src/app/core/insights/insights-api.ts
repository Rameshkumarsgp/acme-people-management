import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { DirectoryStatus } from '../employees/employee-api';

export type InsightDimension =
  'OVERALL' | 'COUNTRY' | 'DEPARTMENT' | 'JOB_TITLE' | 'TITLE_COUNTRY' | 'TENURE';

export type ExtremesOrder = 'HIGHEST' | 'LOWEST';

/** Which employees an insight covers. An empty text or a null id means the filter is not set. */
export interface InsightFilters {
  countryCode: string;
  departmentId: number | null;
  jobTitleId: number | null;
  currency: string;
  status: DirectoryStatus;
}

export interface InsightsRequest extends InsightFilters {
  dimension: InsightDimension;
}

export interface ExtremesRequest extends InsightFilters {
  order: ExtremesOrder;
  limit: number;
}

/** One group in one currency. The total is digits as text, because it can pass the largest exact number. */
export interface InsightGroup {
  key: string;
  label: string;
  currency: string;
  headcount: number;
  total: string;
  min: number;
  median: number;
  mean: number;
  max: number;
}

export interface InsightsResponse {
  dimension: InsightDimension;
  groups: InsightGroup[];
}

export interface ExtremeEmployee {
  id: number;
  fullName: string;
  department: string;
  jobTitle: string;
  country: string;
  amountMinor: number;
  effectiveDate: string;
}

export interface ExtremesResponse {
  order: ExtremesOrder;
  currency: string;
  employees: ExtremeEmployee[];
}

@Injectable({ providedIn: 'root' })
export class InsightsApi {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  insights(request: InsightsRequest): Promise<InsightsResponse> {
    const params = filterParams(request).set('dimension', request.dimension);
    return firstValueFrom(
      this.http.get<InsightsResponse>(`${this.apiBaseUrl}/insights`, { params }),
    );
  }

  extremes(request: ExtremesRequest): Promise<ExtremesResponse> {
    const params = filterParams(request).set('order', request.order).set('limit', request.limit);
    return firstValueFrom(
      this.http.get<ExtremesResponse>(`${this.apiBaseUrl}/insights/extremes`, { params }),
    );
  }
}

function filterParams(filters: InsightFilters): HttpParams {
  let params = new HttpParams().set('status', filters.status);
  if (filters.countryCode !== '') {
    params = params.set('countryCode', filters.countryCode);
  }
  if (filters.departmentId !== null) {
    params = params.set('departmentId', filters.departmentId);
  }
  if (filters.jobTitleId !== null) {
    params = params.set('jobTitleId', filters.jobTitleId);
  }
  if (filters.currency !== '') {
    params = params.set('currency', filters.currency);
  }
  return params;
}
