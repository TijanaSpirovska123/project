import {
  ChangeDetectorRef,
  Component,
  NgZone,
  OnInit,
  OnDestroy,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { AdService } from '../../services/ad/ad.service';
import { AdSetService } from '../../services/adset/adset.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { CreativeService } from '../../services/ad-creative/creative.service';
import { AssetLibraryService } from '../../services/asset/asset-library.service';
import { PageService } from '../../services/ad-creative/page.service';
import {
  AdSetResponse,
  StoredAssetDto,
  CreativeDto,
} from '../../models/adset/adset.model';
import { Campaign } from '../../models/campaign/campaign';
import { Provider } from '../../data/provider/provider.enum';
import { DropdownOption } from '../shared/searchable-dropdown.component';
import { PageDto } from '../../models/ad-creative/page.model';

function urlValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value || value.trim() === '') return null;
  try {
    const url = new URL(value);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return { invalidUrl: 'URL must start with http:// or https://' };
    }
    return null;
  } catch {
    return { invalidUrl: 'Please enter a valid URL' };
  }
}

@Component({
  selector: 'app-create-ad-workflow',
  standalone: false,
  templateUrl: './create-ad-workflow.component.html',
  styleUrls: ['./create-ad-workflow.component.scss'],
})
export class CreateAdWorkflowComponent implements OnInit, OnDestroy {
  adForm!: FormGroup;
  isPublishing = false;
  isInitialLoading = false;

  // Mobile stepper (sm/xs only — controlled by CSS)
  mobileStep = 1; // 1: Ad Details, 2: Choose Creative, 3: Review & Publish
  readonly mobileStepLabels = [
    'Ad Details',
    'Choose Creative',
    'Review & Publish',
  ];

  nextMobileStep(): void {
    if (this.mobileStep < 3) this.mobileStep++;
  }
  prevMobileStep(): void {
    if (this.mobileStep > 1) this.mobileStep--;
  }
  userId = '';
  actId: string | null = null;

  campaigns: Campaign[] = [];
  isLoadingCampaigns = false;
  selectedCampaignId: string = '';

  allAdSets: AdSetResponse[] = []; // full DB list — filtered locally per campaign selection
  adSets: AdSetResponse[] = [];
  isLoadingAdSets = false;

  creatives: any[] = [];
  isLoadingCreatives = false;
  selectedCreative: any = null;

  /** Which side panel is open: creative picker, creative creator, or none */
  activePanelType: 'picker' | 'creator' | null = null;

  // ── Asset Library tab (Tab 2 of picker panel) ────────────────────────────────
  creativePickerTab: 'existing' | 'asset-library' = 'existing';
  pickerAssets: StoredAssetDto[] = [];
  isLoadingPickerAssets = false;
  selectedPickerAsset: StoredAssetDto | null = null;
  isUploadingPickerAsset = false;
  private assetThumbCache = new Map<string, string>();
  private assetThumbObjectUrls: string[] = [];

  // Variant selection modal
  isVariantModalOpen = false;
  selectedAssetForVariant: StoredAssetDto | null = null;
  selectedVariantKey: string = 'ORIGINAL';

  assetCreativeForm!: FormGroup;
  isSubmittingAssetCreative = false;
  assetCreativeSubmitLabel = 'Create Creative';
  showCreativeModeModal = false;

  // Pages for page selection
  pages: PageDto[] = [];
  isLoadingPages = false;
  selectedPage: PageDto | null = null;
  pageDropdownOpen = false;

  // UTM builder
  utmBuilderOpen = false;
  utm = { source: '', medium: '', campaign: '', content: '', term: '' };
  utmPreview = '';

  readonly platforms = [
    { value: Provider.META, label: 'Meta' },
    { value: Provider.FACEBOOK, label: 'Facebook' },
    { value: Provider.INSTAGRAM, label: 'Instagram' },
  ];

  constructor(
    private formBuilder: FormBuilder,
    private router: Router,
    private toastr: AppToastrService,
    private authStore: AuthStoreService,
    private adService: AdService,
    private adSetService: AdSetService,
    private campaignService: CampaignService,
    private creativeService: CreativeService,
    private assetService: AssetLibraryService,
    private pageService: PageService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.userId = this.authStore.getUserId();
    this.actId = this.authStore.getActId();

    this.adForm = this.formBuilder.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      userId: [this.userId],
      status: ['PAUSED', [Validators.required]],
      adSetId: ['', [Validators.required]],
      creativeId: ['', [Validators.required]],
      platform: [Provider.META, [Validators.required]],
      adAccountId: [this.actId ? `act_${this.actId}` : ''],
      pageId: [''],
    });

    this.assetCreativeForm = this.formBuilder.group({
      pageId: ['', [Validators.required]],
      headline: ['', [Validators.required]],
      message: ['', [Validators.required]],
      objectUrl: ['', [Validators.required, urlValidator]],
      urlTags: [''],
      objectType: ['SHARE'],
      platform: ['META'],
      callToAction: ['LEARN_MORE', Validators.required],
    });
    this.isInitialLoading = true;

    forkJoin({
      campaigns: this.campaignService.getAllByPlatform(Provider.META),
      adSets: this.adSetService.getAllByPlatform(Provider.META),
      pages: this.pageService.getAll(),
    })
      .pipe(
        finalize(() => {
          this.isInitialLoading = false;
          this.cdr?.markForCheck();
        }),
      )
      .subscribe({
        next: ({ campaigns, adSets, pages }: any) => {
          this.campaigns = campaigns?.data ?? [];
          this.allAdSets = adSets?.data ?? [];
          this.pages = Array.isArray(pages) ? pages : (pages?.data ?? []);
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(
              CoreService.extractErrorMessage(err, 'Failed to load form data'),
            );
          }
        },
      });
  }

  ngOnDestroy(): void {
    this.assetThumbObjectUrls.forEach((url) => URL.revokeObjectURL(url));
  }

  get campaignOptions(): DropdownOption[] {
    return this.campaigns
      .filter((c) => this.allAdSets.some((a) => a.campaignId === c.id))
      .map((c) => ({ value: String(c.id), label: c.name }));
  }

  get adSetOptions(): DropdownOption[] {
    return this.adSets.map((s) => ({ value: String(s.id), label: s.name }));
  }

  get pageOptions(): DropdownOption[] {
    return this.pages.map((p) => ({ value: p.pageId, label: p.name }));
  }

  get platformOptions(): DropdownOption[] {
    return this.platforms.map((p) => ({ value: p.value, label: p.label }));
  }

  get statusOptions(): DropdownOption[] {
    return [
      { value: 'ACTIVE', label: 'Active' },
      { value: 'PAUSED', label: 'Paused' },
    ];
  }

  get callToActionOptions(): DropdownOption[] {
    return [
      { value: 'LEARN_MORE', label: 'Learn More' },
      { value: 'SHOP_NOW', label: 'Shop Now' },
      { value: 'SIGN_UP', label: 'Sign Up' },
      { value: 'CONTACT_US', label: 'Contact Us' },
      { value: 'DOWNLOAD', label: 'Download' },
      { value: 'GET_QUOTE', label: 'Get Quote' },
      { value: 'BOOK_NOW', label: 'Book Now' },
      { value: 'SUBSCRIBE', label: 'Subscribe' },
      { value: 'WATCH_MORE', label: 'Watch More' },
      { value: 'APPLY_NOW', label: 'Apply Now' },
    ];
  }

  onCampaignChange(opt: DropdownOption | null): void {
    const campaignId = opt ? String(opt.value) : '';
    this.selectedCampaignId = campaignId;
    this.adForm.get('adSetId')?.setValue('');
    this.adSets = [];

    if (campaignId) {
      this.loadAdSetsByCampaign(campaignId);
    }
  }

  loadAdSetsByCampaign(campaignId: string): void {
    // Filter locally from the preloaded DB list — no API call needed
    this.adSets = this.allAdSets.filter(
      (a) => a.campaignId === Number(campaignId),
    );
  }

  onPlatformChange(_opt?: DropdownOption | null): void {
    this.selectedCampaignId = '';
    this.adForm.get('adSetId')?.setValue('');
    this.campaigns = [];
    this.allAdSets = [];
    this.adSets = [];
    this.isLoadingCampaigns = true;
    this.isInitialLoading = true;

    forkJoin({
      campaigns: this.campaignService.getAllByPlatform(Provider.META),
      adSets: this.adSetService.getAllByPlatform(Provider.META),
    })
      .pipe(
        finalize(() => {
          this.isLoadingCampaigns = false;
          this.isInitialLoading = false;
          this.cdr?.markForCheck();
        }),
      )
      .subscribe({
        next: ({ campaigns, adSets }: any) => {
          this.campaigns = campaigns?.data ?? [];
          this.allAdSets = adSets?.data ?? [];
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(
              CoreService.extractErrorMessage(err, 'Failed to reload data'),
            );
          }
        },
      });
  }

  loadCreatives(): void {
    if (!this.actId) return;
    this.isLoadingCreatives = true;
    this.creativeService
      .getAllCreatives(this.userId, `act_${this.actId}`)
      .pipe(
        finalize(() => {
          this.ngZone.run(() => {
            this.isLoadingCreatives = false;
            this.cdr.markForCheck();
          });
        }),
      )
      .subscribe({
        next: (res: any) => {
          this.ngZone.run(() => {
            this.creatives = res?.data ?? [];
            this.cdr.markForCheck();
          });
        },
        error: (err: any) => {
          this.ngZone.run(() => {
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(
                CoreService.extractErrorMessage(
                  err,
                  'Failed to load ad creatives',
                ),
              );
            }
          });
        },
      });
  }

  refreshCreatives(): void {
    this.creatives = [];
    this.loadCreatives();
  }

  openPanel(type: 'picker' | 'creator'): void {
    if (type === 'picker' && !this.actId) {
      this.toastr.warning('No Meta ad account linked to this user');
      return;
    }
    this.activePanelType = type;
    if (
      type === 'picker' &&
      this.creatives.length === 0 &&
      !this.isLoadingCreatives
    ) {
      this.loadCreatives();
    }
  }

  closePanel(): void {
    this.activePanelType = null;
  }

  selectCreative(creative: any): void {
    this.selectedCreative = creative;
    this.adForm.get('creativeId')?.setValue(creative.id);
    this.activePanelType = null;
  }

  getCreativeThumbnail(creative: any): string {
    return creative.thumbnail_url || creative.image_url || '';
  }

  getObjectTypeLabel(type: string): string {
    const map: Record<string, string> = {
      PHOTO: 'Photo',
      SHARE: 'Link',
      STATUS: 'Status',
      POST_DELETED: 'Deleted',
      VIDEO: 'Video',
    };
    return map[type] || type;
  }

  hasError(controlName: string, errorType: string): boolean {
    const control = this.adForm.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  async publish(): Promise<void> {
    if (!this.adForm.valid) {
      this.adForm.markAllAsTouched();
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Please fill all required fields');
      }
      return;
    }
    this.isPublishing = true;
    try {
      await this.adService
        .create({
          ...this.adForm.value,
          userId: this.userId,
        })
        .toPromise();
      this.toastr.success('Ad created successfully!');
      this.router.navigate(['/meta']);
    } catch {
      this.isPublishing = false;
      this.cdr.markForCheck();
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Failed to create ad');
      }
    } finally {
      this.isPublishing = false;
    }
  }

  cancel(): void {
    this.router.navigate(['/meta']);
  }

  // ── Asset Library tab methods ─────────────────────────────────────────────────

  switchCreativeTab(tab: 'existing' | 'asset-library'): void {
    this.creativePickerTab = tab;
    if (
      tab === 'existing' &&
      this.creatives.length === 0 &&
      !this.isLoadingCreatives
    ) {
      this.loadCreatives();
    }
    if (tab === 'asset-library') {
      if (this.pickerAssets.length === 0) {
        this.loadPickerAssets();
      }
      // Pages are loaded on init; only reload if still empty and not already loading
      if (this.pages.length === 0 && !this.isLoadingPages) {
        this.loadPages();
      }
    }
  }

  loadPages(): void {
    this.isLoadingPages = true;
    this.pageService
      .getAll()
      .pipe(
        finalize(() => {
          this.isLoadingPages = false;
        }),
      )
      .subscribe({
        next: (res: any) => {
          this.pages = Array.isArray(res) ? res : (res?.data ?? []);
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(
              CoreService.extractErrorMessage(err, 'Failed to load pages'),
            );
          }
        },
      });
  }

  onPageChange(opt: DropdownOption | null): void {
    const pageId = opt?.value ?? '';
    const page = this.pages.find((p) => p.pageId === pageId);
    this.selectedPage = page || null;
  }

  selectPage(page: PageDto): void {
    this.selectedPage = page;
    this.assetCreativeForm.get('pageId')?.setValue(page.pageId);
    this.assetCreativeForm.get('pageId')?.markAsTouched();
    this.pageDropdownOpen = false;
  }

  get headlineLength(): number {
    return this.assetCreativeForm.get('headline')?.value?.length ?? 0;
  }

  get messageLength(): number {
    return this.assetCreativeForm.get('message')?.value?.length ?? 0;
  }

  buildUtm(): void {
    const params: string[] = [];
    if (this.utm.source)
      params.push(`utm_source=${encodeURIComponent(this.utm.source)}`);
    if (this.utm.medium)
      params.push(`utm_medium=${encodeURIComponent(this.utm.medium)}`);
    if (this.utm.campaign)
      params.push(`utm_campaign=${encodeURIComponent(this.utm.campaign)}`);
    if (this.utm.content)
      params.push(`utm_content=${encodeURIComponent(this.utm.content)}`);
    if (this.utm.term)
      params.push(`utm_term=${encodeURIComponent(this.utm.term)}`);
    this.utmPreview = params.join('&');
  }

  applyUtm(): void {
    this.assetCreativeForm.get('urlTags')?.setValue(this.utmPreview);
    this.utmBuilderOpen = false;
  }

  loadPickerAssets(): void {
    this.isLoadingPickerAssets = true;
    this.assetService
      .list()
      .pipe(
        finalize(() => {
          this.ngZone.run(() => {
            this.isLoadingPickerAssets = false;
            this.cdr.markForCheck();
          });
        }),
      )
      .subscribe({
        next: (res: any) => {
          this.ngZone.run(() => {
            const assets: StoredAssetDto[] = res?.data ?? res ?? [];
            this.pickerAssets = assets;
            this.cdr.markForCheck();
            this.preloadPickerThumbs(assets);
          });
        },
        error: (err: any) => {
          this.ngZone.run(() => {
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(
                CoreService.extractErrorMessage(err, 'Failed to load assets'),
              );
            }
          });
        },
      });
  }

  private preloadPickerThumbs(assets: StoredAssetDto[]): void {
    assets
      .filter((a) => a.assetType === 'IMAGE' && a.status === 'READY')
      .forEach((a) => {
        const variantKey = a.variants.some((v) => v.variantKey === 'ORIGINAL')
          ? 'ORIGINAL'
          : a.variants[0]?.variantKey;
        if (variantKey) this.fetchPickerThumb(a.id, variantKey);
      });
  }

  private fetchPickerThumb(assetId: number, variantKey: string): void {
    const key = `${assetId}_${variantKey}`;
    if (this.assetThumbCache.has(key)) return;
    this.assetService.fetchVariantBlob(assetId, variantKey).subscribe({
      next: (url: string) => {
        this.ngZone.run(() => {
          this.assetThumbObjectUrls.push(url);
          this.assetThumbCache.set(key, url);
        });
      },
      error: () => {},
    });
  }

  getPickerAssetThumb(asset: StoredAssetDto): string | null {
    const variantKey = asset.variants.some((v) => v.variantKey === 'ORIGINAL')
      ? 'ORIGINAL'
      : asset.variants[0]?.variantKey;
    if (!variantKey) return null;
    return this.assetThumbCache.get(`${asset.id}_${variantKey}`) ?? null;
  }

  selectPickerAsset(asset: StoredAssetDto): void {
    // Open variant selection modal
    this.selectedAssetForVariant = asset;
    this.isVariantModalOpen = true;
  }

  closeVariantModal(): void {
    this.isVariantModalOpen = false;
    this.selectedAssetForVariant = null;
  }

  selectVariant(variantKey: string): void {
    this.selectedPickerAsset = this.selectedAssetForVariant;
    this.selectedVariantKey = variantKey;
    this.closeVariantModal();
  }

  getVariantLabel(key: string): string {
    const labels: Record<string, string> = {
      ORIGINAL: 'Original',
      META_SQUARE_1080: '1:1 Square (1080×1080)',
      META_VERTICAL_1080: '4:5 Vertical (1080×1350)',
      META_STORIES_1080: '9:16 Stories (1080×1920)',
      META_LANDSCAPE_1200: '1.91:1 Landscape (1200×628)',
    };
    return labels[key] || key;
  }

  onUploadNewImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    input.value = '';
    this.isUploadingPickerAsset = true;
    this.assetService.upload(file).subscribe({
      next: (res: any) => {
        this.ngZone.run(() => {
          const uploaded: StoredAssetDto = res?.data ?? res;
          if (uploaded) {
            // Reload all assets from asset-creative endpoint
            this.loadPickerAssets();
            this.selectedPickerAsset = uploaded;
            this.toastr.success('Asset uploaded successfully');
          }
          this.isUploadingPickerAsset = false;
        });
      },
      error: (err: any) => {
        this.ngZone.run(() => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(
              CoreService.extractErrorMessage(err, 'Upload failed'),
            );
          }
          this.isUploadingPickerAsset = false;
        });
      },
    });
  }

  hasAssetCreativeFormError(controlName: string, errorType: string): boolean {
    const control = this.assetCreativeForm.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  get canSubmitAssetCreative(): boolean {
    return !!this.selectedPickerAsset && this.assetCreativeForm.valid;
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  }

  formatFileSize(bytes: number): string {
    if (bytes >= 1_000_000) return (bytes / 1_000_000).toFixed(1) + ' MB';
    if (bytes >= 1_000) return (bytes / 1_000).toFixed(0) + ' KB';
    return bytes + ' B';
  }

  isImage(asset: StoredAssetDto): boolean {
    return asset.assetType === 'IMAGE';
  }

  trackAssetById(_: number, asset: StoredAssetDto): number {
    return asset.id;
  }

  submitAssetCreative(): void {
    if (
      !this.canSubmitAssetCreative ||
      !this.selectedPickerAsset ||
      !this.selectedVariantKey ||
      !this.actId
    )
      return;
    this.assetCreativeForm.markAllAsTouched();
    if (!this.assetCreativeForm.valid) return;
    this.showCreativeModeModal = true;
  }

  confirmCreativeMode(mode: 'draft' | 'direct'): void {
    this.showCreativeModeModal = false;
    if (!this.selectedPickerAsset || !this.selectedVariantKey || !this.actId)
      return;

    const asset = this.selectedPickerAsset;
    const formVal = this.assetCreativeForm.value;
    const platform = formVal.platform || 'META';
    const adAccountId = `act_${this.actId}`;

    const body = {
      storedAssetId: asset.id,
      variantKey: this.selectedVariantKey,
      pageId: formVal.pageId,
      objectUrl: formVal.objectUrl,
      imageHash: asset.hash,
      message: formVal.message,
      headline: formVal.headline,
      urlTags: formVal.urlTags || '',
      objectType: formVal.objectType || 'SHARE',
      callToAction: formVal.callToAction || 'LEARN_MORE',
    };

    this.isSubmittingAssetCreative = true;
    this.assetCreativeSubmitLabel =
      mode === 'draft' ? 'Saving draft...' : 'Publishing...';

    const call$ =
      mode === 'draft'
        ? this.creativeService.createCreativeFromAsset(
            {
              storedAssetId: asset.id,
              variantKey: this.selectedVariantKey,
              platform,
              adAccountId,
            },
            {
              pageId: body.pageId,
              objectUrl: body.objectUrl,
              imageHash: body.imageHash,
              message: body.message,
              headline: body.headline,
              urlTags: body.urlTags,
              objectType: body.objectType,
              callToAction: body.callToAction,
            },
          )
        : this.creativeService.publishCreativeFromAsset(
            adAccountId,
            platform,
            body,
          );

    call$
      .pipe(
        finalize(() => {
          this.ngZone.run(() => {
            this.isSubmittingAssetCreative = false;
            this.assetCreativeSubmitLabel = 'Create Creative';
            this.cdr.markForCheck();
          });
        }),
      )
      .subscribe({
        next: (res: any) => {
          this.ngZone.run(() => {
            const creative: CreativeDto = res?.data ?? res;
            const msg =
              mode === 'draft'
                ? 'Creative saved as draft'
                : 'Creative published to platform';
            this.toastr.success(msg);
            this.selectCreative(creative);
            this.selectedPickerAsset = null;
            this.selectedVariantKey = 'ORIGINAL';
            this.selectedPage = null;
            this.assetCreativeForm.reset({
              pageId: '',
              objectType: 'SHARE',
              callToAction: 'LEARN_MORE',
            });
            this.creativePickerTab = 'existing';
          });
        },
        error: (err: any) => {
          this.ngZone.run(() => {
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(
                CoreService.extractErrorMessage(err, 'Failed to create creative'),
              );
            }
          });
        },
      });
  }
}
