import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export interface TableColumn {
  key: string;
  label: string;
  sortable?: boolean;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="data-table-wrapper" role="region" [attr.aria-label]="ariaLabel()">
      @if (loading()) {
        <div class="data-table__loading" aria-live="polite" aria-label="Loading data">
          <mat-spinner diameter="40" />
          <span class="sr-only">Loading...</span>
        </div>
      } @else if (rows().length === 0) {
        <div class="data-table__empty" data-testid="empty-state">
          <p>{{ emptyMessage() }}</p>
        </div>
      } @else {
        <div class="data-table__scroll">
          <table
            mat-table
            [dataSource]="rows()"
            matSort
            (matSortChange)="sortChange.emit($event)"
            class="data-table"
          >
            @for (col of columns(); track col.key) {
              <ng-container [matColumnDef]="col.key">
                <th
                  mat-header-cell
                  *matHeaderCellDef
                  [mat-sort-header]="col.sortable ? col.key : ''"
                  [disabled]="!col.sortable"
                >{{ col.label }}</th>
                <td mat-cell *matCellDef="let row">
                  <ng-container *ngTemplateOutlet="cellTemplate() || defaultCell; context: { $implicit: row, col: col }" />
                  <ng-template #defaultCell>{{ row[col.key] }}</ng-template>
                </td>
              </ng-container>
            }

            <tr mat-header-row *matHeaderRowDef="columnKeys()"></tr>
            <tr
              mat-row
              *matRowDef="let row; columns: columnKeys();"
              class="data-table__row"
              [class.data-table__row--clickable]="clickable()"
              (click)="rowClick.emit(row)"
            ></tr>
          </table>
        </div>

        <mat-paginator
          [length]="totalElements()"
          [pageSize]="pageSize()"
          [pageIndex]="pageIndex()"
          [pageSizeOptions]="[10, 25, 50]"
          (page)="pageChange.emit($event)"
          aria-label="Table pagination"
        />
      }
    </div>
  `,
  styles: [`
    .data-table-wrapper {
      background: #fff;
      border-radius: var(--radius-lg, 16px);
      overflow: hidden;
      box-shadow: var(--shadow-sm, 0 1px 2px rgba(0,0,0,0.05));
    }

    .data-table__loading {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 64px;
    }

    .data-table__empty {
      text-align: center;
      padding: 48px 24px;
      color: var(--color-gray-500, #71717a);
    }

    .data-table__scroll {
      overflow-x: auto;
    }

    .data-table {
      width: 100%;
    }

    .data-table__row {
      transition: background 0.15s ease-out;

      &--clickable {
        cursor: pointer;
        &:hover { background: var(--color-gray-50, #fafafa); }
      }
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `],
})
export class DataTableComponent<T extends Record<string, unknown>> {
  readonly columns = input.required<TableColumn[]>();
  readonly rows = input<T[]>([]);
  readonly loading = input<boolean>(false);
  readonly totalElements = input<number>(0);
  readonly pageSize = input<number>(10);
  readonly pageIndex = input<number>(0);
  readonly emptyMessage = input<string>('No data found.');
  readonly ariaLabel = input<string>('Data table');
  readonly cellTemplate = input<import('@angular/core').TemplateRef<unknown> | null>(null);
  readonly clickable = input<boolean>(false);

  readonly sortChange = output<Sort>();
  readonly pageChange = output<PageEvent>();
  readonly rowClick = output<T>();

  get columnKeys(): () => string[] {
    return () => this.columns().map((c) => c.key);
  }
}
