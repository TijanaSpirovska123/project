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
import { forkJoin, firstValueFrom } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
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
  StoredAssetVariantDto,
  CreativeDto,
} from '../../models/adset/adset.model';
import { Campaign } from '../../models/campaign/campaign';
import { Provider } from '../../data/provider/provider.enum';
import { AD_PLATFORM_OPTIONS, META_VARIANT_LABELS } from '../../data/provider/provider-options';
import { AD_STATUS_OPTIONS, CALL_TO_ACTION_OPTIONS } from '../../data/workflow/ad-form-options';
import { formatFileSize } from '../../utils/format.util';
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

  // Step wizard (steps 1-5, see step*Done / unlockedStep below)
  openStep = 1;
  /** Which creative pathway is active: an already-published creative, or one built from the asset library */
  creativeSource: 'existing' | 'library' | null = null;

  userId = '';

  campaigns: Campaign[] = [];
  isLoadingCampaigns = false;
  selectedCampaignId: string = '';

  allAdSets: AdSetResponse[] = []; // full DB list — filtered locally per campaign selection
  adSets: AdSetResponse[] = [];
  isLoadingAdSets = false;

  creatives: any[] = [];
  isLoadingCreatives = false;
  selectedCreative: any = null;

  /** The embedded creative-library overlay, opened from the "no creatives found" empty state */
  activePanelType: 'creator' | null = null;

  // ── Asset Library tab (Tab 2 of picker panel) ────────────────────────────────
  creativePickerTab: 'existing' | 'asset-library' = 'existing';
  pickerAssets: StoredAssetDto[] = [];
  isLoadingPickerAssets = false;
  selectedPickerAsset: StoredAssetDto | null = null;
  isUploadingPickerAsset = false;
  private assetThumbCache = new Map<string, string>();
  private assetThumbObjectUrls: string[] = [];

  // Variant selection (step 4 of the wizard)
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

  readonly platforms = AD_PLATFORM_OPTIONS;

  // Format is fixed to 'single' — no CreativeStrategy handles the others yet, so they're
  // shown for visibility (matching the design) but stay read-only.
  readonly formatOptions: { key: string; label: string; desc: string; disabled?: boolean }[] = [
    { key: 'single', label: 'Single image or video', desc: 'One asset, mapped to each placement.' },
    { key: 'carousel', label: 'Carousel', desc: '2–10 cards in order, each with its own headline and link.', disabled: true },
    { key: 'collection', label: 'Collection', desc: 'A cover asset plus products from a catalog.', disabled: true },
    { key: 'flexible', label: 'Flexible', desc: 'A pool of assets the platform assembles per placement.', disabled: true },
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

    this.adForm = this.formBuilder.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      userId: [this.userId],
      status: ['PAUSED', [Validators.required]],
      adSetId: ['', [Validators.required]],
      creativeId: ['', [Validators.required]],
      platform: [Provider.META, [Validators.required]],
      adAccountId: [''],
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
        error: (err: any) => this.handleError(err, 'Failed to load form data'),
      });
  }

  ngOnDestroy(): void {
    this.assetThumbObjectUrls.forEach((url) => URL.revokeObjectURL(url));
  }

  // ── Step wizard ─────────────────────────────────────────────────────────────
  // All 5 steps are always shown (matching the reference layout). Steps 4-5
  // (placement + creative details) only unlock when building a new creative
  // from the asset library — picking an already published creative in step 3
  // needs nothing further before publish, so they stay locked for that path.

  get step1Done(): boolean {
    return !!this.adForm?.get('platform')?.value;
  }

  get step2Done(): boolean {
    return !!(
      this.adForm?.get('name')?.value?.trim() &&
      this.selectedCampaignId &&
      this.adForm?.get('adSetId')?.value &&
      this.adForm?.get('status')?.value
    );
  }

  get step3Done(): boolean {
    if (this.creativeSource === 'existing') return !!this.selectedCreative;
    if (this.creativeSource === 'library') return !!this.selectedAssetForVariant;
    return false;
  }

  get step4Done(): boolean {
    return !!this.selectedAssetForVariant && !!this.selectedVariantKey;
  }

  get step5Done(): boolean {
    return this.creativeSource === 'library' && !!this.selectedCreative;
  }

  unlockedStep(n: number): boolean {
    switch (n) {
      case 1: return true;
      case 2: return this.step1Done;
      case 3: return this.step2Done;
      case 4: return this.creativeSource === 'library' && !!this.selectedAssetForVariant;
      case 5: return this.creativeSource === 'library' && this.step4Done;
      default: return false;
    }
  }

  /** Header/arrow click: collapse an already-open step, otherwise open it (if unlocked) */
  toggleStep(n: number): void {
    if (this.openStep === n) {
      this.openStep = 0;
      return;
    }
    this.openStepPanel(n);
  }

  openStepPanel(n: number): void {
    if (!this.unlockedStep(n)) return;
    this.openStep = n;
    if (n === 3) {
      if (this.creativePickerTab === 'existing' && this.creatives.length === 0 && !this.isLoadingCreatives) {
        this.loadCreatives();
      }
      if (this.creativePickerTab === 'asset-library' && this.pickerAssets.length === 0 && !this.isLoadingPickerAssets) {
        this.loadPickerAssets();
      }
    }
  }

  /** Move on from step n to the next unlocked step, or stay put if it isn't unlocked yet */
  continueToNext(n: number): void {
    const next = n + 1;
    if (this.unlockedStep(next)) {
      this.openStepPanel(next);
    } else {
      this.openStep = n;
    }
  }

  get step1Summary(): string {
    const p = this.platformOptions.find((o) => o.value === this.adForm?.get('platform')?.value);
    return p ? `${p.label} · Single image or video` : 'Which network is this ad for?';
  }

  get step2Summary(): string {
    if (!this.step2Done) return 'Name, campaign, ad set, status';
    const campaignName = this.campaigns.find((c) => String(c.id) === this.selectedCampaignId)?.name ?? '';
    return `${this.adForm.get('name')?.value} · ${campaignName} · ${this.adForm.get('status')?.value}`;
  }

  get step3Summary(): string {
    if (this.selectedCreative) return this.selectedCreative.name;
    if (this.selectedPickerAsset) return this.selectedPickerAsset.originalFilename;
    return 'Choose from your library';
  }

  get step4Summary(): string {
    return this.selectedAssetForVariant && this.selectedVariantKey
      ? this.getVariantLabel(this.selectedVariantKey)
      : 'Choose an image variant';
  }

  get step5Summary(): string {
    return this.step5Done ? 'Creative created' : 'Copy, destination and call to action';
  }

  // ── Live preview (right rail) ───────────────────────────────────────────────
  // Shows the exact crop the selected variant will publish with — same width/height
  // the asset-library API generated it at, so this reads correctly for any future
  // platform's variants too, not just Meta's.
  get hasPreviewContent(): boolean {
    return !!this.selectedCreative || !!this.selectedAssetForVariant;
  }

  /** The variant whose crop is currently shown: the one picked in step 4, or the
   *  original (pre-crop) while the user is still choosing one. */
  get previewVariant(): StoredAssetVariantDto | null {
    if (!this.selectedAssetForVariant) return null;
    if (this.selectedVariantKey) {
      const picked = this.selectedAssetForVariant.variants.find((v) => v.variantKey === this.selectedVariantKey);
      if (picked) return picked;
    }
    return (
      this.selectedAssetForVariant.variants.find((v) => v.variantKey === 'ORIGINAL')
      ?? this.selectedAssetForVariant.variants[0]
      ?? null
    );
  }

  get previewAspectRatio(): number {
    const v = this.previewVariant;
    return v?.width && v?.height ? v.width / v.height : 1;
  }

  /** "1:1", "4:5", "9:16"... next to the "Live preview" heading — snaps to the
   *  nearest common ratio name, same idea for any future platform's dimensions. */
  get previewRatioLabel(): string {
    const v = this.previewVariant;
    if (!v?.width || !v?.height) return '';
    const ratio = v.width / v.height;
    const known: [number, string][] = [
      [1, '1:1'], [0.8, '4:5'], [0.6667, '2:3'], [0.5625, '9:16'], [1.91, '1.91:1'],
    ];
    let best = known[0], diff = Infinity;
    for (const k of known) {
      const d = Math.abs(ratio - k[0]);
      if (d < diff) { diff = d; best = k; }
    }
    return diff < 0.03 ? best[1] : `${ratio.toFixed(2)}:1`;
  }

  get previewThumb(): string | null {
    if (this.selectedCreative) return this.getCreativeThumbnail(this.selectedCreative) || null;
    const v = this.previewVariant;
    if (this.selectedAssetForVariant && v) {
      const exact = this.getAssetVariantThumb(this.selectedAssetForVariant, v.variantKey);
      if (exact) return exact;
    }
    if (this.selectedAssetForVariant) return this.getPickerAssetThumb(this.selectedAssetForVariant);
    return null;
  }

  get previewPageName(): string {
    return this.selectedPage?.name
      || this.pages.find((p) => p.pageId === this.assetCreativeForm?.get('pageId')?.value)?.name
      || 'Your Page';
  }

  get previewHeadline(): string {
    return this.assetCreativeForm?.get('headline')?.value || this.selectedCreative?.name || 'Your headline';
  }

  get previewMessage(): string {
    return this.assetCreativeForm?.get('message')?.value || 'Your message appears here.';
  }

  get previewCta(): string {
    const key = this.assetCreativeForm?.get('callToAction')?.value;
    return this.callToActionOptions.find((o) => o.value === key)?.label || 'Learn More';
  }

  // ── Before-publish checklist (right rail) ─────────────────────────────────
  get checklistGroups(): { name: string; items: { label: string; ok: boolean }[] }[] {
    const groups: { name: string; items: { label: string; ok: boolean }[] }[] = [
      {
        name: 'Setup',
        items: [
          { label: 'Platform', ok: this.step1Done },
          { label: 'Ad name', ok: !!this.adForm?.get('name')?.value?.trim() },
          { label: 'Campaign', ok: !!this.selectedCampaignId },
          { label: 'Ad set', ok: !!this.adForm?.get('adSetId')?.value },
          { label: 'Status', ok: !!this.adForm?.get('status')?.value },
        ],
      },
      { name: 'Creative', items: [{ label: 'Ad creative selected', ok: !!this.adForm?.get('creativeId')?.value }] },
    ];
    if (this.creativeSource === 'library') {
      groups.push({
        name: 'Placement',
        items: [{ label: 'Image variant selected', ok: this.step4Done }],
      });
      groups.push({
        name: 'Details',
        items: [
          { label: 'Page', ok: !!this.assetCreativeForm.get('pageId')?.value },
          { label: 'Headline', ok: !!this.assetCreativeForm.get('headline')?.value?.trim() },
          { label: 'Message', ok: !!this.assetCreativeForm.get('message')?.value?.trim() },
          { label: 'Website URL', ok: !!this.assetCreativeForm.get('objectUrl')?.valid },
        ],
      });
      groups.push({ name: 'Finish', items: [{ label: 'Creative created', ok: this.step5Done }] });
    }
    return groups;
  }

  get checklistDone(): number {
    return this.checklistGroups.flatMap((g) => g.items).filter((i) => i.ok).length;
  }

  get checklistTotal(): number {
    return this.checklistGroups.flatMap((g) => g.items).length;
  }

  get campaignOptions(): DropdownOption[] {
    return this.campaigns
      .filter((c) => this.allAdSets.some((a) => a.campaignId === c.id))
      .map((c) => ({ value: String(c.id), label: c.name }));
  }

  get adSetOptions(): DropdownOption[] {
    return this.adSets.map((s) => ({ value: String(s.id), label: s.name }));
  }

  /**
   * A user can have several ad accounts synced at once, so the ad account for the rest of the
   * workflow (creatives, ad creation) is derived from whichever ad set is actually selected here
   * — not from a separately-tracked "current account" that can drift from what's on screen.
   */
  get selectedAdSet(): AdSetResponse | null {
    const id = this.adForm?.get('adSetId')?.value;
    if (!id) return null;
    return this.adSets.find((s) => String(s.id) === String(id)) ?? null;
  }

  get selectedAdSetAccountId(): string | null {
    return this.selectedAdSet?.adAccountId ?? null;
  }

  get pageOptions(): DropdownOption[] {
    return this.pages.map((p) => ({ value: p.pageId, label: p.name }));
  }

  get platformOptions(): DropdownOption[] {
    return this.platforms.map((p) => ({ value: p.value, label: p.label, disabled: p.disabled }));
  }

  readonly statusOptions      = AD_STATUS_OPTIONS;
  readonly callToActionOptions = CALL_TO_ACTION_OPTIONS;

  onCampaignChange(opt: DropdownOption | null): void {
    const campaignId = opt ? String(opt.value) : '';
    this.selectedCampaignId = campaignId;
    this.adForm.get('adSetId')?.setValue('');
    this.adSets = [];
    // The new campaign may belong to a different ad account than the previous selection —
    // clear any previously loaded/picked creative so a stale, wrong-account creative can't
    // ride along with the newly selected ad set.
    this.resetSelectedCreative();

    if (campaignId) {
      this.loadAdSetsByCampaign(campaignId);
    }
  }

  private resetSelectedCreative(): void {
    this.creatives = [];
    this.selectedCreative = null;
    this.adForm.get('creativeId')?.setValue('');
    this.selectedPickerAsset = null;
    this.selectedAssetForVariant = null;
    this.selectedVariantKey = 'ORIGINAL';
    this.creativeSource = null;
    if (this.openStep > 3) this.openStep = 3;
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
    this.resetSelectedCreative();
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
        error: (err: any) => this.handleError(err, 'Failed to reload data'),
      });
  }

  loadCreatives(): void {
    const adAccountId = this.selectedAdSetAccountId;
    if (!adAccountId) return;
    this.isLoadingCreatives = true;
    this.creativeService
      .getAllCreatives(this.userId, adAccountId)
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
        error: (err: any) => this.ngZone.run(() => this.handleError(err, 'Failed to load ad creatives')),
      });
  }

  refreshCreatives(): void {
    this.creatives = [];
    this.loadCreatives();
  }

  closePanel(): void {
    this.activePanelType = null;
  }

  selectCreative(creative: any): void {
    this.selectedCreative = creative;
    this.adForm.get('creativeId')?.setValue(creative.id);
    this.activePanelType = null;
  }

  /** Picking an already-published creative (tab 1) needs nothing further — no placement/details steps */
  pickExistingCreative(creative: any): void {
    if (creative.object_type === 'POST_DELETED') return;
    this.creativeSource = 'existing';
    this.selectCreative(creative);
    // Steps 4/5 (asset-library only) disappear once creativeSource flips to 'existing' —
    // keep the accordion pointed at a step that's still actually rendered.
    this.openStep = 3;
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

  private handleError(err: any, fallback: string): void {
    if (!this.authStore.isSessionExpiredRedirect()) {
      this.toastr.error(err ? CoreService.extractErrorMessage(err, fallback) : fallback);
    }
  }

  private controlHasError(form: FormGroup, controlName: string, errorType: string): boolean {
    const control = form.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  hasError(controlName: string, errorType: string): boolean {
    return this.controlHasError(this.adForm, controlName, errorType);
  }

  async publish(): Promise<void> {
    if (!this.adForm.valid) {
      this.adForm.markAllAsTouched();
      this.handleError(null, 'Please fill all required fields');
      return;
    }
    this.isPublishing = true;
    try {
      await firstValueFrom(this.adService.create({
        ...this.adForm.value,
        userId: this.userId,
        adAccountId: this.selectedAdSetAccountId ?? this.adForm.value.adAccountId,
      }));
      this.toastr.success('Ad created successfully!');
      this.router.navigate(['/meta']);
    } catch (err) {
      this.isPublishing = false;
      this.cdr.markForCheck();
      this.handleError(err, 'Failed to create ad');
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
        error: (err: any) => this.handleError(err, 'Failed to load pages'),
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

  private getControlLength(name: string): number {
    return this.assetCreativeForm.get(name)?.value?.length ?? 0;
  }

  get headlineLength(): number { return this.getControlLength('headline'); }
  get messageLength(): number  { return this.getControlLength('message'); }

  buildUtm(): void {
    this.utmPreview = Object.entries({
      utm_source:   this.utm.source,
      utm_medium:   this.utm.medium,
      utm_campaign: this.utm.campaign,
      utm_content:  this.utm.content,
      utm_term:     this.utm.term,
    })
      .filter(([, v]) => v)
      .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
      .join('&');
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
        error: (err: any) => this.ngZone.run(() => this.handleError(err, 'Failed to load assets')),
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

  /** The exact crop for one variant of an asset — used for the step 4 variant list
   *  and the live preview, so both show precisely how that variant will publish. */
  getAssetVariantThumb(asset: StoredAssetDto, variantKey: string): string | null {
    return this.assetThumbCache.get(`${asset.id}_${variantKey}`) ?? null;
  }

  /** Aspect ratio helper for step 4's variant rows — width/height comes straight off
   *  the variant DTO, so this works the same for any future platform's variants. */
  variantRatio(v: StoredAssetVariantDto): number {
    return v.width && v.height ? v.width / v.height : 1;
  }

  selectPickerAsset(asset: StoredAssetDto): void {
    this.creativeSource = 'library';
    this.selectedAssetForVariant = asset;
    this.openStep = 4;
    // Load the real crop for every publishable variant up front, so both the
    // variant list and the live preview can show the exact picture, not a stand-in.
    this.metaVariants(asset).forEach((v) => this.fetchPickerThumb(asset.id, v.variantKey));
  }

  selectVariant(variantKey: string): void {
    this.selectedPickerAsset = this.selectedAssetForVariant;
    this.selectedVariantKey = variantKey;
    this.continueToNext(4);
  }

  getVariantLabel(key: string): string {
    return META_VARIANT_LABELS[key] || key;
  }

  /** Short form for the preview's placement tabs — "1:1 Square" instead of
   *  "1:1 Square (1080×1080)". Derived from the same label, not hardcoded, so
   *  it keeps working for whatever labels a future platform's variants use. */
  placementTabLabel(key: string): string {
    const full = this.getVariantLabel(key);
    const idx = full.indexOf(' (');
    return idx > -1 ? full.slice(0, idx) : full;
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
          this.handleError(err, 'Upload failed');
          this.isUploadingPickerAsset = false;
        });
      },
    });
  }

  hasAssetCreativeFormError(controlName: string, errorType: string): boolean {
    return this.controlHasError(this.assetCreativeForm, controlName, errorType);
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
    return formatFileSize(bytes);
  }

  isImage(asset: StoredAssetDto): boolean {
    return asset.assetType === 'IMAGE';
  }

  hasMetaUpload(asset: StoredAssetDto): boolean {
    return (asset.variants ?? []).some(
      (v) => v.metaImageHash != null && v.metaImageHash !== '',
    );
  }

  metaVariants(asset: StoredAssetDto): StoredAssetVariantDto[] {
    return (asset.variants ?? []).filter(
      (v) => v.metaImageHash != null && v.metaImageHash !== '',
    );
  }

  trackAssetById(_: number, asset: StoredAssetDto): number {
    return asset.id;
  }

  submitAssetCreative(): void {
    if (
      !this.canSubmitAssetCreative ||
      !this.selectedPickerAsset ||
      !this.selectedVariantKey ||
      !this.selectedAdSetAccountId
    )
      return;
    this.assetCreativeForm.markAllAsTouched();
    if (!this.assetCreativeForm.valid) return;
    this.showCreativeModeModal = true;
  }

  confirmCreativeMode(mode: 'draft' | 'direct'): void {
    this.showCreativeModeModal = false;
    if (!this.selectedPickerAsset || !this.selectedVariantKey || !this.selectedAdSetAccountId)
      return;

    const asset = this.selectedPickerAsset;
    const formVal = this.assetCreativeForm.value;
    const platform = formVal.platform || 'META';
    const adAccountId = this.selectedAdSetAccountId;

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

    const isVideo = asset.assetType === 'VIDEO';

    // Video is always fully published by the stored-asset flow — it uploads to Meta and creates
    // the real platform creative internally, regardless of mode. "Draft" vs "direct" only matters
    // for images, since draft images are saved without ever calling Meta.
    const call$ =
      mode === 'draft' || isVideo
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
        // Direct-publish of an IMAGE: it must be uploaded (and hashed by Meta) for this specific
        // ad account first. asset.hash is our own content hash, not Meta's — it can never match an
        // existing AdAssetEntity, so publishCreativeFromAsset would always 404 without this step.
        // uploadAdImageFromStoredAsset's pageName param is matched by name (pageRepository
        // .findByNameAndUser), not by Facebook page id — formVal.pageId is the id, so it must be
        // this.selectedPage's name instead.
        : this.creativeService
            .uploadAdImageFromStoredAsset(adAccountId, asset.id, this.selectedVariantKey, this.selectedPage?.name)
            .pipe(
              switchMap((uploadRes: any) => {
                const uploadedHash = uploadRes?.data?.imageHash ?? uploadRes?.imageHash;
                return this.creativeService.publishCreativeFromAsset(adAccountId, platform, {
                  ...body,
                  imageHash: uploadedHash,
                });
              }),
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
          this.ngZone.run(() => this.handleError(err, 'Failed to create creative'));
        },
      });
  }
}
