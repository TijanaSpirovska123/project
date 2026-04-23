export interface PageDto {
  id: number;
  pageId: string;
  name: string;
  pictureUrl?: string;
}

export interface PagePostDto {
  id: number;
  postId: string;
  permalinkUrl: string;
}
