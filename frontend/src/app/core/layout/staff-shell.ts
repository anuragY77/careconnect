import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCalendarDays, lucideHouse, lucideUsers } from '@ng-icons/lucide';

interface NavItem {
  label: string;
  path: string;
  icon: string;
}

/** Staff layout: narrow ink rail on the left, mist content area. Rail collapses to a top bar on phones. */
@Component({
  selector: 'app-staff-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon],
  providers: [provideIcons({ lucideHouse, lucideUsers, lucideCalendarDays })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a
      href="#main"
      class="bg-card text-foreground sr-only z-50 rounded-md px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
    >
      Skip to content
    </a>
    <div class="flex min-h-dvh flex-col overflow-x-clip md:flex-row">
      <nav
        class="bg-sidebar text-sidebar-foreground flex shrink-0 items-center gap-1 overflow-x-auto px-3 py-2 md:w-56 md:flex-col md:items-stretch md:gap-0.5 md:px-3 md:py-5"
        aria-label="Main"
      >
        <p translate="no" class="font-serif mr-3 shrink-0 text-lg font-semibold tracking-tight text-white md:mr-0 md:mb-5 md:px-2">
          CareConnect
        </p>
        @for (item of items; track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="bg-sidebar-accent text-sidebar-accent-foreground md:before:absolute md:before:inset-y-2 md:before:left-0 md:before:w-0.5 md:before:origin-center md:before:rounded-full md:before:bg-[#5eead4] md:before:animate-[cc-bar_260ms_var(--ease-out)_both]"
            class="group hover:bg-sidebar-accent relative flex shrink-0 items-center gap-2.5 whitespace-nowrap rounded-md px-2.5 py-2 text-sm font-medium transition-colors motion-reduce:transition-none"
          >
            <ng-icon [name]="item.icon" size="17" class="transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transition-none" aria-hidden="true" />
            {{ item.label }}
          </a>
        }
      </nav>
      <div class="flex min-w-0 flex-1 flex-col">
        <aside
          class="border-border bg-card text-muted-foreground border-b px-4 py-1.5 text-xs sm:px-6"
          aria-label="Training notice"
        >
          Training system with synthetic data. Not for clinical use.
        </aside>
        <main id="main" class="flex-1" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class StaffShell {
  protected readonly items: readonly NavItem[] = [
    { label: 'Home', path: '/home', icon: 'lucideHouse' },
    { label: 'Patients', path: '/patients', icon: 'lucidePeople' === '' ? '' : 'lucideUsers' },
    { label: 'Appointments', path: '/appointments', icon: 'lucideCalendarDays' },
  ];
}
