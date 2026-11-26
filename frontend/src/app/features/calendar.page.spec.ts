import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';
import { CalendarPage, PlannerEvent } from './calendar.page';
import { WorkspaceService } from '../workspace.service';
import { TaskStore } from '../task-store.service';
import { Task } from '../api';

describe('CalendarPage', () => {
  let workspace: Partial<WorkspaceService>;
  let store: Partial<TaskStore>;

  beforeEach(() => {
    localStorage.clear();
    const tasks = signal<Task[]>([
      { id: 't1', title: 'Task 1', status: 'TODO', workspaceId: 'w1', revision: 1 } as Task,
      { id: 't2', title: 'Task 2', status: 'TODO', workspaceId: 'w1', revision: 1 } as Task,
    ]);

    workspace = {
      selectedId: signal('w1'),
      workspaces: signal([{ id: 'w1', name: 'Main Workspace', defaultWorkflowId: 'wf1' }]),
      matches: (t: Task) => t.workspaceId === 'w1',
    };

    store = {
      tasks,
      load: () => Promise.resolve(),
    };

    TestBed.configureTestingModule({
      imports: [CalendarPage],
      providers: [
        { provide: WorkspaceService, useValue: workspace },
        { provide: TaskStore, useValue: store },
      ],
    });
  });

  it('initializes and seeds planner events in localStorage if empty', () => {
    const fixture = TestBed.createComponent(CalendarPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.events().length).toBeGreaterThan(0);
    expect(component.periodTitle()).toContain('2026');
  });

  it('toggles view mode between month and week', () => {
    const fixture = TestBed.createComponent(CalendarPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.viewMode()).toBe('month');
    component.viewMode.set('week');
    expect(component.viewMode()).toBe('week');
    expect(component.weekDays().length).toBe(7);
  });

  it('creates, edits, and deletes events locally without backend errors', () => {
    const fixture = TestBed.createComponent(CalendarPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    // Create event
    component.openCreateModal('2026-11-25', '10:00');
    component.modalTitle = 'Design Review';
    component.modalStart = '10:00';
    component.modalEnd = '11:00';
    component.saveModal();

    const created = component.events().find(e => e.title === 'Design Review');
    expect(created).toBeDefined();
    expect(created?.startTime).toBe('10:00');

    // Edit event
    if (created) {
      component.openEditModal(created);
      component.modalTitle = 'Design Review Updated';
      component.saveModal();

      const updated = component.events().find(e => e.id === created.id);
      expect(updated?.title).toBe('Design Review Updated');

      // Delete event
      component.deleteEvent(created.id);
      expect(component.events().some(e => e.id === created.id)).toBe(false);
    }
  });

  it('schedules task from planner backlog into modal', () => {
    const fixture = TestBed.createComponent(CalendarPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.scheduleTask({ id: 't1', title: 'Task 1' });
    expect(component.modalOpen()).toBe(true);
    expect(component.modalTitle).toBe('Task 1');
    expect(component.modalTaskId).toBe('t1');
  });
});
