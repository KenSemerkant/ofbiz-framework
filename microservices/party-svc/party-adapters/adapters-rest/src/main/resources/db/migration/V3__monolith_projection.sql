-- P2-F2-S2: monolith CDC projection (ADR-012) — the pre-cutover mirror of
-- the monolith party slice, rebuilt by re-consuming ofbiz.* topics.
CREATE TABLE monolith_parties (
    party_id       VARCHAR(255) PRIMARY KEY,
    party_type_id  VARCHAR(20),
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    group_name     VARCHAR(255),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE monolith_party_roles (
    party_id      VARCHAR(255) NOT NULL REFERENCES monolith_parties(party_id) ON DELETE CASCADE,
    role_type_id  VARCHAR(100) NOT NULL,
    PRIMARY KEY (party_id, role_type_id)
);
