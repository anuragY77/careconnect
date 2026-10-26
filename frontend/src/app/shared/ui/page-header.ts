import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Title block at the top of a staff page. Actions (buttons) are projected on the right. */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="border-border bg-card flex flex-wrap items-end justify-between gap-3 border-b px-4 py-5 sm:px-6">
      <div class="cc-rise min-w-0">
        <h1 class="text-xl font-semibold tracking-tight">{{ heading() }}</h1>
        @if (description()) {
          <p class="text-muted-foreground mt-0.5 max-w-prose text-sm">{{ description() }}</p>
        }
      </div>
      <div class="cc-rise flex items-center gap-2" style="--i: 1"><ng-content /></div>
    </header>
  `,
})
export class PageHeader {
  readonly heading = input.required<string>();
  readonly description = input<string>('');
}
