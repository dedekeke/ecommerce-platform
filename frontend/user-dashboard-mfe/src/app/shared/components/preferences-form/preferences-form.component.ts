import { Component, OnInit, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import {
  NOTIFICATION_CATEGORIES,
  NOTIFICATION_CATEGORY_LABELS,
  NotificationCategory,
  SUPPORTED_CURRENCIES,
  SUPPORTED_LANGUAGES,
  UserPreferences,
} from '../../../core/models/preferences.model';

@Component({
  selector: 'app-preferences-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatSlideToggleModule,
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
  ],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="preferences-form" novalidate>
      <section class="preferences-form__section" formGroupName="notifications">
        <h2>Notifications</h2>
        @for (category of categories; track category) {
          <div class="preferences-form__category" [formGroupName]="category">
            <h3>{{ categoryLabels[category] }}</h3>
            <div class="preferences-form__toggles">
              <mat-slide-toggle
                formControlName="email"
                [attr.aria-label]="categoryLabels[category] + ' email notifications'"
                >Email</mat-slide-toggle
              >
              <mat-slide-toggle
                formControlName="sms"
                [attr.aria-label]="categoryLabels[category] + ' SMS notifications'"
                >SMS</mat-slide-toggle
              >
              <mat-slide-toggle
                formControlName="push"
                [attr.aria-label]="categoryLabels[category] + ' push notifications'"
                >Push</mat-slide-toggle
              >
            </div>
          </div>
        }
      </section>

      <section class="preferences-form__section" formGroupName="display">
        <h2>Display</h2>
        <div class="preferences-form__selects">
          <mat-form-field appearance="outline">
            <mat-label>Theme</mat-label>
            <mat-select formControlName="theme" data-testid="theme-select">
              <mat-option value="light">Light</mat-option>
              <mat-option value="dark">Dark</mat-option>
              <mat-option value="system">System</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Language</mat-label>
            <mat-select formControlName="language" data-testid="language-select">
              @for (lang of languages; track lang.value) {
                <mat-option [value]="lang.value">{{ lang.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Currency</mat-label>
            <mat-select formControlName="currency" data-testid="currency-select">
              @for (currency of currencies; track currency.value) {
                <mat-option [value]="currency.value">{{ currency.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>
      </section>

      <section class="preferences-form__section" formGroupName="emailSubscriptions">
        <h2>Email Subscriptions</h2>
        <div class="preferences-form__toggles">
          <mat-slide-toggle formControlName="newsletter">Newsletter</mat-slide-toggle>
          <mat-slide-toggle formControlName="productAnnouncements">Product Announcements</mat-slide-toggle>
          <mat-slide-toggle formControlName="surveys">Surveys & Feedback</mat-slide-toggle>
        </div>
      </section>

      <div class="preferences-form__actions">
        <button
          mat-flat-button
          color="primary"
          type="submit"
          data-testid="save-preferences-btn"
          aria-label="Save preferences"
        >
          Save Preferences
        </button>
      </div>
    </form>
  `,
  styleUrl: './preferences-form.component.scss',
})
export class PreferencesFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly preferences = input.required<UserPreferences>();
  readonly formSubmit = output<UserPreferences>();

  readonly categories = NOTIFICATION_CATEGORIES;
  readonly categoryLabels = NOTIFICATION_CATEGORY_LABELS;
  readonly languages = SUPPORTED_LANGUAGES;
  readonly currencies = SUPPORTED_CURRENCIES;

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.buildForm(this.preferences());
  }

  onSubmit(): void {
    this.formSubmit.emit(this.form.getRawValue() as UserPreferences);
  }

  private buildForm(prefs: UserPreferences): FormGroup {
    const notificationGroups = this.categories.reduce<Record<string, FormGroup>>((acc, category: NotificationCategory) => {
      acc[category] = this.fb.group({
        email: [prefs.notifications[category].email],
        sms: [prefs.notifications[category].sms],
        push: [prefs.notifications[category].push],
      });
      return acc;
    }, {});

    return this.fb.group({
      notifications: this.fb.group(notificationGroups),
      display: this.fb.group({
        theme: [prefs.display.theme],
        language: [prefs.display.language],
        currency: [prefs.display.currency],
      }),
      emailSubscriptions: this.fb.group({
        newsletter: [prefs.emailSubscriptions.newsletter],
        productAnnouncements: [prefs.emailSubscriptions.productAnnouncements],
        surveys: [prefs.emailSubscriptions.surveys],
      }),
    });
  }
}
