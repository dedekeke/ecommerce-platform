import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-form-drawer',
  standalone: true,
  imports: [CommonModule, MatSidenavModule, MatButtonModule, MatIconModule],
  template: `
    <mat-sidenav-container class="drawer-container" [hasBackdrop]="true">
      <mat-sidenav
        #drawer
        position="end"
        [opened]="open()"
        mode="over"
        class="form-drawer"
        role="dialog"
        [attr.aria-label]="title()"
        (closedStart)="drawerClose.emit()"
      >
        <div class="form-drawer__header">
          <h2 class="form-drawer__title">{{ title() }}</h2>
          <button
            mat-icon-button
            (click)="drawerClose.emit()"
            data-testid="drawer-close-btn"
            aria-label="Close drawer"
          >
            <mat-icon>close</mat-icon>
          </button>
        </div>
        <div class="form-drawer__body">
          <ng-content />
        </div>
      </mat-sidenav>

      <mat-sidenav-content>
        <ng-content select="[drawer-host]" />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .drawer-container {
      width: 100%;
      height: 100%;
    }

    .form-drawer {
      width: 440px;
      max-width: 100vw;

      @media (max-width: 768px) {
        width: 100vw;
      }

      &__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 20px 24px;
        border-bottom: 1px solid var(--color-gray-200, #e4e4e7);
      }

      &__title {
        font-size: 1.125rem;
        font-weight: 600;
        margin: 0;
      }

      &__body {
        padding: 24px;
        overflow-y: auto;
        flex: 1;
      }
    }
  `],
})
export class FormDrawerComponent {
  readonly title = input<string>('');
  readonly open = input<boolean>(false);
  readonly drawerClose = output<void>();
}
