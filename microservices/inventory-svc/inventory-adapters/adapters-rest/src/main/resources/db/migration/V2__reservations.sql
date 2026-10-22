-- P3-F2-S2: reservation saga (reserve -> confirm/cancel).
-- order_id references an order owned by order-svc (ADR-007) — no FK.
CREATE TABLE reservations (
    reservation_id     VARCHAR(64)  PRIMARY KEY,
    order_id           VARCHAR(255) NOT NULL,
    order_item_seq_id  VARCHAR(60)  NOT NULL,
    product_id         VARCHAR(255) NOT NULL,
    facility_id        VARCHAR(255) NOT NULL,
    inventory_item_id  VARCHAR(255) NOT NULL,
    quantity           NUMERIC(18,3) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_reservations_order ON reservations (order_id);
