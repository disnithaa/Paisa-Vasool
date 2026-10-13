ALTER TABLE app.purchases ADD COLUMN proposed_offer_id UUID;
ALTER TABLE app.purchases ADD COLUMN offer_merchant_snapshot VARCHAR(120);
ALTER TABLE app.purchases ADD COLUMN offer_title_snapshot VARCHAR(200);
ALTER TABLE app.purchases ADD COLUMN offer_description_snapshot VARCHAR(1000);
ALTER TABLE app.purchases ADD COLUMN offer_type_snapshot VARCHAR(32);
ALTER TABLE app.purchases ADD COLUMN offer_discount_percentage_snapshot NUMERIC(5, 2);
ALTER TABLE app.purchases ADD COLUMN offer_discount_amount_snapshot NUMERIC(12, 2);
ALTER TABLE app.purchases ADD COLUMN offer_minimum_spend_snapshot NUMERIC(12, 2);
ALTER TABLE app.purchases ADD COLUMN offer_maximum_discount_snapshot NUMERIC(12, 2);
ALTER TABLE app.purchases ADD COLUMN offer_valid_from_snapshot TIMESTAMP WITH TIME ZONE;
ALTER TABLE app.purchases ADD COLUMN offer_valid_until_snapshot TIMESTAMP WITH TIME ZONE;
ALTER TABLE app.purchases ADD COLUMN offer_terms_snapshot VARCHAR(2000);
ALTER TABLE app.purchases ADD COLUMN applied_discount_amount NUMERIC(12, 2);
ALTER TABLE app.purchases ADD COLUMN expected_final_amount NUMERIC(12, 2);
ALTER TABLE app.purchases ADD COLUMN offer_proposed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE app.purchases
    ADD CONSTRAINT fk_purchases_proposed_offer
        FOREIGN KEY (proposed_offer_id) REFERENCES app.offers (id);

ALTER TABLE app.purchases
    ADD CONSTRAINT ck_purchases_offer_proposal_snapshot CHECK (
        (
            offer_proposed_at IS NULL
            AND proposed_offer_id IS NULL
            AND offer_merchant_snapshot IS NULL
            AND offer_title_snapshot IS NULL
            AND offer_description_snapshot IS NULL
            AND offer_type_snapshot IS NULL
            AND offer_discount_percentage_snapshot IS NULL
            AND offer_discount_amount_snapshot IS NULL
            AND offer_minimum_spend_snapshot IS NULL
            AND offer_maximum_discount_snapshot IS NULL
            AND offer_valid_from_snapshot IS NULL
            AND offer_valid_until_snapshot IS NULL
            AND offer_terms_snapshot IS NULL
            AND applied_discount_amount IS NULL
            AND expected_final_amount IS NULL
        )
        OR
        (
            offer_proposed_at IS NOT NULL
            AND proposed_offer_id IS NOT NULL
            AND offer_merchant_snapshot IS NOT NULL
            AND offer_title_snapshot IS NOT NULL
            AND offer_description_snapshot IS NOT NULL
            AND offer_type_snapshot IS NOT NULL
            AND offer_minimum_spend_snapshot IS NOT NULL
            AND offer_minimum_spend_snapshot >= 0
            AND offer_valid_from_snapshot IS NOT NULL
            AND offer_valid_until_snapshot > offer_valid_from_snapshot
            AND offer_terms_snapshot IS NOT NULL
            AND applied_discount_amount IS NOT NULL
            AND applied_discount_amount >= 0
            AND expected_final_amount IS NOT NULL
            AND expected_final_amount >= 0
            AND applied_discount_amount + expected_final_amount = estimated_amount
            AND (
                (
                    offer_type_snapshot = 'PERCENTAGE_DISCOUNT'
                    AND offer_discount_percentage_snapshot IS NOT NULL
                    AND offer_discount_percentage_snapshot > 0
                    AND offer_discount_percentage_snapshot <= 100
                    AND offer_discount_amount_snapshot IS NULL
                    AND (
                        offer_maximum_discount_snapshot IS NULL
                        OR offer_maximum_discount_snapshot >= 0
                    )
                )
                OR
                (
                    offer_type_snapshot = 'FIXED_DISCOUNT'
                    AND offer_discount_percentage_snapshot IS NULL
                    AND offer_discount_amount_snapshot IS NOT NULL
                    AND offer_discount_amount_snapshot >= 0
                    AND offer_maximum_discount_snapshot IS NULL
                )
            )
        )
    );

CREATE INDEX ix_purchases_proposed_offer_id
    ON app.purchases (proposed_offer_id);
