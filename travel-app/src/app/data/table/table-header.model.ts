export interface TableHeader {
  colSpan?: number;
  classes?: string;
  title?: string;
  width?: string | number;
  appliedSorting?: boolean;
  sortedAsc?: boolean;
  value?: string;
  childrenHeader?: TableHeader[];
}
