-- P6-T4: content tree + outbox. Object bytes live in S3 (ADR-008), never
-- in the database — contents rows only carry the s3Key reference.
CREATE TABLE contents (
    content_id        VARCHAR(255) PRIMARY KEY,
    content_type_id   VARCHAR(16)  NOT NULL,          -- FOLDER | DOCUMENT
    content_name      VARCHAR(255) NOT NULL,
    parent_content_id VARCHAR(255) REFERENCES contents(content_id),
    status_id         VARCHAR(16)  NOT NULL,          -- DRAFT | PUBLISHED
    s3_key            VARCHAR(512),
    content_type      VARCHAR(255),
    size_bytes        BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_contents_parent ON contents(parent_content_id);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(128) NOT NULL,
    aggregate_key  VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
