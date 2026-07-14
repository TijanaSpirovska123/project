export interface PlatformCard {
  key: string;
  name: string;
  icon: string;
  color: string;
  enabled: boolean;
  syncing: boolean;
  connected: boolean;
  lastSynced: string | null;
  tokenStatus: 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | null;
}
