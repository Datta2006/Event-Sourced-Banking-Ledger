# Config Repository

Spring Cloud Config serves per-service configuration from this directory
(`default`/`docker` profiles, e.g. `account-command-service.yml`,
`loan-service.yml`). Mounted into the Config Server container at
`/tmp/ddbs-config-repo` (see `docker-compose.yml`).

Intentionally empty at scaffolding time — each service currently carries its own
`application.yml`. Externalized configuration lands here when services get their
real implementations (see `docs/DEPLOYMENT.md`).
