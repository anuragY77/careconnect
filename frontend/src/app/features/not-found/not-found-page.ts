import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmButtonImports } from '@spartan-ng/helm/button';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, HlmButtonImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-dvh items-center px-6 sm:px-12">
      <div class="border-border max-w-md border-l-2 pl-5">
        <p class="tnum text-muted-foreground text-sm font-medium">Error 404</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight">This page does not exist</h1>
        <p class="text-muted-foreground mt-2 text-sm">
          The address may be mistyped, or the page may have moved.
        </p>
        <a hlmBtn routerLink="/" class="mt-6">Go to home</a>
      </div>
    </main>
  `,
})
export class NotFoundPage {}
