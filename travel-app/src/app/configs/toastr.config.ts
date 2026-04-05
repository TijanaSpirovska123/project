import { GlobalConfig } from 'ngx-toastr';

export const toastrConfig: Partial<GlobalConfig> = {
  timeOut: 3000,
  positionClass: 'toast-top-center',
  preventDuplicates: true,
  closeButton: true,
  progressBar: true,
};
