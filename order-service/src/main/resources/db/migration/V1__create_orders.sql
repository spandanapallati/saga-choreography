CREATE TABLE orders (
    id             VARCHAR(36)   PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    product_id     BIGINT        NOT NULL,
    quantity       INT           NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_orders_status_created ON orders (status, created_at);
