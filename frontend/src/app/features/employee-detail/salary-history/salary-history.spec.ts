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

  describe('row actions', () => {
    const button = (row: number, kind: 'edit' | 'delete') => {
      const tableRows = page.querySelectorAll('tr[mat-row]');
      return tableRows[row].querySelector<HTMLButtonElement>(`button[data-test="${kind}-entry"]`);
    };

    it('offers Edit and Delete on every row, named by the date of the entry', () => {
      expect(page.querySelectorAll('button[data-test="edit-entry"]')).toHaveLength(3);
      expect(page.querySelectorAll('button[data-test="delete-entry"]')).toHaveLength(3);
      expect(button(1, 'edit')?.getAttribute('aria-label')).toBe('Edit the salary from 2025-04-01');
      expect(button(1, 'delete')?.getAttribute('aria-label')).toBe(
        'Delete the salary from 2025-04-01',
      );
    });

    it('says which entry to edit when Edit is chosen', () => {
      const edited: SalaryEntry[] = [];
      fixture.componentInstance.edit.subscribe((entry) => edited.push(entry));

      button(1, 'edit')!.click();

      expect(edited).toEqual([ENTRIES[1]]);
    });

    it('says which entry to delete when Delete is chosen', () => {
      const removed: SalaryEntry[] = [];
      fixture.componentInstance.remove.subscribe((entry) => removed.push(entry));

      button(2, 'delete')!.click();

      expect(removed).toEqual([ENTRIES[2]]);
    });
  });
});
