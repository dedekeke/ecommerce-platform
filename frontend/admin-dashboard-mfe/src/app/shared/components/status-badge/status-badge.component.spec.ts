import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusBadgeComponent } from './status-badge.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('StatusBadgeComponent', () => {
  let fixture: ComponentFixture<StatusBadgeComponent>;

  function createComponent(label: string, variant?: string) {
    fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('label', label);
    if (variant) fixture.componentRef.setInput('variant', variant);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should render the label text', () => {
    createComponent('PENDING');
    expect(fixture.nativeElement.textContent.trim()).toBe('PENDING');
  });

  it('should apply success variant class', () => {
    createComponent('DELIVERED', 'success');
    const badge = fixture.nativeElement.querySelector('.status-badge--success');
    expect(badge).toBeTruthy();
  });

  it('should apply error variant class', () => {
    createComponent('CANCELLED', 'error');
    const badge = fixture.nativeElement.querySelector('.status-badge--error');
    expect(badge).toBeTruthy();
  });

  it('should default to neutral variant', () => {
    createComponent('UNKNOWN');
    const badge = fixture.nativeElement.querySelector('.status-badge--neutral');
    expect(badge).toBeTruthy();
  });

  it('should set aria-label with the label value', () => {
    createComponent('ACTIVE');
    const badge = fixture.nativeElement.querySelector('.status-badge');
    expect(badge.getAttribute('aria-label')).toBe('Status: ACTIVE');
  });
});
