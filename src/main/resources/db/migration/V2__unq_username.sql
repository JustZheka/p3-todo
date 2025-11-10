ALTER TABLE refresh_tokens
    ADD CONSTRAINT uc_refresh_tokens_username UNIQUE (username);

ALTER TABLE refresh_tokens
    ALTER COLUMN username SET NOT NULL;