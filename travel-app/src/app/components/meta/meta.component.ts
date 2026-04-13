import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { finalize } from 'rxjs/operators';
import {
  CAMPAIGN_COLUMNS,
  ADSET_COLUMNS,
  AD_COLUMNS,
} from '../../data/meta-column-config';

export interface MetaColumnDef {
  key: string;
  label: string;
  enabled: boolean;
  order: number;
  category: 'key_metrics' | 'tracking' | 'ad_settings' | 'advanced' | 'custom';
  parentSection?: string;
  section: string;
  alwaysVisible?: boolean;
  width?: string;
  isConversionEvent?: boolean;
}

function deepClone<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj));
}
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { AdSetService } from '../../services/adset/adset.service';
import { AdService } from '../../services/ad/ad.service';
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { UserConfigService } from '../../services/user-config/user-config.service';
import { Provider } from '../../data/provider/provider.enum';
import { CampaignStatus } from '../../data/campaign/campaign-status';
import { Campaign } from '../../models/campaign/campaign';
import { AdSetResponse, AdResponse } from '../../models/adset/adset.model';
import { TableData } from '../../data/table/table-data.model';
import { TableHeader } from '../../data/table/table-header.model';
import { TableButton } from '../../data/table/table-button.model';
import {
  AnalyticsCard,
  CampaignMetrics,
  DeviceBreakdown,
  AgeBreakdown,
  PerformanceData,
} from '../../models/analytics/analytics.model';

// ─── Standard events list (used for modal conversion-table rendering) ─────────
const SE_LIST: { id: string; label: string }[] = [
  { id: 'achievements_unlocked', label: 'Achievements unlocked' },
  { id: 'adds_of_payment_info', label: 'Adds of payment info' },
  { id: 'adds_to_cart', label: 'Adds to cart' },
  { id: 'adds_to_wishlist', label: 'Adds to wishlist' },
  { id: 'app_activations', label: 'App activations' },
  { id: 'app_installs', label: 'App installs' },
  { id: 'applications_submitted', label: 'Applications submitted' },
  { id: 'appointments_scheduled', label: 'Appointments scheduled' },
  { id: 'checkouts_initiated', label: 'Checkouts initiated' },
  { id: 'contacts', label: 'Contacts' },
  { id: 'content_views', label: 'Content views' },
  { id: 'credit_spends', label: 'Credit spends' },
  { id: 'custom_events', label: 'Custom events' },
  { id: 'desktop_app_engagements', label: 'Desktop app engagements' },
  {
    id: 'desktop_app_story_engagements',
    label: 'Desktop app story engagements',
  },
  { id: 'desktop_app_uses', label: 'Desktop app uses' },
  { id: 'direct_website_purchases', label: 'Direct website purchases' },
  {
    id: 'direct_website_purchases_conversion_value',
    label: 'Direct website purchases (conversion value)',
  },
  { id: 'donation_roas', label: 'Donation ROAS' },
  { id: 'donations', label: 'Donations' },
  { id: 'game_plays', label: 'Game plays' },
  { id: 'get_directions_clicks', label: 'Get directions clicks' },
  { id: 'in_app_ad_clicks', label: 'In-app ad clicks' },
  { id: 'in_app_ad_impressions', label: 'In-app ad impressions' },
  { id: 'landing_page_views_conv', label: 'Landing page views' },
  { id: 'leads', label: 'Leads' },
  { id: 'levels_achieved', label: 'Levels achieved' },
  { id: 'location_searches', label: 'Location searches' },
  { id: 'meta_workflow_completions', label: 'Meta workflow completions' },
  { id: 'mobile_app_d2_retention', label: 'Mobile app D2 retention' },
  { id: 'mobile_app_d7_retention', label: 'Mobile app D7 retention' },
  { id: 'on_fb_message_to_buy', label: 'On Facebook message to buy' },
  { id: 'orders_created', label: 'Orders created' },
  { id: 'orders_dispatched', label: 'Orders dispatched' },
  { id: 'other_offline_conversions', label: 'Other offline conversions' },
  { id: 'phone_number_clicks', label: 'Phone number clicks' },
  { id: 'products_customised', label: 'Products customised' },
  { id: 'purchase_roas', label: 'Purchase ROAS' },
  { id: 'purchases', label: 'Purchases' },
  { id: 'ratings_submitted', label: 'Ratings submitted' },
  { id: 'registrations_completed', label: 'Registrations completed' },
  { id: 'searches', label: 'Searches' },
  { id: 'shops_assisted_purchases', label: 'Shops assisted purchases' },
  {
    id: 'shops_assisted_purchases_conv_value',
    label: 'Shops assisted purchases (conversion value)',
  },
  { id: 'subscriptions', label: 'Subscriptions' },
  { id: 'trials_started', label: 'Trials started' },
  { id: 'tutorials_completed', label: 'Tutorials completed' },
];

const SE_COLS: MetaColumnDef[] = SE_LIST.flatMap((e, i) => [
  {
    key: `conv_${e.id}_total`,
    label: `${e.label} (total)`,
    enabled: false,
    order: 70 + i * 3,
    category: 'key_metrics' as const,
    section: 'Standard events',
  },
  {
    key: `conv_${e.id}_value`,
    label: `${e.label} (value)`,
    enabled: false,
    order: 70 + i * 3 + 1,
    category: 'key_metrics' as const,
    section: 'Standard events',
  },
  {
    key: `conv_${e.id}_cost`,
    label: `${e.label} (cost)`,
    enabled: false,
    order: 70 + i * 3 + 2,
    category: 'key_metrics' as const,
    section: 'Standard events',
  },
]);

// ─── Shared key-metrics columns (identical for Campaigns, Ad Sets, and Ads) ──
const KM_COLS: MetaColumnDef[] = [
  // Results
  {
    key: 'results',
    label: 'Results',
    enabled: true,
    order: 3,
    category: 'key_metrics',
    section: 'Results',
  },
  {
    key: 'cost_per_result',
    label: 'Cost per result',
    enabled: true,
    order: 4,
    category: 'key_metrics',
    section: 'Results',
  },
  {
    key: 'result_rate',
    label: 'Result rate',
    enabled: false,
    order: 5,
    category: 'key_metrics',
    section: 'Results',
  },
  {
    key: 'results_roas',
    label: 'Results ROAS',
    enabled: false,
    order: 6,
    category: 'key_metrics',
    section: 'Results',
  },
  {
    key: 'results_value',
    label: 'Results value',
    enabled: false,
    order: 7,
    category: 'key_metrics',
    section: 'Results',
  },
  // Spend
  {
    key: 'spend',
    label: 'Amount spent',
    enabled: true,
    order: 8,
    category: 'key_metrics',
    section: 'Spend',
  },
  {
    key: 'spend_pct',
    label: 'Amount spent percentage',
    enabled: false,
    order: 9,
    category: 'key_metrics',
    section: 'Spend',
  },
  // Impressions (Distribution)
  {
    key: 'impressions',
    label: 'Impressions',
    enabled: true,
    order: 10,
    category: 'key_metrics',
    section: 'Impressions',
  },
  {
    key: 'reach',
    label: 'Reach',
    enabled: true,
    order: 11,
    category: 'key_metrics',
    section: 'Impressions',
  },
  {
    key: 'frequency',
    label: 'Frequency',
    enabled: false,
    order: 12,
    category: 'key_metrics',
    section: 'Impressions',
  },
  {
    key: 'cpm',
    label: 'CPM (cost per 1,000 impressions)',
    enabled: false,
    order: 13,
    category: 'key_metrics',
    section: 'Impressions',
  },
  {
    key: 'cpp',
    label: 'Cost per 1,000 accounts reached',
    enabled: false,
    order: 14,
    category: 'key_metrics',
    section: 'Impressions',
  },
  // Views (Distribution)
  {
    key: 'viewers',
    label: 'Viewers',
    enabled: false,
    order: 15,
    category: 'key_metrics',
    section: 'Views',
  },
  {
    key: 'views',
    label: 'Views',
    enabled: false,
    order: 16,
    category: 'key_metrics',
    section: 'Views',
  },
  // Media (Awareness)
  {
    key: 'video_plays_2s',
    label: '2-second continuous video plays',
    enabled: false,
    order: 17,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'video_plays_3s',
    label: '3-second video plays',
    enabled: false,
    order: 18,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'cost_per_2s_video_play',
    label: 'Cost per 2-second continuous video play',
    enabled: false,
    order: 19,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'cost_per_3s_video_play',
    label: 'Cost per 3-second video play',
    enabled: false,
    order: 20,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'video_plays',
    label: 'Video plays',
    enabled: false,
    order: 21,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'thruplay_views',
    label: 'ThruPlays',
    enabled: false,
    order: 22,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'cost_per_thruplay',
    label: 'Cost per ThruPlay',
    enabled: false,
    order: 23,
    category: 'key_metrics',
    section: 'Media',
  },
  {
    key: 'unique_2s_video_plays',
    label: 'Unique 2-second continuous video plays',
    enabled: false,
    order: 24,
    category: 'key_metrics',
    section: 'Media',
  },
  // Clicks (Engagement)
  {
    key: 'clicks_all',
    label: 'Clicks (all)',
    enabled: false,
    order: 25,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'cpc_all',
    label: 'CPC (all)',
    enabled: false,
    order: 26,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'cpc_link',
    label: 'CPC (cost per link click)',
    enabled: false,
    order: 27,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'ctr_all',
    label: 'CTR (all)',
    enabled: false,
    order: 28,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'ctr_link',
    label: 'CTR (link click-through rate)',
    enabled: false,
    order: 29,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'cost_per_unique_click',
    label: 'Cost per unique click (all)',
    enabled: false,
    order: 30,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'cost_per_unique_link_click',
    label: 'Cost per unique link click',
    enabled: false,
    order: 31,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'cost_per_outbound_click',
    label: 'Cost per outbound click',
    enabled: false,
    order: 32,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'cost_per_unique_outbound',
    label: 'Cost per unique outbound click',
    enabled: false,
    order: 33,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'link_clicks',
    label: 'Link clicks',
    enabled: false,
    order: 34,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'outbound_clicks',
    label: 'Outbound clicks',
    enabled: false,
    order: 35,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'outbound_ctr',
    label: 'Outbound CTR (click-through rate)',
    enabled: false,
    order: 36,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'photo_clicks',
    label: 'Photo clicks',
    enabled: false,
    order: 37,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'shop_clicks',
    label: 'Shop clicks',
    enabled: false,
    order: 38,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'unique_clicks_all',
    label: 'Unique clicks (all)',
    enabled: false,
    order: 39,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'unique_ctr_all',
    label: 'Unique CTR (all)',
    enabled: false,
    order: 40,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'unique_ctr_link',
    label: 'Unique CTR (link click-through rate)',
    enabled: false,
    order: 41,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'unique_link_clicks',
    label: 'Unique link clicks',
    enabled: false,
    order: 42,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'unique_outbound_clicks',
    label: 'Unique outbound clicks',
    enabled: false,
    order: 43,
    category: 'key_metrics',
    section: 'Clicks',
  },
  {
    key: 'unique_outbound_ctr',
    label: 'Unique outbound CTR (click-through rate)',
    enabled: false,
    order: 44,
    category: 'key_metrics',
    section: 'Clicks',
  },
  // Traffic (Engagement)
  {
    key: 'landing_page_views',
    label: 'Website landing page views',
    enabled: false,
    order: 45,
    category: 'key_metrics',
    section: 'Traffic',
  },
  {
    key: 'cost_per_landing_page_view',
    label: 'Cost per landing page view',
    enabled: false,
    order: 46,
    category: 'key_metrics',
    section: 'Traffic',
  },
  {
    key: 'ig_profile_visits',
    label: 'Instagram profile visits',
    enabled: false,
    order: 47,
    category: 'key_metrics',
    section: 'Traffic',
  },
  // Follows & likes (Engagement)
  {
    key: 'fb_likes',
    label: 'Facebook likes',
    enabled: false,
    order: 48,
    category: 'key_metrics',
    section: 'Follows & likes',
  },
  {
    key: 'cost_per_like',
    label: 'Cost per like',
    enabled: false,
    order: 49,
    category: 'key_metrics',
    section: 'Follows & likes',
  },
  {
    key: 'ig_follows',
    label: 'Instagram follows',
    enabled: false,
    order: 50,
    category: 'key_metrics',
    section: 'Follows & likes',
  },
  // Engagement (Engagement)
  {
    key: 'post_engagements',
    label: 'Post engagements',
    enabled: false,
    order: 51,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'page_engagement',
    label: 'Page engagement',
    enabled: false,
    order: 52,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'cost_per_post_engagement',
    label: 'Cost per post engagement',
    enabled: false,
    order: 53,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'cost_per_page_engagement',
    label: 'Cost per Page engagement',
    enabled: false,
    order: 54,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'post_reactions',
    label: 'Post reactions',
    enabled: false,
    order: 55,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'post_comments',
    label: 'Post comments',
    enabled: false,
    order: 56,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'post_shares',
    label: 'Post shares',
    enabled: false,
    order: 57,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'post_saves',
    label: 'Post saves',
    enabled: false,
    order: 58,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'check_ins',
    label: 'Check-ins',
    enabled: false,
    order: 59,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'event_responses',
    label: 'Event responses',
    enabled: false,
    order: 60,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'cost_per_event_response',
    label: 'Cost per event response',
    enabled: false,
    order: 61,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'join_group_requests',
    label: 'Join group requests',
    enabled: false,
    order: 62,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'cost_per_join_group',
    label: 'Cost per join group request',
    enabled: false,
    order: 63,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'effect_share',
    label: 'Effect share',
    enabled: false,
    order: 64,
    category: 'key_metrics',
    section: 'Engagement',
  },
  {
    key: 'net_reminders_on',
    label: 'Net reminders on',
    enabled: false,
    order: 65,
    category: 'key_metrics',
    section: 'Engagement',
  },
  // Messaging (orders 500+ to leave room for SE_COLS at 70–207)
  {
    key: 'msg_conversations_started',
    label: 'Messaging conversations started',
    enabled: false,
    order: 500,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'cost_per_msg_convo',
    label: 'Cost per messaging conversation started',
    enabled: false,
    order: 501,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'msg_conversations_replied',
    label: 'Messaging conversations replied',
    enabled: false,
    order: 502,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'msg_contacts',
    label: 'Messaging contacts',
    enabled: false,
    order: 503,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'new_msg_contacts',
    label: 'New messaging contacts',
    enabled: false,
    order: 504,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'cost_per_new_msg_contact',
    label: 'Cost per new messaging contact',
    enabled: false,
    order: 505,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'returning_msg_contacts',
    label: 'Returning messaging contacts',
    enabled: false,
    order: 506,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'msg_subscriptions',
    label: 'Messaging subscriptions',
    enabled: false,
    order: 507,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'cost_per_msg_subscription',
    label: 'Cost per messaging subscription',
    enabled: false,
    order: 508,
    category: 'key_metrics',
    section: 'Messaging',
  },
  {
    key: 'welcome_msg_views',
    label: 'Welcome message views',
    enabled: false,
    order: 509,
    category: 'key_metrics',
    section: 'Messaging',
  },
  // Calling
  {
    key: 'callback_requests',
    label: 'Callback requests submitted',
    enabled: false,
    order: 510,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'phone_calls_placed',
    label: 'Phone calls placed',
    enabled: false,
    order: 511,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'messenger_calls_placed',
    label: 'Messenger calls placed',
    enabled: false,
    order: 512,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'calls_20s_messenger',
    label: '20-second Messenger calls',
    enabled: false,
    order: 513,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'calls_20s_phone',
    label: '20-second phone calls',
    enabled: false,
    order: 514,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'calls_60s_messenger',
    label: '60-second Messenger calls',
    enabled: false,
    order: 515,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'calls_60s_phone',
    label: '60-second phone calls',
    enabled: false,
    order: 516,
    category: 'key_metrics',
    section: 'Calling',
  },
  {
    key: 'blocks',
    label: 'Blocks',
    enabled: false,
    order: 517,
    category: 'key_metrics',
    section: 'Calling',
  },
  // Custom events (special placeholder field)
  {
    key: 'custom_events_input',
    label: 'Custom events',
    enabled: false,
    order: 700,
    category: 'key_metrics',
    section: 'Custom events',
  },
];

// ─── Tracking columns ─────────────────────────────────────────────────────────
const TRACKING_COLS: MetaColumnDef[] = [
  // Diagnostics — Ad relevance
  {
    key: 'quality_ranking',
    label: 'Quality ranking',
    enabled: false,
    order: 200,
    category: 'tracking',
    section: 'Diagnostics — Ad relevance',
  },
  {
    key: 'engagement_rate_ranking',
    label: 'Engagement rate ranking',
    enabled: false,
    order: 201,
    category: 'tracking',
    section: 'Diagnostics — Ad relevance',
  },
  {
    key: 'conversion_rate_ranking',
    label: 'Conversion rate ranking',
    enabled: false,
    order: 202,
    category: 'tracking',
    section: 'Diagnostics — Ad relevance',
  },
  // Messaging and calling
  {
    key: 'track_calls_20s_messenger',
    label: '20-second Messenger calls',
    enabled: false,
    order: 210,
    category: 'tracking',
    section: 'Messaging and calling',
  },
  {
    key: 'track_calls_20s_phone',
    label: '20-second phone calls',
    enabled: false,
    order: 211,
    category: 'tracking',
    section: 'Messaging and calling',
  },
  {
    key: 'track_calls_60s_messenger',
    label: '60-second Messenger calls',
    enabled: false,
    order: 212,
    category: 'tracking',
    section: 'Messaging and calling',
  },
  {
    key: 'track_calls_60s_phone',
    label: '60-second phone calls',
    enabled: false,
    order: 213,
    category: 'tracking',
    section: 'Messaging and calling',
  },
  {
    key: 'track_blocks',
    label: 'Blocks',
    enabled: false,
    order: 214,
    category: 'tracking',
    section: 'Messaging and calling',
  },
  // Media
  {
    key: 'track_video_p25',
    label: 'Video plays at 25%',
    enabled: false,
    order: 220,
    category: 'tracking',
    section: 'Media',
  },
  {
    key: 'track_video_p50',
    label: 'Video plays at 50%',
    enabled: false,
    order: 221,
    category: 'tracking',
    section: 'Media',
  },
  {
    key: 'track_video_p75',
    label: 'Video plays at 75%',
    enabled: false,
    order: 222,
    category: 'tracking',
    section: 'Media',
  },
  {
    key: 'track_video_p95',
    label: 'Video plays at 95%',
    enabled: false,
    order: 223,
    category: 'tracking',
    section: 'Media',
  },
  {
    key: 'track_video_p100',
    label: 'Video plays at 100%',
    enabled: false,
    order: 224,
    category: 'tracking',
    section: 'Media',
  },
  {
    key: 'track_video_avg_play_time',
    label: 'Video average play time',
    enabled: false,
    order: 225,
    category: 'tracking',
    section: 'Media',
  },
  {
    key: 'track_video_3s_rate',
    label: '3-second video plays rate per impressions',
    enabled: false,
    order: 226,
    category: 'tracking',
    section: 'Media',
  },
];

const CAMPAIGN_AD_SETTINGS: MetaColumnDef[] = [
  // Status and dates
  {
    key: 'ad_actions',
    label: 'Actions',
    enabled: true,
    order: 300,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'delivery',
    label: 'Delivery',
    enabled: true,
    order: 301,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'attribution_setting',
    label: 'Attribution setting',
    enabled: true,
    order: 302,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'ends',
    label: 'Ends',
    enabled: true,
    order: 303,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'last_significant_edit',
    label: 'Last significant edit',
    enabled: true,
    order: 304,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'ad_set_delivery',
    label: 'Ad set delivery',
    enabled: false,
    order: 305,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'date_created',
    label: 'Date created',
    enabled: false,
    order: 306,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'date_last_edited',
    label: 'Date last edited',
    enabled: false,
    order: 307,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'reporting_ends',
    label: 'Reporting ends',
    enabled: false,
    order: 308,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'reporting_starts',
    label: 'Reporting starts',
    enabled: false,
    order: 309,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'starts',
    label: 'Starts',
    enabled: false,
    order: 310,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'time_elapsed_pct',
    label: 'Time elapsed percentage',
    enabled: false,
    order: 311,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  // Goal, budget & schedule
  {
    key: 'budget',
    label: 'Budget',
    enabled: true,
    order: 320,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'bid_strategy',
    label: 'Bid strategy',
    enabled: true,
    order: 321,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'schedule',
    label: 'Schedule',
    enabled: true,
    order: 322,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'ad_schedule',
    label: 'Ad schedule',
    enabled: false,
    order: 323,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'budget_remaining',
    label: 'Budget remaining',
    enabled: false,
    order: 324,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'buying_type',
    label: 'Buying type',
    enabled: false,
    order: 325,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'campaign_spending_limit',
    label: 'Campaign spending limit',
    enabled: false,
    order: 326,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'conversion_location',
    label: 'Conversion location',
    enabled: false,
    order: 327,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'objective',
    label: 'Objective',
    enabled: false,
    order: 328,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'performance_goal',
    label: 'Performance goal',
    enabled: false,
    order: 329,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  // Object names and IDs
  {
    key: 'ad_set_name',
    label: 'Ad set name',
    enabled: true,
    order: 340,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'campaign_name_col',
    label: 'Campaign name',
    enabled: false,
    order: 341,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'account_id',
    label: 'Account ID',
    enabled: false,
    order: 342,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'account_name',
    label: 'Account name',
    enabled: false,
    order: 343,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_id',
    label: 'Ad ID',
    enabled: false,
    order: 344,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_name',
    label: 'Ad name',
    enabled: false,
    order: 345,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_set_id',
    label: 'Ad set ID',
    enabled: false,
    order: 346,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'campaign_id',
    label: 'Campaign ID',
    enabled: false,
    order: 347,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'tags',
    label: 'Tags',
    enabled: false,
    order: 348,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  // Targeting
  {
    key: 'audience_age',
    label: 'Audience age (ad set settings)',
    enabled: false,
    order: 360,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'audience_gender',
    label: 'Audience gender (ad set settings)',
    enabled: false,
    order: 361,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'audience_location',
    label: 'Audience location (ad set settings)',
    enabled: false,
    order: 362,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'excluded_custom_audiences',
    label: 'Excluded custom audiences',
    enabled: false,
    order: 363,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'included_custom_audiences',
    label: 'Included custom audiences',
    enabled: false,
    order: 364,
    category: 'ad_settings',
    section: 'Targeting',
  },
  // Ad creative
  {
    key: 'body',
    label: 'Body (ad settings)',
    enabled: false,
    order: 370,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'destination',
    label: 'Destination',
    enabled: false,
    order: 371,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'headline',
    label: 'Headline (ad settings)',
    enabled: false,
    order: 372,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'link',
    label: 'Link (ad settings)',
    enabled: false,
    order: 373,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'page_name',
    label: 'Page name',
    enabled: false,
    order: 374,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'preview_link',
    label: 'Preview link',
    enabled: false,
    order: 375,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  // Tracking source
  {
    key: 'app_event',
    label: 'App event',
    enabled: false,
    order: 380,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'offline_event',
    label: 'Offline event',
    enabled: false,
    order: 381,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'url_parameters',
    label: 'URL parameters',
    enabled: false,
    order: 382,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'web_event',
    label: 'Web event',
    enabled: false,
    order: 383,
    category: 'ad_settings',
    section: 'Tracking source',
  },
];

const ADSET_AD_SETTINGS: MetaColumnDef[] = [
  // Status and dates
  {
    key: 'ad_actions',
    label: 'Actions',
    enabled: true,
    order: 300,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'delivery',
    label: 'Delivery',
    enabled: true,
    order: 301,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'attribution_setting',
    label: 'Attribution setting',
    enabled: true,
    order: 302,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'ends',
    label: 'Ends',
    enabled: true,
    order: 303,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'last_significant_edit',
    label: 'Last significant edit',
    enabled: true,
    order: 304,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'ad_set_delivery',
    label: 'Ad set delivery',
    enabled: false,
    order: 305,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'date_created',
    label: 'Date created',
    enabled: false,
    order: 306,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'date_last_edited',
    label: 'Date last edited',
    enabled: false,
    order: 307,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'reporting_ends',
    label: 'Reporting ends',
    enabled: false,
    order: 308,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'reporting_starts',
    label: 'Reporting starts',
    enabled: false,
    order: 309,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'starts',
    label: 'Starts',
    enabled: false,
    order: 310,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'time_elapsed_pct',
    label: 'Time elapsed percentage',
    enabled: false,
    order: 311,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  // Goal, budget & schedule (Ad Set specific)
  {
    key: 'budget',
    label: 'Budget',
    enabled: true,
    order: 320,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'optimization_goal',
    label: 'Optimization goal',
    enabled: true,
    order: 321,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'billing_event',
    label: 'Billing event',
    enabled: true,
    order: 322,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'bid_amount',
    label: 'Bid amount',
    enabled: false,
    order: 323,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'bid_strategy',
    label: 'Bid strategy',
    enabled: false,
    order: 324,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'schedule',
    label: 'Schedule',
    enabled: true,
    order: 325,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'ad_schedule',
    label: 'Ad schedule',
    enabled: false,
    order: 326,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'budget_remaining',
    label: 'Budget remaining',
    enabled: false,
    order: 327,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'audience_size',
    label: 'Audience size estimate',
    enabled: false,
    order: 328,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'placement',
    label: 'Placements',
    enabled: false,
    order: 329,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'performance_goal',
    label: 'Performance goal',
    enabled: false,
    order: 330,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  // Object names and IDs
  {
    key: 'ad_set_name',
    label: 'Ad set name',
    enabled: true,
    order: 340,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'campaign_name_col',
    label: 'Campaign name',
    enabled: false,
    order: 341,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'account_id',
    label: 'Account ID',
    enabled: false,
    order: 342,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'account_name',
    label: 'Account name',
    enabled: false,
    order: 343,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_id',
    label: 'Ad ID',
    enabled: false,
    order: 344,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_name',
    label: 'Ad name',
    enabled: false,
    order: 345,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_set_id',
    label: 'Ad set ID',
    enabled: false,
    order: 346,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'campaign_id',
    label: 'Campaign ID',
    enabled: false,
    order: 347,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'tags',
    label: 'Tags',
    enabled: false,
    order: 348,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  // Targeting
  {
    key: 'audience_age',
    label: 'Audience age (ad set settings)',
    enabled: false,
    order: 360,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'audience_gender',
    label: 'Audience gender (ad set settings)',
    enabled: false,
    order: 361,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'audience_location',
    label: 'Audience location (ad set settings)',
    enabled: false,
    order: 362,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'excluded_custom_audiences',
    label: 'Excluded custom audiences',
    enabled: false,
    order: 363,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'included_custom_audiences',
    label: 'Included custom audiences',
    enabled: false,
    order: 364,
    category: 'ad_settings',
    section: 'Targeting',
  },
  // Ad creative
  {
    key: 'body',
    label: 'Body (ad settings)',
    enabled: false,
    order: 370,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'destination',
    label: 'Destination',
    enabled: false,
    order: 371,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'headline',
    label: 'Headline (ad settings)',
    enabled: false,
    order: 372,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'link',
    label: 'Link (ad settings)',
    enabled: false,
    order: 373,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'page_name',
    label: 'Page name',
    enabled: false,
    order: 374,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'preview_link',
    label: 'Preview link',
    enabled: false,
    order: 375,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  // Tracking source
  {
    key: 'app_event',
    label: 'App event',
    enabled: false,
    order: 380,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'offline_event',
    label: 'Offline event',
    enabled: false,
    order: 381,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'url_parameters',
    label: 'URL parameters',
    enabled: false,
    order: 382,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'web_event',
    label: 'Web event',
    enabled: false,
    order: 383,
    category: 'ad_settings',
    section: 'Tracking source',
  },
];

const AD_AD_SETTINGS: MetaColumnDef[] = [
  // Status and dates
  {
    key: 'ad_actions',
    label: 'Actions',
    enabled: true,
    order: 300,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'delivery',
    label: 'Delivery',
    enabled: true,
    order: 301,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'attribution_setting',
    label: 'Attribution setting',
    enabled: true,
    order: 302,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'ends',
    label: 'Ends',
    enabled: true,
    order: 303,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'last_significant_edit',
    label: 'Last significant edit',
    enabled: true,
    order: 304,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'ad_set_delivery',
    label: 'Ad set delivery',
    enabled: false,
    order: 305,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'date_created',
    label: 'Date created',
    enabled: false,
    order: 306,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'date_last_edited',
    label: 'Date last edited',
    enabled: false,
    order: 307,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'reporting_ends',
    label: 'Reporting ends',
    enabled: false,
    order: 308,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'reporting_starts',
    label: 'Reporting starts',
    enabled: false,
    order: 309,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  {
    key: 'time_elapsed_pct',
    label: 'Time elapsed percentage',
    enabled: false,
    order: 311,
    category: 'ad_settings',
    section: 'Status and dates',
  },
  // Goal, budget & schedule (Ads — simplified)
  {
    key: 'ad_schedule',
    label: 'Schedule',
    enabled: false,
    order: 320,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'ad_starts',
    label: 'Starts',
    enabled: false,
    order: 321,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  {
    key: 'ad_ends',
    label: 'Ends',
    enabled: false,
    order: 322,
    category: 'ad_settings',
    section: 'Goal, budget & schedule',
  },
  // Object names and IDs
  {
    key: 'ad_set_name',
    label: 'Ad set name',
    enabled: true,
    order: 340,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'campaign_name_col',
    label: 'Campaign name',
    enabled: false,
    order: 341,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'account_id',
    label: 'Account ID',
    enabled: false,
    order: 342,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'account_name',
    label: 'Account name',
    enabled: false,
    order: 343,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_id',
    label: 'Ad ID',
    enabled: false,
    order: 344,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_name',
    label: 'Ad name',
    enabled: false,
    order: 345,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'ad_set_id',
    label: 'Ad set ID',
    enabled: false,
    order: 346,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'campaign_id',
    label: 'Campaign ID',
    enabled: false,
    order: 347,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  {
    key: 'tags',
    label: 'Tags',
    enabled: false,
    order: 348,
    category: 'ad_settings',
    section: 'Object names and IDs',
  },
  // Targeting
  {
    key: 'audience_age',
    label: 'Audience age (ad set settings)',
    enabled: false,
    order: 360,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'audience_gender',
    label: 'Audience gender (ad set settings)',
    enabled: false,
    order: 361,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'audience_location',
    label: 'Audience location (ad set settings)',
    enabled: false,
    order: 362,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'excluded_custom_audiences',
    label: 'Excluded custom audiences',
    enabled: false,
    order: 363,
    category: 'ad_settings',
    section: 'Targeting',
  },
  {
    key: 'included_custom_audiences',
    label: 'Included custom audiences',
    enabled: false,
    order: 364,
    category: 'ad_settings',
    section: 'Targeting',
  },
  // Ad creative
  {
    key: 'body',
    label: 'Body (ad settings)',
    enabled: false,
    order: 370,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'destination',
    label: 'Destination',
    enabled: false,
    order: 371,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'headline',
    label: 'Headline (ad settings)',
    enabled: false,
    order: 372,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'link',
    label: 'Link (ad settings)',
    enabled: false,
    order: 373,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'page_name',
    label: 'Page name',
    enabled: false,
    order: 374,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  {
    key: 'preview_link',
    label: 'Preview link',
    enabled: false,
    order: 375,
    category: 'ad_settings',
    section: 'Ad creative',
  },
  // Tracking source
  {
    key: 'app_event',
    label: 'App event',
    enabled: false,
    order: 380,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'offline_event',
    label: 'Offline event',
    enabled: false,
    order: 381,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'url_parameters',
    label: 'URL parameters',
    enabled: false,
    order: 382,
    category: 'ad_settings',
    section: 'Tracking source',
  },
  {
    key: 'web_event',
    label: 'Web event',
    enabled: false,
    order: 383,
    category: 'ad_settings',
    section: 'Tracking source',
  },
];

const SHARED_ADVANCED: MetaColumnDef[] = [
  // Impressions
  {
    key: 'adv_auto_refresh_impr',
    label: 'Auto-refresh impressions',
    enabled: false,
    order: 400,
    category: 'advanced',
    section: 'Impressions',
  },
  {
    key: 'adv_gross_impressions',
    label: 'Gross impressions (includes invalid)',
    enabled: false,
    order: 401,
    category: 'advanced',
    section: 'Impressions',
  },
  {
    key: 'adv_ix_impressions',
    label: 'Instant Experience impressions',
    enabled: false,
    order: 402,
    category: 'advanced',
    section: 'Impressions',
  },
  {
    key: 'adv_ix_reach',
    label: 'Instant Experience reach',
    enabled: false,
    order: 403,
    category: 'advanced',
    section: 'Impressions',
  },
  // Optimisation events
  {
    key: 'adv_opt_events',
    label: 'Optimisation events',
    enabled: false,
    order: 410,
    category: 'advanced',
    section: 'Optimisation events',
  },
  {
    key: 'adv_cost_per_opt_event',
    label: 'Cost per optimisation event',
    enabled: false,
    order: 411,
    category: 'advanced',
    section: 'Optimisation events',
  },
  // Views (Awareness)
  {
    key: 'adv_ix_view_pct',
    label: 'Instant Experience view percentage',
    enabled: false,
    order: 420,
    category: 'advanced',
    section: 'Views',
  },
  {
    key: 'adv_ix_view_time',
    label: 'Instant Experience view time',
    enabled: false,
    order: 421,
    category: 'advanced',
    section: 'Views',
  },
  // Brand lift
  {
    key: 'adv_ad_recall_lift',
    label: 'Ad recall lift',
    enabled: false,
    order: 430,
    category: 'advanced',
    section: 'Brand lift',
  },
  {
    key: 'adv_ad_recall_lift_rate',
    label: 'Ad recall lift rate',
    enabled: false,
    order: 431,
    category: 'advanced',
    section: 'Brand lift',
  },
  {
    key: 'adv_cost_per_ad_recall',
    label: 'Cost per ad recall lift',
    enabled: false,
    order: 432,
    category: 'advanced',
    section: 'Brand lift',
  },
  // Clicks (Engagement)
  {
    key: 'adv_ix_clicks_open',
    label: 'Instant Experience clicks to open',
    enabled: false,
    order: 440,
    category: 'advanced',
    section: 'Clicks',
  },
  {
    key: 'adv_ix_clicks_start',
    label: 'Instant Experience clicks to start',
    enabled: false,
    order: 441,
    category: 'advanced',
    section: 'Clicks',
  },
  {
    key: 'adv_ix_outbound_clicks',
    label: 'Instant Experience outbound clicks',
    enabled: false,
    order: 442,
    category: 'advanced',
    section: 'Clicks',
  },
  // Performance funnel
  {
    key: 'adv_lp_views_per_link_click',
    label: 'Landing page views rate per link clicks',
    enabled: false,
    order: 450,
    category: 'advanced',
    section: 'Performance funnel',
  },
  {
    key: 'adv_purchases_per_lp_view',
    label: 'Purchases rate per landing page views',
    enabled: false,
    order: 451,
    category: 'advanced',
    section: 'Performance funnel',
  },
  {
    key: 'adv_purchases_per_link_click',
    label: 'Purchases rate per link clicks',
    enabled: false,
    order: 452,
    category: 'advanced',
    section: 'Performance funnel',
  },
  {
    key: 'adv_results_per_link_click',
    label: 'Results rate per link clicks',
    enabled: false,
    order: 453,
    category: 'advanced',
    section: 'Performance funnel',
  },
];

const CAMPAIGN_ADVANCED = SHARED_ADVANCED;
const ADSET_ADVANCED = SHARED_ADVANCED;
const AD_ADVANCED = SHARED_ADVANCED;

@Component({
  selector: 'app-meta',
  standalone: false,
  templateUrl: './meta.component.html',
  styleUrls: ['./meta.component.scss'],
})
export class MetaComponent implements OnInit {
  formGroup!: FormGroup;
  userId: string = '';
  actId: string | null = null;

  // Raw data from API
  campaigns: Campaign[] = [];

  // Table data per tab
  campaignTableData: TableData[] = [];
  adSetTableData: TableData[] = [];
  adTableData: TableData[] = [];

  // Headers per tab
  campaignHeaders: TableHeader[] = [];
  adSetHeaders: TableHeader[] = [];
  adHeaders: TableHeader[] = [];

  buttons: TableButton[] = [];

  // Tabs
  tabs: string[] = ['Campaigns', 'Ad Sets', 'Ads'];
  activeTabIndex: number = 0;

  // Column configs (mutable per-tab)
  campaignColumnConfig: MetaColumnDef[] = [];
  adSetColumnConfig: MetaColumnDef[] = [];
  adColumnConfig: MetaColumnDef[] = [];

  // Column customiser state
  isColumnCustomiserOpen = false;
  customiserCategoryIndex = 0;
  columnSearch = '';
  tempColumnConfig: MetaColumnDef[] = [];
  collapsedSections: Set<string> = new Set();

  readonly customiserCategories = [
    { key: 'key_metrics', label: 'Key metrics' },
    { key: 'tracking', label: 'Tracking' },
    { key: 'ad_settings', label: 'Ad settings' },
    { key: 'advanced', label: 'Advanced' },
    { key: 'custom', label: 'Custom' },
  ];

  /** Exposes SE_LIST to the template for the conversion table */
  readonly seList = SE_LIST;

  readonly kmSections: {
    id: string;
    label: string;
    keys: string[];
    isConversionTable?: boolean;
  }[] = [
    {
      id: 'results',
      label: 'Results',
      keys: [
        'results',
        'cost_per_result',
        'result_rate',
        'results_roas',
        'results_value',
      ],
    },
    {
      id: 'spend',
      label: 'Spend',
      keys: ['spend', 'spend_pct'],
    },
    {
      id: 'impressions',
      label: 'Impressions',
      keys: ['impressions', 'reach', 'frequency', 'cpm', 'cpp'],
    },
    {
      id: 'views',
      label: 'Views',
      keys: ['viewers', 'views'],
    },
    {
      id: 'media',
      label: 'Media',
      keys: [
        'video_plays_2s',
        'video_plays_3s',
        'cost_per_2s_video_play',
        'cost_per_3s_video_play',
        'video_plays',
        'thruplay_views',
        'cost_per_thruplay',
        'unique_2s_video_plays',
      ],
    },
    {
      id: 'clicks',
      label: 'Clicks',
      keys: [
        'clicks_all',
        'cpc_all',
        'cpc_link',
        'ctr_all',
        'ctr_link',
        'cost_per_unique_click',
        'cost_per_unique_link_click',
        'cost_per_outbound_click',
        'cost_per_unique_outbound',
        'link_clicks',
        'outbound_clicks',
        'outbound_ctr',
        'photo_clicks',
        'shop_clicks',
        'unique_clicks_all',
        'unique_ctr_all',
        'unique_ctr_link',
        'unique_link_clicks',
        'unique_outbound_clicks',
        'unique_outbound_ctr',
      ],
    },
    {
      id: 'traffic',
      label: 'Traffic',
      keys: [
        'landing_page_views',
        'cost_per_landing_page_view',
        'ig_profile_visits',
      ],
    },
    {
      id: 'follows_likes',
      label: 'Follows & likes',
      keys: ['fb_likes', 'cost_per_like', 'ig_follows'],
    },
    {
      id: 'engagement',
      label: 'Engagement',
      keys: [
        'post_engagements',
        'page_engagement',
        'cost_per_post_engagement',
        'cost_per_page_engagement',
        'post_reactions',
        'post_comments',
        'post_shares',
        'post_saves',
        'check_ins',
        'event_responses',
        'cost_per_event_response',
        'join_group_requests',
        'cost_per_join_group',
        'effect_share',
        'net_reminders_on',
      ],
    },
    {
      id: 'messaging',
      label: 'Messaging',
      keys: [
        'msg_conversations_started',
        'cost_per_msg_convo',
        'msg_conversations_replied',
        'msg_contacts',
        'new_msg_contacts',
        'cost_per_new_msg_contact',
        'returning_msg_contacts',
        'msg_subscriptions',
        'cost_per_msg_subscription',
        'welcome_msg_views',
      ],
    },
    {
      id: 'calling',
      label: 'Calling',
      keys: [
        'callback_requests',
        'phone_calls_placed',
        'messenger_calls_placed',
        'calls_20s_messenger',
        'calls_20s_phone',
        'calls_60s_messenger',
        'calls_60s_phone',
        'blocks',
      ],
    },
    {
      id: 'standard_events',
      label: 'Standard events',
      keys: SE_LIST.flatMap((e) => [
        `conv_${e.id}_total`,
        `conv_${e.id}_value`,
        `conv_${e.id}_cost`,
      ]),
      isConversionTable: true,
    },
    {
      id: 'custom_events',
      label: 'Custom events',
      keys: ['custom_events_input'],
    },
  ];

  // Modal
  isModalOpen: boolean = false;
  title: string = '';
  isEdit: boolean = false;

  // Confirmation modal
  isConfirmModalOpen: boolean = false;
  confirmMessage: string = '';
  confirmAction: (() => void) | null = null;

  // Analytics dashboard
  analyticsCards: AnalyticsCard[] = [];
  selectedCampaign: Campaign | null = null;
  activeModalTab: 'form' | 'graph' = 'form';
  selectedTimePeriod: number = 30;
  campaignMetrics: CampaignMetrics | null = null;
  deviceBreakdown: DeviceBreakdown[] = [];
  ageBreakdown: AgeBreakdown[] = [];
  performanceData: PerformanceData[] = [];

  // Top item for analytics
  topItemData: any = null;
  topItemType: 'campaign' | 'adset' | 'ad' | null = null;
  preloadedItemData: any = null;

  search = '';
  isLoading = false;
  private pendingRequests = 0;

  // Expose enums to template
  CampaignStatus = CampaignStatus;
  Provider = Provider;

  isLoadingColumns = false;

  constructor(
    private readonly toastr: AppToastrService,
    private readonly formBuilder: FormBuilder,
    private readonly campaignService: CampaignService,
    private readonly adSetService: AdSetService,
    private readonly adService: AdService,
    private readonly authStore: AuthStoreService,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly userConfigService: UserConfigService,
  ) {}

  ngOnInit(): void {
    this.userId = this.authStore.getUserId();
    this.actId = this.authStore.getActId();
    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam !== null) {
      const idx = parseInt(tabParam, 10);
      if (!isNaN(idx) && idx >= 0 && idx < this.tabs.length) {
        this.activeTabIndex = idx;
      }
    }
    this.campaignColumnConfig = this.getColumnConfigForTab(0);
    this.adSetColumnConfig = this.getColumnConfigForTab(1);
    this.adColumnConfig = this.getColumnConfigForTab(2);
    this.setCampaignHeaders();
    this.setAdSetHeaders();
    this.setAdHeaders();
    this.setButtons();
    this.createFormGroup();
    this.initializeAnalytics();
    this.loadAllData();
    this.loadColumnConfig(this.activeTabIndex);
  }

  private tabIndexToEntityType(
    tabIndex: number,
  ): 'CAMPAIGN' | 'AD_SET' | 'AD' {
    if (tabIndex === 1) return 'AD_SET';
    if (tabIndex === 2) return 'AD';
    return 'CAMPAIGN';
  }

  private loadColumnConfig(tabIndex: number): void {
    const entityType = this.tabIndexToEntityType(tabIndex);
    this.isLoadingColumns = true;
    this.userConfigService
      .getColumnConfig(entityType)
      .pipe(finalize(() => {
        this.isLoadingColumns = false;
        this.cdr.detectChanges();
      }))
      .subscribe({
        next: (config) => {
          const savedKeys = new Set(config.columns);
          const tabConfig = this.getColumnConfigForTab(tabIndex);
          tabConfig.forEach((col) => {
            col.enabled = col.alwaysVisible || savedKeys.has(col.key);
          });
          switch (tabIndex) {
            case 1:
              this.adSetColumnConfig = tabConfig;
              this.setAdSetHeaders();
              break;
            case 2:
              this.adColumnConfig = tabConfig;
              this.setAdHeaders();
              break;
            default:
              this.campaignColumnConfig = tabConfig;
              this.setCampaignHeaders();
          }
        },
        error: () => {
          // silently fall back to defaults already set in ngOnInit
        },
      });
  }

  // ---------- Data Loading ----------

  loadAllData(): void {
    this.isLoading = true;
    this.pendingRequests = 3;
    this.fetchCampaigns();
    this.fetchAdSets();
    this.fetchAds();
  }

  private finishRequest(): void {
    this.pendingRequests--;
    if (this.pendingRequests === 0) {
      this.isLoading = false;
      this.cdr.detectChanges();
    }
  }

  /**
   * Special column handlers for specific column keys
   * Using Map for O(1) lookup performance with 100+ columns
   */
  private readonly specialColumnHandlers = new Map<
    string,
    (entity: any) => any
  >([
    ['name', (entity) => entity.name || '—'],
    ['status', (entity) => this.statusLabel(entity.status)],
    ['statusBadge', (entity) => this.statusLabel(entity.status)],
    ['platform', (entity) => entity.platform || '—'],
    ['externalId', (entity) => entity.externalId || '—'],
    ['toggle', (entity) => entity.status],
  ]);

  /**
   * Gets value from entity, prioritizing: special handler > entity property > rawData > default
   * Optimized for handling 100+ columns efficiently
   */
  private getColumnValue(key: string, entity: any): any {
    // 1. Check if there's a special handler for this column (O(1) lookup)
    const handler = this.specialColumnHandlers.get(key);
    if (handler) {
      return handler(entity);
    }

    // 2. Check if property exists directly on entity (not null/undefined)
    if (
      entity.hasOwnProperty(key) &&
      entity[key] !== null &&
      entity[key] !== undefined &&
      entity[key] !== ''
    ) {
      return entity[key];
    }

    // 3. Try to get from rawData
    if (entity.rawData && typeof entity.rawData === 'object') {
      const rawValue = entity.rawData[key];
      if (rawValue !== null && rawValue !== undefined && rawValue !== '') {
        return rawValue;
      }
    }

    // 4. Return default
    return '—';
  }

  /**
   * Essential columns that must always be included in table data (for status updates, etc.)
   * even if not enabled for display
   * - externalId: Required for status updates and API calls
   * - platform: Required for determining which service to use
   * - status: Required for toggle button to read current status
   */
  private readonly essentialColumns = ['externalId', 'platform', 'status'];

  /**
   * Populates table rows from entity data and rawData
   * Optimized for 100+ columns - filters once, maps with efficient value extraction
   * Always includes essential columns (externalId, platform) for status updates
   */
  private buildTableRows(
    columnConfig: MetaColumnDef[],
    entity: any,
  ): Array<{ key: string; value: any }> {
    // Get enabled columns
    const enabledColumns = columnConfig.filter((c) => c.enabled);
    const enabledKeys = new Set(enabledColumns.map((c) => c.key));

    // Build rows for enabled columns
    const rows = enabledColumns.map((c) => ({
      key: c.key,
      value: this.getColumnValue(c.key, entity),
    }));

    // Ensure essential columns are included (for status toggle and other operations)
    // Add them only if not already present in enabled columns
    for (const essentialKey of this.essentialColumns) {
      if (!enabledKeys.has(essentialKey)) {
        rows.push({
          key: essentialKey,
          value: this.getColumnValue(essentialKey, entity),
        });
      }
    }

    return rows;
  }

  fetchCampaigns(): void {
    if (!this.actId) {
      this.toastr.warning('No Meta ad account linked to this user');
      this.finishRequest();
      return;
    }
    this.campaignService.getAllByPlatform(Provider.META).subscribe({
      next: (res) => {
        try {
          this.campaigns = res?.data ?? [];
          this.preloadAnalyticsWithMostRecent();
          this.campaignTableData = this.campaigns.map(
            (c) =>
              ({
                guid: String(c.id),
                expanded: false,
                rows: this.buildTableRows(this.campaignColumnConfig, c),
              }) as TableData,
          );
        } finally {
          this.finishRequest();
        }
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to fetch campaigns'));
        }
        this.finishRequest();
      },
    });
  }

  fetchAdSets(): void {
    if (!this.actId) {
      this.finishRequest();
      return;
    }
    this.adSetService.getAllByPlatform(Provider.META).subscribe({
      next: (res) => {
        try {
          const adSets: AdSetResponse[] = res?.data ?? [];
          this.adSetTableData = adSets.map(
            (as) =>
              ({
                guid: String(as.id),
                expanded: false,
                rows: this.buildTableRows(this.adSetColumnConfig, as),
              }) as TableData,
          );
        } finally {
          this.finishRequest();
        }
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to fetch ad sets'));
        }
        this.finishRequest();
      },
    });
  }

  fetchAds(): void {
    if (!this.actId) {
      this.finishRequest();
      return;
    }
    this.adService.getAllByPlatform(Provider.META).subscribe({
      next: (res) => {
        try {
          const ads: AdResponse[] = res?.data ?? [];
          this.adTableData = ads.map(
            (ad) =>
              ({
                guid: String(ad.id),
                expanded: false,
                rows: this.buildTableRows(this.adColumnConfig, ad),
              }) as TableData,
          );
        } finally {
          this.finishRequest();
        }
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to fetch ads'));
        }
        this.finishRequest();
      },
    });
  }

  // ---------- Tab & Data Helpers ----------

  getCurrentData(): TableData[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetTableData;
      case 2:
        return this.adTableData;
      default:
        return this.campaignTableData;
    }
  }

  getCurrentHeaders(): TableHeader[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetHeaders;
      case 2:
        return this.adHeaders;
      default:
        return this.campaignHeaders;
    }
  }

  onTabChange(tabIndex: number): void {
    this.activeTabIndex = tabIndex;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tabIndex },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.loadColumnConfig(tabIndex);
  }

  // ---------- Headers ----------

  private buildHeaders(config: MetaColumnDef[]): TableHeader[] {
    return config
      .filter((c) => c.enabled)
      .sort((a, b) => a.order - b.order)
      .map((c) => ({
        title: c.label,
        value: c.key,
        width: c.width,
        appliedSorting: false,
      }));
  }

  setCampaignHeaders(): void {
    this.campaignHeaders = this.buildHeaders(this.campaignColumnConfig);
  }

  setAdSetHeaders(): void {
    this.adSetHeaders = this.buildHeaders(this.adSetColumnConfig);
  }

  setAdHeaders(): void {
    this.adHeaders = this.buildHeaders(this.adColumnConfig);
  }

  setButtons(): void {
    this.buttons = [
      { name: 'Edit', redirect: false },
      { name: 'Delete', redirect: false },
    ];
  }

  // ---------- Status Helper ----------

  private statusLabel(s: string | null | undefined): string {
    const t = (s ?? '').toString().toUpperCase();
    if (t === 'ACTIVE') return 'Active';
    if (t === 'PAUSED') return 'Paused';
    if (t === 'DELETED') return 'Deleted';
    return s ?? '';
  }

  // ---------- Edit / Delete ----------

  onEditClick(event: { item: any; isChild?: boolean; childData?: any }): void {
    if (this.activeTabIndex === 0) {
      this.openModal(event.item.guid);
    } else {
      this.toastr.info('Editing coming soon for this item type');
    }
  }

  onDeleteClick(event: {
    item: any;
    isChild?: boolean;
    childData?: any;
  }): void {
    if (this.activeTabIndex === 0) {
      const campaignName = this.getValue(event.item, 'name') || 'Campaign';
      this.showConfirmModal(
        `Are you sure you want to delete the campaign "${campaignName}"?`,
        () => this.deleteCampaign(event.item.guid),
      );
    } else {
      this.toastr.info('Deletion coming soon for this item type');
    }
  }

  private getValue(item: any, key: string): any {
    return item?.rows?.find((row: any) => row.key === key)?.value;
  }

  // ---------- Confirmation Modal ----------

  showConfirmModal(message: string, action: () => void): void {
    this.confirmMessage = message;
    this.confirmAction = action;
    this.isConfirmModalOpen = true;
  }

  onConfirmYes(): void {
    if (this.confirmAction) {
      this.confirmAction();
    }
    this.closeConfirmModal();
  }

  onConfirmNo(): void {
    this.closeConfirmModal();
  }

  closeConfirmModal(): void {
    this.isConfirmModalOpen = false;
    this.confirmMessage = '';
    this.confirmAction = null;
  }

  // ---------- Status Toggle ----------

  onStatusToggle(event: {
    item: any;
    currentStatus: string;
    newStatus: string;
  }): void {
    const { item, newStatus, currentStatus } = event;

    const platform = item.rows.find((r: any) => r.key === 'platform')?.value;
    const externalId = item.rows.find(
      (r: any) => r.key === 'externalId',
    )?.value;

    if (!platform || !externalId) {
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Missing platform or external ID for this item');
      }
      return;
    }

    // Optimistic update
    const statusRow = item.rows.find((row: any) => row.key === 'status');
    if (statusRow) statusRow.value = newStatus;
    const badgeRow = item.rows.find((row: any) => row.key === 'statusBadge');
    if (badgeRow) badgeRow.value = newStatus;
    const toggleRow = item.rows.find((row: any) => row.key === 'toggle');
    if (toggleRow) toggleRow.value = newStatus;

    const status = newStatus.toUpperCase();
    let request$;
    let entityLabel: string;

    if (this.activeTabIndex === 0) {
      request$ = this.campaignService.patchStatus(platform, externalId, status);
      entityLabel = 'Campaign';
    } else if (this.activeTabIndex === 1) {
      request$ = this.adSetService.patchStatus(platform, externalId, status);
      entityLabel = 'Ad Set';
    } else {
      request$ = this.adService.patchStatus(platform, externalId, status);
      entityLabel = 'Ad';
    }

    item.disabledButtons = true;
    this.cdr.detectChanges();

    request$.pipe(
      finalize(() => { item.disabledButtons = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (updated: any) => {
        // Sync the returned status back into the table row
        const returnedStatus = updated?.status ?? updated?.data?.status;
        if (returnedStatus) {
          const labeledStatus = this.statusLabel(returnedStatus);
          if (statusRow) statusRow.value = labeledStatus;
          if (badgeRow) badgeRow.value = labeledStatus;
          if (toggleRow) toggleRow.value = labeledStatus;
        }
        this.toastr.success(`${entityLabel} status updated`);
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        // Revert on failure
        if (statusRow) statusRow.value = currentStatus;
        if (badgeRow) badgeRow.value = currentStatus;
        if (toggleRow) toggleRow.value = currentStatus;
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, `Failed to update ${entityLabel} status`));
        }
        this.cdr.detectChanges();
      },
    });
  }

  // ---------- Delete Campaign ----------

  deleteCampaign(guid: any): void {
    this.campaignService.deleteById(guid).subscribe({
      next: () => {
        this.toastr.success('Campaign deleted successfully!');
        this.fetchCampaigns();
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to delete campaign'));
        }
      },
    });
  }

  // ---------- Campaign Edit Modal ----------

  createFormGroup(): void {
    this.formGroup = this.formBuilder.group({
      id: [''],
      name: ['', [Validators.required, Validators.maxLength(255)]],
      userId: [this.userId],
      status: ['', [Validators.required]],
      specialAdCategories: ['', [Validators.maxLength(255)]],
      objective: ['', [Validators.required, Validators.maxLength(100)]],
      adAccountId: [
        this.actId ?? '',
        [Validators.required, Validators.maxLength(255)],
      ],
      externalId: ['', [Validators.maxLength(255)]],
      platform: ['', [Validators.required]],
    });
  }

  updateFormGroup(campaign: Campaign): void {
    this.formGroup = this.formBuilder.group({
      id: [campaign.id],
      name: [campaign.name, [Validators.required, Validators.maxLength(255)]],
      userId: [this.userId],
      status: [campaign.status, [Validators.required]],
      specialAdCategories: [
        campaign.specialAdCategories ?? '',
        [Validators.maxLength(255)],
      ],
      objective: [
        campaign.objective,
        [Validators.required, Validators.maxLength(100)],
      ],
      adAccountId: [
        (campaign.adAccountId || this.actId) ?? '',
        [Validators.required, Validators.maxLength(255)],
      ],
      externalId: [campaign.externalId ?? '', [Validators.maxLength(255)]],
      platform: [campaign.platform, [Validators.required]],
    });
  }

  hasError(controlName: string, errorType: string): boolean {
    const control = this.formGroup.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  openModal(guid: any): void {
    this.activeModalTab = 'form';
    if (guid != null) {
      const campaign = this.campaigns.find(
        (c) => String(c.id) === String(guid),
      );
      if (campaign) {
        this.isEdit = true;
        this.updateFormGroup(campaign);
        this.title = `Edit Campaign - ${campaign.name}`;
      } else {
        this.title = 'Campaign Not Found';
        this.isEdit = false;
        this.createFormGroup();
      }
    } else {
      this.title = 'Create Campaign';
      this.isEdit = false;
      this.createFormGroup();
    }
    this.isModalOpen = true;
  }

  onSubmit(): void {
    if (!this.formGroup.valid) {
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Please fill all required fields');
      }
      return;
    }

    const payload = {
      ...this.formGroup.value,
      userId: this.userId,
    };

    const action = this.isEdit
      ? this.campaignService.updateById(
          this.formGroup.controls['id'].value,
          payload,
        )
      : this.campaignService.create(payload);

    action.subscribe({
      next: () => {
        this.toastr.success(
          `Campaign ${this.isEdit ? 'updated' : 'created'} successfully!`,
        );
        this.formGroup.reset();
        this.isModalOpen = false;
        this.fetchCampaigns();
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(
            CoreService.extractErrorMessage(err, `Failed to ${this.isEdit ? 'update' : 'create'} campaign`),
          );
        }
      },
    });
  }

  onCancel(): void {
    if (this.formGroup) this.formGroup.reset();
    this.isModalOpen = false;
    this.isEdit = false;
  }

  // ---------- Row Click ----------

  onRowClick(event: { item: any; originalEvent?: MouseEvent }): void {
    if (this.activeTabIndex !== 0) return;
    const campaign = this.campaigns.find(
      (c) => String(c.id) === String(event.item.guid),
    );
    if (campaign) {
      this.selectedCampaign = campaign;
      setTimeout(() => {
        this.topItemData = campaign;
        this.topItemType = 'campaign';
        this.cdr.detectChanges();
      }, 0);
    }
  }

  onTopItemChange(topItem: any): void {
    setTimeout(() => {
      if (topItem && this.activeTabIndex === 0) {
        const campaign = this.campaigns.find(
          (c) => String(c.id) === String(topItem.guid),
        );
        if (campaign) {
          this.topItemData = campaign;
          this.topItemType = 'campaign';
        }
      } else {
        this.topItemData = null;
        this.topItemType = null;
      }
      this.cdr.detectChanges();
    }, 0);
  }

  // ---------- Analytics ----------

  initializeAnalytics(): void {
    this.analyticsCards = [
      {
        title: 'Campaigns',
        total: 0,
        active: 0,
        paused: 0,
        deleted: 0,
        activePercentage: 0,
        pausedPercentage: 0,
        deletedPercentage: 0,
      },
      {
        title: 'Ad Sets',
        total: 0,
        active: 0,
        paused: 0,
        deleted: 0,
        activePercentage: 0,
        pausedPercentage: 0,
        deletedPercentage: 0,
      },
      {
        title: 'Ads',
        total: 0,
        active: 0,
        paused: 0,
        deleted: 0,
        activePercentage: 0,
        pausedPercentage: 0,
        deletedPercentage: 0,
      },
    ];
  }

  private preloadAnalyticsWithMostRecent(): void {
    if (this.campaigns.length > 0) {
      const sorted = [...this.campaigns].sort((a, b) => {
        const dateA = new Date(a.updatedAt || 0).getTime();
        const dateB = new Date(b.updatedAt || 0).getTime();
        return dateB - dateA;
      });
      this.preloadedItemData = sorted[0];
    }
  }

  // ---------- Column Customiser ----------

  /** Returns a fresh deep-cloned column config for the given tab, expanding
   *  isConversionEvent markers into _total / _value / _cost triples. */
  getColumnConfigForTab(tabIndex: number): MetaColumnDef[] {
    const source =
      tabIndex === 0
        ? CAMPAIGN_COLUMNS
        : tabIndex === 1
          ? ADSET_COLUMNS
          : AD_COLUMNS;
    const base: MetaColumnDef[] = deepClone(source as MetaColumnDef[]);
    const expanded: MetaColumnDef[] = [];
    let convIdx = 0;
    for (const col of base) {
      if (col.isConversionEvent) {
        const id = col.key.replace(/^conv_/, '');
        const baseOrder = 2000 + convIdx * 3;
        expanded.push(
          {
            ...col,
            key: `conv_${id}_total`,
            label: `${col.label} (total)`,
            order: baseOrder,
            isConversionEvent: false,
          },
          {
            ...col,
            key: `conv_${id}_value`,
            label: `${col.label} (value)`,
            order: baseOrder + 1,
            isConversionEvent: false,
          },
          {
            ...col,
            key: `conv_${id}_cost`,
            label: `${col.label} (cost)`,
            order: baseOrder + 2,
            isConversionEvent: false,
          },
        );
        convIdx++;
      } else {
        expanded.push(col);
      }
    }
    return expanded;
  }

  /** Returns the currently active tab's mutable column config. */
  private getCurrentTabConfig(): MetaColumnDef[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetColumnConfig;
      case 2:
        return this.adColumnConfig;
      default:
        return this.campaignColumnConfig;
    }
  }

  private setColumnConfigForTab(config: MetaColumnDef[]): void {
    switch (this.activeTabIndex) {
      case 1:
        this.adSetColumnConfig = config;
        break;
      case 2:
        this.adColumnConfig = config;
        break;
      default:
        this.campaignColumnConfig = config;
    }
  }

  openColumnCustomiser(): void {
    this.tempColumnConfig = this.getCurrentTabConfig().map((c) => ({ ...c }));
    this.columnSearch = '';
    this.customiserCategoryIndex = 0;
    this.isColumnCustomiserOpen = true;
  }

  closeColumnCustomiser(): void {
    this.isColumnCustomiserOpen = false;
  }

  applyColumnConfig(): void {
    this.setColumnConfigForTab(this.tempColumnConfig.map((c) => ({ ...c })));
    switch (this.activeTabIndex) {
      case 1:
        this.setAdSetHeaders();
        break;
      case 2:
        this.setAdHeaders();
        break;
      default:
        this.setCampaignHeaders();
    }
    this.isColumnCustomiserOpen = false;
    this.cdr.detectChanges();

    const entityType = this.tabIndexToEntityType(this.activeTabIndex);
    const selectedKeys = this.tempColumnConfig
      .filter((c) => c.enabled && !c.alwaysVisible)
      .map((c) => c.key);
    this.userConfigService.saveColumnConfig(entityType, selectedKeys).subscribe({
      next: () => {},
      error: () => {
        this.toastr.warning(
          'Column preferences could not be saved — they will reset on next login.',
        );
      },
    });
  }

  getAvailableColumns(): MetaColumnDef[] {
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    // Custom tab has no toggle-able columns
    if (!cat || cat.key === 'custom') return [];
    const categoryKey = cat.key;
    const q = this.columnSearch.toLowerCase().trim();
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        c.category === categoryKey &&
        (!q || c.label.toLowerCase().includes(q)),
    );
  }

  getSelectedColumns(): MetaColumnDef[] {
    return this.tempColumnConfig.filter((c) => !c.alwaysVisible && c.enabled);
  }

  toggleTempColumn(col: MetaColumnDef): void {
    const found = this.tempColumnConfig.find((c) => c.key === col.key);
    if (found) found.enabled = !found.enabled;
  }

  removeTempColumn(col: MetaColumnDef): void {
    const found = this.tempColumnConfig.find((c) => c.key === col.key);
    if (found) found.enabled = false;
  }

  /** Safe cell value lookup — returns '—' for any missing or unknown key */
  getCellValue(item: any, key: string): string {
    try {
      return item?.rows?.find((r: any) => r.key === key)?.value ?? '—';
    } catch {
      return '—';
    }
  }

  /** Returns true if the given key is enabled in the temp column config */
  isTempColEnabled(key: string): boolean {
    return this.tempColumnConfig.find((c) => c.key === key)?.enabled ?? false;
  }

  /** Toggles a temp column by key (used for conversion table checkboxes) */
  toggleTempColumnByKey(key: string): void {
    const col = this.tempColumnConfig.find((c) => c.key === key);
    if (col) col.enabled = !col.enabled;
  }

  toggleSection(section: string): void {
    if (this.collapsedSections.has(section))
      this.collapsedSections.delete(section);
    else this.collapsedSections.add(section);
  }

  isSectionCollapsed(section: string): boolean {
    return this.collapsedSections.has(section);
  }

  getSectionSelectedCount(keys: string[]): number {
    return this.tempColumnConfig.filter(
      (c) => keys.includes(c.key) && c.enabled,
    ).length;
  }

  getAvailableColumnsByKeys(keys: string[]): MetaColumnDef[] {
    const q = this.columnSearch.toLowerCase().trim();
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        keys.includes(c.key) &&
        (!q || c.label.toLowerCase().includes(q)),
    );
  }

  /** Returns ordered distinct parentSection/section groups for the current non-KM tab */
  getTabSections(): { parentSection: string; section: string; id: string }[] {
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    if (!cat || cat.key === 'custom' || cat.key === 'key_metrics') return [];
    const q = this.columnSearch.toLowerCase().trim();
    const seen = new Set<string>();
    const result: { parentSection: string; section: string; id: string }[] = [];
    for (const col of this.tempColumnConfig) {
      if (col.alwaysVisible || col.category !== cat.key) continue;
      if (q && !col.label.toLowerCase().includes(q)) continue;
      const ps = col.parentSection ?? '';
      const id = `${ps}|||${col.section}`;
      if (!seen.has(id)) {
        seen.add(id);
        result.push({ parentSection: ps, section: col.section, id });
      }
    }
    return result;
  }

  /** Returns columns for a specific parentSection+section in the current tab */
  getColumnsBySection(parentSection: string, section: string): MetaColumnDef[] {
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    if (!cat) return [];
    const q = this.columnSearch.toLowerCase().trim();
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        c.category === cat.key &&
        (c.parentSection ?? '') === parentSection &&
        c.section === section &&
        (!q || c.label.toLowerCase().includes(q)),
    );
  }

  /** Count selected columns in a given tab section (by id = 'parentSection|||section') */
  getTabSectionSelectedCount(sectionId: string): number {
    const [parentSection, section] = sectionId.split('|||');
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    if (!cat) return 0;
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        c.category === cat.key &&
        (c.parentSection ?? '') === parentSection &&
        c.section === section &&
        c.enabled,
    ).length;
  }

  generatePerformanceData(): void {
    const data: PerformanceData[] = [];
    const baseDate = new Date();
    baseDate.setDate(baseDate.getDate() - this.selectedTimePeriod);

    for (let i = 0; i < this.selectedTimePeriod; i++) {
      const date = new Date(baseDate);
      date.setDate(date.getDate() + i);
      const dayOfWeek = date.getDay();
      const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
      const weekendMultiplier = isWeekend ? 0.65 : 1.0;
      const trendMultiplier =
        1 + Math.sin((i / this.selectedTimePeriod) * Math.PI * 2) * 0.2;
      const impressions = Math.floor(
        (12000 + Math.random() * 8000) * weekendMultiplier * trendMultiplier,
      );
      const reach = Math.floor(impressions * (0.72 + Math.random() * 0.18));
      const people = Math.floor(reach * (0.82 + Math.random() * 0.13));
      const clicks = Math.floor(impressions * (0.018 + Math.random() * 0.027));
      const conversions = Math.floor(clicks * (0.025 + Math.random() * 0.08));
      const cost = parseFloat(
        (clicks * (1.1 + Math.random() * 1.3)).toFixed(2),
      );

      data.push({
        date: date.toISOString().split('T')[0],
        impressions,
        reach,
        people,
        clicks,
        conversions,
        cost,
      });
    }
    this.performanceData = data;
  }
}
