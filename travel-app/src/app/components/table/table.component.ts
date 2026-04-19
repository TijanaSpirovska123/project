// src/app/shared/table/reusable-table.component.ts
import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
  OnChanges,
  SimpleChanges,
  TrackByFunction,
  ElementRef,
  ChangeDetectorRef,
} from '@angular/core';

import { TableData, TableDataRow } from '../../data/table/table-data.model';
import { ColumnDef } from '../../data/table/table-column.model';
import { TableHeader } from '../../data/table/table-header.model';
import { Formats } from '../../data/table/formats';
import { TableButton } from '../../data/table/table-button.model';

type SortDir = 'asc' | 'desc' | '';

@Component({
  selector: 'app-table',
  standalone: false,
  templateUrl: './table.component.html',
  styleUrls: ['./table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReusableTableComponent implements OnChanges {
  @Input() columns: ColumnDef[] = [];
  @Input() data: TableData[] = [];
  @Input() hasSearch = false;
  @Input() headers: TableHeader[] = [];
  @Input() buttons: TableButton[] = [];

  /** Tab functionality */
  @Input() tabs: string[] = [];
  @Input() activeTabIndex: number = 0;
  @Output() tabChange = new EventEmitter<number>();

  /** Status toggle functionality */
  @Output() statusToggle = new EventEmitter<{
    item: TableData;
    currentStatus: string;
    newStatus: string;
  }>();

  @Input() enableScrollX: boolean = false;

  /** Create button functionality */
  @Input() showCreateButton: boolean = false;
  @Input() createButtonText: string = 'Create';
  @Output() createClick = new EventEmitter<void>();

  /** Add-column customiser button */
  @Input() showAddCol: boolean = false;
  @Output() addColClick = new EventEmitter<void>();

  /** Edit functionality */
  @Output() editClick = new EventEmitter<{
    item: TableData;
    isChild?: boolean;
    childData?: any;
  }>();

  /** Delete functionality */
  @Output() deleteClick = new EventEmitter<{
    item: TableData;
    isChild?: boolean;
    childData?: any;
  }>();

  @Output() rowClick = new EventEmitter<{
    item: TableData;
    originalEvent?: MouseEvent;
  }>();

  /** Analytics display for top item */
  @Output() topItemChange = new EventEmitter<TableData | null>();

  /** External/inline search */
  @Input() search = '';
  @Output() searchChange = new EventEmitter<string>();

  /** sorting */
  sortKey: string | null = null;
  sortDir: SortDir = '';
  momentDateAndTimeSeconds = Formats.MomentDateAndTimeLowercase;

  /** derived & displayed data */
  filtered: TableData[] = [];
  trackByGuid: TrackByFunction<TableData> = (index, item) => item.guid ?? index;

  // Pagination
  currentPage: number = 1;
  itemsPerPage: number = 5;
  totalItems: number = 0;
  totalPages: number = 0;
  paginatedData: TableData[] = [];
  itemsPerPageOptions: number[] = [5, 10, 25];

  // date range (bound to MatDateRange)
  dateStart: Date | null = null;
  dateEnd: Date | null = null;

  /** Which keys can carry a date in parent rows */
  private dateKeys = ['scheduleTime', 'date', 'reservationDate'];

  // Floating action bar
  hoveredRow: {
    top: number;
    item: TableData;
    isChild: boolean;
    parentItem: TableData | null;
    childData: TableDataRow[] | null;
  } | null = null;
  private leaveTimer: any = null;

  constructor(
    private readonly elRef: ElementRef,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnChanges(_: SimpleChanges) {
    this.applyFilters();
  }

  // ---------- pagination ----------
  onItemsPerPageChange(newItemsPerPage: number): void {
    this.itemsPerPage = newItemsPerPage;
    this.currentPage = 1; // Reset to first page
    this.updatePagination();
  }

  onPageChange(newPage: number): void {
    if (newPage >= 1 && newPage <= this.totalPages) {
      this.currentPage = newPage;
      this.updatePagination();
    }
  }

  getPaginationPages(): number[] {
    const pages: number[] = [];
    const maxPagesToShow = 5;

    if (this.totalPages <= maxPagesToShow) {
      for (let i = 1; i <= this.totalPages; i++) {
        pages.push(i);
      }
    } else {
      const start = Math.max(1, this.currentPage - 2);
      const end = Math.min(this.totalPages, start + maxPagesToShow - 1);

      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
    }

    return pages;
  }

  getEndIndex(): number {
    return Math.min(this.currentPage * this.itemsPerPage, this.totalItems);
  }

  private updatePagination(): void {
    this.totalItems = this.filtered.length;
    this.totalPages = Math.ceil(this.totalItems / this.itemsPerPage);

    const startIndex = (this.currentPage - 1) * this.itemsPerPage;
    const endIndex = startIndex + this.itemsPerPage;
    this.paginatedData = this.filtered.slice(startIndex, endIndex);

    // Emit top item for analytics
    const topItem =
      this.paginatedData.length > 0 ? this.paginatedData[0] : null;
    this.topItemChange.emit(topItem);
  }

  // ---------- header & sorting ----------
  onHeaderSort(h: TableHeader): void {
    const key = h.value;
    const sortable = (h as any)?.sortable ?? !!key;
    if (!sortable || !key) return;

    if (this.sortKey !== key) {
      this.sortKey = key;
      this.sortDir = 'asc';
    } else {
      this.sortDir =
        this.sortDir === 'asc' ? 'desc' : this.sortDir === 'desc' ? '' : 'asc';
      if (this.sortDir === '') this.sortKey = null;
    }

    this.headers = this.headers.map((hd) => ({
      ...hd,
      appliedSorting: !!this.sortKey && hd.value === this.sortKey,
      sortedAsc: hd.value === this.sortKey ? this.sortDir === 'asc' : false,
    }));

    this.applyFilters();
  }

  // ---------- tab switching ----------
  onTabClick(index: number) {
    if (index !== this.activeTabIndex) {
      this.activeTabIndex = index;
      this.currentPage = 1;
      this.tabChange.emit(index);
    }
  }

  // ---------- create button ----------
  onCreateClick() {
    this.createClick.emit();
  }

  // ---------- add-col customiser ----------
  onAddColClick(): void {
    this.addColClick.emit();
  }

  getGridTemplate(): string {
    const tracks = this.headers.map((h) =>
      h.width ? String(h.width) : (this.enableScrollX ? 'minmax(160px, 1fr)' : '1fr')
    );
    tracks.push('48px');
    if (this.showAddCol) tracks.push('40px');
    return tracks.join(' ');
  }

  // ---------- edit functionality ----------
  onEditClick(item: TableData) {
    console.log('Edit clicked for item:', item);
    this.editClick.emit({ item, isChild: false });
  }

  onChildEditClick(parentItem: TableData, child: TableDataRow[]) {
    this.editClick.emit({
      item: parentItem,
      isChild: true,
      childData: child,
    });
  }

  // ---------- row click functionality ----------
  onRowClick(item: TableData, event?: MouseEvent) {
    console.log('Row clicked for item:', item);
    this.rowClick.emit({ item, originalEvent: event });
  }

  // ---------- delete functionality ----------
  onDeleteClick(item: TableData) {
    console.log('Delete clicked for item:', item);
    this.deleteClick.emit({ item, isChild: false });
  }

  onChildDeleteClick(parentItem: TableData, child: TableDataRow[]) {
    this.deleteClick.emit({
      item: parentItem,
      isChild: true,
      childData: child,
    });
  }

  // ---------- floating action bar ----------
  onRowMouseEnter(item: TableData, event: MouseEvent): void {
    if (this.leaveTimer) { clearTimeout(this.leaveTimer); this.leaveTimer = null; }
    const rowRect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const hostRect = (this.elRef.nativeElement as HTMLElement).getBoundingClientRect();
    this.hoveredRow = {
      top: rowRect.top - hostRect.top + rowRect.height / 2,
      item,
      isChild: false,
      parentItem: null,
      childData: null,
    };
  }

  onChildRowMouseEnter(parentItem: TableData, childData: TableDataRow[], event: MouseEvent): void {
    if (this.leaveTimer) { clearTimeout(this.leaveTimer); this.leaveTimer = null; }
    const rowRect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const hostRect = (this.elRef.nativeElement as HTMLElement).getBoundingClientRect();
    this.hoveredRow = {
      top: rowRect.top - hostRect.top + rowRect.height / 2,
      item: parentItem,
      isChild: true,
      parentItem,
      childData,
    };
  }

  onRowMouseLeave(): void {
    this.leaveTimer = setTimeout(() => {
      this.hoveredRow = null;
      this.cdr.markForCheck();
    }, 80);
  }

  onActionBarMouseEnter(): void {
    if (this.leaveTimer) { clearTimeout(this.leaveTimer); this.leaveTimer = null; }
  }

  onActionBarEdit(event: MouseEvent): void {
    event.stopPropagation();
    if (!this.hoveredRow) return;
    if (this.hoveredRow.isChild) {
      this.onChildEditClick(this.hoveredRow.parentItem!, this.hoveredRow.childData!);
    } else {
      this.onEditClick(this.hoveredRow.item);
    }
  }

  onActionBarDelete(event: MouseEvent): void {
    event.stopPropagation();
    if (!this.hoveredRow) return;
    if (this.hoveredRow.isChild) {
      this.onChildDeleteClick(this.hoveredRow.parentItem!, this.hoveredRow.childData!);
    } else {
      this.onDeleteClick(this.hoveredRow.item);
    }
  }

  // ---------- status toggle ----------
  onStatusToggle(item: TableData, currentStatus: string) {
    const newStatus = currentStatus === 'Active' ? 'Paused' : 'Active';
    console.log(
      'Toggling status for item:',
      item,
      'from',
      currentStatus,
      'to',
      newStatus
    );
    this.statusToggle.emit({
      item: item,
      currentStatus: currentStatus,
      newStatus: newStatus,
    });
  }

  onChildStatusToggle(
    parentItem: TableData,
    child: TableDataRow[],
    currentStatus: string
  ) {
    const newStatus = currentStatus === 'Active' ? 'Paused' : 'Active';
    // Create a temporary item object for child row status toggle
    const childItem = {
      guid: this.getChildCell(child, 'id')?.value || 'unknown',
      rows: child,
      isChild: true,
      parentGuid: parentItem.guid,
    };
    this.statusToggle.emit({
      item: childItem as any,
      currentStatus: currentStatus,
      newStatus: newStatus,
    });
  }

  // ---------- expand/collapse ----------
  toggleExpand(item: TableData) {
    item.expanded = !item.expanded;
  }

  hasChildren = (item: TableData) =>
    !!this.getCell(item, 'children')?.childrenRow?.length;

  getChildren(item: TableData): TableDataRow[][] {
    return this.getCell(item, 'children')?.childrenRow ?? [];
  }

  // ---------- search ----------
  onSearchInput(v: string) {
    this.search = v;
    this.searchChange.emit(v);
    this.applyFilters();
  }

  onSearchInputEvent(e: Event) {
    const v = (e.target as HTMLInputElement).value;
    this.onSearchInput(v);
  }

  // ---------- date range ----------
  onRangeChange(range: any) {
    const r = (range?.value ?? range) as {
      begin?: Date | null;
      end?: Date | null;
    };
    this.dateStart = r?.begin ?? null;
    this.dateEnd = r?.end ?? null;
    this.applyFilters();
  }

  // ---------- helpers for values ----------
  getCell(item: TableData, key?: string | null): TableDataRow | undefined {
    if (!key) return undefined;
    return item?.rows?.find((r) => r.key === key);
  }
  getValue(item: TableData, key?: string | null): any {
    return this.getCell(item, key)?.value;
  }

  getChildCell(
    child: TableDataRow[],
    key: string | null
  ): TableDataRow | undefined {
    return child?.find((c) => c.key === key);
  }
  childValue(child: TableDataRow[], key: string | null): any {
    return this.getChildCell(child, key)?.value;
  }

  statusClassFromValue(v: any): 'paid' | 'partial' | 'unpaid' | '' {
    const s = (v ?? '').toString().toLowerCase();
    if (s.includes('fully')) return 'paid';
    if (s.includes('part')) return 'partial';
    if (s.includes('unpaid')) return 'unpaid';
    return '';
  }
  private normalizeStatus(v: any): string {
    return (v ?? '').toString().trim().toLowerCase();
  }
  statusClass(child: TableDataRow[]): 'paid' | 'partial' | 'unpaid' | '' {
    const s = this.normalizeStatus(this.childValue(child, 'status'));
    if (s.includes('fully')) return 'paid';
    if (s.includes('partially')) return 'partial';
    if (s.includes('unpaid')) return 'unpaid';
    return '';
  }

  private stringify(v: any): string {
    if (v instanceof Date) return new Date(v).toISOString();
    if (typeof v === 'number') return String(v);
    if (typeof v === 'boolean') return v ? 'true' : 'false';
    return (v ?? '').toString().toLowerCase();
  }

  private compareByKey(a: TableData, b: TableData, key: string, dir: SortDir) {
    const av = this.getCell(a, key)?.value;
    const bv = this.getCell(b, key)?.value;

    const norm = (x: any) => {
      if (x == null) return '';
      if (x instanceof Date) return x.getTime();
      if (!isNaN(Number(x)) && typeof x !== 'boolean') return Number(x);
      return ('' + x).toLowerCase();
    };

    const na = norm(av);
    const nb = norm(bv);
    const cmp = na < nb ? -1 : na > nb ? 1 : 0;
    return dir === 'asc' ? cmp : -cmp;
  }

  exportCsv(): void {
    const headers = this.headers.map((c) => c.title ?? c.value ?? '');
    const rows = this.filtered.map((item) =>
      this.headers.map((h) => {
        const v = this.getValue(item, h.value);
        if (v instanceof Date) return v.toISOString().split('T')[0];
        return (v ?? '').toString().replaceAll('"', '""');
      })
    );
    const csv = [
      headers.map((h) => `"${h.replaceAll('"', '""')}"`).join(','),
      ...rows.map((r) => r.map((v) => `"${v}"`).join(',')),
    ].join('\r\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'export.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  // ---------- filtering (search + date + sort) ----------
  applyFilters(): void {
    const q = (this.search || '').trim().toLowerCase();
    let rows = [...(this.data || [])];

    if (q) {
      rows = rows.filter((item) => {
        // search parent cells
        const hitParent = (item.rows || []).some(
          (r) =>
            this.stringify(r?.value).includes(q) ||
            (r?.title || '').toLowerCase().includes(q)
        );

        // search children (if present)
        const childGroups = this.getChildren(item);
        const hitChildren = childGroups?.some((child) =>
          child?.some(
            (c) =>
              this.stringify(c?.value).includes(q) ||
              (c?.title || '').toLowerCase().includes(q)
          )
        );

        return hitParent || hitChildren;
      });
    }

    // date range filter (parent row date)
    if (this.dateStart || this.dateEnd) {
      const start = this.dateStart
        ? new Date(this.dateStart.setHours(0, 0, 0, 0))
        : null;
      const end = this.dateEnd
        ? new Date(this.dateEnd.setHours(23, 59, 59, 999))
        : null;

      rows = rows.filter((item) => {
        const cell = (item.rows || []).find((r) =>
          this.dateKeys.includes(r.key || '')
        );
        if (!cell?.value) return false;
        const d =
          cell.value instanceof Date ? cell.value : new Date(cell.value as any);
        if (Number.isNaN(d.getTime())) return false;
        if (start && d < start) return false;
        if (end && d > end) return false;
        return true;
      });
    }

    if (this.sortKey && this.sortDir) {
      rows.sort((a, b) =>
        this.compareByKey(a, b, this.sortKey!, this.sortDir!)
      );
    }

    this.filtered = rows;
    this.updatePagination();
  }
}
