export interface TableButton {
  name?: 'View' | 'Delete' | 'Manage' | 'Edit' | 'Details' | 'Assume' | 'Arrow';
  title?:string;
  classes?: string;
  redirect?: boolean;
  link?: string;
  permission?: string;
}
