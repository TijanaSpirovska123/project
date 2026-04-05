export interface MenuItem {
  icon: string;
  label: string;
  route: string;
  active: boolean;
  isParent?: boolean;
  isExpanded?: boolean;
  children?: MenuItem[];
}
