-- P5-F2: chart of accounts + journal + periods + posting rules + outbox.
-- entries are inline JSON on the transaction row (no cross-entry query in
-- any Phase-5 story).
CREATE TABLE gl_accounts (
    gl_account_id      VARCHAR(255) PRIMARY KEY,
    account_code       VARCHAR(64)  NOT NULL,
    account_name       VARCHAR(255) NOT NULL,
    post_balance       VARCHAR(8)   NOT NULL,
    gl_account_class_id VARCHAR(64)
);

CREATE TABLE gl_transactions (
    acctg_trans_id VARCHAR(255) PRIMARY KEY,
    trans_type     VARCHAR(64)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    period_id      VARCHAR(255) NOT NULL,
    entries        TEXT         NOT NULL,
    posted         BOOLEAN      NOT NULL,
    posted_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE periods (
    period_id  VARCHAR(255) PRIMARY KEY,
    from_date  DATE NOT NULL,
    thru_date  DATE NOT NULL,
    closed     BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE posting_rules (
    rule_id           VARCHAR(255) PRIMARY KEY,
    event_type        VARCHAR(128) NOT NULL UNIQUE,
    debit_account_id  VARCHAR(255) NOT NULL,
    credit_account_id VARCHAR(255) NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE parked_postings (
    id           BIGSERIAL PRIMARY KEY,
    event_type   VARCHAR(128) NOT NULL,
    aggregate_key VARCHAR(255) NOT NULL,
    reason       VARCHAR(512) NOT NULL,
    payload      TEXT NOT NULL,
    parked_at    TIMESTAMPTZ NOT NULL DEFAULT now()
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
