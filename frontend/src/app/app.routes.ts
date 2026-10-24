import { Routes } from '@angular/router';
import { StaffShell } from './core/layout/staff-shell';
import { LoginPage } from './features/auth/login-page';

export const routes: Routes = [
  { path: 'login', component: LoginPage, title: 'Sign in · CareConnect' },
  { path: '', component: StaffShell, children: [] },
  { path: '**', redirectTo: '' },
];
