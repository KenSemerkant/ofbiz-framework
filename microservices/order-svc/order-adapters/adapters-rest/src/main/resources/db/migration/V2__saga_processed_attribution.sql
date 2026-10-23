-- P4-F2: saga state (guarded step markers), consumer idempotency,
-- marketing attributions, monolith CDC projection.
CREATE TABLE saga_state (
    order_id     VARCHAR(255) PRIMARY KEY,
    step         VARCHAR(32) NOT NULL,
    reservations JSONB       NOT NULL DEFAULT '[]',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_attributions (
    order_id      VARCHAR(255) PRIMARY KEY,
    customer_id   VARCHAR(255) NOT NULL,
    tracking_code VARCHAR(100) NOT NULL,
    attributed_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE monolith_order_projection (
    event_id    VARCHAR(255) PRIMARY KEY,
    topic       VARCHAR(128) NOT NULL,
    payload     TEXT         NOT NULL,
    applied_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
