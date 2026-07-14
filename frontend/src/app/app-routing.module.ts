import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MetaComponent } from './components/meta/meta.component';
import { LoginPageComponent } from './components/login-page/login-page.component';
import { SignUpPageComponent } from './components/sign-up-page/sign-up-page.component';
import { OauthSuccessComponent } from './components/oauth/oauth-token.component';
import { CreateAdWorkflowComponent } from './components/create-ad-workflow/create-ad-workflow.component';
import { InsightsComponent } from './components/insights/insights.component';
import { CreativeLibraryComponent } from './pages/creative-library/creative-library.component';
import { SyncAccountsComponent } from './pages/sync-accounts/sync-accounts.component';
import { authGuard } from './guards/auth.guard';

const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginPageComponent },
  { path: 'sign-up', component: SignUpPageComponent },
  { path: 'meta', component: MetaComponent, canActivate: [authGuard] },
  {
    path: 'create-ad-workflow',
    component: CreateAdWorkflowComponent,
    canActivate: [authGuard],
  },
  { path: 'insights', component: InsightsComponent, canActivate: [authGuard] },
  {
    path: 'creative-library',
    component: CreativeLibraryComponent,
    canActivate: [authGuard],
  },
  {
    path: 'sync-accounts',
    component: SyncAccountsComponent,
    canActivate: [authGuard],
  },
  { path: 'oauth-success', component: OauthSuccessComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
