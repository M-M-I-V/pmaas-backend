-- V6__inventory_module.sql

CREATE TABLE inventory_items (
     id              BIGSERIAL       PRIMARY KEY,
     item_name       VARCHAR(200)    NOT NULL,
     brand_name      VARCHAR(200),
     category        VARCHAR(20)     NOT NULL
         CHECK (category IN ('MEDICINE', 'SUPPLIES', 'CONSUMABLES')),
     description     TEXT,
     stock_on_hand   INTEGER         NOT NULL DEFAULT 0
         CHECK (stock_on_hand >= 0),
     expiration_date DATE,
     date_received   DATE,
     remarks         TEXT,
     created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
     updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Functional index: accelerates LOWER(item_name) LIKE LOWER('%query%') searches
CREATE INDEX idx_inventory_item_name_lower
    ON inventory_items (LOWER(item_name));

CREATE INDEX idx_inventory_category
    ON inventory_items (category);

CREATE INDEX idx_inventory_expiration
    ON inventory_items (expiration_date);

CREATE INDEX idx_inventory_date_received
    ON inventory_items (date_received);

-- Composite index for duplicate detection during Excel import
CREATE INDEX idx_inventory_duplicate_check
    ON inventory_items (LOWER(item_name), date_received, category);

COMMENT ON TABLE inventory_items IS
    'Clinic inventory items: medicines, supplies, and consumables.';

COMMENT ON COLUMN inventory_items.category IS
    'Enum: MEDICINE | SUPPLIES | CONSUMABLES — matches Excel sheet name on import.';