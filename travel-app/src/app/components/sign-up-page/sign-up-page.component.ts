import {
  Component, OnInit, OnDestroy, Inject, PLATFORM_ID,
  ViewChild, ElementRef,
} from '@angular/core';
import { Router } from '@angular/router';
import { CoreService } from '../../services/core/core.service';
import { RegisterService } from '../../services/core/register.service';
import { UserDto } from '../../models/core/user.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Eye, EyeOff, Sparkles } from 'lucide-angular';

@Component({
  selector: 'app-sign-up-page',
  standalone: false,
  templateUrl: './sign-up-page.component.html',
  styleUrl: './sign-up-page.component.scss',
})
export class SignUpPageComponent implements OnInit, OnDestroy {
  // ── Existing auth state ──────────────────────────────────────────────────
  formGroup!: FormGroup;
  userCredentials!: UserDto;
  private isBrowser: boolean;

  // ── Lucide icons ─────────────────────────────────────────────────────────
  readonly Eye = Eye;
  readonly EyeOff = EyeOff;
  readonly Sparkles = Sparkles;

  // ── Password visibility ──────────────────────────────────────────────────
  showPassword = false;
  showConfirmPassword = false;

  // ── Animation state ──────────────────────────────────────────────────────
  isPurpleBlinking = false;
  isBlackBlinking = false;
  isTyping = false;
  isLookingAtEachOther = false;
  isPurplePeeking = false;

  @ViewChild('purpleRef') purpleRef!: ElementRef<HTMLDivElement>;
  @ViewChild('blackRef')  blackRef!:  ElementRef<HTMLDivElement>;
  @ViewChild('yellowRef') yellowRef!: ElementRef<HTMLDivElement>;
  @ViewChild('orangeRef') orangeRef!: ElementRef<HTMLDivElement>;

  purplePos = { faceX: 0, faceY: 0, bodySkew: 0 };
  blackPos  = { faceX: 0, faceY: 0, bodySkew: 0 };
  yellowPos = { faceX: 0, faceY: 0, bodySkew: 0 };
  orangePos = { faceX: 0, faceY: 0, bodySkew: 0 };

  private purpleBlinkTimeout?: ReturnType<typeof setTimeout>;
  private blackBlinkTimeout?:  ReturnType<typeof setTimeout>;
  private lookTimeout?:        ReturnType<typeof setTimeout>;
  private peekTimeout?:        ReturnType<typeof setTimeout>;
  private mouseX = 0;
  private mouseY = 0;

  private onMouseMove = (e: MouseEvent) => {
    this.mouseX = e.clientX;
    this.mouseY = e.clientY;
    this.purplePos = this.calcPos(this.purpleRef);
    this.blackPos  = this.calcPos(this.blackRef);
    this.yellowPos = this.calcPos(this.yellowRef);
    this.orangePos = this.calcPos(this.orangeRef);
  };

  // ── Computed getters ─────────────────────────────────────────────────────
  get password(): string { return this.formGroup?.get('password')?.value ?? ''; }
  get isPasswordHidden()  { return this.password.length > 0 && !this.showPassword; }
  get isPasswordVisible() { return this.password.length > 0 && this.showPassword; }

  get purpleHeight() { return (this.isTyping || this.isPasswordHidden) ? '440px' : '400px'; }
  get purpleTransform() {
    if (this.isPasswordVisible) return 'skewX(0deg)';
    if (this.isPasswordHidden || this.isTyping) return `skewX(${(this.purplePos.bodySkew || 0) - 12}deg) translateX(40px)`;
    return `skewX(${this.purplePos.bodySkew || 0}deg)`;
  }
  get purpleEyesLeft()   { return this.isPasswordVisible ? '20px' : this.isLookingAtEachOther ? '55px' : `${45 + this.purplePos.faceX}px`; }
  get purpleEyesTop()    { return this.isPasswordVisible ? '35px' : this.isLookingAtEachOther ? '65px' : `${40 + this.purplePos.faceY}px`; }
  get purpleForceLookX(): number | undefined { if (this.isPasswordVisible) return this.isPurplePeeking ? 4 : -4; if (this.isLookingAtEachOther) return 3; return undefined; }
  get purpleForceLookY(): number | undefined { if (this.isPasswordVisible) return this.isPurplePeeking ? 5 : -4; if (this.isLookingAtEachOther) return 4; return undefined; }

  get blackTransform() {
    if (this.isPasswordVisible) return 'skewX(0deg)';
    if (this.isLookingAtEachOther) return `skewX(${(this.blackPos.bodySkew || 0) * 1.5 + 10}deg) translateX(20px)`;
    if (this.isPasswordHidden || this.isTyping) return `skewX(${(this.blackPos.bodySkew || 0) * 1.5}deg)`;
    return `skewX(${this.blackPos.bodySkew || 0}deg)`;
  }
  get blackEyesLeft()   { return this.isPasswordVisible ? '10px' : this.isLookingAtEachOther ? '32px' : `${26 + this.blackPos.faceX}px`; }
  get blackEyesTop()    { return this.isPasswordVisible ? '28px' : this.isLookingAtEachOther ? '12px' : `${32 + this.blackPos.faceY}px`; }
  get blackForceLookX(): number | undefined { return this.isPasswordVisible ? -4 : this.isLookingAtEachOther ? 0 : undefined; }
  get blackForceLookY(): number | undefined { return this.isPasswordVisible ? -4 : this.isLookingAtEachOther ? -4 : undefined; }

  get orangeTransform() { return this.isPasswordVisible ? 'skewX(0deg)' : `skewX(${this.orangePos.bodySkew || 0}deg)`; }
  get orangeEyesLeft()  { return this.isPasswordVisible ? '50px' : `${82 + (this.orangePos.faceX || 0)}px`; }
  get orangeEyesTop()   { return this.isPasswordVisible ? '85px' : `${90 + (this.orangePos.faceY || 0)}px`; }
  get orangeForceLookX(): number | undefined { return this.isPasswordVisible ? -5 : undefined; }
  get orangeForceLookY(): number | undefined { return this.isPasswordVisible ? -4 : undefined; }

  get yellowTransform()  { return this.isPasswordVisible ? 'skewX(0deg)' : `skewX(${this.yellowPos.bodySkew || 0}deg)`; }
  get yellowEyesLeft()   { return this.isPasswordVisible ? '20px' : `${52 + (this.yellowPos.faceX || 0)}px`; }
  get yellowEyesTop()    { return this.isPasswordVisible ? '35px' : `${40 + (this.yellowPos.faceY || 0)}px`; }
  get yellowMouthLeft()  { return this.isPasswordVisible ? '10px' : `${40 + (this.yellowPos.faceX || 0)}px`; }
  get yellowMouthTop()   { return this.isPasswordVisible ? '88px' : `${88 + (this.yellowPos.faceY || 0)}px`; }
  get yellowForceLookX(): number | undefined { return this.isPasswordVisible ? -5 : undefined; }
  get yellowForceLookY(): number | undefined { return this.isPasswordVisible ? -4 : undefined; }

  constructor(
    private readonly registerService: RegisterService,
    private readonly formBuilder: FormBuilder,
    private readonly router: Router,
    public toastr: AppToastrService,
    @Inject(DOCUMENT) private document: Document,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    this.isBrowser = isPlatformBrowser(this.platformId);
  }

  ngOnInit(): void {
    this.createFormGroup();
    if (this.isBrowser) {
      this.document.body.style.overflowY = 'hidden';
      window.addEventListener('mousemove', this.onMouseMove);
      this.schedulePurpleBlink();
      this.scheduleBlackBlink();
    }
  }

  ngOnDestroy(): void {
    if (this.isBrowser) {
      window.removeEventListener('mousemove', this.onMouseMove);
    }
    [this.purpleBlinkTimeout, this.blackBlinkTimeout, this.lookTimeout, this.peekTimeout]
      .forEach(t => clearTimeout(t));
  }

  // ── Existing auth methods (unchanged) ────────────────────────────────────
  hasError(controlName: string, errorType: string): boolean {
    const control = this.formGroup.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  createFormGroup(): void {
    this.formGroup = this.formBuilder.group(
      {
        fullName: ['', [Validators.required, Validators.maxLength(255)]],
        username: ['', [Validators.required, Validators.maxLength(255)]],
        email: ['', [Validators.required, Validators.maxLength(255), Validators.email]],
        password: ['', [Validators.required, Validators.maxLength(255)]],
        confirmPassword: ['', [Validators.required]],
      },
      { validator: this.passwordMatchValidator }
    );
  }

  passwordMatchValidator(formGroup: FormGroup) {
    const password = formGroup.get('password')?.value;
    const confirmPassword = formGroup.get('confirmPassword')?.value;
    return password === confirmPassword ? null : { passwordMismatch: true };
  }

  onSubmit(): void {
    const { confirmPassword, ...userDto } = this.formGroup.value;
    this.registerService.create(userDto).subscribe({
      next: () => {
        this.toastr.success('Your account was created — please log in', 'Registration Complete');
        this.formGroup.reset();
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.toastr.error(CoreService.extractErrorMessage(err, 'Registration failed'), 'Error');
      },
    });
  }

  // ── Animation methods ─────────────────────────────────────────────────────
  togglePassword() { this.showPassword = !this.showPassword; this.schedulePeekIfNeeded(); }
  toggleConfirmPassword() { this.showConfirmPassword = !this.showConfirmPassword; }

  onInputFocus() {
    this.isTyping = true;
    this.isLookingAtEachOther = true;
    clearTimeout(this.lookTimeout);
    this.lookTimeout = setTimeout(() => { this.isLookingAtEachOther = false; }, 800);
  }

  onInputBlur() { this.isTyping = false; this.isLookingAtEachOther = false; }

  onPasswordChange(_val: string) { this.schedulePeekIfNeeded(); }

  private schedulePeekIfNeeded() {
    clearTimeout(this.peekTimeout);
    if (this.password.length > 0 && this.showPassword) {
      this.peekTimeout = setTimeout(() => {
        this.isPurplePeeking = true;
        setTimeout(() => { this.isPurplePeeking = false; }, 800);
      }, Math.random() * 3000 + 2000);
    } else {
      this.isPurplePeeking = false;
    }
  }

  private schedulePurpleBlink() {
    this.purpleBlinkTimeout = setTimeout(() => {
      this.isPurpleBlinking = true;
      setTimeout(() => { this.isPurpleBlinking = false; this.schedulePurpleBlink(); }, 150);
    }, Math.random() * 4000 + 3000);
  }

  private scheduleBlackBlink() {
    this.blackBlinkTimeout = setTimeout(() => {
      this.isBlackBlinking = true;
      setTimeout(() => { this.isBlackBlinking = false; this.scheduleBlackBlink(); }, 150);
    }, Math.random() * 4000 + 3000);
  }

  private calcPos(ref: ElementRef<HTMLDivElement> | undefined) {
    if (!ref?.nativeElement) return { faceX: 0, faceY: 0, bodySkew: 0 };
    const rect = ref.nativeElement.getBoundingClientRect();
    const dx = this.mouseX - (rect.left + rect.width / 2);
    const dy = this.mouseY - (rect.top + rect.height / 3);
    return {
      faceX:    Math.max(-15, Math.min(15, dx / 20)),
      faceY:    Math.max(-10, Math.min(10, dy / 30)),
      bodySkew: Math.max(-6,  Math.min(6, -dx / 120)),
    };
  }
}
