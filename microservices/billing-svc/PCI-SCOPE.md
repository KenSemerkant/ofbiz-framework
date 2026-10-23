# billing-svc PCI scope (CG-5)

**PCI scope is confined to billing-svc.** Within the whole target stack,
only billing-svc touches payment instrument data, and in P5 it touches
none: the TestGatewayAdapter carries an idempotency key + amount + currency
only — no PAN, no CVV, no expiry (the test gateway approves deterministically
without card data; see `TestGatewayAdapter`).

Rules that keep it that way as real PSP adapters arrive:

1. Payment-instrument data (CreditCard / EftAccount / CheckAccount rows,
   per MAPPING.md owned by billing-svc) is never persisted raw — only the
   PSP token/reference returned by a gateway call (`payments.gateway_ref`).
   The monolith's CreditCard tables stay archived, unreplaced.
2. The `PaymentGateway` port is the only seam through which instrument
   data may leave the service; it lives in `billing.application` and every
   implementation is inside billing-svc. No other service gets a gateway
   adapter, and no instrument field appears on any `billing.*` topic.
3. `gateway_attempts` logs operation + idempotency key + result codes —
   never instrument data.
4. The invoice email (notification.requested.v1) carries invoiceId +
   total + currency only.

Scope boundary review: any PR adding a field to `Payment`,
`GatewayAttempt`, or the event payloads that could carry instrument data
must update this document.
