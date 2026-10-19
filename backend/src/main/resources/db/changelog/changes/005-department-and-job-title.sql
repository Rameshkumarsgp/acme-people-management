--liquibase formatted sql

--changeset acme:005-create-department
CREATE TABLE department (
    id   INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT department_name_is_not_blank CHECK (length(trim(name)) > 0)
);
-- One name per department, whatever the case, so "Finance" and "finance" cannot both exist.
CREATE UNIQUE INDEX department_name_unique ON department (lower(name));
--rollback DROP TABLE department;

--changeset acme:006-load-departments
INSERT INTO department (name) VALUES
    ('Engineering'),
    ('Product'),
    ('Design'),
    ('Data and Analytics'),
    ('Sales'),
    ('Marketing'),
    ('Customer Support'),
    ('Finance'),
    ('Human Resources'),
    ('Operations');
--rollback DELETE FROM department;

--changeset acme:007-create-job-title
CREATE TABLE job_title (
    id   INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT job_title_name_is_not_blank CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX job_title_name_unique ON job_title (lower(name));
--rollback DROP TABLE job_title;

--changeset acme:008-load-job-titles
-- No levels or grades: there is no "Senior" or "Junior" title. See docs/tradeoffs.md.
INSERT INTO job_title (name) VALUES
    ('Software Engineer'),
    ('QA Engineer'),
    ('DevOps Engineer'),
    ('Engineering Manager'),
    ('Data Analyst'),
    ('Data Scientist'),
    ('Product Manager'),
    ('Product Designer'),
    ('UX Researcher'),
    ('Sales Executive'),
    ('Account Manager'),
    ('Sales Manager'),
    ('Marketing Specialist'),
    ('Content Writer'),
    ('Customer Support Agent'),
    ('Support Team Lead'),
    ('Accountant'),
    ('Financial Analyst'),
    ('Finance Manager'),
    ('Recruiter'),
    ('HR Business Partner'),
    ('HR Manager'),
    ('Operations Analyst'),
    ('Operations Manager');
--rollback DELETE FROM job_title;
