import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Routing', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes, withComponentInputBinding())],
    });
    harness = await RouterTestingHarness.create();
  });

  it('opens home from the root address', async () => {
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/home');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Nothing to show yet');
  });

  it.each([
    ['/patients', 'Patients', 'Patient records are not available yet'],
    ['/appointments', 'Appointments', 'Scheduling is not available yet'],
  ])('%s shows its own page and an honest empty state', async (url, heading, empty) => {
    await harness.navigateByUrl(url);
    const text = (harness.routeNativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain(heading);
    expect(text).toContain(empty);
  });

  it('shows a real 404 page for an unknown address and keeps the address', async () => {
    await harness.navigateByUrl('/does-not-exist');
    expect(TestBed.inject(Router).url).toBe('/does-not-exist');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('This page does not exist');
  });
});
