import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PreferencesService } from '../../core/services/preferences.service';
import { ToastService } from '../../core/services/toast.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../../core/models/preferences.model';
import { PreferencesFormComponent } from '../../shared/components/preferences-form/preferences-form.component';

@Component({
  selector: 'app-preferences-page',
  standalone: true,
  imports: [CommonModule, PreferencesFormComponent],
  template: `
    <div class="preferences-page container">
      <h1 class="preferences-page__title">Preferences</h1>
      <p class="preferences-page__subtitle">
        Manage notifications, display settings, and email subscriptions
      </p>
      <div class="preferences-page__card">
        <app-preferences-form [preferences]="preferences()" (formSubmit)="onSave($event)" />
      </div>
    </div>
  `,
  styleUrl: './preferences.page.scss',
})
export class PreferencesPage implements OnInit {
  private readonly preferencesService = inject(PreferencesService);
  private readonly toast = inject(ToastService);

  readonly preferences = signal<UserPreferences>(DEFAULT_PREFERENCES);

  ngOnInit(): void {
    this.preferencesService.getPreferences().subscribe((prefs) => this.preferences.set(prefs));
  }

  onSave(preferences: UserPreferences): void {
    this.preferencesService.save(preferences);
    this.toast.success('Preferences updated');
  }
}
