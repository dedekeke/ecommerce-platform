import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormDrawerComponent } from './form-drawer.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('FormDrawerComponent', () => {
  let fixture: ComponentFixture<FormDrawerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormDrawerComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(FormDrawerComponent);
  });

  it('should render the drawer title when open', () => {
    fixture.componentRef.setInput('title', 'Edit Product');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Edit Product');
  });

  it('should emit drawerClose when close button is clicked', async () => {
    fixture.componentRef.setInput('title', 'Test Drawer');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    let emitted = false;
    fixture.componentInstance.drawerClose.subscribe(() => (emitted = true));

    const closeBtn = fixture.nativeElement.querySelector('[data-testid="drawer-close-btn"]');
    expect(closeBtn).toBeTruthy();
    closeBtn.click();
    fixture.detectChanges();

    expect(emitted).toBeTrue();
  });

  it('should render close button with aria-label', async () => {
    fixture.componentRef.setInput('title', 'Test');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const closeBtn = fixture.nativeElement.querySelector('[data-testid="drawer-close-btn"]');
    expect(closeBtn.getAttribute('aria-label')).toBe('Close drawer');
  });
});
