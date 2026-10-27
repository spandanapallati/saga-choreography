CREATE TABLE wallet (
    user_id BIGINT        PRIMARY KEY,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0)
);

CREATE TABLE payment (
    order_id        VARCHAR(36)   PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    refund_attempts INT           NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ
);

CREATE INDEX idx_payment_refund_due ON payment (next_retry_at) WHERE status = 'REFUND_PENDING';
