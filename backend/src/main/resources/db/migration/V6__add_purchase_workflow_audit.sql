ALTER TABLE app.purchases ADD COLUMN approved_by_membership_id UUID;
ALTER TABLE app.purchases ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app.purchases ADD COLUMN cancelled_by_membership_id UUID;
ALTER TABLE app.purchases ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app.purchases ADD COLUMN completed_by_membership_id UUID;
ALTER TABLE app.purchases ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app.purchases ADD COLUMN completion_type VARCHAR(24);

ALTER TABLE app.purchases
    ADD CONSTRAINT fk_purchases_approved_by_membership
        FOREIGN KEY (approved_by_membership_id) REFERENCES app.group_memberships (id);

ALTER TABLE app.purchases
    ADD CONSTRAINT fk_purchases_cancelled_by_membership
        FOREIGN KEY (cancelled_by_membership_id) REFERENCES app.group_memberships (id);

ALTER TABLE app.purchases
    ADD CONSTRAINT fk_purchases_completed_by_membership
        FOREIGN KEY (completed_by_membership_id) REFERENCES app.group_memberships (id);

ALTER TABLE app.purchases
    ADD CONSTRAINT ck_purchases_approval_audit_pair CHECK (
        (approved_by_membership_id IS NULL AND approved_at IS NULL)
        OR (approved_by_membership_id IS NOT NULL AND approved_at IS NOT NULL)
    );

ALTER TABLE app.purchases
    ADD CONSTRAINT ck_purchases_cancellation_audit_pair CHECK (
        (cancelled_by_membership_id IS NULL AND cancelled_at IS NULL)
        OR (cancelled_by_membership_id IS NOT NULL AND cancelled_at IS NOT NULL)
    );

ALTER TABLE app.purchases
    ADD CONSTRAINT ck_purchases_completion_audit CHECK (
        (
            completed_by_membership_id IS NULL
            AND completed_at IS NULL
            AND completion_type IS NULL
        )
        OR
        (
            completed_by_membership_id IS NOT NULL
            AND completed_at IS NOT NULL
            AND completion_type IS NOT NULL
            AND completion_type = 'SIMULATED'
        )
    );
