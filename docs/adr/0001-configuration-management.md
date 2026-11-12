# ADR-0001: Configuration management

Date: 2026-11-12 · Status: Accepted

## Context

The monorepo contains several tools that each read configuration in their own format:
Maven (`pom.xml`), npm (`package.json`), Docker Compose (`.env`, `compose.yaml`),
Spring Boot (`application.yaml`) and GitHub Actions (workflow YAML).

Without a rule, the same value (for example the PostgreSQL image version) ends up
duplicated in several files and drifts. Secrets risk being committed next to ordinary settings.

## Decision

1. **Single source of truth.** Every value is defined in exactly one place.
   Other consumers read it from there; they never copy it.
2. **Group configuration by purpose, not into one file.**

   | Category | Examples | Location | Committed |
   | --- | --- | --- | --- |
   | Toolchain | Java 25, Node 24 | `.java-version`, `.nvmrc`; CI reads these files | Yes |
   | Library versions | Spring Boot, PrimeVue | `api/pom.xml` properties, `web/package.json` + lockfile | Yes |
   | Local infrastructure | PostgreSQL image, port, database name | `deploy/.env` | Yes (no real secrets) |
   | Application runtime | Datasource, JWT issuer | Environment variables (12-factor); `application.yaml` only maps them | Mapping only |
   | Real secrets | Production passwords, JWT signing key | GitHub Secrets / secret manager | Never |

3. **`deploy/.env` is shared by all local consumers:**
   - Docker Compose reads it directly.
   - Spring Boot (dev) uses `spring-boot-docker-compose`, which starts Compose and
     derives connection details; `application.yaml` contains no datasource URL.
   - Tests read it through the `deploy.env.file` system property set by Surefire,
     so Testcontainers uses the same image as Compose.
4. **Pin versions exactly** (patch level for Docker images). Floating tags such as
   `16-alpine` are not used.
5. **Version upgrades are automated** with a dependency bot (Renovate or Dependabot),
   grouped by area (`api`, `web`, `docker`, `github-actions`). CI validates each upgrade PR.
6. **`deploy/.env` holds development-only values.** Any value that would be secret in a
   real environment is overridden there through environment variables or secrets.

## Consequences

- Changing the PostgreSQL version is a one-line change in `deploy/.env`.
- New settings must be placed according to the table above; reviewers reject duplicates.
- Tests depend on `deploy/.env` being present; a missing key fails fast with a clear message.
- `.gitignore` ignores `.env` files except `deploy/.env`.

## Alternatives considered

- **One configuration file for everything:** rejected; tools cannot all read one format,
  and secrets would mix with non-secret settings.
- **Hard-coded values per tool:** rejected; values drift between Compose, tests and CI.
- **Only `.env.example` committed:** rejected for local infrastructure because it adds a manual
  copy step with no benefit when the values are not secret.
