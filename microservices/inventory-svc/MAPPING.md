# inventory-svc schema mapping

inventory-svc owns the facility slice of
`monolith/applications/datamodel/entitydef/product-entitymodel.xml` (Facility*,
InventoryItem* — ≈17 entities per FUTURESTATE-ARCHITECTURE §4) plus the whole
`shipment-entitymodel.xml` (35 entities by current count; ≈47 in the
architecture doc including picklist/receipt roles) — the ≈64 total named by
the P3-F2 story.

## Live slice (owned by inventory-svc)

| OFBiz entity | inventory-svc home | Notes |
|---|---|---|
| Facility | `inventory.domain.Facility` (facilities) | id + name + type; owner party/contact mech/location wait for a story |
| InventoryItem | `inventory.domain.InventoryItem` (inventory_items) | ATP/QOH running totals + @Version optimistic lock (P3-F2-S2); serialNumber/unitCost optional |
| InventoryItemDetail | not carried as a table | every receive/adjust/transfer/confirm appends its `inventory.stock.*` event with delta + after-snapshot — the outbox IS the detail ledger; model the table if accounting needs a queryable one |
| InventoryTransfer | `transfer` API op | transfer(fromItemId, toItemId, qty) mutates both items; no transfer rows kept |
| OrderItemShipGrpInvRes / InventoryItemTempRes | `Reservation` (P3-F2-S2) | reservation saga replaces OFBiz's semaphore-style oversell prevention (FUTURESTATE-ARCHITECTURE §5.6) |
| Shipment | `Shipment` (P3-F2-S3) | CREATED→PACKED→SHIPPED status machine; orderId link optional |
| ShipmentItem | items JSON on shipments | inline like product-svc memberships; normalize to a table when a query needs them |

## Historical archive

The remaining facility/shipment entities (locations, contact mechs, picklists,
carrier/method boxes, receipts, issuances, cost/time estimates, gateway
configs, ...) are **not migrated**. They remain owned by the monolith until
their owning story pulls them. At Phase 7 (monolith retirement) untouched
tables are archived per ADR-012 deletion plan; nothing is dropped before then.
