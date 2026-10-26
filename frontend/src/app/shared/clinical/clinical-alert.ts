import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleAlert, lucideInfo, lucideTriangleAlert } from '@ng-icons/lucide';

export type AlertLevel = 'INFO' | 'WARNING' | 'CRITICAL';

const STYLE: Record<AlertLevel, { box: string; icon: string; label: string }> = {
  INFO: {
    box: 'border-[var(--cc-info)] bg-[var(--cc-info-tint)] text-[var(--cc-info)]',
    icon: 'lucideInfo',
    label: 'Information',
  },
  WARNING: {
    box: 'border-[var(--cc-warning)] bg-[var(--cc-warning-tint)] text-[var(--cc-warning)]',
    icon: 'lucideTriangleAlert',
    label: 'Warning',
  },
  CRITICAL: {
    box: 'border-[var(--cc-signal)] bg-[var(--cc-signal-tint)] text-[var(--cc-signal)]',
    icon: 'lucideCircleAlert',
    label: 'Critical',
  },
};

/**
 * Clinical alert. Level is always conveyed by icon and text, never by colour alone.
 * A critical alert also pulses a red ring once when it appears.
 */
@Component({
  selector: 'app-clinical-alert',
  imports: [NgIcon],
  providers: [provideIcons({ lucideInfo, lucideTriangleAlert, lucideCircleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="cc-rise flex items-start gap-3 rounded-md border-l-4 px-4 py-3 text-sm"
      [class]="style().box"
      [style.animation]="level() === 'CRITICAL' ? 'cc-rise 480ms var(--ease-out) both, cc-ring 900ms ease-out 400ms 1' : null"
      [attr.role]="level() === 'CRITICAL' ? 'alert' : 'status'"
    >
      <ng-icon [name]="style().icon" size="18" class="mt-0.5 shrink-0" aria-hidden="true" />
      <div class="min-w-0">
        <p class="font-semibold">{{ style().label }}: {{ heading() }}</p>
        <div class="text-foreground mt-0.5"><ng-content /></div>
      </div>
    </div>
  `,
})
export class ClinicalAlert {
  readonly level = input<AlertLevel>('INFO');
  readonly heading = input.required<string>();
  protected readonly style = computed(() => STYLE[this.level()]);
}
