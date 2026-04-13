import { Injectable } from '@angular/core';
import { ToastrService } from 'ngx-toastr';

@Injectable({ providedIn: 'root' })
export class AppToastrService {
  constructor(private toastr: ToastrService) {}

  success(message: string, title?: string): void {
    this.toastr.success(message, title, { timeOut: 4000 });
  }

  error(message: string, title?: string): void {
    this.toastr.error(message, title, { timeOut: 4000 });
  }

  warning(message: string, title?: string): void {
    this.toastr.warning(message, title, { timeOut: 6000 });
  }

  info(message: string, title?: string): void {
    this.toastr.info(message, title, { timeOut: 4000 });
  }

  clear(): void {
    this.toastr.clear();
  }
}
