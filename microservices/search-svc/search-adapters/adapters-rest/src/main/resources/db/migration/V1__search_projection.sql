-- P3-F3-S1: consumer idempotency (ADR-009) + category-name projection
-- (product events carry category IDs only; names resolve from local state).
CREATE TABLE processed_events (
    event_id      VARCHAR(255) PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE product_categories (
    product_category_id VARCHAR(255) PRIMARY KEY,
    category_name       VARCHAR(255) NOT NULL
);
