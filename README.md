# SupportPlane

ODPSC cluster management platform. Provides tenant-isolated cluster monitoring, bundle collection, ticketing, and operator workflows.

## Architecture

```
                         ┌──────────────┐
                         │   Frontend   │
                         │  React 18    │
                         │  Vite + TS   │
                         └──────┬───────┘
                                │ /api proxy
                         ┌──────┴───────┐
                         │   Backend    │
                         │ Spring Boot  │
                         │   3.2 / JWT  │
                         └──┬───────┬───┘
                            │       │
                  ┌─────────┴──┐ ┌──┴──────────┐
                  │ PostgreSQL │ │  Keycloak    │
                  │    15      │ │  24.0        │
                  │  (Flyway)  │ │ clients +    │
                  │            │ │ support      │
                  └────────────┘ └──────────────┘
```

- **Backend**: Java 17 / Spring Boot 3.2 / JPA / OAuth2 Resource Server
- **Frontend**: React 18 / Vite / TypeScript / Tailwind / Zustand
- **Auth**: Keycloak with two realms (`clients` for tenants, `support` for operators)
- **Database**: PostgreSQL 15 with Flyway migrations
- **Multi-tenancy**: JWT-based tenant isolation via TenantFilter

## Prerequisites

- Docker & Docker Compose
- Java 17 (for local backend development)
- Node.js 20+ (for local frontend development)
- Helm 3 (for Kubernetes deployment)

## Quick Start

```bash
docker-compose up --build
```

Services will be available at:

| Service   | URL                          |
|-----------|------------------------------|
| Frontend  | http://localhost:3000         |
| Backend   | http://localhost:8081         |
| Keycloak  | http://localhost:8080         |
| Swagger   | http://localhost:8081/swagger-ui.html |

## Running Tests

### Backend

```bash
cd backend
mvn test
```

53 tests: unit tests for services, `@WebMvcTest` for all controllers, integration tests with H2.

### Frontend

```bash
cd frontend
npm install
npm run test:run
```

16 tests: Zustand store, routing, Login page, Dashboard page.

### E2E

```bash
docker-compose -f docker-compose.test.yml up --build --abort-on-container-exit
```

Runs a curl-based test suite covering: health checks, registration, login, dashboard, cluster attachment, OTP validation, tickets, bundle upload, and OpenAPI docs.

## Helm Deployment

```bash
helm install supportplane ./helm/supportplane \
  --set secrets.postgres.password=<DB_PASSWORD> \
  --set secrets.keycloak.adminPassword=<KC_PASSWORD> \
  --set backend.image.repository=<REGISTRY>/supportplane-backend \
  --set frontend.image.repository=<REGISTRY>/supportplane-frontend \
  --set ingress.enabled=true \
  --set ingress.hosts[0].host=supportplane.example.com
```

To use an external PostgreSQL:

```bash
helm install supportplane ./helm/supportplane \
  --set postgres.enabled=false \
  --set secrets.postgres.password=<EXTERNAL_DB_PASSWORD>
```

## Default Credentials

| Service      | Username          | Password     | Notes                     |
|-------------|-------------------|-------------|---------------------------|
| Keycloak    | admin             | admin        | Admin console              |
| PostgreSQL  | supportplane      | supportplane | Database access            |
| Operator    | (create in Keycloak support realm) | | Use tenantId `support` to login |

## API Endpoints

| Method | Path                              | Auth     | Description               |
|--------|-----------------------------------|----------|---------------------------|
| POST   | `/api/v1/auth/register`           | Public   | Register new tenant       |
| POST   | `/api/v1/auth/login`              | Public   | Login, get JWT tokens     |
| GET    | `/api/v1/dashboard`               | JWT      | Dashboard metrics         |
| GET    | `/api/v1/clusters`                | JWT      | List clusters             |
| POST   | `/api/v1/clusters/attach`         | JWT      | Attach cluster, get OTP   |
| POST   | `/api/v1/clusters/validate-otp`   | Public   | Validate OTP from agent   |
| DELETE | `/api/v1/clusters/{id}`           | JWT      | Detach cluster            |
| GET    | `/api/v1/tickets`                 | JWT      | List tickets              |
| POST   | `/api/v1/tickets`                 | JWT      | Create ticket             |
| PUT    | `/api/v1/tickets/{id}/status`     | JWT      | Update ticket status      |
| POST   | `/api/v1/bundles/upload`          | Public   | Upload bundle (multipart) |
| GET    | `/api/v1/bundles/{bundleId}`      | JWT      | Get bundle details        |
| GET    | `/api/v1/users`                   | JWT      | List users                |
| GET    | `/api/v1/tenants`                 | JWT      | List tenants (operator)   |
| GET    | `/api/v1/licenses`                | JWT      | List licenses (operator)  |
| GET    | `/api-docs`                       | Public   | OpenAPI JSON              |

## Environment Variables

### Backend

| Variable                    | Default                        | Description                |
|-----------------------------|--------------------------------|----------------------------|
| `SPRING_DATASOURCE_URL`    | `jdbc:postgresql://localhost:5432/supportplane` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `supportplane`              | Database username          |
| `SPRING_DATASOURCE_PASSWORD` | `supportplane`              | Database password          |
| `KEYCLOAK_AUTH_SERVER_URL`  | `http://localhost:8080`       | Keycloak base URL          |
| `KEYCLOAK_CLIENTS_REALM`   | `clients`                     | Client tenant realm        |
| `KEYCLOAK_SUPPORT_REALM`   | `support`                     | Operator realm             |

### Frontend

| Variable            | Default   | Description            |
|---------------------|-----------|------------------------|
| `VITE_API_BASE_URL` | `/api/v1` | Backend API base URL   |
