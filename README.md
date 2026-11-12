# BearWithMe

[![CI](https://github.com/nvlinh233/bearwithme/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nvlinh233/bearwithme/actions/workflows/ci.yml)

Flashcards with spaced repetition (SM-2) for IT workplace English.

## Repository layout

| Path      | Content                                                |
|-----------|--------------------------------------------------------|
| `api/`    | Spring Boot 4.1 backend (Java 25, Maven multi-module)  |
| `web/`    | Vue 3 frontend (Vite, TypeScript, PrimeVue)            |
| `deploy/` | Docker Compose and shared local settings (`.env`)      |
| `docs/`   | Specs, change records and architecture decisions       |

## Prerequisites

Java 25, Node 24 LTS, Docker.

## Getting started

```bash
# Build and run all backend tests (Testcontainers starts PostgreSQL)
cd api
./mvnw verify          # Windows: .\mvnw.cmd verify

# Run the API; PostgreSQL from deploy/compose.yaml starts automatically
./mvnw -pl app spring-boot:run
```

Local infrastructure settings (PostgreSQL image, port, credentials) live in
[`deploy/.env`](deploy/.env). See [ADR-0001](docs/adr/0001-configuration-management.md).

## Documentation

- [v1 product specification](docs/specs/v1-product-spec.md) ([rules for specs](docs/specs/README.md))
- [Change records](docs/changes/)
- [Architecture decision records](docs/adr/)
- [Contributing guide](CONTRIBUTING.md)

## License

[MIT](LICENSE)
