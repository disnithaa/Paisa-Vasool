CREATE TABLE app.offers (
    id UUID PRIMARY KEY,
    merchant_name VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    offer_type VARCHAR(32) NOT NULL,
    discount_percentage NUMERIC(5, 2),
    discount_amount NUMERIC(12, 2),
    minimum_spend NUMERIC(12, 2) NOT NULL,
    maximum_discount NUMERIC(12, 2),
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL,
    is_sample BOOLEAN NOT NULL,
    terms VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_offers_type_discount_values CHECK (
        (offer_type = 'PERCENTAGE_DISCOUNT'
            AND discount_percentage IS NOT NULL
            AND discount_percentage > 0
            AND discount_percentage <= 100
            AND discount_amount IS NULL)
        OR
        (offer_type = 'FIXED_DISCOUNT'
            AND discount_percentage IS NULL
            AND discount_amount IS NOT NULL
            AND maximum_discount IS NULL)
    ),
    CONSTRAINT ck_offers_minimum_spend_non_negative CHECK (minimum_spend >= 0),
    CONSTRAINT ck_offers_discount_amount_non_negative CHECK (
        discount_amount IS NULL OR discount_amount >= 0
    ),
    CONSTRAINT ck_offers_maximum_discount_non_negative CHECK (
        maximum_discount IS NULL OR maximum_discount >= 0
    ),
    CONSTRAINT ck_offers_validity_range CHECK (valid_until > valid_from)
);

CREATE INDEX ix_offers_active_validity
    ON app.offers (active, valid_from, valid_until);

CREATE INDEX ix_offers_merchant_title
    ON app.offers (merchant_name, title);

INSERT INTO app.offers (
    id, merchant_name, title, description, offer_type,
    discount_percentage, discount_amount, minimum_spend, maximum_discount,
    valid_from, valid_until, active, is_sample, terms
) VALUES
(
    'e0000000-0000-4000-8000-000000000001',
    'Sample Merchant One',
    'Sample: 10 percent off',
    'Fictional catalog entry for local development and testing.',
    'PERCENTAGE_DISCOUNT',
    10.00,
    NULL,
    500.00,
    100.00,
    '2026-01-01T00:00:00Z',
    '2030-01-01T00:00:00Z',
    TRUE,
    TRUE,
    'Sample terms only. This fictional offer has no real-world value.'
),
(
    'e0000000-0000-4000-8000-000000000002',
    'Sample Merchant Two',
    'Sample: 75 INR off',
    'Fictional catalog entry for local development and testing.',
    'FIXED_DISCOUNT',
    NULL,
    75.00,
    300.00,
    NULL,
    '2026-01-01T00:00:00Z',
    '2030-01-01T00:00:00Z',
    TRUE,
    TRUE,
    'Sample terms only. This fictional offer has no real-world value.'
);
