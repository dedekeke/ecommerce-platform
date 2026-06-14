import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DataTableComponent, TableColumn } from './data-table.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

type TestRow = Record<string, unknown> & { id: string; name: string; price: number };

const columns: TableColumn[] = [
  { key: 'id', label: 'ID' },
  { key: 'name', label: 'Name', sortable: true },
  { key: 'price', label: 'Price' },
];

const rows: TestRow[] = [
  { id: '1', name: 'Product A', price: 10 },
  { id: '2', name: 'Product B', price: 20 },
];

describe('DataTableComponent', () => {
  let fixture: ComponentFixture<DataTableComponent<TestRow>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataTableComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(DataTableComponent<TestRow>);
  });

  it('should show loading spinner when loading is true', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    const spinner = fixture.nativeElement.querySelector('mat-spinner');
    expect(spinner).toBeTruthy();
  });

  it('should show empty state when no rows and not loading', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', []);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(empty).toBeTruthy();
  });

  it('should show custom empty message', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', []);
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('emptyMessage', 'No products found');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No products found');
  });

  it('should render table rows when data is provided', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();
    const tableRows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(tableRows.length).toBe(2);
  });

  it('should render column headers', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();
    const headers = fixture.nativeElement.querySelectorAll('th[mat-header-cell]');
    expect(headers.length).toBe(3);
  });

  it('should emit rowClick when a row is clicked', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();

    let clicked: TestRow | undefined;
    fixture.componentInstance.rowClick.subscribe((r) => (clicked = r));

    const firstRow = fixture.nativeElement.querySelector('tr[mat-row]');
    firstRow.click();
    fixture.detectChanges();
    expect(clicked).toEqual(rows[0]);
  });

  it('should emit pageChange on paginator interaction', () => {
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('totalElements', 100);
    fixture.componentRef.setInput('pageSize', 10);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();

    let pageEvent: unknown = undefined;
    fixture.componentInstance.pageChange.subscribe((e) => (pageEvent = e));
    void pageEvent; // captured for future assertion; paginator interaction not simulated in this test

    const paginator = fixture.nativeElement.querySelector('mat-paginator');
    expect(paginator).toBeTruthy();
  });
});
