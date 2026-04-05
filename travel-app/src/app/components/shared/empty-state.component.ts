import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="es-wrap">
      <i class="fa {{ icon }} es-icon"></i>
      <h4 class="es-title">{{ title }}</h4>
      <p class="es-message" *ngIf="message">{{ message }}</p>
      <button *ngIf="actionLabel" class="es-action" (click)="action.emit()">{{ actionLabel }}</button>
    </div>
  `,
  styles: [`
    .es-wrap {
      display: flex; flex-direction: column; align-items: center;
      justify-content: center; padding: 40px 20px; gap: 12px; text-align: center;
    }
    .es-icon { font-size: 48px; color: #10B981; }
    .es-title { margin: 0; font-size: 16px; font-weight: 600; color: #111827; }
    .es-message { margin: 0; font-size: 14px; color: #6B7280; max-width: 300px; }
    .es-action {
      padding: 8px 20px; background: #10B981; color: white;
      border: none; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer;
      margin-top: 4px;
    }
    .es-action:hover { background: #059669; }
  `]
})
export class AppEmptyStateComponent {
  @Input() icon = 'fa-inbox';
  @Input() title = 'No data found';
  @Input() message = '';
  @Input() actionLabel: string | null = null;
  @Output() action = new EventEmitter<void>();
}
