-- P2-F1-S1: party aggregate store, outbox, idempotency (for P2-F2 consumers).
CREATE TABLE parties (
    party_id    VARCHAR(255) PRIMARY KEY,
    kind        VARCHAR(20)  NOT NULL,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    group_name  VARCHAR(255),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE party_roles (
    party_id      VARCHAR(255) NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
    role_type_id  VARCHAR(100) NOT NULL,
    PRIMARY KEY (party_id, role_type_id)
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
    event_id      VARCHAR(255) PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
