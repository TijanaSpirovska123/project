import {
  Component, Input, OnInit, OnDestroy, AfterViewInit, OnChanges,
  SimpleChanges, ViewChild, ElementRef, ChangeDetectorRef, Inject, PLATFORM_ID,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';

@Component({
  selector: 'app-eyeball',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div #eyeRef class="eyeball"
      [style.width.px]="size"
      [style.height]="isBlinking ? '2px' : size + 'px'"
      [style.backgroundColor]="eyeColor"
      [style.borderRadius]="'50%'"
      [style.overflow]="'hidden'"
      [style.display]="'flex'"
      [style.alignItems]="'center'"
      [style.justifyContent]="'center'"
      [style.transition]="'all 0.15s'">
      <div *ngIf="!isBlinking"
        [style.width.px]="pupilSize"
        [style.height.px]="pupilSize"
        [style.backgroundColor]="pupilColor"
        [style.borderRadius]="'50%'"
        [style.transform]="'translate(' + pos.x + 'px, ' + pos.y + 'px)'"
        [style.transition]="'transform 0.1s ease-out'">
      </div>
    </div>`,
})
export class EyeballComponent implements OnInit, OnDestroy, AfterViewInit, OnChanges {
  @Input() size = 48;
  @Input() pupilSize = 16;
  @Input() maxDistance = 10;
  @Input() eyeColor = 'white';
  @Input() pupilColor = 'black';
  @Input() isBlinking = false;
  @Input() forceLookX?: number;
  @Input() forceLookY?: number;
  @ViewChild('eyeRef') eyeRef!: ElementRef<HTMLDivElement>;

  pos = { x: 0, y: 0 };
  private mouseX = 0;
  private mouseY = 0;
  private readonly isBrowser: boolean;

  constructor(
    private cdr: ChangeDetectorRef,
    @Inject(PLATFORM_ID) platformId: Object,
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  private onMouseMove = (e: MouseEvent) => {
    this.mouseX = e.clientX;
    this.mouseY = e.clientY;
    this.update();
    this.cdr.markForCheck();
  };

  ngOnInit() {
    if (this.isBrowser) {
      window.addEventListener('mousemove', this.onMouseMove);
    }
  }

  ngAfterViewInit() {
    if (this.isBrowser) {
      this.update();
      this.cdr.detectChanges();
    }
  }

  ngOnDestroy() {
    if (this.isBrowser) {
      window.removeEventListener('mousemove', this.onMouseMove);
    }
  }

  ngOnChanges(c: SimpleChanges) {
    if (c['forceLookX'] || c['forceLookY']) this.update();
  }

  private update() {
    if (this.forceLookX !== undefined && this.forceLookY !== undefined) {
      this.pos = { x: this.forceLookX, y: this.forceLookY };
      return;
    }
    if (!this.eyeRef?.nativeElement) return;
    const rect = this.eyeRef.nativeElement.getBoundingClientRect();
    const dx = this.mouseX - (rect.left + rect.width / 2);
    const dy = this.mouseY - (rect.top + rect.height / 2);
    const dist = Math.min(Math.sqrt(dx * dx + dy * dy), this.maxDistance);
    const angle = Math.atan2(dy, dx);
    this.pos = { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist };
  }
}

