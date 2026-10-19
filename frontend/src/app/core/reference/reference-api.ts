import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, forkJoin } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';

export interface Country {
  code: string;
  name: string;
}

export interface NamedReference {
  id: number;
  name: string;
}

export interface Currency {
  code: string;
  minorUnitDigits: number;
}

export interface ReferenceLists {
  countries: Country[];
  departments: NamedReference[];
  jobTitles: NamedReference[];
  currencies: Currency[];
}

/** The standardized lists that forms and filters offer. They change only through a migration. */
@Injectable({ providedIn: 'root' })
export class ReferenceApi {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  loadLists(): Promise<ReferenceLists> {
    return firstValueFrom(
      forkJoin({
        countries: this.http.get<Country[]>(`${this.apiBaseUrl}/reference/countries`),
        departments: this.http.get<NamedReference[]>(`${this.apiBaseUrl}/reference/departments`),
        jobTitles: this.http.get<NamedReference[]>(`${this.apiBaseUrl}/reference/job-titles`),
        currencies: this.http.get<Currency[]>(`${this.apiBaseUrl}/reference/currencies`),
      }),
    );
  }

  loadCurrencies(): Promise<Currency[]> {
    return firstValueFrom(this.http.get<Currency[]>(`${this.apiBaseUrl}/reference/currencies`));
  }
}
