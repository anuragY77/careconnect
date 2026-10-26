import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCalendarDays, lucideFileSearch, lucideHouse, lucideUsers } from '@ng-icons/lucide';

/**
 * Empty state: says what is missing and why, in plain words. Left-aligned on purpose, so it
 * reads like a note in a record and not like a marketing placeholder.
 */
@Component({
  selector: 'app-empty-state',
  imports: [NgIcon],
  providers: [provideIcons({ lucideHouse, lucideUsers, lucideCalendarDays, lucideFileSearch })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="px-4 py-10 sm:px-6" aria-labelledby="empty-title">
      <div class="cc-rise border-border max-w-lg border-l-2 pl-5" style="--i: 2">
        <ng-icon [name]="icon()" size="22" class="cc-pop text-muted-foreground block" style="--i: 2" aria-hidden="true" />
        <h2 id="empty-title" class="mt-3 text-base font-semibold">{{ heading() }}</h2>
        <p class="text-muted-foreground mt-1 text-sm">{{ description() }}</p>
        <div class="mt-4 flex flex-wrap gap-2"><ng-content /></div>
      </div>
    </section>
  `,
})
export class EmptyState {
  readonly icon = input<string>('lucideFileSearch');
  readonly heading = input.required<string>();
  readonly description = input.required<string>();
}
