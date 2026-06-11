import {
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';

export interface ChartDataPoint {
  label: string;
  [key: string]: number | string;
}

export const METRIC_COLORS: Record<string, string> = {
  impressions:      '#34D399',
  reach:            '#60A5FA',
  clicks:           '#A78BFA',
  spend:            '#FBBF24',
  ctr:              '#F87171',
  cpm:              '#2DD4BF',
  cpc:              '#FB923C',
  roas:             '#818CF8',
  frequency:        '#E879F9',
  revenue:          '#4ADE80',
  linkClicks:       '#818CF8',
  postEngagement:   '#4ADE80',
  landingPageViews: '#F472B6',
  outboundClicks:   '#38BDF8',
};

const METRIC_LABELS: Record<string, string> = {
  impressions:      'Impressions',
  reach:            'Reach',
  clicks:           'Clicks',
  spend:            'Spend',
  ctr:              'CTR',
  cpm:              'CPM',
  cpc:              'CPC',
  roas:             'ROAS',
  frequency:        'Frequency',
  revenue:          'Revenue',
  linkClicks:       'Link Clicks',
  postEngagement:   'Post Engagement',
  landingPageViews: 'Landing Page Views',
  outboundClicks:   'Outbound Clicks',
};

const METRIC_FORMATS: Record<string, (v: number) => string> = {
  impressions:      v => v >= 1_000_000 ? (v / 1_000_000).toFixed(1) + 'M' : v >= 1_000 ? (v / 1_000).toFixed(1) + 'K' : String(Math.round(v)),
  reach:            v => v >= 1_000_000 ? (v / 1_000_000).toFixed(1) + 'M' : v >= 1_000 ? (v / 1_000).toFixed(1) + 'K' : String(Math.round(v)),
  clicks:           v => Math.round(v).toLocaleString(),
  spend:            v => v >= 1_000 ? '$' + (v / 1_000).toFixed(1) + 'K' : '$' + v.toFixed(2),
  ctr:              v => v.toFixed(2) + '%',
  cpm:              v => v >= 1_000 ? '$' + (v / 1_000).toFixed(1) + 'K' : '$' + v.toFixed(2),
  cpc:              v => v >= 1_000 ? '$' + (v / 1_000).toFixed(1) + 'K' : '$' + v.toFixed(2),
  roas:             v => v.toFixed(2) + 'x',
  frequency:        v => v.toFixed(2) + 'x',
  revenue:          v => v >= 1_000 ? '$' + (v / 1_000).toFixed(1) + 'K' : '$' + v.toFixed(2),
  linkClicks:       v => Math.round(v).toLocaleString(),
  postEngagement:   v => Math.round(v).toLocaleString(),
  landingPageViews: v => Math.round(v).toLocaleString(),
  outboundClicks:   v => Math.round(v).toLocaleString(),
};

function defaultFmt(v: number): string {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(1) + 'M';
  if (v >= 1000) return (v / 1000).toFixed(1) + 'K';
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
}

@Component({
  selector: 'app-insights-svg-chart',
  templateUrl: './insights-svg-chart.component.html',
  styleUrls: ['./insights-svg-chart.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class InsightsSvgChartComponent implements OnChanges {
  constructor(private cdr: ChangeDetectorRef) {}
  @Input() data: ChartDataPoint[] = [];
  @Input() previousData: ChartDataPoint[] = [];
  @Input() activeMetrics: string[] = [];
  @Input() chartType: 'line' | 'bar' = 'line';
  /** Total days in the selected range — drives X-axis label density. */
  @Input() totalDays = 0;
  /** ISO date string for the start of the selected range (e.g. "2025-01-01"). */
  @Input() dateStart = '';
  /** ISO date string for the end of the selected range (e.g. "2025-05-27"). */
  @Input() dateStop = '';
  /** All 6 KPI block metrics to display in the hover tooltip (key + label). */
  @Input() kpiMetricBlocks: {key: string; label: string}[] = [];

  readonly SVG_W = 700;
  readonly SVG_H = 300;
  readonly PAD_LEFT = 58;
  readonly PAD_RIGHT = 20;
  readonly PAD_TOP = 20;
  readonly PAD_BOTTOM = 44;

  get innerW(): number { return this.SVG_W - this.PAD_LEFT - this.PAD_RIGHT; }
  get innerH(): number { return this.SVG_H - this.PAD_TOP - this.PAD_BOTTOM; }

  hoveredIndex: number | null = null;
  tooltipSvgX = 0;
  tooltipSvgY = 0;

  readonly metricColors = METRIC_COLORS;
  readonly gridLines = [0, 1, 2, 3, 4];

  // ── Zoom state ────────────────────────────────────────────────────────────
  /** Indices into `data[]` for the current zoom window. Null = no zoom. */
  zoomWindow: { start: number; end: number } | null = null;
  isDragging = false;
  /** SVG-space X at drag start (for rendering the selection rect). */
  dragPixelStart = 0;
  /** SVG-space X at current drag position. */
  dragPixelCurrent = 0;
  private dragDataStart = -1;  // visData index at mousedown

  /** Slice of data currently in view (respects zoomWindow). */
  get visData(): ChartDataPoint[] {
    if (!this.zoomWindow) return this.data;
    const s = Math.max(this.zoomWindow.start, 0);
    const e = Math.min(this.zoomWindow.end, this.data.length - 1);
    return s < e ? this.data.slice(s, e + 1) : this.data;
  }

  get isZoomed(): boolean { return this.zoomWindow !== null; }

  resetZoom(): void {
    this.zoomWindow = null;
    this.isDragging = false;
    this.dragDataStart = -1;
    this.hoveredIndex = null;
  }

  /** Drag selection rect left edge in SVG coords. */
  get dragRectX(): number { return Math.min(this.dragPixelStart, this.dragPixelCurrent); }
  /** Drag selection rect width in SVG coords. */
  get dragRectW(): number { return Math.abs(this.dragPixelCurrent - this.dragPixelStart); }

  // ── Per-metric max — each metric scales to its own range ─────────────────
  private getMetricMax(metric: string): number {
    if (!metric || !this.visData.length) return 1;
    return Math.max(...this.visData.map(d => (d[metric] as number) ?? 0), 1);
  }

  // Grid labels show the first active metric's scale
  private get primaryMax(): number {
    return this.getMetricMax(this.activeMetrics[0] ?? '');
  }

  ngOnChanges(_changes: SimpleChanges): void {
    this.zoomWindow = null;   // reset zoom when input data changes
    this.hoveredIndex = null;
  }

  getColor(metric: string): string {
    return METRIC_COLORS[metric] ?? '#60A5FA';
  }

  getLabel(metric: string): string {
    return METRIC_LABELS[metric] ?? metric;
  }

  // ── Line mode: edge-to-edge X positioning (uses visData) ──────────────────
  getLineX(i: number): number {
    const n = this.visData.length;
    if (n <= 1) return this.PAD_LEFT + this.innerW / 2;
    return this.PAD_LEFT + (i / (n - 1)) * this.innerW;
  }

  // ── Bar mode: slot-based X positioning ────────────────────────────────────
  get slotW(): number { return this.innerW / Math.max(this.visData.length, 1); }
  get barW(): number { return this.slotW * 0.6; }

  getBarX(i: number): number {
    return this.PAD_LEFT + i * this.slotW + this.slotW * 0.2;
  }

  getBarMidX(i: number): number {
    return this.getBarX(i) + this.barW / 2;
  }

  // ── Y positioning (per-metric scale — each line uses its own max) ─────────
  getY(metric: string, i: number): number {
    const value = (this.visData[i]?.[metric] as number) ?? 0;
    const ratio = value / this.getMetricMax(metric);
    return this.PAD_TOP + this.innerH - ratio * this.innerH;
  }

  getBarHeight(metric: string, i: number): number {
    const value = (this.visData[i]?.[metric] as number) ?? 0;
    return (value / this.getMetricMax(metric)) * this.innerH;
  }

  getBarY(metric: string, i: number): number {
    return this.PAD_TOP + this.innerH - this.getBarHeight(metric, i);
  }

  // ── Range label helpers (for X-axis from/till) ────────────────────────────

  private formatRangeLabel(iso: string): string {
    if (!iso) return '';
    const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    const parts = iso.split('-');
    if (parts.length < 3) return iso;
    const m = parseInt(parts[1], 10);
    const d = parseInt(parts[2], 10);
    return (MONTHS[m - 1] ?? '') + ' ' + d;
  }

  /**
   * Left-edge label for the x-axis.
   * When zoomed, shows the first visible data-point date.
   * Otherwise shows the dateStart input, falling back to the first data label.
   */
  get xRangeStartLabel(): string {
    if (this.isZoomed && this.visData.length > 0) {
      const d = this.visData[0]?.['date'] as string;
      if (d) return this.formatRangeLabel(d);
    }
    if (this.dateStart) return this.formatRangeLabel(this.dateStart);
    return (this.visData[0]?.label as string) ?? '';
  }

  /**
   * Right-edge label for the x-axis.
   * When zoomed, shows the last visible data-point date.
   * Otherwise shows the dateStop input, falling back to the last data label.
   */
  get xRangeStopLabel(): string {
    if (this.isZoomed && this.visData.length > 0) {
      const last = this.visData.length - 1;
      const d = this.visData[last]?.['date'] as string;
      if (d) return this.formatRangeLabel(d);
    }
    if (this.dateStop) return this.formatRangeLabel(this.dateStop);
    const last = this.visData.length - 1;
    return last >= 0 ? ((this.visData[last]?.label as string) ?? '') : '';
  }

  // ── SVG paths ─────────────────────────────────────────────────────────────
  getLinePath(metric: string): string {
    if (!this.visData.length) return '';
    if (this.visData.length === 1) {
      // Single data point: draw a horizontal line across the full chart width
      // so the value is clearly visible as a line, not just an isolated dot.
      const y  = this.getY(metric, 0).toFixed(1);
      const x1 = this.PAD_LEFT.toFixed(1);
      const x2 = (this.PAD_LEFT + this.innerW).toFixed(1);
      return `M ${x1},${y} L ${x2},${y}`;
    }
    return 'M ' + this.visData.map((_, i) =>
      `${this.getLineX(i).toFixed(1)},${this.getY(metric, i).toFixed(1)}`
    ).join(' L ');
  }

  get hasPreviousData(): boolean {
    return this.chartType === 'line' && this.previousData.length > 0 && this.activeMetrics.length > 0;
  }

  getPrevLineX(i: number): number {
    const n = this.previousData.length;
    if (n <= 1) return this.PAD_LEFT + this.innerW / 2;
    return this.PAD_LEFT + (i / (n - 1)) * this.innerW;
  }

  getPrevY(metric: string, i: number): number {
    const value = (this.previousData[i]?.[metric] as number) ?? 0;
    const ratio = value / this.getMetricMax(metric);
    return this.PAD_TOP + this.innerH - ratio * this.innerH;
  }

  getPrevLinePath(metric: string): string {
    if (!this.previousData.length) return '';
    return 'M ' + this.previousData.map((_, i) =>
      `${this.getPrevLineX(i).toFixed(1)},${this.getPrevY(metric, i).toFixed(1)}`
    ).join(' L ');
  }

  getAreaPath(metric: string): string {
    if (!this.visData.length) return '';
    const bottom = this.PAD_TOP + this.innerH;
    if (this.visData.length === 1) {
      // Single data point: fill the entire chart width at that value
      const y  = this.getY(metric, 0).toFixed(1);
      const x1 = this.PAD_LEFT;
      const x2 = this.PAD_LEFT + this.innerW;
      return `M ${x1},${bottom} L ${x1},${y} L ${x2},${y} L ${x2},${bottom} Z`;
    }
    const first  = this.getLineX(0).toFixed(1);
    const last   = this.getLineX(this.visData.length - 1).toFixed(1);
    const line   = this.visData.map((_, i) =>
      `${this.getLineX(i).toFixed(1)},${this.getY(metric, i).toFixed(1)}`
    ).join(' L ');
    return `M ${first},${bottom} L ${line} L ${last},${bottom} Z`;
  }

  // ── Grid ──────────────────────────────────────────────────────────────────
  gridY(n: number): number {
    return this.PAD_TOP + (n / 4) * this.innerH;
  }

  gridLabel(n: number): string {
    const primary = this.activeMetrics[0];
    if (!primary) return '';
    const value = this.primaryMax - (n / 4) * this.primaryMax;
    const fmt = METRIC_FORMATS[primary];
    return fmt ? fmt(value) : defaultFmt(value);
  }

  // ── X-axis labels ─────────────────────────────────────────────────────────

  /** Effective day-count driving label density (uses zoomed length when zoomed). */
  private get effectiveDays(): number {
    return this.isZoomed
      ? this.visData.length
      : (this.totalDays > 0 ? this.totalDays : this.visData.length);
  }

  get xLabelStep(): number {
    const days = this.effectiveDays;
    if (days <= 7)  return 1;
    if (days <= 14) return 2;
    if (days <= 30) return 7;
    if (days <= 90) return 14;
    return 30;
  }

  showXLabel(i: number): boolean {
    const n = this.visData.length;
    if (n === 0) return false;
    // First and last are always shown as fixed range labels (xRangeStartLabel /
    // xRangeStopLabel), so skip them here to avoid duplicates.
    if (i === 0 || i === n - 1) return false;

    const days = this.effectiveDays;
    if (days > 90) {
      // Year+ range: show first data-point of each calendar month
      const date = this.visData[i]?.['date'] as string | undefined;
      if (!date) return false;
      const monthKey = date.substring(0, 7); // "YYYY-MM"
      for (let j = 0; j < i; j++) {
        const prev = this.visData[j]?.['date'] as string | undefined;
        if (prev && prev.substring(0, 7) === monthKey) return false;
      }
      // Skip if too close to the forced last label
      return i <= n - 4;
    }

    const step = this.xLabelStep;
    // Skip regular step labels that would overlap the forced last label
    if (i > n - 1 - Math.floor(step / 2)) return false;
    return i % step === 0;
  }

  /**
   * Label text for X-axis tick i.
   * Year view → abbreviated month ("Jan"); otherwise full "Jan 15" label.
   */
  getXLabelText(i: number): string {
    const days = this.effectiveDays;
    if (days > 90) {
      const date = this.visData[i]?.['date'] as string | undefined;
      if (date) {
        const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
        const m = parseInt(date.split('-')[1], 10);
        return MONTHS[m - 1] ?? (this.visData[i].label as string);
      }
    }
    return this.visData[i].label as string;
  }

  // ── Dot radius ────────────────────────────────────────────────────────────
  getDotRadius(i: number): number {
    if (this.hoveredIndex === i) return 5;
    const n = this.visData.length;
    if (n <= 30) return 3.5;
    if (n <= 60) return 2.5;
    return 2;
  }

  // ── Crosshair X ───────────────────────────────────────────────────────────
  crosshairX(i: number): number {
    return this.chartType === 'bar' ? this.getBarMidX(i) : this.getLineX(i);
  }

  // ── Hover zones ───────────────────────────────────────────────────────────
  hoverZoneX(i: number): number {
    if (this.chartType === 'bar') return this.PAD_LEFT + i * this.slotW;
    const step = this.innerW / Math.max(this.visData.length - 1, 1);
    return this.PAD_LEFT + i * step - step / 2;
  }

  hoverZoneW(i: number): number {
    if (this.chartType === 'bar') return this.slotW;
    return this.innerW / Math.max(this.visData.length - 1, 1);
  }

  // ── Drag/zoom helpers ─────────────────────────────────────────────────────

  /** Convert a MouseEvent to SVG-space X coordinate. */
  private toSvgX(event: MouseEvent): number {
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    return (event.clientX - rect.left) / (rect.width / this.SVG_W);
  }

  /** Convert SVG-space X to the nearest visData index. */
  private svgXToVisIdx(svgMouseX: number): number {
    const chartX = svgMouseX - this.PAD_LEFT;
    const n = this.visData.length;
    if (this.chartType === 'bar') {
      const slot = this.innerW / Math.max(n, 1);
      return Math.max(0, Math.min(n - 1, Math.floor(chartX / slot)));
    }
    const step = this.innerW / Math.max(n - 1, 1);
    return Math.max(0, Math.min(n - 1, Math.round(chartX / step)));
  }

  // ── Mouse events ──────────────────────────────────────────────────────────

  onSvgMouseDown(event: MouseEvent): void {
    if (!this.data.length) return;
    event.preventDefault();
    const mx = this.toSvgX(event);
    this.isDragging = true;
    this.dragPixelStart   = mx;
    this.dragPixelCurrent = mx;
    this.dragDataStart    = this.svgXToVisIdx(mx);
    this.hoveredIndex     = null;
  }

  onHoverZoneEnter(i: number): void {
    if (this.isDragging) return;
    this.hoveredIndex = i;
    this.cdr.detectChanges();
  }

  onSvgMouseMove(event: MouseEvent): void {
    if (!this.data.length) return;
    const svg   = event.currentTarget as SVGSVGElement;
    const rect  = svg.getBoundingClientRect();
    const scaleX = rect.width  / this.SVG_W;
    const scaleY = rect.height / this.SVG_H;
    const mouseX = (event.clientX - rect.left) / scaleX;
    const mouseY = (event.clientY - rect.top)  / scaleY;
    this.tooltipSvgX = mouseX;
    this.tooltipSvgY = mouseY;

    if (this.isDragging) {
      this.dragPixelCurrent = mouseX;
      this.hoveredIndex     = null;
      this.cdr.detectChanges();
      return;
    }

    // Normal hover
    const chartX = mouseX - this.PAD_LEFT;
    let idx: number;
    if (this.chartType === 'bar') {
      idx = Math.floor(chartX / this.slotW);
    } else {
      const step = this.innerW / Math.max(this.visData.length - 1, 1);
      idx = Math.round(chartX / step);
    }
    this.hoveredIndex = (idx >= 0 && idx < this.visData.length) ? idx : null;
    this.cdr.detectChanges();
  }

  onSvgMouseUp(event: MouseEvent): void {
    if (!this.isDragging) return;
    const mx       = this.toSvgX(event);
    const endIdx   = this.svgXToVisIdx(mx);
    const startIdx = this.dragDataStart;

    this.isDragging    = false;
    this.dragDataStart = -1;

    const lo = Math.min(startIdx, endIdx);
    const hi = Math.max(startIdx, endIdx);

    if (hi - lo >= 1) {
      // Convert visData indices to original data indices
      const offset = this.zoomWindow?.start ?? 0;
      this.zoomWindow = { start: offset + lo, end: offset + hi };
    }
    this.hoveredIndex = null;
  }

  onSvgMouseLeave(): void {
    if (this.isDragging) {
      this.isDragging    = false;
      this.dragDataStart = -1;
    }
    this.hoveredIndex = null;
    this.cdr.detectChanges();
  }

  // ── Tooltip positioning ───────────────────────────────────────────────────
  get tooltipLeftPct(): number {
    return (this.tooltipSvgX / this.SVG_W) * 100;
  }

  get tooltipTopPct(): number {
    // Clamp so tooltip stays within the chart area (top/bottom padding)
    const raw = this.tooltipSvgY / this.SVG_H * 100;
    return Math.max(5, Math.min(raw, 75));
  }

  get tooltipOnLeft(): boolean {
    return this.tooltipSvgX > this.SVG_W * 0.6;
  }

  get tooltipFlipY(): boolean {
    return (this.tooltipSvgY / this.SVG_H) > 0.55;
  }

  /** Metrics shown in the hover tooltip — all 6 KPI blocks when provided, else active metrics. */
  get tooltipMetrics(): {key: string; label: string}[] {
    if (this.kpiMetricBlocks.length) return this.kpiMetricBlocks;
    return this.activeMetrics.map(m => ({ key: m, label: this.getLabel(m) }));
  }

  formatTooltipValue(metric: string, i: number): string {
    const value = (this.visData[i]?.[metric] as number) ?? 0;
    const fmt = METRIC_FORMATS[metric];
    return fmt ? fmt(value) : defaultFmt(value);
  }

  formatTooltipDate(i: number | null): string {
    if (i === null) return '';
    const iso = this.visData[i]?.['date'] as string | undefined;
    if (!iso) return (this.visData[i]?.label as string) ?? '';
    const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    const parts = iso.split('-');
    if (parts.length < 3) return (this.visData[i]?.label as string) ?? '';
    const m = parseInt(parts[1], 10);
    const d = parseInt(parts[2], 10);
    return `${MONTHS[m - 1] ?? ''} ${d}, ${parts[0]}`;
  }
}
