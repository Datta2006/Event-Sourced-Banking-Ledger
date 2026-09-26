# Skills Playbook — When to Invoke Which Skill

> These are **recommendations for future sessions**. The team should invoke the relevant skill explicitly when starting that kind of work — don't assume it fires automatically.

## Planning

| Task | Skill(s) | Why |
|---|---|---|
| Explore architecture options before committing | `superpowers:brainstorming` | Structured divergent thinking before locking an ADR |
| Turn agreed scope into a working plan | `superpowers:writing-plans`, `autoplan` | Converts FEATURES docs into an actionable, ordered plan |
| Investigate an unfamiliar pattern/library before deciding | `research`, `superpowers:subagent-dr` | Deep dive without polluting the main context window |
| Draft the actual per-service API contracts | `spec` | Dedicated skill for writing specs before implementation |
| Model the domain (aggregates, events, invariants) before coding | `domain-modeling` | Directly matches our event-sourcing/DDD design step |

## Architecture

| Task | Skill(s) | Why |
|---|---|---|
| Produce/refresh Mermaid or C4 diagrams | `diagram`, `artifact-diagramming` | Keep ARCHITECTURE.md and SAGA_DESIGN.md diagrams current |
| Get a second opinion on a design before implementation | `plan-eng-review`, `plan-design-review`, `plan-devex-review` | Cheap review gate before 4 people build in parallel |

## Setup

| Task | Skill(s) | Why |
|---|---|---|
| Bootstrap each service repo/module correctly | `init` | Standardized project setup |
| Configure git hygiene, branch protection, pre-commit hooks | `git-guardrails-claude-code`, `setup-pre-commit` | Prevents 4 people from breaking `main` |
| Wire up CI/CD and deployment target | `setup-deploy` | Turns the deployment doc into working automation |

## Parallel Development

| Task | Skill(s) | Why |
|---|---|---|
| Let each of the 4 members work on a different service without stepping on each other | `superpowers:using-git-worktrees`, `superpowers:dispatching-parallel-agents` | Matches "one team member per service" ownership model in CONTRIBUTING.md |

## Implementation

| Task | Skill(s) | Why |
|---|---|---|
| Build each feature test-first | `superpowers:test-driven`, `tdd` | Keeps unit/integration coverage honest per feature |
| Work carefully through a feature doc's acceptance criteria one at a time | `careful`, `impeccable`, `pair-agent` | Slower, higher-quality implementation pass for core financial logic |

## Debugging

| Task | Skill(s) | Why |
|---|---|---|
| Diagnose a failing saga step, event ordering bug, or read-model drift | `superpowers:diagnosing-bugs`, `superpowers:systematic-debugging` | Structured root-cause process instead of guesswork on distributed bugs |

## Frontend

| Task | Skill(s) | Why |
|---|---|---|
| Build/refine the dashboard UI | `design-html`, `design-taste-frontend`, `design-consultation` | Matches the "web-based frontend" rubric requirement |
| Add restrained motion/feedback to the dashboard | `animate`, `animation-vocabulary` | Optional polish once core UI works |

## Review

| Task | Skill(s) | Why |
|---|---|---|
| Before merging any service's PR | `superpowers:requesting-code-review`, `superpowers:receiving-code-review`, `code-review`, `review` | Standard PR gate across all 4 service owners |
| Security pass on auth, input validation, secrets handling | `security-review` | Financial-domain project — worth a dedicated pass before submission |

## Finishing

| Task | Skill(s) | Why |
|---|---|---|
| Wrap up a feature branch cleanly | `superpowers:finishing-a-development-branch`, `pr` | Consistent branch/PR hygiene |
| Resolve conflicts when two services' shared code diverges | `superpowers:resolving-merge-conflicts` | Shared event contracts are the most likely conflict point |
| Full pre-submission pass across all services | `qa`, `qa-only`, `superpowers:verification-before-completion` | Final check against the FEATURES acceptance criteria before demo day |

## Documentation

| Task | Skill(s) | Why |
|---|---|---|
| Regenerate/update docs as the system evolves post-scaffolding | `document-generate`, `writing-for-agents` | Keep docs in sync with code instead of letting them rot |
| Produce a submission-ready PDF of the report/docs | `make-pdf` | Course deliverable is likely a written report as well as code |

## Deployment

| Task | Skill(s) | Why |
|---|---|---|
| Cut the final deployable build for submission/demo | `ship`, `land-and-deploy`, `freeze` | Final packaging once all services pass QA |

## Housekeeping

| Task | Skill(s) | Why |
|---|---|---|
| Save/restore context between long working sessions per service | `context-save`, `context-restore` | Useful given 4 people juggling 5+ services |

## Meta

| Task | Skill(s) | Why |
|---|---|---|
| Find a skill you're not sure exists yet | `find-skills` | Fallback whenever this table doesn't cover a new situation |