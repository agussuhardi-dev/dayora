import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TasksPage } from './tasks.page';
import { TaskStore } from '../task-store.service';
import { WorkspaceService } from '../workspace.service';
import { Api, Task } from '../api';
import { firstValueFrom, of, Subject } from 'rxjs';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TaskLabelPickerComponent } from './task-label-picker.component';
import { MatDatepicker } from '@angular/material/datepicker';

const defaultStatuses = [{id:'todo',workspaceId:'w1',name:'To do',category:'UNSTARTED',color:'#000000',position:0,isDefault:true,archived:false}];
const api = {online:signal(true),labels:()=>of([]),createLabel:()=>of({}),deleteLabel:()=>of(null),taskStatuses:()=>of(defaultStatuses)} as unknown as Api;

describe('TasksPage component integration', () => {
  it('adds a task into the current workspace and clears the input on success', async () => {
    const tasks = signal<Task[]>([]);
    const store = {
      tasks,
      status: signal(''),
      load: vi.fn().mockResolvedValue(undefined),
      create: vi.fn().mockImplementation(async (title: string, workspaceId?: string) => {
        const created: Task = { id: 'created-id', title, status: 'TODO', priority: 'NONE', revision: 1, workspaceId };
        tasks.update(list => [created, ...list]);
        return created;
      }),
    } as unknown as TaskStore;

    const workspace = {
      selectedId: signal('w1'),
      workspaces: signal([{ id: 'w1', name: 'Personal' }]),
      matches: (entity: { workspaceId?: string }) => entity.workspaceId === 'w1',
    } as unknown as WorkspaceService;

    const page = TestBed.runInInjectionContext(() => new TasksPage(store, workspace, api));
    page.title = 'New feature';
    await page.add();

    expect(store.create).toHaveBeenCalledWith('New feature', 'w1');
    expect(page.title).toBe('');
    expect(page.tasks().map(item => item.id)).toEqual(['created-id']);
  });

  it('keeps the user title and shows an error message when creation fails', async () => {
    const store = {
      tasks: signal<Task[]>([]),
      status: signal(''),
      load: vi.fn().mockResolvedValue(undefined),
      create: vi.fn().mockRejectedValue({ status: 500 }),
    } as unknown as TaskStore;

    const workspace = {
      selectedId: signal('w1'),
      workspaces: signal([{ id: 'w1', name: 'Personal' }]),
      matches: () => true,
    } as unknown as WorkspaceService;

    const page = TestBed.runInInjectionContext(() => new TasksPage(store, workspace, api));
    page.title = 'Preserve my text';
    await page.add();

    expect(page.title).toBe('Preserve my text');
    expect(page.message()).toContain('could not be added');
    expect(page.tasks()).toEqual([]);
  });

  it('warns for an active duplicate, then allows explicit creation', async () => {
    const tasks = signal<Task[]>([{id:'existing',title:'Daily review',statusId:'todo',priority:'NONE',revision:1,workspaceId:'w1'}]);
    const store = {tasks,status:signal(''),load:vi.fn(),create:vi.fn().mockImplementation(async(title:string,workspaceId:string)=>{const created={id:'second',title,statusId:'todo',priority:'NONE',revision:1,workspaceId} as Task;tasks.update(v=>[created,...v]);return created})} as unknown as TaskStore;
    const workspace = {selectedId:signal('w1'),workspaces:signal([{id:'w1',name:'Personal'}]),matches:(t:Task)=>t.workspaceId==='w1'} as unknown as WorkspaceService;
    const page=TestBed.runInInjectionContext(()=>new TasksPage(store,workspace,api));await page.loadStatuses();page.title=' daily REVIEW ';
    await page.add();expect(store.create).not.toHaveBeenCalled();expect(page.duplicateWarning()).toBe('daily REVIEW');
    await page.add(true);expect(store.create).toHaveBeenCalledWith('daily REVIEW','w1');expect(page.tasks()).toHaveLength(2);
  });

  it('does not warn when the same title only exists as done', async () => {
    const tasks=signal<Task[]>([{id:'done',title:'Review',statusId:'done',priority:'NONE',revision:1,workspaceId:'w1'}]);
    const store={tasks,status:signal(''),load:vi.fn(),create:vi.fn().mockResolvedValue({id:'new',title:'Review',statusId:'todo',priority:'NONE',revision:1,workspaceId:'w1'})} as unknown as TaskStore;
    const workspace={selectedId:signal('w1'),workspaces:signal([{id:'w1',name:'Personal'}]),matches:(t:Task)=>t.workspaceId==='w1'} as unknown as WorkspaceService;
    const page=TestBed.runInInjectionContext(()=>new TasksPage(store,workspace,api));page.title='Review';await page.add();
    expect(store.create).toHaveBeenCalled();expect(page.duplicateWarning()).toBe('');
  });

  it('disables save until a valid field changes and keeps drafts when closed', async () => {
    const task={id:'edit',title:'Original',statusId:'todo',priority:'NONE',revision:1,workspaceId:'w1'} as Task;
    const store={tasks:signal([task]),status:signal(''),load:vi.fn(),update:vi.fn()} as unknown as TaskStore;
    const workspace={selectedId:signal('w1'),workspaces:signal([]),matches:()=>true} as unknown as WorkspaceService;
    const page=TestBed.runInInjectionContext(()=>new TasksPage(store,workspace,api));await page.loadStatuses();
    expect(page.canSave(task)).toBe(false);page.draft(task).title=' Changed ';expect(page.canSave(task)).toBe(true);
    page.toggleEditor(task);page.closeEditor(task);expect(page.editing()).toBe('');expect(page.draft(task).title).toBe(' Changed ');
  });

  it('opens one deadline calendar, selects, clears and saves without replacing drafts', async () => {
    const task = {id:'date-task',title:'Date',statusId:'todo',priority:'NONE',revision:1,dueDate:'2026-11-24',workspaceId:'w1'} as Task;
    const label = {id:'label',workspaceId:'w1',name:'Work',color:'#000000'};
    const update = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({providers:[provideRouter([]),
      {provide:TaskStore,useValue:{tasks:signal([task]),status:signal(''),load:vi.fn(),update}},
      {provide:WorkspaceService,useValue:{selectedId:signal('w1'),workspaces:signal([]),matches:()=>true}},
      {provide:Api,useValue:{...api,labels:()=>of([label]),taskLabels:()=>of([label])}},
    ]});
    const fixture = TestBed.createComponent(TasksPage);
    const page = fixture.componentInstance;
    page.setView('list');
    fixture.detectChanges(); await fixture.whenStable();
    page.toggleEditor(task);
    fixture.detectChanges(); await fixture.whenStable();
    const picker = fixture.debugElement.query(By.directive(TaskLabelPickerComponent));
    expect(picker.nativeElement.closest('fieldset.editor')?.id).toBe('editor-date-task');
    expect(picker.componentInstance.selected()).toEqual(['label']);
    const draft = page.draft(task);
    const calendar = fixture.debugElement.query(By.directive(MatDatepicker)).componentInstance as MatDatepicker<Date>;
    expect(fixture.debugElement.queryAll(By.directive(MatDatepicker))).toHaveLength(1);
    const model = page.dueDate(draft);
    fixture.detectChanges(); await fixture.whenStable();
    expect(page.dueDate(draft)).toBe(model);
    calendar.open(); fixture.detectChanges(); await fixture.whenStable();
    expect(calendar.opened).toBe(true);
    calendar.select(new Date(2026,11,3)); calendar.close();
    fixture.detectChanges(); await fixture.whenStable();
    expect(page.draft(task)).toBe(draft);
    expect(draft.dueDate).toBe('2026-12-03');
    expect(task.dueDate).toBe('2026-11-24');
    expect(update).not.toHaveBeenCalled();
    await page.save(task);
    expect(update).toHaveBeenLastCalledWith(expect.objectContaining({dueDate:'2026-12-03'}));
    fixture.nativeElement.querySelector('button[aria-label="Clear deadline"]').click();
    fixture.detectChanges(); await fixture.whenStable();
    expect(draft.dueDate).toBe('');
    expect(page.dueDate(draft)).toBeNull();
    await page.save(task);
    expect(update).toHaveBeenLastCalledWith(expect.objectContaining({dueDate:''}));
    page.toggleEditor(task); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-task-label-picker')).toBeNull();
  });

  it('keeps target date changes in the stable draft as LocalDate', () => {
    const task = {id:'date-task',title:'Date',statusId:'todo',priority:'NONE',revision:1,dueDate:'2026-11-24',workspaceId:'w1'} as Task;
    const page = TestBed.runInInjectionContext(() => new TasksPage({tasks:signal([task]),status:signal(''),load:vi.fn()} as unknown as TaskStore,{selectedId:signal('w1'),workspaces:signal([]),matches:()=>true} as unknown as WorkspaceService,api));
    const draft = page.draft(task);
    draft.dueDate = '2026-12-03';
    expect(page.draft(task)).toBe(draft);
    expect(page.draft(task).dueDate).toBe('2026-12-03');
  });

  it('opens one editor at a time and retains drafts when collapsed', () => {
    const store={tasks:signal<Task[]>([]),status:signal(''),load:vi.fn()} as unknown as TaskStore;
    const workspace={selectedId:signal('w1'),workspaces:signal([]),matches:()=>true} as unknown as WorkspaceService;
    const page=TestBed.runInInjectionContext(()=>new TasksPage(store,workspace,api));
    const task={id:'t1',title:'Original',statusId:'todo',revision:1,workspaceId:'w1'} as Task;
    page.toggleEditor(task); expect(page.editing()).toBe('t1');
    page.draft(task).title='Edited'; page.toggleEditor(task); expect(page.editing()).toBe('');
    page.toggleEditor(task); expect(page.draft(task).title).toBe('Edited');
  });

  it('ignores status and label responses from a previous workspace', async () => {
    const oldStatuses=new Subject<typeof defaultStatuses>();
    const oldLabels=new Subject<{id:string;workspaceId:string;name:string;color:string}[]>();
    const selectedId=signal('w1');
    const workspace={selectedId,workspaces:signal([]),matches:()=>true} as unknown as WorkspaceService;
    const store={tasks:signal<Task[]>([]),status:signal(''),load:vi.fn()} as unknown as TaskStore;
    const client={taskStatuses:(id:string)=>id==='w1'?oldStatuses:of([]),labels:(id:string)=>id==='w1'?oldLabels:of([])} as unknown as Api;
    const page=TestBed.runInInjectionContext(()=>new TasksPage(store,workspace,client));
    const statuses=page.loadStatuses(),labels=page.loadLabels(); selectedId.set('w2');
    await Promise.all([page.loadStatuses(),page.loadLabels()]);
    oldStatuses.next(defaultStatuses); oldLabels.next([{id:'l1',workspaceId:'w1',name:'Old',color:'#000000'}]);
    await Promise.all([statuses,labels]); expect(page.statuses()).toEqual([]); expect(page.labels()).toEqual([]);
  });

  describe('Board moves', () => {
    function setup() {
      const task: Task = {id:'t1',title:'Move me',statusId:'todo',priority:'NONE',revision:1,workspaceId:'w1'};
      const tasks = signal<Task[]>([task]);
      const selectedId = signal('w1');
      const response = new Subject<void>();
      const update = vi.fn(async (changed: Task) => {
        await firstValueFrom(response);
        tasks.update(list => list.map(item => item.id === changed.id ? {...changed,revision:2} : item));
      });
      const store = {tasks,status:signal(''),load:vi.fn(),update} as unknown as TaskStore;
      const workspace = {selectedId,workspaces:signal([]),matches:(item:Task)=>item.workspaceId===selectedId()} as unknown as WorkspaceService;
      const page = TestBed.runInInjectionContext(() => new TasksPage(store,workspace,api));
      page.statuses.set([
        {...defaultStatuses[0],category:'UNSTARTED'},
        {...defaultStatuses[0],id:'doing',name:'Doing',category:'STARTED',isDefault:false},
        {...defaultStatuses[0],id:'archived',archived:true,category:'COMPLETED'},
      ]);
      page.statusesReady.set(true);
      return {page,task,tasks,selectedId,response,update};
    }

    it('moves optimistically, then retains the saved column on success', async () => {
      const {page,task,response,update} = setup();
      page.draft(task).title = 'Unsaved draft';
      const pending = page.move(task,'doing');
      expect(update).toHaveBeenCalledExactlyOnceWith({...task,statusId:'doing'});
      expect(page.columnTasks('todo')).toEqual([]);
      expect(page.columnTasks('doing')).toEqual([task]);
      expect(page.saving()).toEqual([task.id]);
      response.next();
      await pending;
      expect(page.columnTasks('todo')).toEqual([]);
      expect(page.columnTasks('doing')).toEqual([{...task,statusId:'doing',revision:2}]);
      expect(page.saving()).toEqual([]);
      expect(page.message()).toBe('Task moved.');
      expect(page.draft(task).title).toBe(task.title);
    });

    it('restores the previous column and retains the draft when saving fails', async () => {
      const {page,task,response,tasks} = setup();
      page.draft(task).title = 'Keep draft';
      const pending = page.move(task,'doing');
      expect(page.columnTasks('doing')).toEqual([task]);
      response.error(new Error('Save failed'));
      await pending;
      expect(tasks()).toEqual([task]);
      expect(page.columnTasks('todo')).toEqual([task]);
      expect(page.columnTasks('doing')).toEqual([]);
      expect(page.saving()).toEqual([]);
      expect(page.message()).toBe('Task could not be moved. Previous status restored.');
      expect(page.draft(task).title).toBe('Keep draft');
    });

    it('makes no request for a same-column selection or drop', async () => {
      const {page,task,update} = setup();
      await page.move(task,'todo');
      const container = {data:'todo'};
      page.drop({previousContainer:container,container,item:{data:task}} as CdkDragDrop<string,string,Task>);
      expect(update).not.toHaveBeenCalled();
      expect(page.columnTasks('todo')).toEqual([task]);
      expect(page.saving()).toEqual([]);
    });

    it('rejects archived targets without changing columns', async () => {
      const {page,task,update} = setup();
      await page.move(task,'archived');
      expect(update).not.toHaveBeenCalled();
      expect(page.columnTasks('todo')).toEqual([task]);
      expect(page.columnTasks('archived')).toEqual([]);
      expect(page.saving()).toEqual([]);
    });

    it.each(['success','failure'])('ignores a previous workspace move %s after switching', async outcome => {
      const {page,task,tasks,selectedId,response,update} = setup();
      const currentTask: Task = {...task,id:'t2',workspaceId:'w2'};
      tasks.update(list => [...list,currentTask]);
      const pending = page.move(task,'doing');
      selectedId.set('w2');
      page.message.set('Current workspace message');
      await page.move(task,'doing');
      expect(update).toHaveBeenCalledTimes(1);
      expect(page.columnTasks('todo')).toEqual([currentTask]);
      expect(page.columnTasks('doing')).toEqual([]);
      if (outcome === 'success') response.next();
      else response.error(new Error('Old workspace save failed'));
      await pending;
      expect(page.message()).toBe('Current workspace message');
      expect(page.columnTasks('todo')).toEqual([currentTask]);
      expect(page.columnTasks('doing')).toEqual([]);
      expect(page.saving()).toEqual([]);
      selectedId.set('w1');
      expect(page.columnTasks(outcome === 'success' ? 'doing' : 'todo')).toHaveLength(1);
    });
  });

  it('does not add when no workspace is selected', async () => {
    const store = {
      tasks: signal<Task[]>([]),
      status: signal(''),
      load: vi.fn(),
      create: vi.fn(),
    } as unknown as TaskStore;

    const workspace = {
      selectedId: signal(''),
      workspaces: signal([]),
      matches: () => false,
    } as unknown as WorkspaceService;

    const page = TestBed.runInInjectionContext(() => new TasksPage(store, workspace, api));
    page.title = 'Orphan task';
    await page.add();

    expect(store.create).not.toHaveBeenCalled();
    expect(page.title).toBe('Orphan task');
  });
});
