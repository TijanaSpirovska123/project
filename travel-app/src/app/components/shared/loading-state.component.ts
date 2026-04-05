import { Component, Input, Output, EventEmitter, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (isLoading) {
      @if (loadingTemplate) {
        <ng-container *ngTemplateOutlet="loadingTemplate"></ng-container>
      } @else {
        <div class="ls-center">
          <div class="ls-spinner"></div>
        </div>
      }
    } @else if (error) {
      <div class="ls-error">
        <i class="fa fa-exclamation-circle ls-error-icon"></i>
        <p class="ls-error-msg">{{ error }}</p>
        <button class="ls-retry-btn" (click)="retry.emit()">{{ retryLabel }}</button>
      </div>
    } @else {
      <ng-content></ng-content>
    }
  `,
  styles: [`
    .ls-center {
      display: flex; justify-content: center; align-items: center; padding: 40px;
    }
    .ls-spinner {
      width: 32px; height: 32px;
      border: 3px solid #D1FAE5; border-top-color: #10B981;
      border-radius: 50%; animation: ls-spin 0.7s linear infinite;
    }
    @keyframes ls-spin { to { transform: rotate(360deg); } }
    .ls-error {
      display: flex; flex-direction: column; align-items: center;
      gap: 12px; padding: 32px; text-align: center;
    }
    .ls-error-icon { font-size: 32px; color: #ef4444; }
    .ls-error-msg { margin: 0; font-size: 14px; color: #6b7280; max-width: 280px; }
    .ls-retry-btn {
      padding: 8px 20px; background: transparent;
      border: 1px solid #10B981; border-radius: 8px;
      color: #10B981; font-size: 14px; font-weight: 500; cursor: pointer;
    }
    .ls-retry-btn:hover { background: #ECFDF5; }
  `]
})
export class AppLoadingStateComponent {
  @Input() isLoading = false;
  @Input() error: string | null = null;
  @Input() retryLabel = 'Retry';
  @Input() loadingTemplate: TemplateRef<any> | null = null;
  @Output() retry = new EventEmitter<void>();
}
