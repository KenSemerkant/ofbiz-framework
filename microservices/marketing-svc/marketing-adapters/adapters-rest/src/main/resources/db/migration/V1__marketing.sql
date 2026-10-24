-- P6-T5: campaigns + tracking codes + opportunities + attributions +
-- outbox. The attribution order_id primary key gives consumer idempotency
-- behind processed_events (ADR-009).
CREATE TABLE marketing_campaigns (
    marketing_campaign_id VARCHAR(255) PRIMARY KEY,
    campaign_name         VARCHAR(255) NOT NULL,
    status_id             VARCHAR(32)  NOT NULL,
    from_date             DATE NOT NULL,
    thru_date             DATE NOT NULL
);

CREATE TABLE tracking_codes (
    tracking_code_id      VARCHAR(255) PRIMARY KEY,
    marketing_campaign_id VARCHAR(255),
    description           VARCHAR(512),
    from_date             DATE NOT NULL,
    thru_date             DATE NOT NULL
);

CREATE INDEX idx_tracking_codes_campaign ON tracking_codes (marketing_campaign_id);

CREATE TABLE sales_opportunities (
    sales_opportunity_id  VARCHAR(255) PRIMARY KEY,
    description           VARCHAR(1024),
    opportunity_stage_id  VARCHAR(64) NOT NULL,
    close_probability     INT NOT NULL CHECK (close_probability BETWEEN 0 AND 100)
);

CREATE TABLE attributions (
    order_id     VARCHAR(255) PRIMARY KEY,
    tracking_code VARCHAR(255) NOT NULL,
    order_total  NUMERIC(18,2) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attributions_tracking_code ON attributions (tracking_code);

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
