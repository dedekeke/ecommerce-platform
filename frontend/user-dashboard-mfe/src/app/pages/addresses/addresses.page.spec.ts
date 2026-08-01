import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AddressesPage } from './addresses.page';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserService } from '../../core/services/user.service';
import { of } from 'rxjs';
import { Address } from '../../core/models/user.model';

const mockAddresses: Address[] = [
  {
    id: 'addr-1',
    userId: 'user-1',
    label: 'Home',
    firstName: 'John',
    lastName: 'Doe',
    street: '123 Main St',
    city: 'NYC',
    state: 'NY',
    postalCode: '10001',
    country: 'US',
    isDefault: true,
    type: 'shipping',
  },
];

describe('AddressesPage', () => {
  let fixture: ComponentFixture<AddressesPage>;
  let userServiceSpy: jasmine.SpyObj<UserService>;

  beforeEach(async () => {
    userServiceSpy = jasmine.createSpyObj('UserService', [
      'getAddresses',
      'createAddress',
      'updateAddress',
      'deleteAddress',
    ]);
    userServiceSpy.getAddresses.and.returnValue(of(mockAddresses));
    userServiceSpy.deleteAddress.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [AddressesPage, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserService, useValue: userServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AddressesPage);
    fixture.detectChanges();
  });

  it('should display page title', () => {
    expect(fixture.nativeElement.textContent).toContain('Addresses');
  });

  it('should render address cards after loading', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('app-address-card');
    expect(cards.length).toBe(1);
  });

  it('should show add address button', () => {
    const addBtn = fixture.nativeElement.querySelector('[data-testid="add-address-btn"]');
    expect(addBtn).toBeTruthy();
  });

  it('should call deleteAddress when delete is emitted', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.onDeleteAddress('addr-1');
    expect(userServiceSpy.deleteAddress).toHaveBeenCalledWith('me', 'addr-1');
  });
});
