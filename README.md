# Lulumi — E-Commerce Storefront & REST API

A full-stack e-commerce application built with **Spring Boot 3.4** — a server-rendered Thymeleaf
storefront for customers and admins, backed by a JWT-protected REST API, with Paymob
(Egyptian gateway) card payments and HMAC-verified webhooks.

> Demo catalog is seeded on first boot: the "Lulumi" baby & nursery store.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Running Tests](#running-tests)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Security Model](#security-model)
- [Payments (Paymob)](#payments-paymob)
- [Notes](#notes)

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 |
| Build | Maven (wrapper included — `./mvnw` / `mvnw.cmd`) |
| Web (storefront) | Spring MVC + Thymeleaf + Thymeleaf Security Extras |
| Web (API) | Spring Web MVC REST controllers |
| Security | Spring Security 6, JWT (jjwt 0.11.5, HS512), session form login, BCrypt |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL 8 (`mysql-connector-j`) |
| Mapping | MapStruct 1.6.3 |
| Boilerplate | Lombok 1.18.30 |
| Image hosting | Cloudinary (`cloudinary-http44`) |
| API docs | springdoc-openapi 2.8.7 (Swagger UI) |
| Payments | Paymob Intention API + Mock gateway for local dev |
| Tests | JUnit 5, Spring Security Test, Mockito |

---

## Features

### Storefront (Thymeleaf)
- Home page with featured products
- Product catalog with search (`?q=`), category filter, sorting (price/name, asc/desc) and pagination (12/page)
- Product detail page with related items
- Shopping cart (add / update quantity / remove / clear)
- Checkout + order placement
- Customer order history, order details, and cancellation
- Billing email collection at checkout (required by Paymob)
- Registration, login, logout and account pages
- Customer return page after payment redirect

### Admin area (`/admin/**`, `admin` authority)
- Dashboard with sales and order stats
- Product CRUD with Cloudinary photo upload (replaces main image on edit)
- Order listing, order details, and status transitions
- Products are **soft deleted**, never hard removed

### REST API
- Auth: login, logout, refresh token, register user, register admin
- Products, categories, images, cart, orders, payments
- Swagger UI with JWT bearer authorization

### Payments
- Gateway abstraction (`PaymentGateway` + `PaymentGatewayRegistry`) — add a provider by implementing one interface
- Paymob: intention creation, hosted checkout redirect, transaction verification
- Webhook processing with **HMAC-SHA512 signature verification**, idempotent event recording, and quarantine of invalid events
- Development-only `MOCK` gateway that accepts unsigned webhooks

---

## Prerequisites

- **JDK 17+**
- **Maven 3.8+** (or just use the bundled wrapper — no install needed)
- **MySQL 8** running locally
- Optional: a [Paymob](https://accept.paymob.com) account for real payments
- Optional: a [Cloudinary](https://cloudinary.com) account for image uploads

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/amerMuhamed/Ecommerce-app.git
cd Ecommerce-app
```

### 2. Create the database

The JDBC URL below creates the schema automatically, but you can do it manually:

```sql
CREATE DATABASE IF NOT EXISTS ecommerce;
```

### 3. Create your configuration file

`src/main/resources/application.properties` is **gitignored** (it holds secrets), so create it yourself:

```properties
# --- Server ---
server.port=8888

# --- Database ---
spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce?createDatabaseIfNotExist=true
spring.datasource.username=YOUR_DB_USER
spring.datasource.password=YOUR_DB_PASSWORD
spring.jpa.hibernate.ddl-auto=update

# --- JWT (secret must be Base64 encoded) ---
# Generate one:  openssl rand -base64 64
auth.secret=YOUR_BASE64_ENCODED_SECRET
auth.access.expiration=18000000
auth.refresh.expiration=18000000

# --- Cloudinary (optional, for admin image uploads) ---
cloudinary.cloud-name=YOUR_CLOUD_NAME
cloudinary.api-key=YOUR_API_KEY
cloudinary.api-secret=YOUR_API_SECRET

# --- Paymob (optional, for real card payments) ---
payment.paymob.secret-key=YOUR_SECRET_KEY
payment.paymob.public-key=YOUR_PUBLIC_KEY
payment.paymob.hmac-secret=YOUR_HMAC_SECRET
payment.paymob.integration-ids=YOUR_INTEGRATION_ID

# Public URL Paymob can reach (use ngrok or similar for local dev)
APP_PUBLIC_BASE_URL=https://your-public-tunnel.example.com

# --- Local development ---
lulumi.seed.enabled=true
lulumi.admin.username=admin
lulumi.admin.password=choose-a-strong-password
lulumi.admin.full-name=Lulumi Admin
```

### 4. Run

**Linux / macOS**

```bash
./mvnw spring-boot:run
```

**Windows**

```powershell
mvnw.cmd spring-boot:run
```

Or build and run the jar:

```bash
./mvnw clean package
java -jar target/e-commerce-0.0.1-SNAPSHOT.jar
```

### 5. Open

| URL | Description |
| --- | --- |
| http://localhost:8888/ | Storefront |
| http://localhost:8888/login | Sign in |
| http://localhost:8888/register | Create a customer account |
| http://localhost:8888/admin | Admin dashboard (requires an `admin` account) |
| http://localhost:8888/swagger-ui/index.html | Swagger UI |

The database is created and the demo catalog plus your admin user are seeded automatically on first boot.

---

## Configuration

All settings live in `src/main/resources/application.properties` (gitignored). Anything that looks like
`${ENV_VAR:default}` can be supplied as an environment variable instead — recommended for deployment.

| Property | Env var | Default | Purpose |
| --- | --- | --- | --- |
| `server.port` | — | `8888` | HTTP port |
| `spring.datasource.url` | — | `jdbc:mysql://localhost:3306/ecommerce` | MySQL connection |
| `spring.datasource.username` / `.password` | — | — | DB credentials |
| `spring.jpa.hibernate.ddl-auto` | — | `update` | Schema management |
| `auth.secret` | — | — | **Base64**-encoded HS512 signing key (required) |
| `auth.access.expiration` | — | `18000000` | Access token TTL in ms (5 h) |
| `auth.refresh.expiration` | — | `18000000` | Refresh token TTL in ms (5 h) |
| `cloudinary.cloud-name` / `.api-key` / `.api-secret` | — | — | Image uploads |
| `payment.paymob.secret-key` | `PAYMOB_SECRET_KEY` | — | Paymob API auth |
| `payment.paymob.public-key` | `PAYMOB_PUBLIC_KEY` | — | Paymob checkout |
| `payment.paymob.hmac-secret` | `PAYMOB_HMAC_SECRET` | — | Webhook signature verification |
| `payment.paymob.integration-ids` | `PAYMOB_INTEGRATION_IDS` | — | Comma-separated integration ids |
| `payment.paymob.notification-url` | `PAYMOB_NOTIFICATION_URL` | `${APP_PUBLIC_BASE_URL}/api/webhooks/payments/paymob` | Webhook URL registered with Paymob |
| `payment.paymob.redirection-url` | `PAYMOB_REDIRECTION_URL` | `${APP_PUBLIC_BASE_URL}/payments/return/paymob` | Where the customer is sent back |
| `payment.paymob.base-url` | `PAYMOB_BASE_URL` | `https://accept.paymob.com` | Paymob API host |
| `payment.paymob.checkout-url` | `PAYMOB_CHECKOUT_URL` | `https://eg.checkout.paymob.com/` | Hosted checkout host |
| `payment.mock.enabled` | `PAYMENT_MOCK_ENABLED` | `false` | Registers the dev-only MOCK gateway |
| `lulumi.seed.enabled` | `LULUMI_SEED_ENABLED` | `true` | Seed demo categories/products (idempotent) |
| `lulumi.admin.username` | `LULUMI_ADMIN_USERNAME` | *(empty)* | Bootstrap admin username |
| `lulumi.admin.password` | `LULUMI_ADMIN_PASSWORD` | *(empty)* | Bootstrap admin password |
| `lulumi.admin.full-name` | `LULUMI_ADMIN_FULL_NAME` | `Lulumi Admin` | Bootstrap admin display name |
| `spring.servlet.multipart.max-file-size` | — | `10MB` | Max product photo upload size |

> `lulumi.admin.*` is safe to leave set in production — the bootstrap runner is a no-op if the user
> already exists, and it never logs credentials.

---

## Running Tests

```bash
./mvnw test          # Linux / macOS
mvnw.cmd test        # Windows
```

```bash
./mvnw test -Dtest=PaymobHmacVerifierTest        # single test class
./mvnw clean verify                              # tests + package
```

The suite covers payment webhooks and HMAC verification, the Paymob client and gateway, order stock
concurrency, product service logic, the seed runner, admin order handling, image replacement, the
payment return controller, and storefront security rules.

---

## API Documentation

With the app running, open **http://localhost:8888/swagger-ui/index.html**.

Authorise in Swagger with the access token from `POST /api/auth/login`:

```bash
curl -X POST http://localhost:8888/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"your-password"}'
```

```json
{
  "success": true,
  "message": "Login successful",
  "data": { "accessToken": "eyJ...", "refreshToken": "eyJ..." }
}
```

Then pass `Authorization: Bearer <accessToken>`.

### Endpoint overview

All API responses use a common envelope:

```json
{ "success": true, "message": "...", "data": { } }
```

**Auth** — `/api/auth`

| Method | Path | Access |
| --- | --- | --- |
| POST | `/login` | Public |
| POST | `/registerUser` | Public |
| POST | `/logout` | Authenticated |
| POST | `/refresh` | Public (refresh token) |
| POST | `/registerAdmin` | `admin` |

**Products** — `/api/products`

| Method | Path | Access |
| --- | --- | --- |
| GET | `` | Public |
| GET | `/{id}` | Public |
| GET | `/name?name=...` | Public |
| POST | `` | `admin` |
| PUT | `/{id}` | `admin` |
| DELETE | `/{id}` | `admin` |
| DELETE | `?name=...` | `admin` |

**Categories** — `/api/categories`

| Method | Path | Access |
| --- | --- | --- |
| GET | ``, `/{id}` | Public |
| POST | ``, `PATCH /{id}`, `PATCH /{categoryId}/addProducts`, `DELETE /{id}`, `DELETE` | `admin` |

**Cart** — `/api/cart` (authenticated)

| Method | Path |
| --- | --- |
| POST | `/item` |
| GET | `` |
| PATCH | `/item/{productId}` |
| DELETE | ``, `/item/{productId}` |

**Orders** — `/api/orders` (authenticated)

| Method | Path |
| --- | --- |
| POST | `` |
| GET | ``, `/{id}` |
| PATCH | `/{id}/cancel` |

**Payments** — `/api/payments` (authenticated, except where noted)

| Method | Path | Access |
| --- | --- | --- |
| POST | `/orders/{orderId}/initiate` | Authenticated |
| GET | `/orders/{orderId}`, `/my`, `/{id}` | Authenticated |
| GET | `/return/{provider}` | Public — HMAC-verified customer redirect |
| POST | `/api/webhooks/payments/{provider}` | Public — HMAC-verified provider callback |

**Users** — `/api/user` (authenticated)

| Method | Path |
| --- | --- |
| GET | ``, `/{id}` |
| PUT | `/update` |
| POST | `/Upload-profile-image` |
| DELETE | `/delete-profile-image` |

**Roles** — `/api/role` — `GET /`, `GET /{id}`

**Storefront pages** — `/`, `/shop`, `/product/{id}`, `/cart`, `/checkout`, `/orders`, `/orders/{id}`,
`/account`, `/login`, `/register`

---

## Project Structure

```
src/main/java/com/spring/eCommerce
├── config/            # Cloudinary, Paymob, OpenAPI, JPA auditing, seed & admin bootstrap runners
├── Controller/        # REST controllers
├── dto/               # Request/response records grouped by domain
├── entity/            # JPA entities + enums (OrderStatus, PaymentStatus, ...)
├── exception/         # BusinessException, TokenException, global handler, entry points
├── Mapper/            # MapStruct mappers
├── repository/        # Spring Data JPA repositories
├── security/          # SecurityConfig, AuthFilter, JwtTokenUtils, password encoder
├── service/           # Domain services
│   ├── authentication/ cart/ category/ image/ order/ product/ role/ token/ user/
│   └── payment/       # Gateway abstraction + Paymob implementation
└── web/               # Thymeleaf storefront: controllers, forms, StorefrontService

src/main/resources
├── application.properties   # gitignored — create it locally
├── static/                  # css, images
└── templates/               # Thymeleaf views (storefront + admin)

src/test/java/...            # JUnit 5 + Spring Security Test
```

Architecture: **Controller → Service → Repository**, with MapStruct handling entity ⇄ DTO mapping.
API authorization is enforced server-side in `SecurityConfig`, not in the UI.

---

## Security Model

- **Roles**: `user` (self-registration) and `admin` (bootstrap or `/api/auth/registerAdmin`).
- **Two authentication paths**
  - REST clients: stateless JWT bearer tokens (`Authorization: Bearer …`), access + refresh token pairs
    tracked in the database so they can be revoked on logout.
  - Browsers: session-based form login on `/login`, with CSRF protection enabled for `/api/**`-excluded
    form posts.
- **Token hygiene**: refresh tokens rotate on every refresh; expired tokens are cleaned up by a scheduled job.
- **Webhooks and payment redirects are public** — they are authenticated by the provider's HMAC signature
  rather than by JWT. Paymob callbacks are verified with HMAC-SHA512 in constant time.
- **Passwords** are hashed with BCrypt; plaintext is never logged.
- Validation failures are returned as `400` with the collected field errors via `@RestControllerAdvice`.

---

## Payments (Paymob)

1. Customer checks out and the app creates an `Order` plus a `Payment` record.
2. The app requests a Paymob **intention** and redirects the browser to hosted checkout.
3. Paymob calls back the **transaction processed webhook** (`POST /api/webhooks/payments/paymob`);
   the HMAC signature is verified, the event is recorded (idempotent), and the payment/order status is updated.
4. The customer returns to `/payments/return/paymob`, where the redirect HMAC is verified before
   showing the result.

Order statuses: `PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED`, or `CANCELLED`.

### Local testing without Paymob

```properties
payment.mock.enabled=true
```

The `MOCK` gateway issues a fake checkout URL and accepts unsigned webhooks. **Development only — never
enable it in production.**

For real webhook testing, expose your machine with a tunnel and set `APP_PUBLIC_BASE_URL` to the public URL.

### Adding another provider

Implement `PaymentGateway` (`getProvider`, `initiate`, `verify`, `parseWebhook`, `verifyWebhookSignature`).
Spring auto-registers it and `PaymentGatewayRegistry` resolves it by `PaymentProvider`.

---

## Notes

- Product deletion is a **soft delete**, so existing order history keeps its references.
- Stock is decremented with atomic conditional updates to stay correct under concurrent checkouts.
- `lulumi.seed.enabled` only inserts records that are missing (matched by name), so restarts never duplicate data.
- `spring.jpa.hibernate.ddl-auto=update` is convenient for development; use a migration tool (Flyway/Liquibase)
  for production schema management.
- The committed `.gitignore` excludes `application.properties` — never commit secrets.

## License

Distributed under the terms of the project license.

## Acknowledgements

Built with Spring Boot, Thymeleaf, MapStruct, Lombok, Cloudinary and Paymob.
