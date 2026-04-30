import { Component, input, output, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { UserProfile, UpdateProfilePayload } from '../../../core/models/user.model';

@Component({
  selector: 'app-profile-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="profile-form" novalidate>
      <div class="profile-form__row">
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

      <mat-form-field appearance="outline" class="profile-form__full">
        <mat-label>Email</mat-label>
        <input matInput formControlName="email" type="email" [readonly]="true" />
        <mat-hint>Email cannot be changed here</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline" class="profile-form__full">
        <mat-label>Phone</mat-label>
        <input matInput formControlName="phone" type="tel" placeholder="+1 555 000 0000" />
      </mat-form-field>

      <div class="profile-form__actions">
        <button
          mat-flat-button
          color="primary"
          type="submit"
          data-testid="save-profile-btn"
          [disabled]="form.invalid || form.pristine"
          aria-label="Save profile"
        >
          Save Changes
        </button>
      </div>
    </form>
  `,
  styleUrl: './profile-form.component.scss',
})
export class ProfileFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly profile = input.required<UserProfile>();
  readonly formSubmit = output<UpdateProfilePayload>();

  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: [{ value: '', disabled: false }],
    phone: [''],
  });

  ngOnInit(): void {
    const p = this.profile();
    this.form.patchValue({
      firstName: p.firstName,
      lastName: p.lastName,
      email: p.email,
      phone: p.phone ?? '',
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.formSubmit.emit({
      firstName: value.firstName!,
      lastName: value.lastName!,
      phone: value.phone ?? undefined,
    });
  }
}
