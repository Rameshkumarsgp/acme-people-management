--liquibase formatted sql

--changeset acme:003-create-country
CREATE TABLE country (
    code VARCHAR(2)   PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT country_code_is_two_capital_letters CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT country_name_is_not_blank CHECK (length(trim(name)) > 0)
);
--rollback DROP TABLE country;

--changeset acme:004-load-countries
-- The ten countries we use. Codes and English names come from the JDK's ISO 3166 data.
-- A country does not decide a currency: the currency belongs to each salary entry.
INSERT INTO country (code, name) VALUES
    ('IN', 'India'),
    ('US', 'United States'),
    ('GB', 'United Kingdom'),
    ('DE', 'Germany'),
    ('FR', 'France'),
    ('SG', 'Singapore'),
    ('AU', 'Australia'),
    ('CA', 'Canada'),
    ('JP', 'Japan'),
    ('AE', 'United Arab Emirates');
--rollback DELETE FROM country;
