-- P6-T2: work efforts + timesheets + outbox. Timesheet entries are inline
-- JSON on the sheet row (inline-JSON precedent; no cross-entry query in
-- any Phase-6 story).
CREATE TABLE work_efforts (
    work_effort_id             VARCHAR(255) PRIMARY KEY,
    work_effort_type_id        VARCHAR(16)  NOT NULL,
    work_effort_name           VARCHAR(255) NOT NULL,
    parent_work_effort_id      VARCHAR(255),
    status_id                  VARCHAR(16)  NOT NULL,
    estimated_start_date       DATE,
    estimated_completion_date  DATE,
    created_at                 DATE NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_efforts_parent ON work_efforts (parent_work_effort_id);

CREATE TABLE timesheets (
    timesheet_id  VARCHAR(255) PRIMARY KEY,
    party_id      VARCHAR(255) NOT NULL,
    from_date     DATE NOT NULL,
    thru_date     DATE NOT NULL,
    status_id     VARCHAR(16)  NOT NULL,
    entries       TEXT NOT NULL,
    created_at    DATE NOT NULL DEFAULT now()
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
