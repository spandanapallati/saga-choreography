CREATE TABLE outbox (
    id          BIGSERIAL    PRIMARY KEY,
    topic       VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(10)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox (id) WHERE status = 'PENDING';
