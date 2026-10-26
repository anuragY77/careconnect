import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AllergyStrip } from './allergy-strip';
import { ClinicalAlert } from './clinical-alert';
import { StatusChip } from './status-chip';

function el<T>(f: ComponentFixture<T>): HTMLElement {
  return f.nativeElement as HTMLElement;
}

describe('AllergyStrip', () => {
  it('lists allergies and flags severe ones in text', async () => {
    const f = TestBed.createComponent(AllergyStrip);
    f.componentRef.setInput('allergies', [
      { substance: 'Penicillin', reaction: 'Anaphylaxis', severity: 'SEVERE' },
      { substance: 'Sulfa drugs', reaction: 'Rash', severity: 'MODERATE' },
    ]);
    await f.whenStable();
    const text = el(f).textContent ?? '';
    expect(text).toContain('Penicillin');
    expect(text).toContain('Severe');
    expect(text).toContain('Sulfa drugs');
    expect(el(f).querySelectorAll('li').length).toBe(2);
  });

  it('shows an explicit state when there are no known allergies', async () => {
    const f = TestBed.createComponent(AllergyStrip);
    await f.whenStable();
    expect(el(f).textContent).toContain('No known allergies recorded');
    expect(el(f).querySelector('[aria-label="Allergies"]')).not.toBeNull();
  });
});

describe('ClinicalAlert', () => {
  it.each([
    ['INFO', 'Information', 'status'],
    ['WARNING', 'Warning', 'status'],
    ['CRITICAL', 'Critical', 'alert'],
  ] as const)('%s is announced with text and the right role', async (level, label, role) => {
    const f = TestBed.createComponent(ClinicalAlert);
    f.componentRef.setInput('level', level);
    f.componentRef.setInput('heading', 'Something happened');
    await f.whenStable();
    expect(el(f).textContent).toContain(`${label}: Something happened`);
    expect(el(f).querySelector(`[role="${role}"]`)).not.toBeNull();
  });
});

describe('StatusChip', () => {
  it('renders its label with an icon', async () => {
    const f = TestBed.createComponent(StatusChip);
    f.componentRef.setInput('label', 'Locked');
    f.componentRef.setInput('kind', 'danger');
    await f.whenStable();
    expect(el(f).textContent).toContain('Locked');
    expect(el(f).querySelector('ng-icon')).not.toBeNull();
  });
});
