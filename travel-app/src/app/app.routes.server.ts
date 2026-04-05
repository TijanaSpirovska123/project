import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    path: 'meta',
    renderMode: RenderMode.Client,
  },
  {
    path: 'create-ad-workflow',
    renderMode: RenderMode.Client,
  },
  {
    path: 'insights',
    renderMode: RenderMode.Client,
  },
  {
    path: 'creative-library',
    renderMode: RenderMode.Client,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
