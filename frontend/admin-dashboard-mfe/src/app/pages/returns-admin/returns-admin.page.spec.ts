import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ReturnsAdminPage } from './returns-admin.page';
import { ReturnAdminService } from '../../core/services/return-admin.service';
import { PagedReturns, ReturnRequest, ReturnSummary } from '../../core/models/return.model';

const mockReturn: ReturnRequest = {
  id: 'rma-1',
  rmaNumber: 'RMA-ABC123',
  orderId: 'ord-1',
  userId: 'user-1',
  status: 'AWAITING_SHIPMENT',
  reason: 'Wrong size',
  requestedAt: '2024-01-01T00:00:00Z',
  lines: [
    { id: 'line-1', orderItemId: 'item-1', productId: 'prod-1', quantity: 2, unitPrice: 25, approved: false },
  ],
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockSummary: ReturnSummary = {
  id: 'rma-1',
  rmaNumber: 'RMA-ABC123',
  orderId: 'ord-1',
  userId: 'user-1',
  status: 'AWAITING_SHIPMENT',
  requestedAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const emptyPaged: PagedReturns = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

function dialogSpy(result: unknown): jasmine.SpyObj<MatDialog> {
  const spy = jasmine.createSpyObj('MatDialog', ['open']);
  spy.open.and.returnValue({ afterClosed: () => of(result) });
  return spy;
}

describe('ReturnsAdminPage', () => {
  let fixture: ComponentFixture<ReturnsAdminPage>;
  let returnServiceSpy: jasmine.SpyObj<ReturnAdminService>;

  async function setup(dialog: jasmine.SpyObj<MatDialog> = dialogSpy(true)) {
    TestBed.resetTestingModule();
    returnServiceSpy = jasmine.createSpyObj('ReturnAdminService', [
      'getReturnById', 'getReturnsByUser', 'markReceived', 'inspect', 'getReturns',
    ]);
    returnServiceSpy.getReturns.and.returnValue(of(emptyPaged));

    await TestBed.configureTestingModule({
      imports: [ReturnsAdminPage, NoopAnimationsModule],
      providers: [
        { provide: ReturnAdminService, useValue: returnServiceSpy },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReturnsAdminPage);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setup();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Returns / RMA');
  });

  it('should call getReturns on init', () => {
    expect(returnServiceSpy.getReturns).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it('should show the status filter dropdown', () => {
    const filter = fixture.nativeElement.querySelector('[data-testid="return-status-filter"]');
    expect(filter).toBeTruthy();
  });

  it('should call getReturns with status filter when onStatusFilter is invoked', () => {
    fixture.componentInstance.onStatusFilter('AWAITING_SHIPMENT');
    expect(returnServiceSpy.getReturns).toHaveBeenCalledWith(
      jasmine.objectContaining({ status: 'AWAITING_SHIPMENT', page: 0 }),
    );
  });

  it('should call getReturns with new page params when onPage is invoked', () => {
    fixture.componentInstance.onPage({ pageIndex: 1, pageSize: 25, length: 100 });
    expect(returnServiceSpy.getReturns).toHaveBeenCalledWith(jasmine.objectContaining({ page: 1, size: 25 }));
  });

  it('should show the empty state when no returns have been searched yet', () => {
    const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(empty.textContent).toContain('No returns found.');
  });

  it('should search returns by user id and populate the table', () => {
    returnServiceSpy.getReturnsByUser.and.returnValue(of([mockReturn]));
    fixture.componentInstance.userSearchForm.setValue({ userId: 'user-1' });
    fixture.componentInstance.onSearchByUser();
    fixture.detectChanges();

    expect(returnServiceSpy.getReturnsByUser).toHaveBeenCalledWith('user-1');
    expect(fixture.componentInstance.returns().length).toBe(1);
  });

  it('should show an inline message when a user search yields no returns', () => {
    returnServiceSpy.getReturnsByUser.and.returnValue(of([]));
    fixture.componentInstance.userSearchForm.setValue({ userId: 'user-2' });
    fixture.componentInstance.onSearchByUser();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="search-error"]');
    expect(error.textContent).toContain('No returns found for user: user-2');
  });

  it('should show an inline error when the user search request fails', () => {
    returnServiceSpy.getReturnsByUser.and.returnValue(throwError(() => new Error('500')));
    fixture.componentInstance.userSearchForm.setValue({ userId: 'user-1' });
    fixture.componentInstance.onSearchByUser();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="search-error"]');
    expect(error.textContent).toContain('Failed to search returns for user: user-1');
  });

  it('should look up a return by RMA id and open the detail drawer', () => {
    returnServiceSpy.getReturnById.and.returnValue(of(mockReturn));
    fixture.componentInstance.rmaSearchForm.setValue({ rmaId: 'rma-1' });
    fixture.componentInstance.onSearchByRmaId();
    fixture.detectChanges();

    expect(returnServiceSpy.getReturnById).toHaveBeenCalledWith('rma-1');
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedReturn()?.rmaNumber).toBe('RMA-ABC123');
  });

  it('should show an inline error when the RMA lookup fails', () => {
    returnServiceSpy.getReturnById.and.returnValue(throwError(() => new Error('404')));
    fixture.componentInstance.rmaSearchForm.setValue({ rmaId: 'missing' });
    fixture.componentInstance.onSearchByRmaId();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="search-error"]');
    expect(error.textContent).toContain('Return not found: missing');
  });

  it('should fetch the full return with lines via getReturnById and open the drawer on row click', () => {
    returnServiceSpy.getReturnById.and.returnValue(of(mockReturn));

    fixture.componentInstance.onRowClick(mockSummary as unknown as Record<string, unknown> & ReturnSummary);
    fixture.detectChanges();

    expect(returnServiceSpy.getReturnById).toHaveBeenCalledWith('rma-1');
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedReturn()?.lines.length).toBe(1);
  });

  it('should show a snackbar-triggering error path when the row-click detail fetch fails', () => {
    returnServiceSpy.getReturnById.and.returnValue(throwError(() => new Error('404')));

    fixture.componentInstance.onRowClick(mockSummary as unknown as Record<string, unknown> & ReturnSummary);

    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
  });

  it('should compute the return lines subtotal and total', () => {
    fixture.componentInstance.selectedReturn.set(mockReturn);
    expect(fixture.componentInstance.lineSubtotal(mockReturn.lines[0])).toBe(50);
    expect(fixture.componentInstance.linesTotal()).toBe(50);
  });

  it('should allow marking an AWAITING_SHIPMENT return as received (after confirm)', () => {
    fixture.componentInstance.selectedReturn.set(mockReturn);
    returnServiceSpy.markReceived.and.returnValue(of({ ...mockReturn, status: 'RECEIVED' }));

    fixture.componentInstance.onMarkReceived();

    expect(returnServiceSpy.markReceived).toHaveBeenCalledWith('rma-1');
    expect(fixture.componentInstance.selectedReturn()?.status).toBe('RECEIVED');
    expect(returnServiceSpy.getReturns).toHaveBeenCalledTimes(2);
  });

  it('should NOT mark received when the confirm dialog is dismissed', async () => {
    await setup(dialogSpy(false));
    fixture.componentInstance.selectedReturn.set(mockReturn);

    fixture.componentInstance.onMarkReceived();

    expect(returnServiceSpy.markReceived).not.toHaveBeenCalled();
  });

  it('should approve an inspectable return with a restocking fee (after confirm)', () => {
    const inspectable: ReturnRequest = { ...mockReturn, status: 'RECEIVED' };
    fixture.componentInstance.selectedReturn.set(inspectable);
    fixture.componentInstance.inspectForm.setValue({ restockingFeePercent: 15, condition: 'OPENED', notes: 'Minor wear' });
    returnServiceSpy.inspect.and.returnValue(of({ ...inspectable, status: 'APPROVED', outcome: 'APPROVED' }));

    fixture.componentInstance.onInspect('APPROVED');

    expect(returnServiceSpy.inspect).toHaveBeenCalledWith('rma-1', {
      outcome: 'APPROVED',
      condition: 'OPENED',
      notes: 'Minor wear',
      restockingFeePercent: 15,
    });
    expect(fixture.componentInstance.selectedReturn()?.outcome).toBe('APPROVED');
  });

  it('should reject an inspectable return (after confirm)', () => {
    const inspectable: ReturnRequest = { ...mockReturn, status: 'INSPECTING' };
    fixture.componentInstance.selectedReturn.set(inspectable);
    returnServiceSpy.inspect.and.returnValue(of({ ...inspectable, status: 'REJECTED', outcome: 'REJECTED' }));

    fixture.componentInstance.onInspect('REJECTED');

    expect(returnServiceSpy.inspect).toHaveBeenCalledWith('rma-1', jasmine.objectContaining({ outcome: 'REJECTED' }));
    expect(fixture.componentInstance.selectedReturn()?.status).toBe('REJECTED');
  });

  it('should show an error state when inspection fails', () => {
    const inspectable: ReturnRequest = { ...mockReturn, status: 'RECEIVED' };
    fixture.componentInstance.selectedReturn.set(inspectable);
    returnServiceSpy.inspect.and.returnValue(throwError(() => new Error('500')));

    fixture.componentInstance.onInspect('APPROVED');

    expect(fixture.componentInstance.processing()).toBeFalse();
  });

  it('should gate receive/inspect actions by status', () => {
    expect(fixture.componentInstance.canReceive('AWAITING_SHIPMENT')).toBeTrue();
    expect(fixture.componentInstance.canReceive('RECEIVED')).toBeFalse();
    expect(fixture.componentInstance.canInspect('RECEIVED')).toBeTrue();
    expect(fixture.componentInstance.canInspect('INSPECTING')).toBeTrue();
    expect(fixture.componentInstance.canInspect('COMPLETED')).toBeFalse();
  });

  it('should close the drawer and clear selectedReturn', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.selectedReturn.set(mockReturn);
    fixture.componentInstance.closeDrawer();

    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
    expect(fixture.componentInstance.selectedReturn()).toBeNull();
  });

  it('should map return statuses to the correct badge variants', () => {
    expect(fixture.componentInstance.returnVariant('COMPLETED')).toBe('success');
    expect(fixture.componentInstance.returnVariant('REJECTED')).toBe('error');
    expect(fixture.componentInstance.returnVariant('AWAITING_SHIPMENT')).toBe('warning');
  });
});
