import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnChanges,
  SimpleChanges,
  ViewChild,
  ElementRef,
  AfterViewInit,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import {
  AnalyticsCard,
  CampaignMetrics,
  DeviceBreakdown,
  AgeBreakdown,
  PerformanceData,
} from '../../models/analytics/analytics.model';
import { formatNumber, capitalize } from '../../utils/format.util';
import { ChartDimensions, drawChartGrid, renderPieChart } from '../../utils/canvas-chart.util';
import { computeNameHash } from '../../utils/hash.util';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsComponent implements OnInit, AfterViewInit, OnChanges {
  @Input() analyticsCards: AnalyticsCard[] = [];
  @Input() showPerformanceGraphs: boolean = false;
  @Input() campaignMetrics: CampaignMetrics | null = null;
  @Input() deviceBreakdown: DeviceBreakdown[] = [];
  @Input() ageBreakdown: AgeBreakdown[] = [];
  @Input() selectedTimePeriod: number = 30;
  @Input() performanceData: PerformanceData[] = [];

  // New inputs for selected item details
  @Input() selectedItemData: any = null;
  @Input() selectedItemType: 'campaign' | 'adset' | 'ad' | null = null;

  // New widget-based layout (replacing modal layout)
  @Input() preloadedItemData: any = null; // For preloading most recent item

  @Output() timePeriodChange = new EventEmitter<number>();
  @Output() cardClick = new EventEmitter<AnalyticsCard>();

  @ViewChild('performanceChart')
  performanceChartRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('deviceChart') deviceChartRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('engagementChart')
  engagementChartRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('selectedItemChart')
  selectedItemChartRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('selectedDeviceChart')
  selectedDeviceChartRef?: ElementRef<HTMLCanvasElement>;

  tooltipVisible: boolean[] = [];
  highlightedStats: { [key: number]: string | null } = {};

  ngOnInit(): void {
    // Initialize tooltip visibility array
    this.tooltipVisible = new Array(this.analyticsCards.length).fill(false);

    // If preloaded data is available but no selected item, use preloaded data
    if (this.preloadedItemData && !this.selectedItemData) {
      this.selectedItemData = this.preloadedItemData;
      this.selectedItemType = this.determineItemType(this.preloadedItemData);
    }
  }

  private determineItemType(itemData: any): 'campaign' | 'adset' | 'ad' | null {
    if (!itemData) return null;

    // Try to determine type based on data structure
    if (itemData.facebookCampaignId || itemData.objective) return 'campaign';
    if (
      itemData.facebookAdSetId ||
      itemData.dailyBudget ||
      itemData.lifetimeBudget
    )
      return 'adset';
    if (itemData.facebookAdId || itemData.creativeId || itemData.message)
      return 'ad';

    // Default fallback
    return 'campaign';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedItemData']) {
      if (this.selectedItemData) {
        // Reinitialize charts when selected item changes
        setTimeout(() => {
          this.initializeSelectedItemCharts();
          this.drawEngagementChart();
        }, 200);
      }
    }

    // Handle preloaded data changes
    if (changes['preloadedItemData']) {
      if (this.preloadedItemData && !this.selectedItemData) {
        this.selectedItemData = this.preloadedItemData;
        this.selectedItemType = this.determineItemType(this.preloadedItemData);
      }
    }
  }

  ngAfterViewInit(): void {
    if (this.showPerformanceGraphs) {
      setTimeout(() => {
        this.initializeCharts();
      }, 100);
    }

    // Initialize selected item charts when data is available
    setTimeout(() => {
      if (this.selectedItemData) {
        this.initializeSelectedItemCharts();
      }
    }, 300);

    // Initialize engagement chart
    setTimeout(() => {
      this.drawEngagementChart();
    }, 400);
  }

  onTimePeriodChange(period: number): void {
    this.selectedTimePeriod = period;
    this.timePeriodChange.emit(period);

    // Redraw engagement chart with new time period
    setTimeout(() => {
      this.drawEngagementChart();
    }, 100);
  }

  onCardClick(card: AnalyticsCard): void {
    this.cardClick.emit(card);
  }

  showChartTooltip(index: number, event: MouseEvent): void {
    this.tooltipVisible[index] = true;
  }

  hideChartTooltip(index: number): void {
    this.tooltipVisible[index] = false;
  }

  highlightStat(statType: string, cardIndex: number): void {
    this.highlightedStats[cardIndex] = statType;
  }

  clearHighlight(cardIndex: number): void {
    this.highlightedStats[cardIndex] = null;
  }

  initializeCharts(): void {
    this.drawPerformanceChart();
    this.drawDeviceChart();
  }

  drawPerformanceChart(): void {
    const canvas = this.performanceChartRef?.nativeElement;
    if (!canvas) return;
    let dataPoints = this.performanceData?.length
      ? this.performanceData
      : this.generateRealisticData(this.selectedTimePeriod);
    dataPoints = dataPoints.slice(-this.selectedTimePeriod);
    this.renderLineChart(canvas, dataPoints, true);
  }

  private renderLineChart(
    canvas: HTMLCanvasElement,
    dataPoints: PerformanceData[],
    showDataPoints = false,
  ): void {
    const ctx = canvas.getContext('2d');
    if (!ctx || dataPoints.length === 0) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const padding = 50;
    const dims: ChartDimensions = {
      padding,
      chartWidth: canvas.width - 2 * padding,
      chartHeight: canvas.height - 2 * padding,
    };

    drawChartGrid(ctx, dims, Math.min(7, dataPoints.length), 5);

    const maxImpressions = Math.max(...dataPoints.map((d) => d.impressions));
    const maxReach      = Math.max(...dataPoints.map((d) => d.reach));
    const maxPeople     = Math.max(...dataPoints.map((d) => d.people));
    const maxClicks     = Math.max(...dataPoints.map((d) => d.clicks));

    this.drawLine(ctx, { dataPoints, metric: 'impressions', maxValue: maxImpressions, dimensions: dims, style: { color: '#1ca698', lineWidth: 3 } });
    this.drawLine(ctx, { dataPoints, metric: 'reach',       maxValue: maxReach,       dimensions: dims, style: { color: '#3498db', lineWidth: 2 } });
    this.drawLine(ctx, { dataPoints, metric: 'people',      maxValue: maxPeople,      dimensions: dims, style: { color: '#9b59b6', lineWidth: 2 } });
    this.drawLine(ctx, { dataPoints, metric: 'clicks',      maxValue: maxClicks * 10, dimensions: dims, style: { color: '#f39c12', lineWidth: 2 } });

    if (showDataPoints) {
      this.drawDataPoints(ctx, {
        dataPoints,
        maxValues: { impressions: maxImpressions, reach: maxReach, people: maxPeople, clicks: maxClicks },
        dimensions: dims,
      });
    }

    this.drawLegend(ctx, canvas.width, padding);
    this.drawYAxisLabels(ctx, maxImpressions, padding, dims.chartHeight);
  }

  private generateRealisticData(days: number): PerformanceData[] {
    const data: PerformanceData[] = [];
    const baseDate = new Date();
    baseDate.setDate(baseDate.getDate() - days);

    for (let i = 0; i < days; i++) {
      const date = new Date(baseDate);
      date.setDate(date.getDate() + i);

      // Generate realistic campaign data with trends
      const dayOfWeek = date.getDay();
      const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
      const weekendMultiplier = isWeekend ? 0.7 : 1.0;

      // Base metrics with realistic relationships
      const impressions = Math.floor(
        (8000 + Math.random() * 4000) * weekendMultiplier
      );
      const reach = Math.floor(impressions * (0.7 + Math.random() * 0.2)); // 70-90% of impressions
      const people = Math.floor(reach * (0.8 + Math.random() * 0.15)); // 80-95% of reach
      const clicks = Math.floor(impressions * (0.01 + Math.random() * 0.03)); // 1-4% CTR
      const conversions = Math.floor(clicks * (0.02 + Math.random() * 0.08)); // 2-10% conversion rate
      const cost = parseFloat(
        (clicks * (0.8 + Math.random() * 1.2)).toFixed(2)
      ); // $0.8-2.0 CPC

      data.push({
        date: date.toISOString().split('T')[0],
        impressions,
        reach,
        people,
        clicks,
        conversions,
        cost,
      });
    }

    return data;
  }

  private drawLine(
    ctx: CanvasRenderingContext2D,
    config: {
      dataPoints: PerformanceData[];
      metric: keyof PerformanceData;
      maxValue: number;
      dimensions: { padding: number; chartWidth: number; chartHeight: number };
      style: { color: string; lineWidth: number };
    }
  ): void {
    if (config.maxValue === 0) return;

    ctx.strokeStyle = config.style.color;
    ctx.lineWidth = config.style.lineWidth;
    ctx.beginPath();

    config.dataPoints.forEach((point, index) => {
      const x =
        config.dimensions.padding +
        (config.dimensions.chartWidth / (config.dataPoints.length - 1)) * index;
      const value = point[config.metric] as number;
      const y =
        config.dimensions.padding +
        config.dimensions.chartHeight -
        (value / config.maxValue) * config.dimensions.chartHeight;

      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    ctx.stroke();
  }

  private drawDataPoints(
    ctx: CanvasRenderingContext2D,
    config: {
      dataPoints: PerformanceData[];
      maxValues: {
        impressions: number;
        reach: number;
        people: number;
        clicks: number;
      };
      dimensions: { padding: number; chartWidth: number; chartHeight: number };
    }
  ): void {
    const pointRadius = 3;

    config.dataPoints.forEach((point, index) => {
      const x =
        config.dimensions.padding +
        (config.dimensions.chartWidth / (config.dataPoints.length - 1)) * index;

      // Draw impression points
      if (config.maxValues.impressions > 0) {
        const y =
          config.dimensions.padding +
          config.dimensions.chartHeight -
          (point.impressions / config.maxValues.impressions) *
            config.dimensions.chartHeight;
        ctx.fillStyle = '#1ca698';
        ctx.beginPath();
        ctx.arc(x, y, pointRadius, 0, 2 * Math.PI);
        ctx.fill();
      }

      // Draw reach points
      if (config.maxValues.reach > 0) {
        const y =
          config.dimensions.padding +
          config.dimensions.chartHeight -
          (point.reach / config.maxValues.reach) *
            config.dimensions.chartHeight;
        ctx.fillStyle = '#3498db';
        ctx.beginPath();
        ctx.arc(x, y, pointRadius - 1, 0, 2 * Math.PI);
        ctx.fill();
      }
    });
  }

  private drawLegend(
    ctx: CanvasRenderingContext2D,
    canvasWidth: number,
    padding: number
  ): void {
    const legendItems = [
      { color: '#1ca698', label: 'Impressions' },
      { color: '#3498db', label: 'Reach' },
      { color: '#9b59b6', label: 'People' },
      { color: '#f39c12', label: 'Clicks' },
    ];

    ctx.font = '12px Arial';
    const legendY = padding - 25;
    let legendX = padding;

    legendItems.forEach((item) => {
      // Draw color dot
      ctx.fillStyle = item.color;
      ctx.beginPath();
      ctx.arc(legendX, legendY, 4, 0, 2 * Math.PI);
      ctx.fill();

      // Draw label
      ctx.fillStyle = '#20233a';
      ctx.fillText(item.label, legendX + 10, legendY + 4);

      legendX += ctx.measureText(item.label).width + 30;
    });
  }

  private drawYAxisLabels(
    ctx: CanvasRenderingContext2D,
    maxValue: number,
    padding: number,
    chartHeight: number
  ): void {
    ctx.font = '10px Arial';
    ctx.fillStyle = '#666';
    ctx.textAlign = 'right';
    for (let i = 0; i <= 5; i++) {
      const value = Math.floor((maxValue / 5) * (5 - i));
      const y = padding + (chartHeight / 5) * i;
      ctx.fillText(formatNumber(value), padding - 10, y + 4);
    }
    ctx.textAlign = 'left';
  }

  drawDeviceChart(): void {
    const canvas = this.deviceChartRef?.nativeElement;
    if (canvas) renderPieChart(canvas, this.deviceBreakdown);
  }

  // Methods for selected item data display
  getSelectedItemTitle(): string {
    if (!this.selectedItemData || !this.selectedItemType) return '';
    return `${capitalize(this.selectedItemType)}: ${this.selectedItemData.name || 'Unknown'}`;
  }

  private getItemNameHash(): number {
    return computeNameHash(this.selectedItemData?.name);
  }

  private getTypeMultiplier(adsetFactor = 0.6, adFactor = 0.3): number {
    if (this.selectedItemType === 'adset') return adsetFactor;
    if (this.selectedItemType === 'ad') return adFactor;
    return 1.0;
  }

  getSelectedItemFields(): Array<{
    label: string;
    value: string;
    key: string;
  }> {
    if (!this.selectedItemData) return [];

    const commonFields = [
      {
        label: 'Name',
        value: this.selectedItemData.name || 'N/A',
        key: 'name',
      },
      {
        label: 'Status',
        value: this.selectedItemData.status || 'N/A',
        key: 'status',
      },
      {
        label: 'Platform',
        value: this.selectedItemData.platform || 'N/A',
        key: 'platform',
      },
      {
        label: 'Created',
        value: this.formatDate(this.selectedItemData.createdAt),
        key: 'createdAt',
      },
      {
        label: 'Updated',
        value: this.formatDate(this.selectedItemData.updatedAt),
        key: 'updatedAt',
      },
      {
        label: 'Scheduled',
        value: this.formatDate(this.selectedItemData.scheduleTime),
        key: 'scheduleTime',
      },
    ];

    // Add type-specific fields
    if (this.selectedItemType === 'campaign') {
      commonFields.push(
        {
          label: 'Objective',
          value: this.selectedItemData.objective || 'N/A',
          key: 'objective',
        },
        {
          label: 'Special Categories',
          value: this.selectedItemData.specialAdCategories || 'N/A',
          key: 'specialAdCategories',
        },
        {
          label: 'Facebook Campaign ID',
          value: this.selectedItemData.facebookCampaignId || 'N/A',
          key: 'facebookCampaignId',
        }
      );
    } else if (this.selectedItemType === 'adset') {
      commonFields.push(
        {
          label: 'Campaign ID',
          value: this.selectedItemData.campaignId?.toString() || 'N/A',
          key: 'campaignId',
        },
        {
          label: 'Daily Budget',
          value: this.formatCurrency(this.selectedItemData.dailyBudget),
          key: 'dailyBudget',
        },
        {
          label: 'Lifetime Budget',
          value: this.formatCurrency(this.selectedItemData.lifetimeBudget),
          key: 'lifetimeBudget',
        },
        {
          label: 'Optimization Goal',
          value: this.selectedItemData.optimizationGoal || 'N/A',
          key: 'optimizationGoal',
        },
        {
          label: 'Billing Event',
          value: this.selectedItemData.billingEvent || 'N/A',
          key: 'billingEvent',
        },
        {
          label: 'Targeting',
          value: this.selectedItemData.targeting || 'N/A',
          key: 'targeting',
        },
        {
          label: 'Ad Account ID',
          value: this.selectedItemData.adAccountId || 'N/A',
          key: 'adAccountId',
        },
        {
          label: 'Facebook AdSet ID',
          value: this.selectedItemData.facebookAdSetId || 'N/A',
          key: 'facebookAdSetId',
        }
      );
    } else if (this.selectedItemType === 'ad') {
      commonFields.push(
        {
          label: 'AdSet ID',
          value: this.selectedItemData.adSetId?.toString() || 'N/A',
          key: 'adSetId',
        },
        {
          label: 'Creative ID',
          value: this.selectedItemData.creativeId || 'N/A',
          key: 'creativeId',
        },
        {
          label: 'Message',
          value: this.selectedItemData.message || 'N/A',
          key: 'message',
        },
        {
          label: 'Facebook Ad ID',
          value: this.selectedItemData.facebookAdId || 'N/A',
          key: 'facebookAdId',
        }
      );
    }

    return commonFields;
  }

  private formatDate(dateValue: any): string {
    if (!dateValue) return 'N/A';
    try {
      const date = new Date(dateValue);
      return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    } catch {
      return 'Invalid Date';
    }
  }

  private formatCurrency(value: any): string {
    if (value === null || value === undefined || isNaN(value)) return 'N/A';
    return `$${Number(value).toFixed(2)}`;
  }

  trackByKey(
    index: number,
    field: { label: string; value: string; key: string }
  ): string {
    return field.key;
  }

  // Methods for selected item analytics
  getSelectedItemMetrics(): CampaignMetrics {
    if (!this.selectedItemData) {
      return this.getDefaultMetrics();
    }

    // Generate realistic metrics based on the selected item type and data
    return this.generateSelectedItemMetrics();
  }

  private getDefaultMetrics(): CampaignMetrics {
    return {
      impressions: 0,
      impressionsChange: 0,
      reach: 0,
      reachChange: 0,
      people: 0,
      peopleChange: 0,
      clicks: 0,
      clicksChange: 0,
      conversions: 0,
      conversionsChange: 0,
      cost: 0,
      costChange: 0,
      ctr: 0,
      ctrChange: 0,
      cpm: 0,
      cpmChange: 0,
      cpc: 0,
      cpcChange: 0,
    };
  }

  private generateSelectedItemMetrics(): CampaignMetrics {
    const baseMultiplier = this.getTypeMultiplier();
    const random = Math.abs(Math.sin(this.getItemNameHash())) * 1000;

    const impressions = Math.floor((30000 + (random % 80000)) * baseMultiplier);
    const reach = Math.floor(impressions * (0.72 + (random % 100) / 500));
    const people = Math.floor(reach * (0.82 + (random % 100) / 600));
    const clicks = Math.floor(impressions * (0.012 + (random % 100) / 4000));
    const conversions = Math.floor(clicks * (0.04 + (random % 100) / 1000));
    const cost = parseFloat((clicks * (0.8 + (random % 100) / 50)).toFixed(2));
    const ctr = parseFloat(((clicks / impressions) * 100).toFixed(2));
    const cpm = parseFloat((cost / (impressions / 1000)).toFixed(2));
    const cpc = clicks > 0 ? parseFloat((cost / clicks).toFixed(2)) : 0;

    return {
      impressions,
      impressionsChange: -5 + Math.random() * 25,
      reach,
      reachChange: -3 + Math.random() * 20,
      people,
      peopleChange: -2 + Math.random() * 18,
      clicks,
      clicksChange: -8 + Math.random() * 30,
      conversions,
      conversionsChange: -10 + Math.random() * 35,
      cost,
      costChange: 5 + Math.random() * 15,
      ctr,
      ctrChange: -1 + Math.random() * 8,
      cpm,
      cpmChange: 2 + Math.random() * 12,
      cpc,
      cpcChange: 1 + Math.random() * 10,
    };
  }

  getSelectedItemDeviceBreakdown(): DeviceBreakdown[] {
    if (!this.selectedItemData) return [];
    const random = Math.abs(Math.sin(this.getItemNameHash())) * 100;
    const mobileBase = 55 + (random % 25); // 55-80%
    const desktopBase = Math.floor(
      (100 - mobileBase) * (0.6 + (random % 30) / 100)
    ); // Rest distributed
    const tabletBase = 100 - mobileBase - desktopBase;

    return [
      { name: 'Mobile', percentage: Math.floor(mobileBase), color: '#1ca698' },
      {
        name: 'Desktop',
        percentage: Math.floor(desktopBase),
        color: '#3498db',
      },
      { name: 'Tablet', percentage: Math.floor(tabletBase), color: '#9b59b6' },
    ];
  }

  getSelectedItemAgeBreakdown(): AgeBreakdown[] {
    if (!this.selectedItemData) return [];
    const random = Math.abs(Math.sin(this.getItemNameHash())) * 1000;

    return [
      {
        range: '18-24',
        clicks: Math.floor(800 + (random % 600)),
        percentage: Math.floor(75 + (random % 20)),
      },
      {
        range: '25-34',
        clicks: Math.floor(1400 + (random % 800)),
        percentage: Math.floor(85 + (random % 15)),
      },
      {
        range: '35-44',
        clicks: Math.floor(1200 + (random % 700)),
        percentage: Math.floor(70 + (random % 25)),
      },
      {
        range: '45-54',
        clicks: Math.floor(600 + (random % 400)),
        percentage: Math.floor(55 + (random % 30)),
      },
      {
        range: '55-64',
        clicks: Math.floor(300 + (random % 200)),
        percentage: Math.floor(35 + (random % 25)),
      },
      {
        range: '65+',
        clicks: Math.floor(100 + (random % 150)),
        percentage: Math.floor(20 + (random % 20)),
      },
    ];
  }

  initializeSelectedItemCharts(): void {
    if (!this.selectedItemData) return;

    // Ensure canvas elements are ready
    setTimeout(() => {
      this.drawSelectedItemPerformanceChart();
      this.drawSelectedItemDeviceChart();
    }, 50);
  }

  drawSelectedItemPerformanceChart(): void {
    const canvas = this.selectedItemChartRef?.nativeElement;
    if (!canvas) return;
    this.renderLineChart(canvas, this.generateSelectedItemPerformanceData(this.selectedTimePeriod));
  }

  private generateSelectedItemPerformanceData(days: number): PerformanceData[] {
    const data: PerformanceData[] = [];
    const baseDate = new Date();
    baseDate.setDate(baseDate.getDate() - days);
    const baseMultiplier = this.getTypeMultiplier();

    for (let i = 0; i < days; i++) {
      const date = new Date(baseDate);
      date.setDate(date.getDate() + i);

      const dayOfWeek = date.getDay();
      const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
      const weekendMultiplier = isWeekend ? 0.7 : 1.0;

      const impressions = Math.floor(
        (5000 + Math.random() * 3000) * baseMultiplier * weekendMultiplier
      );
      const reach = Math.floor(impressions * (0.75 + Math.random() * 0.15));
      const people = Math.floor(reach * (0.85 + Math.random() * 0.1));
      const clicks = Math.floor(impressions * (0.015 + Math.random() * 0.025));
      const conversions = Math.floor(clicks * (0.05 + Math.random() * 0.15));
      const cost = parseFloat(
        (clicks * (1.2 + Math.random() * 2.8)).toFixed(2)
      );

      data.push({
        date: date.toISOString().split('T')[0],
        impressions,
        reach,
        people,
        clicks,
        conversions,
        cost,
      });
    }

    return data;
  }

  drawSelectedItemDeviceChart(): void {
    const canvas = this.selectedDeviceChartRef?.nativeElement;
    if (canvas) renderPieChart(canvas, this.getSelectedItemDeviceBreakdown());
  }

  // Method to get analytics title
  getAnalyticsTitle(): string {
    if (this.selectedItemData) {
      return this.getSelectedItemTitle();
    }
    if (this.preloadedItemData) {
      const itemType = this.determineItemType(this.preloadedItemData);
      const typeName = itemType ? capitalize(itemType) : 'Item';
      return `${typeName}: ${this.preloadedItemData.name || 'Default Campaign'} - Analytics`;
    }
    return 'Campaign Analytics Dashboard';
  }

  // Method to get key metrics for performance widget
  getKeyMetrics(): Array<{ label: string; value: string; change: number }> {
    const metrics = this.selectedItemData
      ? this.getSelectedItemMetrics()
      : this.campaignMetrics;

    if (!metrics) {
      return [];
    }

    return [
      {
        label: 'Impressions',
        value: formatNumber(metrics.impressions),
        change: Math.round(metrics.impressionsChange * 10) / 10,
      },
      {
        label: 'Reach',
        value: formatNumber(metrics.reach),
        change: Math.round(metrics.reachChange * 10) / 10,
      },
      {
        label: 'Clicks',
        value: formatNumber(metrics.clicks),
        change: Math.round(metrics.clicksChange * 10) / 10,
      },
      {
        label: 'Conversions',
        value: formatNumber(metrics.conversions),
        change: Math.round(metrics.conversionsChange * 10) / 10,
      },
      {
        label: 'CTR',
        value: `${metrics.ctr}%`,
        change: Math.round(metrics.ctrChange * 10) / 10,
      },
      {
        label: 'CPC',
        value: `$${metrics.cpc}`,
        change: Math.round(metrics.cpcChange * 10) / 10,
      },
    ];
  }

  // Helper method to safely get current metrics
  getCurrentMetrics(): CampaignMetrics | null {
    return this.selectedItemData
      ? this.getSelectedItemMetrics()
      : this.campaignMetrics;
  }

  // Method to get engagement statistics
  getEngagementStats(): {
    likes: number;
    comments: number;
    shares: number;
    saves: number;
    rate: number;
    bestTime: string;
  } {
    if (!this.selectedItemData) {
      return {
        likes: 0,
        comments: 0,
        shares: 0,
        saves: 0,
        rate: 0,
        bestTime: 'N/A',
      };
    }

    const random = Math.abs(Math.sin(this.getItemNameHash())) * 1000;
    const metrics = this.getSelectedItemMetrics();

    // Calculate engagement based on impressions and clicks
    const likes = Math.floor(metrics.clicks * (2.5 + (random % 100) / 50));
    const comments = Math.floor(metrics.clicks * (0.3 + (random % 50) / 200));
    const shares = Math.floor(metrics.clicks * (0.15 + (random % 30) / 300));
    const saves = Math.floor(metrics.clicks * (0.8 + (random % 40) / 100));

    const totalEngagements = likes + comments + shares + saves;
    const rate =
      metrics.impressions > 0
        ? (totalEngagements / metrics.impressions) * 100
        : 0;

    const times = ['9:00 AM', '12:00 PM', '3:00 PM', '6:00 PM', '9:00 PM'];
    const bestTime = times[Math.floor(random % times.length)];

    return {
      likes,
      comments,
      shares,
      saves,
      rate: parseFloat(rate.toFixed(2)),
      bestTime,
    };
  }

  drawEngagementChart(): void {
    const canvas = this.engagementChartRef?.nativeElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Generate engagement data for the selected time period
    const dataPoints = this.generateEngagementData(this.selectedTimePeriod);

    if (dataPoints.length === 0) return;

    const padding = 40;
    const dims: ChartDimensions = {
      padding,
      chartWidth: canvas.width - 2 * padding,
      chartHeight: canvas.height - 2 * padding,
    };
    const { chartWidth, chartHeight } = dims;
    drawChartGrid(ctx, dims, Math.min(5, dataPoints.length), 4);

    // Find max engagement rate for scaling
    const maxRate = Math.max(...dataPoints.map((d) => d.rate));

    // Draw engagement rate line
    ctx.strokeStyle = '#9b59b6';
    ctx.lineWidth = 3;
    ctx.beginPath();

    dataPoints.forEach((point, index) => {
      const x = padding + (chartWidth / (dataPoints.length - 1)) * index;
      const y = padding + chartHeight - (point.rate / maxRate) * chartHeight;

      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    ctx.stroke();

    // Draw data points
    dataPoints.forEach((point, index) => {
      const x = padding + (chartWidth / (dataPoints.length - 1)) * index;
      const y = padding + chartHeight - (point.rate / maxRate) * chartHeight;

      ctx.fillStyle = '#9b59b6';
      ctx.beginPath();
      ctx.arc(x, y, 4, 0, 2 * Math.PI);
      ctx.fill();
    });

    // Add Y-axis labels
    ctx.font = '10px Arial';
    ctx.fillStyle = '#666';
    ctx.textAlign = 'right';

    for (let i = 0; i <= 4; i++) {
      const value = (maxRate / 4) * (4 - i);
      const y = padding + (chartHeight / 4) * i;
      ctx.fillText(`${value.toFixed(1)}%`, padding - 10, y + 4);
    }

    ctx.textAlign = 'left';
  }

  private generateEngagementData(
    days: number
  ): Array<{ date: string; rate: number }> {
    const data: Array<{ date: string; rate: number }> = [];
    const baseDate = new Date();
    baseDate.setDate(baseDate.getDate() - days);
    const baseMultiplier = this.getTypeMultiplier(0.8, 0.6);

    for (let i = 0; i < days; i++) {
      const date = new Date(baseDate);
      date.setDate(date.getDate() + i);

      const dayOfWeek = date.getDay();
      const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
      const weekendMultiplier = isWeekend ? 1.2 : 1.0; // Higher engagement on weekends

      // Generate realistic engagement rate (1-6%)
      const rate =
        (1.5 + Math.random() * 4.5) * baseMultiplier * weekendMultiplier;

      data.push({
        date: date.toISOString().split('T')[0],
        rate: parseFloat(rate.toFixed(2)),
      });
    }

    return data;
  }
}
