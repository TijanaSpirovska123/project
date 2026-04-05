# TravelApp — Routing & Navigation Flows

## Route Definitions

| Path | Component | Guard |
|------|-----------|-------|
| `/` | — | None | Redirects to `/home` |
| `/login` | LoginPageComponent | None |
| `/sign-up` | SignUpPageComponent | None |
| `/oauth-success` | OauthSuccessComponent | None |
| `/meta` | MetaComponent | authGuard |
| `/create-ad-workflow` | CreateAdWorkflowComponent | authGuard |
| `/insights` | InsightsComponent | authGuard |
| `/creative-library` | CreativeLibraryComponent | authGuard |
| `/sync-accounts` | SyncAccountsComponent | authGuard |

---

## Auth Guard (`auth.guard.ts`)

```
User visits protected route
  ├── SSR context → allow (auth checked on browser)
  ├── isAuthenticated() === true → allow
  └── isAuthenticated() === false → redirect /login
```

`isAuthenticated()` returns `!!localStorage.getItem('authToken')`.

---

## Flow 1: Normal Login

```
/home
  └── [Go to Login] button
        └── router.navigate(['login'])

/login
  └── User submits credentials
        └── LoginService.create(credentials)
              ├── SUCCESS
              │     AuthStoreService.login(token, undefined, userId, actId)
              │       → stores authToken, userId, actId in localStorage
              │     toastr.success("Welcome back!")
              │     router.navigate(['/sync-accounts'])
              └── ERROR
                    toastr.error("Invalid credentials")
```

---

## Flow 2: OAuth Login (Meta via Login Page)

```
/login
  └── [Connect with Meta] button
        └── OAuthService.connectMeta()   POST /oauth/meta/connect
              ├── SUCCESS
              │     window.location.href = `${API}${res.authorizationUrl}`
              │       → browser leaves app, goes to Meta OAuth
              └── ERROR
                    toastr.error("Meta connection failed")
```

---

## Flow 3: OAuth Callback (`/oauth-success`)

```
Meta/Instagram OAuth provider
  └── redirects to /oauth-success?token=...&accessToken=...&userId=...

OauthSuccessComponent.ngOnInit()
  └── reads query params
        ├── token present
        │     AuthStoreService.login(token, accessToken, userId)
        │       → stores data in localStorage
        │     router.navigate(['/meta'])
        │       └── FAIL → router.navigate(['/login'])
        └── no token
              console.warn("No token provided")
              router.navigate(['/login'])

  (error reading params) → router.navigate(['/login'])
```

---

## Flow 4: Connect Meta from Sync Accounts

```
/sync-accounts
  └── [Connect Meta] button
        └── OAuthService.connectMeta()   POST /oauth/meta/connect
              ├── SUCCESS
              │     window.location.href = `${API}${res.authorizationUrl}`
              │       → browser leaves app, goes to Meta OAuth
              │       → returns to /oauth-success (Flow 3)
              └── ERROR
                    toastr.error("Meta connection failed")
```

---

## Flow 5: Create Ad Workflow

```
/create-ad-workflow
  └── Multi-step form (Name → Platform → Campaign → Ad Set → Status → Creative)

  [Publish] button
    └── adForm.valid check
          ├── INVALID → markAllAsTouched(), toastr.error("Fill required fields")
          └── VALID
                AdService.create({ ...adForm.value, userId })
                  ├── SUCCESS
                  │     toastr.success("Ad created successfully!")
                  │     router.navigate(['/meta'])
                  └── ERROR
                        toastr.error("Failed to create ad")

  [Cancel] button
    └── router.navigate(['/meta'])
```

---

## Flow 6: Logout

```
Menu component
  └── [Logout] button
        └── LogoutService.logout()   POST /auth/logout
              └── SUCCESS
                    AuthStoreService.logout()
                      → clears authToken, accessToken, userId, actId from localStorage
                    router.navigate(['login'])
                    toastr.success("You have been logged out successfully")
```

---

## Flow 7: Menu Navigation

```
Menu item clicked
  ├── isParent === true → toggle expand/collapse children (no navigate)
  └── isParent === false
        └── router.navigate([selectedItem.route])
              Routes: /meta, /create-ad-workflow, /insights,
                      /creative-library, /sync-accounts
```

---

## Flow 8: Tab Navigation (Query Params)

Used in MetaComponent and InsightsComponent for tab state:

```
Tab changed
  └── router.navigate([], {
        relativeTo: this.route,
        queryParams: { tab: tabIndex },
        queryParamsHandling: 'merge',
        replaceUrl: true
      })
```
Does not change the route — only updates `?tab=N` in the URL.

---

## Navigation Summary

| Trigger | Destination | Method |
|---------|-------------|--------|
| App loads (unauthenticated + protected route) | `/login` | authGuard |
| App loads (root `/`) | `/home` | route redirect |
| Login success | `/sync-accounts` | `router.navigate` |
| Login page "go to login" | `/login` | `router.navigate` |
| Meta OAuth initiated | Meta OAuth URL | `window.location.href` |
| OAuth callback — success | `/meta` | `router.navigate` |
| OAuth callback — failure/no token | `/login` | `router.navigate` |
| Ad created | `/meta` | `router.navigate` |
| Ad workflow cancelled | `/meta` | `router.navigate` |
| Logout | `/login` | `router.navigate` |
| Menu item | respective route | `router.navigate` |

---

## Auth State (`AuthStoreService`)

localStorage keys managed:

| Key | Value |
|-----|-------|
| `authToken` | JWT used for API calls (via HTTP interceptor) |
| `accessToken` | Meta access token |
| `userId` | Internal user ID |
| `actId` | Meta ad account ID (stored as `act_<id>`) |

`isAuthenticated()` is purely token-presence: `!!localStorage.getItem('authToken')`.
