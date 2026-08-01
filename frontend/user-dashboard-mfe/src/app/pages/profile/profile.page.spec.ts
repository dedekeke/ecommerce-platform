import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfilePage } from './profile.page';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserService } from '../../core/services/user.service';
import { of, throwError } from 'rxjs';
import { UserProfile } from '../../core/models/user.model';

const mockProfile: UserProfile = {
  id: 'user-1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john@example.com',
  createdAt: '2024-01-01T00:00:00Z',
};

describe('ProfilePage', () => {
  let fixture: ComponentFixture<ProfilePage>;
  let userServiceSpy: jasmine.SpyObj<UserService>;

  beforeEach(async () => {
    userServiceSpy = jasmine.createSpyObj('UserService', ['getProfile', 'updateProfile']);
    userServiceSpy.getProfile.and.returnValue(of(mockProfile));
    userServiceSpy.updateProfile.and.returnValue(of({ ...mockProfile, firstName: 'Jane' }));

    await TestBed.configureTestingModule({
      imports: [ProfilePage, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserService, useValue: userServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
  });

  it('should display the page title', () => {
    expect(fixture.nativeElement.textContent).toContain('Profile');
  });

  it('should load and display profile form after data loads', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const profileForm = fixture.nativeElement.querySelector('app-profile-form');
    expect(profileForm).toBeTruthy();
  });

  it('should call updateProfile when form is submitted', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.onSaveProfile({ firstName: 'Jane', lastName: 'Doe' });
    expect(userServiceSpy.updateProfile).toHaveBeenCalledWith('me', { firstName: 'Jane', lastName: 'Doe' });
  });

  it('should show error state when profile fails to load', async () => {
    userServiceSpy.getProfile.and.returnValue(throwError(() => new Error('Network error')));
    const errorFixture = TestBed.createComponent(ProfilePage);
    errorFixture.detectChanges();
    await errorFixture.whenStable();
    errorFixture.detectChanges();
    expect(errorFixture.nativeElement.textContent).toContain('Failed to load');
  });
});
