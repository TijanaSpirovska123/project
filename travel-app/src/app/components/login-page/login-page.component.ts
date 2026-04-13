import {
  Component,
  OnInit,
  OnDestroy,
  Inject,
  PLATFORM_ID,
  inject,
  ViewChild,
  ElementRef,
  ChangeDetectorRef,
  NgZone,
} from '@angular/core';
import { Router } from '@angular/router';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import {
  LoginResponseDto,
  RequestTokenDto,
  ResetPasswordDto,
  UserDto,
} from '../../models/core/user.model';
import { CoreService } from '../../services/core/core.service';
import { LoginService } from '../../services/core/login.service';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { RequestTokenService } from '../../services/reset-password/request-token.service';
import { ResetPasswordService } from '../../services/reset-password/reset-password.service';
import { OAuthService } from '../../services/core/oatuh.service';
import { environment } from '../../models/environment/environment-properties.model';
import { Eye, EyeOff, Mail, Sparkles } from 'lucide-angular';

@Component({
  selector: 'app-login-page',
  standalone: false,
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss',
})
export class LoginPageComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);

  // ── Existing auth state ──────────────────────────────────────────────────
  formGroup!: FormGroup;
  userCredentials!: UserDto;
  isLoggedIn = false;
  isForgotPassword = false;
  isRequestToken = false;
  isResetPassword = false;
  requestTokenDto!: RequestTokenDto;
  resetPasswordDto!: ResetPasswordDto;
  private isBrowser: boolean;

  // ── Lucide icons ─────────────────────────────────────────────────────────
  readonly Eye = Eye;
  readonly EyeOff = EyeOff;
  readonly Mail = Mail;
  readonly Sparkles = Sparkles;

  // ── Password visibility ──────────────────────────────────────────────────
  showPassword = false;

  // ── Animation state ──────────────────────────────────────────────────────
  isPurpleBlinking = false;
  isBlackBlinking = false;
  isTyping = false;
  isLookingAtEachOther = false;
  isPurplePeeking = false;

  @ViewChild('purpleRef') purpleRef!: ElementRef<HTMLDivElement>;
  @ViewChild('blackRef') blackRef!: ElementRef<HTMLDivElement>;
  @ViewChild('yellowRef') yellowRef!: ElementRef<HTMLDivElement>;
  @ViewChild('orangeRef') orangeRef!: ElementRef<HTMLDivElement>;

  purplePos = { faceX: 0, faceY: 0, bodySkew: 0 };
  blackPos = { faceX: 0, faceY: 0, bodySkew: 0 };
  yellowPos = { faceX: 0, faceY: 0, bodySkew: 0 };
  orangePos = { faceX: 0, faceY: 0, bodySkew: 0 };

  private purpleBlinkTimeout?: ReturnType<typeof setTimeout>;
  private blackBlinkTimeout?: ReturnType<typeof setTimeout>;
  private lookTimeout?: ReturnType<typeof setTimeout>;
  private peekTimeout?: ReturnType<typeof setTimeout>;

  private mouseX = 0;
  private mouseY = 0;

  private onMouseMove = (e: MouseEvent) => {
    this.mouseX = e.clientX;
    this.mouseY = e.clientY;
    this.purplePos = this.calcPos(this.purpleRef);
    this.blackPos = this.calcPos(this.blackRef);
    this.yellowPos = this.calcPos(this.yellowRef);
    this.orangePos = this.calcPos(this.orangeRef);
    this.cdr.detectChanges();
  };

  // ── Computed password getter ─────────────────────────────────────────────
  get password(): string {
    return this.formGroup?.get('password')?.value ?? '';
  }
  get isPasswordHidden() {
    return this.password.length > 0 && !this.showPassword;
  }
  get isPasswordVisible() {
    return this.password.length > 0 && this.showPassword;
  }

  // ── Character transforms ─────────────────────────────────────────────────
  get purpleHeight() {
    return this.isTyping || this.isPasswordHidden ? '440px' : '400px';
  }
  get purpleTransform() {
    if (this.isPasswordVisible) return 'skewX(0deg)';
    if (this.isPasswordHidden || this.isTyping)
      return `skewX(${(this.purplePos.bodySkew || 0) - 12}deg) translateX(40px)`;
    return `skewX(${this.purplePos.bodySkew || 0}deg)`;
  }
  get purpleEyesLeft() {
    return this.isPasswordVisible
      ? '20px'
      : this.isLookingAtEachOther
        ? '55px'
        : `${45 + this.purplePos.faceX}px`;
  }
  get purpleEyesTop() {
    return this.isPasswordVisible
      ? '35px'
      : this.isLookingAtEachOther
        ? '65px'
        : `${40 + this.purplePos.faceY}px`;
  }
  get purpleForceLookX(): number | undefined {
    if (this.isPasswordVisible) return this.isPurplePeeking ? 4 : -4;
    if (this.isLookingAtEachOther) return 3;
    return undefined;
  }
  get purpleForceLookY(): number | undefined {
    if (this.isPasswordVisible) return this.isPurplePeeking ? 5 : -4;
    if (this.isLookingAtEachOther) return 4;
    return undefined;
  }

  get blackTransform() {
    if (this.isPasswordVisible) return 'skewX(0deg)';
    if (this.isLookingAtEachOther)
      return `skewX(${(this.blackPos.bodySkew || 0) * 1.5 + 10}deg) translateX(20px)`;
    if (this.isPasswordHidden || this.isTyping)
      return `skewX(${(this.blackPos.bodySkew || 0) * 1.5}deg)`;
    return `skewX(${this.blackPos.bodySkew || 0}deg)`;
  }
  get blackEyesLeft() {
    return this.isPasswordVisible
      ? '10px'
      : this.isLookingAtEachOther
        ? '32px'
        : `${26 + this.blackPos.faceX}px`;
  }
  get blackEyesTop() {
    return this.isPasswordVisible
      ? '28px'
      : this.isLookingAtEachOther
        ? '12px'
        : `${32 + this.blackPos.faceY}px`;
  }
  get blackForceLookX(): number | undefined {
    return this.isPasswordVisible
      ? -4
      : this.isLookingAtEachOther
        ? 0
        : undefined;
  }
  get blackForceLookY(): number | undefined {
    return this.isPasswordVisible
      ? -4
      : this.isLookingAtEachOther
        ? -4
        : undefined;
  }

  get orangeTransform() {
    return this.isPasswordVisible
      ? 'skewX(0deg)'
      : `skewX(${this.orangePos.bodySkew || 0}deg)`;
  }
  get orangeEyesLeft() {
    return this.isPasswordVisible
      ? '50px'
      : `${82 + (this.orangePos.faceX || 0)}px`;
  }
  get orangeEyesTop() {
    return this.isPasswordVisible
      ? '85px'
      : `${90 + (this.orangePos.faceY || 0)}px`;
  }
  get orangeForceLookX(): number | undefined {
    return this.isPasswordVisible ? -5 : undefined;
  }
  get orangeForceLookY(): number | undefined {
    return this.isPasswordVisible ? -4 : undefined;
  }

  get yellowTransform() {
    return this.isPasswordVisible
      ? 'skewX(0deg)'
      : `skewX(${this.yellowPos.bodySkew || 0}deg)`;
  }
  get yellowEyesLeft() {
    return this.isPasswordVisible
      ? '20px'
      : `${52 + (this.yellowPos.faceX || 0)}px`;
  }
  get yellowEyesTop() {
    return this.isPasswordVisible
      ? '35px'
      : `${40 + (this.yellowPos.faceY || 0)}px`;
  }
  get yellowMouthLeft() {
    return this.isPasswordVisible
      ? '10px'
      : `${40 + (this.yellowPos.faceX || 0)}px`;
  }
  get yellowMouthTop() {
    return this.isPasswordVisible
      ? '88px'
      : `${88 + (this.yellowPos.faceY || 0)}px`;
  }
  get yellowForceLookX(): number | undefined {
    return this.isPasswordVisible ? -5 : undefined;
  }
  get yellowForceLookY(): number | undefined {
    return this.isPasswordVisible ? -4 : undefined;
  }

  constructor(
    @Inject(DOCUMENT) private document: Document,
    @Inject(PLATFORM_ID) private platformId: Object,
    private readonly loginService: LoginService,
    private readonly formBuilder: FormBuilder,
    private readonly requestTokenService: RequestTokenService,
    private readonly oauthService: OAuthService,
    private readonly resetPasswordService: ResetPasswordService,
    public toastr: AppToastrService,
    private authStore: AuthStoreService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
  ) {
    this.isBrowser = isPlatformBrowser(this.platformId);
  }

  ngOnInit(): void {
    this.createFormGroup();
    if (this.isBrowser) {
      this.document.body.style.overflowY = 'hidden';
      this.document.documentElement.style.overflowY = 'hidden';
      this.ngZone.runOutsideAngular(() => {
        window.addEventListener('mousemove', this.onMouseMove);
      });
      this.schedulePurpleBlink();
      this.scheduleBlackBlink();
    }
  }

  ngOnDestroy(): void {
    if (this.isBrowser) {
      window.removeEventListener('mousemove', this.onMouseMove);
      this.document.body.style.overflowY = '';
      this.document.documentElement.style.overflowY = '';
    }
    [
      this.purpleBlinkTimeout,
      this.blackBlinkTimeout,
      this.lookTimeout,
      this.peekTimeout,
    ].forEach((t) => clearTimeout(t));
  }

  // ── Existing auth methods (unchanged) ────────────────────────────────────
  createFormGroup(): void {
    this.formGroup = this.formBuilder.group({
      email: ['', [Validators.required, Validators.maxLength(255)]],
      password: ['', [Validators.required, Validators.maxLength(255)]],
      confirmPassword: ['', [Validators.required, Validators.maxLength(255)]],
      token: ['', [Validators.required, Validators.maxLength(255)]],
    });
  }

  hasError(controlName: string, errorType: string): boolean {
    const control = this.formGroup.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  login() {
    const { token, ...userDtoWithoutToken } = this.formGroup.value;
    this.userCredentials = userDtoWithoutToken;
    this.loginService.create(this.userCredentials).subscribe({
      next: (data: LoginResponseDto) => {
        this.formGroup.reset();
        this.authStore.login(
          data.token,
          String(data.userId),
          data.actId,
        );
        this.toastr.success('You have successfully logged in', 'Welcome back!');
        let redirect = '/sync-accounts';
        try {
          redirect = sessionStorage.getItem('redirectAfterLogin') || '/sync-accounts';
          sessionStorage.removeItem('redirectAfterLogin');
        } catch { /* ignore storage errors */ }
        this.router.navigate([redirect]);
      },
      error: (err) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(
            CoreService.extractErrorMessage(err, 'Invalid credentials. Please try again.'),
            'Login Failed',
          );
        }
      },
    });
  }

  connectMeta() {
    this.oauthService.connectMeta().subscribe({
      next: (res) => {
        window.location.href = res.authorizationUrl;
      },
      error: (err) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(
            CoreService.extractErrorMessage(err, 'Meta connection failed'),
            'Error',
          );
        }
      },
    });
  }

  loginWithInstagram() {
    window.location.href =
      'http://localhost:8080/oauth2/authorization/instagram';
  }

  forgotPassword() {
    this.isResetPassword = true;
  }

  sendToken() {
    this.requestTokenDto = { email: this.formGroup.value.email };
    this.requestTokenService.create(this.requestTokenDto).subscribe({
      next: () => {
        this.toastr.success(
          'All set! Check your email for the password reset code.',
          'Email Sent Successfully',
        );
        this.isForgotPassword = true;
        this.isResetPassword = false;
      },
      error: (err) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to send token'), 'Error');
        }
      },
    });
  }

  resetPassword() {
    this.resetPasswordDto = {
      email: this.formGroup.value.email,
      token: this.formGroup.value.token,
      newPassword: this.formGroup.value.password,
    };
    this.resetPasswordService.create(this.resetPasswordDto).subscribe({
      next: () => {
        this.isForgotPassword = false;
        this.formGroup.reset();
        this.toastr.success(
          'Your password has been successfully updated!',
          'Password Changed',
        );
      },
      error: (err) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to reset password'), 'Error');
        }
      },
    });
  }

  // ── Animation methods ─────────────────────────────────────────────────────
  togglePassword() {
    this.showPassword = !this.showPassword;
    this.schedulePeekIfNeeded();
  }

  onInputFocus() {
    this.isTyping = true;
    this.isLookingAtEachOther = true;
    clearTimeout(this.lookTimeout);
    this.lookTimeout = setTimeout(() => {
      this.isLookingAtEachOther = false;
    }, 800);
  }

  onInputBlur() {
    this.isTyping = false;
    this.isLookingAtEachOther = false;
  }

  onPasswordChange(_val: string) {
    this.schedulePeekIfNeeded();
  }

  private schedulePeekIfNeeded() {
    clearTimeout(this.peekTimeout);
    if (this.password.length > 0 && this.showPassword) {
      this.peekTimeout = setTimeout(
        () => {
          this.isPurplePeeking = true;
          setTimeout(() => {
            this.isPurplePeeking = false;
          }, 800);
        },
        Math.random() * 3000 + 2000,
      );
    } else {
      this.isPurplePeeking = false;
    }
  }

  private schedulePurpleBlink() {
    this.purpleBlinkTimeout = setTimeout(
      () => {
        this.isPurpleBlinking = true;
        setTimeout(() => {
          this.isPurpleBlinking = false;
          this.schedulePurpleBlink();
        }, 150);
      },
      Math.random() * 4000 + 3000,
    );
  }

  private scheduleBlackBlink() {
    this.blackBlinkTimeout = setTimeout(
      () => {
        this.isBlackBlinking = true;
        setTimeout(() => {
          this.isBlackBlinking = false;
          this.scheduleBlackBlink();
        }, 150);
      },
      Math.random() * 4000 + 3000,
    );
  }

  private calcPos(ref: ElementRef<HTMLDivElement> | undefined) {
    if (!ref?.nativeElement) return { faceX: 0, faceY: 0, bodySkew: 0 };
    const rect = ref.nativeElement.getBoundingClientRect();
    const dx = this.mouseX - (rect.left + rect.width / 2);
    const dy = this.mouseY - (rect.top + rect.height / 3);
    return {
      faceX: Math.max(-15, Math.min(15, dx / 20)),
      faceY: Math.max(-10, Math.min(10, dy / 30)),
      bodySkew: Math.max(-6, Math.min(6, -dx / 120)),
    };
  }
}
