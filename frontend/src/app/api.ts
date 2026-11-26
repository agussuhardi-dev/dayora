import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, throwError, tap, switchMap } from 'rxjs';

export interface Workspace { id:string; name:string }
export interface Label { id:string; workspaceId:string; name:string; color:string }
export type TaskStatusCategory = 'UNSTARTED'|'STARTED'|'COMPLETED'|'CANCELED';
export interface TaskStatus { id:string; workspaceId:string; name:string; category:TaskStatusCategory; color:string; position:number; isDefault:boolean; archived:boolean }
export interface Task { id:string; workspaceId?:string; statusId?:string; status?:'TODO'|'IN_PROGRESS'|'DONE'|'CANCELED'|TaskStatus; title:string; notes?:string; priority?:string; dueDate?:string; completedAt?:string; revision:number }
export interface Event { id:string; workspaceId?:string; taskId?:string; title:string; startsAt:string; endsAt:string; timezone:string; category?:string; color?:string; revision:number }
export interface Focus { id:string; workspaceId?:string; state:string; durationSeconds:number; activeDurationSeconds:number; runningSince:string|null; taskId?:string|null; mode?:'NORMAL'|'POMODORO'; phase?:'FOCUS'|'SHORT_BREAK'|'LONG_BREAK'; completedFocusCount?:number; focusDurationMinutes?:number; shortBreakDurationMinutes?:number; longBreakDurationMinutes?:number; cyclesBeforeLongBreak?:number }

@Injectable({providedIn:'root'}) export class Api {
  online = signal(navigator.onLine);
  csrf = signal<{headerName:string;token:string}|null>(null);
  constructor(private http:HttpClient) { addEventListener('online',()=>this.online.set(true)); addEventListener('offline',()=>this.online.set(false)); }
  loadCsrf() { return this.http.get<{headerName:string;token:string}>('/api/auth/csrf').pipe(tap(token=>this.csrf.set(token))); }
  login(username:string,password:string) { const body=new URLSearchParams({username,password}); return this.http.post('/api/auth/login',body.toString(),{headers:{'Content-Type':'application/x-www-form-urlencoded'}}).pipe(switchMap(()=>this.loadCsrf())); }
  register(username:string,password:string,timezone:string) {
    return this.loadCsrf().pipe(
      tap(token => this.csrf.set(token)),
      switchMap(() => this.http.post('/api/auth/register',{username,password,timezone})),
    );
  }
  logout(){ return this.http.post('/api/auth/logout',{}); }
  me(){ return this.http.get<{id:string}>('/api/auth/me').pipe(switchMap(user=>this.loadCsrf().pipe(map(()=>user)))); }
  workspaces(){ return this.http.get<Workspace[]>('/api/workspaces'); }
  createWorkspace(v:{name:string}){ return this.http.post<Workspace>('/api/workspaces',v); }
  tasks(page=0){ return this.http.get<{content:Task[];totalElements:number}>(`/api/tasks?page=${page}&size=100`); }
  createTask(v:Partial<Task>){ return this.http.post<Task>('/api/tasks',v); }
  updateTask(t:Task){ return this.http.put<Task>(`/api/tasks/${t.id}?baseRevision=${t.revision}`,t); }
  deleteTask(t:Task){ return this.http.delete(`/api/tasks/${t.id}?baseRevision=${t.revision}`); }
  taskStatuses(workspaceId:string){ return this.http.get<TaskStatus[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses`); }
  createTaskStatus(workspaceId:string,v:Pick<TaskStatus,'name'|'category'|'color'>){ return this.http.post<TaskStatus>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses`,v); }
  updateTaskStatus(workspaceId:string,id:string,v:Partial<Pick<TaskStatus,'name'|'category'|'color'|'isDefault'>>){ return this.http.patch<TaskStatus>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses/${encodeURIComponent(id)}`,v); }
  reorderTaskStatuses(workspaceId:string,statusIds:string[]){ return this.http.post<TaskStatus[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses/reorder`,{statusIds}); }
  archiveTaskStatus(workspaceId:string,id:string,replacementStatusId?:string){ return this.http.post<TaskStatus>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses/${encodeURIComponent(id)}/archive`,replacementStatusId?{replacementStatusId}:{}); }
  restoreTaskStatus(workspaceId:string,id:string){ return this.http.post<TaskStatus>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses/${encodeURIComponent(id)}/restore`,{}); }
  deleteTaskStatus(workspaceId:string,id:string){ return this.http.delete<void>(`/api/workspaces/${encodeURIComponent(workspaceId)}/task-statuses/${encodeURIComponent(id)}`); }
  labels(workspaceId:string){ return this.http.get<Label[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/labels`); }
  createLabel(workspaceId:string,v:{name:string;color:string}){ return this.http.post<Label>(`/api/workspaces/${encodeURIComponent(workspaceId)}/labels`,v); }
  deleteLabel(workspaceId:string,labelId:string){ return this.http.delete(`/api/workspaces/${encodeURIComponent(workspaceId)}/labels/${encodeURIComponent(labelId)}`); }
  taskLabels(taskId:string){ return this.http.get<Label[]>(`/api/tasks/${encodeURIComponent(taskId)}/labels`); }
  setTaskLabels(taskId:string,labelIds:string[]){ return this.http.put<Label[]>(`/api/tasks/${encodeURIComponent(taskId)}/labels`,{labelIds}); }
  events(from:string,to:string,workspaceId?:string){ return this.http.get<Event[]>(`/api/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${workspaceId?'&workspaceId='+encodeURIComponent(workspaceId):''}`); }
  createEvent(v:Partial<Event>){ return this.http.post<Event>('/api/calendar',v); }
  updateEvent(e:Event){ return this.http.put<Event>(`/api/calendar/${e.id}?baseRevision=${e.revision}`,e); }
  deleteEvent(e:Event){ return this.http.delete(`/api/calendar/${e.id}?baseRevision=${e.revision}`); }
  startFocus(durationSeconds:number,taskId?:string,mode:'NORMAL'|'POMODORO'='NORMAL',config?:Pick<Focus,'focusDurationMinutes'|'shortBreakDurationMinutes'|'longBreakDurationMinutes'|'cyclesBeforeLongBreak'>,workspaceId?:string){ return this.http.post<Focus>('/api/focus',{mode, ...(workspaceId ? {workspaceId}:{}), ...(taskId ? {taskId}:{}), ...(mode==='NORMAL' ? {durationSeconds}: config)}); }
  focusAction(id:string,action:'pause'|'resume'|'complete'){ return this.http.post<Focus>(`/api/focus/${id}/${action}`,{}); }
  nextFocus(id:string){ return this.http.post<Focus>(`/api/focus/${id}/next`,{}); }
  focus(id:string){ return this.http.get<Focus>(`/api/focus/${id}`); }
  currentFocus(){ return this.http.get<Focus|null>('/api/focus/current'); }
  focusHistory(workspaceId?:string){ return this.http.get<Focus[]>(`/api/focus/history${workspaceId?'?workspaceId='+encodeURIComponent(workspaceId):''}`); }
}

export const authInterceptor: HttpInterceptorFn = (req,next) => {
  const api = inject(Api);
  req = req.clone({withCredentials:true});
  if (req.method !== 'GET' && req.method !== 'HEAD' && req.url.startsWith('/api/')) {
    const csrf = api.csrf();
    if (csrf) req = req.clone({setHeaders:{[csrf.headerName]:csrf.token}});
  }
  return next(req).pipe(catchError((e:HttpErrorResponse)=>{ if(e.status===401 && !req.url.endsWith('/csrf') && !req.url.endsWith('/login')) location.href='/login'; return throwError(()=>e); }));
};
@Injectable({providedIn:'root'}) export class AuthGuard { constructor(private api:Api,private router:Router){} canActivate():Observable<boolean>{ return this.api.me().pipe(map(()=>true),catchError(()=>{this.router.navigateByUrl('/login');return [false];})); } }
