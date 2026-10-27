# SAGA Choreography: Spring Boot + Kafka + PostgreSQL + Flyway + Transactional Outbox

Three Spring Boot 3.3 services (**Spring MVC**, no WebFlux; plain **spring-kafka**, no Spring Cloud Stream),
one PostgreSQL database per service, Flyway migrations, and the outbox pattern.

| Service            | Port | Database      | Role |
|--------------------|------|---------------|------|
| `order-service`    | 8081 | `orderdb`     | Starts the saga, receives the final outcome, runs the timeout watchdog |
| `payment-service`  | 8082 | `paymentdb`   | Charges the wallet; compensates with a refund (retry job) |
| `inventory-service`| 8083 | `inventorydb` | Reserves stock; releases it when an order is cancelled |

## Flow

```
POST /orders -> Order --order-created--> Payment --payment-succeeded--> Inventory
                  ^  ^                      |                              |
                  |  +---- payment-failed --+                              |
                  +------- inventory-reserved (success) -------------------+
                  +------- inventory-failed   (failure) ------------------+
                                                 |
        Payment also consumes inventory-failed --+--> REFUND_PENDING --> RefundJob (retries) --> REFUNDED

SagaTimeoutJob (Order): order stuck in CREATED > 120s --> CANCELLED + order-cancelled
                        --> Payment refunds, Inventory releases stock
```

## Prerequisites
* JDK 17+ (21 is fine), Maven 3.9+, Docker with Compose

## Run it

```bash
# 1. Infrastructure: Kafka (localhost:9092) + PostgreSQL (localhost:5432, user/password: saga/saga)
docker compose up -d

# 2. Build all three services
mvn clean package -DskipTests

# 3. Start each service in its own terminal
java -jar order-service/target/order-service-1.0.0.jar
java -jar payment-service/target/payment-service-1.0.0.jar
java -jar inventory-service/target/inventory-service-1.0.0.jar
#   (or: mvn -pl order-service spring-boot:run, etc.)
```

Flyway creates the schema and seed data on first start (user 1 has 1000, user 2 has 50, product 100 has 10 units).

## Try the scenarios

```bash
./scripts/run-scenarios.sh
```

or by hand:

```bash
# Happy path -> COMPLETED
curl -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":100,"quantity":2,"amount":200}'

# Payment fails (user 2 has 50) -> CANCELLED
curl -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"userId":2,"productId":100,"quantity":1,"amount":200}'

# Out of stock -> payment taken, then refunded (compensation), order CANCELLED
curl -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":100,"quantity":50,"amount":100}'
```

Inspect state:

| What | URL |
|------|-----|
| Order | `GET :8081/orders/{id}`, `GET :8081/orders` |
| Payment / wallet | `GET :8082/payments/{orderId}`, `GET :8082/wallets/{userId}` |
| Reservation / stock | `GET :8083/reservations/{orderId}`, `GET :8083/stock/{productId}` |

Or query PostgreSQL directly:

```bash
docker compose exec postgres psql -U saga -d paymentdb -c "select * from payment;"
docker compose exec postgres psql -U saga -d orderdb   -c "select topic,status,attempts from outbox order by id desc limit 10;"
```

## Swagger UI (OpenAPI)

Powered by `springdoc-openapi-starter-webmvc-ui` 2.6.0 (version set by `springdoc.version` in the parent pom).

| What | URL |
|------|-----|
| **Combined Swagger UI** (dropdown: order / payment / inventory) | http://localhost:8081/swagger-ui.html |
| Order Service UI + spec | http://localhost:8081/swagger-ui.html, http://localhost:8081/v3/api-docs |
| Payment Service UI + spec | http://localhost:8082/swagger-ui.html, http://localhost:8082/v3/api-docs |
| Inventory Service UI + spec | http://localhost:8083/swagger-ui.html, http://localhost:8083/v3/api-docs |

* `POST /orders` has a dropdown with the three scenarios (happy path, payment fails, out of stock). Pick one, press **Execute**, copy the returned `id`, then use `GET /orders/{id}`.
* In the combined UI, switch the "Select a definition" dropdown to `payment-service` or `inventory-service` to inspect wallets, payments, stock and reservations. Those services allow CORS from `http://localhost:8081` for this.
* Each service also works on its own port if you do not start the others.

## Failure drills

**A. Crash before the compensating transaction runs**
```bash
# stop payment-service, then restart it with a delay inside the compensation handler:
java -jar payment-service/target/payment-service-1.0.0.jar --saga.compensation-delay-ms=20000
# create the out-of-stock order (scenario 3); when Payment logs "DEMO: sleeping" run:
kill -9 <payment-service-pid>
# restart payment-service normally: Kafka redelivers inventory-failed, the payment becomes
# REFUND_PENDING, RefundJob refunds it, wallet restored.
```

**B. Refund keeps failing (retry with backoff, then escalate)**
```bash
java -jar payment-service/target/payment-service-1.0.0.jar --saga.simulate-refund-failures=3
# scenario 3: watch refund_attempts increase, next_retry_at back off (10s, 20s, 40s), 4th attempt succeeds.
# Use --saga.simulate-refund-failures=99 to see REFUND_FAILED and the ALERT log line.
```

**C. A service is down, so the saga times out**
```bash
# stop payment-service, create a normal order. It stays CREATED.
# After 120s SagaTimeoutJob sets it CANCELLED and publishes order-cancelled.
# Start payment-service again: whichever event it reads first, the end state is consistent
# (never charged, or charged and then refunded; stock never stays reserved).
# Shorten the wait with: java -jar order-service/target/order-service-1.0.0.jar --saga.order-timeout-seconds=30
```

**D. Kafka down while creating an order**
Stop the Kafka container, create an order (HTTP 201, the event sits in the `outbox` table as PENDING),
start Kafka again, and the OutboxRelay publishes it automatically.

## Reset everything

```bash
docker compose down -v && docker compose up -d
```

## Notes
* `ddl-auto: validate`: Flyway owns the schema. Never edit an applied migration; add a new `V4__...` file.
* Seed data lives in `V3__seed_*.sql` for demo convenience only.
* All consumers are idempotent (at-least-once delivery from Kafka and from the outbox relay).
* Messages that still fail after 5 retries (1s,2s,4s,8s,16s) go to `<topic>.DLT`.
* The three `OrderEvent` records and the `outbox` package are intentionally copied per service
  (no shared library) to keep each service independently deployable.
* Production hardening ideas: Debezium instead of the polling relay, Avro/Protobuf + schema registry,
  Micrometer metrics and alerts on DLT depth / `REFUND_FAILED`, authentication, an orchestrator
  (Temporal/Camunda) once flows get more complex.
