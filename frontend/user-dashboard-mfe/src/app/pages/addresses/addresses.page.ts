import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { UserService } from '../../core/services/user.service';
import { Address, CreateAddressPayload } from '../../core/models/user.model';
import { AddressCardComponent } from '../../shared/components/address-card/address-card.component';
import { AddressFormComponent } from '../../shared/components/address-form/address-form.component';

const DEMO_USER_ID = 'me';

@Component({
  selector: 'app-addresses-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    AddressCardComponent,
    AddressFormComponent,
  ],
  template: `
    <div class="addresses-page container">
      <div class="addresses-page__header">
        <h1 class="addresses-page__title">Addresses</h1>
        <button
          mat-flat-button
          color="primary"
          data-testid="add-address-btn"
          (click)="showForm.set(true)"
          aria-label="Add new address"
        >
          <mat-icon>add</mat-icon>
          Add Address
        </button>
      </div>

      @if (showForm()) {
        <div class="addresses-page__form-panel">
          <h2 class="addresses-page__form-title">
            {{ editingAddress() ? 'Edit Address' : 'New Address' }}
          </h2>
          <app-address-form
            [existingAddress]="editingAddress()"
            (formSubmit)="onSaveAddress($event)"
            (cancel)="onCancelForm()"
          />
        </div>
      }

      @if (loading()) {
        <div class="addresses-page__loading" role="status" aria-label="Loading addresses">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (addresses().length === 0) {
        <div class="addresses-page__empty">
          <p>No addresses yet. Add one to get started!</p>
        </div>
      } @else {
        <div class="addresses-grid">
          @for (address of addresses(); track address.id) {
            <app-address-card
              [address]="address"
              (edit)="onEditAddress($event)"
              (delete)="onDeleteAddress($event)"
            />
          }
        </div>
      }
    </div>
  `,
  styleUrl: './addresses.page.scss',
})
export class AddressesPage implements OnInit {
  private readonly userService = inject(UserService);

  readonly addresses = signal<Address[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);
  readonly editingAddress = signal<Address | null>(null);

  ngOnInit(): void {
    this.loadAddresses();
  }

  onEditAddress(address: Address): void {
    this.editingAddress.set(address);
    this.showForm.set(true);
  }

  onDeleteAddress(addressId: string): void {
    this.userService.deleteAddress(DEMO_USER_ID, addressId).subscribe({
      next: () => {
        this.addresses.update((list) => list.filter((a) => a.id !== addressId));
      },
    });
  }

  onSaveAddress(payload: CreateAddressPayload): void {
    const editing = this.editingAddress();
    const request$ = editing
      ? this.userService.updateAddress(DEMO_USER_ID, editing.id, payload)
      : this.userService.createAddress(DEMO_USER_ID, payload);

    request$.subscribe({
      next: (saved) => {
        this.addresses.update((list) => {
          const idx = list.findIndex((a) => a.id === saved.id);
          return idx >= 0
            ? list.map((a, i) => (i === idx ? saved : a))
            : [...list, saved];
        });
        this.onCancelForm();
      },
    });
  }

  onCancelForm(): void {
    this.showForm.set(false);
    this.editingAddress.set(null);
  }

  private loadAddresses(): void {
    this.userService.getAddresses(DEMO_USER_ID).subscribe({
      next: (list) => {
        this.addresses.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
