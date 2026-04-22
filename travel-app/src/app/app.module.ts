import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ToastrModule } from 'ngx-toastr';
import { toastrConfig } from './configs/toastr.config';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import {
  HTTP_INTERCEPTORS,
  provideHttpClient,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { AuthInterceptor } from './configs/http.token.interceptor';
import { MenuComponent } from './components/menu/menu.component';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule, MatOptionModule } from '@angular/material/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { LoginPageComponent } from './components/login-page/login-page.component';
import { SignUpPageComponent } from './components/sign-up-page/sign-up-page.component';
import { MetaComponent } from './components/meta/meta.component';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTimepickerModule } from '@angular/material/timepicker';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SpinnerComponent } from './components/spinner/spinner.component';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ChipModule } from 'primeng/chip';
import { DatePickerModule } from 'primeng/datepicker';
import { ReusableTableComponent } from './components/table/table.component';
import { AnalyticsComponent } from './components/analytics/analytics.component';
import { AnalyticsModalComponent } from './components/analytics-modal/analytics-modal.component';
import { CreateAdWorkflowComponent } from './components/create-ad-workflow/create-ad-workflow.component';
import { OauthSuccessComponent } from './components/oauth/oauth-token.component';
import { InsightsComponent } from './components/insights/insights.component';
import { CreativeLibraryComponent } from './pages/creative-library/creative-library.component';
import { SyncAccountsComponent } from './pages/sync-accounts/sync-accounts.component';
import {
  LucideAngularModule,
  Eye,
  EyeOff,
  Mail,
  Sparkles,
} from 'lucide-angular';
import { PupilComponent } from './components/ui/pupil.component';
import { EyeballComponent } from './components/ui/eyeball.component';
import { AppLoadingStateComponent } from './components/shared/loading-state.component';
import { AppEmptyStateComponent } from './components/shared/empty-state.component';
import { AppErrorStateComponent } from './components/shared/error-state.component';
import { SearchableDropdownComponent } from './components/shared/searchable-dropdown.component';
import { DateRangePickerComponent } from './components/shared/date-range-picker.component';
import { AdflowDateRangePickerComponent } from './components/insights/adflow-date-range-picker.component';

@NgModule({
  declarations: [
    AppComponent,
    MenuComponent,
    MetaComponent,
    LoginPageComponent,
    SignUpPageComponent,
    SpinnerComponent,
    ReusableTableComponent,
    AnalyticsModalComponent,
    CreateAdWorkflowComponent,
    InsightsComponent,
    AdflowDateRangePickerComponent,
    CreativeLibraryComponent,
    SyncAccountsComponent,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    ToastrModule.forRoot({
      positionClass: 'toast-top-right',
      closeButton: true,
      progressBar: true,
      progressAnimation: 'decreasing',
      timeOut: 4000,
      extendedTimeOut: 2000,
      preventDuplicates: true,
      countDuplicates: false,
      resetTimeoutOnDuplicate: true,
      maxOpened: 5,
      newestOnTop: true,
    }),
    DatePickerModule,
    BreadcrumbModule,
    ChipModule,
    FormsModule,
    ReactiveFormsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSlideToggleModule,
    MatInputModule,
    MatIconModule,
    MatTimepickerModule,
    MatFormFieldModule,
    MatSelectModule,
    MatOptionModule,
    AnalyticsComponent,
    OauthSuccessComponent,
    LucideAngularModule.pick({ Eye, EyeOff, Mail, Sparkles }),
    PupilComponent,
    EyeballComponent,
    MatTooltipModule,
    AppLoadingStateComponent,
    AppEmptyStateComponent,
    AppErrorStateComponent,
    SearchableDropdownComponent,
    DateRangePickerComponent,
  ],
  providers: [
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
  ],
  exports: [ToastrModule, ReactiveFormsModule],
  bootstrap: [AppComponent],
})
export class AppModule {}

