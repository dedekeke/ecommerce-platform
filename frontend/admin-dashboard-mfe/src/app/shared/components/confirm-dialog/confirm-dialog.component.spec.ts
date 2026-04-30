import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

const mockData: ConfirmDialogData = {
  title: 'Delete Product',
  message: 'Are you sure you want to delete this product?',
  confirmLabel: 'Delete',
  cancelLabel: 'Keep',
};

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: mockData },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
  });

  it('should display the title', () => {
    expect(fixture.nativeElement.textContent).toContain('Delete Product');
  });

  it('should display the message', () => {
    expect(fixture.nativeElement.textContent).toContain('Are you sure you want to delete this product?');
  });

  it('should display custom confirm and cancel labels', () => {
    expect(fixture.nativeElement.textContent).toContain('Delete');
    expect(fixture.nativeElement.textContent).toContain('Keep');
  });

  it('should render confirm button with data-testid', () => {
    const confirmBtn = fixture.nativeElement.querySelector('[data-testid="confirm-btn"]');
    expect(confirmBtn).toBeTruthy();
  });

  it('should render cancel button with data-testid', () => {
    const cancelBtn = fixture.nativeElement.querySelector('[data-testid="cancel-btn"]');
    expect(cancelBtn).toBeTruthy();
  });
});
