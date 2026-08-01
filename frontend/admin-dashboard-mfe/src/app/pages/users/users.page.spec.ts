import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UsersPage } from './users.page';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserAdminService } from '../../core/services/user-admin.service';
import { of } from 'rxjs';
import { AdminUser, PagedUsers } from '../../core/models/user-admin.model';

const mockUser: AdminUser = {
  id: 'user-1',
  firstName: 'Alice',
  lastName: 'Smith',
  email: 'alice@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00Z',
};

const mockPaged: PagedUsers = {
  content: [mockUser],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

describe('UsersPage', () => {
  let fixture: ComponentFixture<UsersPage>;
  let userServiceSpy: jasmine.SpyObj<UserAdminService>;

  beforeEach(async () => {
    userServiceSpy = jasmine.createSpyObj('UserAdminService', ['getUsers', 'updateUserRole']);
    userServiceSpy.getUsers.and.returnValue(of(mockPaged));

    await TestBed.configureTestingModule({
      imports: [UsersPage, NoopAnimationsModule],
      providers: [
        { provide: UserAdminService, useValue: userServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Users');
  });

  it('should call getUsers on init', () => {
    expect(userServiceSpy.getUsers).toHaveBeenCalledWith({ page: 0, size: 10 });
  });

  it('should open drawer and set selectedUser on row click', () => {
    fixture.componentInstance.onRowClick(mockUser as unknown as Record<string, unknown> & AdminUser);
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedUser()?.id).toBe('user-1');
    expect(fixture.componentInstance.selectedRole()).toBe('CUSTOMER');
  });

  it('should close drawer and clear selectedUser on closeDrawer', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.selectedUser.set(mockUser);
    fixture.componentInstance.closeDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
    expect(fixture.componentInstance.selectedUser()).toBeNull();
  });

  it('should call updateUserRole when onUpdateRole is invoked', async () => {
    userServiceSpy.updateUserRole.and.returnValue(of({ ...mockUser, role: 'ADMIN' }));
    fixture.componentInstance.selectedUser.set(mockUser);
    fixture.componentInstance.selectedRole.set('ADMIN');
    fixture.componentInstance.onUpdateRole();
    await fixture.whenStable();
    expect(userServiceSpy.updateUserRole).toHaveBeenCalledWith('user-1', { role: 'ADMIN' });
  });

  it('should not call updateUserRole when no user selected', () => {
    fixture.componentInstance.selectedUser.set(null);
    fixture.componentInstance.onUpdateRole();
    expect(userServiceSpy.updateUserRole).not.toHaveBeenCalled();
  });

  it('should return correct role variants', () => {
    expect(fixture.componentInstance.roleVariant('ADMIN')).toBe('error');
    expect(fixture.componentInstance.roleVariant('MODERATOR')).toBe('info');
    expect(fixture.componentInstance.roleVariant('CUSTOMER')).toBe('neutral');
  });

  it('should return correct status variants', () => {
    expect(fixture.componentInstance.statusVariant('ACTIVE')).toBe('success');
    expect(fixture.componentInstance.statusVariant('SUSPENDED')).toBe('error');
    expect(fixture.componentInstance.statusVariant('INACTIVE')).toBe('neutral');
  });
});
