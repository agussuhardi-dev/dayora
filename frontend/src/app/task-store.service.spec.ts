import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LocalStore } from './local-store';
import { of, Subject, throwError } from 'rxjs';
import { TaskStore } from './task-store.service';
import { Api, Task } from './api';
import { RealtimeService } from './realtime.service';

const task=(id:string):Task=>({id,title:id,revision:1});
describe('TaskStore',()=>{
  it('uses the authenticated account instead of stale cached owner data',async()=>{
    const owner=signal('account-a');const api={online:signal(true),createTask:()=>of({...task('task'),workspaceId:'workspace'})} as unknown as Api;
    const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner} as unknown as LocalStore,{invalidated:new Subject<never>()} as unknown as RealtimeService));
    store.tasks.set([task('old')]);owner.set('account-b');
    await store.create('New','workspace');
    expect(store.tasks().map(t=>t.id)).toEqual(['task']);
  });
  it('loads every paginated task page',async()=>{
    const pages=[{content:Array.from({length:100},(_,i)=>task(String(i))),totalElements:101},{content:[task('100')],totalElements:101}];
    const api={online:signal(true),tasks:vi.fn((page:number)=>of(pages[page]))} as unknown as Api;
    const invalidated=new Subject<never>();const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner:signal('user')} as unknown as LocalStore,{invalidated} as unknown as RealtimeService));
    await store.load();
    expect(api.tasks).toHaveBeenCalledTimes(2);expect(store.tasks()).toHaveLength(101);
  });
  it('creates through REST and immediately appends the returned task',async()=>{
    const created={...task('created'),title:'Ship it',workspaceId:'workspace-1',status:'TODO' as const,priority:'NONE'};
    const api={online:signal(true),createTask:vi.fn().mockReturnValue(of(created))} as unknown as Api;
    const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner:signal('user')} as unknown as LocalStore,{invalidated:new Subject<never>()} as unknown as RealtimeService));
    await expect(store.create('Ship it','workspace-1')).resolves.toEqual(created);
    expect(api.createTask).toHaveBeenCalledWith({title:'Ship it',workspaceId:'workspace-1',priority:'NONE'});
    expect(store.tasks()).toEqual([created]);
  });
  it('does not append a task when REST create fails',async()=>{
    const api={online:signal(true),createTask:vi.fn().mockReturnValue(throwError(()=>({status:403})))} as unknown as Api;
    const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner:signal('user')} as unknown as LocalStore,{invalidated:new Subject<never>()} as unknown as RealtimeService));
    await expect(store.create('Denied','workspace-1')).rejects.toMatchObject({status:403});
    expect(store.tasks()).toEqual([]);
    expect(store.status()).toContain('could not');
  });
  it.each([400,401,403,409,500])('preserves the list on HTTP %i',async(status)=>{
    const api={online:signal(true),tasks:()=>of({content:[task('existing')],totalElements:1}),createTask:()=>throwError(()=>({status}))} as unknown as Api;
    const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner:signal('user')} as unknown as LocalStore,{invalidated:new Subject<never>()} as unknown as RealtimeService));
    await store.load();
    await expect(store.create('New','workspace')).rejects.toMatchObject({status});
    expect(store.tasks().map(t=>t.id)).toEqual(['existing']);
    expect(store.status()).toBeTruthy();
  });
  it('ignores an older load after create succeeds',async()=>{
    const response=new Subject<{content:Task[];totalElements:number}>();
    const api={online:signal(true),tasks:()=>response,createTask:()=>of(task('new'))} as unknown as Api;
    const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner:signal('user')} as unknown as LocalStore,{invalidated:new Subject<never>()} as unknown as RealtimeService));
    const loading=store.load();
    await store.create('new','workspace');
    response.next({content:[],totalElements:0});await loading;
    expect(store.tasks().map(t=>t.id)).toEqual(['new']);
  });
  it('retains the last complete snapshot when a later page fails',async()=>{
    const api={online:signal(true),tasks:vi.fn().mockReturnValueOnce(of({content:[task('old')],totalElements:1}))} as unknown as Api;
    const invalidated=new Subject<never>();const store=TestBed.runInInjectionContext(()=>new TaskStore(api,{owner:signal('user')} as unknown as LocalStore,{invalidated} as unknown as RealtimeService));
    await store.load();api.tasks=vi.fn().mockReturnValueOnce(of({content:[task('new')],totalElements:2})).mockReturnValueOnce(throwError(()=>new Error()));await store.load();
    expect(store.tasks().map(t=>t.id)).toEqual(['old']);expect(store.status()).toContain('could not');
  });
});
