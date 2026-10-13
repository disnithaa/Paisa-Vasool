# Week 1 foundation architecture

```mermaid
flowchart LR
    Browser[React + TypeScript frontend\nVite on :5173] -->|/api requests| Proxy[Vite development proxy]
    Proxy -->|HTTP :8080| Security[Spring Security\nlocal Basic identity]
    Security --> API[REST controllers and DTOs]
    API --> Services[Group and purchase services\ntransactions and business rules]
    Services --> Repositories[Spring Data JPA repositories]
    Repositories --> DB[(PostgreSQL :5432\napp schema)]
    DB -. schema changes .-> Flyway[Flyway migrations]
```

The Java backend owns application data and database access. The browser calls the API over REST; it does not connect to PostgreSQL. Flyway applies versioned schema changes when the backend starts, and Hibernate validates the mapped schema without changing it.

## Week 2 domain relationships

```mermaid
erDiagram
    APP_USER ||--o{ GROUP_MEMBERSHIP : participates
    EXPENSE_GROUP ||--o{ GROUP_MEMBERSHIP : contains
    EXPENSE_GROUP ||--o{ PURCHASE : plans
    APP_USER ||--o{ PURCHASE : creates
    GROUP_MEMBERSHIP {
        uuid id PK
        uuid user_id FK
        uuid group_id FK
        string role
        string status
        timestamp joined_at
    }
    PURCHASE {
        uuid id PK
        uuid group_id FK
        uuid created_by FK
        numeric estimated_amount
        string status
    }
```

`GROUP_MEMBERSHIP` is an entity because role and active/left status are domain state. The unique `(group_id, user_id)` constraint prevents duplicate membership records. A purchase separately references its group and creator; active group membership is checked by the application service before purchase creation. The local HTTP Basic principal is mapped by email to an application user and is explicitly not production authentication.
