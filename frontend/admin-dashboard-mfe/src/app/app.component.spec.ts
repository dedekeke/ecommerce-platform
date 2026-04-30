import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
  });

  it('should render the sidenav with brand label', () => {
    expect(fixture.nativeElement.textContent).toContain('Admin');
  });

  it('should render nav links', () => {
    const links = fixture.nativeElement.querySelectorAll('a[mat-list-item]');
    expect(links.length).toBe(5);
  });

  it('should have accessible aria-label on the navigation', () => {
    const nav = fixture.nativeElement.querySelector('[aria-label="Admin dashboard navigation"]');
    expect(nav).toBeTruthy();
  });

  it('should include a router-outlet', () => {
    const outlet = fixture.nativeElement.querySelector('router-outlet');
    expect(outlet).toBeTruthy();
  });
});
