# travel-app — Authentication & API Flow

## Table of Contents

1. [Overview](#overview)
2. [Auth State Management](#auth-state-management)
3. [Login Flow](#login-flow)
4. [Meta OAuth Connect Flow](#meta-oauth-connect-flow)
5. [How the Meta Access Token Is Handled](#how-the-meta-access-token-is-handled)
6. [HTTP Interceptor — Every API Call](#http-interceptor--every-api-call)
7. [Route Guards](#route-guards)
8. [What Is Stored in the Browser](#what-is-stored-in-the-browser)
9. [Logout](#logout)
10. [Key Files](#key-files)

---

## Overview

The frontend is an **Angular 21 SSR application**. Authentication is entirely **JWT-based** — no session cookies are used for protected API calls. A single `AuthStoreService` owns all credential state; an HTTP interceptor attaches the JWT to every outbound request; and a route guard blocks unauthenticated navigation.

```
User → LoginPage → POST /api/auth/login
                         ↓
                   JWT + userId + actId
                         ↓
               AuthStoreService (localStorage)
                         ↓
       AuthInterceptor adds "Authorization: Bearer <jwt>"
       to every subsequent HTTP request
```

---

## Auth State Management

**File:** `src/app/services/core/auth-store.service.ts`

`AuthStoreService` is the single source of truth for authentication state. It reads and writes to `localStorage` directly and exposes a reactive `isLoggedIn$` observable.

### What is stored

| localStorage key | Value | Description |
|---|---|---|
| `authToken` | JWT string | Issued by the backend on login. Used in every API call header. |
| `accessToken` | Meta access token string | Long-lived Meta Graph API token. Received after OAuth completes. |
| `userId` | Integer string | The user's database ID. Used to scope requests. |
| `actId` | String (e.g. `act_123`) | The active Meta ad account ID. |

### Key methods

```typescript
login(token: string, accessToken?: string, userId?: number, actId?: string)
// Stores all four keys into localStorage and sets isLoggedIn$ = true

logout()
// Clears all four keys from localStorage and sets isLoggedIn$ = false

isAuthenticated(): boolean
// Returns true if authToken exists in localStorage

getAuthToken(): string | null
getAccessToken(): string | null
getActId(): string | null   // strips leading "act_" prefix before returning
getUserId(): number | null
```

---

## Login Flow

**Component:** `src/app/components/login-page/login-page.component.ts`
**Service:** `src/app/services/login/login.service.ts`

```
1. User submits email + password form
2. LoginService.create() → POST /api/auth/login
3. Backend validates credentials, returns:
   {
     token:       "<jwt>",
     userId:      42,
     role:        "USER",
     actId:       "act_1234567890"   // null if no Meta connection yet
   }
4. authStore.login(token, undefined, userId, actId)
   → Writes authToken, userId, actId to localStorage
   → Sets isLoggedIn$ = true
5. Router navigates to /sync-accounts
   (or the URL saved in sessionStorage before the guard redirected)
```

If the user was blocked by `authGuard` before logging in, the intended URL is saved to `sessionStorage` and restored after a successful login.

---

## Meta OAuth Connect Flow

**Component:** `src/app/components/oauth/oauth-token.component.ts`
**Service:** calls `POST /api/oauth/meta/connect` and handles the callback redirect

The Meta connection is a **3-legged OAuth 2.0 flow**. The backend drives the token exchange; the frontend only initiates the redirect and captures the result.

```
Step 1 — Initiate
──────────────────
Frontend (authenticated) → POST /api/oauth/meta/connect
Backend creates a one-time state UUID (expires in 10 min)
Backend returns: { authorizationUrl: "https://www.facebook.com/dialog/oauth?..." }
Frontend redirects the browser to that URL

Step 2 — User grants permissions on Facebook
─────────────────────────────────────────────
Facebook shows the permission dialog to the user
User clicks "Continue as ..."
Facebook redirects browser to:
  GET /api/oauth/meta/callback?code=AUTH_CODE&state=UUID

Step 3 — Backend handles the callback (no frontend involvement)
────────────────────────────────────────────────────────────────
Backend validates state UUID (checks database, checks expiry, marks consumed)
Backend exchanges code → short-lived token → long-lived token (60 days)
Backend saves token to database (OAuthAccountEntity)
Backend fetches Meta user info + ad accounts
Backend upserts AdAccountConnectionEntity rows

Step 4 — Redirect back to the frontend
────────────────────────────────────────
On first connect:
  Backend redirects to:
  http://localhost:5000/oauth/token?token=<jwt>&userId=42&actId=act_123

  OAuthTokenComponent reads query params
  Calls authStore.login(jwt, undefined, userId, actId)
  Navigates to /meta

On reconnect (user already logged in):
  Backend redirects to a success page
  No token is re-issued (existing session continues)
```

---

## How the Meta Access Token Is Handled

The **Meta access token is never held by the frontend at runtime** for API calls. All calls to Meta Graph API are made server-side by the Spring Boot backend.

The only time the term "accessToken" appears in the frontend is:

- `authStore.accessToken` — this slot exists in `localStorage` but its value is only populated when the backend explicitly returns one (currently unused for Graph API calls from the frontend). In practice the backend holds the token in the database and uses it for all syncing/reporting operations.
- The frontend sends its own **JWT** in `Authorization: Bearer` headers to the backend, and the backend retrieves the Meta token from the database internally.

### Token lifecycle from the frontend perspective

```
OAuth callback redirect URL contains no raw Meta token.
The backend has already stored the long-lived token (60 days) in the database.

The frontend receives only:
  - A fresh JWT (authToken) → used to call /api/* endpoints
  - userId and actId → used to scope UI state

When the Meta token approaches expiry (within 2 days):
  Backend throws an error on any sync operation
  Frontend receives a 4xx response
  User is prompted to click "Reconnect Meta"
  → OAuth flow restarts from Step 1 above
```

---

## HTTP Interceptor — Every API Call

**File:** `src/app/configs/http.token.interceptor.ts`

Every HTTP request made by the Angular app passes through `AuthInterceptor` before it leaves the browser.

```typescript
intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
  const token = this.authStore.getAuthToken();

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next.handle(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        this.authStore.logout();         // clears localStorage
        this.router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
}
```

**What this means for every protected call:**
- The JWT is automatically attached — no service needs to do it manually.
- A 401 from the backend (expired or invalid token) automatically signs the user out and redirects to `/login`.
- The interceptor handles both browser-side and SSR contexts.

---

## Route Guards

**File:** `src/app/guards/auth.guard.ts`

All protected routes are wrapped with `authGuard` (a functional `CanActivateFn`).

```
Browser request to /campaigns
  ↓
authGuard runs
  ↓
authStore.isAuthenticated()?
  ├── YES → allow navigation
  └── NO  → save intended URL to sessionStorage
             redirect to /login
```

After a successful login, the saved URL is read from `sessionStorage` and the user is redirected to where they originally tried to go.

During SSR (server-side rendering), the guard always allows navigation. Auth is enforced on the browser.

---

## What Is Stored in the Browser

```
localStorage
├── authToken      → JWT for all /api/* calls (expires in 1 hour server-side)
├── accessToken    → Meta token slot (populated by backend in some flows)
├── userId         → User's database ID
└── actId          → Active Meta ad account ID (e.g. "act_1234567890")

sessionStorage
└── redirectUrl    → Intended URL saved by authGuard before redirecting to /login
```

Nothing sensitive is stored in cookies by the frontend. The backend may set an `oauth_state` cookie during the OAuth handshake, but that is managed entirely by Spring Security and is short-lived.

---

## Logout

`authStore.logout()` removes all four `localStorage` keys and sets `isLoggedIn$` to `false`. The router then navigates to `/login`. No backend call is required — since auth is stateless (JWT), invalidation is purely client-side.

---

## Key Files

| File | Purpose |
|---|---|
| `src/app/services/core/auth-store.service.ts` | Auth state (localStorage + observables) |
| `src/app/configs/http.token.interceptor.ts` | Attaches JWT to every request; handles 401 |
| `src/app/guards/auth.guard.ts` | Protects routes from unauthenticated access |
| `src/app/components/login-page/login-page.component.ts` | Login form + login logic |
| `src/app/services/login/login.service.ts` | POST /api/auth/login |
| `src/app/components/oauth/oauth-token.component.ts` | Reads OAuth callback query params, stores token |
| `src/app/models/environment/environment-properties.model.ts` | API base URL (`http://localhost:8080/api`) |
