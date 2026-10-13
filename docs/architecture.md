# Week 1 foundation architecture

```mermaid
flowchart LR
    Browser[React + TypeScript frontend\nVite on :5173] -->|GET /api/health| Proxy[Vite development proxy]
    Proxy -->|HTTP :8080| API[Spring Boot REST API]
    API -->|JDBC SELECT 1\nFlyway migrations| DB[(PostgreSQL :5432)]
```

The Java backend owns application data and database access. The browser calls the API over REST; it does not connect to PostgreSQL. Flyway applies versioned schema changes when the backend starts. In local development, Vite proxies `/api` requests to the backend.
