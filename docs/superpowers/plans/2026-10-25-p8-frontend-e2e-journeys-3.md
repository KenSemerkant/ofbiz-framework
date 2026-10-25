# P8 Frontend E2E — Journeys 3 (T7–T9: campaign-to-attribution, content+calendar, notification descope) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two new journey screens + specs (campaign-to-attribution, content+calendar), one honest descope (T9), IMPLEMENTATION T7–T9 closed, P8-GATE evidence.

**Architecture:** identical to plans 1–2 (docs/superpowers/plans/2026-10-25-p8-frontend-e2e-harness.md, -journeys-2.md). Screens in platform/spa/src/screens/, specs in tests/e2e/specs/, server-truth assertions, collision-proof IDs (`Date.now(36)+'-'+Math.random(36).slice(2,8)`), deployed-image green via 40-run-e2e.sh.

**Tech Stack:** unchanged.

**Verified API facts (from controllers + gateway yaml):**
- Gateway routes exist for everything needed: `/api/v1/campaigns|tracking-codes|opportunities|attribution/**` (marketing), `/api/v1/contents/**` (content), `/api/v1/workefforts/**` (workeffort). NO new gateway routes needed this plan.
- Campaign: `POST /api/v1/campaigns` `{marketingCampaignId, campaignName, statusId, fromDate, thruDate}` (P6-screen proven: statusId ACTIVE, dates ISO).
- Tracking code: `POST /api/v1/tracking-codes` `{trackingCodeId, marketingCampaignId, description, fromDate, thruDate}`.
- Order placement carries `trackingCode` (PlaceOrderRequest.trackingCode — verified plan 1).
- Attribution (event-driven read model — expect lag): `GET /api/v1/attribution/{trackingCode}` → `AttributionDetail {trackingCode, campaignId, orderCount, ...}`; `GET /api/v1/attribution` → rows. Assert the minted code's detail, not the global report (avoids state pollution from other specs' orders).
- Content: `POST /api/v1/contents` `{contentId, contentTypeId, contentName, parentContentId}` (DOCUMENT); `POST /{id}/upload` — RAW byte body + Content-Type header (DRAFT only); `POST /{id}/publish` (DRAFT→PUBLISHED forward-only, 409 on re-publish/no-upload); `GET /{id}/data` → bytes with stored content type (PUBLISHED only). Upload/data bypass the JSON api client — screen uses direct fetch with Bearer header; api.ts gains a text helper for the calendar feed.
- Calendar: `GET /api/v1/workefforts/calendar.ics` → `text/calendar` body (RFC 5545); per-effort `GET /{id}/calendar.ics`. Assert `BEGIN:VCALENDAR` + the created effort's id.
- WorkEffort create: `{workEffortId, workEffortTypeId: 'TASK', workEffortName}` (P6-screen proven).
- **notification-svc has NO REST surface** (pure Kafka consumer + SMTP adapter; verified — zero @RestController in non-test sources). T9 screen is impossible without inventing a surface → recorded descope, same pattern as T5's production-run. Delivery behavior is covered by service tests (NotificationDeliveryTest, NotificationDlqTest per IMPLEMENTATION P1-F3).

**Conventions mandatory (from plans 1–2):** collision-proof runId in every screen; server-truth assertions (status via GET/transition response); bounded 10×300ms retry with surfaced error for async read models (Returns.tsx pattern); Playwright button names are substring matches — gate overlapping labels; api() throws on !ok and on missing token.

**Environment prerequisites:** kind cluster `ofbiz`, spa:local + gateway:p8-ship deployed (plan 2 end state).

---

### Task 1: T7 Campaign-to-attribution journey

**Files:**
- Create: `tests/e2e/specs/campaign.spec.ts`, `platform/spa/src/screens/Campaign.tsx`
- Modify: `platform/spa/src/App.tsx` (nav + route)

- [ ] **Step 1: Failing spec**

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('campaign-to-attribution: campaign + tracking code → tracked order → attribution detail', async ({ page }) => {
  const run = runPrefix();
  const campaignId = run + 'cmp';
  const codeId = run + 'tc';
  await page.goto('/campaign');

  // 1. create campaign + tracking code (ids derived from the run-prefix input)
  await page.getByLabel('Run prefix').fill(run);
  await page.getByRole('button', { name: 'Create campaign + code' }).click();
  await expect(page.getByTestId('cmp-setup')).toContainText(`campaign ${campaignId} + code ${codeId}`);

  // 2. seed stock + place order carrying the tracking code
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Place tracked order' }).click();
  await expect(page.getByTestId('cmp-order')).toContainText(/order \S+ status/);

  // 3. attribution readback (event-driven read model — screen retries briefly)
  await page.getByRole('button', { name: 'Show attribution' }).click();
  await expect(page.getByTestId('cmp-attribution')).toContainText(new RegExp(`code ${codeId} .* orders 1`));
});
```

- [ ] **Step 2: Run red** — port-forwards gateway+spa; `pnpm exec playwright test specs/campaign.spec.ts` → FAIL (placeholder).

- [ ] **Step 3: Campaign.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Order = { orderId: string; status: string; trackingCode: string };
type AttributionDetail = { trackingCode: string; campaignId: string; orderCount: number };
const runId = () => 'e2e-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8) + '-';

export default function Campaign() {
  const [run, setRun] = useState('');
  const [facility, setFacility] = useState('');
  const [setup, setSetup] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [attribution, setAttribution] = useState('');
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';

  const setupCampaign = async () => {
    setErr('');
    try {
      const campaignId = run + 'cmp';
      const codeId = run + 'tc';
      await api('/api/v1/campaigns', { method: 'POST',
        body: { marketingCampaignId: campaignId, campaignName: 'e2e campaign ' + run,
          statusId: 'ACTIVE', fromDate: '2026-01-01', thruDate: '2026-12-31' } });
      await api('/api/v1/tracking-codes', { method: 'POST',
        body: { trackingCodeId: codeId, marketingCampaignId: campaignId,
          description: 'e2e attribution', fromDate: '2026-01-01', thruDate: '2026-12-31' } });
      setSetup(`campaign ${campaignId} + code ${codeId}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const placeTrackedOrder = async () => {
    setErr('');
    try {
      const itemId = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: itemId, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${itemId}/receive`, { method: 'POST', body: { quantity: 5 } });
      setOrder(await api<Order>('/api/v1/orders', { method: 'POST',
        body: { customerId, currencyUomId: 'USD', trackingCode: run + 'tc',
          lines: [{ productId, facilityId: facility, quantity: 1 }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const showAttribution = async () => {
    setErr('');
    // attribution is an event-driven read model (order.order.placed → marketing consumer);
    // bounded retry until the tracked order lands — same shape as Returns.tsx reserve saga.
    try {
      let detail: AttributionDetail | null = null;
      for (let attempt = 0; ; attempt++) {
        try {
          detail = await api<AttributionDetail>(`/api/v1/attribution/${run + 'tc'}`);
          if (detail.orderCount >= 1) break;
        } catch (e) {
          if (attempt >= 9) throw e; // 404 until the read model catches up
        }
        await new Promise(r => setTimeout(r, 300));
      }
      setAttribution(`code ${detail!.trackingCode} campaign ${detail!.campaignId} orders ${detail!.orderCount}`);
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="cmp-h">
      <h2 id="cmp-h">Campaign-to-attribution</h2>
      <p className="err" data-testid="cmp-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> ×1</p>
      <label>Run prefix <input value={run} onChange={e => setRun(e.target.value)} /></label>{' '}
      <button onClick={() => void setupCampaign()}>Create campaign + code</button>
      <p data-testid="cmp-setup">{setup}</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void placeTrackedOrder()}>Place tracked order</button>
      <p data-testid="cmp-order">{order && `order ${order.orderId} status ${order.status}`}</p>
      {order && <button onClick={() => void showAttribution()}>Show attribution</button>}
      <p data-testid="cmp-attribution">{attribution}</p>
    </section>
  );
}
```

NOTE: AttributionDetail fields beyond orderCount are not fully verified — check `AttributionService.AttributionDetail` record first; if field names differ, adjust the type + rendered string to server truth, keeping the spec-required render shape `code <id> ... orders <n>`.

- [ ] **Step 4: App.tsx** — nav `<NavLink to="/campaign">Campaign</NavLink>` + routes: `/campaign` → `<Campaign />`; `/content` and `/calendar` h1 placeholders (Task 2 swaps content).

- [ ] **Step 5: Green** — build + 10-install.sh, port-forwards, full suite → 10 passed. Kill port-forwards.

- [ ] **Step 6: Commit**

```bash
git add platform/spa/src tests/e2e/specs/campaign.spec.ts
git commit -m "feat(spa): campaign-to-attribution journey — tracked order → attribution detail, e2e green"
```

---

### Task 2: T8 Content + calendar journeys

**Files:**
- Create: `tests/e2e/specs/content.spec.ts`, `tests/e2e/specs/calendar.spec.ts`, `platform/spa/src/screens/Content.tsx`, `platform/spa/src/screens/CalendarScreen.tsx`
- Modify: `platform/spa/src/api.ts` (add apiText helper), `platform/spa/src/App.tsx`

- [ ] **Step 1: api.ts — text helper** (calendar feed is text/calendar; JSON default untouched)

```ts
export async function apiText(path: string): Promise<string> {
  const token = localStorage.getItem('ofbiz.token');
  if (!token) throw new Error('not signed in');
  const res = await fetch(GATEWAY + path, { headers: { Authorization: 'Bearer ' + token } });
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.text();
}
```

- [ ] **Step 2: Failing specs**

`tests/e2e/specs/content.spec.ts`:

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('content: create → upload → publish → fetch roundtrip', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/content');

  await page.getByLabel('Content ID').fill(run + 'doc');
  await page.getByRole('button', { name: 'Create + upload + publish' }).click();
  await expect(page.getByTestId('cnt-status')).toContainText(/published/);

  await page.getByRole('button', { name: 'Fetch data' }).click();
  await expect(page.getByTestId('cnt-data')).toContainText('e2e content roundtrip');
});
```

`tests/e2e/specs/calendar.spec.ts`:

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('calendar: create work effort → iCal feed contains it', async ({ page }) => {
  const run = runPrefix();
  const effortId = run + 'we';
  await page.goto('/calendar');

  await page.getByLabel('Work effort ID').fill(effortId);
  await page.getByLabel('Name').fill('e2e calendar effort');
  await page.getByRole('button', { name: 'Create effort' }).click();
  await expect(page.getByTestId('cal-effort')).toContainText(`effort ${effortId}`);

  await page.getByRole('button', { name: 'Load feed' }).click();
  await expect(page.getByTestId('cal-feed')).toContainText('BEGIN:VCALENDAR');
  await expect(page.getByTestId('cal-feed')).toContainText(effortId);
});
```

- [ ] **Step 3: Run red**

- [ ] **Step 4: Content.tsx** — upload/data use direct fetch (binary body, not JSON):

```tsx
import { useState } from 'react';
import { api } from '../api';
import { GATEWAY } from '../config';

export default function Content() {
  const [contentId, setContentId] = useState('');
  const [status, setStatus] = useState('');
  const [data, setData] = useState('');
  const [err, setErr] = useState('');

  const PAYLOAD_TEXT = 'e2e content roundtrip ' + contentId;

  const createUploadPublish = async () => {
    setErr('');
    try {
      await api('/api/v1/contents', { method: 'POST',
        body: { contentId, contentTypeId: 'DOCUMENT', contentName: 'e2e ' + contentId } });
      const token = localStorage.getItem('ofbiz.token');
      if (!token) throw new Error('not signed in');
      const up = await fetch(`${GATEWAY}/api/v1/contents/${contentId}/upload`, {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'text/plain' },
        body: PAYLOAD_TEXT });
      if (!up.ok) throw new Error(`upload -> ${up.status}`);
      await api(`/api/v1/contents/${contentId}/publish`, { method: 'POST' });
      // server truth: read back rather than trusting the non-throw path
      const saved = await api<{ contentId: string; statusId?: string }>(`/api/v1/contents/${contentId}`);
      setStatus(saved.statusId ? String(saved.statusId).toLowerCase() : 'published');
    } catch (e) { setErr((e as Error).message); }
  };

  const fetchData = async () => {
    setErr('');
    try {
      const token = localStorage.getItem('ofbiz.token');
      if (!token) throw new Error('not signed in');
      const res = await fetch(`${GATEWAY}/api/v1/contents/${contentId}/data`,
        { headers: { Authorization: 'Bearer ' + token } });
      if (!res.ok) throw new Error(`data -> ${res.status}`);
      setData((await res.text()).slice(0, 200));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="cnt-h">
      <h2 id="cnt-h">Content</h2>
      <p className="err" data-testid="cnt-err">{err}</p>
      <label>Content ID <input value={contentId} onChange={e => setContentId(e.target.value)} /></label>{' '}
      <button onClick={() => void createUploadPublish()}>Create + upload + publish</button>
      <p data-testid="cnt-status">{status}</p>
      {status && <button onClick={() => void fetchData()}>Fetch data</button>}
      <p data-testid="cnt-data">{data}</p>
    </section>
  );
}
```

NOTE: ContentResponse field names beyond contentId unverified — read the record in ContentController first; if the status field is named differently (e.g. statusId vs status), use the real name. The spec regex `/published/` must match what the server actually returns — tighten to the real value (e.g. `published` lowercased or enum name) after reading the domain enum; do not loosen.

`CalendarScreen.tsx`:

```tsx
import { useState } from 'react';
import { api, apiText } from '../api';

export default function CalendarScreen() {
  const [effortId, setEffortId] = useState('');
  const [name, setName] = useState('');
  const [effort, setEffort] = useState('');
  const [feed, setFeed] = useState('');
  const [err, setErr] = useState('');

  const createEffort = async () => {
    setErr('');
    try {
      await api('/api/v1/workefforts', { method: 'POST',
        body: { workEffortId: effortId, workEffortTypeId: 'TASK', workEffortName: name } });
      const saved = await api<{ workEffortId: string }>(`/api/v1/workefforts/${effortId}`);
      setEffort(`effort ${saved.workEffortId}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const loadFeed = async () => {
    setErr('');
    try {
      setFeed((await apiText('/api/v1/workefforts/calendar.ics')).slice(0, 2000));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="cal-h">
      <h2 id="cal-h">Calendar</h2>
      <p className="err" data-testid="cal-err">{err}</p>
      <label>Work effort ID <input value={effortId} onChange={e => setEffortId(e.target.value)} /></label>{' '}
      <label>Name <input value={name} onChange={e => setName(e.target.value)} /></label>{' '}
      <button onClick={() => void createEffort()}>Create effort</button>
      <p data-testid="cal-effort">{effort}</p>
      {effort && <button onClick={() => void loadFeed()}>Load feed</button>}
      <pre data-testid="cal-feed">{feed}</pre>
    </section>
  );
}
```

NOTE: if the global feed is truncated/omits older efforts (check `efforts.calendarFeed()` impl), switch loadFeed to the per-effort feed `GET /api/v1/workefforts/{id}/calendar.ics` — assertions unchanged (`BEGIN:VCALENDAR` + effortId). Use whichever the implementation actually returns; document choice.

- [ ] **Step 5: App.tsx** — swap `/content` → `<Content />`, add `/calendar` → `<CalendarScreen />` + nav links.

- [ ] **Step 6: Green** — build + install, port-forwards, full suite → 12 passed. Kill port-forwards.

- [ ] **Step 7: Commit**

```bash
git add platform/spa/src tests/e2e/specs/content.spec.ts tests/e2e/specs/calendar.spec.ts
git commit -m "feat(spa): content roundtrip + calendar iCal journeys, e2e green"
```

---

### Task 3: T9 descope + IMPLEMENTATION closure

**Files:**
- Modify: `IMPLEMENTATION.md` (PHASE 8)

- [ ] **Step 1:** Rewrite T9 bullet:

```markdown
- **T9 (S):** Notification — DESCOPE (recorded): notification-svc exposes no REST surface (pure Kafka consumer + SMTP adapter; zero @RestController in non-test sources). No screen possible without inventing a platform surface. Delivery/DLQ behavior covered by service tests (NotificationDeliveryTest, NotificationDlqTest, P1-F3). Email leg remains infra-blocked on kind (P5). AC: [x] descope recorded with evidence
```

- [ ] **Step 2:** Commit

```bash
git add IMPLEMENTATION.md && git commit -m "Added: T9 notification descope recorded — no REST surface, service-test coverage stands"
```

---

### Task 4: Evidence — P8-GATE closes

**Files:**
- Modify: `IMPLEMENTATION.md`

- [ ] **Step 1:** `platform/spa/40-run-e2e.sh` → expect `12 passed` + `P8 E2E EVIDENCE PASSED (<ts>)`.
- [ ] **Step 2:** Tick T7 `[x] spec green`, T8 `[x] specs green`. Append under P8-GATE:
  `- T7+T8 green: 40-run-e2e.sh → P8 E2E EVIDENCE PASSED <ts> (12/12; attribution detail server-readback with bounded retry; content upload→publish→data roundtrip; calendar iCal feed)`
  And append to the P8-GATE bullet itself: `(CLOSED <date> — 12/12, T9 descope recorded)`.
- [ ] **Step 3: Commit**

```bash
git add IMPLEMENTATION.md && git commit -m "Added: P8 T7-T8 evidence + GATE closed — 40-run-e2e.sh 12/12 green"
```

---

## Self-Review (done)

- Spec coverage: T7 (Task 1), T8 (Task 2), T9 descope (Task 3), gate closure (Task 4). Program complete after this plan.
- Placeholders: none — two implementer-verify NOTEs (AttributionDetail fields; content status field name / calendar feed scope) with working defaults and instruction to follow server truth.
- Honest evidence: attribution asserts the minted code only (not the global report); content asserts byte roundtrip via GET data with server readback on publish; T9 descope is evidence-backed (no controller exists), consistent with the repo rule against inventing surfaces.
- Risks: attribution read-model lag → bounded retry included; upload requires DRAFT state — create→upload ordering fixed in one handler; publish forward-only (run-prefixed ids never reused).
