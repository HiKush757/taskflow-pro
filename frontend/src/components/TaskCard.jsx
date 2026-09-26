import React from 'react';

export default function TaskCard({ task, dragging, onClick, onCriticalPath, maxCriticalPath }) {
  const isCritical = onCriticalPath && onCriticalPath[task.id] === maxCriticalPath && maxCriticalPath > 0;

  return (
    <div
      className={`task-card ${dragging ? 'task-card-dragging' : ''} ${isCritical ? 'task-card-critical' : ''}`}
      onClick={onClick}
    >
      <div className="task-title">{task.title}</div>
      <div className="task-meta">
        {task.status !== 'DONE' && (
          <span className={`badge ${task.blocked ? 'badge-blocked' : 'badge-ready'}`}>
            {task.blocked ? 'Blocked' : 'Ready'}
          </span>
        )}
        {task.scheduledEnd && <span className="due-date">due {task.scheduledEnd}</span>}
      </div>
    </div>
  );
}
