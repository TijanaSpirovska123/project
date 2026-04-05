export interface BaseEntity {
  id?: number;
  name: string;
  status: string;
  scheduleTime: Date | null;
  createdAt?: Date;
  updatedAt?: Date;
  platform: string;
  userId: number;
}

export interface BaseCreateDto {
  name: string;
  status: string;
  scheduleTime: Date;
  platform: string;
  userId: number;
}

export interface BaseUpdateDto extends BaseCreateDto {
  id: number;
}
