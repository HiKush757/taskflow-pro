# AI-Tool Declaration

Fill in the specifics before submitting — this is a template with the
required structure, not a finished declaration.

## Tools used

| Tool | Model | What it was used for |
|---|---|---|
| Claude (Claude.ai / Claude Code) | e.g. Claude Sonnet 4.6 | Scaffolding the project structure, drafting the DagEngine algorithm, generating boilerplate (entities, controllers, DTOs), drafting this README and the synopsis. |
| (add any others you used — Copilot, ChatGPT, etc.) | | |

## What was AI-assisted vs. hand-written

Be specific and honest — this is a required, scored disclosure item, not a
formality.

- **AI-assisted:** e.g. initial DagEngine structure, entity/controller
  boilerplate, README drafting.
- **Reviewed and modified by me:** describe what you changed and why —
  e.g. "adjusted the cycle-detection direction after tracing through the
  diamond example by hand", "rewrote the blocked-state rollback logic
  after finding the multi-level test case didn't match intended behavior."
- **Written entirely by hand:** list anything you wrote without AI
  assistance, if applicable.

## Verification performed

Describe how you checked the AI-assisted parts were actually correct, not
just plausible — e.g.:

- Ran the DagEngine unit tests (`mvn test`) covering cycle detection, the
  diamond no-compounding case, and rollback.
- Manually traced the diamond example from the problem statement by hand
  against the engine's output.
- (Add anything else you did: manual QA on the board, API testing with
  curl/Postman, etc.)

## Product-level AI feature (in-app)

The dependency-suggestion feature itself uses AI at runtime — this is
disclosed and explained in detail in `README.md` under "AI / LLM usage"
and in synopsis Section 4. That in-app usage is a required evaluation
criterion (15% of both scores) and is separate from this declaration,
which covers AI tools used *during development*.
