# Specifications

Product specifications for BearWithMe, one file per release (for example `v1-product-spec.md`).

## Rules

- **A frozen spec is never edited.** Once a spec is marked `Status: Frozen`, its content
  is final, including typos and outdated details.
- **Changes go into new documents:**
  - a change record in [`docs/changes/`](../changes/) (`CR-NNN-short-title.md`) for a
    decision that amends a frozen spec, for example [CR-001](../changes/CR-001-java-25-spring-boot-4.md);
  - a new release spec in this folder (`v1.1-product-spec.md`, ...) for the next release's scope.
- A new spec lists the change records it absorbs, so the latest spec plus its later CRs
  always describe the current product.

## Index

| Spec | Release | Status |
| --- | --- | --- |
| [v1-product-spec.md](v1-product-spec.md) | v1 (Round 1) | Frozen 2026-10-27 |
