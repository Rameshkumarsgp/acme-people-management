import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((module) => module.Login),
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home').then((module) => module.Home),
  },
  {
    path: 'employees',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/directory/directory').then((module) => module.Directory),
  },
  {
    path: 'employees/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employee-form/employee-form').then((module) => module.EmployeeForm),
  },
  {
    path: 'employees/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employee-detail/employee-detail').then((module) => module.EmployeeDetail),
  },
  { path: '**', redirectTo: '' },
];
