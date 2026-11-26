import { signal } from '@angular/core';
import { FocusPage } from './focus.page';
import { Task } from '../../api';

describe('Focus task picker', () => {
  it('searches unfinished tasks, selects only synced in-progress tasks', () => {
    const page = Object.create(FocusPage.prototype) as FocusPage;
    Object.assign(page, {
      workspace: { matches: (t:Task) => t.workspaceId === 'work' },
      taskQuery: '', taskId: '',
    });
    page.tasks = signal<Task[]>([
      ...(['TODO','IN_PROGRESS','DONE','CANCELED'] as const).map(status=>({id:status,title:`Task ${status}`,status,workspaceId:'work',revision:1})),
      {id:'pending',title:'Pending task',status:'IN_PROGRESS',workspaceId:'work',revision:1},
      {id:'other',title:'Other task',status:'IN_PROGRESS',workspaceId:'other',revision:1},
    ]);
    expect(page.filteredTasks().map(t=>t.id)).toEqual(['TODO','IN_PROGRESS','CANCELED','pending']);
    page.taskQuery='task';
    expect(page.filteredTasks().map(t=>t.id)).toEqual(['TODO','IN_PROGRESS','CANCELED','pending']);
    expect(page.filteredTasks().filter(t=>page.canFocus(t)).map(t=>t.id)).toEqual(['IN_PROGRESS','pending']);
    page.selectTask('TODO');
    expect(page.taskId).toBe('');
  });
  it('creates and selects a task from the picker', async () => {
    const page=Object.create(FocusPage.prototype) as FocusPage;
    const task={id:'new',title:'New task',status:'IN_PROGRESS',workspaceId:'work',revision:1} as Task;
    Object.assign(page,{workspace:{selectedId:()=> 'work',matches:()=>true},store:{create:async()=> task},taskId:'',taskQuery:'',newTaskTitle:' New task ',taskCreateError:'',taskCreateOpen:true});
    page.tasks=signal<Task[]>([]);
    await page.createTask();
    expect(page.taskId).toBe('new');expect(page.taskQuery).toBe('New task');expect(page.taskCreateOpen).toBe(false);
  });
});
