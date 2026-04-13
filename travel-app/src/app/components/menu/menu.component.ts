import {
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  OnDestroy,
} from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { MenuItem } from '../../models/core/menu.model';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { LogoutService } from '../../services/core/logout.service';
import { ThemeService } from '../../services/core/theme.service';

@Component({
  selector: 'app-menu',
  standalone: false,
  templateUrl: './menu.component.html',
  styleUrls: ['./menu.component.scss'],
})
export class MenuComponent implements OnInit, OnDestroy {
  @Input() isVisible: boolean = false;
  isExpanded: boolean = false;
  @Output() expanded: EventEmitter<boolean> = new EventEmitter<boolean>();
  isLoggedIn: boolean = false;
  isWorkationRequestActive: boolean = false;
  menuItems: MenuItem[] = [];
  private routerSubscription: Subscription = new Subscription();

  constructor(
    private router: Router,
    private toastr: AppToastrService,
    private authStore: AuthStoreService,
    private readonly logoutService: LogoutService,
    public theme: ThemeService,
  ) {}

  private onResize = () => {
    if (typeof document !== 'undefined') {
      if (window.innerWidth < 768) {
        document.documentElement.style.setProperty('--sidebar-width', '0px');
      } else if (!this.isExpanded) {
        document.documentElement.style.setProperty('--sidebar-width', '64px');
      }
    }
  };

  ngOnInit(): void {
    this.menuItems = [
      {
        icon: 'add_circle',
        label: 'Create Ad',
        route: 'create-ad-workflow',
        active: false,
        isParent: false,
        isExpanded: false,
      },
      {
        icon: 'inbox',
        label: 'Meta',
        route: 'meta',
        active: false,
        isParent: false,
        isExpanded: false,
      },
      {
        icon: 'insights',
        label: 'Insights',
        route: 'insights',
        active: false,
        isParent: false,
        isExpanded: false,
      },
      {
        icon: 'perm_media',
        label: 'Creative Library',
        route: 'creative-library',
        active: false,
        isParent: false,
        isExpanded: false,
      },
      {
        icon: 'sync',
        label: 'Sync Accounts',
        route: 'sync-accounts',
        active: false,
        isParent: false,
        isExpanded: false,
      },
    ];

    // Initialise the CSS variable used by page containers for margin-left
    if (typeof document !== 'undefined') {
      const isMobile = window.innerWidth < 768;
      document.documentElement.style.setProperty('--sidebar-width', isMobile ? '0px' : '64px');
      window.addEventListener('resize', this.onResize);
    }

    // Set initial active states based on current route
    this.setActiveMenuFromRoute();

    // Subscribe to authentication state changes
    this.authStore.isLoggedIn$.subscribe((isLoggedIn) => {
      this.isLoggedIn = isLoggedIn;
    });

    // Subscribe to router events to update active menu on navigation
    this.routerSubscription = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.setActiveMenuFromRoute();
      });
  }

  ngOnDestroy(): void {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this.onResize);
    }
  }

  navigate(selectedItem: MenuItem): void {
    if (selectedItem.isParent) {
      // Toggle parent expansion only when explicitly clicked
      selectedItem.isExpanded = !selectedItem.isExpanded;
      return;
    }

    // Clear all active states
    this.menuItems.forEach((item) => {
      item.active = false;
      if (item.children) {
        item.children.forEach((child) => (child.active = false));
      }
    });

    // Find parent of selected child item and keep it expanded and active
    const parentItem = this.menuItems.find((item) =>
      item.children?.includes(selectedItem),
    );

    if (parentItem) {
      parentItem.active = true;
      parentItem.isExpanded = true; // Keep parent expanded
    }

    // Set selected child item as active
    selectedItem.active = true;

    // Navigate to route
    if (selectedItem.route) {
      this.router.navigate([selectedItem.route]);
    }
  }

  setActiveMenuFromRoute(): void {
    const currentRoute = this.router.url.split('/')[1].split('?')[0]; // Get first path segment, strip query params

    // Clear all active states first
    this.menuItems.forEach((item) => {
      item.active = false;
      if (item.children) {
        item.children.forEach((child) => (child.active = false));
      }
    });

    // Find and activate the matching route
    this.menuItems.forEach((item) => {
      // Top-level non-parent item matching current route
      if (!item.isParent && item.route === currentRoute) {
        item.active = true;
        return;
      }

      // Parent item — check children
      if (item.children) {
        const activeChild = item.children.find(
          (child) => child.route === currentRoute,
        );

        if (activeChild) {
          item.active = true;
          item.isExpanded = true;
          activeChild.active = true;
        }
      }
    });
  }

  logout(): void {
    this.logoutService.logout().subscribe({
      next: () => {
        this.authStore.logout();
        // Successfully logged out from server
        this.router.navigate(['login']);
        this.toastr.success(
          'You have been logged out successfully',
          'Goodbye!',
        );
      },
    });
  }

  toggleExpand() {
    this.isExpanded = !this.isExpanded;
    this.expanded.emit(this.isExpanded);
    if (typeof document !== 'undefined' && window.innerWidth >= 768) {
      document.documentElement.style.setProperty(
        '--sidebar-width',
        this.isExpanded ? '220px' : '64px',
      );
    }
  }
}
