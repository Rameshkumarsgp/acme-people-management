import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { Country, NamedReference } from '../reference/reference-api';

export interface CreateEmployeeRequest {
  fullName: string;
  email: string;
  hireDate: string;
  departmentId: number;
  jobTitleId: number;
  countryCode: string;
  initialSalary: { effectiveDate?: string; amountMinor: number; currency: string };
}

export interface Employee {
  id: number;
  fullName: string;
  email: string;
  hireDate: string;
  terminationDate: string | null;
  status: 'ACTIVE' | 'FORMER';
  department: NamedReference;
  jobTitle: NamedReference;
  country: Country;
  currentSalary: { effectiveDate: string; amountMinor: number; currency: string };
  version: number;
}

/** The whole profile of an employee, with the version HR saw. An empty termination date means still employed. */
export interface UpdateEmployeeRequest {
  fullName: string;
  email: string;
  hireDate: string;
  terminationDate: string | null;
  departmentId: number;
  jobTitleId: number;
  countryCode: string;
  version: number;
}

export interface SalaryEntry {
  id: number;
  effectiveDate: string;
  amountMinor: number;
  currency: string;
  endDate: string | null;
  current: boolean;
  scheduled: boolean;
}

export interface AddSalaryEntryRequest {
  effectiveDate: string;
  amountMinor: number;
  currency?: string;
}

export type DirectoryStatus = 'ACTIVE' | 'FORMER' | 'ALL';

/** What the directory asks for. An empty text or a null id means the filter is not set. */
export interface DirectoryQuery {
  q: string;
  countryCode: string;
  departmentId: number | null;
  jobTitleId: number | null;
  currency: string;
  status: DirectoryStatus;
  sort: string;
  page: number;
  size: number;
}

export const DEFAULT_DIRECTORY_QUERY: DirectoryQuery = {
  q: '',
  countryCode: '',
  departmentId: null,
  jobTitleId: null,
  currency: '',
  status: 'ACTIVE',
  sort: 'fullName,asc',
  page: 0,
  size: 20,
};

export interface DirectoryRow {
  id: number;
  fullName: string;
  email: string;
  department: string;
  jobTitle: string;
  country: string;
  status: 'ACTIVE' | 'FORMER';
  hireDate: string;
  currentSalary: { amountMinor: number; currency: string };
}

export interface DirectoryPage {
  content: DirectoryRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  create(request: CreateEmployeeRequest): Promise<Employee> {
    return firstValueFrom(this.http.post<Employee>(`${this.apiBaseUrl}/employees`, request));
  }

  get(id: number): Promise<Employee> {
    return firstValueFrom(this.http.get<Employee>(`${this.apiBaseUrl}/employees/${id}`));
  }

  listEmployees(query: DirectoryQuery): Promise<DirectoryPage> {
    let params = new HttpParams()
      .set('status', query.status)
      .set('sort', query.sort)
      .set('page', query.page)
      .set('size', query.size);
    if (query.q !== '') {
      params = params.set('q', query.q);
    }
    if (query.countryCode !== '') {
      params = params.set('countryCode', query.countryCode);
    }
    if (query.departmentId !== null) {
      params = params.set('departmentId', query.departmentId);
    }
    if (query.jobTitleId !== null) {
      params = params.set('jobTitleId', query.jobTitleId);
    }
    if (query.currency !== '') {
      params = params.set('currency', query.currency);
    }
    return firstValueFrom(this.http.get<DirectoryPage>(`${this.apiBaseUrl}/employees`, { params }));
  }

  update(id: number, request: UpdateEmployeeRequest): Promise<Employee> {
    return firstValueFrom(this.http.put<Employee>(`${this.apiBaseUrl}/employees/${id}`, request));
  }

  delete(id: number, version: number): Promise<void> {
    const params = new HttpParams().set('version', version);
    return firstValueFrom(
      this.http.delete<void>(`${this.apiBaseUrl}/employees/${id}`, { params }),
    ).then(() => undefined);
  }

  listSalaryEntries(employeeId: number): Promise<SalaryEntry[]> {
    return firstValueFrom(
      this.http.get<SalaryEntry[]>(`${this.apiBaseUrl}/employees/${employeeId}/salary-entries`),
    );
  }

  addSalaryEntry(employeeId: number, request: AddSalaryEntryRequest): Promise<SalaryEntry> {
    return firstValueFrom(
      this.http.post<SalaryEntry>(
        `${this.apiBaseUrl}/employees/${employeeId}/salary-entries`,
        request,
      ),
    );
  }
}
