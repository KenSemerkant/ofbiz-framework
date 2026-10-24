# manufacturing-svc schema mapping

P6 split of `monolith/applications/datamodel/entitydef/manufacturing-entitymodel.xml`
(9 entities per FUTURESTATE-ARCHITECTURE.md §5.7) plus the BOM/routing
slices that live in other entity models in the monolith. See
product-svc/MAPPING.md and workeffort-svc/MAPPING.md for their sides of
the cross-model entities.

## Live slice (migrated in P6)

| Monolith source | Monolith entity | manufacturing-svc home | Notes |
|---|---|---|---|
| manufacturing-entitymodel | MrpEvent | `manufacturing.domain.MrpEvent` (mrp_events) | append-only supply/demand ledger; event_id PK = idempotency |
| manufacturing-entitymodel | MrpEventType / MrpEventView | `MrpEventType` enum (SUPPLY/DEMAND) | vocabulary collapsed into the enum |
| manufacturing-entitymodel | (ProposedOrder/Requirement output of MrpServices) | `manufacturing.domain.MrpProposal` (mrp_proposals) | product-level proposals from the deterministic run (v1: no BOM explosion, minimumStock 0) |
| product-entitymodel | ProductAssoc (PRODUCT_MANUFACTURER) | `manufacturing.domain.Bom` (boms) | one-level BOM, components inline JSON `{productId, quantity, scrapPct}` |
| workeffort-entitymodel | WorkEffort (ROUTING) + WorkEffortGoodStd | `manufacturing.domain.Routing` (routings) | tasks inline JSON `{seq, name, estimatedDuration}` |
| MrpServices.java (netting loop) | stockTmp accumulation | `manufacturing.domain.MrpPlanner` | per-product netting ordered by occurred_at/event_id; one proposal per short product |

Events consumed (P6-T1 demand/supply legs): `order.order.placed.v1`
(lines[].productId/quantity), `inventory.stock.received.v1`,
`inventory.stock.adjusted.v1`. Events published:
`manufacturing.bom.created/updated/deleted.v1`,
`manufacturing.routing.created/updated.v1`,
`manufacturing.mrp.proposed.v1`.

## Deferred (still monolith-owned)

| Monolith entity | Why deferred |
|---|---|
| MrpRunLog | run logging replaced by OTel traces + the run endpoint response; re-add if a run-history UI appears |
| ProductManufacturingRule | constraint rules (ProdConf) not needed by v1 netting; re-add with BOM explosion |
| TechDataCalendar / TechDataCalendarWeek / TechDataCalendarExcDay / TechDataCalendarExcWeek | capacity calendars — needed only for finite scheduling, not for netting |

Routing-linked monolith machinery (WorkEffort deps, routing tasks as
WorkEffort children, TechDataCalendar scheduling) stays in the monolith
until finite-capacity scheduling moves over (post-P6 story).
