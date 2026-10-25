# P8 Frontend E2E — Journeys 2 (T4–T6: returns, plan-to-ship, hire-to-retire) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three new journey screens + specs (returns, plan-to-ship, hire-to-retire), one gateway route (shipments), IMPLEMENTATION T4–T6 ticked with evidence.

**Architecture:** Identical to plan 1 (docs/superpowers/plans/2026-10-25-p8-frontend-e2e-harness.md) — Vite SPA screens in platform/spa/src/screens/, specs in tests/e2e/specs/, server-truth assertions after transitions, run-unique IDs, deployed-image green via 40-run-e2e.sh.

**Tech Stack:** unchanged (Vite 5, React 18, TS 5, Playwright, pnpm).

**AMENDMENT (Task 2 execution, 2026-10-25):** order-svc rejects returns unless the order is COMPLETED (ReturnService: "returns need COMPLETED", tested 409 on CREATED) and auto-completes the return before save — observable return status is `COMPLETED`, not `RECEIVED`. Task 2 screen therefore adds Approve → Complete steps (server re-fetch after each) gated before Create return; assertions expect COMPLETED. Also: ID minting gains a random suffix (`Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8)`) in helpers/ids.ts and screen runId() copies — ms-resolution IDs collided across parallel workers (duplicate-inventory 409 flake).

**Verified API facts (from controllers + gateway yaml):**
- Gateway: `/api/v1/returns/**`, `/api/v1/boms|routings|mrp/**`, `/api/v1/positions|employments|onboarding/**` routed. **`/api/v1/shipments/**` NOT routed** → Task 1 adds it (plan-1 Task 6a pattern).
- Returns: `POST /api/v1/returns` `{orderId, lines:[{productId,facilityId,quantity}]}` → 201 `{returnId, orderId, status, lines}`; status enum `RECEIVED|COMPLETED`, no complete endpoint → observable status is `RECEIVED`. Credit memo = outbox event `order.creditmemo.issued.v1` (ReturnService.java:20) — event-level, covered by service tests; NOT asserted in E2E (honest-evidence rule from plan 1).
- MRP: `POST /api/v1/mrp/run` (no body) → `{proposals:[{productId, quantity}]}`; deterministic per-product netting ledger. Proposal content depends on demand/supply state — spec asserts the run completes and renders, plus shipment flow success; does NOT assert a specific product appears in proposals.
- Production run: no REST surface (ManufacturingController exposes boms/routings/mrp only) → journey descope: MRP proposals + shipment flow. Recorded as deviation from the original journey sketch.
- Shipments: `POST /api/v1/shipments` `{shipmentId, orderId, items:[{productId, quantity, inventoryItemId}]}` → 201 `{shipmentId, orderId, status, items}`; `POST /{id}/pack`, `POST /{id}/ship` (no body) → `{...status}`; forward-only transitions; ship → `SHIPPED`.
- Onboarding: `POST /api/v1/onboarding` `{partyId, emplPositionId}` → 201 `{onboardingId, partyId, emplPositionId, status, steps:[String], failedReason}` (saga: EMPLOYEE role via party-svc, then employment); `GET /api/v1/onboarding/{id}`; `POST /{id}/retry`.
- Positions: `POST /api/v1/positions` `{emplPositionId, emplPositionTypeId, statusId}` (P6 screen/verify-script proven); `PUT /api/v1/positions/{id}` `{emplPositionTypeId, statusId}` — deactivate via `statusId: "INACTIVE"` (30-verify-p6.sh proven).
- Seeded: party `P1`, product `MP1` (9.99 USD), user demo-user (CUSTOMER). Stock: POST /api/v1/inventory-items {inventoryItemId, productId, facilityId} + /receive {quantity}.

**Environment prerequisites:** kind cluster up (name `ofbiz`), spa:local + gateway:p8-inv deployed (plan 1 end state).

---

### Task 1: Gateway shipments route

**Files:**
- Modify: `microservices/gateway/src/main/resources/application.yaml`

- [ ] **Step 1: Add route** — after the inventory-svc block (same style):

```yaml
        # P8 plan-2 Task 1: shipments for the plan-to-ship journey (north-south via gateway only)
        - id: inventory-shipments
          uri: ${gateway.inventory.uri:http://inventory-svc.ofbiz.svc:8080}
          predicates:
            - Path=/api/v1/shipments/**
          metadata:
            required-role: CUSTOMER
```

- [ ] **Step 2: Rebuild + redeploy gateway** (plan-1 pattern): `./gradlew :microservices:gateway:bootJar`, `docker build -t gateway:p8-ship microservices/gateway`, `kind load docker-image gateway:p8-ship --name ofbiz`, `kubectl -n ofbiz set image deploy/gateway gateway=gateway:p8-ship`, `kubectl -n ofbiz rollout status deploy/gateway --timeout=180s`.

- [ ] **Step 3: Verify** — port-forward gateway 8080; preflight `OPTIONS /api/v1/shipments` with Origin http://localhost:4200 → access-control-allow-origin present; machine client_credentials token POST /api/v1/shipments → 403. Kill port-forward.

- [ ] **Step 4: Commit**

```bash
git add microservices/gateway/src/main/resources/application.yaml
git commit -m "feat(gateway): shipments route (Path=/api/v1/shipments/**, required-role CUSTOMER) — P8 plan-to-ship journey"
```

---

### Task 2: T4 Returns journey (spec first, then screen)

**Files:**
- Create: `tests/e2e/specs/returns.spec.ts`, `platform/spa/src/screens/Returns.tsx`
- Modify: `platform/spa/src/App.tsx` (nav link + route)

- [ ] **Step 1: Failing spec**

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('returns: place order → return line → read back RECEIVED (server truth)', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/returns');

  // 1. seed stock + place a one-line order for MP1 (journey prereq)
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Seed stock' }).click();
  await expect(page.getByTestId('ret-stock')).toContainText('stocked');
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByTestId('ret-order')).toContainText(/order \S+ status/);

  // 2. return one unit of the ordered product
  await page.getByRole('button', { name: 'Create return' }).click();
  await expect(page.getByTestId('ret-return')).toContainText(/return \S+ status RECEIVED/);

  // 3. read back from the server (no locally-minted state)
  await page.getByRole('button', { name: 'Read back' }).click();
  await expect(page.getByTestId('ret-readback')).toContainText(/RECEIVED.*MP1/);
});
```

- [ ] **Step 2: Run red** — `cd tests/e2e && pnpm exec playwright test specs/returns.spec.ts` → FAIL (route placeholder).

- [ ] **Step 3: Returns.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Order = { orderId: string; status: string };
type ReturnOrder = { returnId: string; orderId: string; status: string;
  lines: { productId: string; facilityId: string; quantity: number }[] };
const runId = () => 'e2e-' + Date.now().toString(36) + '-';

export default function Returns() {
  const [facility, setFacility] = useState('');
  const [stock, setStock] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [ret, setRet] = useState<ReturnOrder | null>(null);
  const [readback, setReadback] = useState('');
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';

  const seedStock = async () => {
    setErr('');
    try {
      const itemId = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: itemId, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${itemId}/receive`, { method: 'POST', body: { quantity: 5 } });
      setStock('stocked');
    } catch (e) { setErr((e as Error).message); }
  };

  const placeOrder = async () => {
    setErr('');
    try {
      setOrder(await api<Order>('/api/v1/orders', { method: 'POST',
        body: { customerId, currencyUomId: 'USD',
          lines: [{ productId, facilityId: facility, quantity: 2 }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const createReturn = async () => {
    setErr('');
    try {
      setRet(await api<ReturnOrder>('/api/v1/returns', { method: 'POST',
        body: { orderId: order!.orderId,
          lines: [{ productId, facilityId: facility, quantity: 1 }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const readBack = async () => {
    setErr('');
    try {
      const r = await api<ReturnOrder>(`/api/v1/returns/${ret!.returnId}`);
      setReadback(`${r.status} ${r.lines.map(l => `${l.productId}×${l.quantity}`).join(', ')}`);
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="ret-h">
      <h2 id="ret-h">Returns</h2>
      <p className="err" data-testid="ret-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> ×2, return 1</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void seedStock()}>Seed stock</button>
      <p data-testid="ret-stock">{stock}</p>
      <button onClick={() => void placeOrder()}>Place order</button>
      <p data-testid="ret-order">{order && `order ${order.orderId} status ${order.status}`}</p>
      {order && <button onClick={() => void createReturn()}>Create return</button>}
      <p data-testid="ret-return">{ret && `return ${ret.returnId} status ${ret.status}`}</p>
      {ret && <button onClick={() => void readBack()}>Read back</button>}
      <p data-testid="ret-readback">{readback}</p>
    </section>
  );
}
```

- [ ] **Step 4: App.tsx** — add nav `<NavLink to="/returns">Returns</NavLink>` and `<Route path="/returns" element={<Returns />} />` (import Returns). Also add placeholders now for the other two journeys: `/plan-to-ship`, `/hire-to-retire` h1 placeholders + nav links (Tasks 3–4 swap the elements).

- [ ] **Step 5: Green** — `cd platform/spa && pnpm build && ./10-install.sh`; port-forwards gateway+spa; `cd tests/e2e && pnpm exec playwright test` → 7 passed. Kill port-forwards.

- [ ] **Step 6: Commit**

```bash
git add platform/spa/src tests/e2e/specs/returns.spec.ts
git commit -m "feat(spa): returns journey — order→return→server readback, e2e green"
```

---

### Task 3: T5 Plan-to-ship journey

**Files:**
- Create: `tests/e2e/specs/plan-to-ship.spec.ts`, `platform/spa/src/screens/PlanToShip.tsx`
- Modify: `platform/spa/src/App.tsx` (route swap)

- [ ] **Step 1: Failing spec**

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('plan-to-ship: order → MRP run → shipment pack/ship → SHIPPED', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/plan-to-ship');

  // 1. seed stock (captures inventoryItemId shown on screen) + place order
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Seed stock' }).click();
  await expect(page.getByTestId('pts-stock')).toContainText(/stocked.*inv/);
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByTestId('pts-order')).toContainText(/order \S+ status/);

  // 2. MRP run completes and renders proposals (content state-dependent — assert run, not items)
  await page.getByRole('button', { name: 'Run MRP' }).click();
  await expect(page.getByTestId('pts-mrp')).toContainText(/MRP run complete: \d+ proposals?/);

  // 3. ship the order: create → pack → ship, assert server status each step
  await page.getByRole('button', { name: 'Create shipment' }).click();
  await expect(page.getByTestId('pts-shipment')).toContainText(/shipment \S+ status/);
  await page.getByRole('button', { name: 'Pack' }).click();
  await expect(page.getByTestId('pts-shipment')).toContainText(/status PACKED|status PACKING/i);
  await page.getByRole('button', { name: 'Ship' }).click();
  await expect(page.getByTestId('pts-shipment')).toContainText('status SHIPPED');
});
```

NOTE: pack status name not verified from code — implementer MUST check the Shipment domain status enum (inventory-svc domain) first and tighten the pack assertion to the exact enum value; do not leave a loose regex. If the transition order differs, adjust per domain truth and document.

- [ ] **Step 2: Run red**

- [ ] **Step 3: PlanToShip.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Order = { orderId: string; status: string };
type Shipment = { shipmentId: string; orderId: string; status: string };
type MrpRun = { proposals: { productId: string; quantity: number }[] };
const runId = () => 'e2e-' + Date.now().toString(36) + '-';

export default function PlanToShip() {
  const [facility, setFacility] = useState('');
  const [itemId, setItemId] = useState('');
  const [stock, setStock] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [mrp, setMrp] = useState('');
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';

  const seedStock = async () => {
    setErr('');
    try {
      const id = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: id, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${id}/receive`, { method: 'POST', body: { quantity: 5 } });
      setItemId(id);
      setStock(`stocked ${id}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const placeOrder = async () => {
    setErr('');
    try {
      setOrder(await api<Order>('/api/v1/orders', { method: 'POST',
        body: { customerId, currencyUomId: 'USD',
          lines: [{ productId, facilityId: facility, quantity: 2 }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const runMrp = async () => {
    setErr('');
    try {
      const result = await api<MrpRun>('/api/v1/mrp/run', { method: 'POST' });
      setMrp(`MRP run complete: ${result.proposals.length} proposals`);
    } catch (e) { setErr((e as Error).message); }
  };

  const createShipment = async () => {
    setErr('');
    try {
      const id = runId() + 'shp';
      setShipment(await api<Shipment>/api/v1/shipments', { method: 'POST',
        body: { shipmentId: id, orderId: order!.orderId,
          items: [{ productId, quantity: 2, inventoryItemId: itemId }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const transition = async (action: 'pack' | 'ship') => {
    setErr('');
    try {
      setShipment(await api<Shipment>(`/api/v1/shipments/${shipment!.shipmentId}/${action}`, { method: 'POST' }));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="pts-h">
      <h2 id="pts-h">Plan-to-ship</h2>
      <p className="err" data-testid="pts-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> ×2</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void seedStock()}>Seed stock</button>
      <p data-testid="pts-stock">{stock}</p>
      <button onClick={() => void placeOrder()}>Place order</button>
      <p data-testid="pts-order">{order && `order ${order.orderId} status ${order.status}`}</p>
      <button onClick={() => void runMrp()}>Run MRP</button>
      <p data-testid="pts-mrp">{mrp}</p>
      {order && <button onClick={() => void createShipment()}>Create shipment</button>}
      <p data-testid="pts-shipment">
        {shipment && `shipment ${shipment.shipmentId} status ${shipment.status}`}
      </p>
      {shipment && <button onClick={() => void transition('pack')}>Pack</button>}{' '}
      {shipment && <button onClick={() => void transition('ship')}>Ship</button>}
    </section>
  );
}
```

FIX before implementing: line `setShipment(await api<Shipment>/api/v1/shipments', ...)` is missing its opening `(` — correct form: `setShipment(await api<Shipment>('/api/v1/shipments', { method: 'POST', ...`. Implementer uses the corrected line.

- [ ] **Step 4: App.tsx** — swap `/plan-to-ship` placeholder for `<PlanToShip />`.

- [ ] **Step 5: Green** — build, install.sh, suite → 8 passed. Tighten pack assertion per domain enum first (Step 1 NOTE).

- [ ] **Step 6: Commit**

```bash
git add platform/spa/src tests/e2e/specs/plan-to-ship.spec.ts
git commit -m "feat(spa): plan-to-ship journey — MRP run + shipment pack/ship to SHIPPED, e2e green"
```

---

### Task 4: T6 Hire-to-retire journey

**Files:**
- Create: `tests/e2e/specs/hire-to-retire.spec.ts`, `platform/spa/src/screens/HireToRetire.tsx`
- Modify: `platform/spa/src/App.tsx` (route swap)

- [ ] **Step 1: Failing spec**

```ts
import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('hire-to-retire: position → onboarding saga → read back → retire position', async ({ page }) => {
  const run = runPrefix();
  const positionId = run + 'pos';
  await page.goto('/hire-to-retire');

  // 1. create an active position
  await page.getByLabel('Position ID').fill(positionId);
  await page.getByRole('button', { name: 'Create position' }).click();
  await expect(page.getByTestId('htr-position')).toContainText(`position ${positionId}`);

  // 2. start the onboarding saga for seeded party P1 (saga adds EMPLOYEE role + employment)
  await page.getByRole('button', { name: 'Start onboarding' }).click();
  await expect(page.getByTestId('htr-onboarding')).toContainText(/onboarding \S+ status \S+/);

  // 3. server readback of saga state + steps
  await page.getByRole('button', { name: 'Read back' }).click();
  await expect(page.getByTestId('htr-readback')).toContainText(/steps:/);

  // 4. retire: deactivate the position (PUT, 30-verify-p6.sh proven shape)
  await page.getByRole('button', { name: 'Retire position' }).click();
  await expect(page.getByTestId('htr-position')).toContainText('INACTIVE');
});
```

NOTE: saga status value not assumed — spec asserts `status \S+` and the screen renders whatever the server returns; if the domain exposes a terminal status (check the saga status enum in humanres domain), implementer tightens the regex to it. `steps:` prefix is rendered by the screen from server data.

- [ ] **Step 2: Run red**

- [ ] **Step 3: HireToRetire.tsx**

```tsx
import { useState } from 'react';
import { api } from '../api';

type Position = { emplPositionId: string; statusId: string };
type Onboarding = { onboardingId: string; status: string; steps: string[]; failedReason?: string };

export default function HireToRetire() {
  const [positionId, setPositionId] = useState('');
  const [position, setPosition] = useState<Position | null>(null);
  const [onboarding, setOnboarding] = useState<Onboarding | null>(null);
  const [readback, setReadback] = useState('');
  const [err, setErr] = useState('');

  const partyId = 'P1';

  const createPosition = async () => {
    setErr('');
    try {
      await api('/api/v1/positions', { method: 'POST',
        body: { emplPositionId: positionId, emplPositionTypeId: 'ENGINEER', statusId: 'ACTIVE' } });
      setPosition({ emplPositionId: positionId, statusId: 'ACTIVE' });
    } catch (e) { setErr((e as Error).message); }
  };

  const startOnboarding = async () => {
    setErr('');
    try {
      setOnboarding(await api<Onboarding>('/api/v1/onboarding', { method: 'POST',
        body: { partyId, emplPositionId: positionId } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const readBack = async () => {
    setErr('');
    try {
      const o = await api<Onboarding>(`/api/v1/onboarding/${onboarding!.onboardingId}`);
      setReadback(`status ${o.status}, steps: ${o.steps.join(' → ')}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const retirePosition = async () => {
    setErr('');
    try {
      await api(`/api/v1/positions/${positionId}`, { method: 'PUT',
        body: { emplPositionTypeId: 'ENGINEER', statusId: 'INACTIVE' } });
      const saved = await api<{ emplPositionId: string; statusId?: string }>(`/api/v1/positions/${positionId}`);
      setPosition({ emplPositionId: saved.emplPositionId, statusId: saved.statusId ?? 'INACTIVE' });
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="htr-h">
      <h2 id="htr-h">Hire-to-retire</h2>
      <p className="err" data-testid="htr-err">{err}</p>
      <p>party <strong>{partyId}</strong> (saga adds EMPLOYEE role + employment)</p>
      <label>Position ID <input value={positionId} onChange={e => setPositionId(e.target.value)} /></label>{' '}
      <button onClick={() => void createPosition()}>Create position</button>
      <p data-testid="htr-position">{position && `position ${position.emplPositionId} ${position.statusId}`}</p>
      {position && <button onClick={() => void startOnboarding()}>Start onboarding</button>}
      <p data-testid="htr-onboarding">{onboarding && `onboarding ${onboarding.onboardingId} status ${onboarding.status}`}</p>
      {onboarding && <button onClick={() => void readBack()}>Read back</button>}
      <p data-testid="htr-readback">{readback}</p>
      {onboarding && <button onClick={() => void retirePosition()}>Retire position</button>}
    </section>
  );
}
```

- [ ] **Step 4: App.tsx** — swap `/hire-to-retire` placeholder + nav link.

- [ ] **Step 5: Green** — build, install.sh, suite → 9 passed. Tighten saga-status regex if a terminal status exists (Step 1 NOTE).

- [ ] **Step 6: Commit**

```bash
git add platform/spa/src tests/e2e/specs/hire-to-retire.spec.ts
git commit -m "feat(spa): hire-to-retire journey — position + onboarding saga + retire, e2e green"
```

---

### Task 5: Evidence — rerun runner, tick T4–T6

**Files:**
- Modify: `IMPLEMENTATION.md` (PHASE 8 ticks)

- [ ] **Step 1:** `platform/spa/40-run-e2e.sh` → expect `9 passed` + `P8 E2E EVIDENCE PASSED (<ts>)`.
- [ ] **Step 2:** Tick:
  - T4 AC: `[x] spec green`
  - T5 AC: `[x] spec green` — amend wording: "MRP run + shipment pack/ship (production-run execution has no REST surface — descope recorded; MRP proposals + shipment flow asserted)"
  - T6 AC: `[x] spec green`
  - Append under P8-GATE: `- T4+T5+T6 green: 40-run-e2e.sh → P8 E2E EVIDENCE PASSED <ts> (9/9; shipments gateway route gateway:p8-ship)`
- [ ] **Step 3: Commit**

```bash
git add IMPLEMENTATION.md
git commit -m "Added: P8 T4-T6 evidence — 40-run-e2e.sh green (returns, plan-to-ship, hire-to-retire)"
```

---

## Self-Review (done)

- Spec coverage: T4 (Task 2), T5 (Tasks 1+3), T6 (Task 4), evidence (Task 5). T7–T9 remain for plan 3.
- Placeholders: none; two implementer-verify NOTEs (shipment pack status enum, onboarding terminal status) with working default assertions either way, plus one explicit syntax FIX callout in Task 3 Step 3.
- Honest-evidence rules applied: return status asserted as RECEIVED (observable truth); credit memo NOT asserted (outbox event); MRP proposal content NOT asserted (state-dependent); production-run descope recorded.
- Type consistency: api client, runId, test-id prefixes (ret-/pts-/htr-) consistent between specs and screens.
- Known risk: shipment create referencing an inventoryItemId whose ATP the order saga reserved — spec seeds quantity 5, orders 2 (3 remaining); if inventory-svc still rejects the item reference, implementer reports the exact response (DONE_WITH_CONCERNS), does not hack.
