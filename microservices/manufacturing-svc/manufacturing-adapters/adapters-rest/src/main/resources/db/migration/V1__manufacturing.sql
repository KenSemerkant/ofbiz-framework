-- P6-T1: boms + routings + MRP ledger/proposals + outbox.
-- components/tasks are inline JSON on the row (gl_transactions precedent);
-- mrp_events.event_id is the PK so a replayed append collides instead of
-- double-counting (consumers also guard via processed_events).
CREATE TABLE boms (
    product_id  VARCHAR(255) PRIMARY KEY,
    components  TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE routings (
    routing_id  VARCHAR(255) PRIMARY KEY,
    product_id  VARCHAR(255) NOT NULL,
    tasks       TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE mrp_events (
    event_id    VARCHAR(255) PRIMARY KEY,
    product_id  VARCHAR(255) NOT NULL,
    event_type  VARCHAR(8)   NOT NULL,
    quantity    NUMERIC(18,3) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_mrp_events_product ON mrp_events (product_id);

CREATE TABLE mrp_proposals (
    id          BIGSERIAL PRIMARY KEY,
    product_id  VARCHAR(255) NOT NULL,
    quantity    NUMERIC(18,3) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(128) NOT NULL,
    aggregate_key  VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE TABLE processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
