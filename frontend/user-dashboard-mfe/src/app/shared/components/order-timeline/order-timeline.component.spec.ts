import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderTimelineComponent } from './order-timeline.component';
import { TimelineEvent } from '../../../core/models/order.model';

const mockTimeline: TimelineEvent[] = [
  { status: 'PENDING', timestamp: '2024-01-10T08:00:00Z' },
  { status: 'CONFIRMED', timestamp: '2024-01-10T09:00:00Z' },
  { status: 'SHIPPED', timestamp: '2024-01-12T14:00:00Z' },
  { status: 'DELIVERED', timestamp: '2024-01-15T10:00:00Z' },
];

describe('OrderTimelineComponent', () => {
  let fixture: ComponentFixture<OrderTimelineComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderTimelineComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTimelineComponent);
    fixture.componentRef.setInput('timeline', mockTimeline);
    fixture.detectChanges();
  });

  it('should render all timeline events', () => {
    const steps = fixture.nativeElement.querySelectorAll('.timeline__step');
    expect(steps.length).toBe(4);
  });

  it('should display status labels for each event', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('PENDING');
    expect(compiled.textContent).toContain('CONFIRMED');
    expect(compiled.textContent).toContain('DELIVERED');
  });

  it('should mark the last event as current', () => {
    const steps = fixture.nativeElement.querySelectorAll('.timeline__step');
    const lastStep = steps[steps.length - 1];
    expect(lastStep.classList.contains('timeline__step--current')).toBeTrue();
  });
});
