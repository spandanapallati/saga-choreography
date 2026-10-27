CREATE TABLE stock (
    product_id BIGINT PRIMARY KEY,
    available  INT    NOT NULL CHECK (available >= 0)
);

CREATE TABLE reservation (
    order_id   VARCHAR(36) PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(20) NOT NULL
);
