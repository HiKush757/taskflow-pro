import React, { useState } from 'react';
import { api, errorMessage } from '../api.js';

export default function TaskModal({ task, allTasks, onClose, onChanged, onError }) {
  const [suggestions, setSuggestions] = useState([]);
  const [selectedPrereq, setSelectedPrereq] = useState('');

  const prereqIds = task.prerequisiteIds || [];
  const prereqTasks = allTasks.filter(t => prereqIds.includes(t.id));
  const candidates = allTasks.filter(t => t.id !== task.id && !prereqIds.includes(t.id));

  const addDependency = async () => {
    if (!selectedPrereq) return;
    try {
      await api.addDependency(task.id, selectedPrereq);
      setSelectedPrereq('');
      onChanged();
    } catch (e) {
      onError(e); // e.g. 409 Conflict: would create a cycle
    }
  };

  const removeDependency = async (prereqId) => {
    try {
      await api.removeDependency(task.id, prereqId);
      onChanged();
    } catch (e) {
      onError(e);
    }
  };

  const requestSuggestions = async () => {
    try {
      const result = await api.suggestDependencies(task.id);
      setSuggestions(result);
    } catch (e) {
      onError(e);
    }
  };

  const acceptSuggestion = async (id) => {
    try {
      await api.acceptSuggestion(id);
      setSuggestions(s => s.filter(x => x.id !== id));
      onChanged();
    } catch (e) {
      onError(e);
    }
  };

  const rejectSuggestion = async (id) => {
    await api.rejectSuggestion(id).catch(e => onError(e));
    setSuggestions(s => s.filter(x => x.id !== id));
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <button className="modal-close" onClick={onClose}>×</button>
        <h2>{task.title}</h2>
        <p className="task-description">{task.description || 'No description.'}</p>

        <div className="modal-row">
          <span className={`badge ${task.blocked ? 'badge-blocked' : 'badge-ready'}`}>
            {task.blocked ? 'Blocked' : 'Ready'}
          </span>
          {task.scheduledStart && task.scheduledEnd && (
            <span className="schedule-range">{task.scheduledStart} → {task.scheduledEnd}</span>
          )}
        </div>

        <section>
          <h3>Prerequisites</h3>
          {prereqTasks.length === 0 && <p className="muted">None yet.</p>}
          <ul className="prereq-list">
            {prereqTasks.map(p => (
              <li key={p.id}>
                {p.title}
                <button className="link-button" onClick={() => removeDependency(p.id)}>remove</button>
              </li>
            ))}
          </ul>
          <div className="add-prereq-row">
            <select value={selectedPrereq} onChange={e => setSelectedPrereq(e.target.value)}>
              <option value="">Add a prerequisite…</option>
              {candidates.map(c => (
                <option key={c.id} value={c.id}>{c.title}</option>
              ))}
            </select>
            <button onClick={addDependency}>Add</button>
          </div>
        </section>

        <section>
          <h3>AI Suggestions</h3>
          <button onClick={requestSuggestions}>Suggest dependencies</button>
          <ul className="suggestion-list">
            {suggestions.map(s => {
              const candidate = allTasks.find(t => t.id === s.candidateId);
              return (
                <li key={s.id}>
                  <div>
                    <strong>{candidate ? candidate.title : s.candidateId}</strong>
                    <span className="confidence"> ({Math.round((s.confidence || 0) * 100)}% confidence)</span>
                    <div className="muted small">{s.reason}</div>
                  </div>
                  <div className="suggestion-actions">
                    <button onClick={() => acceptSuggestion(s.id)}>Accept</button>
                    <button onClick={() => rejectSuggestion(s.id)}>Reject</button>
                  </div>
                </li>
              );
            })}
          </ul>
          <p className="muted small">
            Suggestions are never applied automatically — accept turns one into a real
            dependency and it goes through the same cycle check as a manual one.
          </p>
        </section>
      </div>
    </div>
  );
}
