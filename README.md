# TaskFlow Pro

A Kanban board (Backlog / In Progress / Review / Done) backed by a
dependency-aware DAG engine: tasks can require other tasks to finish first,
the engine enforces that the dependency graph never has a cycle, and it
recomputes scheduled dates and Blocked/Ready status whenever anything
upstream changes — without ever double-counting a delay that reaches a task
through more than one path.

## Stack

- **Backend:** Spring Boot 3 (Java 17), PostgreSQL, Flyway migrations
- **Frontend:** React + Vite, `@hello-pangea/dnd` for drag-and-drop
- **Everything graph-related lives in one dependency-free class**,
  `DagEngine` (`backend/.../engine/DagEngine.java`), so it can be unit
  tested without Spring or a database.

## Running it locally

### 1. Database

```bash
docker compose up -d
```

This starts Postgres on `localhost:5432` with the credentials in
`docker-compose.yml` (`taskflow` / `taskflow`, database `taskflow_pro`).
Flyway creates the schema and seeds 10 tasks automatically the first time
the backend starts.

### 2. Backend

```bash
cd backend
cp ../.env.example ../.env.local   # then edit if you changed any defaults
mvn spring-boot:run
```

Runs on `http://localhost:8080`. Run the test suite (including the
DAG engine tests) with:

```bash
mvn test
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` and proxies `/api` to the backend.

## Seed data

The migration (`backend/src/main/resources/db/migration/V1__init.sql`)
seeds 10 tasks whose dependency graph deliberately exercises every rule in
the problem statement:

- A simple chain: **Design Database Schema → Build Backend API → Write
  Integration Tests** (mirrors the exact example in the brief).
- A diamond convergence: **Backend API → Frontend Board UI → Demo Video**
  and **Backend API → AI Suggestion Service → Demo Video**. If you push
  Backend API's date out, Demo Video should shift by exactly that amount —
  never double.
- A multi-level chain after that: **Demo Video → Docs → Deployment**, so a
  schedule change is visible three hops away.

## API overview

| Endpoint | Purpose |
|---|---|
| `GET /api/tasks` | List all tasks, each with its `prerequisiteIds`, computed `blocked`, `scheduledStart`/`scheduledEnd`. |
| `POST /api/tasks` | Create a task. |
| `PATCH /api/tasks/{id}/move` | Move a card between columns / reorder it. Rejected with 409 if the task is Blocked and the move isn't backward. |
| `PATCH /api/tasks/{id}/schedule` | Change planned start date or duration; triggers recompute of everything downstream. |
| `POST /api/tasks/{id}/dependencies` | Add a prerequisite. Rejected with 409 (and nothing persisted) if it would create a cycle. |
| `DELETE /api/tasks/{id}/dependencies/{prerequisiteId}` | Remove a dependency. |
| `POST /api/tasks/{id}/suggest-dependencies` | AI-augmented suggestions (see below). |
| `POST /api/suggestions/{id}/accept` \| `/reject` | Turn a suggestion into a real dependency, or dismiss it. |
| `GET /api/tasks/critical-path` | Longest-duration chain ending at each task (optional bonus feature). |

## How the DAG engine works

- **Cycle detection:** before adding an edge `prerequisite -> task`, the
  engine walks backward from `prerequisite` through its own prerequisites.
  If it reaches `task`, a path `task -> ... -> prerequisite` already
  exists, so the new edge would close a loop — rejected with HTTP 409,
  nothing written.
- **Scheduling without compounding:** every task's start date is
  `max(its own planned start, the latest scheduled end among its
  prerequisites)` — a maximum, not a sum. So when two paths converge on the
  same downstream task (a diamond), that task shifts by the size of the
  largest single delay reaching it, never by the total of both paths. This
  also makes recomputation idempotent: running it twice in a row changes
  nothing the second time.
- **What gets recomputed:** on any change, the engine finds every task
  reachable downstream of the one that changed (a forward BFS), then walks
  the *whole* graph in topological order, only touching nodes in that
  downstream set. Unaffected tasks keep their existing (still valid) dates.
- **Blocked vs Ready:** a task is Blocked if any direct prerequisite's
  status isn't Done. This is recalculated every time, so moving a
  completed task back to In Progress immediately re-blocks whatever
  depended on it.

## AI / LLM usage

`POST /api/tasks/{id}/suggest-dependencies` asks which existing tasks
should be prerequisites of the given task, based on titles and
descriptions. Grounding strategy (detailed in the synopsis, summarized
here):

1. **Closed-set prompting** — only real, existing task ids can be chosen.
2. **Structured output** — a candidate id, a short reason, and a confidence
   score; anything that doesn't parse is discarded.
3. **Server-side re-validation** — every suggestion is checked again in
   code (the id must exist, can't be the task itself, can't already be a
   prerequisite, and must not create a cycle) before it's ever shown to a
   user.
4. **Human in the loop** — suggestions are stored as `PROPOSED` and never
   become a real dependency until the user clicks Accept, at which point
   they go through the exact same cycle check as a manually added one.
5. **Fallback** — `AiSuggestionService` ships with a keyword-overlap
   heuristic (`ai.llm.enabled=false` by default) so the feature works with
   zero external configuration. The hook for a real LLM call is
   `AiSuggestionService.callLlm(...)`, gated behind `ai.llm.enabled` and an
   API key supplied only via an environment variable — never committed.

The dependency engine, not the model, remains the sole authority on
whether the graph is valid.

## Key Assumptions and Limitations

- **Dependencies are finish-to-start only**, and durations are whole
  calendar days (no working-day calendar, no lag/lead time).
- **A prerequisite only counts as satisfied when its status is exactly
  Done.** A task whose prerequisite regresses (Done → In Progress) is
  re-evaluated as Blocked — but **a task that is already Done is never
  automatically reopened** just because something upstream of it
  regressed. Its own status field is a human decision. This means a
  regression's *Blocked* effect doesn't cascade past an already-completed
  task, even though the *date* recomputation does still reach every
  downstream task regardless of status. This is a deliberate choice, not
  an oversight — see `DagEngineTest.rollbackReblocksOnlyTasksNotYetDone`
  for the exact behavior it verifies.
- **A Blocked task cannot be moved forward** (out of Backlog) — enforced
  server-side (HTTP 409), not just hidden in the UI.
- Every write that touches the graph loads the full task and dependency
  set from the database (`TaskService.recomputeFrom`). This is the
  simplest correct approach at the scale of a hackathon board (dozens to a
  few hundred tasks); at real scale this would instead load only the
  connected component containing the changed task.
- No authentication — single shared board, matching the brief's scope.
- The AI suggestion feature ships with a working, zero-config fallback
  heuristic rather than a live external LLM call, to keep the submission
  runnable without any API key. The integration point for a real model is
  clearly marked and documented above.

## Testing

`DagEngineTest` (`backend/src/test/java/com/taskflowpro/engine/`) covers,
independently of Spring or a database:

- Direct and multi-node cycle rejection, and self-dependencies.
- That a valid new edge is still accepted.
- The diamond example from the brief, verified to shift by exactly the
  single delay amount rather than compounding across both paths.
- Idempotency of recomputation.
- Rollback: a regressed task re-blocks its direct dependent, and the exact
  boundary of that effect (see Limitations above).
- Multi-level date propagation across two hops.
