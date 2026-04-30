import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserService } from './user.service';
import { UserProfile, Address, CreateAddressPayload } from '../models/user.model';

const mockProfile: UserProfile = {
  id: 'user-1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john@example.com',
  createdAt: '2024-01-01T00:00:00Z',
};

const mockAddress: Address = {
  id: 'addr-1',
  userId: 'user-1',
  label: 'Home',
  firstName: 'John',
  lastName: 'Doe',
  street: '123 Main',
  city: 'NYC',
  state: 'NY',
  postalCode: '10001',
  country: 'US',
  isDefault: true,
  type: 'shipping',
};

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET user profile', () => {
    service.getProfile('user-1').subscribe((p) => expect(p).toEqual(mockProfile));
    const req = httpMock.expectOne('/api/users/user-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockProfile);
  });

  it('should PATCH to update profile', () => {
    const payload = { firstName: 'Jane', lastName: 'Doe' };
    service.updateProfile('user-1', payload).subscribe((p) => expect(p.firstName).toBe('Jane'));
    const req = httpMock.expectOne('/api/users/user-1');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...mockProfile, firstName: 'Jane' });
  });

  it('should GET addresses for user', () => {
    service.getAddresses('user-1').subscribe((list) => expect(list.length).toBe(1));
    const req = httpMock.expectOne('/api/users/user-1/addresses');
    expect(req.request.method).toBe('GET');
    req.flush([mockAddress]);
  });

  it('should POST to create address', () => {
    const payload: CreateAddressPayload = {
      label: 'Work',
      firstName: 'John',
      lastName: 'Doe',
      street: '456 Work Ave',
      city: 'NYC',
      state: 'NY',
      postalCode: '10002',
      country: 'US',
      isDefault: false,
      type: 'billing',
    };
    service.createAddress('user-1', payload).subscribe((a) => expect(a.label).toBe('Work'));
    const req = httpMock.expectOne('/api/users/user-1/addresses');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'addr-2', userId: 'user-1', ...payload });
  });

  it('should PUT to update address', () => {
    const payload: CreateAddressPayload = { ...mockAddress };
    service.updateAddress('user-1', 'addr-1', payload).subscribe();
    const req = httpMock.expectOne('/api/users/user-1/addresses/addr-1');
    expect(req.request.method).toBe('PUT');
    req.flush(mockAddress);
  });

  it('should DELETE an address', () => {
    service.deleteAddress('user-1', 'addr-1').subscribe();
    const req = httpMock.expectOne('/api/users/user-1/addresses/addr-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
