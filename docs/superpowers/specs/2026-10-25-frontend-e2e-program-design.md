# Frontend E2E Program (P8) — Design

Date: 2026-10-25
Status: Approved (design review)
Repo authority: this program is added as phase P8 in IMPLEMENTATION.md before any code.

## Problem

The 16-service target stack is delivered (CG-0..7 passed, monolith decommissioned) but the SPA is a 181-line skeleton (`platform/spa/index.html`) covering only login, parties list, catalog+price, and P6 CRUD. No browser automation exists anywhere in the repo — "frontend verification" is curl-style shell scripts. Remaining use-cases (orders, returns, billing, GL, shipments, MRP, HR, attribution, content, calendar) have zero UI coverage.

## Goal

A journey-driven demo SPA built on real gateway APIs — usable as gate evidence and as the seed for a future operational UI — with a Playwright E2E suite covering every journey, run locally against the kind cluster, green runs recorded as evidence in IMPLEMENTATION.md.

## Non-goals

- CI wiring (cluster is local kind)
- i18n/l10n (already flagged as missed-requirement; needs its own decision)
- Notification email delivery (SMTP blackholed on kind — P5 known infra block)
- Operational-UI depth: role-based screen permissions, UX polish beyond usable forms + tables

## Architecture

- `platform/spa/` rebuilt as Vite + React + TypeScript.
  - Screens under `src/journeys/<name>/`; existing 4 screens ported as `login`, `parties`, `catalog`, `p6`.
  - One auth module owns OIDC PKCE (registered redirect `/login/oauth2/code/gateway`, unchanged).
  - All API calls via gateway only, `Authorization: Bearer` from auth module.
- Deploy unchanged: `npm run build` Docker stage → static dist into existing nginx image → same `spa.yaml` helm chart. No gateway or helm changes.
- Exact API paths per journey are taken from the services' REST adapters and the existing verify scripts (`platform/spa/20-verify-catalog.sh`, `30-verify-p6.sh`), which already demonstrate the gateway call + auth pattern.

## Journey inventory

| Route | Journey | Services exercised |
|---|---|---|
| `/order-to-cash` | pick party → catalog + price → quote → place order → billing attempt → GL entry | party, product, order, billing, finance |
| `/returns` | create return → credit memo | order, finance |
| `/plan-to-ship` | MRP run → production run → shipment | manufacturing, inventory |
| `/hire-to-retire` | onboarding saga → position | humanres, party |
| `/campaign` | campaign → tracked order → attribution report | marketing, order |
| `/content` | upload → list → fetch | content (+ Garage S3) |
| `/calendar` | work-effort iCal feed render | workeffort |
| `/parties` | list/search (ported) | party |
| `/catalog` | browse + price (ported) | product |
| `/notification` | settings stub: send test request, assert queued-not-delivered | notification |

## Playwright suite

- Location: `tests/e2e/` (repo root), TypeScript, pnpm.
- Locators: `getByRole`/`getByLabel`/test IDs only — no text-scraping selectors.
- Auth: real OIDC login once (demo-party-user), saved to `storageState`, reused across specs.
- Isolation: every run mints unique IDs (prefix `e2e-<timestamp>-`); no shared state between tests.
- One spec file per journey + one auth spec. `playwright.config.ts` reads `E2E_BASE_URL` (default gateway URL on kind).
- Local run: `pnpm playwright test`; dot reporter.

## Evidence + process

- New phase **P8** in IMPLEMENTATION.md, stories:
  - P8-T1 SPA harness: Vite/React/TS rebuild, ports existing 4 screens, deploy via existing chart.
  - P8-T2 Playwright harness: `tests/e2e/` + auth storageState + CI-free runner.
  - P8-T3..T9 one per new journey (order-to-cash, returns, plan-to-ship, hire-to-retire, campaign, content, calendar + notification stub).
- Each story AC: journey spec green on kind cluster + evidence link (PASS output).
- `platform/spa/40-run-e2e.sh`: runs suite, prints per-journey PASS summary — matches existing verify-script culture.

## Risks / notes

- OIDC in Playwright: login flow is browser-verified working (commit 33a1c6c4fe); storageState avoids re-login cost. If identity session expires mid-run, auth spec re-mints.
- Billing test gateway is steerable (append-only attempt log) — assertions target attempt state, not real payment.
- GL assertions use finance read APIs; balanced-posting invariant already covered by service tests, E2E asserts visibility.
- MRP run is async — plan poll-with-timeout in spec, not fixed sleep.
