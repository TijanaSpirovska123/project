export interface ColumnConfigResponse {
  entityType: string;
  columns: string[];
  isDefault: boolean;
}

export interface ColumnConfigRequest {
  entityType: string;
  columns: string[];
}

export interface ThemeConfigResponse {
  mode: 'dark' | 'light';
}

export interface ThemeConfigRequest {
  mode: 'dark' | 'light';
}
