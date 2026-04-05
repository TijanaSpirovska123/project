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

interface StoredAssetVariantDto {
  id: number;
  variantKey: string;
  bucket: string;
  objectKey: string;
  width: number | null;
  height: number | null;
  createdAt: string;
  updatedAt: string;
}

interface StoredAssetDto {
  id: number;
  userId: number;
  assetType: 'IMAGE' | 'VIDEO';
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  hash: string;
  status: 'PROCESSING' | 'READY' | 'FAILED';
  createdAt: string;
  updatedAt: string;
  variants: StoredAssetVariantDto[];
}

interface PageDto {
  id: number;
  pageId: string;
  name: string;
}

interface PagePostDto {
  id: number;
  postId: string;
  permalinkUrl: string;
}

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
  readonly platforms = [
    { key: 'META', label: 'Meta', icon: 'facebook' },
    { key: 'TIKTOK', label: 'TikTok', icon: 'music_video' },
    { key: 'PINTEREST', label: 'Pinterest', icon: 'push_pin' },
    { key: 'GOOGLE', label: 'Google Ads', icon: 'ads_click' },
  ];

  readonly META_VARIANTS = [
    { key: 'ORIGINAL', label: 'Original' },
    { key: 'META_SQUARE_1080', label: '1:1 Feed (1080×1080)' },
    { key: 'META_VERTICAL_1080', label: '4:5 Feed (1080×1350)' },
    { key: 'META_STORIES_1080', label: '9:16 Stories (1080×1920)' },
    { key: 'META_LANDSCAPE_1200', label: '1.91:1 Landscape (1200×628)' },
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
        error: (err: any) => this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to load assets')),
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
        this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to delete asset'));
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
          this.toastr.error(CoreService.extractErrorMessage(err, 'Upload failed'));
        },
      });
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
    const preferred = [
      'META_SQUARE_1080',
      'META_VERTICAL_1080',
      'META_STORIES_1080',
      'META_LANDSCAPE_1200',
      'ORIGINAL',
    ];
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
    this.publishAsset = null;
    this.isPublishing = false;
  }

  publishToMeta(): void {
    if (!this.publishAsset || !this.actId) return;
    this.isPublishing = true;

    const adAccountId = this.actId.startsWith('act_')
      ? this.actId
      : `${this.actId}`;

    this.creativeService
      .uploadAdImageFromStoredAsset(
        adAccountId,
        this.publishAsset.id,
        this.publishVariantKey,
      )
      .pipe(
        switchMap(() => {
          const body = {
            name: this.publishCreativeName || undefined,
            linkUrl: this.publishLinkUrl || undefined,
          };
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
          this.closePublishModal();
          if (this.embeddedMode) {
            this.closePanel.emit();
          } else {
            this.loadAssets();
          }
        },
        error: (err: any) => {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to publish to Meta'));
        },
      });
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
      this.openPublishModal(asset);
      return;
    }
    const names = Array.from(this.selectedPlatforms).join(', ');
    this.toastr.info(`Push to ${names} — coming soon`);
    this.closePlatformModal();
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
        this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to load posts'));
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
      this.toastr.error('Please select a page and a post');
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
            this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to create creative'));
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
    if (bytes >= 1_000_000) return (bytes / 1_000_000).toFixed(1) + ' MB';
    if (bytes >= 1_000) return (bytes / 1_000).toFixed(0) + ' KB';
    return bytes + ' B';
  }

  isImage(asset: StoredAssetDto): boolean {
    return asset.assetType === 'IMAGE';
  }

  trackById(_: number, a: StoredAssetDto): number {
    return a.id;
  }
}
