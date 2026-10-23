# Gate CG-3 — Phase 3: product-svc + inventory-svc

**Type:** phase completion gate
**Objective:** catalog + inventory domains live on the new stack; the P2 CDC/cutover pattern replicated for the product + shipment slices.
**Depends on:** all phase 3 stories
**Status:** PASSED 2026-10-22 (local kind staging)

## Checklist (evidence)
- [x] Catalog browse + price lookup via new APIs in SPA — platform/spa/ (catalog table + per-row price lookup, nginx :4200); platform/spa/20-verify-catalog.sh PASSED: scripted OIDC PKCE login (demo-user, the SPA's own flow — code → token via party-projection roles chain) → `catalog browse: 54 products via gateway (first: LIVE-1792707983)` → `price lookup: ... price":9.99,"currencyUomId":"USD"` → `role gate: machine token 403 on /api/v1/products`
- [x] Stock reserve/release saga works; oversell impossible under concurrency (100 parallel reserves, 0 over-reserved) — platform/services/50-reserve-drill.sh PASSED: `successes=28 conflicts=72` (28+72=100, every loss a bounded-retry 409), `final ATP=22 (expected 22)` = 50−28 exact, `ATP after cancel=23 (expected 23)` — compensation releases. In-process proof: ReservationConcurrencyTest (parallel reserves never oversell).
- [x] search-svc indexes products on change events; search p99 < 100ms @ 100k docs — platform/services/60-search-benchmark.sh PASSED: `live path OK: searchable in 0s (event -> relay -> index -> query)` (product-svc API → outbox → relay → product.product.created.v1 → indexer) and `query p50=4ms p99=14ms (200 queries, 100001 docs)`; 100k corpus bulk-loaded straight into the versioned `products` index (benchmark load — the live path is proven separately in the same run).
- [x] CDC cutover + consistency delta 0 for product/shipment slices; rollback drill done — product: 45-import-products.sh (50 monolith products through product-svc's API, reverse-sync converged) then 31-check-product-consistency.sh: `pre-cutover mirror : monolith=53 projection=53 checksums identical` and `post-cutover truth : monolith=53 product-svc=53 checksums identical` → DELTA 0; shipment: 32-check-shipment-consistency.sh `monolith=20 projection=20` → DELTA 0; rollback: 55-rollback-drill-product.sh PASSED (gateway product route flipped back, monolith-side write `RB…` mirrored into product-svc's projection by CDC — zero data loss — flipped forward, `53 -> 54` preserved).

## Deploy/infra evidence
- 8 services Ready, STRICT mesh mTLS, P3 topics + DLQs, product/inventory/search DBs — platform/services/20-verify.sh: `RESULT: 34 passed, 0 failed / ALL CHECKS PASSED`
- Connectors: monolith-product / monolith-shipment RUNNING (unique replication slots + publications; see deviations)

## Deviations (documented)
- **Shipment reverse-sync deferred to P4.** The shipment slice is read-mostly pre-P4 (shipments become write-hot when order-svc lands); CG-3 proves the CDC mirror + delta 0 + rollback path instead. Product reverse-sync IS implemented (new-service wins, set-sync members/prices/assocs).
- **Price/quantity columns are TEXT in the monolith stand-in + projections.** Debezium (decimal.handling.mode=string, epoch dates) wire forms round-trip byte-faithful; numeric equality is preserved, formatting is not (reverse-sync normalises `20.00`→`20.0` through JSON BigDecimal). FROM_DATE excluded from checksum folds (wire-form epochs), same call as party excluding audit stamps.
- **Debezium connector fixes found on-cluster (all three connectors):** shared `topic.prefix` collided JMX MBeans and stalled snapshots (fixed: unique prefixes + RegexRouter restoring `ofbiz.<TABLE>`); the P2 `table.list` property was silently ignored by Debezium (real property `table.include.list` — the party connector had been capturing all tables); all connectors shared the default replication slot `debezium` (fixed: unique slot + publication per connector — one connector streams, the rest starve).
- **20-verify.sh mTLS probe truststore fix:** needs BOTH CAs (cluster + clients) like 10-deploy's PEM bundles; the P1-era single-CA truststore fails PKIX after broker cert rotation.
- **Ops events during the gate (cluster, not repo):** kind node disk hit OpenSearch flood-stage watermark during the 100k bulk load (pruned unused node images, raised local watermarks); consumer-group offset resets + `snapshot.mode=always` re-snapshot used to rebuild the product projection after the slot-conflict window lost WAL events.

## Known issues carried
- party-svc OutboxRelay no-retry-cap + unbounded findUnpublished (repo-wide, carried from P2)
- party-svc missing @Transactional on outbox paths (carried from P2)
- shipment entitymodel count 35 vs ~47 (documented in microservices/inventory-svc/MAPPING.md)
- OpenSearch single node @ 94% disk on the local kind VM — benchmark headroom is thin; cloud profile needs sized storage
