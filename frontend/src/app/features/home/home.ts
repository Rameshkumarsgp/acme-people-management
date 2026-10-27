import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DirectoryStatus } from '../../core/employees/employee-api';
import {
  ExtremesOrder,
  ExtremesRequest,
  ExtremesResponse,
  InsightDimension,
  InsightsApi,
  InsightsRequest,
  InsightsResponse,
} from '../../core/insights/insights-api';
import { ReferenceApi, ReferenceLists } from '../../core/reference/reference-api';
import {
  DIMENSIONS,
  DashboardQuery,
  readDashboardQuery,
  toDashboardParams,
} from './dashboard-query';
import { ExtremesList } from './extremes-list/extremes-list';
import { InsightsTable } from './insights-table/insights-table';

type LoadState = 'loading' | 'loaded' | 'failed';

const EXTREMES_LIMIT = 10;

const sameRequest = (first: unknown, second: unknown) =>
  JSON.stringify(first) === JSON.stringify(second);

@Component({
  selector: 'app-home',
  imports: [
    ExtremesList,
    InsightsTable,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    RouterLink,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly auth = inject(AuthService);
  private readonly insightsApi = inject(InsightsApi);
  private readonly referenceApi = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private latestInsights = 0;
  private latestExtremes = 0;

  protected readonly username = this.auth.username;
  protected readonly dimensions = DIMENSIONS;
  protected readonly lists = signal<ReferenceLists | null>(null);
  protected readonly insights = signal<InsightsResponse | null>(null);
  protected readonly insightsState = signal<LoadState>('loading');
  protected readonly extremes = signal<ExtremesResponse | null>(null);
  protected readonly extremesState = signal<LoadState>('loading');
  protected readonly query = toSignal(this.route.queryParamMap.pipe(map(readDashboardQuery)), {
    requireSync: true,
  });

  /** What the groups depend on. A change of the ranking alone is the same request, so it does not reload them. */
  private readonly groupsRequest = computed(
    () => {
      const { rank, rankCurrency, ...request } = this.query();
      void rank;
      void rankCurrency;
      return request;
    },
    { equal: sameRequest },
  );

  /** The currency to rank in: the one chosen, else the currency filter, else the first group's. */
  protected readonly rankCurrency = computed(
    () =>
      this.query().rankCurrency ||
      this.query().currency ||
      this.insights()?.groups[0]?.currency ||
      '',
  );

  /** What the ranking depends on. A change of the dimension alone is the same ranking, so it does not reload it. */
  private readonly rankingRequest = computed(
    () => {
      const { countryCode, departmentId, jobTitleId, status, rank } = this.query();
      const currency = this.rankCurrency();
      return {
        countryCode,
        departmentId,
        jobTitleId,
        status,
        currency,
        order: rank,
        limit: EXTREMES_LIMIT,
      };
    },
    { equal: sameRequest },
  );

  constructor() {
    effect(() => {
      const request = this.groupsRequest();
      untracked(() => void this.loadInsights(request));
    });
    effect(() => {
      const request = this.rankingRequest();
      untracked(() => void this.loadExtremes(request));
    });
    void this.loadLists();
  }

  protected chooseDimension(event: Event): void {
    this.change({ dimension: this.valueOf(event) as InsightDimension });
  }

  protected chooseCountry(event: Event): void {
    this.change({ countryCode: this.valueOf(event) });
  }

  protected chooseDepartment(event: Event): void {
    this.change({ departmentId: this.idOf(event) });
  }

  protected chooseJobTitle(event: Event): void {
    this.change({ jobTitleId: this.idOf(event) });
  }

  protected chooseCurrency(event: Event): void {
    this.change({ currency: this.valueOf(event), rankCurrency: '' });
  }

  protected chooseStatus(event: Event): void {
    this.change({ status: this.valueOf(event) as DirectoryStatus });
  }

  protected chooseRank(event: Event): void {
    this.change({ rank: this.valueOf(event) as ExtremesOrder });
  }

  protected chooseRankCurrency(event: Event): void {
    this.change({ rankCurrency: this.valueOf(event) });
  }

  protected retry(): void {
    void this.loadLists();
    void this.loadInsights(this.groupsRequest());
    void this.loadExtremes(this.rankingRequest());
  }

  protected signOut(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }

  private change(change: Partial<DashboardQuery>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toDashboardParams({ ...this.query(), ...change }),
    });
  }

  private valueOf(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }

  private idOf(event: Event): number | null {
    const value = this.valueOf(event);
    return value === '' ? null : Number(value);
  }

  private async loadLists(): Promise<void> {
    try {
      this.lists.set(await this.referenceApi.loadLists());
    } catch (error) {
      this.failed(error, this.insightsState);
    }
  }

  private async loadInsights(request: InsightsRequest): Promise<void> {
    const attempt = ++this.latestInsights;
    this.insightsState.set('loading');
    try {
      const response = await this.insightsApi.insights(request);
      if (attempt === this.latestInsights) {
        this.insights.set(response);
        this.insightsState.set('loaded');
      }
    } catch (error) {
      if (attempt === this.latestInsights) {
        this.failed(error, this.insightsState);
      }
    }
  }

  private async loadExtremes(request: ExtremesRequest): Promise<void> {
    const attempt = ++this.latestExtremes;
    if (request.currency === '') {
      this.extremes.set(null);
      this.extremesState.set('loaded');
      return;
    }
    this.extremesState.set('loading');
    try {
      const response = await this.insightsApi.extremes(request);
      if (attempt === this.latestExtremes) {
        this.extremes.set(response);
        this.extremesState.set('loaded');
      }
    } catch (error) {
      if (attempt === this.latestExtremes) {
        this.failed(error, this.extremesState);
      }
    }
  }

  private failed(error: unknown, state: { set(value: LoadState): void }): void {
    if (!(error instanceof HttpErrorResponse)) {
      throw error;
    }
    state.set('failed');
  }
}
