import {
  Component,
  Input,
  OnInit,
  OnChanges,
  SimpleChanges,
  ViewChild,
  ElementRef,
  AfterViewInit,
  ChangeDetectionStrategy,
} from '@angular/core';
import { ModalConfig } from '../../models/analytics/analytics-modal.model';
import { computeNameHash } from '../../utils/hash.util';
import { BarItem, drawVerticalBarChart, renderPieChart } from '../../utils/canvas-chart.util';

@Component({
  selector: 'app-analytics-modal',
  standalone: false,
  templateUrl: './analytics-modal.component.html',
  styleUrls: ['./analytics-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsModalComponent
  implements OnInit, AfterViewInit, OnChanges
{
  @Input() modalConfig!: ModalConfig;
  @Input() itemData: any = null;
  @Input() itemType: 'campaign' | 'adset' | 'ad' | null = null;
  @Input() selectedTimePeriod: number = 30;

  @ViewChild('chartCanvas') chartCanvasRef?: ElementRef<HTMLCanvasElement>;

  // Computed data based on modal type
  modalMetrics: any = {};
  chartData: any[] = [];

  ngOnInit(): void {
    this.generateModalData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['itemData'] ||
      changes['modalConfig'] ||
      changes['selectedTimePeriod']
    ) {
      this.generateModalData();

      // Reinitialize charts when data changes
      if (this.chartCanvasRef) {
        setTimeout(() => {
          this.drawModalChart();
        }, 100);
      }
    }
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.drawModalChart();
    }, 200);
  }

  private getTypeMultiplier(adsetFactor = 0.6, adFactor = 0.3): number {
    if (this.itemType === 'adset') return adsetFactor;
    if (this.itemType === 'ad') return adFactor;
    return 1.0;
  }

  private generateModalData(): void {
    if (!this.itemData || !this.modalConfig) return;
    const random = Math.abs(Math.sin(computeNameHash(this.itemData?.name))) * 1000;

    switch (this.modalConfig.type) {
      case 'performance':
        this.generatePerformanceData(random);
        break;
      case 'demographics':
        this.generateDemographicsData(random);
        break;
      case 'devices':
        this.generateDevicesData(random);
        break;
      case 'timeline':
        this.generateTimelineData(random);
        break;
      case 'conversions':
        this.generateConversionsData(random);
        break;
      case 'budget':
        this.generateBudgetData(random);
        break;
      case 'engagement':
        this.generateEngagementData(random);
        break;
    }
  }

  private generatePerformanceData(random: number): void {
    const baseMultiplier = this.getTypeMultiplier();
    const impressions = Math.floor((30000 + (random % 80000)) * baseMultiplier);
    const reach = Math.floor(impressions * (0.72 + (random % 100) / 500));
    const clicks = Math.floor(impressions * (0.012 + (random % 100) / 4000));
    const ctr = parseFloat(((clicks / impressions) * 100).toFixed(2));

    this.modalMetrics = {
      impressions: impressions.toLocaleString(),
      reach: reach.toLocaleString(),
      clicks: clicks.toLocaleString(),
      ctr: `${ctr}%`,
      impressionsChange: (-5 + Math.random() * 25).toFixed(1),
      reachChange: (-3 + Math.random() * 20).toFixed(1),
      clicksChange: (-8 + Math.random() * 30).toFixed(1),
      ctrChange: (-1 + Math.random() * 8).toFixed(1),
    };
  }

  private generateDemographicsData(random: number): void {
    this.modalMetrics = {
      ageGroups: [
        { range: '18-24', percentage: Math.floor(15 + (random % 20)) },
        { range: '25-34', percentage: Math.floor(25 + (random % 15)) },
        { range: '35-44', percentage: Math.floor(20 + (random % 15)) },
        { range: '45-54', percentage: Math.floor(15 + (random % 10)) },
        { range: '55-64', percentage: Math.floor(10 + (random % 10)) },
        { range: '65+', percentage: Math.floor(5 + (random % 10)) },
      ],
      genderSplit: [
        { gender: 'Female', percentage: Math.floor(45 + (random % 20)) },
        { gender: 'Male', percentage: Math.floor(40 + (random % 20)) },
        { gender: 'Other', percentage: Math.floor(1 + (random % 5)) },
      ],
    };
  }

  private generateDevicesData(random: number): void {
    const mobileBase = 55 + (random % 25);
    const desktopBase = Math.floor(
      (100 - mobileBase) * (0.6 + (random % 30) / 100)
    );
    const tabletBase = 100 - mobileBase - desktopBase;

    this.modalMetrics = {
      devices: [
        {
          name: 'Mobile',
          percentage: Math.floor(mobileBase),
          color: '#1ca698',
        },
        {
          name: 'Desktop',
          percentage: Math.floor(desktopBase),
          color: '#3498db',
        },
        {
          name: 'Tablet',
          percentage: Math.floor(tabletBase),
          color: '#9b59b6',
        },
      ],
      topBrowsers: [
        { name: 'Chrome', percentage: Math.floor(60 + (random % 20)) },
        { name: 'Safari', percentage: Math.floor(20 + (random % 15)) },
        { name: 'Firefox', percentage: Math.floor(8 + (random % 10)) },
        { name: 'Edge', percentage: Math.floor(5 + (random % 5)) },
        { name: 'Other', percentage: Math.floor(2 + (random % 5)) },
      ],
    };
  }

  private generateTimelineData(random: number): void {
    this.modalMetrics = {
      peakHours: [
        { hour: '9:00', activity: Math.floor(70 + (random % 30)) },
        { hour: '12:00', activity: Math.floor(85 + (random % 15)) },
        { hour: '15:00', activity: Math.floor(75 + (random % 25)) },
        { hour: '18:00', activity: Math.floor(90 + (random % 10)) },
        { hour: '21:00', activity: Math.floor(80 + (random % 20)) },
      ],
      weekdayVsWeekend: {
        weekdays: Math.floor(65 + (random % 20)),
        weekends: Math.floor(35 + (random % 20)),
      },
    };
  }

  private generateConversionsData(random: number): void {
    const clicks = Math.floor(1000 + (random % 2000));
    const conversions = Math.floor(clicks * (0.02 + (random % 100) / 1000));

    this.modalMetrics = {
      conversions: conversions.toLocaleString(),
      conversionRate: ((conversions / clicks) * 100).toFixed(2) + '%',
      costPerConversion: '$' + (50 + (random % 100)).toFixed(2),
      conversionTypes: [
        { type: 'Purchase', count: Math.floor(conversions * 0.4) },
        { type: 'Lead', count: Math.floor(conversions * 0.3) },
        { type: 'Sign Up', count: Math.floor(conversions * 0.2) },
        { type: 'Download', count: Math.floor(conversions * 0.1) },
      ],
    };
  }

  private generateBudgetData(random: number): void {
    const totalBudget = 1000 + (random % 5000);
    const spent = totalBudget * (0.65 + (random % 300) / 1000);

    this.modalMetrics = {
      totalBudget: '$' + totalBudget.toFixed(2),
      spent: '$' + spent.toFixed(2),
      remaining: '$' + (totalBudget - spent).toFixed(2),
      spentPercentage: ((spent / totalBudget) * 100).toFixed(1),
      averageCPC: '$' + (0.8 + (random % 200) / 100).toFixed(2),
      averageCPM: '$' + (5 + (random % 15)).toFixed(2),
      dailySpend: [
        { date: '2025-01-01', spend: spent / 30 + (random % 100) },
        { date: '2025-01-02', spend: spent / 30 + (random % 120) },
        { date: '2025-01-03', spend: spent / 30 + (random % 90) },
        { date: '2025-01-04', spend: spent / 30 + (random % 110) },
        { date: '2025-01-05', spend: spent / 30 + (random % 80) },
      ],
    };
  }

  private generateEngagementData(random: number): void {
    const impressions = 10000 + (random % 20000);

    this.modalMetrics = {
      likes: Math.floor(impressions * (0.005 + (random % 50) / 10000)),
      shares: Math.floor(impressions * (0.001 + (random % 20) / 10000)),
      comments: Math.floor(impressions * (0.002 + (random % 30) / 10000)),
      saves: Math.floor(impressions * (0.003 + (random % 40) / 10000)),
      engagementRate: (0.5 + (random % 300) / 100).toFixed(2) + '%',
      topEngagingContent: [
        { type: 'Image', engagement: Math.floor(60 + (random % 30)) + '%' },
        { type: 'Video', engagement: Math.floor(70 + (random % 25)) + '%' },
        { type: 'Carousel', engagement: Math.floor(50 + (random % 35)) + '%' },
      ],
    };
  }

  private drawModalChart(): void {
    const canvas = this.chartCanvasRef?.nativeElement;
    if (!canvas || !this.modalConfig) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    switch (this.modalConfig.type) {
      case 'performance':
        this.drawPerformanceChart(ctx, canvas);
        break;
      case 'demographics':
        this.drawDemographicsChart(ctx, canvas);
        break;
      case 'devices':
        this.drawDeviceChart(ctx, canvas);
        break;
      case 'timeline':
        this.drawTimelineChart(ctx, canvas);
        break;
      case 'conversions':
        this.drawConversionsChart(ctx, canvas);
        break;
      case 'budget':
        this.drawBudgetChart(ctx, canvas);
        break;
      case 'engagement':
        this.drawEngagementChart(ctx, canvas);
        break;
    }
  }

  private drawPerformanceChart(
    ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    const bars: BarItem[] = ['impressions', 'reach', 'clicks'].map((m) => ({
      label: m,
      value: Number.parseInt(this.modalMetrics[m]?.replaceAll(',', '') || '0'),
    }));
    drawVerticalBarChart(ctx, canvas, bars, '#1ca698', 1.5, 0.5);
  }

  private drawDemographicsChart(
    _ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    if (!this.modalMetrics.ageGroups) return;
    const colors = ['#1ca698', '#3498db', '#9b59b6', '#f39c12', '#e74c3c', '#2ecc71'];
    const segments = this.modalMetrics.ageGroups.map((g: any, i: number) => ({
      percentage: g.percentage,
      color: colors[i % colors.length],
    }));
    renderPieChart(canvas, segments, Math.min(canvas.width, canvas.height) / 2 - 20);
  }

  private drawDeviceChart(
    _ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    if (!this.modalMetrics.devices) return;
    renderPieChart(canvas, this.modalMetrics.devices, Math.min(canvas.width, canvas.height) / 2 - 20);
  }

  private drawTimelineChart(
    ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    if (!this.modalMetrics.peakHours) return;
    const bars: BarItem[] = this.modalMetrics.peakHours.map((h: any) => ({
      label: h.hour,
      value: h.activity,
    }));
    drawVerticalBarChart(ctx, canvas, bars, '#3498db');
  }

  private drawConversionsChart(
    ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    if (!this.modalMetrics.conversionTypes) return;
    const colors = ['#1ca698', '#f39c12', '#9b59b6', '#e74c3c'];
    const bars: BarItem[] = this.modalMetrics.conversionTypes.map((c: any, i: number) => ({
      label: c.type,
      value: c.count,
      color: colors[i % colors.length],
    }));
    drawVerticalBarChart(ctx, canvas, bars);
  }

  private drawBudgetChart(
    ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    if (!this.modalMetrics.dailySpend) return;

    const data = this.modalMetrics.dailySpend;
    const maxSpend = Math.max(...data.map((d: any) => d.spend));
    const lineWidth = canvas.width / (data.length - 1);
    const maxLineHeight = canvas.height - 40;

    ctx.strokeStyle = '#f39c12';
    ctx.lineWidth = 3;
    ctx.beginPath();

    data.forEach((day: any, index: number) => {
      const x = index * lineWidth;
      const y = canvas.height - 20 - (day.spend / maxSpend) * maxLineHeight;

      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    ctx.stroke();

    // Draw points
    ctx.fillStyle = '#f39c12';
    data.forEach((day: any, index: number) => {
      const x = index * lineWidth;
      const y = canvas.height - 20 - (day.spend / maxSpend) * maxLineHeight;

      ctx.beginPath();
      ctx.arc(x, y, 4, 0, 2 * Math.PI);
      ctx.fill();
    });
  }

  private drawEngagementChart(
    ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ): void {
    if (!this.modalMetrics.topEngagingContent) return;

    const data = this.modalMetrics.topEngagingContent;
    const barHeight = (canvas.height - 60) / data.length;
    const colors = ['#1ca698', '#3498db', '#9b59b6'];

    data.forEach((content: any, index: number) => {
      const percentage = parseFloat(content.engagement.replace('%', ''));
      const barWidth = (percentage / 100) * (canvas.width - 100);
      const y = index * barHeight + 20;

      ctx.fillStyle = colors[index % colors.length];
      ctx.fillRect(50, y, barWidth, barHeight * 0.7);

      // Type label
      ctx.fillStyle = '#20233a';
      ctx.font = '12px Arial';
      ctx.textAlign = 'left';
      ctx.fillText(content.type, 5, y + barHeight * 0.5);

      // Percentage label
      ctx.textAlign = 'right';
      ctx.fillText(content.engagement, canvas.width - 5, y + barHeight * 0.5);
    });
  }

  getModalTitle(): string {
    if (!this.modalConfig || !this.itemData) return '';

    const itemName = this.itemData.name || 'Unknown Item';
    return `${this.modalConfig.title} - ${itemName}`;
  }

  parseFloat(value: string): number {
    return parseFloat(value);
  }
}
