import { Component, Input, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'app-error-state',
  standalone: true,
  template: `
    <div class="ers-wrap">
      <i class="fa fa-exclamation-circle ers-icon"></i>
      <p class="ers-msg">{{ message }}</p>
      <button class="ers-retry" (click)="retry.emit()">
        <i class="fa fa-refresh"></i> Retry
      </button>
    </div>
  `,
  styles: [`
    .ers-wrap {
      display: flex; flex-direction: column; align-items: center;
      gap: 10px; padding: 24px; text-align: center;
    }
    .ers-icon { font-size: 32px; color: #ef4444; }
    .ers-msg { margin: 0; font-size: 14px; color: #6b7280; max-width: 260px; }
    .ers-retry {
      display: flex; align-items: center; gap: 6px;
      padding: 6px 16px; border: 1px solid #10B981; border-radius: 8px;
      background: transparent; color: #10B981; font-size: 13px; cursor: pointer;
    }
    .ers-retry:hover { background: #ECFDF5; }
  `]
})
export class AppErrorStateComponent {
  @Input() message = 'Something went wrong. Please try again.';
  @Output() retry = new EventEmitter<void>();
}
