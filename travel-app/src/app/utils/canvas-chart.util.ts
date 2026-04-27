export interface ChartDimensions {
  padding: number;
  chartWidth: number;
  chartHeight: number;
}

export interface PieSegment {
  percentage: number;
  color: string;
}

export function drawChartGrid(
  ctx: CanvasRenderingContext2D,
  dims: ChartDimensions,
  verticalLines: number,
  horizontalLines: number,
): void {
  ctx.fillStyle = '#fafbfc';
  ctx.fillRect(dims.padding, dims.padding, dims.chartWidth, dims.chartHeight);

  ctx.strokeStyle = '#e8ebf2';
  ctx.lineWidth = 1;

  for (let i = 0; i <= verticalLines; i++) {
    const x = dims.padding + (dims.chartWidth / verticalLines) * i;
    ctx.beginPath();
    ctx.moveTo(x, dims.padding);
    ctx.lineTo(x, dims.padding + dims.chartHeight);
    ctx.stroke();
  }

  for (let i = 0; i <= horizontalLines; i++) {
    const y = dims.padding + (dims.chartHeight / horizontalLines) * i;
    ctx.beginPath();
    ctx.moveTo(dims.padding, y);
    ctx.lineTo(dims.padding + dims.chartWidth, y);
    ctx.stroke();
  }
}

export interface BarItem {
  label: string;
  value: number;
  color?: string;
}

export function drawVerticalBarChart(
  ctx: CanvasRenderingContext2D,
  canvas: HTMLCanvasElement,
  bars: BarItem[],
  defaultColor = '#1ca698',
  spacingFactor = 1.2,
  startOffset = 0.1,
): void {
  if (!bars.length) return;
  const maxValue = Math.max(...bars.map((b) => b.value));
  const barWidth = canvas.width / (bars.length * spacingFactor);
  const maxBarHeight = canvas.height - 40;

  bars.forEach((bar, index) => {
    const barHeight = maxValue > 0 ? (bar.value / maxValue) * maxBarHeight : 0;
    const x = (index + startOffset) * barWidth;
    const y = canvas.height - barHeight - 20;

    ctx.fillStyle = bar.color ?? defaultColor;
    ctx.fillRect(x, y, barWidth * 0.8, barHeight);

    ctx.fillStyle = '#20233a';
    ctx.font = '11px Arial';
    ctx.textAlign = 'center';
    ctx.fillText(bar.label, x + barWidth * 0.4, canvas.height - 5);
  });
  ctx.textAlign = 'left';
}

export function renderPieChart(
  canvas: HTMLCanvasElement,
  segments: PieSegment[],
  radius = 80,
): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  const centerX = canvas.width / 2;
  const centerY = canvas.height / 2;
  let currentAngle = 0;

  for (const segment of segments) {
    const sliceAngle = (segment.percentage / 100) * 2 * Math.PI;
    ctx.fillStyle = segment.color;
    ctx.beginPath();
    ctx.moveTo(centerX, centerY);
    ctx.arc(centerX, centerY, radius, currentAngle, currentAngle + sliceAngle);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2;
    ctx.stroke();
    currentAngle += sliceAngle;
  }
}
