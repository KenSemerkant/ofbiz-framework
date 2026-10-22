-- P3-GATE: monolith CDC projection (ADR-012) — the pre-cutover mirror of
-- the monolith product slice, rebuilt by re-consuming ofbiz.* topics.
-- One projection table per mirrored monolith table (product categories,
-- memberships, prices, associations), so the consistency checker can fold
-- the same joins as the monolith side.
CREATE TABLE processed_events (
    event_id      VARCHAR(255) PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE monolith_products (
    product_id      VARCHAR(255) PRIMARY KEY,
    product_type_id VARCHAR(100),
    internal_name   VARCHAR(255),
    product_name    VARCHAR(255),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE monolith_product_categories (
    product_category_id VARCHAR(255) PRIMARY KEY,
    category_name       VARCHAR(255)
);

CREATE TABLE monolith_product_members (
    product_id        VARCHAR(255) NOT NULL,
    product_category_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, product_category_id)
);

-- FROM_DATE/PRICE stay TEXT: Debezium serialises numerics as strings
-- (decimal.handling.mode=string) and timestamps as epoch values; storing
-- the wire form keeps the projection byte-faithful to the event.
CREATE TABLE monolith_product_prices (
    product_id           VARCHAR(255) NOT NULL,
    product_price_type_id VARCHAR(100) NOT NULL,
    currency_uom_id      VARCHAR(20)  NOT NULL,
    from_date            VARCHAR(64)  NOT NULL,
    price                VARCHAR(64)  NOT NULL,
    PRIMARY KEY (product_id, product_price_type_id, currency_uom_id, from_date)
);

CREATE TABLE monolith_product_assocs (
    product_id           VARCHAR(255) NOT NULL,
    product_id_to        VARCHAR(255) NOT NULL,
    product_assoc_type_id VARCHAR(100) NOT NULL,
    from_date            VARCHAR(64)  NOT NULL,
    PRIMARY KEY (product_id, product_id_to, product_assoc_type_id, from_date)
);

CREATE INDEX idx_monolith_members_category ON monolith_product_members (product_category_id);
CREATE INDEX idx_monolith_prices_product ON monolith_product_prices (product_id);
CREATE INDEX idx_monolith_assocs_product ON monolith_product_assocs (product_id);
