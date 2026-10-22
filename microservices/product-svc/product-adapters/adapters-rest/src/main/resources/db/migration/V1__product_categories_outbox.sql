-- P3-F1-S1: product aggregate store, categories, outbox.
-- memberships/assocs are stored inline as JSON (same call as party-svc's
-- contact_mechs: no cross-product query in any Phase-3 story; normalize to
-- ProductCategoryMember/ProductAssoc tables if one appears).
CREATE TABLE products (
    product_id      VARCHAR(255) PRIMARY KEY,
    product_type_id VARCHAR(100) NOT NULL,
    internal_name   VARCHAR(255) NOT NULL,
    product_name    VARCHAR(255),
    categories      TEXT         NOT NULL DEFAULT '[]',
    assocs          TEXT         NOT NULL DEFAULT '[]',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- No self-FK on primary_parent_category_id: children may be loaded before
-- their parents; the domain cycle check guards the tree query.
CREATE TABLE product_categories (
    product_category_id        VARCHAR(255) PRIMARY KEY,
    primary_parent_category_id VARCHAR(255),
    category_name              VARCHAR(255) NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(128) NOT NULL,
    aggregate_key  VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
