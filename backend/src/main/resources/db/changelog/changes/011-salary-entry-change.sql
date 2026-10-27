--liquibase formatted sql

--changeset acme:011-create-salary-entry-change
CREATE TABLE salary_entry_change (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id        BIGINT      NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    -- Deliberately not a foreign key: the entry may have been deleted, and its change rows stay.
    entry_id           BIGINT      NOT NULL,
    action             VARCHAR(10) NOT NULL,
    old_effective_date DATE,
    old_amount_minor   BIGINT,
    old_currency_code  VARCHAR(3)  REFERENCES currency (code),
    new_effective_date DATE,
    new_amount_minor   BIGINT,
    new_currency_code  VARCHAR(3)  REFERENCES currency (code),
    changed_at         TIMESTAMPTZ NOT NULL,
    -- An added entry has only new values, a deleted one only old values, and an edited one has both.
    -- This also rejects any action other than these three.
    CONSTRAINT salary_entry_change_values_match_action CHECK (
        (action = 'ADDED' AND old_effective_date IS NULL AND new_effective_date IS NOT NULL)
        OR (action = 'EDITED' AND old_effective_date IS NOT NULL AND new_effective_date IS NOT NULL)
        OR (action = 'DELETED' AND old_effective_date IS NOT NULL AND new_effective_date IS NULL))
);
-- The change log of one employee, newest first.
CREATE INDEX salary_entry_change_employee_idx ON salary_entry_change (employee_id, changed_at DESC);
--rollback DROP TABLE salary_entry_change;
