CREATE TABLE refresh_tokens
(
    id       VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    token    VARCHAR(2048),
    expiry   TIMESTAMP WITHOUT TIME ZONE,
    revoked  BOOLEAN      NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id)
);

CREATE TABLE subtasks
(
    id        UUID         NOT NULL,
    text      VARCHAR(255) NOT NULL,
    completed BOOLEAN      NOT NULL,
    task_id   UUID,
    CONSTRAINT pk_subtasks PRIMARY KEY (id)
);

CREATE TABLE todos
(
    id         UUID         NOT NULL,
    title      VARCHAR(255) NOT NULL,
    completed  BOOLEAN      NOT NULL,
    created_at date,
    deadline   date,
    ldap_uid   VARCHAR(255),
    CONSTRAINT pk_todos PRIMARY KEY (id)
);

ALTER TABLE subtasks
    ADD CONSTRAINT FK_SUBTASKS_ON_TASK FOREIGN KEY (task_id) REFERENCES todos (id);