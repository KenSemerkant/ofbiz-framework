# order-svc

Phase 4 domain service: order aggregate + status machine, quotes, returns,
and the placeOrder saga (party validation → price snapshot → placed event →
ECA steps: inventory reserve, invoice trigger, marketing attribution).

- **Domain** (`order-domain`): `Order` (enum-guarded transitions, append-only
  history, immutable price-snapshot lines), `Quote`, `ReturnOrder` — pure
  Java, zero framework (ADR-007, ArchUnit-enforced).
- **Application** (`order-application`): `PlaceOrderSaga`, `OrderEcaService`
  (guarded saga-state markers, resumable after restart, idempotent under
  redelivery), `OrderLifecycleService`, `QuoteService`, `ReturnService`,
  `AttributionService` + outbound ports (party/product/inventory).
- **Adapters** (`order-adapters/adapters-rest`): REST edge, JPA/JDBC stores,
  transactional outbox + relay, ECA consumers on `order.order.placed.v1` /
  `order.order.cancelled.v1`, reverse-sync into the monolith ORDER_* tables,
  CDC projection for the rollback path.

Events (topics): `order.order.placed/approved/completed/cancelled/
invoicerequested.v1`, `order.return.created.v1`, `order.creditmemo.issued.v1`.

Gate evidence: `issues/P4-GATE.md`; lifecycle drill
`platform/services/70-order-lifecycle.sh`; consistency
`platform/cdc/33-check-order-consistency.sh`; rollback
`platform/cdc/65-rollback-drill-order.sh`.
