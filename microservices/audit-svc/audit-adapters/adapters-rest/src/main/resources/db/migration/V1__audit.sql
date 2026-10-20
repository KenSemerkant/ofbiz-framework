-- P1-F3-S1: audit store, range-partitioned by month (partitions created on
-- demand by PartitionMaintainer; PK must include the partition key).
create table audit_events (
    event_id     text not null,
    topic        text not null,
    event_type   text not null,
    severity     text not null,
    payload      text,
    occurred_at  timestamptz not null,
    consumed_at  timestamptz not null,
    primary key (event_id, occurred_at)
) partition by range (occurred_at);

create index audit_events_topic_time_idx on audit_events (topic, occurred_at);

-- Idempotent consumption (ADR-009): skip event ids already seen.
create table processed_events (
    event_id    text primary key,
    consumed_at timestamptz not null default now()
);
