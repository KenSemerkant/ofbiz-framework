-- P1-F4-S1: idempotent consumption — one row per processed notificationId
-- (ADR-009 processed_events equivalent scoped to this service's table).
create table processed_notifications (
    notification_id varchar(64) primary key,
    processed_at    timestamptz not null
);
