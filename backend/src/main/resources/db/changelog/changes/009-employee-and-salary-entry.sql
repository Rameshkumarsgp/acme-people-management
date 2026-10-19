--liquibase formatted sql

--changeset acme:009-create-employee
CREATE TABLE employee (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name        VARCHAR(200) NOT NULL,
    email            VARCHAR(254) NOT NULL,
    hire_date        DATE         NOT NULL,
    termination_date DATE,
    department_id    INTEGER      NOT NULL REFERENCES department (id),
    job_title_id     INTEGER      NOT NULL REFERENCES job_title (id),
    country_code     VARCHAR(2)   NOT NULL REFERENCES country (code),
    -- The optimistic-lock column. Hibernate increments it on every profile update.
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT employee_full_name_is_not_blank CHECK (length(trim(full_name)) > 0),
    CONSTRAINT employee_email_is_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT employee_termination_not_before_hire CHECK (termination_date >= hire_date)
);
-- One email per employee, whatever the case. The service relies on this index's name to report a duplicate.
CREATE UNIQUE INDEX employee_email_unique ON employee (lower(email));
-- The directory and the insights filter and group on these three.
CREATE INDEX employee_country_idx ON employee (country_code);
CREATE INDEX employee_department_idx ON employee (department_id);
CREATE INDEX employee_job_title_idx ON employee (job_title_id);
--rollback DROP TABLE employee;

--changeset acme:010-create-salary-entry
CREATE TABLE salary_entry (
    id             BIGINT     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id    BIGINT     NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    effective_date DATE       NOT NULL,
    -- Annual base pay in minor units. No end date: it is derived from the next entry.
    amount_minor   BIGINT     NOT NULL,
    currency_code  VARCHAR(3) NOT NULL REFERENCES currency (code),
    CONSTRAINT salary_entry_one_per_employee_and_date UNIQUE (employee_id, effective_date),
    -- 9007199254740991 is the largest integer that a double and a JavaScript number both hold exactly.
    -- Money.MAX_AMOUNT_MINOR states the same value.
    CONSTRAINT salary_entry_amount_in_range CHECK (amount_minor BETWEEN 1 AND 9007199254740991)
);
--rollback DROP TABLE salary_entry;
