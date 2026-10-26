import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleCheck, lucideTriangleAlert } from '@ng-icons/lucide';

export interface AllergyItem {
  substance: string;
  reaction: string;
  severity: 'MILD' | 'MODERATE' | 'SEVERE';
}

/**
 * Fixed at the top of every patient chart. Allergies are the one fact a clinician must
 * never miss, so it is always visible and never collapsed. "No known allergies" is an
 * explicit state, not an empty strip.
 */
@Component({
  selector: 'app-allergy-strip',
  imports: [NgIcon],
  providers: [provideIcons({ lucideTriangleAlert, lucideCircleCheck })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (hasAllergies()) {
      <section
        class="border-[var(--cc-signal)] bg-[var(--cc-signal-tint)] flex flex-wrap items-center gap-x-5 gap-y-1 border-y-2 px-4 py-2 sm:px-6"
        aria-label="Allergies"
        role="region"
      >
        <h2 class="text-[var(--cc-signal)] flex items-center gap-1.5 text-sm font-semibold">
          <ng-icon name="lucideTriangleAlert" size="16" class="cc-pop" aria-hidden="true" />
          Allergies
        </h2>
        <ul class="m-0 flex list-none flex-wrap gap-x-5 gap-y-1 p-0 text-sm">
          @for (a of allergies(); track a.substance) {
            <li>
              <span class="font-semibold">{{ a.substance }}</span>
              <span class="text-muted-foreground"> · {{ a.reaction }}</span>
              @if (a.severity === 'SEVERE') {
                <span class="text-[var(--cc-signal)] font-semibold"> · Severe</span>
              }
            </li>
          }
        </ul>
      </section>
    } @else {
      <section
        class="border-border bg-card text-muted-foreground flex items-center gap-1.5 border-y px-4 py-2 text-sm sm:px-6"
        aria-label="Allergies"
        role="region"
      >
        <ng-icon name="lucideCircleCheck" size="16" aria-hidden="true" />
        No known allergies recorded
      </section>
    }
  `,
})
export class AllergyStrip {
  readonly allergies = input<readonly AllergyItem[]>([]);
  protected readonly hasAllergies = computed(() => this.allergies().length > 0);
}
