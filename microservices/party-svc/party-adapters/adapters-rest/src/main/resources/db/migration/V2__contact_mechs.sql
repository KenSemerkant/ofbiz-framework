-- P2-F1-S2: contact mechanisms, stored inline as JSON.
-- ponytail: no cross-party contact-mech query in any Phase-2 story; normalize
-- to contact_mech/purpose tables if one appears.
ALTER TABLE parties ADD COLUMN contact_mechs TEXT NOT NULL DEFAULT '[]';
