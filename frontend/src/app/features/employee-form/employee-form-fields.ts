export type ControlName =
  | 'fullName'
  | 'email'
  | 'hireDate'
  | 'departmentId'
  | 'jobTitleId'
  | 'countryCode'
  | 'currency'
  | 'salaryAmount'
  | 'salaryEffectiveDate';

/** The API names a field by its request path. This maps it to the form control that shows its error. */
export const SERVER_FIELD_TO_CONTROL: Readonly<Record<string, ControlName | undefined>> = {
  fullName: 'fullName',
  email: 'email',
  hireDate: 'hireDate',
  departmentId: 'departmentId',
  jobTitleId: 'jobTitleId',
  countryCode: 'countryCode',
  'initialSalary.currency': 'currency',
  'initialSalary.amountMinor': 'salaryAmount',
  'initialSalary.effectiveDate': 'salaryEffectiveDate',
};
