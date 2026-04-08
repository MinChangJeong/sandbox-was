# Sandbox WMS (Warehouse Management System)

Spring Boot + Kotlin 기반 창고 관리 시스템. Hexagonal Architecture, DDD, CQRS 패턴을 적용한 학습/샌드박스 프로젝트.

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 1.9.22 |
| Framework | Spring Boot 3.2.2 |
| JDK | Java 21 |
| Build | Gradle 8.x (Kotlin DSL) |
| ORM | Spring Data JPA + Hibernate |
| Database | H2 (In-Memory) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Auth | JWT (jjwt 0.12.3) |
| Logging | Logback + Logstash Encoder |
| Testing | JUnit 5, MockK, Mockito-Kotlin |

## Architecture

```
presentation/       REST DTOs, error handling
     |
application/        CQRS handlers (Command / Query)
     |
domain/             Entities, Value Objects, Events, Repository Ports
     |
infrastructure/     JPA adapters, REST controllers, config
```

### Domain Modules

| Module | Description |
|--------|-------------|
| `warehouse` | Warehouse master data |
| `zone` | Zone master data |
| `location` | Location master data |
| `item` | Item master data |
| `inventory` | Inventory management (adjustment, movement, history) |
| `inbound` | Inbound order management (inspection, putaway) |
| `outbound` | Outbound order management (allocation, picking, packing, shipping) |

## Getting Started

### Prerequisites

- JDK 21+
- Gradle 8.x (wrapper included)

### Run

```bash
./gradlew bootRun
```

Application starts at **http://localhost:8080**

### Build

```bash
./gradlew clean build
```

### Test

```bash
./gradlew test
```

## API Endpoints

### Swagger UI

http://localhost:8080/swagger-ui.html

### H2 Console

http://localhost:8080/h2-console

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:testdb` |
| Username | `sa` |
| Password | *(empty)* |

### REST API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| **Inventory** | | |
| `POST` | `/api/v1/inventory/commands/adjustments` | Inventory adjustment |
| `POST` | `/api/v1/inventory/commands/movements` | Inventory movement |
| `GET` | `/api/v1/inventory/queries` | Query inventory list |
| **Inbound** | | |
| `POST` | `/api/v1/inbound-orders/commands` | Create inbound order |
| `POST` | `/api/v1/inbound-orders/commands/{id}/inspection/start` | Start inspection |
| `PUT` | `/api/v1/inbound-orders/commands/{id}/items/{itemId}/inspection` | Inspect item |
| `POST` | `/api/v1/inbound-orders/commands/{id}/inspection/complete` | Complete inspection |
| `POST` | `/api/v1/inbound-orders/commands/{id}/putaway` | Putaway |
| `POST` | `/api/v1/inbound-orders/commands/{id}/complete` | Complete inbound |
| `POST` | `/api/v1/inbound-orders/commands/{id}/reject` | Reject inbound |
| `GET` | `/api/v1/inbound-orders/queries/{id}` | Get inbound order |
| `GET` | `/api/v1/inbound-orders/queries` | Query inbound orders |
| **Outbound** | | |
| `POST` | `/api/v1/outbound-orders/commands` | Create outbound order |
| `POST` | `/api/v1/outbound-orders/commands/{id}/allocate` | Allocate inventory |
| `POST` | `/api/v1/outbound-orders/commands/{id}/deallocate` | Deallocate inventory |
| `POST` | `/api/v1/outbound-orders/commands/{id}/start-picking` | Start picking |
| `POST` | `/api/v1/outbound-orders/commands/{id}/complete-picking` | Complete picking |
| `POST` | `/api/v1/outbound-orders/commands/{id}/pack` | Pack |
| `POST` | `/api/v1/outbound-orders/commands/{id}/ship` | Ship |
| `POST` | `/api/v1/outbound-orders/commands/{id}/cancel` | Cancel outbound |
| `GET` | `/api/v1/outbound-orders/queries/{id}` | Get outbound order |
| `GET` | `/api/v1/outbound-orders/queries` | Query outbound orders |

## Project Structure

```
src/main/kotlin/com/wms/
├── WmsApplication.kt
├── domain/
│   ├── common/              # Shared: AggregateRoot, events, exceptions
│   ├── warehouse/           # Warehouse aggregate
│   ├── zone/                # Zone aggregate
│   ├── location/            # Location aggregate
│   ├── item/                # Item aggregate
│   ├── inventory/           # Inventory aggregate + history
│   ├── inbound/             # Inbound order aggregate
│   └── outbound/            # Outbound order aggregate
├── application/
│   ├── inventory/           # Inventory command/query handlers
│   ├── inbound/             # Inbound command/query handlers
│   ├── outbound/            # Outbound command/query handlers
│   └── ...                  # Master data handlers
├── infrastructure/
│   ├── persistence/         # JPA repositories, mappers
│   ├── web/                 # REST controllers
│   └── config/              # Spring configuration
└── presentation/            # REST API layer
```

## Design Principles

- **Tell, Don't Ask** - Domain entities manage their own state via domain methods
- **Immutability** - No `data class` for domain entities; private `var` + public `val` getters
- **CQRS** - Command and Query handlers are separated
- **Pessimistic Locking** - All inventory mutations use `PESSIMISTIC_WRITE` lock
- **Domain Events** - State changes emit events for cross-aggregate coordination
