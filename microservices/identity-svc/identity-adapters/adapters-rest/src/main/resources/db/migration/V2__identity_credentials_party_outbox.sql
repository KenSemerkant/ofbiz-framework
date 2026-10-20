-- P1-F1-S2/S3: credential store, party projection, outbox, idempotency.
CREATE TABLE user_logins (
    user_login_id     VARCHAR(255) PRIMARY KEY,
    party_id          VARCHAR(255),
    current_password  VARCHAR(512) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    roles             VARCHAR(255) NOT NULL DEFAULT 'USER',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE party_projection (
    party_id    VARCHAR(255) PRIMARY KEY,
    payload     TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id            BIGSERIAL PRIMARY KEY,
    event_type    VARCHAR(128) NOT NULL,
    payload       TEXT         NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE TABLE processed_events (
    event_id      VARCHAR(255) PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
