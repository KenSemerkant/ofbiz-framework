-- P4-F1: order aggregate store + quotes + returns + outbox.
-- lines/history/reservations are inline JSON (same call as product-svc's
-- assocs/prices: no cross-line query in any Phase-4 story).
CREATE TABLE orders (
    order_id      VARCHAR(255) PRIMARY KEY,
    customer_id   VARCHAR(255) NOT NULL,
    tracking_code VARCHAR(100),
    status        VARCHAR(32)  NOT NULL,
    lines         TEXT         NOT NULL,
    history       TEXT         NOT NULL,
    grand_total   NUMERIC(18,2) NOT NULL,
    currency_uom_id VARCHAR(20) NOT NULL,
    placed_at     TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE quotes (
    quote_id    VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    lines       TEXT         NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE returns (
    return_id VARCHAR(255) PRIMARY KEY,
    order_id  VARCHAR(255) NOT NULL,
    status    VARCHAR(32)  NOT NULL,
    lines     TEXT         NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(128) NOT NULL,
    aggregate_key  VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
