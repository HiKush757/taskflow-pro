-- TaskFlow Pro schema + seed data.
-- 10 tasks with a dependency graph that deliberately includes: a simple
-- chain (Database Schema -> Backend API -> Integration Tests, matching the
-- problem statement's own example), a diamond convergence (Backend API ->
-- Frontend Board UI -> Demo Video, and Backend API -> AI Suggestion Service
-- -> Demo Video), and a multi-level chain (Demo Video -> Docs -> Deploy).

CREATE TABLE tasks (
    id              VARCHAR(36) PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL,
    board_position  INTEGER NOT NULL,
    duration_days   INTEGER NOT NULL,
    planned_start   DATE,
    scheduled_start DATE,
    scheduled_end   DATE,
    is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE dependencies (
    prerequisite_id VARCHAR(36) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    task_id         VARCHAR(36) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    source          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (prerequisite_id, task_id),
    CONSTRAINT no_self_dependency CHECK (prerequisite_id <> task_id)
);

CREATE INDEX idx_dependencies_task_id ON dependencies(task_id);
CREATE INDEX idx_dependencies_prerequisite_id ON dependencies(prerequisite_id);

CREATE TABLE ai_suggestions (
    id           VARCHAR(36) PRIMARY KEY,
    task_id      VARCHAR(36) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    candidate_id VARCHAR(36) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    reason       TEXT,
    confidence   DOUBLE PRECISION,
    state        VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_suggestions_task_id ON ai_suggestions(task_id);

-- Seed tasks. Schedule columns below are pre-computed by hand to match what
-- the DagEngine would produce, so the board is consistent from first load
-- (max(plannedStart, latest prerequisite end); no compounding across the
-- diamond at Demo Video).

INSERT INTO tasks (id, title, description, status, board_position, duration_days, planned_start, scheduled_start, scheduled_end, is_blocked) VALUES
('11111111-1111-1111-1111-111111111001', 'Design Database Schema', 'Tasks, dependencies, and AI suggestion tables.', 'DONE', 0, 2, '2026-01-05', '2026-01-05', '2026-01-07', FALSE),
('11111111-1111-1111-1111-111111111002', 'Build Backend API', 'REST endpoints for tasks, dependencies, and scheduling.', 'DONE', 1, 5, '2026-01-05', '2026-01-07', '2026-01-12', FALSE),
('11111111-1111-1111-1111-111111111003', 'Build Frontend Kanban Board', 'React board with four columns and drag-and-drop.', 'IN_PROGRESS', 0, 4, '2026-01-05', '2026-01-12', '2026-01-16', FALSE),
('11111111-1111-1111-1111-111111111004', 'Implement Drag and Drop Persistence', 'Column and position changes saved to the database.', 'BACKLOG', 0, 3, '2026-01-05', '2026-01-16', '2026-01-19', TRUE),
('11111111-1111-1111-1111-111111111005', 'Build AI Suggestion Service', 'Keyword heuristic plus optional LLM hook for dependency suggestions.', 'BACKLOG', 1, 3, '2026-01-05', '2026-01-12', '2026-01-15', FALSE),
('11111111-1111-1111-1111-111111111006', 'Write Integration Tests', 'End-to-end tests against the running API.', 'BACKLOG', 2, 2, '2026-01-05', '2026-01-12', '2026-01-14', FALSE),
('11111111-1111-1111-1111-111111111007', 'Record Demo Video', 'Screen recording walking through the board and AI suggestions.', 'BACKLOG', 3, 1, '2026-01-05', '2026-01-16', '2026-01-17', TRUE),
('11111111-1111-1111-1111-111111111008', 'Write README and Docs', 'Setup instructions, architecture notes, assumptions and limitations.', 'BACKLOG', 4, 2, '2026-01-05', '2026-01-17', '2026-01-19', TRUE),
('11111111-1111-1111-1111-111111111009', 'Set Up Deployment', 'Deploy backend and frontend; optional for bonus credit.', 'BACKLOG', 5, 1, '2026-01-05', '2026-01-19', '2026-01-20', TRUE),
('11111111-1111-1111-1111-111111111010', 'Critical Path View', 'Optional bonus: highlight the longest dependency chain.', 'BACKLOG', 6, 2, '2026-01-05', '2026-01-19', '2026-01-21', TRUE);

-- Dependency edges (prerequisite_id -> task_id).
INSERT INTO dependencies (prerequisite_id, task_id, source) VALUES
('11111111-1111-1111-1111-111111111001', '11111111-1111-1111-1111-111111111002', 'MANUAL'), -- Schema -> API
('11111111-1111-1111-1111-111111111002', '11111111-1111-1111-1111-111111111003', 'MANUAL'), -- API -> Board UI
('11111111-1111-1111-1111-111111111002', '11111111-1111-1111-1111-111111111005', 'MANUAL'), -- API -> AI Suggestion Service
('11111111-1111-1111-1111-111111111002', '11111111-1111-1111-1111-111111111006', 'MANUAL'), -- API -> Integration Tests
('11111111-1111-1111-1111-111111111003', '11111111-1111-1111-1111-111111111004', 'MANUAL'), -- Board UI -> Drag and Drop
('11111111-1111-1111-1111-111111111003', '11111111-1111-1111-1111-111111111007', 'MANUAL'), -- Board UI -> Demo Video (diamond side 1)
('11111111-1111-1111-1111-111111111005', '11111111-1111-1111-1111-111111111007', 'MANUAL'), -- AI Suggestion Service -> Demo Video (diamond side 2)
('11111111-1111-1111-1111-111111111006', '11111111-1111-1111-1111-111111111008', 'MANUAL'), -- Integration Tests -> Docs
('11111111-1111-1111-1111-111111111007', '11111111-1111-1111-1111-111111111008', 'MANUAL'), -- Demo Video -> Docs
('11111111-1111-1111-1111-111111111008', '11111111-1111-1111-1111-111111111009', 'MANUAL'), -- Docs -> Deployment
('11111111-1111-1111-1111-111111111004', '11111111-1111-1111-1111-111111111010', 'MANUAL'); -- Drag and Drop -> Critical Path View
