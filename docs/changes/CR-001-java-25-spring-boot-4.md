# CR-001: Java 25 and Spring Boot 4.1

Date: 2026-10-27 · Status: Accepted · Affects: [v1-product-spec.md](../specs/v1-product-spec.md) (Tech stack, Learning goal)

## Change

- Java 21 → Java 25 (LTS).
- Spring Boot 3 → Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Jackson 3).

## Reason

Java 25 is the current LTS. Spring Boot 3.5 OSS support ended on 2026-06-30;
4.1 is the supported line.

## Impact

- JDK 25 locally and in CI (version recorded in `.java-version`).
- Boot 4 starter names (for example `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`),
  Testcontainers 2, springdoc 3.
- Tutorials written for Boot 3 may not apply as-is.
