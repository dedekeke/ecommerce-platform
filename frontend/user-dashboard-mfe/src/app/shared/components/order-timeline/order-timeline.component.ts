import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatusEvent } from '../../../core/models/order.model';

@Component({
  selector: 'app-order-timeline',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ol class="timeline" aria-label="Order status timeline">
      @for (event of timeline(); track event.timestamp; let last = $last) {
        <li
          class="timeline__step"
          [class.timeline__step--current]="last"
          [class.timeline__step--done]="!last"
        >
          <div class="timeline__dot" [attr.aria-hidden]="true"></div>
          <div class="timeline__content">
            <span class="timeline__status">{{ event.status }}</span>
            <time class="timeline__time" [dateTime]="event.timestamp">
              {{ event.timestamp | date: 'medium' }}
            </time>
            @if (event.note) {
              <p class="timeline__note">{{ event.note }}</p>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styleUrl: './order-timeline.component.scss',
})
export class OrderTimelineComponent {
  readonly timeline = input.required<StatusEvent[]>();
}
