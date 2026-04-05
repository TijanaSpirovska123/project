import {
  Component, Input, OnInit, OnDestroy, AfterViewInit, OnChanges,
  SimpleChanges, ViewChild, ElementRef, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-pupil',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div #pupilRef class="pupil"
      [style.width.px]="size"
      [style.height.px]="size"
      [style.backgroundColor]="pupilColor"
      [style.transform]="'translate(' + pos.x + 'px, ' + pos.y + 'px)'"
      [style.transition]="'transform 0.1s ease-out'"
      [style.borderRadius]="'50%'">
    </div>`,
})
export class PupilComponent implements OnInit, OnDestroy, AfterViewInit, OnChanges {
  @Input() size = 12;
  @Input() maxDistance = 5;
  @Input() pupilColor = 'black';
  @Input() forceLookX?: number;
  @Input() forceLookY?: number;
  @ViewChild('pupilRef') pupilRef!: ElementRef<HTMLDivElement>;

  pos = { x: 0, y: 0 };
  private mouseX = 0;
  private mouseY = 0;

  constructor(private cdr: ChangeDetectorRef) {}

  private onMouseMove = (e: MouseEvent) => {
    this.mouseX = e.clientX;
    this.mouseY = e.clientY;
    this.update();
    this.cdr.markForCheck();
  };

  ngOnInit() { window.addEventListener('mousemove', this.onMouseMove); }
  ngAfterViewInit() { this.update(); }
  ngOnDestroy() { window.removeEventListener('mousemove', this.onMouseMove); }
  ngOnChanges(c: SimpleChanges) {
    if (c['forceLookX'] || c['forceLookY']) this.update();
  }

  private update() {
    if (this.forceLookX !== undefined && this.forceLookY !== undefined) {
      this.pos = { x: this.forceLookX, y: this.forceLookY };
      return;
    }
    if (!this.pupilRef?.nativeElement) return;
    const rect = this.pupilRef.nativeElement.getBoundingClientRect();
    const dx = this.mouseX - (rect.left + rect.width / 2);
    const dy = this.mouseY - (rect.top + rect.height / 2);
    const dist = Math.min(Math.sqrt(dx * dx + dy * dy), this.maxDistance);
    const angle = Math.atan2(dy, dx);
    this.pos = { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist };
  }
}
