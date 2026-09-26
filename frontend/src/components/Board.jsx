import React from 'react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';
import TaskCard from './TaskCard.jsx';

const COLUMN_LABELS = {
  BACKLOG: 'Backlog',
  IN_PROGRESS: 'In Progress',
  REVIEW: 'Review',
  DONE: 'Done',
};

export default function Board({ columns, tasks, onMove, onSelect, criticalPath, maxCriticalPath }) {
  const byColumn = Object.fromEntries(
    columns.map(col => [
      col,
      tasks.filter(t => t.status === col).sort((a, b) => a.position - b.position),
    ])
  );

  const handleDragEnd = (result) => {
    const { destination, source, draggableId } = result;
    if (!destination) return;
    if (destination.droppableId === source.droppableId && destination.index === source.index) return;
    onMove(draggableId, destination.droppableId, destination.index);
  };

  return (
    <DragDropContext onDragEnd={handleDragEnd}>
      <div className="board">
        {columns.map(col => (
          <Droppable droppableId={col} key={col}>
            {(provided, snapshot) => (
              <div
                className={`column ${snapshot.isDraggingOver ? 'column-drag-over' : ''}`}
                ref={provided.innerRef}
                {...provided.droppableProps}
              >
                <div className="column-header">
                  {COLUMN_LABELS[col]} <span className="count">{byColumn[col].length}</span>
                </div>
                <div className="column-body">
                  {byColumn[col].length === 0 && (
                    <div className="empty-column">No tasks</div>
                  )}
                  {byColumn[col].map((task, index) => (
                    <Draggable draggableId={task.id} index={index} key={task.id}>
                      {(dragProvided, dragSnapshot) => (
                        <div
                          ref={dragProvided.innerRef}
                          {...dragProvided.draggableProps}
                          {...dragProvided.dragHandleProps}
                        >
                          <TaskCard
                            task={task}
                            dragging={dragSnapshot.isDragging}
                            onClick={() => onSelect(task.id)}
                            onCriticalPath={criticalPath}
                            maxCriticalPath={maxCriticalPath}
                          />
                        </div>
                      )}
                    </Draggable>
                  ))}
                  {provided.placeholder}
                </div>
              </div>
            )}
          </Droppable>
        ))}
      </div>
    </DragDropContext>
  );
}
