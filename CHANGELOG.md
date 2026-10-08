# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-10-08

User stories: code-corhuila/barber-saas-docs#10, code-corhuila/barber-saas-docs#11, code-corhuila/barber-saas-docs#13, code-corhuila/barber-saas-docs#59

### Added

- domain: add the domain errors and the text length rules
- domain: add the finance record, always positive with the type as sign
- domain: add the stock quantity with two decimals, never negative
- domain: add the product whose stock only changes through movements
- application: declare the caller, its tenant and the errors of the use cases
- application: declare the clock, ids, idempotency keys and dependency failures
- application: declare the finance use cases and their repository
- application: ask appointment-api whether a related appointment exists
- application: declare the inventory use cases and their repository
- application: answer a key stored by a concurrent request as a retry
- application: record income and expenses and sum a period
- application: create products and move their stock in one transaction
- persistence: add pages, idempotency keys, the clock and the ids
- persistence: store finance records and sum a period in postgresql
- persistence: keep finance records in memory when there is no database
- persistence: move the stock by a delta in postgresql, guarded by its check
- persistence: keep products and movements in memory when there is no database
- http: call another domain's api with explicit timeouts and one retry
- http: ask appointment-api whether a related appointment exists
- http: answer every error with the shared envelope and its trace id
- http: carry the correlation id through the request and the logs
- http: add the health probe outside /api
- http: validate the rs256 token and take the tenant only from it
- app: compose the repositories, the appointment-api client and the use cases
- app: declare the server, pool and shutdown limits
- http: check bodies, paging and idempotency keys against the contract
- http: answer records and the summary with the contract's schemas
- http: expose the finance records and the period summary
- http: answer products and movements with two-decimal quantities
- http: expose the products and their stock movements
- deploy: build the service image and compose it with its own database user

### Documentation

- readme: point the header to Barber Saas and barber-saas-docs
- readme: explain the operations, the rules, how to start it and test it

### Tests

- ci: build and test every pull request with java 21
- app: start the whole service against an appointment-api stub

### Maintenance

- build: ignore build output, ide files, env files and keys
- github: add the pull request template
- github: track the story environment on the board
- build: add the maven parent on spring boot 3.5 and java 21
- build: add the core module without any framework dependency
- build: add the adapters module on spring web and jdbc
- build: add the app module that composes the service
- config: list the environment variables without any value

[2.0.0]: https://github.com/code-corhuila/barber-saas-finance-inventory-api/releases/tag/v2.0.0
