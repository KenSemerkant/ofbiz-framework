# Gap Analysis: OFBiz Monolith vs Microservices Target State

Date: 2026-10-28 · Branch: `ops-toolkit` · Method: OFBiz official capability baseline
(business-users + developers pages) mapped against live `microservices/` code
(agents verified domain models, Flyway migrations, REST/gRPC surfaces, Kafka topics).

Companion doc: [PARITY-MATRIX.md](../PARITY-MATRIX.md) — screen/UI-level coverage
per monolith app. This doc is business-capability level.

## Headline verdict

The microservices estate delivers the **core transactional spine at (near-)parity —
party → catalog → order → inventory → billing → GL — with machine-verified
consistency (delta-0 checkers, CG-1..9 gates)**. The **long-tail domains are
skeletons** (manufacturing, HR, marketing, workeffort, content), and several
breadth features were **deliberately descoped** (P9-F1/F8: assets, budgets,
agreements, production runs/costing). Across ~60 baseline capability items:

| Verdict | Count | ~Share |
|---|---|---|
| COVERED (functional parity) | 17 | ~28% |
| PARTIAL (core in place, breadth missing) | 20 | ~33% |
| ABSENT | 23 | ~38% |

**On the primary goal — deployable subsets as a foundation for industry-specific
builds: architecturally YES, mechanically easy, operationally undocumented.**
Details in §3.

## 1. Business capability matrix

### 1.1 Accounting (finance-svc + billing-svc)

| Baseline item | Verdict | Notes |
|---|---|---|
| Double-entry GL | **COVERED** | Unbalanced→409, closed-period block, TB parity vs monolith (consistency checker PARITY) |
| Multi-org, account hierarchies, segmentation | **ABSENT** | `gl_accounts` flat, single-book |
| AR | **PARTIAL** | Customer invoices + `outstanding_ar_balance` |
| AP (vendor invoices/payments) | **ABSENT** | Payments inbound only, tied to `invoice_id` |
| Statements, aging | **ABSENT** | (invoices/payments themselves covered) |
| Agreements & credit mgmt | **PARTIAL** | billing_accounts credit-limit + net-days (breach→typed 409); Agreement screens descoped P9-F1 |
| Asset mgmt + depreciation | **ABSENT** | FixedAsset descoped P9-F1 |
| Budgeting | **ABSENT** | Descoped (finance-svc MAPPING.md) |
| Payment gateways | **PARTIAL** | Adapter framework + test gateway (idempotency, DLQ, reconciliation); real PSP configs descoped |
| Financial reporting | **PARTIAL** | TB / balance sheet / income statement; no cash-flow, aging, custom reports |
| Integration (order/inv/purchasing/mfg) | **PARTIAL** | order→invoice→GL auto-posting works; no inventory/mfg costing integration |

### 1.2 Manufacturing (manufacturing-svc)

| Baseline item | Verdict | Notes |
|---|---|---|
| BOM | **PARTIAL** | Single-level + scrapPct; no multi-level explosion, effectivity, variants |
| Jobshop/routings/tasks | **PARTIAL** | Routing + ordered tasks w/ durations; execution machinery descoped |
| MRP | **PARTIAL** | Deterministic netting, parity w/ monolith MrpServices on dataset; **product-level only** — no time-phasing, no BOM explosion, proposals not orders |
| Production runs | **ABSENT** | Known gap (P8-T5), descope reaffirmed P9-F8 |
| Production/job costing | **ABSENT** | Cost entities descoped to finance, nothing implemented |
| Equipment billing | **ABSENT** | FixedAsset not migrated |
| Raw material procurement | **PARTIAL (indirect)** | `mrp.proposed` events exist; no consumer → no POs |
| Manufacturing reporting | **ABSENT** | Raw lists only |

### 1.3 Human Resources (humanres-svc)

| Baseline item | Verdict | Notes |
|---|---|---|
| Positions + hierarchy | **COVERED** | empl_positions, reportingTo chain, status |
| Employments | **COVERED** | party↔position w/ from/thru |
| Onboarding | **COVERED** | Saga: EMPLOYEE role grant + employment, retry/restart-safe |
| Org/dept structure | **PARTIAL** | Only via position chain + PARTY_GROUP roles; no internal-org entity |
| Skills | **ABSENT** | FUTURESTATE §5.10 said "skills, payroll inputs" — never built; doc aligned to code in P7 |
| Performance reviews | **ABSENT** | |
| Recruitment (applications, interviews) | **ABSENT** | |
| Salaries/payroll | **ABSENT** | |
| Employment contracts | **ABSENT** | Beyond from/thru record |
| Expenses, training | **ABSENT** | |

### 1.4 Inventory / Facility (inventory-svc)

| Baseline item | Verdict | Notes |
|---|---|---|
| Single & multi-warehouse | **COVERED** | Facility + transfers |
| Receiving | **COVERED** | `receive` + `stock.received` |
| Shipment integration (outbound) | **COVERED** | Shipment pack/ship + events + CDC projection |
| Serialized inventory | **PARTIAL** | `serial_number` column only; no serial-aware flows |
| Picklist & packaging | **PARTIAL** | pack/ship only; no picklist entity |
| Returns (inventory side) | **PARTIAL** | Returns owned by order-svc; no restock flow |
| Inventory locations | **ABSENT** | No location/container columns |
| Lot management | **ABSENT** | No lot_id |

### 1.5 Catalog (product-svc + search-svc)

| Baseline item | Verdict | Notes |
|---|---|---|
| Keyword search | **COVERED** | OpenSearch projection + reindex + gRPC query (replaces hibernate-search) |
| Stores/catalogs/categories/products | **PARTIAL** | Store + Category tree + Product; no Catalog tier; **no REST write path — writes arrive via CDC projection (see §4 gap #1)** |
| Product types | **PARTIAL** | Generic Product + ProductAssoc (variant expressible); no digital/downloadable fulfillment, no configurable bundling |
| Price rules | **PARTIAL** | Price slices by type/currency/half-open time-window; no party/group/quantity-break conditions |
| Gift certificates/cards | **ABSENT** | |
| Promotion engine | **ABSENT** | Campaign entity only; no promo/order-adjustment engine |
| POS integration | **ABSENT** | |

### 1.6 CRM & Order Management (order-svc + marketing-svc + party-svc)

| Baseline item | Verdict | Notes |
|---|---|---|
| Quotes | **COVERED** | Create/list/convert |
| Order entry | **COVERED** | PlaceOrderSaga: price snapshot, party validation, inventory reserve, compensation |
| Order management | **COVERED** | State machine + approve/complete/cancel + history |
| Returns | **COVERED** | ReturnOrder + creditmemo events |
| Campaigns & tracking codes | **COVERED** | marketing-svc + order attribution pipeline |
| Leads & opportunities | **PARTIAL** | SalesOpportunity CRUD (stage, close prob); no lead capture/pipeline |
| Shared docs/calendar/tasks | **PARTIAL** | content-svc (S3 publish) + workeffort (tasks, .ics, timesheets); no collaboration |
| Email integration | **PARTIAL** | Outbound SMTP + i18n templates; no inbound |
| Sales forecasts | **ABSENT** | Zero hits repo-wide |
| Case management | **ABSENT** | No case entity |

### 1.7 eCommerce

| Baseline item | Verdict | Notes |
|---|---|---|
| Customer portal | **COVERED** | React SPA, OIDC auth-code+PKCE, role-gated routes |
| Storefront | **PARTIAL** | Order-centric portal (Catalog/OrderToCash/Returns screens); no cart UI, no content-managed pages, no merchandising |
| Cross-sell/upsell | **ABSENT** | ProductAssoc exists, nothing consumes it |

### 1.8 Party & Identity (cross-cutting baseline)

| Item | Verdict | Notes |
|---|---|---|
| Party master (persons/groups/roles) | **PARTIAL** | Solid core; **PartyRelationship in domain but not persisted**; no attributes/classifications; postal address free-text, unstructured |
| AuthN (OIDC/JWT) | **COVERED** | Spring Authorization Server; replaces userLogin+JWTManager; legacy hash verification for migration |
| AuthZ | **PARTIAL** | **Coarse roles only** — no OFBiz SecurityGroup/Permission fine-grained model |

## 2. Technical capability comparison (developers page + ARCHITECTURE.md)

| Monolith capability | Microservices replacement | Status |
|---|---|---|
| Entity Engine (904 entities, XML, Delegator) | JPA/Hibernate + Flyway, DB-per-service | ✅ Parity on migrated slice; entity ownership frozen per-service MAPPING.md |
| Service Engine (9 engine types, XML-declared) | Spring use cases + Kafka events + outbox saga | ✅ ECA chains → event flows (verified in order/billing/finance) |
| Widget/FreeMarker UI | Headless REST + React SPA + gateway | ✅ (deviation: SPA is React, not the planned Angular 17) |
| gRPC east-west mesh | **Deviation: REST-only** except search-svc proto | ⚠️ FUTURESTATE §3 specifies gRPC east-west; only search-svc ships proto (documented deviations in service MAPPING.mds) |
| SOAP/REST API layer | REST via gateway only | ❌ SOAP dropped at CG-7 (declared moot — new gateway requirement if ever needed) |
| Multi-tenancy | Not implemented | ❌ OPEN (FUTURESTATE §11.5) — blocks any SaaS-style offering |
| Plugin architecture (ofbiz-plugins trunk) | No plugin mechanism; extension = new service behind gateway | ⚠️ Different extension model — arguably better (§3), but no hot-plug story |
| Clustering/JobManager | K8s + HPA + Kafka consumers | ✅ Above monolith |

## 3. The primary goal: subset deployment as a foundation

**Verdict: the goal is met architecturally, ahead of the monolith — with three
operational gaps.**

What's already above-and-beyond vs the monolith:

1. **Per-service deployability** — every service: own Gradle build → distroless
   image → independent Helm release via one shared chart
   (`platform/helm/service-chart`); own Postgres DB + Flyway. No shared
   persistence, references by ID only. party-svc is fully standalone
   (consumes nothing).
2. **Graceful degradation** — event consumers idle when producers are absent
   (manufacturing without order-svc, finance without billing-svc: both fine).
   Sync dependencies fail loud (bounded 5s, retryable), never silent.
3. **Composability** — gateway is the BFF composition point; SPA composes;
   new industry services slot in as another `/api/v1/<context>` route + event
   topics. This is the extension model that replaces OFBiz plugins, and it is
   strictly more flexible for "build only the subset you need, extend with
   your own services."
4. **Minimum footprint** (functional): kind + Istio + Kafka (+ Apicurio) +
   one Postgres + identity-svc + gateway + Redis + chosen domain services.
   Garage S3 only needed for content-svc.

Operational gaps against the goal:

| # | Gap | Impact |
|---|---|---|
| G1 | `platform/services/10-deploy.sh` deploys all 16 linearly; no subset profile/flag | Business must hand-pick helm blocks; easy but undocumented |
| G2 | **No subset-deployment doc or reference profile** ("deploy party+order+billing only") | The core promise of the program has no onboarding path |
| G3 | Platform floor is heavy (Istio STRICT mTLS, Kafka+registry, cert-manager, Vault) vs `gradle ofbiz` single-JVM | A team wanting 3 services carries ~8 platform components; a "lite" profile (no mesh, single-broker) doesn't exist |
| G4 | DoD mandates full observability stack per service (ADR-011) | Good default; but no documented "dev-lite" deviation path for subset adopters |

## 4. Top functional gaps (ranked by blocking impact)

1. **product-svc has no write path** — product creation/mutation only via CDC
   projection from the monolith (`V4__monolith_projection.sql`), and the
   monolith was decommissioned in P7. Post-decommission, catalog maintenance
   has no supported write route. **This is the one gap that breaks the spine
   for any new adopter.**
2. **AP / procure-to-pay entirely absent** — no vendor invoices, no POs, no
   procurement. Blocks any business needing spend-side ERP.
3. **No promotion/pricing breadth** — no promo engine, no party/group price
   conditions, no gift cards. Blocks retail/eCommerce adopters.
4. **Manufacturing is planning-only** — no production runs, no costing, no
   multi-level BOM. Blocks discrete-mfg adopters beyond planning.
5. **HR is positions+onboarding only** — no payroll, recruitment, skills,
   reviews, expenses.
6. **Fine-grained permissions** — coarse roles only; enterprises coming from
   OFBiz SecurityGroups will find this a regression.
7. **Data-model depth losses**: flat CoA (no orgs/hierarchies), unpersisted
   party relationships, unstructured postal addresses, no lots/locations.

## 5. Recommended next stories (if the program reopens)

1. **P11-F1 product write path** — REST product/category/price mutation in
   product-svc (drop or repurpose CDC projection). Unblocks all new adopters.
2. **P11-F2 subset-deployment profile** — `10-deploy.sh --services=a,b,c` +
   minimum-footprint doc + optional lite profile (Linkerd-or-no-mesh, single
   node Kafka). Directly serves the program's stated goal.
3. **P11-F3 AP slice** — vendor invoices + payments in billing-svc, symmetric
   to AR.
4. **P11-F4 promo + price-rule conditions** — order-adjustment engine,
   party/quantity price conditions.
5. **P11-F5 fine-grained permissions** — permission entities in identity-svc
   fed from party roles; keep JWT compact via group claims.

## Sources

- Baseline: ofbiz.apache.org business-users + developers pages (fetched 2026-10-28)
- Target design: FUTURESTATE-ARCHITECTURE.md (§4 catalog, §10 roadmap)
- Code: `microservices/*` (domain models, V1..Vn Flyway, REST controllers,
  Kafka topics), per-service MAPPING.md, IMPLEMENTATION.md P9 descope records
- Verification evidence: CG-1..CG-9 gate files, consistency checkers (delta-0)
