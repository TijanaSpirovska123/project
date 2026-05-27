import {
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  ChangeDetectionStrategy,
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
  @Input() data: ChartDataPoint[] = [];
  @Input() previousData: ChartDataPoint[] = [];
  @Input() activeMetrics: string[] = [];
  @Input() chartType: 'line' | 'bar' = 'line';
  /** Total number of days in the selected range — drives X-axis density. */
  @Input() totalDays = 0;

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

  private primaryMax = 1;
  readonly metricColors = METRIC_COLORS;
  readonly gridLines = [0, 1, 2, 3, 4];

  ngOnChanges(_changes: SimpleChanges): void {
    this.computePrimaryMax();
    this.hoveredIndex = null;
  }

  private computePrimaryMax(): void {
    const primary = this.activeMetrics[0];
    if (!primary || !this.data.length) { this.primaryMax = 1; return; }
    const values = this.data.map(d => (d[primary] as number) ?? 0);
    this.primaryMax = Math.max(...values, 1);
  }

  getColor(metric: string): string {
    return METRIC_COLORS[metric] ?? '#60A5FA';
  }

  getLabel(metric: string): string {
    return METRIC_LABELS[metric] ?? metric;
  }

  // ── Line mode: edge-to-edge X positioning ──
  getLineX(i: number): number {
    if (this.data.length <= 1) return this.PAD_LEFT + this.innerW / 2;
    return this.PAD_LEFT + (i / (this.data.length - 1)) * this.innerW;
  }

  // ── Bar mode: slot-based X positioning ──
  get slotW(): number { return this.innerW / Math.max(this.data.length, 1); }
  get barW(): number { return this.slotW * 0.6; }

  getBarX(i: number): number {
    return this.PAD_LEFT + i * this.slotW + this.slotW * 0.2;
  }

  getBarMidX(i: number): number {
    return this.getBarX(i) + this.barW / 2;
  }

  // ── Y positioning (primary metric scale) ──
  getY(metric: string, i: number): number {
    const value = (this.data[i]?.[metric] as number) ?? 0;
    const ratio = value / this.primaryMax;
    return this.PAD_TOP + this.innerH - ratio * this.innerH;
  }

  getBarHeight(metric: string, i: number): number {
    const value = (this.data[i]?.[metric] as number) ?? 0;
    return (value / this.primaryMax) * this.innerH;
  }

  getBarY(metric: string, i: number): number {
    return this.PAD_TOP + this.innerH - this.getBarHeight(metric, i);
  }

  // ── SVG paths ──
  getLinePath(metric: string): string {
    if (!this.data.length) return '';
    return 'M ' + this.data.map((_, i) =>
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
    const ratio = value / this.primaryMax;
    return this.PAD_TOP + this.innerH - ratio * this.innerH;
  }

  getPrevLinePath(metric: string): string {
    if (!this.previousData.length) return '';
    return 'M ' + this.previousData.map((_, i) =>
      `${this.getPrevLineX(i).toFixed(1)},${this.getPrevY(metric, i).toFixed(1)}`
    ).join(' L ');
  }

  getAreaPath(metric: string): string {
    if (!this.data.length) return '';
    const bottom = this.PAD_TOP + this.innerH;
    const first = this.getLineX(0).toFixed(1);
    const last = this.getLineX(this.data.length - 1).toFixed(1);
    const line = this.data.map((_, i) =>
      `${this.getLineX(i).toFixed(1)},${this.getY(metric, i).toFixed(1)}`
    ).join(' L ');
    return `M ${first},${bottom} L ${line} L ${last},${bottom} Z`;
  }

  // ── Grid ──
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

  // ── X-axis labels ──
  get xLabelStep(): number {
    const days = this.totalDays > 0 ? this.totalDays : this.data.length;
    if (days <= 7)  return 1;
    if (days <= 14) return 2;
    if (days <= 30) return 7;
    if (days <= 90) return 14;
    return 30; // fallback; year range uses month-grouping in showXLabel()
  }

  showXLabel(i: number): boolean {
    const days = this.totalDays > 0 ? this.totalDays : this.data.length;
    if (days > 90) {
      // Year+ range: show only the first data point of each calendar month
      const date = this.data[i]?.['date'] as string | undefined;
      if (!date) return i % 30 === 0;
      const monthKey = date.substring(0, 7); // "YYYY-MM"
      for (let j = 0; j < i; j++) {
        const prevDate = this.data[j]?.['date'] as string | undefined;
        if (prevDate && prevDate.substring(0, 7) === monthKey) return false;
      }
      return true;
    }
    return i % this.xLabelStep === 0;
  }

  /**
   * Returns the text to display on the X-axis for data point i.
   * Year range → just the month abbreviation ("Jan", "Feb", …).
   * All other ranges → the full label already set on the data point ("Jan 15").
   */
  getXLabelText(i: number): string {
    const days = this.totalDays > 0 ? this.totalDays : this.data.length;
    if (days > 90) {
      const date = this.data[i]?.['date'] as string | undefined;
      if (date) {
        const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
        const m = parseInt(date.split('-')[1], 10);
        return MONTHS[m - 1] ?? (this.data[i].label as string);
      }
    }
    return this.data[i].label as string;
  }

  // ── Dot radius helpers ──
  /**
   * Returns the radius of a data-point dot.
   * Scales down for dense datasets to avoid visual overlap.
   */
  getDotRadius(i: number): number {
    if (this.hoveredIndex === i) return 5;
    const n = this.data.length;
    if (n <= 30)  return 3.5;
    if (n <= 60)  return 2.5;
    return 2;
  }

  // ── Crosshair X ──
  crosshairX(i: number): number {
    return this.chartType === 'bar' ? this.getBarMidX(i) : this.getLineX(i);
  }

  // ── Hover zones ──
  hoverZoneX(i: number): number {
    if (this.chartType === 'bar') return this.PAD_LEFT + i * this.slotW;
    const step = this.innerW / Math.max(this.data.length - 1, 1);
    return this.PAD_LEFT + i * step - step / 2;
  }

  hoverZoneW(i: number): number {
    if (this.chartType === 'bar') return this.slotW;
    return this.innerW / Math.max(this.data.length - 1, 1);
  }

  // ── Mouse events ──
  onSvgMouseMove(event: MouseEvent): void {
    if (!this.data.length) return;
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    const scaleX = rect.width / this.SVG_W;
    const mouseX = (event.clientX - rect.left) / scaleX;
    this.tooltipSvgX = mouseX;

    let idx: number;
    const chartX = mouseX - this.PAD_LEFT;
    if (this.chartType === 'bar') {
      idx = Math.floor(chartX / this.slotW);
    } else {
      const step = this.innerW / Math.max(this.data.length - 1, 1);
      idx = Math.round(chartX / step);
    }
    this.hoveredIndex = (idx >= 0 && idx < this.data.length) ? idx : null;
  }

  onSvgMouseLeave(): void {
    this.hoveredIndex = null;
  }

  // ── Tooltip positioning ──
  get tooltipLeftPct(): number {
    return (this.tooltipSvgX / this.SVG_W) * 100;
  }

  get tooltipOnLeft(): boolean {
    return this.tooltipSvgX > this.SVG_W * 0.6;
  }

  formatTooltipValue(metric: string, i: number): string {
    const value = (this.data[i]?.[metric] as number) ?? 0;
    const fmt = METRIC_FORMATS[metric];
    return fmt ? fmt(value) : defaultFmt(value);
  }
}
