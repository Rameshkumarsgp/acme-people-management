import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalaryEntry } from '../../../core/employees/employee-api';
import { SalaryHistory } from './salary-history';

const ENTRIES: SalaryEntry[] = [
  {
    id: 3,
    effectiveDate: '2027-01-01',
    amountMinor: 20000000,
    currency: 'JPY',
    endDate: null,
    current: false,
    scheduled: true,
  },
  {
    id: 2,
    effectiveDate: '2025-04-01',
    amountMinor: 150000050,
    currency: 'INR',
    endDate: '2026-12-31',
    current: true,
    scheduled: false,
  },
  {
    id: 1,
    effectiveDate: '2024-04-01',
    amountMinor: 125000000,
    currency: 'INR',
    endDate: '2025-03-31',
    current: false,
    scheduled: false,
  },
];

const CURRENCIES = [
  { code: 'INR', minorUnitDigits: 2 },
  { code: 'JPY', minorUnitDigits: 0 },
];

describe('SalaryHistory', () => {
  let fixture: ComponentFixture<SalaryHistory>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SalaryHistory] }).compileComponents();
    fixture = TestBed.createComponent(SalaryHistory);
    page = fixture.nativeElement as HTMLElement;
    fixture.componentRef.setInput('entries', ENTRIES);
    fixture.componentRef.setInput('currencies', CURRENCIES);
    fixture.detectChanges();
  });

  function rows(): string[][] {
    return Array.from(page.querySelectorAll('tr[mat-row]')).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim() ?? ''),
    );
  }

  it('shows one row per entry in the order given, newest first', () => {
    expect(rows().map((row) => row[0])).toEqual(['2027-01-01', '2025-04-01', '2024-04-01']);
  });

  it('shows the last day of each period, and a dash for the newest', () => {
    expect(rows().map((row) => row[1])).toEqual(['—', '2026-12-31', '2025-03-31']);
  });

  it('shows each amount in its own currency with that currency decimals', () => {
    expect(rows().map((row) => row[2])).toEqual([
      '20,000,000 JPY',
      '1,500,000.50 INR',
      '1,250,000.00 INR',
    ]);
  });

  it('marks the current and the scheduled entries', () => {
    expect(rows().map((row) => row[3])).toEqual(['Scheduled', 'Current', '']);
  });

  it('shows the raw amount when its currency is not in the list', () => {
    fixture.componentRef.setInput('currencies', []);
    fixture.detectChanges();

    expect(rows().map((row) => row[2])).toEqual(['20000000 JPY', '150000050 INR', '125000000 INR']);
  });
});
