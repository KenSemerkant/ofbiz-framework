-- P3-F1-S2: prices stored inline as JSON (ProductPrice slice: type,
-- currency, half-open from/thru range, amount).
ALTER TABLE products ADD COLUMN prices TEXT NOT NULL DEFAULT '[]';
