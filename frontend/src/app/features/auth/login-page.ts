import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { HeroScene } from './hero-scene';

/**
 * Sign-in screen. Layout is deliberately asymmetric: a serif headline on an ink panel
 * and a left-aligned form on mist. The request itself is wired in TASK-024.
 */
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, HlmButtonImports, HlmInputImports, HlmLabelImports, HeroScene],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside
        class="relative isolate flex flex-col justify-between gap-12 overflow-hidden bg-[var(--cc-ink)] bg-[radial-gradient(48rem_32rem_at_0%_0%,#14505a_0%,transparent_65%)] px-6 py-8 text-white sm:px-12 lg:py-14"
      >
        <!-- Decorative 3D helix, loaded after first paint in its own chunk. -->
        <div class="pointer-events-none absolute inset-0 -z-20" aria-hidden="true">
          @defer (on idle) {
            <app-hero-scene class="block size-full" />
          }
          <!-- Keeps the headline readable where it meets the helix. -->
          <div class="absolute inset-0 bg-[linear-gradient(90deg,rgb(14_42_51/0.85),transparent_72%)]"></div>
        </div>

        <!-- Decorative heartbeat. Pure transform animation, hidden from assistive tech. -->
        <div
          class="pointer-events-none absolute inset-x-0 bottom-28 -z-10 h-28 overflow-hidden text-[#5eead4] opacity-20 [mask-image:linear-gradient(to_right,transparent,black_18%,black_82%,transparent)]"
          aria-hidden="true"
        >
          <svg
            class="h-full w-[200%] animate-[cc-ecg_14s_linear_infinite] motion-reduce:animate-none"
            viewBox="0 0 1600 120"
            preserveAspectRatio="none"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            vector-effect="non-scaling-stroke"
          >
            <defs>
              <path
                id="beat"
                vector-effect="non-scaling-stroke"
                d="M0 60 H130 L146 48 L162 60 H210 L226 72 L246 6 L268 108 L284 60 H350 L372 40 L394 60 H400"
              />
            </defs>
            <use href="#beat" x="0" />
            <use href="#beat" x="400" />
            <use href="#beat" x="800" />
            <use href="#beat" x="1200" />
          </svg>
        </div>

        <p class="cc-rise text-sm font-medium tracking-wide text-[#5eead4]" translate="no">CareConnect</p>
        <div>
          <h1 class="cc-rise font-serif max-w-md text-4xl leading-tight font-semibold sm:text-5xl" style="--i: 1">
            The whole patient record, in one place.
          </h1>
          <p class="cc-rise mt-4 max-w-sm text-base text-[#b8cdd1]" style="--i: 2">
            Clinical notes, orders and medications for staff. Appointments and results for patients.
          </p>
        </div>
        <p class="cc-rise max-w-sm text-xs text-[#9db5ba]" style="--i: 3" role="note">
          Training system with synthetic data. Not for clinical use.
        </p>
      </aside>

      <main class="relative flex items-center bg-[radial-gradient(#cfdadc_1px,transparent_1px)] [background-size:22px_22px] px-6 py-10 sm:px-12 lg:px-20">
        <form class="cc-rise w-full max-w-sm" style="--i: 2" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <h2 class="text-2xl font-semibold tracking-tight">Sign in</h2>
          <p class="text-muted-foreground mt-1 text-sm">Use the account your administrator created.</p>

          <div class="mt-8 space-y-5">
            <div class="space-y-1.5">
              <label hlmLabel for="username">Username</label>
              <input
                hlmInput
                id="username"
                class="w-full"
                formControlName="username"
                autocomplete="username"
                autocapitalize="none"
                spellcheck="false"
                [attr.aria-invalid]="invalid('username')"
                [attr.aria-describedby]="invalid('username') ? 'username-error' : null"
              />
              @if (invalid('username')) {
                <p id="username-error" class="text-destructive text-sm" role="alert">Enter your username.</p>
              }
            </div>

            <div class="space-y-1.5">
              <label hlmLabel for="password">Password</label>
              <input
                hlmInput
                id="password"
                type="password"
                class="w-full"
                formControlName="password"
                autocomplete="current-password"
                [attr.aria-invalid]="invalid('password')"
                [attr.aria-describedby]="invalid('password') ? 'password-error' : null"
              />
              @if (invalid('password')) {
                <p id="password-error" class="text-destructive text-sm" role="alert">Enter your password.</p>
              }
            </div>
          </div>

          <button hlmBtn size="lg" type="submit" class="mt-8 w-full" [disabled]="pending()">
            {{ pending() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
      </main>
    </div>
  `,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly pending = signal(false);
  protected readonly form = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  protected invalid(name: 'username' | 'password'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      // Move focus to the first field that needs attention.
      const first = ['username', 'password'].find((n) => this.form.controls[n as 'username'].invalid);
      this.host.nativeElement.querySelector<HTMLInputElement>(`#${first}`)?.focus();
      return;
    }
    // Wired to the auth API in TASK-024.
  }
}
