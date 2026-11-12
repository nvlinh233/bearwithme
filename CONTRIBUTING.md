# Contributing to BearWithMe

All code, comments, documentation, commit messages, issues and pull requests are written in English.

## Repository layout

| Path | Content |
| --- | --- |
| `api/` | Spring Boot backend, Maven multi-module (`app/` plus one module per domain) |
| `web/` | Vue 3 frontend |
| `deploy/` | Docker Compose and shared local settings (`deploy/.env`) |
| `docs/specs/` | Product specifications (frozen once released) |
| `docs/changes/` | Change records (CR) against a frozen spec |
| `docs/adr/` | Architecture decision records |
| `.github/` | CI workflows, PR template, dependency bot config |

## Workflow

1. **Every change starts from a GitHub issue.** Requirements are referenced by ID (for example `FR-C1`).
2. **Create a branch from `main`:** `<type>/<issue-number>-<short-description>`,
   for example `feat/12-card-crud` or `chore/1-init-monorepo`.
3. **Commit with [Conventional Commits](https://www.conventionalcommits.org/):**
   `<type>(<scope>): <summary>`
   - Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`
   - Scopes: `api`, `web`, `deploy`, `ci`, `docs`
   - Summary in imperative mood, lowercase, no trailing period:
     `feat(api): add card creation endpoint`
4. **Open a pull request** using the template. Link the issue with `Closes #<number>`.
5. **CI must be green** (build, tests, lint, type-check) before merging.
6. **Squash merge** into `main`; the PR title becomes the commit message, so it follows Conventional Commits too.
7. Delete the branch after merge.

`main` is protected: no direct pushes, pull request and passing CI required.

## Decisions and specifications

- A released spec is **frozen**. Changes go into a change record:
  `docs/changes/CR-NNN-<title>.md`.
- Architecture or tooling decisions with trade-offs go into an ADR:
  `docs/adr/NNNN-<title>.md` (sections: Context, Decision, Consequences, Alternatives considered).
- ADRs are not edited after acceptance; a new ADR supersedes an old one.

## Configuration

Follow [ADR-0001](docs/adr/0001-configuration-management.md):

- Each value has exactly one source; never copy a version or setting into a second file.
- Local infrastructure settings live in `deploy/.env`.
- Application runtime settings come from environment variables; `application.yaml` only maps them.
- Never commit real secrets.
- Pin exact versions; let the dependency bot propose upgrades.

## Code quality

- Backend: unit tests for domain logic, integration tests with Testcontainers; line coverage at least 70%.
- A domain module never reads another module's tables or internal classes; it uses the other module's public API.
- Frontend: TypeScript strict mode, lint and type-check must pass.
- Keep pull requests small and focused on one issue.

## Definition of done

- [ ] Acceptance criteria of the issue are met
- [ ] Tests added or updated, CI green
- [ ] Docs updated (README, ADR or CR) when behavior or setup changed
- [ ] No secrets, no debug leftovers
