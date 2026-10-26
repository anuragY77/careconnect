import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Identity block at the top of a chart. MRN and age use tabular figures. */
@Component({
  selector: 'app-patient-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bg-card flex flex-wrap items-baseline gap-x-6 gap-y-1 px-4 py-4 sm:px-6">
      <h1 class="text-xl font-semibold tracking-tight">{{ name() }}</h1>
      <dl class="text-muted-foreground m-0 flex flex-wrap gap-x-6 gap-y-1 text-sm">
        <div class="flex gap-1.5">
          <dt>MRN</dt>
          <dd class="tnum text-foreground m-0 font-medium">{{ mrn() }}</dd>
        </div>
        <div class="flex gap-1.5">
          <dt>Age</dt>
          <dd class="tnum text-foreground m-0 font-medium">{{ age() }}</dd>
        </div>
        <div class="flex gap-1.5">
          <dt>Sex</dt>
          <dd class="text-foreground m-0 font-medium">{{ sex() }}</dd>
        </div>
      </dl>
    </header>
  `,
})
export class PatientHeader {
  readonly name = input.required<string>();
  readonly mrn = input.required<string>();
  readonly age = input.required<number>();
  readonly sex = input.required<string>();
}
