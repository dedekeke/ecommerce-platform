import { Component, input, output, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { Address, CreateAddressPayload } from '../../../core/models/user.model';

@Component({
  selector: 'app-address-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
  ],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="address-form" novalidate>
      <div class="address-form__row">
        <mat-form-field appearance="outline">
          <mat-label>Label (e.g. Home)</mat-label>
          <input matInput formControlName="label" placeholder="Home" />
          @if (form.get('label')?.errors?.['required'] && form.get('label')?.touched) {
            <mat-error>This field needs some love</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="type">
            <mat-option value="shipping">Shipping</mat-option>
            <mat-option value="billing">Billing</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <div class="address-form__row">
        <mat-form-field appearance="outline">
          <mat-label>First Name</mat-label>
          <input matInput formControlName="firstName" />
          @if (form.get('firstName')?.errors?.['required'] && form.get('firstName')?.touched) {
            <mat-error>This field needs some love</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Last Name</mat-label>
          <input matInput formControlName="lastName" />
          @if (form.get('lastName')?.errors?.['required'] && form.get('lastName')?.touched) {
            <mat-error>This field needs some love</mat-error>
          }
        </mat-form-field>
      </div>

      <mat-form-field appearance="outline" class="address-form__full">
        <mat-label>Street Address</mat-label>
        <input matInput formControlName="street" />
        @if (form.get('street')?.errors?.['required'] && form.get('street')?.touched) {
          <mat-error>This field needs some love</mat-error>
        }
      </mat-form-field>

      <div class="address-form__row">
        <mat-form-field appearance="outline">
          <mat-label>City</mat-label>
          <input matInput formControlName="city" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>State / Province</mat-label>
          <input matInput formControlName="state" />
        </mat-form-field>
      </div>

      <div class="address-form__row">
        <mat-form-field appearance="outline">
          <mat-label>Postal Code</mat-label>
          <input matInput formControlName="postalCode" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Country</mat-label>
          <input matInput formControlName="country" placeholder="US" />
        </mat-form-field>
      </div>

      <mat-checkbox formControlName="isDefault" class="address-form__default">
        Set as default address
      </mat-checkbox>

      <div class="address-form__actions">
        <button
          mat-button
          type="button"
          (click)="cancel.emit()"
          aria-label="Cancel"
        >
          Cancel
        </button>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          data-testid="submit-btn"
          [disabled]="form.invalid"
          aria-label="Save address"
        >
          Save Address
        </button>
      </div>
    </form>
  `,
  styleUrl: './address-form.component.scss',
})
export class AddressFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly existingAddress = input<Address | null>(null);
  readonly formSubmit = output<CreateAddressPayload>();
  readonly cancel = output<void>();

  form = this.fb.group({
    label: ['', Validators.required],
    type: ['shipping' as 'shipping' | 'billing', Validators.required],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    street: ['', Validators.required],
    city: ['', Validators.required],
    state: ['', Validators.required],
    postalCode: ['', Validators.required],
    country: ['', Validators.required],
    isDefault: [false],
  });

  ngOnInit(): void {
    const existing = this.existingAddress();
    if (existing) {
      this.form.patchValue(existing);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.formSubmit.emit({
      label: value.label!,
      type: value.type!,
      firstName: value.firstName!,
      lastName: value.lastName!,
      street: value.street!,
      city: value.city!,
      state: value.state!,
      postalCode: value.postalCode!,
      country: value.country!,
      isDefault: value.isDefault ?? false,
    });
  }
}
