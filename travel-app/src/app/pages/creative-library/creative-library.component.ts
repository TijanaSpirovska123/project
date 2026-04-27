import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { finalize, switchMap } from 'rxjs/operators';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { AssetLibraryService } from '../../services/asset/asset-library.service';
import { CreativeService } from '../../services/ad-creative/creative.service';
import { PageService } from '../../services/ad-creative/page.service';
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { StoredAssetDto, StoredAssetVariantDto } from '../../models/adset/adset.model';
import { PageDto, PagePostDto } from '../../models/ad-creative/page.model';
import { formatFileSize } from '../../utils/format.util';

@Component({
  selector: 'app-creative-library',
  standalone: false,
  templateUrl: './creative-library.component.html',
  styleUrls: ['./creative-library.component.scss'],
})
export class CreativeLibraryComponent implements OnInit, OnDestroy {
  /** When true, renders as an embedded card (no page wrapper / sidebar). */
  @Input() embeddedMode = false;
  /** Emits when the user clicks X in embedded mode. */
  @Output() closePanel = new EventEmitter<void>();

  assets: StoredAssetDto[] = [];
  filteredAssets: StoredAssetDto[] = [];
  isLoading = false;
  isUploading = false;

  // Filters
  filterType: 'ALL' | 'IMAGE' | 'VIDEO' = 'ALL';
  filterStatus: 'ALL' | 'READY' | 'PROCESSING' | 'FAILED' = 'ALL';
  filterPlatform: 'ALL' | 'META' | 'TIKTOK' | 'PINTEREST' | 'GOOGLE' = 'ALL';
  searchQuery = '';

  readonly platformFilters = [
    { key: 'ALL', label: 'All' },
    { key: 'META', label: 'Meta' },
    { key: 'TIKTOK', label: 'TikTok' },
    { key: 'PINTEREST', label: 'Pinterest' },
    { key: 'GOOGLE', label: 'Google Ads' },
  ] as const;

  readonly PLATFORM_VARIANT_PREFIXES: Record<string, string[]> = {
    META: ['META_'],
    TIKTOK: ['TIKTOK_'],
    PINTEREST: ['PIN_'],
    GOOGLE: ['GOOGLE_'],
  };

  // Upload
  isDragOver = false;

  // Detail modal
  selectedAsset: StoredAssetDto | null = null;
  isDetailModalOpen = false;
  selectedVariantKey = 'ORIGINAL';

  // Platform push modal
  isPlatformModalOpen = false;
  platformAsset: StoredAssetDto | null = null;
  selectedPlatforms: Set<string> = new Set();

  // Variant picker modal (step 2 after platform selection)
  isVariantPickerOpen = false;
  variantPickerPlatform = '';
  readonly platforms = [
    { key: 'META', label: 'Meta', icon: 'facebook' },
  ];

  readonly META_VARIANTS = [
    { key: 'ORIGINAL', label: 'Original' },
    { key: 'META_SQUARE_1080', label: '1:1 Feed (1080×1080)' },
    { key: 'META_VERTICAL_1080', label: '4:5 Feed (1080×1350)' },
    { key: 'META_STORIES_1080', label: '9:16 Stories (1080×1920)' },
    { key: 'META_LANDSCAPE_1200', label: '1.91:1 Landscape (1200×628)' },
    { key: 'META_VIDEO_SQUARE', label: '1:1 Square (1080×1080)' },
    { key: 'META_VIDEO_VERTICAL', label: '4:5 Vertical (1080×1350)' },
    { key: 'META_VIDEO_LANDSCAPE', label: '1.91:1 Landscape (1200×628)' },
    { key: 'META_VIDEO_STORY', label: '9:16 Story/Reels (1080×1920)' },
  ];

  // Blob object URL cache
  private variantBlobCache = new Map<string, string>();
  private createdObjectUrls: string[] = [];

  actId: string | null = null;

  // Publish modal state
  isPublishModalOpen = false;
  publishAsset: StoredAssetDto | null = null;
  publishVariantKey = 'META_SQUARE_1080';
  publishCreativeName = '';
  publishLinkUrl = '';
  isPublishing = false;

  readonly VARIANT_LABELS: Record<string, string> = {
    ORIGINAL: 'Original',
    META_SQUARE_1080: '1:1 Feed (1080×1080)',
    META_VERTICAL_1080: '4:5 Feed (1080×1350)',
    META_STORIES_1080: '9:16 Stories (1080×1920)',
    META_LANDSCAPE_1200: '1.91:1 Landscape (1200×628)',
    META_VIDEO_SQUARE: '1:1 Square (1080×1080)',
    META_VIDEO_VERTICAL: '4:5 Vertical (1080×1350)',
    META_VIDEO_LANDSCAPE: '1.91:1 Landscape (1200×628)',
    META_VIDEO_STORY: '9:16 Story/Reels (1080×1920)',
  };

  // ── Create from Post ────────────────────────────────────────────────────────
  pages: PageDto[] = [];
  isLoadingPages = false;
  selectedPage: PageDto | null = null;

  posts: PagePostDto[] = [];
  isLoadingPosts = false;
  selectedPost: PagePostDto | null = null;

  isCreateFromPostOpen = false;
  createFromPostName = '';
  createFromPostLinkUrl = '';
  isCreatingFromPost = false;

  constructor(
    private readonly assetService: AssetLibraryService,
    private readonly creativeService: CreativeService,
    private readonly pageService: PageService,
    private readonly authStore: AuthStoreService,
    private readonly toastr: AppToastrService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.actId = this.authStore.getActId();
    this.loadAssets();
    this.loadPages();
  }

  ngOnDestroy(): void {
    this.createdObjectUrls.forEach((url) => URL.revokeObjectURL(url));
  }

  loadAssets(): void {
    this.isLoading = true;
    this.assetService
      .list()
      .pipe(
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (res: any) => {
          this.assets = res?.data ?? [];
          this.applyFilters();
          this.preloadThumbnails();
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to load assets'));
          }
        },
      });
  }

  private preloadThumbnails(): void {
    this.assets
      .filter((a) => a.assetType === 'IMAGE' && a.status === 'READY')
      .forEach((asset) => {
        const variant = asset.variants.some(
          (v) => v.variantKey === 'META_SQUARE_1080',
        )
          ? 'META_SQUARE_1080'
          : 'ORIGINAL';
        this.fetchAndCacheBlob(asset.id, variant);
      });
  }

  private fetchAndCacheBlob(assetId: number, variantKey: string): void {
    const key = `${assetId}_${variantKey}`;
    if (this.variantBlobCache.has(key)) return;
    this.assetService.fetchVariantBlob(assetId, variantKey).subscribe({
      next: (objectUrl) => {
        this.createdObjectUrls.push(objectUrl);
        this.variantBlobCache.set(key, objectUrl);
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  getThumbUrl(asset: StoredAssetDto): string | undefined {
    const variant = asset.variants.some(
      (v) => v.variantKey === 'META_SQUARE_1080',
    )
      ? 'META_SQUARE_1080'
      : 'ORIGINAL';
    return this.variantBlobCache.get(`${asset.id}_${variant}`);
  }

  getModalVariantUrl(assetId: number, variantKey: string): string | undefined {
    const key = `${assetId}_${variantKey}`;
    if (!this.variantBlobCache.has(key)) {
      this.fetchAndCacheBlob(assetId, variantKey);
    }
    return this.variantBlobCache.get(key);
  }

  applyFilters(): void {
    let result = [...this.assets];
    if (this.filterType !== 'ALL')
      result = result.filter((a) => a.assetType === this.filterType);
    if (this.filterStatus !== 'ALL')
      result = result.filter((a) => a.status === this.filterStatus);
    if (this.filterPlatform !== 'ALL') {
      const prefixes = this.PLATFORM_VARIANT_PREFIXES[this.filterPlatform] ?? [];
      result = result.filter((a) =>
        a.variants.some((v) => prefixes.some((p) => v.variantKey.startsWith(p)))
      );
    }
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter((a) =>
        a.originalFilename.toLowerCase().includes(q),
      );
    }
    this.filteredAssets = result;
    this.cdr.detectChanges();
  }

  setPlatformFilter(key: 'ALL' | 'META' | 'TIKTOK' | 'PINTEREST' | 'GOOGLE'): void {
    this.filterPlatform = key;
    this.applyFilters();
  }

  clearFilters(): void {
    this.filterType = 'ALL';
    this.filterStatus = 'ALL';
    this.filterPlatform = 'ALL';
    this.searchQuery = '';
    this.applyFilters();
  }

  openUploadDialog(): void {
    document.getElementById('fileUploadInput')?.click();
  }

  getVariantCountForPlatform(asset: StoredAssetDto): number {
    if (this.filterPlatform === 'ALL') return asset.variants.length;
    const prefixes = this.PLATFORM_VARIANT_PREFIXES[this.filterPlatform] ?? [];
    return asset.variants.filter((v) =>
      prefixes.some((p) => v.variantKey.startsWith(p))
    ).length;
  }

  deleteAsset(asset: StoredAssetDto, event?: Event): void {
    event?.stopPropagation();
    if (!confirm(`Delete "${asset.originalFilename}"? This cannot be undone.`)) return;
    this.assetService.deleteById(String(asset.id)).subscribe({
      next: () => {
        this.assets = this.assets.filter((a) => a.id !== asset.id);
        this.applyFilters();
        this.toastr.success('Asset deleted');
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to delete asset'));
        }
      },
    });
  }

  // ── Upload ──────────────────────────────────────────────────────────────────

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.uploadFile(input.files[0]);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.uploadFile(file);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(): void {
    this.isDragOver = false;
  }

  uploadFile(file: File): void {
    const isVideo = file.type.startsWith('video/');

    if (isVideo) {
      this.uploadVideoFile(file);
    } else {
      this.uploadImageFile(file);
    }
  }

  private uploadImageFile(file: File): void {
    this.isUploading = true;
    this.assetService
      .upload(file)
      .pipe(
        finalize(() => {
          this.isUploading = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (res: any) => {
          const uploaded: StoredAssetDto = res?.data;
          if (uploaded) {
            this.assets.unshift(uploaded);
            this.applyFilters();
            if (uploaded.assetType === 'IMAGE' && uploaded.status === 'READY') {
              const variant = uploaded.variants.some(
                (v) => v.variantKey === 'META_SQUARE_1080',
              )
                ? 'META_SQUARE_1080'
                : 'ORIGINAL';
              this.fetchAndCacheBlob(uploaded.id, variant);
            }
            this.toastr.success('Asset uploaded successfully');
          }
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Upload failed'));
          }
        },
      });
  }

  private uploadVideoFile(file: File): void {
    this.isUploading = true;
    const formData = new FormData();
    formData.append('file', file);
    this.assetService
      .uploadVideo(formData)
      .pipe(
        finalize(() => {
          this.isUploading = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (res: any) => {
          const uploaded: StoredAssetDto = res?.data ?? res;
          if (uploaded) {
            this.assets.unshift(uploaded);
            this.applyFilters();
            this.toastr.info('Video uploaded. Processing variants in the background...');
            this.pollAssetStatus(uploaded.id);
          }
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Video upload failed'));
          }
        },
      });
  }

  pollAssetStatus(assetId: number): void {
    const pollInterval = setInterval(() => {
      this.assetService.getAssetStatus(assetId).subscribe({
        next: (status) => {
          if (status.status === 'READY') {
            clearInterval(pollInterval);
            this.assetService.getById(assetId).subscribe(updated => {
              const idx = this.assets.findIndex(a => a.id === assetId);
              if (idx !== -1) {
                this.assets[idx] = updated;
                this.applyFilters();
                this.cdr.detectChanges();
              }
            });
            this.toastr.success('Video processed and ready to publish');
          } else if (status.status === 'FAILED') {
            clearInterval(pollInterval);
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error('Video processing failed. Please try again.');
            }
          }
        },
        error: () => clearInterval(pollInterval),
      });
    }, 5000);
    // Stop polling after 10 minutes
    setTimeout(() => clearInterval(pollInterval), 10 * 60 * 1000);
  }

  // ── Detail Modal ────────────────────────────────────────────────────────────

  openDetail(asset: StoredAssetDto): void {
    this.selectedAsset = asset;
    this.selectedVariantKey = 'ORIGINAL';
    this.isDetailModalOpen = true;
  }

  closeDetail(): void {
    this.isDetailModalOpen = false;
    this.selectedAsset = null;
  }

  getVariantsForAsset(asset: StoredAssetDto): typeof this.META_VARIANTS {
    return this.META_VARIANTS.filter((mv) =>
      asset.variants.some((v) => v.variantKey === mv.key),
    );
  }

  getVariantDownloadUrl(assetId: number, variantKey: string): string {
    return this.assetService.getVariantDownloadUrl(assetId, variantKey);
  }

  getVariantLabel(key: string): string {
    return this.VARIANT_LABELS[key] ?? key;
  }

  // ── Publish to Meta ─────────────────────────────────────────────────────────

  openPublishModal(asset: StoredAssetDto, event?: Event): void {
    event?.stopPropagation();
    this.publishAsset = asset;
    const preferredImage = [
      'META_SQUARE_1080',
      'META_VERTICAL_1080',
      'META_STORIES_1080',
      'META_LANDSCAPE_1200',
      'ORIGINAL',
    ];
    const preferredVideo = [
      'META_VIDEO_SQUARE',
      'META_VIDEO_VERTICAL',
      'META_VIDEO_LANDSCAPE',
      'META_VIDEO_STORY',
      'ORIGINAL',
    ];
    const preferred = asset.assetType === 'VIDEO' ? preferredVideo : preferredImage;
    const available = asset.variants.map((v) => v.variantKey);
    this.publishVariantKey =
      preferred.find((k) => available.includes(k)) ??
      available[0] ??
      'ORIGINAL';
    this.publishCreativeName = '';
    this.publishLinkUrl = '';
    this.isPublishModalOpen = true;
  }

  closePublishModal(): void {
    this.isPublishModalOpen = false;
    this.isVariantPickerOpen = false;
    this.publishAsset = null;
    this.isPublishing = false;
    this.cdr.detectChanges();
  }

  publishToMeta(): void {
    if (!this.publishAsset || !this.actId) return;
    this.isPublishing = true;

    const adAccountId = this.actId.startsWith('act_')
      ? this.actId
      : `${this.actId}`;

    const isVideo = this.publishAsset.assetType === 'VIDEO';
    const body = {
      name: this.publishCreativeName || undefined,
      linkUrl: this.publishLinkUrl || undefined,
    };

    if (isVideo) {
      // For VIDEO: skip image upload, go directly to createCreativeFromStoredAsset
      // The backend handles Meta video upload internally
      this.creativeService
        .createCreativeFromStoredAsset(
          this.publishAsset.id,
          this.publishVariantKey,
          'META',
          adAccountId,
          body,
        )
        .pipe(
          finalize(() => {
            this.isPublishing = false;
            this.cdr.detectChanges();
          }),
        )
        .subscribe({
          next: () => {
            this.toastr.success('Video creative published to Meta successfully');
            const pushedAssetId = this.publishAsset?.id;
            this.closePublishModal();
            if (this.embeddedMode) {
              this.closePanel.emit();
            } else if (pushedAssetId) {
              this.refreshAsset(pushedAssetId);
            }
          },
          error: (err: any) => {
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to publish video to Meta'));
            }
          },
        });
    } else {
      // IMAGE: existing flow unchanged
      this.creativeService
        .uploadAdImageFromStoredAsset(
          adAccountId,
          this.publishAsset.id,
          this.publishVariantKey,
        )
        .pipe(
          switchMap(() => {
            return this.creativeService.createCreativeFromStoredAsset(
              this.publishAsset!.id,
              this.publishVariantKey,
              'META',
              adAccountId,
              body,
            );
          }),
          finalize(() => {
            this.isPublishing = false;
            this.cdr.detectChanges();
          }),
        )
        .subscribe({
          next: () => {
            this.toastr.success('Creative published to Meta successfully');
            const pushedAssetId = this.publishAsset?.id;
            this.closePublishModal();
            if (this.embeddedMode) {
              this.closePanel.emit();
            } else if (pushedAssetId) {
              this.refreshAsset(pushedAssetId);
            }
          },
          error: (err: any) => {
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to publish to Meta'));
            }
          },
        });
    }
  }

  // ── Platform Modal ──────────────────────────────────────────────────────────

  openPlatformModal(asset: StoredAssetDto, event?: Event): void {
    event?.stopPropagation();
    this.platformAsset = asset;
    this.selectedPlatforms.clear();
    this.isPlatformModalOpen = true;
  }

  closePlatformModal(): void {
    this.isPlatformModalOpen = false;
    this.platformAsset = null;
  }

  togglePlatform(key: string): void {
    if (this.selectedPlatforms.has(key)) this.selectedPlatforms.delete(key);
    else this.selectedPlatforms.add(key);
  }

  pushToPlatforms(): void {
    if (this.selectedPlatforms.has('META') && this.platformAsset) {
      const asset = this.platformAsset;
      this.closePlatformModal();
      this.openVariantPicker(asset, 'META');
      return;
    }
    const names = Array.from(this.selectedPlatforms).join(', ');
    this.toastr.info(`Push to ${names} — coming soon`);
    this.closePlatformModal();
  }

  openVariantPicker(asset: StoredAssetDto, platform: string): void {
    this.publishAsset = asset;
    this.variantPickerPlatform = platform;
    const preferredImage = ['META_SQUARE_1080', 'META_VERTICAL_1080', 'META_STORIES_1080', 'META_LANDSCAPE_1200', 'ORIGINAL'];
    const preferredVideo = ['META_VIDEO_SQUARE', 'META_VIDEO_VERTICAL', 'META_VIDEO_LANDSCAPE', 'META_VIDEO_STORY', 'ORIGINAL'];
    const preferred = asset.assetType === 'VIDEO' ? preferredVideo : preferredImage;
    const available = asset.variants.map(v => v.variantKey);
    this.publishVariantKey = preferred.find(k => available.includes(k)) ?? available[0] ?? 'ORIGINAL';
    this.publishCreativeName = '';
    this.publishLinkUrl = '';
    if (asset.assetType === 'IMAGE') {
      asset.variants.forEach(v => this.fetchAndCacheBlob(asset.id, v.variantKey));
    }
    this.isVariantPickerOpen = true;
  }

  closeVariantPicker(): void {
    this.isVariantPickerOpen = false;
    this.publishAsset = null;
    this.isPublishing = false;
  }

  // ── Create from Post ────────────────────────────────────────────────────────

  loadPages(): void {
    this.isLoadingPages = true;
    this.pageService.getAll().subscribe({
      next: (res: any) => {
        this.pages = Array.isArray(res) ? res : (res?.data ?? []);
        this.isLoadingPages = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoadingPages = false;
        this.cdr.detectChanges();
      },
    });
  }

  onPageChange(page: PageDto | null): void {
    this.selectedPost = null;
    this.posts = [];
    if (!page) return;
    this.isLoadingPosts = true;
    this.pageService.getPostsByPageName(page.name).subscribe({
      next: (res: any) => {
        this.posts = Array.isArray(res) ? res : (res?.data ?? []);
        this.isLoadingPosts = false;
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to load posts'));
        }
        this.isLoadingPosts = false;
        this.cdr.detectChanges();
      },
    });
  }

  onPageSelectChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const page = this.pages.find((p) => p.name === select.value) ?? null;
    this.selectedPage = page;
    this.onPageChange(page);
  }

  onPostSelectChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.selectedPost =
      this.posts.find((p) => p.postId === select.value) ?? null;
  }

  openCreateFromPost(): void {
    this.selectedPage = null;
    this.selectedPost = null;
    this.posts = [];
    this.createFromPostName = '';
    this.createFromPostLinkUrl = '';
    this.isCreatingFromPost = false;
    this.isCreateFromPostOpen = true;
  }

  closeCreateFromPost(): void {
    this.isCreateFromPostOpen = false;
    this.selectedPage = null;
    this.selectedPost = null;
    this.posts = [];
    this.createFromPostName = '';
    this.createFromPostLinkUrl = '';
    this.isCreatingFromPost = false;
  }

  createFromPost(): void {
    if (!this.selectedPage || !this.selectedPost) {
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Please select a page and a post');
      }
      return;
    }
    this.isCreatingFromPost = true;
    this.creativeService
      .createCreativeFromPost(
        this.selectedPost.postId,
        'META',
        this.selectedPage.name,
        {
          name: this.createFromPostName || undefined,
          linkUrl: this.createFromPostLinkUrl || undefined,
        },
      )
      .subscribe({
        next: () => {
          setTimeout(() => {
            this.toastr.success('Ad creative created successfully!');
            this.closeCreateFromPost();
            if (this.embeddedMode) {
              this.closePanel.emit();
            } else {
              this.loadAssets();
            }
          });
        },
        error: (err: any) => {
          setTimeout(() => {
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to create creative'));
            }
            this.isCreatingFromPost = false;
          });
        },
      });
  }

  truncatePostId(postId: string): string {
    if (postId.length <= 30) return postId;
    return postId.substring(0, 14) + '…' + postId.substring(postId.length - 14);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  formatFileSize(bytes: number): string {
    return formatFileSize(bytes);
  }

  isImage(asset: StoredAssetDto): boolean {
    return asset.assetType === 'IMAGE';
  }

  isVideo(asset: StoredAssetDto): boolean {
    return asset.assetType === 'VIDEO';
  }

  formatDuration(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  filterByType(type: 'ALL' | 'IMAGE' | 'VIDEO'): void {
    this.filterType = type;
    this.applyFilters();
  }

  trackById(_: number, a: StoredAssetDto): number {
    return a.id;
  }

  // ── Meta upload status helpers ───────────────────────────────────────────────

  hasMetaUpload(asset: StoredAssetDto): boolean {
    return (asset.variants ?? []).some(v =>
      (v.metaImageHash != null && v.metaImageHash !== '') ||
      (v.metaVideoId != null && v.metaVideoId !== '')
    );
  }

  metaUploadCount(asset: StoredAssetDto): { uploaded: number; total: number; allUploaded: boolean } {
    const variants = asset.variants ?? [];
    const total = variants.length;
    const uploaded = variants.filter(v =>
      (v.metaImageHash != null && v.metaImageHash !== '') ||
      (v.metaVideoId != null && v.metaVideoId !== '')
    ).length;
    return { uploaded, total, allUploaded: uploaded === total && total > 0 };
  }

  isVariantUploaded(variant: StoredAssetVariantDto): boolean {
    return (variant.metaImageHash != null && variant.metaImageHash !== '') ||
           (variant.metaVideoId != null && variant.metaVideoId !== '');
  }

  // ── Refresh single asset after push ─────────────────────────────────────────

  private refreshAsset(assetId: number): void {
    this.assetService.getById(assetId).subscribe({
      next: (updated) => {
        const idx = this.assets.findIndex(a => a.id === assetId);
        if (idx !== -1) {
          this.assets[idx] = updated;
          this.applyFilters();
          this.cdr.detectChanges();
        }
      },
      error: () => {},
    });
  }
}

