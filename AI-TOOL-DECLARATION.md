# AI-Tool Declaration

## Tools used

| Tool | Model | What it was used for |
|---|---|---|
| Claude (Claude.ai) | Claude Sonnet 5 | Scaffolding the project structure (Spring Boot backend), writing the DagEngine cycle-detection/scheduling algorithm, writing the entities/controllers/services/DTOs, writing the Flyway seed migration |

## What was AI-assisted vs. hand-written

- **AI-assisted:** The full backend (DagEngine, TaskService, AiSuggestionService, controllers, entities), the React frontend (Board, TaskCard, TaskModal), the seed data, were generated with Claude's help based on the problem statement.
- **Reviewed and modified by me:** I ran the app end-to-end (Postgres via Docker, backend via Maven, frontend via npm), manually tested drag-and-drop, the Blocked/Ready badges, adding/removing dependencies, and the AI suggestion feature in the browser to confirm they work as described.
- **Written entirely by hand:** README, The project was built with help of AI assistance given the sprint timeline; all logic was verified by running it, not just reading it.

## Verification performed

- Ran the full app locally (Postgres + Spring Boot backend + React frontend) and manually tested: dragging tasks between columns, that Blocked tasks cannot be moved forward, adding/removing prerequisites, the cycle-detection rejection, and the AI suggestion accept/reject flow.
- Traced the diamond-dependency example from the problem statement against the seeded data to confirm a task's schedule doesn't compound when two paths converge on it.

## Product-level AI feature (in-app)

The dependency-suggestion feature itself uses AI at runtime — see `README.md` under "AI / LLM usage" for the full grounding strategy (closed-set prompting, structured output, server-side re-validation, human-in-the-loop acceptance, and a keyword-overlap fallback).
