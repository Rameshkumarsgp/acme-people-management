import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, map } from 'rxjs';
import {
  DEFAULT_DIRECTORY_QUERY,
  DirectoryPage,
  DirectoryQuery,
  DirectoryRow,
  DirectoryStatus,
  EmployeeApi,
} from '../../core/employees/employee-api';
import { ReferenceApi, ReferenceLists } from '../../core/reference/reference-api';
import { formatMoney } from '../../shared/money/format-money';
import {
  PAGE_SIZES,
  readQuery,
  sortDirection,
  sortField,
  toQueryParams,
  withFilter,
  withPage,
} from './directory-query';

type LoadState = 'loading' | 'loaded' | 'failed';

const SEARCH_PAUSE_IN_MS = 300;

@Component({
  selector: 'app-directory',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSortModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './directory.html',
  styleUrl: './directory.scss',
})
export class Directory {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly referenceApi = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly typed = new Subject<string>();
  private latestRequest = 0;

  protected readonly pageSizes = PAGE_SIZES;
  protected readonly columns = [
    'id',
    'fullName',
    'department',
    'jobTitle',
    'country',
    'hireDate',
    'salary',
  ];
  protected readonly lists = signal<ReferenceLists | null>(null);
  protected readonly page = signal<DirectoryPage | null>(null);
  protected readonly state = signal<LoadState>('loading');
  protected readonly query = toSignal(this.route.queryParamMap.pipe(map(readQuery)), {
    requireSync: true,
  });
  protected readonly sortActive = computed(() => sortField(this.query().sort));
  protected readonly sortOrder = computed(() => sortDirection(this.query().sort));

  constructor() {
    this.typed
      .pipe(debounceTime(SEARCH_PAUSE_IN_MS), takeUntilDestroyed())
      .subscribe((text) => this.changeFilter({ q: text }));
    effect(() => {
      const query = this.query();
      untracked(() => void this.loadPage(query));
    });
    void this.loadLists();
  }

  protected searchTyped(event: Event): void {
    this.typed.next(this.valueOf(event));
  }

  protected chooseCountry(event: Event): void {
    this.changeFilter({ countryCode: this.valueOf(event) });
  }

  protected chooseDepartment(event: Event): void {
    this.changeFilter({ departmentId: this.idOf(event) });
  }

  protected chooseJobTitle(event: Event): void {
    this.changeFilter({ jobTitleId: this.idOf(event) });
  }

  protected chooseCurrency(event: Event): void {
    this.changeFilter({ currency: this.valueOf(event) });
  }

  protected chooseStatus(event: Event): void {
    this.changeFilter({ status: this.valueOf(event) as DirectoryStatus });
  }

  protected changeSort(sort: Sort): void {
    this.changeFilter({
      sort:
        sort.direction === '' ? DEFAULT_DIRECTORY_QUERY.sort : `${sort.active},${sort.direction}`,
    });
  }

  protected changePage(event: PageEvent): void {
    this.navigate(withPage(this.query(), event.pageIndex, event.pageSize));
  }

  protected retry(): void {
    void this.loadLists();
    void this.loadPage(this.query());
  }

  protected salaryText(row: DirectoryRow): string {
    const salary = row.currentSalary;
    return formatMoney(salary.amountMinor, salary.currency, this.lists()?.currencies);
  }

  private changeFilter(change: Partial<DirectoryQuery>): void {
    this.navigate(withFilter(this.query(), change));
  }

  private navigate(query: DirectoryQuery): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: toQueryParams(query) });
  }

  private valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  private idOf(event: Event): number | null {
    const value = this.valueOf(event);
    return value === '' ? null : Number(value);
  }

  private async loadLists(): Promise<void> {
    try {
      this.lists.set(await this.referenceApi.loadLists());
    } catch (error) {
      this.failed(error);
    }
  }

  private async loadPage(query: DirectoryQuery): Promise<void> {
    const request = ++this.latestRequest;
    this.state.set('loading');
    try {
      const page = await this.employeeApi.listEmployees(query);
      if (request === this.latestRequest) {
        this.page.set(page);
        this.state.set('loaded');
      }
    } catch (error) {
      if (request === this.latestRequest) {
        this.failed(error);
      }
    }
  }

  private failed(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      throw error;
    }
    this.state.set('failed');
  }
}
