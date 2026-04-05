export interface UserDto {
  id?: number;
  fullName: string;
  username: string;
  email: string;
  phoneNumber?: number;
  address: string;
  password: string;
  accountExpiredDate?: string;
  accountLocked?: boolean;
  credentialsExpiredDate?: string;
  enabled?: boolean;
  role?: string;
}
export class LoginResponseDto{
  token:string='';
  userId:number=0;
  role:string='';
  actId:string|null=null;
}
export class RequestTokenDto {
  email: string = '';
}

export class ResetPasswordDto {
  email: string = '';
  token: string = '';
  newPassword: string = '';
}


