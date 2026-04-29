import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  ChangeDetectorRef,
  HostListener,
  ElementRef,
} from '@angular/core';
import {
  InsightsBreakdownRow,
  SegmentFilter,
  BREAKDOWN_DIMENSIONS,
  BREAKDOWN_GROUPS,
  BreakdownDimension,
} from './insights-breakdown.types';

@Component({
  selector: 'app-insights-breakdown-panel',
  standalone: false,
  templateUrl: './insights-breakdown-panel.component.html',
  styleUrls: ['./insights-breakdown-panel.component.scss'],
})
export class InsightsBreakdownPanelComponent implements OnChanges {
  @Input() dimension = 'age';
  @Input() rows: InsightsBreakdownRow[] = [];
  @Input() isLoading = false;
  @Input() hasError = false;
  @Input() activeSegment: SegmentFilter | null = null;
  @Input() otherDimension = 'gender';

  @Output() segmentSelect = new EventEmitter<SegmentFilter | null>();
  @Output() dimensionSelect = new EventEmitter<string>();

  dropdownOpen = false;
  dropdownTop = 0;
  dropdownLeft = 0;

  readonly dimensionGroups = BREAKDOWN_GROUPS;
  readonly allDimensions = BREAKDOWN_DIMENSIONS;

  constructor(private cdr: ChangeDetectorRef, private elRef: ElementRef) {}

  ngOnChanges(_changes: SimpleChanges): void {}

  get dimensionLabel(): string {
    return this.allDimensions.find(d => d.key === this.dimension)?.label ?? this.dimension;
  }

  getDimensionsForGroup(group: string): BreakdownDimension[] {
    return this.allDimensions.filter(d => d.group === group);
  }

  isDimensionDisabled(key: string): boolean {
    return key === this.otherDimension;
  }

  isDimensionSelected(key: string): boolean {
    return key === this.dimension;
  }

  toggleDropdown(triggerEl: HTMLElement): void {
    if (this.dropdownOpen) {
      this.dropdownOpen = false;
      return;
    }
    const rect = triggerEl.getBoundingClientRect();
    const menuHeight = 280;
    const spaceBelow = window.innerHeight - rect.bottom;
    this.dropdownTop = spaceBelow >= menuHeight
      ? rect.bottom + 4
      : rect.top - menuHeight - 4;
    this.dropdownLeft = Math.min(rect.left, window.innerWidth - 204);
    this.dropdownOpen = true;
    this.cdr.detectChanges();
  }

  selectDimension(key: string): void {
    if (this.isDimensionDisabled(key)) return;
    this.dropdownOpen = false;
    if (key !== this.dimension) {
      this.dimensionSelect.emit(key);
    }
  }

  closeDropdown(): void {
    this.dropdownOpen = false;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.dropdownOpen && !this.elRef.nativeElement.contains(event.target)) {
      this.dropdownOpen = false;
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.dropdownOpen = false;
  }

  clickRow(row: InsightsBreakdownRow): void {
    const key = `${this.dimension}-${row.dimensionValue}`;
    if (this.activeSegment?.segmentKey === key) {
      this.segmentSelect.emit(null);
    } else {
      this.segmentSelect.emit({
        dimensionKey: this.dimension,
        segmentKey: key,
        segmentLabel: `${this.dimensionLabel}: ${row.dimensionValue}`,
        share: row.share,
      });
    }
  }

  isRowActive(row: InsightsBreakdownRow): boolean {
    return this.activeSegment?.segmentKey === `${this.dimension}-${row.dimensionValue}`;
  }
}
