import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  standalone:false,
  templateUrl: './spinner.component.html',
  styleUrls: ['./spinner.component.scss'],
})
export class SpinnerComponent implements OnInit {
  @Input() showSpinner: boolean = false;

  constructor() {}

  ngOnInit(): void {}
}
