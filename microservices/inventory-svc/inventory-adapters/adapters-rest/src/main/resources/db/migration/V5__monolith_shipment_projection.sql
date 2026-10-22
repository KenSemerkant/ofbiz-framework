-- P3-GATE: monolith CDC projection (ADR-012) for the shipment slice —
-- the pre-cutover mirror of the monolith SHIPMENT / SHIPMENT_ITEM tables,
-- rebuilt by re-consuming ofbiz.* topics. Read-mostly slice pre-P4: the
-- native shipments table stays the write model, this projection is the
-- migration consistency check.
CREATE TABLE processed_events (
    event_id      VARCHAR(255) PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE monolith_shipments (
    shipment_id  VARCHAR(255) PRIMARY KEY,
    status_id    VARCHAR(30),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- QUANTITY stays TEXT: Debezium serialises numerics as strings
-- (decimal.handling.mode=string); storing the wire form keeps the
-- projection byte-faithful to the event.
CREATE TABLE monolith_shipment_items (
    shipment_id         VARCHAR(255) NOT NULL,
    shipment_item_seq_id VARCHAR(20) NOT NULL,
    product_id          VARCHAR(255),
    quantity            VARCHAR(64),
    PRIMARY KEY (shipment_id, shipment_item_seq_id)
);
