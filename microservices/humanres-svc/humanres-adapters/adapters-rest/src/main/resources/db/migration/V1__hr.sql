-- P6-T3: positions + employments + party projection + onboarding saga +
-- outbox. Party data is NOT duplicated — the projection carries display
-- names only (FUTURESTATE §5.10: party linkage by id).
CREATE TABLE empl_positions (
    empl_position_id               VARCHAR(255) PRIMARY KEY,
    empl_position_type_id          VARCHAR(64)  NOT NULL,
    reporting_to_empl_position_id  VARCHAR(255),
    status_id                      VARCHAR(64)  NOT NULL
);

CREATE TABLE employments (
    employment_id    VARCHAR(255) PRIMARY KEY,
    party_id         VARCHAR(255) NOT NULL,
    empl_position_id VARCHAR(255) NOT NULL,
    from_date        DATE         NOT NULL,
    thru_date        DATE
);

CREATE TABLE party_projection (
    party_id     VARCHAR(255) PRIMARY KEY,
    names        TEXT         NOT NULL,
    projected_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Onboarding saga state (order-svc saga_state pattern): guarded status
-- transitions + the completed-step log.
CREATE TABLE onboarding_saga (
    onboarding_id   VARCHAR(255) PRIMARY KEY,
    party_id        VARCHAR(255) NOT NULL,
    empl_position_id VARCHAR(255) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    steps           JSONB        NOT NULL DEFAULT '[]',
    failed_reason   VARCHAR(512),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
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
