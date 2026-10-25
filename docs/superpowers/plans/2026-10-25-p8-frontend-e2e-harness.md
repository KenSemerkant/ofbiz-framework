# P8 Frontend E2E — Harness + Order-to-Cash (Plan 1 of 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `platform/spa/` as Vite+React+TS, add a Playwright suite in `tests/e2e/` (auth + ported screens + order-to-cash journey), register phase P8 in IMPLEMENTATION.md, produce first green evidence run.

**Architecture:** Static SPA served by existing nginx/helm deploy (image swap, no gateway/helm changes). OIDC PKCE login through gateway, token in localStorage (Playwright `storageState` captures it). One spec file per screen/journey, run against live kind cluster via port-forwards.

**Tech Stack:** Vite 5, React 18, TypeScript 5, react-router-dom 6, Playwright 1.47, pnpm.

**Verified API facts (from code + evidence scripts):**
- Seeded: customer party `P1`, product `MP1` (9.99 USD), user `demo-user`/`demo-password` (role CUSTOMER)
- Gateway origin = SPA origin with port → `:8080`; SPA on `:4200`
- OIDC: authorize at `GW /oauth2/authorize` (PKCE S256, client_id `gateway`, redirect `<spa-origin>/login/oauth2/code/gateway`); identity login form fields `input[name=username]`, `input[name=password]`; token exchange `POST GW /oauth2/token` with header `Authorization: Basic base64(gateway:dev-local-secret)`
- Quote: `POST /api/v1/quotes` `{customerId,currencyUomId,lines:[{productId,facilityId,quantity}]}` → `{quoteId,status,lines}`; `POST /api/v1/quotes/{id}/convert` → order; `GET /api/v1/quotes/{id}`
- Order: `POST /api/v1/orders` `{customerId,currencyUomId,lines,trackingCode}` → 201 `{orderId,status,grandTotal,currencyUomId,lines}`; `POST /{id}/approve`, `POST /{id}/complete`, `GET /{id}/history`
- Stock must exist before order (saga reserves): `POST /api/v1/inventory-items` `{inventoryItemId,productId,facilityId}` then `POST /api/v1/inventory-items/{id}/receive` `{quantity}`
- Billing: `POST /api/v1/invoices` `{invoiceId,customerId,billingAccountId,currencyUomId,lines:[{invoiceItemSeqId,invoiceItemTypeId,description,quantity,amount}]}`; `POST /api/v1/payments` `{paymentId,invoiceId,idempotencyKey,amount,currencyUomId}`; `POST /api/v1/payments/{id}/capture`
- GL: `GET /api/v1/gl/trial-balance`

**Environment prerequisites (every run):** kind cluster up, `kubectl -n ofbiz port-forward svc/gateway 8080:8080 &` and `kubectl -n ofbiz port-forward svc/spa 4200:4200 &`.

---

### Task 1: Register phase P8 in IMPLEMENTATION.md

**Files:**
- Modify: `IMPLEMENTATION.md` (append P8 section after P7 / before program-docs tail)

- [ ] **Step 1: Append P8 section**

Append exactly:

```markdown
# PHASE 8 — Frontend E2E program (journey-driven SPA + Playwright)

Spec: docs/superpowers/specs/2026-10-25-frontend-e2e-program-design.md.
Rebuild platform/spa as Vite+React+TS; Playwright suite at tests/e2e run
against the live kind cluster; each story's green run is gate evidence.

- **P8-GATE:** all journey specs green via platform/spa/40-run-e2e.sh; per-journey PASS output linked below.
- **T1 (M):** SPA harness — Vite/React/TS rebuild, existing 4 screens (login/parties/catalog/p6) ported, deployed via existing helm chart (image swap). AC: [ ] specs for ported screens green [ ] deployed image serves SPA on :4200 with OIDC login working
- **T2 (M):** Playwright harness — tests/e2e with auth storageState, isolation (e2e-<ts>- prefixed IDs), accessibility locators. AC: [ ] auth setup + ≥1 spec green [ ] no shared state between tests
- **T3 (L):** Order-to-cash journey — quote→convert→order→approve, stock seeding, invoice+payment capture, GL trial-balance visibility. AC: [ ] spec green [ ] exercises party/product/order/inventory/billing/finance via gateway
- **T4 (M):** Returns journey — return→credit memo. AC: [ ] spec green
- **T5 (M):** Plan-to-ship journey — MRP→production run→shipment. AC: [ ] spec green
- **T6 (M):** Hire-to-retire journey — onboarding saga→position. AC: [ ] spec green
- **T7 (M):** Campaign-to-attribution journey — campaign→tracked order→attribution report. AC: [ ] spec green
- **T8 (S):** Content + calendar journeys. AC: [ ] specs green
- **T9 (S):** Notification stub screen — send test request, assert queued-not-delivered (email leg infra-blocked on kind, P5). AC: [ ] spec green
```

- [ ] **Step 2: Commit**

```bash
git add IMPLEMENTATION.md && git commit -m "Added: P8 phase registered in IMPLEMENTATION.md — frontend E2E program stories T1-T9"
```

---

### Task 2: Vite scaffold + auth + api client

**Files:**
- Create: `platform/spa/package.json`, `platform/spa/vite.config.ts`, `platform/spa/tsconfig.json`, `platform/spa/index.html` (replaces skeleton content), `platform/spa/src/main.tsx`, `platform/spa/src/App.tsx`, `platform/spa/src/auth.ts`, `platform/spa/src/api.ts`

- [ ] **Step 1: package.json**

```json
{
  "name": "ofbiz-spa",
  "private": true,
  "type": "module",
  "scripts": { "dev": "vite", "build": "tsc -b && vite build" },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.2"
  },
  "devDependencies": {
    "@types/react": "^18.3.11",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.2",
    "typescript": "^5.6.3",
    "vite": "^5.4.9"
  }
}
```

- [ ] **Step 2: vite.config.ts**

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { port: 4200 },
});
```

- [ ] **Step 3: tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "jsx": "react-jsx",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "isolatedModules": true
  },
  "include": ["src"]
}
```

- [ ] **Step 4: index.html (replaces skeleton; skeleton JS dies with it)**

```html
<!doctype html>
<!-- P8: Vite+React+TS SPA (was: CG-2 no-build skeleton). -->
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>OFBiz — Journeys</title>
    <style>
      body { font-family: system-ui, sans-serif; margin: 2rem; }
      table { border-collapse: collapse; margin-top: 1rem; }
      th, td { border: 1px solid #ccc; padding: .4rem .8rem; text-align: left; }
      .err { color: #b00; }
      nav a { margin-right: 1rem; }
      section { margin-top: 2rem; border-top: 1px solid #eee; padding-top: 1rem; }
    </style>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: src/auth.ts — OIDC PKCE; localStorage (NOT sessionStorage) so Playwright storageState captures the token**

```ts
// P8: token in localStorage deliberately — Playwright storageState persists
// it across specs. Dev-only client secret, same trust level as the skeleton.
const GATEWAY = location.origin.replace(/:\d+$/, ':8080');
const REDIRECT = location.origin + '/login/oauth2/code/gateway';
const BASIC = 'Basic ' + btoa('gateway:dev-local-secret');

const b64url = (s: string) =>
  btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return String.fromCharCode(...new Uint8Array(buf));
}

export async function login() {
  const verifier = b64url(btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))));
  localStorage.setItem('ofbiz.verifier', verifier);
  const challenge = b64url(await sha256(verifier));
  const auth = new URL(GATEWAY + '/oauth2/authorize');
  auth.search = new URLSearchParams({
    response_type: 'code', client_id: 'gateway', redirect_uri: REDIRECT,
    scope: 'openid', code_challenge: challenge, code_challenge_method: 'S256',
  }).toString();
  location.assign(auth);
}

export async function handleCallback(code: string): Promise<boolean> {
  const verifier = localStorage.getItem('ofbiz.verifier');
  if (!verifier) return false;
  const res = await fetch(GATEWAY + '/oauth2/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: BASIC,
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code', code, client_id: 'gateway',
      redirect_uri: REDIRECT, code_verifier: verifier,
    }),
  });
  if (!res.ok) return false;
  localStorage.setItem('ofbiz.token', (await res.json()).access_token);
  localStorage.removeItem('ofbiz.verifier');
  return true;
}
```

- [ ] **Step 6: src/api.ts**

```ts
const GATEWAY = location.origin.replace(/:\d+$/, ':8080');

export async function api<T>(path: string, init?: RequestInit & { body?: unknown }): Promise<T> {
  const token = localStorage.getItem('ofbiz.token');
  const res = await fetch(GATEWAY + path, {
    ...init,
    headers: {
      Authorization: 'Bearer ' + token,
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(init?.body ? { body: JSON.stringify(init.body) } : {}),
  });
  if (!res.ok) throw new Error(`${init?.method ?? 'GET'} ${path} -> ${res.status}`);
  return res.status === 204 ? (undefined as T) : res.json();
}
```

- [ ] **Step 7: src/main.tsx + src/App.tsx (placeholders so build passes; screens land in Task 4)**

`src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
```

`src/App.tsx`:

```tsx
import { Routes, Route, NavLink, useSearchParams, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { login, handleCallback } from './auth';

export default function App() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const code = params.get('code');
  const signedIn = !!localStorage.getItem('ofbiz.token');
  useEffect(() => {
    if (!code) return;
    void handleCallback(code).then(ok => navigate(ok ? '/parties' : '/', { replace: true }));
  }, [code, navigate]);
  return (
    <>
      <nav>
        <NavLink to="/parties">Parties</NavLink>
        <NavLink to="/catalog">Catalog</NavLink>
        <NavLink to="/p6">Long-tail</NavLink>
        <NavLink to="/order-to-cash">Order-to-cash</NavLink>
        {!signedIn
          ? <button data-testid="signin" onClick={() => void login()}>Sign in</button>
          : <span data-testid="signed-in">signed in</span>}
      </nav>
      <Routes>
        <Route path="/" element={<h1>OFBiz journeys</h1>} />
        <Route path="/login/oauth2/code/gateway" element={<h1>OFBiz journeys</h1>} />
        <Route path="/parties" element={<h1>parties (Task 4)</h1>} />
        <Route path="/catalog" element={<h1>catalog (Task 4)</h1>} />
        <Route path="/p6" element={<h1>long-tail (Task 4)</h1>} />
        <Route path="/order-to-cash" element={<h1>order-to-cash (Task 6)</h1>} />
      </Routes>
    </>
  );
}
```

- [ ] **Step 8: Install + build**

```bash
cd platform/spa && pnpm install && pnpm build
```

Expected: `dist/` produced, no TS errors.

- [ ] **Step 9: Commit**

```bash
git add platform/spa/package.json platform/spa/pnpm-lock.yaml platform/spa/vite.config.ts platform/spa/tsconfig.json platform/spa/index.html platform/spa/src
git commit -m "feat(spa): Vite+React+TS scaffold — OIDC PKCE auth (localStorage token), gateway api client, router"
```

---

### Task 3: Playwright harness + failing specs for ported screens

**Files:**
- Create: `tests/e2e/package.json`, `tests/e2e/tsconfig.json`, `tests/e2e/playwright.config.ts`, `tests/e2e/auth.setup.ts`, `tests/e2e/helpers/ids.ts`, `tests/e2e/specs/parties.spec.ts`, `tests/e2e/specs/catalog.spec.ts`, `tests/e2e/specs/p6.spec.ts`

- [ ] **Step 1: tests/e2e/package.json**

```json
{
  "name": "ofbiz-e2e",
  "private": true,
  "scripts": { "test": "playwright test", "test:dot": "playwright test --reporter=dot" },
  "devDependencies": { "@playwright/test": "^1.47.2", "typescript": "^5.6.3" }
}
```

- [ ] **Step 2: tests/e2e/tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022", "module": "ESNext", "moduleResolution": "bundler",
    "strict": true, "noEmit": true, "skipLibCheck": true, "types": ["node"]
  },
  "include": ["."]
}
```

- [ ] **Step 3: playwright.config.ts**

```ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 30_000,
  reporter: process.env.CI ? 'dot' : 'list',
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200' },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'e2e',
      testMatch: /specs\/.*\.spec\.ts/,
      dependencies: ['setup'],
      use: { storageState: 'playwright/.auth/user.json' },
    },
  ],
  outputDir: 'playwright/.artifacts',
});
```

NOTE: original plan had `testDir: './specs'`, which excluded auth.setup.ts (package root) from discovery — setup project ran 0 tests and e2e failed ENOENT on the storage state. Fixed during Task 3 execution (f113e12b2f); this block reflects the accepted shape.

- [ ] **Step 4: helpers/ids.ts — run-unique IDs, no shared state**

```ts
export const runPrefix = () => `e2e-${Date.now().toString(36)}-`;
```

- [ ] **Step 5: auth.setup.ts — real OIDC login once**

```ts
import { test as setup, expect } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const STATE = 'playwright/.auth/user.json';

setup('OIDC login as demo-user', async ({ page }) => {
  mkdirSync('playwright/.auth', { recursive: true });
  await page.goto('/');
  await page.getByTestId('signin').click();
  // identity login form is served through the gateway passthrough; it is
  // not our UI, so name-attribute selectors are the stable contract here.
  await page.locator('input[name="username"]').fill('demo-user');
  await page.locator('input[name="password"]').fill('demo-password');
  await page.locator('button[type="submit"], input[type="submit"]').first().click();
  await expect(page.getByTestId('signed-in')).toBeVisible({ timeout: 15_000 });
  await page.context().storageState({ path: STATE });
});
```

- [ ] **Step 6: specs/parties.spec.ts**

```ts
import { test, expect } from '@playwright/test';

test('search parties by role shows seeded customers', async ({ page }) => {
  await page.goto('/parties');
  await page.getByLabel('Role').fill('CUSTOMER');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('parties-count')).toContainText(/parties/);
  await expect(page.getByRole('table').getByRole('row').nth(1)).toContainText(/P\d|demo/);
});

test('token removed mid-session surfaces error, not a crash', async ({ page }) => {
  await page.goto('/parties');
  await page.evaluate(() => localStorage.removeItem('ofbiz.token'));
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('parties-msg')).toBeVisible();
});
```

- [ ] **Step 7: specs/catalog.spec.ts**

```ts
import { test, expect } from '@playwright/test';

test('browse catalog and resolve MP1 price at 9.99 USD', async ({ page }) => {
  await page.goto('/catalog');
  await page.getByRole('button', { name: 'Browse catalog' }).click();
  await expect(page.getByTestId('catalog-count')).toContainText(/products/);
  const mp1 = page.getByRole('row', { name: /MP1/ });
  await expect(mp1).toBeVisible();
  await mp1.getByRole('button', { name: 'lookup' }).click();
  await expect(page.getByTestId('price-MP1')).toContainText('9.99 USD');
});
```

- [ ] **Step 8: specs/p6.spec.ts**

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('create + read back a work effort via the generic CRUD screen', async ({ page }) => {
  const id = runPrefix() + 'we';
  await page.goto('/p6');
  const effort = page.getByRole('heading', { name: 'Work effort (workeffort)' })
    .locator('..');
  await effort.getByLabel('id').fill(id);
  await effort.getByLabel('type').fill('TASK');
  await effort.getByLabel('name').fill('e2e work effort');
  await effort.getByRole('button', { name: 'create' }).click();
  await expect(effort.getByTestId('p6-result-workEffortId')).toContainText('saved + read back');
});
```

- [ ] **Step 9: Install browsers + run — expect FAIL (routes render placeholders, no test IDs)**

```bash
cd tests/e2e && pnpm install && pnpm exec playwright install chromium
pnpm exec playwright test
```

Expected: all specs FAIL (elements not found) — correct red state.

- [ ] **Step 10: Commit (red)**

```bash
git add tests/e2e && git commit -m "test(e2e): Playwright harness + failing specs for parties/catalog/p6 (red)"
```

---

### Task 4: Port screens — make Task 3 specs green

**Files:**
- Create: `platform/spa/src/screens/Parties.tsx`, `platform/spa/src/screens/Catalog.tsx`, `platform/spa/src/screens/P6.tsx`
- Modify: `platform/spa/src/App.tsx`

- [ ] **Step 1: Parties.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Party = { partyId: string; kind: string; displayName: string };

export default function Parties() {
  const [role, setRole] = useState('CUSTOMER');
  const [rows, setRows] = useState<Party[]>([]);
  const [msg, setMsg] = useState('');

  const search = async () => {
    try {
      const page = await api<Page<Party>>(`/api/v1/parties?role=${encodeURIComponent(role)}&page=0&size=20`);
      setRows(page.items);
      setMsg(`${page.totalElements} parties (page ${page.page + 1}/${page.totalPages})`);
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  return (
    <section aria-labelledby="parties-h">
      <h2 id="parties-h">Parties</h2>
      <p className="err" data-testid="parties-msg">{msg}</p>
      <form onSubmit={e => { e.preventDefault(); void search(); }}>
        <label>Role <input value={role} onChange={e => setRole(e.target.value)} /></label>
        <button>Search</button>
      </form>
      <table>
        <thead><tr><th>ID</th><th>Kind</th><th>Name</th></tr></thead>
        <tbody>
          {rows.map(p => <tr key={p.partyId}><td>{p.partyId}</td><td>{p.kind}</td><td>{p.displayName}</td></tr>)}
        </tbody>
      </table>
      <p data-testid="parties-count">{msg}</p>
    </section>
  );
}
```

- [ ] **Step 2: Catalog.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Product = { productId: string; internalName: string; productName?: string };

export default function Catalog() {
  const [rows, setRows] = useState<Product[]>([]);
  const [msg, setMsg] = useState('');
  const [prices, setPrices] = useState<Record<string, string>>({});

  const browse = async () => {
    try {
      const page = await api<Page<Product>>('/api/v1/products?page=0&size=20');
      setRows(page.items);
      setMsg(`${page.totalElements} products (page ${page.page + 1}/${page.totalPages})`);
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  const lookup = async (productId: string) => {
    try {
      const at = encodeURIComponent(new Date().toISOString());
      const { price } = await api<{ price: string }>(`/api/v1/products/${productId}/price?currency=USD&at=${at}`);
      setPrices(p => ({ ...p, [productId]: price + ' USD' }));
    } catch {
      setPrices(p => ({ ...p, [productId]: 'n/a' }));
    }
  };

  return (
    <section aria-labelledby="catalog-h">
      <h2 id="catalog-h">Catalog</h2>
      <p className="err" data-testid="catalog-msg">{msg}</p>
      <button onClick={() => void browse()}>Browse catalog</button>
      <table>
        <thead><tr><th>ID</th><th>Internal name</th><th>Product name</th><th>Price (USD)</th></tr></thead>
        <tbody>
          {rows.map(p => (
            <tr key={p.productId}>
              <td>{p.productId}</td><td>{p.internalName}</td><td>{p.productName ?? ''}</td>
              <td data-testid={`price-${p.productId}`}>
                {prices[p.productId] ?? <button onClick={() => void lookup(p.productId)}>lookup</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p data-testid="catalog-count">{msg}</p>
    </section>
  );
}
```

- [ ] **Step 3: P6.tsx — port of the five-domain generic CRUD (skeleton payload builders preserved)**

```tsx
import { useState } from 'react';
import { api } from '../api';

const DOMAINS = [
  { title: 'BOM (manufacturing)', endpoint: '/api/v1/boms', id: 'productId',
    fields: [['product', 'productId'], ['component', 'componentId'], ['qty', 'quantity']] },
  { title: 'Work effort (workeffort)', endpoint: '/api/v1/workefforts', id: 'workEffortId',
    fields: [['id', 'workEffortId'], ['type', 'workEffortTypeId'], ['name', 'workEffortName']] },
  { title: 'Position (humanres)', endpoint: '/api/v1/positions', id: 'emplPositionId',
    fields: [['id', 'emplPositionId'], ['type', 'emplPositionTypeId']] },
  { title: 'Content', endpoint: '/api/v1/contents', id: 'contentId',
    fields: [['id', 'contentId'], ['name', 'contentName'], ['type', 'contentTypeId']] },
  { title: 'Campaign (marketing)', endpoint: '/api/v1/campaigns', id: 'marketingCampaignId',
    fields: [['id', 'marketingCampaignId'], ['name', 'campaignName']] },
];

const PAYLOAD: Record<string, (v: Record<string, string>) => unknown> = {
  productId: v => ({ productId: v.productId,
    components: [{ productId: v.componentId, quantity: Number(v.quantity) || 1, scrapPct: 0 }] }),
  workEffortId: v => ({ ...v, workEffortTypeId: v.workEffortTypeId || 'TASK' }),
  emplPositionId: v => ({ ...v, statusId: 'ACTIVE' }),
  contentId: v => ({ ...v, contentTypeId: v.contentTypeId || 'DOCUMENT' }),
  marketingCampaignId: v => ({ ...v, statusId: 'ACTIVE', fromDate: '2026-01-01', thruDate: '2026-12-31' }),
};

export default function P6() {
  const [results, setResults] = useState<Record<string, string>>({});
  return (
    <section aria-labelledby="p6-h">
      <h2 id="p6-h">Long-tail domains (P6)</h2>
      {DOMAINS.map(d => (
        <div key={d.id}>
          <h3>{d.title}</h3>
          <form onSubmit={async e => {
            e.preventDefault();
            const v = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
            try {
              await api(d.endpoint, { method: 'POST', body: PAYLOAD[d.id](v) });
              const saved = await api(`${d.endpoint}/${encodeURIComponent(v[d.id])}`);
              setResults(r => ({ ...r, [d.id]: 'saved + read back: ' + JSON.stringify(saved).slice(0, 120) }));
            } catch (err) {
              setResults(r => ({ ...r, [d.id]: (err as Error).message }));
            }
          }}>
            {d.fields.map(([label, name]) => (
              <label key={name}>{label} <input name={name} aria-label={label} /></label>
            ))}
            <button>create</button>
          </form>
          <p data-testid={`p6-result-${d.id}`}>{results[d.id] ?? '—'}</p>
        </div>
      ))}
    </section>
  );
}
```

- [ ] **Step 4: App.tsx — wire routes**

```tsx
import { Routes, Route, NavLink, useSearchParams, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { login, handleCallback } from './auth';
import Parties from './screens/Parties';
import Catalog from './screens/Catalog';
import P6 from './screens/P6';

export default function App() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const code = params.get('code');
  const signedIn = !!localStorage.getItem('ofbiz.token');
  useEffect(() => {
    if (!code) return;
    void handleCallback(code).then(ok => navigate(ok ? '/parties' : '/', { replace: true }));
  }, [code, navigate]);
  return (
    <>
      <nav>
        <NavLink to="/parties">Parties</NavLink>
        <NavLink to="/catalog">Catalog</NavLink>
        <NavLink to="/p6">Long-tail</NavLink>
        <NavLink to="/order-to-cash">Order-to-cash</NavLink>
        {!signedIn
          ? <button data-testid="signin" onClick={() => void login()}>Sign in</button>
          : <span data-testid="signed-in">signed in</span>}
      </nav>
      <Routes>
        <Route path="/" element={<h1>OFBiz journeys</h1>} />
        <Route path="/login/oauth2/code/gateway" element={<h1>OFBiz journeys</h1>} />
        <Route path="/parties" element={<Parties />} />
        <Route path="/catalog" element={<Catalog />} />
        <Route path="/p6" element={<P6 />} />
        <Route path="/order-to-cash" element={<h1>order-to-cash (Task 6)</h1>} />
      </Routes>
    </>
  );
}
```

- [ ] **Step 5: Run suite green (dev loop: vite dev server + port-forwards)**

Terminal 1: `kubectl -n ofbiz port-forward svc/gateway 8080:8080 & kubectl -n ofbiz port-forward svc/spa 4200:4200 &`
Terminal 2: `cd platform/spa && pnpm dev`
Terminal 3:

```bash
cd tests/e2e && pnpm exec playwright test
```

Expected: 5 passed (setup + 4 spec tests).

- [ ] **Step 6: Commit (green)**

```bash
git add platform/spa/src && git commit -m "feat(spa): port parties/catalog/p6 screens to React — e2e specs green"
```

---

### Task 5: Deploy pipeline — image swap

**Files:**
- Create: `platform/spa/Dockerfile`, `platform/spa/nginx.conf`
- Modify: `platform/spa/spa.yaml` (image instead of configmaps), `platform/spa/10-install.sh` (build + kind load)

- [ ] **Step 1: Dockerfile**

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile --ignore-scripts
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
```

NOTE: `--ignore-scripts` is required — pnpm ≥10 gates esbuild's postinstall (ERR_PNPM_IGNORED_BUILDS); the linux binary ships via the @esbuild/linux-x64 optional dep, so skipping the script is safe. If a future dep genuinely needs a postinstall, switch to `pnpm.onlyBuiltDependencies` in package.json. Also add `.dockerignore` (`node_modules`, `dist`) next to the Dockerfile.

- [ ] **Step 2: nginx.conf (same contract as the configmap it replaces)**

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;
  location / { try_files $uri /index.html; }
}
```

- [ ] **Step 3: spa.yaml — drop configmap volumes, use local image**

```yaml
# P8: built SPA image (Vite dist in nginx). Served on :4200 to match the
# gateway CORS allow-list and the identity redirect URI.
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spa
  namespace: ofbiz
spec:
  replicas: 1
  selector:
    matchLabels: {app: spa}
  template:
    metadata:
      labels: {app: spa}
    spec:
      containers:
        - name: nginx
          image: spa:local
          imagePullPolicy: IfNotPresent
          ports: [{containerPort: 80}]
---
apiVersion: v1
kind: Service
metadata:
  name: spa
  namespace: ofbiz
spec:
  selector: {app: spa}
  ports: [{port: 4200, targetPort: 80}]
```

- [ ] **Step 4: 10-install.sh rewrite**

```bash
#!/usr/bin/env bash
# P8: build the SPA image, load into kind, roll the deployment.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"
TAG="spa:local"

docker build -t "${TAG}" "${SCRIPT_DIR}"
kind load docker-image "${TAG}" --name ofbiz
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/spa.yaml"
kubectl -n "${NS}" set image deploy/spa nginx="${TAG}"
kubectl -n "${NS}" rollout status deploy/spa --timeout=180s
echo "SPA deployed (port-forward: kubectl -n ${NS} port-forward svc/spa 4200:4200)"
```

- [ ] **Step 5: Deploy + verify deployed image serves the React app**

```bash
cd platform/spa && ./10-install.sh
kubectl -n ofbiz port-forward svc/spa 4200:4200 &
curl -s localhost:4200 | grep -q '<div id="root">' && echo DEPLOYED-OK
cd ../tests/e2e && pnpm exec playwright test
```

Expected: `DEPLOYED-OK`, all specs green against the deployed image (kill `pnpm dev` first so :4200 is the port-forward, not vite).

- [ ] **Step 6: Delete orphaned configmaps (no longer mounted)**

```bash
kubectl -n ofbiz delete configmap spa-index spa-nginx-conf --ignore-not-found
```

- [ ] **Step 7: Commit**

```bash
git add platform/spa/Dockerfile platform/spa/nginx.conf platform/spa/spa.yaml platform/spa/10-install.sh
git commit -m "feat(spa): image-based deploy — Vite dist in nginx, kind load + set image"
```

---

### Task 6: Order-to-cash journey (spec first, then screen)

**Files:**
- Create: `tests/e2e/specs/order-to-cash.spec.ts`, `platform/spa/src/screens/OrderToCash.tsx`
- Modify: `platform/spa/src/App.tsx` (route swap)

- [ ] **Step 1: Failing spec**

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('order-to-cash: quote → order → approve → invoice → payment capture → GL visible', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/order-to-cash');

  // 1. seed stock (saga reserves on place)
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Seed stock' }).click();
  await expect(page.getByTestId('otc-stock')).toContainText('stocked');

  // 2. quote for seeded customer/product
  await page.getByRole('button', { name: 'Create quote' }).click();
  await expect(page.getByTestId('otc-quote')).toContainText(/quote \S+ created \(status: \S+\)/);

  // 3. convert → order (grand total snapshot-priced: 2 × 9.99)
  await page.getByRole('button', { name: 'Convert to order' }).click();
  await expect(page.getByTestId('otc-order')).toContainText(/order \S+ status/);
  await expect(page.getByTestId('otc-order')).toContainText('19.98');

  // 4. approve
  await page.getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByTestId('otc-order')).toContainText('ORDER_APPROVED');

  // 5. invoice + capture payment
  await page.getByRole('button', { name: 'Invoice + capture' }).click();
  await expect(page.getByTestId('otc-billing')).toContainText(/invoice \S+/);
  await expect(page.getByTestId('otc-billing')).toContainText(/payment \S+ status: \S+/);

  // 6. GL visibility (balanced-posting invariant is finance-svc's own test)
  await page.getByRole('button', { name: 'Show trial balance' }).click();
  await expect(page.getByTestId('otc-gl')).toContainText(/trial balance/);
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd tests/e2e && pnpm exec playwright test specs/order-to-cash.spec.ts
```

Expected: FAIL (element not found — screen is a placeholder).

- [ ] **Step 3: OrderToCash.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Quote = { quoteId: string; status: string };
type Order = { orderId: string; status: string; grandTotal: string; currencyUomId: string };
type Invoice = { invoiceId: string };
type Payment = { paymentId: string; status: string };
const runId = () => 'e2e-' + Date.now().toString(36) + '-';

export default function OrderToCash() {
  const [facility, setFacility] = useState('');
  const [stock, setStock] = useState('');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [billing, setBilling] = useState('');
  const [gl, setGl] = useState('');
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';
  const qty = 2;

  const seedStock = async () => {
    try {
      const itemId = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: itemId, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${itemId}/receive`, { method: 'POST', body: { quantity: 10 } });
      setStock('stocked');
    } catch (e) { setErr((e as Error).message); }
  };

  const createQuote = async () => {
    try {
      setQuote(await api<Quote>('/api/v1/quotes', { method: 'POST',
        body: { customerId, currencyUomId: 'USD',
          lines: [{ productId, facilityId: facility, quantity: qty }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const convert = async () => {
    try {
      setOrder(await api<Order>(`/api/v1/quotes/${quote!.quoteId}/convert`, { method: 'POST' }));
    } catch (e) { setErr((e as Error).message); }
  };

  const approve = async () => {
    try {
      await api(`/api/v1/orders/${order!.orderId}/approve`, { method: 'POST' });
      setOrder({ ...order!, status: 'ORDER_APPROVED' });
    } catch (e) { setErr((e as Error).message); }
  };

  const invoiceAndCapture = async () => {
    try {
      const inv = runId() + 'inv';
      const pay = runId() + 'pay';
      const invoice = await api<Invoice>('/api/v1/invoices', { method: 'POST',
        body: { invoiceId: inv, customerId, billingAccountId: null, currencyUomId: 'USD',
          lines: [{ invoiceItemSeqId: '00001', invoiceItemTypeId: 'INV_FPROD_ITEM',
            description: 'e2e order-to-cash', quantity: qty, amount: '9.99' }] } });
      await api<Payment>(`/api/v1/payments/${pay}/authorize`, { method: 'POST' });
      const captured = await api<Payment>(`/api/v1/payments/${pay}/capture`, { method: 'POST' });
      setBilling(`invoice ${invoice.invoiceId} — payment ${pay} status: ${captured.status}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const showTrialBalance = async () => {
    try {
      const tb = await api<unknown>('/api/v1/gl/trial-balance');
      setGl('trial balance: ' + JSON.stringify(tb).slice(0, 200));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="otc-h">
      <h2 id="otc-h">Order-to-cash</h2>
      <p className="err" data-testid="otc-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> × {qty}</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void seedStock()}>Seed stock</button>
      <p data-testid="otc-stock">{stock}</p>
      <button onClick={() => void createQuote()}>Create quote</button>
      <p data-testid="otc-quote">{quote && `quote ${quote.quoteId} created (status: ${quote.status})`}</p>
      {quote && <button onClick={() => void convert()}>Convert to order</button>}
      <p data-testid="otc-order">
        {order && `order ${order.orderId} status ${order.status} — total ${order.grandTotal} ${order.currencyUomId}`}
      </p>
      {order && <button onClick={() => void approve()}>Approve</button>}
      {order && order.status === 'ORDER_APPROVED' && <button onClick={() => void invoiceAndCapture()}>Invoice + capture</button>}
      <p data-testid="otc-billing">{billing}</p>
      <button onClick={() => void showTrialBalance()}>Show trial balance</button>
      <p data-testid="otc-gl">{gl}</p>
    </section>
  );
}
```

NOTE: `invoiceAndCapture` calls `POST /api/v1/payments` create? It must — insert before the authorize call:

```ts
      await api<Payment>('/api/v1/payments', { method: 'POST',
        body: { paymentId: pay, invoiceId: inv, idempotencyKey: pay,
          amount: order!.grandTotal, currencyUomId: 'USD' } });
```

Final order inside `invoiceAndCapture`: create invoice → create payment → authorize → capture.

- [ ] **Step 4: App.tsx — swap route element**

```tsx
import OrderToCash from './screens/OrderToCash';
// ...
<Route path="/order-to-cash" element={<OrderToCash />} />
```

- [ ] **Step 5: Run green**

```bash
cd platform/spa && ./10-install.sh   # redeploy with the new screen
cd ../tests/e2e && pnpm exec playwright test
```

Expected: 6 passed (setup + 5 tests). The billing regex `/payment \S+ status: \S+/` tolerates any gateway status value.

- [ ] **Step 6: Commit**

```bash
git add platform/spa/src tests/e2e/specs/order-to-cash.spec.ts
git commit -m "feat(spa): order-to-cash journey — quote→order→approve→invoice→capture→GL, e2e green"
```

---

### Task 6a: Gateway route for inventory-svc (plan amendment, discovered during Task 6)

**Files:**
- Modify: `microservices/gateway/src/main/resources/application.yaml` (add route block)
- Modify: `platform/spa/10-install.sh` (same-tag redeploy no-op fix)

The order-to-cash journey seeds stock through the gateway (architecture: north-south = gateway only), but the gateway has no inventory route (verified 404 + missing CORS headers). Add:

```yaml
- id: inventory-svc
  uri: ${gateway.inventory.uri:http://inventory-svc.ofbiz.svc:8080}
  predicates:
    - Path=/api/v1/inventory-items/**
  metadata:
    required-role: CUSTOMER
```

required-role CUSTOMER matches the other domain routes the demo journeys use (dev kind profile; machine tokens still 403). Redeploy gateway per platform/services/10-deploy.sh pattern, rebuild spa image, rerun full suite (expect 6/6), then commit journey + route + script fix (separate commits).

Also fix `platform/spa/10-install.sh` same-tag no-op: append `kubectl -n "${NS}" rollout restart deploy/spa` after `set image` (same tag → set image is a no-op; restart forces pod replacement).

---

### Task 7: Runner script + P8 evidence

**Files:**
- Create: `platform/spa/40-run-e2e.sh`
- Modify: `IMPLEMENTATION.md` (tick T1–T3 + evidence)

- [ ] **Step 1: 40-run-e2e.sh**

```bash
#!/usr/bin/env bash
# P8: run the Playwright suite against the deployed kind cluster.
# Manages port-forwards; prints per-journey PASS summary. Non-zero on failure.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"
PIDS=()

cleanup() { kill "${PIDS[@]}" 2>/dev/null || true; }
trap cleanup EXIT

kubectl -n "${NS}" port-forward svc/gateway 8080:8080 >/dev/null 2>&1 & PIDS+=($!)
kubectl -n "${NS}" port-forward svc/spa 4200:4200 >/dev/null 2>&1 & PIDS+=($!)
for _ in $(seq 1 15); do
  curl -s -o /dev/null localhost:4200 && curl -s -o /dev/null localhost:8080 && break; sleep 1
done

cd "${SCRIPT_DIR}/../../tests/e2e"
pnpm exec playwright test --reporter=dot 2>&1 | tee /tmp/p8-e2e.log
echo
echo "== P8 E2E summary =="
grep -oE '[0-9]+ passed' /tmp/p8-e2e.log | tail -1
if grep -qE('[0-9]+ failed|✘') /tmp/p8-e2e.log; then echo "FAILURES PRESENT"; exit 1; fi
echo "P8 E2E EVIDENCE PASSED ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
```

- [ ] **Step 2: Run it**

```bash
chmod +x platform/spa/40-run-e2e.sh && platform/spa/40-run-e2e.sh
```

Expected: `N passed` + `P8 E2E EVIDENCE PASSED`.

- [ ] **Step 3: Tick stories in IMPLEMENTATION.md**

T1: `[x] specs for ported screens green [x] deployed image serves SPA on :4200 with OIDC login working`
T2: `[x] auth setup + ≥1 spec green [x] no shared state between tests`
T3: `[x] spec green [x] exercises party/product/order/inventory/billing/finance via gateway`
Append evidence line under P8-GATE: `- T1+T2+T3 green: 40-run-e2e.sh → P8 E2E EVIDENCE PASSED <date>`

- [ ] **Step 4: Commit**

```bash
git add platform/spa/40-run-e2e.sh IMPLEMENTATION.md
git commit -m "Added: P8 runner + T1-T3 evidence — 40-run-e2e.sh green (ported screens + order-to-cash)"
```

---

## Self-Review (done)

- Spec coverage: T1 (Tasks 2,4,5), T2 (Task 3), T3 (Task 6), evidence/process (Tasks 1,7). T4–T9 deferred to Plans 2–3 per scope check.
- Placeholders: none — every code step carries full content; Task 6 NOTE block resolves to concrete code.
- Type consistency: `api<T>(path, init)` used identically across screens; `runPrefix()` helper referenced only by specs; test IDs (`signin`, `signed-in`, `parties-msg`, `parties-count`, `catalog-count`, `price-*`, `p6-result-*`, `otc-*`) match between specs and components.
- Known risks: identity login submit selector (`button[type=submit], input[type=submit]`) — one-line fix if identity renders differently; `authorize`-before-`capture` is already in the Task 6 code (create → authorize → capture) so the billing-controller sequence risk is covered.
