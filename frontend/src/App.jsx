import React, { useCallback, useEffect, useState } from 'react';
import Board from './components/Board.jsx';
import TaskModal from './components/TaskModal.jsx';
import { api, errorMessage } from './api.js';

const COLUMNS = ['BACKLOG', 'IN_PROGRESS', 'REVIEW', 'DONE'];

export default function App() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedTaskId, setSelectedTaskId] = useState(null);
  const [showCriticalPath, setShowCriticalPath] = useState(false);
  const [criticalPath, setCriticalPath] = useState({});

  const reload = useCallback(async () => {
    try {
      const data = await api.listTasks();
      setTasks(data);
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    if (!showCriticalPath) return;
    api.criticalPath().then(setCriticalPath).catch(e => setError(errorMessage(e)));
  }, [showCriticalPath, tasks]);

  const handleMove = async (taskId, status, position) => {
    // Optimistic update; reconciled (or rolled back) once the server replies.
    const prevTasks = tasks;
    setTasks(ts => ts.map(t => (t.id === taskId ? { ...t, status, position } : t)));
    try {
      await api.moveTask(taskId, status, position);
      await reload();
    } catch (e) {
      setTasks(prevTasks); // rollback: server rejected (e.g. Blocked task moved forward)
      setError(errorMessage(e));
    }
  };

  const handleCreate = async (payload) => {
    try {
      await api.createTask(payload);
      await reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  if (loading) return <div className="center-message">Loading board…</div>;

  const selectedTask = tasks.find(t => t.id === selectedTaskId) || null;
  const maxCriticalPath = Math.max(0, ...Object.values(criticalPath));

  return (
    <div className="app">
      <header className="app-header">
        <h1>TaskFlow Pro</h1>
        <div className="header-actions">
          <label className="toggle">
            <input
              type="checkbox"
              checked={showCriticalPath}
              onChange={e => setShowCriticalPath(e.target.checked)}
            />
            Critical Path
          </label>
          <NewTaskForm onCreate={handleCreate} />
        </div>
      </header>

      {error && (
        <div className="error-banner" onClick={() => setError(null)}>
          {error} <span className="dismiss">(dismiss)</span>
        </div>
      )}

      <Board
        columns={COLUMNS}
        tasks={tasks}
        onMove={handleMove}
        onSelect={setSelectedTaskId}
        criticalPath={showCriticalPath ? criticalPath : null}
        maxCriticalPath={maxCriticalPath}
      />

      {selectedTask && (
        <TaskModal
          task={selectedTask}
          allTasks={tasks}
          onClose={() => setSelectedTaskId(null)}
          onChanged={reload}
          onError={e => setError(errorMessage(e))}
        />
      )}
    </div>
  );
}

function NewTaskForm({ onCreate }) {
  const [title, setTitle] = useState('');
  const [open, setOpen] = useState(false);

  if (!open) {
    return <button onClick={() => setOpen(true)}>+ New Task</button>;
  }

  return (
    <form
      className="new-task-form"
      onSubmit={e => {
        e.preventDefault();
        if (!title.trim()) return;
        onCreate({ title: title.trim(), durationDays: 1, plannedStart: new Date().toISOString().slice(0, 10) });
        setTitle('');
        setOpen(false);
      }}
    >
      <input
        autoFocus
        value={title}
        onChange={e => setTitle(e.target.value)}
        placeholder="Task title"
      />
      <button type="submit">Add</button>
      <button type="button" onClick={() => setOpen(false)}>Cancel</button>
    </form>
  );
}
