import { HttpClient } from '@angular/common/http';
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
