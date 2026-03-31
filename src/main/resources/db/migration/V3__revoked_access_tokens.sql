CREATE TABLE revoked_access_tokens
(
    token_hash VARCHAR(64) NOT NULL,
    expiry     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_revoked_access_tokens PRIMARY KEY (token_hash)
);
