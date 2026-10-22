-- P3-F2 quality review: optimistic-lock versions on reservations and
-- shipments. V2/V3 are already committed, so the columns land here rather
-- than editing immutable migrations. A stale write (e.g. a pack computed
-- off a pre-ship snapshot regressing SHIPPED -> PACKED) now fails at flush
-- instead of silently overwriting.
ALTER TABLE reservations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE shipments   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
