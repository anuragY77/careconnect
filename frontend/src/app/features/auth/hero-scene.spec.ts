import { TestBed } from '@angular/core/testing';
import { HeroScene } from './hero-scene';

describe('HeroScene', () => {
  it('renders a decorative canvas and does not crash without WebGL', async () => {
    const f = TestBed.createComponent(HeroScene);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('canvas')).not.toBeNull();
    f.destroy();
  });
});
