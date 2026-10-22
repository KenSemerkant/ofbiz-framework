-- P3-F2-S1: facilities, inventory items, outbox.
-- product_id/facility_id references are ids owned by other services
-- (ADR-007 database-per-service) — no cross-service FKs.
CREATE TABLE facilities (
    facility_id     VARCHAR(255) PRIMARY KEY,
    facility_name   VARCHAR(255) NOT NULL,
    facility_type_id VARCHAR(100),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- OFBiz InventoryItem slice: ATP/QOH running totals + optimistic-lock version
-- (P3-F2-S2 concurrency: conflicting mutations fail at flush, never overwrite).
CREATE TABLE inventory_items (
    inventory_item_id           VARCHAR(255) PRIMARY KEY,
    product_id                  VARCHAR(255) NOT NULL,
    facility_id                 VARCHAR(255) NOT NULL,
    serial_number               VARCHAR(255),
    unit_cost                   NUMERIC(18,2),
    quantity_on_hand_total      NUMERIC(18,3) NOT NULL,
    available_to_promise_total  NUMERIC(18,3) NOT NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_items_product_facility
    ON inventory_items (product_id, facility_id);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(128) NOT NULL,
    aggregate_key  VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
