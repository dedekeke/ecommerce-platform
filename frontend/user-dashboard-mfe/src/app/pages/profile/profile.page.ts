import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { UserService } from '../../core/services/user.service';
import { UserProfile, UpdateProfilePayload } from '../../core/models/user.model';
import { ProfileFormComponent } from '../../shared/components/profile-form/profile-form.component';

const DEMO_USER_ID = 'me';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule, ProfileFormComponent],
  template: `
    <div class="profile-page container">
      <h1 class="profile-page__title">Profile</h1>
      <p class="profile-page__subtitle">Manage your personal information</p>

      @if (loading()) {
        <div class="profile-page__loading" role="status" aria-label="Loading profile">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (error()) {
        <div class="profile-page__error" role="alert">
          <p>Failed to load profile. Please try again.</p>
        </div>
      } @else if (profile()) {
        <div class="profile-page__card">
          <app-profile-form
            [profile]="profile()!"
            (formSubmit)="onSaveProfile($event)"
          />
          @if (saveSuccess()) {
            <div class="profile-page__success" role="status">Profile updated successfully!</div>
          }
        </div>
      }
    </div>
  `,
  styleUrl: './profile.page.scss',
})
export class ProfilePage implements OnInit {
  private readonly userService = inject(UserService);

  readonly profile = signal<UserProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly saveSuccess = signal(false);

  ngOnInit(): void {
    this.userService.getProfile(DEMO_USER_ID).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  onSaveProfile(payload: UpdateProfilePayload): void {
    this.userService.updateProfile(DEMO_USER_ID, payload).subscribe({
      next: (updated) => {
        this.profile.set(updated);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
    });
  }
}
