import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AppComponent } from './app.component';

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

  it('should render a navigation link to preferences', () => {
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[routerLink="/preferences"]');
    expect(link).toBeTruthy();
    expect(link.textContent).toContain('Preferences');
  });

  it('should render navigation links for every dashboard section', () => {
    const routes = ['/', '/profile', '/orders', '/addresses', '/wishlist', '/payment-methods', '/preferences'];
    routes.forEach((route) => {
      expect(fixture.nativeElement.querySelector(`a[routerLink="${route}"]`)).toBeTruthy();
    });
  });
});
