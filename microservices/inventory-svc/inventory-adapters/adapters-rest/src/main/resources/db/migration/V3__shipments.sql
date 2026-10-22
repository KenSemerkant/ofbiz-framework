-- P3-F2-S3: shipments. order_id references an order owned by order-svc
-- (ADR-007) — no FK. Items are inline JSON (product-svc convention).
CREATE TABLE shipments (
    shipment_id  VARCHAR(255) PRIMARY KEY,
    order_id     VARCHAR(255),
    status       VARCHAR(20)  NOT NULL,
    items        TEXT         NOT NULL DEFAULT '[]',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
