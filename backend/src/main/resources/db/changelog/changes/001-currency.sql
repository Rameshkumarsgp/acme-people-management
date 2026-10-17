--liquibase formatted sql

--changeset acme:001-create-currency
CREATE TABLE currency (
    code              VARCHAR(3) PRIMARY KEY,
    minor_unit_digits INTEGER    NOT NULL,
    CONSTRAINT currency_code_is_three_capital_letters CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT currency_minor_unit_digits_not_negative CHECK (minor_unit_digits >= 0)
);
--rollback DROP TABLE currency;

--changeset acme:002-load-currencies
-- The currencies of the ten countries we use. Codes and minor-unit digits come from the JDK's
-- ISO 4217 data (java.util.Currency). One minor unit of JPY is a whole yen, so it has no decimals.
INSERT INTO currency (code, minor_unit_digits) VALUES
    ('INR', 2),
    ('USD', 2),
    ('GBP', 2),
    ('EUR', 2),
    ('SGD', 2),
    ('AUD', 2),
    ('CAD', 2),
    ('JPY', 0),
    ('AED', 2);
--rollback DELETE FROM currency;
