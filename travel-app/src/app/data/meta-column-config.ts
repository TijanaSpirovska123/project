export interface ColumnDef {
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

// ─── Campaign Columns ─────────────────────────────────────────────────────────

export const CAMPAIGN_COLUMNS: ColumnDef[] = [

  // ── Always visible ────────────────────────────────────────────────────────
  { key: 'name',        label: 'Campaign', category: 'key_metrics', section: '', alwaysVisible: true, enabled: true,  order: 0, width: '180px' },
  { key: 'toggle',      label: 'On/Off',   category: 'key_metrics', section: '', alwaysVisible: true, enabled: true,  order: 1, width: '60px'  },
  { key: 'statusBadge', label: 'Status',   category: 'key_metrics', section: '', alwaysVisible: true, enabled: true,  order: 2, width: '120px' },

  // ── KEY METRICS ───────────────────────────────────────────────────────────

  // Group: Results and spend  /  Section: Results
  { key: 'results',                      label: 'Results',                                    category: 'key_metrics', parentSection: 'Results and spend', section: 'Results',         enabled: true,  order: 10 },
  { key: 'cost_per_result',              label: 'Cost per result',                            category: 'key_metrics', parentSection: 'Results and spend', section: 'Results',         enabled: true,  order: 11 },
  { key: 'result_rate',                  label: 'Result rate',                                category: 'key_metrics', parentSection: 'Results and spend', section: 'Results',         enabled: false, order: 12 },
  { key: 'results_roas',                 label: 'Results ROAS',                               category: 'key_metrics', parentSection: 'Results and spend', section: 'Results',         enabled: false, order: 13 },
  { key: 'results_value',                label: 'Results value',                              category: 'key_metrics', parentSection: 'Results and spend', section: 'Results',         enabled: false, order: 14 },

  // Section: Spend
  { key: 'spend',                        label: 'Amount spent',                               category: 'key_metrics', parentSection: 'Results and spend', section: 'Spend',           enabled: true,  order: 20 },
  { key: 'spend_pct',                    label: 'Amount spent percentage',                    category: 'key_metrics', parentSection: 'Results and spend', section: 'Spend',           enabled: false, order: 21 },

  // Group: Distribution  /  Section: Impressions
  { key: 'impressions',                  label: 'Impressions',                                category: 'key_metrics', parentSection: 'Distribution',      section: 'Impressions',     enabled: true,  order: 30 },
  { key: 'reach',                        label: 'Reach',                                      category: 'key_metrics', parentSection: 'Distribution',      section: 'Impressions',     enabled: true,  order: 31 },
  { key: 'cpm',                          label: 'CPM (cost per 1,000 impressions)',            category: 'key_metrics', parentSection: 'Distribution',      section: 'Impressions',     enabled: false, order: 32 },
  { key: 'cpp',                          label: 'Cost per 1,000 accounts reached',            category: 'key_metrics', parentSection: 'Distribution',      section: 'Impressions',     enabled: false, order: 33 },
  { key: 'frequency',                    label: 'Frequency',                                  category: 'key_metrics', parentSection: 'Distribution',      section: 'Impressions',     enabled: false, order: 34 },

  // Section: Views
  { key: 'viewers',                      label: 'Viewers',                                    category: 'key_metrics', parentSection: 'Distribution',      section: 'Views',           enabled: false, order: 40 },
  { key: 'views',                        label: 'Views',                                      category: 'key_metrics', parentSection: 'Distribution',      section: 'Views',           enabled: false, order: 41 },

  // Group: Awareness  /  Section: Media
  { key: 'video_plays',                  label: 'Video plays',                                category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 50 },
  { key: 'video_plays_2s',               label: '2-second continuous video plays',            category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 51 },
  { key: 'video_plays_3s',               label: '3-second video plays',                       category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 52 },
  { key: 'cost_per_2s_video_play',       label: 'Cost per 2-second continuous video play',    category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 53 },
  { key: 'cost_per_3s_video_play',       label: 'Cost per 3-second video play',               category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 54 },
  { key: 'thruplay_views',               label: 'ThruPlays',                                  category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 55 },
  { key: 'cost_per_thruplay',            label: 'Cost per ThruPlay',                          category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 56 },
  { key: 'unique_2s_video_plays',        label: 'Unique 2-second continuous video plays',     category: 'key_metrics', parentSection: 'Awareness',         section: 'Media',           enabled: false, order: 57 },

  // Group: Engagement  /  Section: Clicks
  { key: 'clicks_all',                   label: 'Clicks (all)',                               category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 60 },
  { key: 'cpc_all',                      label: 'CPC (all)',                                  category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 61 },
  { key: 'cpc_link',                     label: 'CPC (cost per link click)',                  category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 62 },
  { key: 'ctr_all',                      label: 'CTR (all)',                                  category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 63 },
  { key: 'ctr_link',                     label: 'CTR (link click-through rate)',               category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 64 },
  { key: 'cost_per_unique_click',        label: 'Cost per unique click (all)',                category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 65 },
  { key: 'cost_per_unique_link_click',   label: 'Cost per unique link click',                 category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 66 },
  { key: 'cost_per_outbound_click',      label: 'Cost per outbound click',                   category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 67 },
  { key: 'cost_per_unique_outbound',     label: 'Cost per unique outbound click',             category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 68 },
  { key: 'link_clicks',                  label: 'Link clicks',                               category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 69 },
  { key: 'outbound_clicks',              label: 'Outbound clicks',                           category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 70 },
  { key: 'outbound_ctr',                 label: 'Outbound CTR (click-through rate)',          category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 71 },
  { key: 'photo_clicks',                 label: 'Photo clicks',                              category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 72 },
  { key: 'shop_clicks',                  label: 'Shop clicks',                               category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 73 },
  { key: 'unique_clicks_all',            label: 'Unique clicks (all)',                       category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 74 },
  { key: 'unique_ctr_all',               label: 'Unique CTR (all)',                          category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 75 },
  { key: 'unique_ctr_link',              label: 'Unique CTR (link click-through rate)',       category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 76 },
  { key: 'unique_link_clicks',           label: 'Unique link clicks',                        category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 77 },
  { key: 'unique_outbound_clicks',       label: 'Unique outbound clicks',                    category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 78 },
  { key: 'unique_outbound_ctr',          label: 'Unique outbound CTR (click-through rate)',   category: 'key_metrics', parentSection: 'Engagement',        section: 'Clicks',          enabled: false, order: 79 },

  // Section: Traffic
  { key: 'landing_page_views',           label: 'Website landing page views',                category: 'key_metrics', parentSection: 'Engagement',        section: 'Traffic',         enabled: false, order: 80 },
  { key: 'cost_per_landing_page_view',   label: 'Cost per landing page view',                category: 'key_metrics', parentSection: 'Engagement',        section: 'Traffic',         enabled: false, order: 81 },
  { key: 'ig_profile_visits',            label: 'Instagram profile visits',                  category: 'key_metrics', parentSection: 'Engagement',        section: 'Traffic',         enabled: false, order: 82 },

  // Section: Follows & likes
  { key: 'fb_likes',                     label: 'Facebook likes',                            category: 'key_metrics', parentSection: 'Engagement',        section: 'Follows & likes', enabled: false, order: 90 },
  { key: 'cost_per_like',                label: 'Cost per like',                             category: 'key_metrics', parentSection: 'Engagement',        section: 'Follows & likes', enabled: false, order: 91 },
  { key: 'ig_follows',                   label: 'Instagram follows',                         category: 'key_metrics', parentSection: 'Engagement',        section: 'Follows & likes', enabled: false, order: 92 },

  // Section: Engagement (sub-section)
  { key: 'post_engagements',             label: 'Post engagements',                          category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 100 },
  { key: 'page_engagement',              label: 'Page engagement',                           category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 101 },
  { key: 'cost_per_post_engagement',     label: 'Cost per post engagement',                  category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 102 },
  { key: 'cost_per_page_engagement',     label: 'Cost per Page engagement',                  category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 103 },
  { key: 'post_reactions',               label: 'Post reactions',                            category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 104 },
  { key: 'post_comments',                label: 'Post comments',                             category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 105 },
  { key: 'post_shares',                  label: 'Post shares',                               category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 106 },
  { key: 'post_saves',                   label: 'Post saves',                                category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 107 },
  { key: 'check_ins',                    label: 'Check-ins',                                 category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 108 },
  { key: 'event_responses',              label: 'Event responses',                           category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 109 },
  { key: 'cost_per_event_response',      label: 'Cost per event response',                   category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 110 },
  { key: 'join_group_requests',          label: 'Join group requests',                       category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 111 },
  { key: 'cost_per_join_group',          label: 'Cost per join group request',               category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 112 },
  { key: 'effect_share',                 label: 'Effect share',                              category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 113 },
  { key: 'net_reminders_on',             label: 'Net reminders on',                          category: 'key_metrics', parentSection: 'Engagement',        section: 'Engagement',      enabled: false, order: 114 },

  // Section: Messaging
  { key: 'msg_conversations_started',    label: 'Messaging conversations started',           category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 120 },
  { key: 'cost_per_msg_convo',           label: 'Cost per messaging conversation started',   category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 121 },
  { key: 'msg_conversations_replied',    label: 'Messaging conversations replied',           category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 122 },
  { key: 'msg_contacts',                 label: 'Messaging contacts',                        category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 123 },
  { key: 'new_msg_contacts',             label: 'New messaging contacts',                    category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 124 },
  { key: 'cost_per_new_msg_contact',     label: 'Cost per new messaging contact',            category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 125 },
  { key: 'returning_msg_contacts',       label: 'Returning messaging contacts',              category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 126 },
  { key: 'msg_subscriptions',            label: 'Messaging subscriptions',                   category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 127 },
  { key: 'cost_per_msg_subscription',    label: 'Cost per messaging subscription',           category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 128 },
  { key: 'welcome_msg_views',            label: 'Welcome message views',                     category: 'key_metrics', parentSection: 'Engagement',        section: 'Messaging',       enabled: false, order: 129 },

  // Section: Calling
  { key: 'callback_requests',            label: 'Callback requests submitted',               category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 130 },
  { key: 'phone_calls_placed',           label: 'Phone calls placed',                        category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 131 },
  { key: 'messenger_calls_placed',       label: 'Messenger calls placed',                    category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 132 },
  { key: 'calls_20s_messenger',          label: '20-second Messenger calls',                 category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 133 },
  { key: 'calls_20s_phone',              label: '20-second phone calls',                     category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 134 },
  { key: 'calls_60s_messenger',          label: '60-second Messenger calls',                 category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 135 },
  { key: 'calls_60s_phone',              label: '60-second phone calls',                     category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 136 },
  { key: 'blocks',                       label: 'Blocks',                                    category: 'key_metrics', parentSection: 'Engagement',        section: 'Calling',         enabled: false, order: 137 },

  // Group: Conversions  /  Section: Standard events  (isConversionEvent:true — expanded to _total/_value/_cost at runtime)
  { key: 'conv_achievements_unlocked',           label: 'Achievements unlocked',                        category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 200 },
  { key: 'conv_adds_of_payment_info',            label: 'Adds of payment info',                         category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 201 },
  { key: 'conv_adds_to_cart',                    label: 'Adds to cart',                                 category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 202 },
  { key: 'conv_adds_to_wishlist',                label: 'Adds to wishlist',                             category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 203 },
  { key: 'conv_app_activations',                 label: 'App activations',                              category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 204 },
  { key: 'conv_app_installs',                    label: 'App installs',                                 category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 205 },
  { key: 'conv_applications_submitted',          label: 'Applications submitted',                       category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 206 },
  { key: 'conv_appointments_scheduled',          label: 'Appointments scheduled',                       category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 207 },
  { key: 'conv_checkouts_initiated',             label: 'Checkouts initiated',                          category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 208 },
  { key: 'conv_contacts',                        label: 'Contacts',                                     category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 209 },
  { key: 'conv_content_views',                   label: 'Content views',                                category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 210 },
  { key: 'conv_credit_spends',                   label: 'Credit spends',                                category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 211 },
  { key: 'conv_custom_events',                   label: 'Custom events',                                category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 212 },
  { key: 'conv_desktop_app_engagements',         label: 'Desktop app engagements',                      category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 213 },
  { key: 'conv_desktop_app_story_engagements',   label: 'Desktop app story engagements',                category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 214 },
  { key: 'conv_desktop_app_uses',                label: 'Desktop app uses',                             category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 215 },
  { key: 'conv_direct_website_purchases',        label: 'Direct website purchases',                     category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 216 },
  { key: 'conv_direct_website_purchases_conv_value', label: 'Direct website purchases conversion value', category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 217 },
  { key: 'conv_donation_roas',                   label: 'Donation ROAS (return on ad spend)',            category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 218 },
  { key: 'conv_donations',                       label: 'Donations',                                    category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 219 },
  { key: 'conv_game_plays',                      label: 'Game plays',                                   category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 220 },
  { key: 'conv_get_directions_clicks',           label: 'Get directions clicks',                        category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 221 },
  { key: 'conv_in_app_ad_clicks',                label: 'In-app ad clicks',                             category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 222 },
  { key: 'conv_in_app_ad_impressions',           label: 'In-app ad impressions',                        category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 223 },
  { key: 'conv_landing_page_views',              label: 'Landing page views',                           category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 224 },
  { key: 'conv_leads',                           label: 'Leads',                                        category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 225 },
  { key: 'conv_levels_achieved',                 label: 'Levels achieved',                              category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 226 },
  { key: 'conv_location_searches',               label: 'Location searches',                            category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 227 },
  { key: 'conv_meta_workflow_completions',        label: 'Meta workflow completions',                    category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 228 },
  { key: 'conv_mobile_app_d2_retention',         label: 'Mobile app D2 retention',                      category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 229 },
  { key: 'conv_mobile_app_d7_retention',         label: 'Mobile app D7 retention',                      category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 230 },
  { key: 'conv_on_fb_message_to_buy',            label: 'On-Facebook message-to-buy events',            category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 231 },
  { key: 'conv_orders_created',                  label: 'Orders created',                               category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 232 },
  { key: 'conv_orders_dispatched',               label: 'Orders dispatched',                            category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 233 },
  { key: 'conv_other_offline_conversions',       label: 'Other offline conversions',                    category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 234 },
  { key: 'conv_phone_number_clicks',             label: 'Phone number clicks',                          category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 235 },
  { key: 'conv_products_customised',             label: 'Products customised',                          category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 236 },
  { key: 'conv_purchase_roas',                   label: 'Purchase ROAS (return on ad spend)',            category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 237 },
  { key: 'conv_purchases',                       label: 'Purchases',                                    category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 238 },
  { key: 'conv_ratings_submitted',               label: 'Ratings submitted',                            category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 239 },
  { key: 'conv_registrations_completed',         label: 'Registrations completed',                      category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 240 },
  { key: 'conv_searches',                        label: 'Searches',                                     category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 241 },
  { key: 'conv_shops_assisted_purchases',        label: 'Shops-assisted purchases',                     category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 242 },
  { key: 'conv_shops_assisted_purchases_conv_value', label: 'Shops-assisted purchases conversion value', category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 243 },
  { key: 'conv_subscriptions',                   label: 'Subscriptions',                                category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 244 },
  { key: 'conv_trials_started',                  label: 'Trials started',                               category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 245 },
  { key: 'conv_tutorials_completed',             label: 'Tutorials completed',                          category: 'key_metrics', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: true, enabled: false, order: 246 },

  // ── TRACKING ──────────────────────────────────────────────────────────────

  // Group: Diagnostics  /  Section: Ad relevance
  { key: 'quality_ranking',              label: 'Quality ranking',                            category: 'tracking', parentSection: 'Diagnostics', section: 'Ad relevance',                  enabled: false, order: 300 },
  { key: 'engagement_rate_ranking',      label: 'Engagement rate ranking',                    category: 'tracking', parentSection: 'Diagnostics', section: 'Ad relevance',                  enabled: false, order: 301 },
  { key: 'conversion_rate_ranking',      label: 'Conversion rate ranking',                    category: 'tracking', parentSection: 'Diagnostics', section: 'Ad relevance',                  enabled: false, order: 302 },

  // Section: Messaging and calling
  { key: 'track_calls_20s_messenger',   label: '20-second Messenger calls',                  category: 'tracking', parentSection: 'Diagnostics', section: 'Messaging and calling',          enabled: false, order: 310 },
  { key: 'track_calls_20s_phone',        label: '20-second phone calls',                      category: 'tracking', parentSection: 'Diagnostics', section: 'Messaging and calling',          enabled: false, order: 311 },
  { key: 'track_calls_60s_messenger',   label: '60-second Messenger calls',                  category: 'tracking', parentSection: 'Diagnostics', section: 'Messaging and calling',          enabled: false, order: 312 },
  { key: 'track_calls_60s_phone',        label: '60-second phone calls',                      category: 'tracking', parentSection: 'Diagnostics', section: 'Messaging and calling',          enabled: false, order: 313 },
  { key: 'track_blocks',                 label: 'Blocks',                                     category: 'tracking', parentSection: 'Diagnostics', section: 'Messaging and calling',          enabled: false, order: 314 },

  // Section: Media
  { key: 'track_video_p25',              label: 'Video plays at 25%',                         category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 320 },
  { key: 'track_video_p50',              label: 'Video plays at 50%',                         category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 321 },
  { key: 'track_video_p75',              label: 'Video plays at 75%',                         category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 322 },
  { key: 'track_video_p95',              label: 'Video plays at 95%',                         category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 323 },
  { key: 'track_video_p100',             label: 'Video plays at 100%',                        category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 324 },
  { key: 'track_video_avg_play_time',    label: 'Video average play time',                    category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 325 },
  { key: 'track_video_3s_rate',          label: '3-second video plays rate per impressions',  category: 'tracking', parentSection: 'Diagnostics', section: 'Media',                         enabled: false, order: 326 },

  // ── AD SETTINGS ───────────────────────────────────────────────────────────

  // Section: Status and dates
  { key: 'ad_actions',                   label: 'Actions',                                    category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: true,  order: 400 },
  { key: 'delivery',                     label: 'Delivery',                                   category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: true,  order: 401 },
  { key: 'attribution_setting',          label: 'Attribution setting',                        category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: true,  order: 402 },
  { key: 'ends',                         label: 'Ends',                                       category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: true,  order: 403 },
  { key: 'last_significant_edit',        label: 'Last significant edit',                      category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: true,  order: 404 },
  { key: 'ad_set_delivery',              label: 'Ad set delivery',                            category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 405 },
  { key: 'date_created',                 label: 'Date created',                               category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 406 },
  { key: 'date_last_edited',             label: 'Date last edited',                           category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 407 },
  { key: 'reporting_ends',               label: 'Reporting ends',                             category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 408 },
  { key: 'reporting_starts',             label: 'Reporting starts',                           category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 409 },
  { key: 'starts',                       label: 'Starts',                                     category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 410 },
  { key: 'time_elapsed_pct',             label: 'Time elapsed percentage',                    category: 'ad_settings', parentSection: '', section: 'Status and dates',         enabled: false, order: 411 },

  // Section: Goal, budget & schedule
  { key: 'budget',                       label: 'Budget',                                     category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: true,  order: 420 },
  { key: 'bid_strategy',                 label: 'Bid strategy',                               category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: true,  order: 421 },
  { key: 'schedule',                     label: 'Schedule',                                   category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: true,  order: 422 },
  { key: 'ad_schedule',                  label: 'Ad schedule',                                category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 423 },
  { key: 'budget_remaining',             label: 'Budget remaining',                           category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 424 },
  { key: 'buying_type',                  label: 'Buying type',                                category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 425 },
  { key: 'campaign_spending_limit',      label: 'Campaign spending limit',                    category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 426 },
  { key: 'conversion_location',          label: 'Conversion location',                        category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 427 },
  { key: 'objective',                    label: 'Objective',                                  category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 428 },
  { key: 'performance_goal',             label: 'Performance goal',                           category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule',  enabled: false, order: 429 },

  // Section: Object names and IDs
  { key: 'ad_set_name',                  label: 'Ad set name',                                category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: true,  order: 440 },
  { key: 'campaign_name_col',            label: 'Campaign name',                              category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 441 },
  { key: 'account_id',                   label: 'Account ID',                                 category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 442 },
  { key: 'account_name',                 label: 'Account name',                               category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 443 },
  { key: 'ad_id',                        label: 'Ad ID',                                      category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 444 },
  { key: 'ad_name',                      label: 'Ad name',                                    category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 445 },
  { key: 'ad_set_id',                    label: 'Ad set ID',                                  category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 446 },
  { key: 'campaign_id',                  label: 'Campaign ID',                                category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 447 },
  { key: 'tags',                         label: 'Tags',                                       category: 'ad_settings', parentSection: '', section: 'Object names and IDs',     enabled: false, order: 448 },

  // Section: Targeting
  { key: 'audience_age',                 label: 'Audience age (ad set settings)',              category: 'ad_settings', parentSection: '', section: 'Targeting',                enabled: false, order: 460 },
  { key: 'audience_gender',              label: 'Audience gender (ad set settings)',           category: 'ad_settings', parentSection: '', section: 'Targeting',                enabled: false, order: 461 },
  { key: 'audience_location',            label: 'Audience location (ad set settings)',         category: 'ad_settings', parentSection: '', section: 'Targeting',                enabled: false, order: 462 },
  { key: 'excluded_custom_audiences',    label: 'Excluded custom audiences',                  category: 'ad_settings', parentSection: '', section: 'Targeting',                enabled: false, order: 463 },
  { key: 'included_custom_audiences',    label: 'Included custom audiences',                  category: 'ad_settings', parentSection: '', section: 'Targeting',                enabled: false, order: 464 },

  // Section: Ad creative
  { key: 'body',                         label: 'Body (ad settings)',                          category: 'ad_settings', parentSection: '', section: 'Ad creative',              enabled: false, order: 470 },
  { key: 'destination',                  label: 'Destination',                                category: 'ad_settings', parentSection: '', section: 'Ad creative',              enabled: false, order: 471 },
  { key: 'headline',                     label: 'Headline (ad settings)',                      category: 'ad_settings', parentSection: '', section: 'Ad creative',              enabled: false, order: 472 },
  { key: 'link',                         label: 'Link (ad settings)',                          category: 'ad_settings', parentSection: '', section: 'Ad creative',              enabled: false, order: 473 },
  { key: 'page_name',                    label: 'Page name',                                  category: 'ad_settings', parentSection: '', section: 'Ad creative',              enabled: false, order: 474 },
  { key: 'preview_link',                 label: 'Preview link',                               category: 'ad_settings', parentSection: '', section: 'Ad creative',              enabled: false, order: 475 },

  // Section: Tracking source
  { key: 'app_event',                    label: 'App event',                                  category: 'ad_settings', parentSection: '', section: 'Tracking source',          enabled: false, order: 480 },
  { key: 'offline_event',                label: 'Offline event',                              category: 'ad_settings', parentSection: '', section: 'Tracking source',          enabled: false, order: 481 },
  { key: 'url_parameters',               label: 'URL parameters',                             category: 'ad_settings', parentSection: '', section: 'Tracking source',          enabled: false, order: 482 },
  { key: 'web_event',                    label: 'Web event',                                  category: 'ad_settings', parentSection: '', section: 'Tracking source',          enabled: false, order: 483 },

  // ── ADVANCED ──────────────────────────────────────────────────────────────

  // Group: Distribution  /  Section: Impressions
  { key: 'adv_auto_refresh_impr',        label: 'Auto-refresh impressions',                   category: 'advanced', parentSection: 'Distribution', section: 'Impressions',            enabled: false, order: 500 },
  { key: 'adv_gross_impressions',        label: 'Gross impressions (includes invalid)',        category: 'advanced', parentSection: 'Distribution', section: 'Impressions',            enabled: false, order: 501 },
  { key: 'adv_ix_impressions',           label: 'Instant Experience impressions',             category: 'advanced', parentSection: 'Distribution', section: 'Impressions',            enabled: false, order: 502 },
  { key: 'adv_ix_reach',                 label: 'Instant Experience reach',                   category: 'advanced', parentSection: 'Distribution', section: 'Impressions',            enabled: false, order: 503 },

  // Section: Optimisation events
  { key: 'adv_opt_events',               label: 'Optimisation events',                        category: 'advanced', parentSection: 'Distribution', section: 'Optimisation events',    enabled: false, order: 510 },
  { key: 'adv_cost_per_opt_event',       label: 'Cost per optimisation event',                category: 'advanced', parentSection: 'Distribution', section: 'Optimisation events',    enabled: false, order: 511 },

  // Group: Awareness  /  Section: Views
  { key: 'adv_ix_view_pct',              label: 'Instant Experience view percentage',         category: 'advanced', parentSection: 'Awareness', section: 'Views',              enabled: false, order: 520 },
  { key: 'adv_ix_view_time',             label: 'Instant Experience view time',               category: 'advanced', parentSection: 'Awareness', section: 'Views',              enabled: false, order: 521 },

  // Section: Brand lift
  { key: 'adv_ad_recall_lift',           label: 'Ad recall lift',                             category: 'advanced', parentSection: 'Awareness', section: 'Brand lift',         enabled: false, order: 530 },
  { key: 'adv_ad_recall_lift_rate',      label: 'Ad recall lift rate',                        category: 'advanced', parentSection: 'Awareness', section: 'Brand lift',         enabled: false, order: 531 },
  { key: 'adv_cost_per_ad_recall',       label: 'Cost per ad recall lift',                    category: 'advanced', parentSection: 'Awareness', section: 'Brand lift',         enabled: false, order: 532 },

  // Group: Engagement  /  Section: Clicks
  { key: 'adv_ix_clicks_open',           label: 'Instant Experience clicks to open',          category: 'advanced', parentSection: 'Engagement', section: 'Clicks',            enabled: false, order: 540 },
  { key: 'adv_ix_clicks_start',          label: 'Instant Experience clicks to start',         category: 'advanced', parentSection: 'Engagement', section: 'Clicks',            enabled: false, order: 541 },
  { key: 'adv_ix_outbound_clicks',       label: 'Instant Experience outbound clicks',         category: 'advanced', parentSection: 'Engagement', section: 'Clicks',            enabled: false, order: 542 },

  // Section: Performance funnel
  { key: 'adv_lp_views_per_link_click',  label: 'Landing page views rate per link clicks',    category: 'advanced', parentSection: 'Engagement', section: 'Performance funnel',     enabled: false, order: 550 },
  { key: 'adv_purchases_per_lp_view',    label: 'Purchases rate per landing page views',      category: 'advanced', parentSection: 'Engagement', section: 'Performance funnel',     enabled: false, order: 551 },
  { key: 'adv_purchases_per_link_click', label: 'Purchases rate per link clicks',             category: 'advanced', parentSection: 'Engagement', section: 'Performance funnel',     enabled: false, order: 552 },
  { key: 'adv_results_per_link_click',   label: 'Results rate per link clicks',               category: 'advanced', parentSection: 'Engagement', section: 'Performance funnel',     enabled: false, order: 553 },

  // Group: Conversions  /  Section: Standard events
  { key: 'adv_avg_purchases_conv_value', label: 'Average purchases conversion value',         category: 'advanced', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: false, enabled: false, order: 570 },
  { key: 'adv_cost_per_mobile_d2',       label: 'Cost per mobile app day 2 retention',        category: 'advanced', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: false, enabled: false, order: 571 },
  { key: 'adv_cost_per_mobile_d7',       label: 'Cost per mobile app day 7 retention',        category: 'advanced', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: false, enabled: false, order: 572 },
  { key: 'adv_mobile_d2_retention',      label: 'Mobile app day 2 retention',                 category: 'advanced', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: false, enabled: false, order: 573 },
  { key: 'adv_mobile_d7_retention',      label: 'Mobile app day 7 retention',                 category: 'advanced', parentSection: 'Conversions', section: 'Standard events', isConversionEvent: false, enabled: false, order: 574 },
];

// ─── Ad Set Columns ───────────────────────────────────────────────────────────
// Same as CAMPAIGN_COLUMNS but with Ad Set-specific Goal, budget & schedule entries.

const _adsetGoalRemove = new Set(['buying_type', 'campaign_spending_limit', 'conversion_location', 'objective']);

export const ADSET_COLUMNS: ColumnDef[] = [
  // Override name label
  { ...CAMPAIGN_COLUMNS.find(c => c.key === 'name')!,        label: 'Ad Set' },
  // All other campaign columns except name and the campaign-only Goal entries
  ...CAMPAIGN_COLUMNS.filter(c =>
    c.key !== 'name' &&
    !(c.section === 'Goal, budget & schedule' && _adsetGoalRemove.has(c.key)),
  ),
  // Ad Set-specific Goal entries (orders chosen to not conflict with kept entries 420-429)
  { key: 'optimization_goal', label: 'Optimization goal',    category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule', enabled: true,  order: 431 },
  { key: 'billing_event',     label: 'Billing event',        category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule', enabled: true,  order: 432 },
  { key: 'bid_amount',        label: 'Bid amount',           category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule', enabled: false, order: 433 },
  { key: 'audience_size',     label: 'Audience size estimate', category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule', enabled: false, order: 434 },
  { key: 'placement',         label: 'Placements',           category: 'ad_settings', parentSection: '', section: 'Goal, budget & schedule', enabled: false, order: 435 },
];

// ─── Ad Columns ───────────────────────────────────────────────────────────────
// Same as CAMPAIGN_COLUMNS plus Ad-specific entries.

export const AD_COLUMNS: ColumnDef[] = [
  // Override name label
  { ...CAMPAIGN_COLUMNS.find(c => c.key === 'name')!, label: 'Ad' },
  // All other campaign columns except name
  ...CAMPAIGN_COLUMNS.filter(c => c.key !== 'name'),
  // Ad-specific Status and dates entries
  { key: 'creative_type',    label: 'Creative type',    category: 'ad_settings', parentSection: '', section: 'Status and dates',   enabled: false, order: 412 },
  { key: 'ad_format',        label: 'Ad format',        category: 'ad_settings', parentSection: '', section: 'Status and dates',   enabled: false, order: 413 },
  { key: 'effective_status', label: 'Effective status', category: 'ad_settings', parentSection: '', section: 'Status and dates',   enabled: false, order: 414 },
  // Ad-specific Advanced entry
  { key: 'preview',          label: 'Ad preview',       category: 'advanced',    parentSection: 'Engagement', section: 'Performance funnel', enabled: false, order: 560 },
];
