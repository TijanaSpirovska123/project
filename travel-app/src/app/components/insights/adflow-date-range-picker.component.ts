import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Inject,
  Input,
  LOCALE_ID,
  OnChanges,
  Output,
  QueryList,
  SimpleChanges,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { DateRange } from '../shared/date-range-picker.component';

export type DatePresetId =
  | 'today'
  | 'yesterday'
  | 'last_7d'
  | 'last_14d'
  | 'last_30d'
  | 'last_90d'
  | 'this_week'
  | 'last_week_mon_sun'
  | 'this_month'
  | 'last_month'
  | 'this_year'
  | 'maximum';

export interface DateRangeSelection {
  preset: DatePresetId | null;
  dateStart: string | null;
  dateStop: string | null;
  compareToPrevious: boolean;
}

interface DatePresetOption {
  id: DatePresetId;
  label: string;
}

const DEFAULT_SELECTION: DateRangeSelection = {
  preset: 'last_30d',
  dateStart: null,
  dateStop: null,
  compareToPrevious: false,
};

@Component({
  selector: 'adflow-date-range-picker',
  standalone: false,
  templateUrl: './adflow-date-range-picker.component.html',
  styleUrls: ['./adflow-date-range-picker.component.scss'],
  providers: [DatePipe],
})
export class AdflowDateRangePickerComponent implements OnChanges {
  @Input() value: DateRangeSelection = DEFAULT_SELECTION;
  @Output() valueChange = new EventEmitter<DateRangeSelection>();

  @ViewChild('triggerButton') triggerButton?: ElementRef<HTMLButtonElement>;
  @ViewChildren('presetButton') presetButtons?: QueryList<ElementRef<HTMLButtonElement>>;

  readonly presetOptions: DatePresetOption[] = [
    { id: 'today', label: 'Today' },
    { id: 'yesterday', label: 'Yesterday' },
    { id: 'last_7d', label: 'Last 7 days' },
    { id: 'last_14d', label: 'Last 14 days' },
    { id: 'last_30d', label: 'Last 30 days' },
    { id: 'last_90d', label: 'Last 90 days' },
    { id: 'this_week', label: 'This week' },
    { id: 'last_week_mon_sun', label: 'Last week' },
    { id: 'this_month', label: 'This month' },
    { id: 'last_month', label: 'Last month' },
    { id: 'this_year', label: 'This year' },
    { id: 'maximum', label: 'Maximum' },
  ];

  isOpen = false;
  customExpanded = false;
  focusedPresetIndex = 0;
  liveMessage = '';

  draftCompareToPrevious = false;
  draftCustomStart: Date | null = null;
  draftCustomStop: Date | null = null;
  draftCalendarRange: DateRange = { from: null, to: null };

  readonly today = this.startOfDay(new Date());
  private appliedValue: DateRangeSelection = DEFAULT_SELECTION;

  constructor(
    private readonly elementRef: ElementRef<HTMLElement>,
    private readonly datePipe: DatePipe,
    @Inject(LOCALE_ID) private readonly locale: string,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value']) {
      this.appliedValue = this.normalizeSelection(this.value);
      if (!this.isOpen) {
        this.resetDraftFromApplied();
      }
    }
  }

  get triggerLabel(): string {
    if (this.appliedValue.preset) {
      return this.presetOptions.find((option) => option.id === this.appliedValue.preset)?.label ?? 'Last 30 days';
    }

    if (!this.appliedValue.dateStart || !this.appliedValue.dateStop) {
      return 'Custom range';
    }

    const start = this.parseIsoDate(this.appliedValue.dateStart);
    const stop = this.parseIsoDate(this.appliedValue.dateStop);
    if (!start || !stop) {
      return 'Custom range';
    }

    const startFormat = start.getFullYear() === stop.getFullYear() ? 'MMM d' : 'MMM d, y';
    const endFormat = 'MMM d, y';
    return `${this.datePipe.transform(start, startFormat, undefined, this.locale)} - ${this.datePipe.transform(stop, endFormat, undefined, this.locale)}`;
  }

  get isCustomSelection(): boolean {
    return this.appliedValue.preset === null;
  }

  get applyDisabled(): boolean {
    if (!this.draftCustomStart || !this.draftCustomStop) {
      return true;
    }
    return this.startOfDay(this.draftCustomStart).getTime() > this.startOfDay(this.draftCustomStop).getTime();
  }

  get customRangeActive(): boolean {
    return this.customExpanded;
  }

  togglePopover(): void {
    if (this.isOpen) {
      this.closePopover('outside');
      return;
    }

    this.openPopover();
  }

  onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
      event.preventDefault();
      if (!this.isOpen) {
        this.openPopover();
      } else {
        this.focusPresetButton(this.focusedPresetIndex);
      }
    }
  }

  onPopoverKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closePopover('escape');
      return;
    }

    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }

    const activeElement = document.activeElement;
    if (!(activeElement instanceof HTMLElement) || !activeElement.classList.contains('preset-row')) {
      return;
    }

    event.preventDefault();
    const direction = event.key === 'ArrowDown' ? 1 : -1;
    const nextIndex = (this.focusedPresetIndex + direction + this.presetOptions.length) % this.presetOptions.length;
    this.focusPresetButton(nextIndex);
  }

  selectPreset(option: DatePresetOption): void {
    const nextSelection: DateRangeSelection = {
      preset: option.id,
      dateStart: null,
      dateStop: null,
      compareToPrevious: this.draftCompareToPrevious,
    };
    this.commitSelection(nextSelection);
    this.closePopover('programmatic');
  }

  toggleCustomExpanded(): void {
    this.customExpanded = !this.customExpanded;
  }

  onCustomCalendarChange(range: DateRange): void {
    this.draftCustomStart = range.from ?? null;
    this.draftCustomStop = range.to ?? null;
    this.draftCalendarRange = range;
    if (range.from && range.to) {
      this.applyCustomRange();
    }
  }

  applyCustomRange(): void {
    if (this.applyDisabled || !this.draftCustomStart || !this.draftCustomStop) {
      return;
    }

    const nextSelection: DateRangeSelection = {
      preset: null,
      dateStart: this.toIsoDate(this.draftCustomStart),
      dateStop: this.toIsoDate(this.draftCustomStop),
      compareToPrevious: this.draftCompareToPrevious,
    };
    this.commitSelection(nextSelection);
    this.closePopover('programmatic');
  }

  onCompareToggleChange(checked: boolean): void {
    this.draftCompareToPrevious = checked;
  }

  isPresetActive(option: DatePresetOption): boolean {
    return this.appliedValue.preset === option.id;
  }

  trackPreset(_: number, option: DatePresetOption): DatePresetId {
    return option.id;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen) {
      return;
    }

    const target = event.target;
    if (target instanceof Node && !this.elementRef.nativeElement.contains(target)) {
      this.closePopover('outside');
    }
  }

  private openPopover(): void {
    this.appliedValue = this.normalizeSelection(this.value);
    this.resetDraftFromApplied();
    this.isOpen = true;
    setTimeout(() => this.focusPresetButton(this.initialPresetIndex()), 0);
  }

  private closePopover(reason: 'escape' | 'outside' | 'programmatic'): void {
    if (!this.isOpen) {
      return;
    }

    if (reason === 'outside') {
      this.commitCompareOnlyIfNeeded();
    } else if (reason === 'escape') {
      this.resetDraftFromApplied();
    }

    this.isOpen = false;
    this.customExpanded = this.appliedValue.preset === null;
    setTimeout(() => this.triggerButton?.nativeElement.focus(), 0);
  }

  private commitCompareOnlyIfNeeded(): void {
    if (this.appliedValue.compareToPrevious === this.draftCompareToPrevious) {
      return;
    }

    const nextSelection: DateRangeSelection = {
      ...this.appliedValue,
      compareToPrevious: this.draftCompareToPrevious,
    };
    this.commitSelection(nextSelection);
  }

  private commitSelection(selection: DateRangeSelection): void {
    const next = this.normalizeSelection(selection);
    if (this.isSameSelection(this.appliedValue, next)) {
      this.appliedValue = next;
      this.resetDraftFromApplied();
      return;
    }

    this.appliedValue = next;
    this.resetDraftFromApplied();
    this.liveMessage = `Date range set to ${this.triggerLabel}`;
    this.valueChange.emit(next);
  }

  private resetDraftFromApplied(): void {
    this.draftCompareToPrevious = this.appliedValue.compareToPrevious;
    this.draftCustomStart = this.parseIsoDate(this.appliedValue.dateStart);
    this.draftCustomStop = this.parseIsoDate(this.appliedValue.dateStop);
    this.draftCalendarRange = { from: this.draftCustomStart, to: this.draftCustomStop };
    this.customExpanded = this.appliedValue.preset === null;
    this.focusedPresetIndex = this.initialPresetIndex();
  }

  private initialPresetIndex(): number {
    const preset = this.appliedValue.preset ?? DEFAULT_SELECTION.preset;
    return Math.max(this.presetOptions.findIndex((option) => option.id === preset), 0);
  }

  private focusPresetButton(index: number): void {
    this.focusedPresetIndex = index;
    const button = this.presetButtons?.toArray()[index]?.nativeElement;
    button?.focus();
  }

  private normalizeSelection(selection: DateRangeSelection | null | undefined): DateRangeSelection {
    const next = selection ?? DEFAULT_SELECTION;
    return {
      preset: next.preset ?? null,
      dateStart: next.preset ? null : next.dateStart ?? null,
      dateStop: next.preset ? null : next.dateStop ?? null,
      compareToPrevious: next.compareToPrevious ?? false,
    };
  }

  private isSameSelection(a: DateRangeSelection, b: DateRangeSelection): boolean {
    return a.preset === b.preset
      && a.dateStart === b.dateStart
      && a.dateStop === b.dateStop
      && a.compareToPrevious === b.compareToPrevious;
  }

  private parseIsoDate(value: string | null): Date | null {
    if (!value) {
      return null;
    }

    const parts = value.split('-').map((part) => Number(part));
    if (parts.length !== 3 || parts.some((part) => Number.isNaN(part))) {
      return null;
    }

    return new Date(parts[0], parts[1] - 1, parts[2]);
  }

  private toIsoDate(value: Date): string {
    const normalized = this.startOfDay(value);
    const year = normalized.getFullYear();
    const month = `${normalized.getMonth() + 1}`.padStart(2, '0');
    const day = `${normalized.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private startOfDay(value: Date): Date {
    const next = new Date(value);
    next.setHours(0, 0, 0, 0);
    return next;
  }
}

