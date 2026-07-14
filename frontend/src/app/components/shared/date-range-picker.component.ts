import {
  Component, Input, Output, EventEmitter, HostListener, forwardRef,
  ChangeDetectionStrategy, ElementRef, signal, computed,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CommonModule } from '@angular/common';

export interface DateRange {
  from: Date | null;
  to: Date | null;
}

type QuickKey = '7d' | '14d' | '30d' | '90d';

const QUICK_OPTIONS: { label: string; key: QuickKey; days: number }[] = [
  { label: 'Last 7 days',  key: '7d',  days: 7  },
  { label: 'Last 14 days', key: '14d', days: 14 },
  { label: 'Last 30 days', key: '30d', days: 30 },
  { label: 'Last 90 days', key: '90d', days: 90 },
];

const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
const MONTHS_SHORT = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];

@Component({
  selector: 'app-date-range-picker',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => DateRangePickerComponent),
    multi: true,
  }],
  template: `
    <div class="drp-wrap">

      <!-- Quick selectors -->
      <div class="drp-quick" role="group" aria-label="Quick date ranges">
        @for (opt of quickOptions; track opt.key) {
          <button type="button" class="drp-pill"
                  [class.drp-pill-active]="activeQuick() === opt.key"
                  (click)="setQuickSelect(opt)">
            {{ opt.label }}
          </button>
        }
      </div>

      @if (!inline) {
        <!-- FROM → TO trigger row -->
        <div class="drp-inputs">
          <div class="drp-field">
            <label class="drp-label">FROM</label>
            <div class="drp-input-wrap"
                 [class.drp-input-active]="isOpen() && activeInput() === 'from'"
                 (click)="openPanel('from')"
                 role="combobox"
                 [attr.aria-expanded]="isOpen() && activeInput() === 'from'"
                 aria-haspopup="dialog"
                 tabindex="0"
                 (keydown.enter)="openPanel('from')"
                 (keydown.space)="$event.preventDefault(); openPanel('from')">
              <span class="drp-input-text" [class.drp-placeholder]="!startDate()">
                {{ startDate() ? formatDate(startDate()) : 'DD/MM/YYYY' }}
              </span>
              <i class="fa fa-calendar drp-cal-icon" aria-hidden="true"></i>
            </div>
          </div>

          <span class="drp-arrow" aria-hidden="true">→</span>

          <div class="drp-field">
            <label class="drp-label">TO</label>
            <div class="drp-input-wrap"
                 [class.drp-input-active]="isOpen() && activeInput() === 'to'"
                 (click)="openPanel('to')"
                 role="combobox"
                 [attr.aria-expanded]="isOpen() && activeInput() === 'to'"
                 aria-haspopup="dialog"
                 tabindex="0"
                 (keydown.enter)="openPanel('to')"
                 (keydown.space)="$event.preventDefault(); openPanel('to')">
              <span class="drp-input-text" [class.drp-placeholder]="!endDate()">
                {{ endDate() ? formatDate(endDate()) : 'DD/MM/YYYY' }}
              </span>
              <i class="fa fa-calendar drp-cal-icon" aria-hidden="true"></i>
            </div>
          </div>
        </div>

        <!-- Validation error -->
        @if (rangeError()) {
          <div class="drp-error" role="alert">{{ rangeError() }}</div>
        }

        <!-- Calendar panel (floating) -->
        @if (isOpen()) {
          <div class="drp-panel" role="dialog" aria-label="Date picker calendar" aria-modal="true">
            <ng-container *ngTemplateOutlet="calendarTpl"></ng-container>
          </div>
        }
      }

      @if (inline) {
        <!-- Inline calendar -->
        <div class="drp-inline" role="dialog" aria-label="Date picker calendar">
          <ng-container *ngTemplateOutlet="calendarTpl"></ng-container>
        </div>
      }

    </div>

    <ng-template #calendarTpl>
      <!-- Header -->
      <div class="drp-header">
        <span class="drp-month-badge">
          {{ headerLabel() }}
          <i class="fa fa-caret-down drp-badge-caret" aria-hidden="true"></i>
        </span>
        <div class="drp-chevrons">
          <button type="button" class="drp-chevron" (click)="prevMonth()" aria-label="Previous month">
            <i class="fa fa-chevron-left" aria-hidden="true"></i>
          </button>
          <button type="button" class="drp-chevron" (click)="nextMonth()" aria-label="Next month">
            <i class="fa fa-chevron-right" aria-hidden="true"></i>
          </button>
        </div>
      </div>

      <!-- Weekday headers -->
      <div class="drp-weekdays" role="row">
        @for (wd of weekdays; track wd) {
          <span class="drp-wd" role="columnheader" [attr.aria-label]="wd">{{ wd }}</span>
        }
      </div>

      <!-- Day grid -->
      <div class="drp-grid" role="grid">
        @for (day of daysGrid(); track day.date.getTime()) {
          <div class="drp-day-cell"
               [class.drp-day-start-half]="isStartDate(day.date) && !!endDate() && !isSameDay(day.date, endDate())"
               [class.drp-day-end-half]="isEndDate(day.date) && !!startDate() && !isSameDay(day.date, startDate())"
               [class.drp-day-in-range]="isInRange(day.date)">
            <div class="drp-day-inner"
                 [class.drp-day-outside]="day.outside"
                 [class.drp-day-selected]="!day.outside && (isStartDate(day.date) || isEndDate(day.date))"
                 [class.drp-day-today]="isToday(day.date) && !isStartDate(day.date) && !isEndDate(day.date)"
                 [class.drp-day-disabled]="isDayDisabled(day.date)"
                 role="gridcell"
                 [attr.aria-selected]="!day.outside && (isStartDate(day.date) || isEndDate(day.date))"
                 [attr.aria-disabled]="isDayDisabled(day.date)"
                 (click)="selectDay(day)">
              {{ day.date.getDate() }}
            </div>
          </div>
        }
      </div>

      <!-- Footer -->
      <div class="drp-footer">
        <button type="button" class="drp-foot-btn" (click)="clear()">Clear</button>
        <button type="button" class="drp-foot-btn" (click)="setToday()">Today</button>
      </div>
    </ng-template>
  `,
  styles: [`
    .drp-wrap {
      position: relative;
      display: inline-flex;
      flex-direction: column;
      gap: 10px;
      font-family: Inter, system-ui, sans-serif;
      font-size: 14px;
    }

    /* ── Quick pills ── */
    .drp-quick { display: flex; gap: 8px; flex-wrap: wrap; }

    .drp-pill {
      border: 1px solid #E5E7EB;
      border-radius: 20px;
      padding: 4px 12px;
      font-size: 13px;
      color: #374151;
      background: white;
      cursor: pointer;
      transition: background 150ms, color 150ms, border-color 150ms;
      font-family: inherit;
    }
    .drp-pill:hover { border-color: #1ca698; color: #1ca698; }
    .drp-pill-active { background: #1ca698 !important; color: white !important; border-color: #1ca698 !important; }

    /* ── Trigger row ── */
    .drp-inputs { display: flex; align-items: flex-end; gap: 8px; }
    .drp-arrow { font-size: 16px; color: #9CA3AF; line-height: 40px; }

    .drp-field { display: flex; flex-direction: column; gap: 4px; }

    .drp-label {
      font-size: 12px;
      text-transform: uppercase;
      color: #6B7280;
      letter-spacing: 0.05em;
      font-weight: 500;
    }

    .drp-input-wrap {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      background: white;
      border: 1px solid #E5E7EB;
      border-radius: 8px;
      height: 40px;
      padding: 0 12px;
      cursor: pointer;
      width: 148px;
      box-sizing: border-box;
      transition: border-color 150ms;
      outline: none;
    }
    .drp-input-wrap:hover { border-color: #9CA3AF; }
    .drp-input-wrap:focus-visible { border: 2px solid #1ca698; }
    .drp-input-active { border: 2px solid #1ca698 !important; }

    .drp-input-text { font-size: 14px; color: #111827; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .drp-placeholder { color: #9CA3AF; }
    .drp-cal-icon { color: #9CA3AF; font-size: 13px; flex-shrink: 0; }

    /* ── Error ── */
    .drp-error { font-size: 12px; color: #EF4444; }

    /* ── Calendar panel (floating) ── */
    .drp-panel {
      position: absolute;
      top: calc(100% + 4px);
      left: 0;
      width: 290px;
      background: #F5F5F5;
      border: none;
      border-radius: 12px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.10);
      z-index: 1000;
      padding: 14px;
      box-sizing: border-box;
    }

    /* ── Inline calendar ── */
    .drp-inline {
      width: 100%;
      background: transparent;
      padding: 4px 0 0;
      box-sizing: border-box;
    }

    /* ── Panel header ── */
    .drp-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    .drp-month-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: white;
      border: 1px solid #E5E7EB;
      border-radius: 6px;
      padding: 4px 10px;
      font-size: 13px;
      font-weight: 600;
      color: #111827;
      letter-spacing: 0.02em;
    }
    .drp-badge-caret { color: #6B7280; font-size: 11px; }

    .drp-chevrons { display: flex; gap: 2px; }

    .drp-chevron {
      width: 28px;
      height: 28px;
      border: 1px solid #E5E7EB;
      border-radius: 6px;
      background: white;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #6B7280;
      font-size: 11px;
      transition: background 150ms, border-color 150ms;
      padding: 0;
    }
    .drp-chevron:hover { background: #F3F4F6; border-color: #D1D5DB; }

    /* ── Weekday row ── */
    .drp-weekdays {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      margin-bottom: 2px;
    }
    .drp-wd {
      text-align: center;
      font-size: 12px;
      color: #374151;
      font-weight: 700;
      padding: 4px 0;
    }

    /* ── Day grid ── */
    .drp-grid { display: grid; grid-template-columns: repeat(7, 1fr); }

    .drp-day-cell {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      height: 40px;
    }

    /* Range fill: full-width band for in-range days */
    .drp-day-in-range::before {
      content: '';
      position: absolute;
      left: 0; right: 0;
      top: 50%; transform: translateY(-50%);
      height: 34px;
      background: #ECFDF5;
      z-index: 0;
    }

    /* Start date: right-half gets range color (connects to the range) */
    .drp-day-start-half::after {
      content: '';
      position: absolute;
      right: 0;
      top: 50%; transform: translateY(-50%);
      width: 50%; height: 34px;
      background: #ECFDF5;
      z-index: 0;
    }

    /* End date: left-half gets range color (connects to the range) */
    .drp-day-end-half::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%; transform: translateY(-50%);
      width: 50%; height: 34px;
      background: #ECFDF5;
      z-index: 0;
    }

    /* Day inner circle — sits above pseudo-elements */
    .drp-day-inner {
      position: relative;
      z-index: 1;
      width: 34px;
      height: 34px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      cursor: pointer;
      font-size: 14px;
      color: #374151;
      transition: background 100ms;
      user-select: none;
    }

    .drp-day-inner:not(.drp-day-outside):not(.drp-day-disabled):not(.drp-day-selected):hover {
      background: #E5E7EB;
    }

    .drp-day-selected {
      background: #1ca698 !important;
      color: #fff !important;
      border: none !important;
      font-weight: 600;
      border-radius: 50%;
    }

    .drp-day-today {
      border: 2px solid #1ca698;
      border-radius: 50%;
    }

    .drp-day-outside { color: #D1D5DB; cursor: pointer; }
    .drp-day-outside:not(.drp-day-disabled):hover { background: #E5E7EB; }

    .drp-day-disabled { color: #E5E7EB !important; cursor: not-allowed !important; pointer-events: none; }

    /* In-range text color (not applied to selected endpoints) */
    .drp-day-in-range .drp-day-inner:not(.drp-day-selected) { color: #065F46; }

    /* ── Footer ── */
    .drp-footer {
      display: none !important;
    }
    .drp-foot-btn {
      font-size: 13px;
      font-weight: 500;
      color: #1ca698;
      background: transparent;
      border: none;
      cursor: pointer;
      padding: 0;
      font-family: inherit;
    }
    .drp-foot-btn:hover { text-decoration: underline; }
  `],
})
export class DateRangePickerComponent implements ControlValueAccessor {
  @Input() maxRangeDays = 365;
  @Input() inline = false;
  @Output() rangeChange = new EventEmitter<DateRange>();
  @Output() quickSelectChange = new EventEmitter<QuickKey>();

  readonly weekdays = WEEKDAYS;
  readonly quickOptions = QUICK_OPTIONS;

  // ── State signals ──
  startDate  = signal<Date | null>(null);
  endDate    = signal<Date | null>(null);
  viewYear   = signal(new Date().getFullYear());
  viewMonth  = signal(new Date().getMonth());
  isOpen     = signal(false);
  activeInput = signal<'from' | 'to'>('from');
  activeQuick = signal<QuickKey | null>(null);
  rangeError  = signal<string | null>(null);

  // ── Derived ──
  headerLabel = computed(() => `${MONTHS_SHORT[this.viewMonth()]} ${this.viewYear()}`);
  daysGrid    = computed(() => this.buildGrid(this.viewYear(), this.viewMonth()));

  private onChange: (v: DateRange) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private el: ElementRef) {}

  // ── ControlValueAccessor ──
  writeValue(val: DateRange): void {
    if (val) {
      this.startDate.set(val.from ?? null);
      this.endDate.set(val.to ?? null);
    }
  }
  registerOnChange(fn: (v: DateRange) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(_: boolean): void { /* no disabled state on the picker */ }

  // ── Host listeners ──
  @HostListener('document:click', ['$event'])
  onDocClick(e: MouseEvent): void {
    if (this.isOpen() && !this.el.nativeElement.contains(e.target)) {
      this.closePanel();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.closePanel(); }

  // ── Panel ──
  openPanel(input: 'from' | 'to'): void {
    this.activeInput.set(input);
    const ref = input === 'from' ? this.startDate() : this.endDate();
    const nav = ref ?? new Date();
    this.viewYear.set(nav.getFullYear());
    this.viewMonth.set(nav.getMonth());
    this.isOpen.set(true);
    this.onTouched();
  }

  closePanel(): void { this.isOpen.set(false); }

  prevMonth(): void {
    let m = this.viewMonth() - 1, y = this.viewYear();
    if (m < 0) { m = 11; y--; }
    this.viewMonth.set(m);
    this.viewYear.set(y);
  }

  nextMonth(): void {
    let m = this.viewMonth() + 1, y = this.viewYear();
    if (m > 11) { m = 0; y++; }
    this.viewMonth.set(m);
    this.viewYear.set(y);
  }

  // ── Day selection ──
  selectDay(day: { date: Date; outside: boolean }): void {
    if (this.isDayDisabled(day.date)) return;
    if (day.outside) {
      this.viewYear.set(day.date.getFullYear());
      this.viewMonth.set(day.date.getMonth());
    }
    const date = new Date(day.date);

    if (this.activeInput() === 'from') {
      this.startDate.set(date);
      this.endDate.set(null);
      this.activeQuick.set(null);
      this.rangeError.set(null);
      this.activeInput.set('to');          // stay open for end selection
    } else {
      if (this.startDate() && date < this.startDate()!) {
        // clicked before start — restart from this date
        this.startDate.set(date);
        this.endDate.set(null);
        this.activeQuick.set(null);
        return;
      }
      this.endDate.set(date);
      this.activeQuick.set(null);
      this.validateRange();
      if (!this.rangeError()) {
        this.emitChange();
        if (this.inline) {
          this.activeInput.set('from');
        } else {
          this.closePanel();
        }
      }
    }
  }

  // ── Day state helpers ──
  isDayDisabled(date: Date): boolean {
    if (this.activeInput() === 'to' && this.startDate()) {
      const s = midnight(this.startDate()!);
      return midnight(date) < s;
    }
    return false;
  }

  isStartDate(date: Date): boolean { return this.isSameDay(date, this.startDate()); }
  isEndDate(date: Date): boolean   { return this.isSameDay(date, this.endDate()); }

  isInRange(date: Date): boolean {
    const s = this.startDate(), e = this.endDate();
    if (!s || !e) return false;
    const t = midnight(date).getTime();
    return t > midnight(s).getTime() && t < midnight(e).getTime();
  }

  isToday(date: Date): boolean { return this.isSameDay(date, new Date()); }

  isSameDay(a: Date, b: Date | null): boolean {
    if (!b) return false;
    return a.getFullYear() === b.getFullYear()
        && a.getMonth()    === b.getMonth()
        && a.getDate()     === b.getDate();
  }

  formatDate(date: Date | null): string {
    if (!date) return '';
    const d = String(date.getDate()).padStart(2, '0');
    const m = String(date.getMonth() + 1).padStart(2, '0');
    return `${d}/${m}/${date.getFullYear()}`;
  }

  // ── Quick select ──
  setQuickSelect(opt: { key: QuickKey; days: number }): void {
    const to   = midnight(new Date());
    const from = new Date(to);
    from.setDate(from.getDate() - (opt.days - 1));
    this.startDate.set(from);
    this.endDate.set(to);
    this.activeQuick.set(opt.key);
    this.rangeError.set(null);
    this.emitChange();
    this.quickSelectChange.emit(opt.key);
  }

  // ── Footer actions ──
  clear(): void {
    this.startDate.set(null);
    this.endDate.set(null);
    this.activeQuick.set(null);
    this.rangeError.set(null);
    this.emitChange();
    this.closePanel();
  }

  setToday(): void {
    const today = midnight(new Date());
    this.startDate.set(today);
    this.endDate.set(today);
    this.activeQuick.set(null);
    this.rangeError.set(null);
    this.emitChange();
    this.closePanel();
  }

  // ── Private helpers ──
  private validateRange(): void {
    const s = this.startDate(), e = this.endDate();
    if (s && e) {
      const diff = Math.round((e.getTime() - s.getTime()) / 86_400_000);
      this.rangeError.set(diff > this.maxRangeDays
        ? `Maximum range is ${this.maxRangeDays} days`
        : null);
    }
  }

  private emitChange(): void {
    const r: DateRange = { from: this.startDate(), to: this.endDate() };
    this.onChange(r);
    this.rangeChange.emit(r);
  }

  private buildGrid(year: number, month: number): { date: Date; outside: boolean }[] {
    const firstDow = new Date(year, month, 1).getDay(); // 0 = Sunday
    const lastDate = new Date(year, month + 1, 0).getDate();
    const days: { date: Date; outside: boolean }[] = [];

    // Tail of previous month
    for (let i = firstDow - 1; i >= 0; i--) {
      days.push({ date: new Date(year, month, -i), outside: true });
    }
    // Current month
    for (let d = 1; d <= lastDate; d++) {
      days.push({ date: new Date(year, month, d), outside: false });
    }
    // Head of next month — fill to 42 cells (6 rows × 7)
    let next = 1;
    while (days.length < 42) {
      days.push({ date: new Date(year, month + 1, next++), outside: true });
    }
    return days;
  }
}

function midnight(d: Date): Date {
  const c = new Date(d);
  c.setHours(0, 0, 0, 0);
  return c;
}
