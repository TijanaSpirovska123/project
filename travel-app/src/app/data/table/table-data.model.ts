export interface TableData {
  guid?: string;
  secondaryGuid?: string;
  disabledButtons?: boolean;
  hidden?: boolean;
  active?: boolean;
  viewed?: boolean;
  expanded?: boolean;
  rows: TableDataRow[];
}

export interface TableDataRow {
  colSpan?: number;
  classes?: string;
  value?: string | number | boolean | Date;
  title?: string;
  key?: string;
  childrenRow?: TableDataRow[][];
  hidden?: boolean;
  format?: string;
  width?: string;
  onCopy?: (event: any) => void;
  type?:
    | 'string'
    | 'date'
    | 'number'
    | 'boolean'
    | 'circle'
    | 'exclamation-mark'
    | 'error'
    | 'checkbox'
    | 'mailbox'
    | 'star'
    | 'arrow';
}
