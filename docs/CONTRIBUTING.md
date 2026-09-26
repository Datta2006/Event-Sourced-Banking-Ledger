# Contributing Guide

## Branching Strategy

```
main (protected, CI runs on push)
  |
  +-- feature/account-command-service-deposit
  +-- feature/ledger-query-service-statements
  +-- feature/fraud-detection-service-rules
  +-- feature/notification-service-webhook
  +-- feature/api-gateway-aggregation
  +-- fix/...
  +-- docs/...
```

- `main` is **protected** — no direct pushes
- All work goes through feature branches: `feature/<service>-<short-desc>`
- PRs must be reviewed by at least one other team member
- Branches auto-delete after merge (GitHub Actions cleanup)

## Commit Message Convention

```
<type>(<scope>): <subject>

<body>
<footer>
```

**Types:** `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `perf`, `ci`

**Examples:**
- `feat(account-command): add deposit command handler`
- `fix(ledger-query): correct balance read model update`
- `docs(api-spec): add transfer endpoint schema`
- `test(fraud-detection): add velocity rule test`
- `chore(infra): update docker-compose kafka image`

## PR Checklist

Before opening a PR:
- [ ] Tests added/updated (unit + integration)
- [ ] All tests pass locally (`mvn test`)
- [ ] `mvn verify` passes (full build)
- [ ] Code formatted (`mvn spotless:apply` or `prettier --write`)
- [ ] OpenAPI spec updated if endpoints changed
- [ ] Event catalog updated if events changed
- [ ] Docs updated (README, architecture, etc.)
- [ ] No hardcoded secrets or credentials
- [ ] Commit messages follow convention
- [ ] PR description links to feature doc

## Code Style

### Java
- **Indentation:** 4 spaces
- **Brace style:** K&R (same line)
- **Line length:** 120 characters max
- **Naming:** camelCase for methods/variables, PascalCase for classes
- **Imports:** Alphabetical, static imports first
- **Annotations:** `@Override` on overridden methods

### TypeScript / React
- **Indentation:** 2 spaces
- **Quotes:** Single quotes
- **Semicolons:** No
- **Line length:** 100 characters max
- **Naming:** camelCase for variables/functions, PascalCase for components

### YAML
- **Indentation:** 2 spaces
- **Comments:** Explain non-obvious decisions

## Linting

- **Java:** `spotless-maven-plugin` (Google Java Format)
- **TypeScript:** `eslint` + `prettier`
- **YAML:** `yaml-lint`

## Team Ownership

| Team Member | Service(s) | Notes |
|---|---|---|
| [Member A] | Account Command Service | Primary owner |
| [Member B] | Ledger Query Service | Primary owner |
| [Member C] | Fraud Detection Service | Primary owner |
| [Member D] | Notification Service | Primary owner |
| [Member A + B] | API Gateway | Shared ownership |
| [Member C + D] | Infrastructure (Docker, Config, CI) | Shared ownership |

> **Edit this table** — assign actual team members to services based on preference.

## Conflict Resolution

Shared contracts (event schemas, API specs) are the most likely conflict point. When two services diverge:
1. Re-read the latest version of the shared file
2. Discuss which change is correct (in PR comments)
3. Update the shared contract accordingly
4. Re-run contract tests before merging

## Code Review

All PRs must be reviewed by at least one other team member. Reviewers should:
- Verify tests pass
- Check for security issues (input validation, secrets)
- Ensure docs are updated
- Look for event schema consistency
- Verify saga/compensation logic is correct

## Questions?

Open an issue on the repo or ping the team on Slack/Teams.