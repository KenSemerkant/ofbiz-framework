-- P5-F1: invoices + payments + billing accounts + gateway attempts + outbox.
-- lines/history are inline JSON (order-svc call: no cross-line query in any
-- Phase-5 story).
CREATE TABLE invoices (
    invoice_id         VARCHAR(255) PRIMARY KEY,
    order_id           VARCHAR(255),
    customer_id        VARCHAR(255) NOT NULL,
    billing_account_id VARCHAR(255),
    status             VARCHAR(32)  NOT NULL,
    lines              TEXT         NOT NULL,
    history            TEXT         NOT NULL,
    total              NUMERIC(18,2) NOT NULL,
    currency_uom_id    VARCHAR(20)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    payment_id      VARCHAR(255) PRIMARY KEY,
    invoice_id      VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    amount          NUMERIC(18,2) NOT NULL,
    currency_uom_id VARCHAR(20)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    gateway_ref     VARCHAR(255),
    result_code     VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE billing_accounts (
    billing_account_id    VARCHAR(255) PRIMARY KEY,
    customer_id           VARCHAR(255) NOT NULL,
    credit_limit          NUMERIC(18,2) NOT NULL,
    outstanding_ar_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_days_term         INTEGER
);

CREATE TABLE gateway_attempts (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(255) NOT NULL,
    operation       VARCHAR(32)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    approved        BOOLEAN      NOT NULL,
    ref_num         VARCHAR(255),
    result_code     VARCHAR(64)  NOT NULL,
    attempted_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
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
