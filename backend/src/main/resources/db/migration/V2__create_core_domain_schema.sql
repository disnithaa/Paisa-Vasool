CREATE TABLE app.app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    CONSTRAINT uq_app_users_email UNIQUE (email)
);

CREATE TABLE app.expense_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE app.group_memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    group_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_group_memberships_user
        FOREIGN KEY (user_id) REFERENCES app.app_users (id),
    CONSTRAINT fk_group_memberships_group
        FOREIGN KEY (group_id) REFERENCES app.expense_groups (id),
    CONSTRAINT uq_group_memberships_group_user UNIQUE (group_id, user_id),
    CONSTRAINT ck_group_memberships_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT ck_group_memberships_status CHECK (status IN ('ACTIVE', 'LEFT'))
);

CREATE INDEX ix_group_memberships_user_id ON app.group_memberships (user_id);

CREATE TABLE app.purchases (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    created_by UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    estimated_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_purchases_group
        FOREIGN KEY (group_id) REFERENCES app.expense_groups (id),
    CONSTRAINT fk_purchases_created_by
        FOREIGN KEY (created_by) REFERENCES app.app_users (id),
    CONSTRAINT ck_purchases_estimated_amount_positive CHECK (estimated_amount > 0),
    CONSTRAINT ck_purchases_status CHECK (
        status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CANCELLED', 'COMPLETED')
    )
);

CREATE INDEX ix_purchases_group_id_created_at
    ON app.purchases (group_id, created_at DESC);
