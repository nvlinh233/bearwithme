# BearWithMe v1 - Product Specification

Version: 1.0 · Status: Frozen on 2026-10-27 · Author: Linh

> This document is frozen. Changes after v1 are written as separate documents (for example `v1.1-product-spec.md` or a change record) and must not edit this file.

## Overview

BearWithMe v1 is a single-user web app for learning IT workplace English vocabulary and phrases through flashcards with spaced repetition (SM-2).

- **Primary user:** the author, a software engineer whose reading and writing are stronger than speaking.
- **Problem:** IT phrases used in standups, code reviews, meetings and interviews are not recalled fast enough in conversation.
- **v1 goal:** a daily 15-minute review loop that is fast to open, fast to grade and easy to fill with new cards.
- **Learning goal:** exercise Spring Boot 3, Spring Security (JWT), JPA, Flyway, Testcontainers and Vue 3 end to end, then evolve the system into microservices step by step (see Learning roadmap).

**Success criteria (after 4 weeks of use):**

1. At least 300 cards in the system.
2. Reviews done on at least 24 of 28 days.
3. A daily due queue completes in under 15 minutes.
4. All P0 issues of Round 1 closed, CI green on main.

## Scope

v1 equals Round 1 of the roadmap: vocabulary cards and daily review only. Speaking features start in v1.1.

| Area | v1 (Round 1) | Later |
| --- | --- | --- |
| Auth | Single seeded account, JWT login | Registration, multiple users, roles |
| Cards | CRUD, tags, search, CSV import | Shared decks, images, audio files |
| Review | SM-2 queue, 4 grades, keyboard shortcuts | Reverse cards (meaning to term), cram mode |
| Pronunciation | Browser text-to-speech (en-US) | Recording, speech-to-text, shadowing (v1.1) |
| Scenarios | None | Role-play dialogues (v1.1), AI conversation (v2) |
| Stats | Dashboard: due, reviewed today, streak, total | 30-day history chart (v1.1) |
| Platform | Desktop web (Chrome, Edge) | Mobile layout, PWA, native app |
| Infra | Docker Compose, GitHub Actions CI | MyBatis, Kafka, microservices, Kubernetes (Rounds 2-5) |

Explicitly out of v1: payments, high school (SGK) curriculum, gamification, i18n of the UI, admin panel.

## Functional requirements

Requirement IDs are referenced from GitHub issues and tests.

### Authentication

- **FR-A1** The system has one user, seeded by a Flyway migration; the password is stored as a BCrypt hash.
- **FR-A2** Login with username and password returns a JWT access token valid for 7 days.
- **FR-A3** Every `/api/**` endpoint except login requires a valid token; otherwise it returns 401.
- **FR-A4** Logout clears the token on the client; there is no server-side revocation in v1.

### Cards

- **FR-C1** A card has: term (required, max 200 chars), meaning in English (required, max 500), example sentence (optional, max 1000), note (optional, max 1000), tags (0-10).
- **FR-C2** Terms are unique per user, compared case-insensitively after trimming.
- **FR-C3** Tags are lowercase kebab-case (for example `code-review`); unknown tags are created on save.
- **FR-C4** The card list supports full-text search on term and meaning, filter by one tag, sort by created date or term, page size 20.
- **FR-C5** Creating a card also creates its review state as a new card (never reviewed); new cards enter the queue in creation order, limited by FR-R1.
- **FR-C6** Deleting a card deletes its review state and review logs, after confirmation.

### CSV import

- **FR-I1** Upload a UTF-8 CSV with header `term,meaning,example,tags`; tags are separated by `|`.
- **FR-I2** Maximum 2 MB and 2,000 rows per file.
- **FR-I3** Rows with an existing term are skipped; invalid rows are reported with line number and reason.
- **FR-I4** Valid rows are saved in one transaction; the response returns created, skipped and error counts.

### Review

- **FR-R1** The daily queue contains every reviewed card whose due date is today or earlier (oldest due first), plus new cards up to 20 new cards per day; one session shows at most 50 cards.
- **FR-R2** Cards are reviewed term to meaning only: the term is shown first and the user flips it to see meaning, example and note.
- **FR-R3** The user grades with Again, Hard, Good or Easy; the next due date is computed with SM-2 (see Scheduling rules).
- **FR-R4** Each grade writes one review log row and updates the review state in the same transaction.
- **FR-R5** Cards graded Again reappear at the end of the current session until graded Hard or better.
- **FR-R6** The term and example can be read aloud with browser text-to-speech, voice en-US.

### Dashboard

- **FR-D1** Shows: cards due now, cards reviewed today, current streak, total cards.
- **FR-D2** Streak = consecutive days, ending today or yesterday, with at least one review.
- **FR-D3** All day boundaries use the Asia/Ho_Chi_Minh time zone.

## Screens

v1 has five screens. After login the user lands on the Dashboard; a top navigation bar links Dashboard and Cards on every screen except Login.

| # | Screen | Route | Main elements | Key behaviors |
| --- | --- | --- | --- | --- |
| 1 | Login | `/login` | Username, password, Sign in button, error message | Redirects to the page originally requested; already logged in goes to `/` |
| 2 | Dashboard | `/` | Due count, reviewed today, streak, total cards, Start review button | Start review disabled when nothing is due; empty state links to Add card |
| 3 | Review session | `/review` | Card front (term), card back (meaning, example, note), speaker buttons, 4 grade buttons, progress (n / total) | Space flips; keys 1-4 grade; end screen shows reviewed count and Back to dashboard |
| 4 | Card list | `/cards` | Search box, tag filter, table (term, meaning, tags, next due), pagination, Add card and Import CSV buttons | Search debounced 300 ms; delete asks for confirmation |
| 5 | Card editor | `/cards/new`, `/cards/:id` | Term, meaning, example, note, tag multi-select, Listen button, Save and Cancel; CSV upload panel on `/cards/import` | Inline validation per FR-C1; Save and add another; import shows the report from FR-I4 |

## Scheduling rules (SM-2)

Scheduling follows classic SM-2 with four buttons mapped to quality q. A new card starts with ease factor EF = 2.5, repetitions n = 0, interval I = 0.

| Button | Key | Quality q |
| --- | --- | --- |
| Again | 1 | 1 |
| Hard | 2 | 3 |
| Good | 3 | 4 |
| Easy | 4 | 5 |

**Ease factor update (every grade):**

```latex
EF' = \max\left(1.3,\; EF + 0.1 - (5 - q)(0.08 + 0.02(5 - q))\right)
```

**Interval update:**

- If q < 3 (Again): n = 0, I = 1 day, card is re-queued in the current session (FR-R5).
- If q ≥ 3: n = n + 1; I = 1 when n = 1, I = 6 when n = 2, otherwise I = round(I × EF').
- Next due date = today (Asia/Ho_Chi_Minh) + I days.

The algorithm is a pure Java class with no Spring dependency, covered by unit tests for every branch and the 1.3 floor.

## Data model

Six PostgreSQL tables, created by Flyway migration V1. Content (cards, tags) is separated from learning state (review_states, review_logs) so multiple users can be added later without redesign.

| Table | Key columns | Constraints and indexes |
| --- | --- | --- |
| users | id (bigint), username, password_hash, created_at | unique(username) |
| cards | id, user_id, term, meaning, example, note, created_at, updated_at | unique(user_id, lower(term)); FK users |
| tags | id, user_id, name | unique(user_id, name) |
| card_tags | card_id, tag_id | PK(card_id, tag_id); FKs with ON DELETE CASCADE |
| review_states | card_id (PK), user_id, ease_factor numeric(4,2), interval_days int, repetitions int, due_date date, last_reviewed_at | index(user_id, due_date) |
| review_logs | id, card_id, user_id, grade smallint, ease_factor_after, interval_after, reviewed_at timestamptz | index(user_id, reviewed_at) |

All timestamps are stored as `timestamptz` in UTC; `due_date` is a local date in Asia/Ho_Chi_Minh.

## REST API

All endpoints are JSON under `/api`, documented by springdoc at `/swagger-ui`. Errors return RFC 9457 ProblemDetail.

| Method | Path | Request | Response | FR |
| --- | --- | --- | --- | --- |
| POST | /api/auth/login | {username, password} | 200 {accessToken, expiresAt}; 401 | A2 |
| GET | /api/dashboard | - | {dueCount, reviewedToday, streak, totalCards} | D1-D3 |
| GET | /api/reviews/due | ?limit=50 | [{cardId, term, meaning, example, note, tags}] | R1 |
| POST | /api/reviews/{cardId} | {grade: AGAIN, HARD, GOOD, EASY} | {nextDueDate, intervalDays, easeFactor} | R3-R5 |
| GET | /api/cards | ?q=&tag=&sort=&page=&size=20 | Page of {id, term, meaning, tags, dueDate} | C4 |
| GET | /api/cards/{id} | - | Card detail | C1 |
| POST | /api/cards | {term, meaning, example, note, tags[]} | 201 Card; 409 duplicate term | C1-C3, C5 |
| PUT | /api/cards/{id} | same as POST | 200 Card; 404; 409 | C1-C3 |
| DELETE | /api/cards/{id} | - | 204 | C6 |
| POST | /api/cards/import | multipart file | {created, skipped, errors[{line, reason}]} | I1-I4 |
| GET | /api/tags | - | [{name, cardCount}] | C3 |

## Non-functional requirements and tech stack

| Area | Requirement |
| --- | --- |
| Performance | API p95 under 200 ms locally; grading a card feels instant (under 100 ms round trip) |
| Data size | Designed for 10,000 cards and 100,000 review logs per user |
| Security | BCrypt passwords, JWT signed with a secret from environment variables, no secrets in git, CORS limited to the web origin |
| Testing | Unit tests for SM-2 and services; integration tests with Testcontainers PostgreSQL; backend line coverage at least 70% |
| Quality | CI runs build, tests, lint and type-check on every pull request; main stays green |
| Language | All code, comments, docs, commits and issues in English |
| Browsers | Latest Chrome and Edge on desktop (Web Speech API support) |
| Branding | Name BearWithMe; chocolate poodle mascot; palette chocolate #6E4030, dark #5A3325, milk brown #9C6A50, milk white #FBF8F5 (logo deferred) |

**Tech stack:** Java 21, Maven (multi-module), Spring Boot 3 (Web, Data JPA, Validation, Security, OAuth2 Resource Server, Flyway, Actuator), PostgreSQL 16, springdoc-openapi; Vue 3 with Vite, TypeScript, Vue Router, Pinia, Axios and PrimeVue (styled mode with a custom chocolate preset); Docker Compose for local development; GitHub Actions for CI; GitHub Issues and Projects for tracking. Monorepo layout: `/api`, `/web`, `/worker` (later), `/deploy`.

## Learning roadmap

v1 is a modular monolith on purpose; later rounds split it into microservices one concept at a time, each tied to a real feature. Module boundaries in v1 (auth, card, review, dashboard) are the future service boundaries, so each module owns its tables and no module queries another module's tables directly.

| Round | Release | App feature | Backend and architecture topics |
| --- | --- | --- | --- |
| 1 | v1 | Cards, CSV import, SM-2 review, dashboard | Modular monolith, JPA, Flyway, JWT, ProblemDetail, Testcontainers, CI |
| 2 | v1.1 | Scenarios, shadowing, 30-day stats | MyBatis for reporting queries alongside JPA, file storage for recordings |
| 3 | v1.2 | Async stats and recording processing | Kafka (KRaft), transactional outbox, idempotent consumers, first separate service (worker) |
| 4 | v1.3 | Same features, split into services | Microservices: API gateway (Spring Cloud Gateway), auth service issuing JWT, card, review and stats services with database per service, service discovery and central config, sync REST vs async events, saga for cross-service flows, Resilience4j (timeout, retry, circuit breaker), distributed tracing with OpenTelemetry, contract tests |
| 5 | v1.4 | Production-like deployment | Kubernetes (k3d), Helm or Kustomize, health probes, ConfigMaps and Secrets, horizontal scaling, Prometheus and Grafana |

Open point for Round 4: use the Spring Cloud stack (Eureka, Config Server) or rely on Kubernetes-native discovery and config, which overlaps with Round 5.

## Decisions

All v1 decisions are made; the spec above reflects them.

- [x] v1 = Round 1 only (cards + review); scenarios and shadowing move to v1.1.
- [x] Card direction: term to meaning only.
- [x] Meaning language: English.
- [x] New cards: at most 20 per day.
- [x] Session size: at most 50 cards.
- [x] Deployment for v1: local only (Docker Compose).
- [x] UI kit: PrimeVue.
- [x] Build tool: Maven.
