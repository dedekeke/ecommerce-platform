import { ComponentFixture, TestBed } from '@angular/core/testing';
import { KpiCardComponent } from './kpi-card.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('KpiCardComponent', () => {
  let fixture: ComponentFixture<KpiCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiCardComponent, NoopAnimationsModule],
    }).compileComponents();
  });

  function createComponent(opts: { title: string; value: number | string; delta?: number | null; prefix?: string; suffix?: string }) {
    fixture = TestBed.createComponent(KpiCardComponent);
    fixture.componentRef.setInput('title', opts.title);
    fixture.componentRef.setInput('value', opts.value);
    if (opts.delta !== undefined) fixture.componentRef.setInput('delta', opts.delta);
    if (opts.prefix) fixture.componentRef.setInput('prefix', opts.prefix);
    if (opts.suffix) fixture.componentRef.setInput('suffix', opts.suffix);
    fixture.detectChanges();
    return fixture;
  }

  it('should display the title', () => {
    createComponent({ title: "Today's Orders", value: 42 });
    expect(fixture.nativeElement.textContent).toContain("Today's Orders");
  });

  it('should display numeric value', () => {
    createComponent({ title: 'Revenue', value: 3200, prefix: '$' });
    const el = fixture.nativeElement.querySelector('[data-testid="kpi-value"]');
    expect(el.textContent.trim()).toBe('$3200');
  });

  it('should show positive delta with up indicator', () => {
    createComponent({ title: 'Orders', value: 42, delta: 5 });
    const delta = fixture.nativeElement.querySelector('[data-testid="kpi-delta"]');
    expect(delta).toBeTruthy();
    expect(delta.classList.contains('kpi-card__delta--up')).toBeTrue();
    expect(delta.textContent).toContain('+5%');
  });

  it('should show negative delta with down indicator', () => {
    createComponent({ title: 'Revenue', value: 100, delta: -3 });
    const delta = fixture.nativeElement.querySelector('[data-testid="kpi-delta"]');
    expect(delta.classList.contains('kpi-card__delta--down')).toBeTrue();
    expect(delta.textContent).toContain('-3%');
  });

  it('should not render delta element when delta is null', () => {
    createComponent({ title: 'Stock', value: 7, delta: null });
    const delta = fixture.nativeElement.querySelector('[data-testid="kpi-delta"]');
    expect(delta).toBeNull();
  });

  it('should have an accessible aria-label on the card', () => {
    createComponent({ title: 'Pending', value: 3 });
    const card = fixture.nativeElement.querySelector('mat-card');
    expect(card.getAttribute('aria-label')).toContain('Pending');
  });
});
