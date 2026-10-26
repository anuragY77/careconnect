import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';

/**
 * Page for a staff section whose module is not built yet. It states that plainly and names
 * the phase it ships in. Each route passes its text through route data.
 */
@Component({
  selector: 'app-pending-module-page',
  imports: [PageHeader, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header [heading]="heading()" [description]="description()" />
    <app-empty-state [icon]="icon()" [heading]="emptyHeading()" [description]="emptyDescription()" />
  `,
})
export class PendingModulePage {
  readonly heading = input.required<string>();
  readonly description = input<string>('');
  readonly icon = input<string>('lucideFileSearch');
  readonly emptyHeading = input.required<string>();
  readonly emptyDescription = input.required<string>();
}
