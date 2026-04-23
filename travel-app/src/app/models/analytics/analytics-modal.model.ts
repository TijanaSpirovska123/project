export interface ModalConfig {
  title: string;
  type:
    | 'performance'
    | 'demographics'
    | 'devices'
    | 'timeline'
    | 'conversions'
    | 'budget'
    | 'engagement';
  size: 'small' | 'medium' | 'large';
}
