-- P3-F1-S3: store settings (ProductStore slice). inventory_facility_id is an
-- id owned by inventory-svc (P3-F2) — reference only, no FK across services.
CREATE TABLE product_stores (
    product_store_id        VARCHAR(255) PRIMARY KEY,
    store_name              VARCHAR(255) NOT NULL,
    default_currency_uom_id VARCHAR(20),
    inventory_facility_id   VARCHAR(255),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
