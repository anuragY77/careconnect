import { Routes } from '@angular/router';
import { StaffShell } from './core/layout/staff-shell';
import { LoginPage } from './features/auth/login-page';
import { NotFoundPage } from './features/not-found/not-found-page';
import { PendingModulePage } from './features/pending/pending-module-page';

export const routes: Routes = [
  { path: 'login', component: LoginPage, title: 'Sign in · CareConnect' },
  {
    path: '',
    component: StaffShell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        component: PendingModulePage,
        title: 'Home · CareConnect',
        data: {
          heading: 'Home',
          description: 'Your worklist for today.',
          icon: 'lucideHouse',
          emptyHeading: 'Nothing to show yet',
          emptyDescription:
            'Your worklist will appear here once patient records and appointments are available.',
        },
      },
      {
        path: 'patients',
        component: PendingModulePage,
        title: 'Patients · CareConnect',
        data: {
          heading: 'Patients',
          description: 'Search and open patient charts.',
          icon: 'lucideUsers',
          emptyHeading: 'Patient records are not available yet',
          emptyDescription: 'Registration and charts are built in Phase 2 of the project.',
        },
      },
      {
        path: 'appointments',
        component: PendingModulePage,
        title: 'Appointments · CareConnect',
        data: {
          heading: 'Appointments',
          description: 'Schedule and visits.',
          icon: 'lucideCalendarDays',
          emptyHeading: 'Scheduling is not available yet',
          emptyDescription: 'Appointments are built in Phase 2 of the project.',
        },
      },
    ],
  },
  { path: '**', component: NotFoundPage, title: 'Page not found · CareConnect' },
];
