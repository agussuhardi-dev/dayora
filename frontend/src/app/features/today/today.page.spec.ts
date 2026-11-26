import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TodayPage } from './today.page';
import { Api } from '../../api';
import { WorkspaceService } from '../../workspace.service';
import { TaskStore } from '../../task-store.service';
import { RealtimeService } from '../../realtime.service';
import { DestroyRef } from '@angular/core';
import { of, Subject } from 'rxjs';

describe('TodayPage', () => {
  it('computes greetings and filters active tasks', () => {
    const api = {
      events: () => of([]),
      currentFocus: () => of(null),
    } as unknown as Api;

    const workspace = {
      selectedId: signal('w1'),
      workspaces: signal([{ id: 'w1', name: 'Personal' }]),
      matches: () => true,
      open: vi.fn(),
    } as unknown as WorkspaceService;

    const store = {
      tasks: signal([
        { id: '1', title: 'Task 1', status: 'TODO' },
        { id: '2', title: 'Task 2', status: 'DONE' },
        { id: '3', title: 'Task 3', status: 'IN_PROGRESS' },
      ]),
      status: signal(''),
      load: vi.fn().mockResolvedValue(undefined),
    } as unknown as TaskStore;

    const realtime = {
      invalidated: new Subject<void>(),
    } as unknown as RealtimeService;

    const destroy = { onDestroy: vi.fn() } as unknown as DestroyRef;

    const page = TestBed.runInInjectionContext(
      () => new TodayPage(api, workspace, store, realtime, destroy)
    );

    page.tasks.set(store.tasks() as any);
    expect(page.greeting()).toMatch(/Good (morning|afternoon|evening)/);
    expect(page.workspaceName()).toBe('Personal');
    expect(page.activeTasks().map(t => t.id)).toEqual(['1', '3']);
  });
});
