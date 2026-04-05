import {
  Component, Input, Output, EventEmitter, OnChanges,
  SimpleChanges, forwardRef, ChangeDetectorRef, ElementRef, HostListener,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

export interface DropdownOption {
  value: string | number;
  label: string;
}

@Component({
  selector: 'app-searchable-dropdown',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SearchableDropdownComponent),
    multi: true,
  }],
  template: `
    <div class="sdd-wrap" [class.sdd-open]="isOpen" [class.sdd-disabled]="disabled">
      <button type="button" class="sdd-trigger" (click)="toggle()" [disabled]="disabled">
        <span class="sdd-value">{{ selectedLabel || placeholder }}</span>
        @if (loading) {
          <span class="sdd-loading-dot"></span>
        } @else {
          <i class="fa fa-chevron-down sdd-arrow" [class.rotated]="isOpen"></i>
        }
      </button>

      @if (isOpen) {
        <div class="sdd-panel">
          <div class="sdd-search-wrap">
            <i class="fa fa-search sdd-search-icon"></i>
            <input
              class="sdd-search"
              type="text"
              placeholder="Search…"
              [(ngModel)]="query"
              (ngModelChange)="onQueryChange()"
              (click)="$event.stopPropagation()"
              #searchInput
            />
          </div>
          <ul class="sdd-list">
            @if (filteredOptions.length === 0) {
              <li class="sdd-no-results">No results found</li>
            }
            @for (opt of filteredOptions; track opt.value) {
              <li
                class="sdd-item"
                [class.sdd-selected]="opt.value === value"
                (click)="select(opt)"
              >{{ opt.label }}</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styles: [`
    .sdd-wrap { position: relative; width: 100%; }
    .sdd-trigger {
      width: 100%; display: flex; align-items: center; justify-content: space-between;
      padding: 8px 12px; border: 1px solid var(--tbl-border); border-radius: 8px;
      background: var(--input-bg); font-size: 14px; color: var(--body-text); cursor: pointer;
      transition: border-color 0.15s;
    }
    .sdd-trigger:hover:not(:disabled) { border-color: #10B981; }
    .sdd-open .sdd-trigger { border-color: #10B981; border-bottom-left-radius: 0; border-bottom-right-radius: 0; }
    .sdd-disabled .sdd-trigger { background: var(--tbl-bg-alt); color: var(--tbl-muted); cursor: not-allowed; }
    .sdd-value { flex: 1; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .sdd-arrow { font-size: 11px; color: var(--tbl-muted); transition: transform 0.2s; }
    .sdd-arrow.rotated { transform: rotate(180deg); }
    .sdd-loading-dot {
      width: 14px; height: 14px; border: 2px solid #D1FAE5; border-top-color: #10B981;
      border-radius: 50%; animation: sdd-spin 0.7s linear infinite;
    }
    @keyframes sdd-spin { to { transform: rotate(360deg); } }
    .sdd-panel {
      position: absolute; top: 100%; left: 0; right: 0; z-index: 200;
      background: var(--card-bg); border: 1px solid #10B981; border-top: none;
      border-radius: 0 0 8px 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.10);
    }
    .sdd-search-wrap {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 12px; border-bottom: 1px solid var(--tbl-border);
    }
    .sdd-search-icon { font-size: 12px; color: var(--tbl-muted); }
    .sdd-search {
      flex: 1; border: none; outline: none; font-size: 13px; color: var(--body-text); background: transparent;
    }
    .sdd-list { list-style: none; margin: 0; padding: 4px 0; max-height: 200px; overflow-y: auto; }
    .sdd-item {
      padding: 8px 12px; font-size: 14px; color: var(--body-text); cursor: pointer;
      transition: background 0.1s;
    }
    .sdd-item:hover { background: var(--tbl-bg-alt); }
    .sdd-selected { background: #ECFDF5 !important; color: #059669; font-weight: 500; }
    .sdd-no-results { padding: 8px 12px; font-size: 13px; color: var(--tbl-muted); text-align: center; }
  `]
})
export class SearchableDropdownComponent implements ControlValueAccessor, OnChanges {
  @Input() options: DropdownOption[] = [];
  @Input() placeholder = 'Select…';
  @Input() loading = false;
  @Input() disabled = false;
  @Output() selectionChange = new EventEmitter<DropdownOption | null>();

  value: string | number | null = null;
  query = '';
  isOpen = false;
  filteredOptions: DropdownOption[] = [];

  private onChange: (v: any) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private cdr: ChangeDetectorRef, private el: ElementRef) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(e: MouseEvent): void {
    if (!this.el.nativeElement.contains(e.target)) {
      this.isOpen = false;
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['options']) {
      this.onQueryChange();
    }
  }

  get selectedLabel(): string {
    const found = this.options.find(o => o.value === this.value);
    return found?.label ?? '';
  }

  toggle(): void {
    if (this.disabled) return;
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.query = '';
      this.filteredOptions = [...this.options];
    }
  }

  onQueryChange(): void {
    const q = this.query.toLowerCase();
    this.filteredOptions = this.options.filter(o => o.label.toLowerCase().includes(q));
  }

  select(opt: DropdownOption): void {
    this.value = opt.value;
    this.onChange(opt.value);
    this.onTouched();
    this.selectionChange.emit(opt);
    this.isOpen = false;
    this.cdr.markForCheck();
  }

  writeValue(val: any): void {
    this.value = val ?? null;
    this.cdr.markForCheck();
  }
  registerOnChange(fn: any): void { this.onChange = fn; }
  registerOnTouched(fn: any): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }
}
