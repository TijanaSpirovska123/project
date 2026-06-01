import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  ChangeDetectorRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { InsightsBreakdownRow, SegmentFilter } from './insights-breakdown.types';

@Component({
  selector: 'app-insights-breakdown-panels',
  standalone: false,
  templateUrl: './insights-breakdown-panels.component.html',
  styleUrls: ['./insights-breakdown-panels.component.scss'],
  changeDetection: ChangeDetectionStrategy.Default,
})
export class InsightsBreakdownPanelsComponent implements OnChanges {
  @Input() leftDimension = 'age';
  @Input() rightDimension = 'gender';
  @Input() leftRows: InsightsBreakdownRow[] = [];
  @Input() rightRows: InsightsBreakdownRow[] = [];
  @Input() leftLoading = false;
  @Input() rightLoading = false;
  @Input() activeSegment: SegmentFilter | null = null;
  @Input() activeSegments: Partial<Record<string, SegmentFilter>> = {};

  @Output() segmentChange = new EventEmitter<SegmentFilter | null>();
  @Output() leftSegmentChange = new EventEmitter<SegmentFilter | null>();
  @Output() rightSegmentChange = new EventEmitter<SegmentFilter | null>();
  @Output() leftDimensionChange = new EventEmitter<string>();
  @Output() rightDimensionChange = new EventEmitter<string>();

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnChanges(_changes: SimpleChanges): void {}

  onLeftSegment(seg: SegmentFilter | null): void {
    this.segmentChange.emit(seg);
    this.leftSegmentChange.emit(seg);
  }

  onRightSegment(seg: SegmentFilter | null): void {
    this.segmentChange.emit(seg);
    this.rightSegmentChange.emit(seg);
  }

  onLeftDimension(dim: string): void {
    this.leftDimensionChange.emit(dim);
  }

  onRightDimension(dim: string): void {
    this.rightDimensionChange.emit(dim);
  }
}
