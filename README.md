# OFBiz Modernization Program

This repository is an ERP modernization program: the **Apache OFBiz monolith** (current, production system) living alongside the **target-state microservices architecture** that will progressively replace it via a strangler migration.

This README describes the program repository. The monolith's own upstream README lives at [monolith/README.md](monolith/README.md).

---

## Repository Layout

```
.
├── ARCHITECTURE.md              # Current monolith architecture (3-tier descriptive)
├── LOWLEVEL.md                  # Current monolith implementation reference
├── FUTURESTATE-ARCHITECTURE.md  # Target microservices architecture (design)
├── FUTURESTATE-LOWLEVEL.md      # Target-state implementation reference (blueprint)
├── adr/                         # ADRs 001-006 (monolith) + 007-015 (migration)
├── CLAUDE.md  AGENTS.md         # Repo rules for Claude sessions / coding agents
├── IMPLEMENTATION.md            # Delivery plan (phases/features/stories/gates)
├── issues/                      # Story + gate tracking (63 files)
├── PROJECT-PLAN.md              # Timeline / staffing / risks
├── platform/                    # Phase-0 infra: kind, Istio, Kafka, observability, Garage S3
├── tasks/                       # PRDs (agent orientation / program work)
├── monolith/                    # Apache OFBiz — the current system, unmodified
│   ├── framework/               #   16 framework components (entity, service, webapp…)
│   ├── applications/            #   12 business applications (order, accounting…)
│   ├── themes/  docker/  docs/  #   UI themes, container support, docs
│   └── build.gradle, gradlew…   #   Gradle build (self-contained)
├── microservices/               # Future state — one folder per service
│   ├── party-svc/ … marketing-svc/    # 11 domain services
│   └── identity-svc/ gateway/ search-svc/ notification-svc/ audit-svc/   # 5 platform
└── .codegraph/ build/ .gradle/  # local tooling artifacts (not tracked)
```

## Documentation Map

| Question | Document |
|---|---|
| What does the monolith require (FR/NFR)? | [PRD.md](PRD.md) |
| How does the current system work? | [ARCHITECTURE.md](ARCHITECTURE.md) + [LOWLEVEL.md](LOWLEVEL.md) |
| Where is X in the monolith code? | [LOWLEVEL.md](LOWLEVEL.md) §10 lookup table |
| What are we moving to? | [FUTURESTATE-ARCHITECTURE.md](FUTURESTATE-ARCHITECTURE.md) |
| C4 + sequence diagrams | [docs/diagrams/C4-DIAGRAMS.md](docs/diagrams/C4-DIAGRAMS.md) |
| How will the target be built? | [FUTURESTATE-LOWLEVEL.md](FUTURESTATE-LOWLEVEL.md) |
| What is the delivery plan (phases/stories/gates)? | [IMPLEMENTATION.md](IMPLEMENTATION.md) |
| What is the project plan (timeline/staffing/risks)? | [PROJECT-PLAN.md](PROJECT-PLAN.md) |
| Why was X decided? | [adr/](adr/) + FUTURESTATE-ARCHITECTURE.md §2 decision record |
| What work is planned? | [tasks/](tasks/) |
| What screens does the SPA cover vs the monolith? | [docs/PARITY-MATRIX.md](docs/PARITY-MATRIX.md) |
| How was the migration executed end-to-end? | [MIGRATION-JOURNEY.md](MIGRATION-JOURNEY.md) |

## Current State: The Monolith

Apache OFBiz — Java 17, Gradle, single-JVM ERP: 28 components, 904 entities (XML-declared), Service Engine (9 engine types), Entity Engine (12 DB dialects), Widget/FreeMarker UI, embedded Tomcat. Full detail: [ARCHITECTURE.md](ARCHITECTURE.md).

### Run the monolith (docker demo)

```bash
cd monolith
docker build --target runtime -t ofbiz-docker .
cd docker/examples/postgres-demo
docker compose up -d
# → https://localhost:8443/webtools/control/main  (login admin/ofbiz)
# Self-signed cert: Advanced → Proceed. First boot loads demo data (~2 min).
```

Known first-boot gotchas (fixed on this branch): mounted hook scripts need exec bits; `OFBIZ_HOST` must match the name your browser uses (`localhost`).

Native (non-docker): `cd monolith && ./gradlew ofbiz` (requires JDK 17; run `sh gradle/init-gradle-wrapper.sh` once to fetch the wrapper jar).

## Future State: Microservices

**16 independently deployable services** — Java 21, Spring Boot 4.0.x (ADR-015), clean architecture (domain/application/adapters/infrastructure per service), database-per-service, Kafka event backbone (outbox pattern), Kubernetes + Istio mesh, Spring Cloud Gateway, Spring Authorization Server (OIDC), React SPA frontend (`platform/spa`, Vite + React + TypeScript; an early Angular sketch in FUTURESTATE docs was superseded during Phase 8).

| Group | Services |
|---|---|
| Domain (11) | party, product, order, billing, finance, inventory, manufacturing, content, workeffort, humanres, marketing |
| Platform (5) | identity, gateway, search, notification, audit |

Each `microservices/<svc>/` folder holds a delivered service (mono-repo decided Phase 0 — §11.1 resolved; repo-per-service split stays optional post-program). Service purposes, owned entity slices, API surfaces, and event flows: FUTURESTATE-ARCHITECTURE.md §5–§6.

## Migration Roadmap (strangler)

| Phase | Scope | Gate |
|---|---|---|
| 0 | Platform: K8s + mesh, Kafka, gateway (routing to monolith), identity, observability | CG-0 |
| 1 | Party (+ audit) — zero inbound dependencies | CG-1 |
| 2 | Product + Inventory (+ search) | CG-2 |
| 3 | Order | CG-3 |
| 4 | Billing + Finance (+ notification) | CG-4/CG-5 |
| 5 | Long tail: manufacturing, workeffort, humanres, content, marketing | CG-6 |
| 6 | Decommission monolith (archive to S3) | CG-7 |
| 7 | (folded into 6) — decommission hardening, ops handover | CG-7 |
| 8 | Frontend: journey-driven SPA + Playwright E2E (12/12) | P8-GATE |
| 9 | Frontend parity: monolith screen coverage per area (19/19 e2e) | CG-9 |

Cutover/rollback gates per phase: FUTURESTATE-ARCHITECTURE.md §10; story-level receipts: [IMPLEMENTATION.md](IMPLEMENTATION.md).

### Build the microservices

```bash
./gradlew build                # full build: compile + tests + jacoco 95% line gate + ArchUnit
# one service:
./gradlew :microservices:party-svc:party-adapters:adapters-rest:build
```

Conventions live in `buildSrc` (`ofbiz-msa.spring-boot-service` etc.): Java 21 toolchain, OTel + actuator on every service, trivy-scanned images (CI pushes to `ghcr.io/kensemerkant/*`).

### Deploy locally (kind)

```bash
kind create cluster --config platform/bootstrap/kind.yaml   # if not already up
platform/bootstrap/10-*.sh … # mesh, cert-manager, Kafka, Postgres, Garage, observability
platform/services/10-deploy.sh                              # all 16 services + SPA
```

Deploy a rebuilt service (local images; note the 512M pod limit needs `BPL_JVM_THREAD_COUNT=50` on buildpack images):

```bash
./gradlew :microservices:party-svc:party-adapters:adapters-rest:bootBuildImage
docker tag adapters-rest:latest party-svc:dev          # tag IMMEDIATELY — every service's image is named adapters-rest!
kind load docker-image party-svc:dev --name ofbiz
kubectl -n ofbiz set image deploy/party-svc party-svc=party-svc:dev
kubectl -n ofbiz set env deploy/party-svc BPL_JVM_THREAD_COUNT=50
kubectl -n ofbiz rollout status deploy/party-svc
```

Verify infra with `platform/*/20-verify.sh` scripts (gate evidence generators).

### Walk-thru: microservices SPA

```bash
kubectl -n ofbiz port-forward svc/gateway 8080:8080 &
kubectl -n ofbiz port-forward svc/spa 4200:4200 &
# browser → http://localhost:4200   (NOT 127.0.0.1 — gateway CORS allows localhost:4200 only)
# Sign in → demo-user / demo-password (OIDC auth-code + PKCE via gateway)
```

Areas: Parties (role search + detail), Catalog (products/prices/categories/stores/facilities/stock/shipments), Orders (search/quotes/returns), Accounting (invoices/payments/GL/journal/trial balance/balance sheet/income statement), Long-tail (find screens for content/marketing/manufacturing/workeffort/humanres), plus journeys (order-to-cash, returns, plan-to-ship, hire-to-retire, campaign→attribution, content, calendar iCal).

E2E: `platform/spa/40-run-e2e.sh` (Playwright, 19 specs — journeys + P9 parity). Screen-by-screen monolith coverage: [docs/PARITY-MATRIX.md](docs/PARITY-MATRIX.md).

### Walk-thru: monolith (reference/archive)

Same as above under "Run the monolith" — the docker demo boots the full upstream UI at `https://localhost:8443` (admin/ofbiz). The monolith is decommissioned as the system of record (CG-7) but remains runnable for reference and diffing.

## Status

- Current: **target stack live** — all 16 services delivered, gates CG-0..CG-7 passed 2026-10-20..24 (local kind staging); monolith decommissioned (scaled to zero, archived to `s3://ofbiz-archive`)
- Phase 8 (2026-10-25): journey SPA + Playwright harness — P8-GATE closed 12/12
- Phase 9 (2026-10-26): frontend parity per monolith app — CG-9 passed (19/19 e2e); every app triaged implemented-or-descope-recorded in [docs/PARITY-MATRIX.md](docs/PARITY-MATRIX.md). Parity = core find/browse/statement surfaces + recorded descopes; demo-site-level edit/admin depth (relationship maintenance, classifications, security groups…) needs domain modeling first — tracked as future stories.
- Remaining: org-side actions ([OPS-HANDOVER.md](OPS-HANDOVER.md)) — 30-day production soak, sign-off, paging-tool load
- Note: fork CI workflows moved with `monolith/.github` (root-path-bound, would need path fixes to re-enable)

## Contributing

Program docs (root) and the monolith follow upstream OFBiz conventions respectively. New architecture work starts as a spec against FUTURESTATE-LOWLEVEL.md, not as code.
