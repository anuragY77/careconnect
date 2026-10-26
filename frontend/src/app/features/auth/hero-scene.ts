import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';
import {
  AdditiveBlending,
  BufferGeometry,
  CanvasTexture,
  Color,
  Float32BufferAttribute,
  Group,
  LineBasicMaterial,
  LineSegments,
  PerspectiveCamera,
  Points,
  PointsMaterial,
  Scene,
  WebGLRenderer,
} from 'three';

/** Deterministic random numbers, so the scene looks the same on every load. */
function mulberry32(seed: number): () => number {
  let a = seed;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function glowSprite(): CanvasTexture {
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const g = c.getContext('2d')!;
  const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, 'rgba(255,255,255,1)');
  grad.addColorStop(0.35, 'rgba(255,255,255,0.45)');
  grad.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = grad;
  g.fillRect(0, 0, 64, 64);
  return new CanvasTexture(c);
}

/**
 * Builds the DNA double helix and runs it. Returns a cleanup function.
 * Does nothing when WebGL is unavailable (the page still looks right without it).
 */
function startScene(canvas: HTMLCanvasElement, host: HTMLElement): () => void {
  if (typeof WebGL2RenderingContext === 'undefined') return () => undefined;

  let renderer: WebGLRenderer;
  try {
    renderer = new WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'low-power' });
  } catch {
    return () => undefined;
  }

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const rand = mulberry32(7);
  const scene = new Scene();
  const camera = new PerspectiveCamera(38, 1, 0.1, 100);
  camera.position.set(0, 0, 13);

  const world = new Group();
  scene.add(world);

  // Helix: two strands and a rung every few points.
  const N = 360;
  const height = 10;
  const radius = 1.5;
  const turns = 3.6;
  const strandPos: number[] = [];
  const strandCol: number[] = [];
  const rungPos: number[] = [];
  const a = new Color('#5eead4');
  const b = new Color('#9bdcff');
  const A: [number, number, number][] = [];
  const B: [number, number, number][] = [];
  for (let i = 0; i < N; i++) {
    const t = i / (N - 1);
    const y = (t - 0.5) * height;
    const ang = t * turns * Math.PI * 2;
    const jitter = () => (rand() - 0.5) * 0.06;
    const pa: [number, number, number] = [radius * Math.cos(ang) + jitter(), y, radius * Math.sin(ang) + jitter()];
    const pb: [number, number, number] = [
      radius * Math.cos(ang + Math.PI) + jitter(),
      y,
      radius * Math.sin(ang + Math.PI) + jitter(),
    ];
    A.push(pa);
    B.push(pb);
    strandPos.push(...pa, ...pb);
    strandCol.push(a.r, a.g, a.b, b.r, b.g, b.b);
    if (i % 6 === 0) rungPos.push(...pa, ...pb);
  }

  const sprite = glowSprite();
  const strandGeo = new BufferGeometry();
  strandGeo.setAttribute('position', new Float32BufferAttribute(strandPos, 3));
  strandGeo.setAttribute('color', new Float32BufferAttribute(strandCol, 3));
  const strandMat = new PointsMaterial({
    size: 0.2,
    map: sprite,
    vertexColors: true,
    transparent: true,
    depthWrite: false,
    blending: AdditiveBlending,
    opacity: 0.95,
  });
  const strands = new Points(strandGeo, strandMat);

  const rungGeo = new BufferGeometry();
  rungGeo.setAttribute('position', new Float32BufferAttribute(rungPos, 3));
  const rungMat = new LineBasicMaterial({
    color: '#2dd4bf',
    transparent: true,
    opacity: 0.22,
    blending: AdditiveBlending,
    depthWrite: false,
  });
  const rungs = new LineSegments(rungGeo, rungMat);

  const helix = new Group();
  helix.add(strands, rungs);
  helix.rotation.z = 0.22;
  world.add(helix);

  // Depth dust around the helix.
  const dustPos: number[] = [];
  for (let i = 0; i < 420; i++) {
    const r = 2.6 + rand() * 6;
    const th = rand() * Math.PI * 2;
    dustPos.push(r * Math.cos(th), (rand() - 0.5) * 13, r * Math.sin(th) - 2);
  }
  const dustGeo = new BufferGeometry();
  dustGeo.setAttribute('position', new Float32BufferAttribute(dustPos, 3));
  const dustMat = new PointsMaterial({
    size: 0.09,
    map: sprite,
    color: '#7fe9d9',
    transparent: true,
    opacity: 0.45,
    depthWrite: false,
    blending: AdditiveBlending,
  });
  const dust = new Points(dustGeo, dustMat);
  world.add(dust);

  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));

  const resize = (): void => {
    const w = host.clientWidth;
    const h = host.clientHeight;
    if (w === 0 || h === 0) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    // Fit the helix into the room the text leaves free, measured from the real text, so it
    // never crosses the headline at any panel size or browser zoom.
    const unitsPerPx = (2 * Math.tan((camera.fov * Math.PI) / 360) * camera.position.z) / h;
    const hostLeft = host.getBoundingClientRect().left;
    let textRight = 0;
    host.closest('aside')?.querySelectorAll('h1, p').forEach((el) => {
      const range = document.createRange();
      range.selectNodeContents(el);
      textRight = Math.max(textRight, range.getBoundingClientRect().right - hostLeft);
    });
    const free = w - textRight - 24;
    const helixHalfUnits = 2.5; // radius + tilt + pointer parallax, at scale 1
    if (free >= 170) {
      const scale = Math.min(0.85, (free / 2) * unitsPerPx / helixHalfUnits);
      world.position.x = (textRight + 12 + free / 2 - w / 2) * unitsPerPx;
      world.scale.setScalar(scale);
      host.style.setProperty('--hero-opacity', '1');
    } else {
      // No free column (phones): centred behind the text, smaller and dimmer.
      world.position.x = 0;
      world.scale.setScalar(0.7);
      host.style.setProperty('--hero-opacity', '0.4');
    }
    if (reduced) draw(1.1);
  };

  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  const onMove = (e: PointerEvent): void => {
    pointer.tx = e.clientX / window.innerWidth - 0.5;
    pointer.ty = e.clientY / window.innerHeight - 0.5;
  };

  const draw = (t: number): void => {
    helix.rotation.y = t * 0.14;
    dust.rotation.y = -t * 0.03;
    const pulse = 1 + 0.015 * Math.sin(t * 1.6);
    helix.scale.setScalar(pulse);
    pointer.x += (pointer.tx - pointer.x) * 0.04;
    pointer.y += (pointer.ty - pointer.y) * 0.04;
    world.rotation.y = pointer.x * 0.5;
    world.rotation.x = pointer.y * 0.25;
    renderer.render(scene, camera);
  };

  let raf = 0;
  let inView = true;
  const start = performance.now();
  const loop = (): void => {
    draw((performance.now() - start) / 1000);
    raf = requestAnimationFrame(loop);
  };
  const sync = (): void => {
    cancelAnimationFrame(raf);
    if (!reduced && inView && !document.hidden) raf = requestAnimationFrame(loop);
  };

  const ro = new ResizeObserver(resize);
  ro.observe(host);
  const io = new IntersectionObserver(([entry]) => {
    inView = entry.isIntersecting;
    sync();
  });
  io.observe(host);
  document.addEventListener('visibilitychange', sync);
  if (!reduced) window.addEventListener('pointermove', onMove, { passive: true });

  resize();
  draw(reduced ? 1.1 : 0);
  canvas.style.opacity = 'var(--hero-opacity, 1)';
  sync();

  return () => {
    cancelAnimationFrame(raf);
    ro.disconnect();
    io.disconnect();
    document.removeEventListener('visibilitychange', sync);
    window.removeEventListener('pointermove', onMove);
    strandGeo.dispose();
    rungGeo.dispose();
    dustGeo.dispose();
    strandMat.dispose();
    rungMat.dispose();
    dustMat.dispose();
    sprite.dispose();
    renderer.dispose();
  };
}

/** Decorative 3D DNA helix for the sign-in hero. Hidden from assistive technology. */
@Component({
  selector: 'app-hero-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  template: `<canvas #canvas class="block size-full opacity-0 transition-opacity duration-[1400ms] ease-out"></canvas>`,
})
export class HeroScene {
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  constructor() {
    const destroyRef = inject(DestroyRef);
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    afterNextRender(() => {
      destroyRef.onDestroy(startScene(this.canvas().nativeElement, host));
    });
  }
}
