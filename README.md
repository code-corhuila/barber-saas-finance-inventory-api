# barber-saas-finance-inventory-api

> finance-inventory bounded context: service API

Part of the **Barber Saas** distributed system — team `barber-saas`, Grupo 2.
Governance and documentation live in [`barber-saas-docs`](https://github.com/code-corhuila/barber-saas-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `barber-saas-docs`.

---

## BarberSaaS — what this repository is

The finance-inventory service: a barbershop's income and expenses with their summary per period,
and its product inventory with stock movements (`07-api/contracts/openapi/finance-inventory-service.yaml`,
HU-FIN-001 #10, HU-INV-001 #11). Hexagonal, three Maven modules (ADR-012, annex C):
`finance-inventory-core` (domain and use cases, no Spring), `finance-inventory-adapters` (HTTP in
and out, JDBC, RS256 validation) and `finance-inventory-app` (composition root). It never migrates
its schema: that is `barber-saas-finance-inventory-db`.

| Operation | Who |
|---|---|
| `POST /api/v1/finance/records` (`Idempotency-Key` required) | `ADMIN_BARBERSHOP` |
| `GET /api/v1/finance/records?page&limit&from&to&type&category`, `GET …/{id}` | `ADMIN_BARBERSHOP` |
| `GET /api/v1/finance/summary?from&to` | `ADMIN_BARBERSHOP` |
| `POST /api/v1/inventory/products` (`Idempotency-Key` required), `GET …?lowStock`, `GET …/{id}`, `PUT …/{id}` | `ADMIN_BARBERSHOP` |
| `POST /api/v1/inventory/products/{id}/movements` (`Idempotency-Key` required), `GET …/movements?movementType` | `ADMIN_BARBERSHOP` |
| `GET /health` | liveness, no token |

Rules: an amount is an integer of cents ≥ 1 for both types and `type` gives the sign (DEC-FIN-01);
records are never edited nor deleted (DEC-FIN-02). Stock is a quantity with two decimals, set when
the product is created and changed only by a movement (DEC-INV-01): `PUT` with `currentStock` is a
`400`, and an exit larger than the stock is a `422` that records nothing. The stock moves by a delta
in the same transaction as the movement and its key, so two concurrent exits cannot take the same
stock (`chk_inventory_product_stock`). `lowStock` = stock ≤ minimum, computed on read. The tenant
comes **only** from the token: another barbershop's record or product answers `404`
(HU-TENANT-001 #13).

**The appointment domain, through its API (golden rule 8):** a `relatedAppointmentId` is checked with
`GET /api/v1/appointments/{id}` on `appointment-api`, carrying the owner's token and
`X-Correlation-Id` (2 s to connect, 3 s per attempt, one retry only when unreachable or 502/503/504).
A `404` there is a `404` here; any other failure answers `500` instead of assuming it exists.

### How to start it

As part of the platform: `./scripts/up.sh dev` in `barber-saas-infra-postgres`. Alone, without a
database (in-memory repositories):

```bash
mvn -B -DskipTests package
JWT_PUBLIC_KEY="$(cat ../barber-saas-infra-postgres/keys/jwt-public.pem)" \
APPOINTMENT_API_URL=http://localhost:8083 \
  java -jar finance-inventory-app/target/finance-inventory-app-0.1.0.jar
```

### Where the data is

Schema `finance_inventory` of the shared PostgreSQL instance, as `finance_inventory_app`
(`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`; see `.env.example`), never as the
administrator. Every creation and its idempotency key are written in one transaction.

### How it is tested

`mvn -B verify` (no Docker needed): the domain, the use cases with fake ports, the appointment-api
client against a stub HTTP server, and the HTTP contract over the whole service (annex C) with a
stand-in for appointment-api, including cross-barbershop tests. The JDBC repositories are also
tested against a database migrated by `barber-saas-finance-inventory-db` when `TEST_DATABASE_URL`,
`TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD` are set.

### What is missing

- It consumes no events: income and expenses are recorded by the staff (phase 2 decision).
- The contract has no `GET` of a single movement; the movement's `Location` points to
  `/api/v1/inventory/products/{id}/movements/{movementId}`.
