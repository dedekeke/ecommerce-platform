import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserAdminService } from './user-admin.service';
import { AdminUser, PagedUsers } from '../models/user-admin.model';

const mockUser: AdminUser = {
  id: 'user-1',
  firstName: 'Alice',
  lastName: 'Smith',
  email: 'alice@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00Z',
};

const mockPagedUsers: PagedUsers = {
  content: [mockUser],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

describe('UserAdminService', () => {
  let service: UserAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch paginated users', () => {
    let result: PagedUsers | undefined;
    service.getUsers({ page: 0, size: 10 }).subscribe((r) => (result = r));
    const req = httpMock.expectOne((r) => r.url === '/api/users');
    expect(req.request.method).toBe('GET');
    req.flush(mockPagedUsers);
    expect(result).toEqual(mockPagedUsers);
  });

  it('should include optional search and role filters', () => {
    service.getUsers({ page: 0, size: 10, search: 'alice', role: 'ADMIN' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/users');
    expect(req.request.params.get('search')).toBe('alice');
    expect(req.request.params.get('role')).toBe('ADMIN');
    req.flush(mockPagedUsers);
  });

  it('should fetch user by id', () => {
    let result: AdminUser | undefined;
    service.getUserById('user-1').subscribe((r) => (result = r));
    const req = httpMock.expectOne('/api/users/user-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);
    expect(result).toEqual(mockUser);
  });

  it('should update user role with PATCH', () => {
    service.updateUserRole('user-1', { role: 'ADMIN' }).subscribe();
    const req = httpMock.expectOne('/api/users/user-1/role');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ role: 'ADMIN' });
    req.flush({ ...mockUser, role: 'ADMIN' });
  });
});
