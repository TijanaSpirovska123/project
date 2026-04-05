// src/app/shared/table/reusable-table.models.ts
export interface ColumnDef {
  /** Which TableDataRow.key to show in this column */
  key: string;
  /** Header label text */
  header: string;
  /** Optional fixed width (e.g. '140px' or '10%') */
  width?: string;
  /** Sortable header */
  sortable?: boolean;
  /** Center/Right alignment */
  align?: 'left' | 'center' | 'right';
  /** Optional custom cell rendering hint (used in template) */
  template?:
    | 'avatar-name' // circular avatar + name + mini badges
    | 'status-pill' // green/lilac/red pills
    | 'money' // currency
    | 'number-of-bill' // '2/2' style
    | 'actions'; // kebab/icons area
}
