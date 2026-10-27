import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalaryChange } from '../../../core/employees/employee-api';
import { SalaryChanges } from './salary-changes';

const CHANGES: SalaryChange[] = [
  {
    id: 3,
    entryId: 5,
    action: 'DELETED',
    changedAt: '2026-10-19T09:30:00Z',
    before: { effectiveDate: '2026-05-01', amountMinor: 160000000, currency: 'INR' },
    after: null,
  },
  {
    id: 2,
    entryId: 5,
    action: 'EDITED',
    changedAt: '2026-10-18T09:00:00Z',
    before: { effectiveDate: '2026-04-01', amountMinor: 150000050, currency: 'INR' },
    after: { effectiveDate: '2026-05-01', amountMinor: 160000000, currency: 'INR' },
  },
  {
    id: 1,
    entryId: 5,
    action: 'ADDED',
    changedAt: '2026-10-17T09:00:00Z',
    before: null,
    after: { effectiveDate: '2026-04-01', amountMinor: 6500000, currency: 'JPY' },
  },
];

const CURRENCIES = [
  { code: 'INR', minorUnitDigits: 2 },
  { code: 'JPY', minorUnitDigits: 0 },
];

describe('SalaryChanges', () => {
  let fixture: ComponentFixture<SalaryChanges>;
  let page: HTMLElement;

  async function show(changes: SalaryChange[]): Promise<void> {
    await TestBed.configureTestingModule({ imports: [SalaryChanges] }).compileComponents();
    fixture = TestBed.createComponent(SalaryChanges);
    page = fixture.nativeElement as HTMLElement;
    fixture.componentRef.setInput('changes', changes);
    fixture.componentRef.setInput('currencies', CURRENCIES);
    fixture.detectChanges();
  }

  const rows = () =>
    Array.from(page.querySelectorAll('tr[mat-row]')).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim() ?? ''),
    );

  it('says there are no changes yet when the log is empty', async () => {
    await show([]);

    expect(page.textContent).toContain('No changes yet.');
    expect(page.querySelector('table')).toBeNull();
  });

  it('shows one row per change in the order given, with the time in UTC and the action', async () => {
    await show(CHANGES);

    expect(rows().map((row) => row.slice(0, 2))).toEqual([
      ['2026-10-19 09:30', 'Deleted'],
      ['2026-10-18 09:00', 'Edited'],
      ['2026-10-17 09:00', 'Added'],
    ]);
    expect(page.querySelector('th')?.textContent).toContain('UTC');
  });

  it('shows what was deleted, what an edit changed from and to, and what was added', async () => {
    await show(CHANGES);

    expect(rows().map((row) => row[2])).toEqual([
      '2026-05-01 · 1,600,000.00 INR',
      '2026-04-01 · 1,500,000.50 INR → 2026-05-01 · 1,600,000.00 INR',
      '2026-04-01 · 6,500,000 JPY',
    ]);
  });

  it('shows an amount as stored when its currency is not known', async () => {
    await show([{ ...CHANGES[2], after: { ...CHANGES[2].after!, currency: 'XXX' } }]);

    expect(rows()[0][2]).toBe('2026-04-01 · 6500000 XXX');
  });
});
