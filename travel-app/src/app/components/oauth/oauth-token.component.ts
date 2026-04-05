import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { Subscription } from 'rxjs';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-oauth-success',
  template: `<p>Logging you in...</p>`,
})
export class OauthSuccessComponent implements OnInit, OnDestroy {
  private queryParamsSubscription?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authStore: AuthStoreService,
    private toastrService: ToastrService,
  ) {}

  ngOnInit(): void {
    this.queryParamsSubscription = this.route.queryParams.subscribe({
      next: (params) => {
        const token = params['token'];
        const userId = params['userId'];

        if (token) {
          try {
            this.authStore.login(token, userId);
            // Redirect to meta after OAuth connection
            this.router.navigate(['/meta']).catch((error) => {
              console.error('Navigation to meta failed:', error);
              this.router.navigate(['/login']);
            });
          } catch (error) {
            console.error('Login failed:', error);
            this.router.navigate(['/login']);
          }
        } else if (this.authStore.isAuthenticated()) {
          // Reconnect flow: user already logged in, Meta token was refreshed
          this.router.navigate(['/meta']);
        } else {
          console.warn('No token provided in query params');
          this.router.navigate(['/login']);
        }
      },
      error: (error) => {
        console.error('Error reading query params:', error);
        this.router.navigate(['/login']);
      },
    });
  }

  ngOnDestroy(): void {
    if (this.queryParamsSubscription) {
      this.queryParamsSubscription.unsubscribe();
    }
  }
}
