CREATE TABLE app.expenses (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    purchase_id UUID,
    description VARCHAR(200) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    paid_by_membership_id UUID NOT NULL,
    created_by_membership_id UUID NOT NULL,
    incurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_expenses_group
        FOREIGN KEY (group_id) REFERENCES app.expense_groups (id),
    CONSTRAINT fk_expenses_purchase
        FOREIGN KEY (purchase_id) REFERENCES app.purchases (id),
    CONSTRAINT fk_expenses_paid_by_membership
        FOREIGN KEY (paid_by_membership_id) REFERENCES app.group_memberships (id),
    CONSTRAINT fk_expenses_created_by_membership
        FOREIGN KEY (created_by_membership_id) REFERENCES app.group_memberships (id),
    CONSTRAINT ck_expenses_total_amount_positive CHECK (total_amount > 0)
);

CREATE INDEX ix_expenses_group_incurred_at
    ON app.expenses (group_id, incurred_at DESC);

CREATE TABLE app.expense_allocations (
    id UUID PRIMARY KEY,
    expense_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_expense_allocations_expense
        FOREIGN KEY (expense_id) REFERENCES app.expenses (id),
    CONSTRAINT fk_expense_allocations_membership
        FOREIGN KEY (membership_id) REFERENCES app.group_memberships (id),
    CONSTRAINT uq_expense_allocations_expense_member UNIQUE (expense_id, membership_id),
    CONSTRAINT ck_expense_allocations_amount_positive CHECK (amount > 0)
);

CREATE INDEX ix_expense_allocations_membership_id
    ON app.expense_allocations (membership_id);

CREATE TABLE app.settlements (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    from_membership_id UUID NOT NULL,
    to_membership_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    created_by_membership_id UUID NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_settlements_group
        FOREIGN KEY (group_id) REFERENCES app.expense_groups (id),
    CONSTRAINT fk_settlements_from_membership
        FOREIGN KEY (from_membership_id) REFERENCES app.group_memberships (id),
    CONSTRAINT fk_settlements_to_membership
        FOREIGN KEY (to_membership_id) REFERENCES app.group_memberships (id),
    CONSTRAINT fk_settlements_created_by_membership
        FOREIGN KEY (created_by_membership_id) REFERENCES app.group_memberships (id),
    CONSTRAINT ck_settlements_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_settlements_distinct_members
        CHECK (from_membership_id <> to_membership_id)
);

CREATE INDEX ix_settlements_group_settled_at
    ON app.settlements (group_id, settled_at DESC);
