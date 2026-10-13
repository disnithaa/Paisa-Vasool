# Paisa Vasool

Paisa Vasool is a group spending and savings application. Its initial product journey will help groups plan purchases, compare curated offers, record simulated expenses, and understand settlements. It does not custody money or execute payments.

## Week 1: Foundation

Week 1 provides a React + TypeScript + Vite frontend, a Java 21 / Spring Boot REST API, PostgreSQL in Docker Compose, Flyway migrations, and a database-backed health check.

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Node.js 20+ and npm 10+
- Docker Desktop (Windows/macOS) or Docker Engine with the Compose plugin (Linux)
- Git

Check the installed tools:

```bash
java -version
mvn -version
node --version
npm --version
docker --version
docker compose version
```

## Repository layout

```text
paisa-vasool/
├── backend/       Spring Boot API and Flyway migrations
├── frontend/      React + TypeScript + Vite application
├── infra/         Local PostgreSQL Compose configuration
├── docs/          Architecture and project documentation
├── optimizer/     Reserved for the later Python optimization service
└── README.md
```

## Start PostgreSQL

From the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
docker compose -f infra/docker-compose.yml ps
```

The local database is `paisa_vasool`, with username `paisa` and password `paisa123`. These are development-only defaults; set `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` before starting Compose if you need different local credentials. Do not use these defaults for a deployed environment.

Stop the database and keep its data:

```bash
docker compose -f infra/docker-compose.yml down
```

To also remove the local database volume and all its data:

```bash
docker compose -f infra/docker-compose.yml down -v
```

## Run the backend

In a terminal from the repository root:

```bash
cd backend
mvn spring-boot:run
```

The first startup applies the Flyway migration and checks PostgreSQL connectivity. The API listens on `http://localhost:8080`.

To use another database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the backend process environment. Defaults match the Compose configuration.

Health endpoints:

- `GET http://localhost:8080/api/health` checks the API and runs `SELECT 1` against PostgreSQL. It returns HTTP 200 when both are available and HTTP 503 when the database check fails.
- `GET http://localhost:8080/actuator/health` is Spring Boot's health endpoint.

## Run the frontend

In a second terminal from the repository root:

```bash
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite (normally `http://localhost:5173`). The frontend requests `/api/health`; Vite proxies that request to the backend at `http://localhost:8080`, avoiding local CORS setup for development.

Create a production frontend build with:

```bash
npm run build
```

## Verify the Week 1 journey

1. Start PostgreSQL using the Compose command above.
2. Start the backend and wait for Spring Boot to report that it has started.
3. Open `http://localhost:8080/api/health`. Confirm the response reports `UP` for both the service and database.
4. Start the frontend and open its Vite URL. Confirm the service card reports `API up · PostgreSQL up`.
5. If the database is stopped, the API health endpoint returns HTTP 503; the frontend shows that the database check failed.

## Backend dependencies

- Spring Web serves the REST API.
- Spring Data JPA and the PostgreSQL JDBC driver establish the persistence foundation.
- Spring Security is configured with a public health endpoint and permissive access during this setup milestone; authorization must be added with the domain APIs.
- Bean Validation is available for request validation in later milestones.
- Flyway Core and its PostgreSQL support apply versioned database migrations.
- Spring Boot Actuator exposes application health.
- H2 is used only for lightweight backend tests; PostgreSQL remains the development database.

## Current scope

This milestone establishes the service and database connection, not the group, offers, approvals, ledger, or optimizer features. The next milestone should begin only after Week 1 is running locally and the user confirms proceeding.
