import { Component, effect, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { WorkspaceService } from '../../workspace.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Api, Event, Focus, Task } from '../../api';
import { TaskStore } from '../../task-store.service';
import { RealtimeService } from '../../realtime.service';
import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  standalone: true,
  imports: [RouterLink, MatIconModule],
  template: `<main class="today-page">
    <header class="welcome">
      <div>
        <div class="welcome-meta">
          <span class="today-date">{{today}}</span>
          <span class="workspace-badge">{{workspaceName()}}</span>
        </div>
        <h1>{{greeting()}}</h1>
      </div>
      <div class="day-summary glass-surface">
        <span><strong>{{activeTasks().length}}</strong> tasks</span>
        <i></i>
        <span><strong>{{events().length}}</strong> events</span>
      </div>
    </header>
    @if (loading()) { <p class="state glass-surface" role="status"><mat-icon>hourglass_top</mat-icon> Loading…</p> }
    @if (error()) { <p class="state error glass-surface" role="alert">{{error()}} <button (click)="load()">Try again</button></p> }
    @if (!loading() && !error()) {
      <section class="hero glass-surface" aria-labelledby="focus-title">
        <div class="hero-left">
          <span class="status-dot" [class.active]="focus()"></span>
          <div>
            <h2 id="focus-title">{{focus() ? 'Focus active' : 'Focus session'}}</h2>
            <p>{{focus() ? (focusLabel() + (focusOutsideWorkspace() ? ' · ' + focusWorkspaceName() : '')) : 'Ready when you are'}}</p>
          </div>
        </div>
        <a class="primary-cta" routerLink="/focus">
          <mat-icon>{{focus() ? 'play_arrow' : 'timer'}}</mat-icon>
          {{focus() ? 'Continue' : 'Start'}}
        </a>
      </section>
      <div class="dashboard-grid">
        <section class="card glass-surface" aria-labelledby="tasks-title">
          <div class="card-head">
            <h3 id="tasks-title"><mat-icon>checklist</mat-icon> Tasks</h3>
            <a routerLink="/tasks">View all <mat-icon>arrow_forward</mat-icon></a>
          </div>
          @if(activeTasks().length){
            <ul class="task-list">
              @for(t of activeTasks().slice(0, 5); track t.id){
                <li><span class="check"></span><span>{{t.title}}</span></li>
              }
            </ul>
          }@else{
            <div class="empty"><p>No tasks for today</p></div>
          }
        </section>
        <section class="card glass-surface" aria-labelledby="events-title">
          <div class="card-head">
            <h3 id="events-title"><mat-icon>calendar_month</mat-icon> Schedule</h3>
            <a routerLink="/calendar">View all <mat-icon>arrow_forward</mat-icon></a>
          </div>
          @if(events().length){
            <ul class="event-list">
              @for(e of events().slice(0, 5); track e.id){
                <li><time>{{eventTime(e)}}</time><span>{{e.title}}</span></li>
              }
            </ul>
          }@else{
            <div class="empty"><p>No events today</p></div>
          }
        </section>
      </div>
    }
  </main>`,
  styles: [`
    :host { display: block; }
    .today-page { max-width: 1040px; margin: auto; padding: 36px 8px 60px; }
    .welcome { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin: 0 4px 24px; }
    .welcome-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
    .today-date { color: var(--dayora-primary); font-size: .8rem; font-weight: 700; }
    .workspace-badge { display: inline-block; padding: 2px 9px; border-radius: 99px; background: var(--dayora-surface); color: var(--dayora-muted); font-size: .75rem; font-weight: 600; }
    .welcome h1 { margin: 0; font-size: clamp(1.8rem, 4vw, 2.6rem); letter-spacing: -.04em; line-height: 1.1; }
    .day-summary { display: flex; align-items: center; gap: 12px; padding: 10px 16px; border-radius: 16px; color: var(--dayora-muted); font-size: .82rem; white-space: nowrap; }
    .day-summary strong { color: var(--dayora-text); font-size: 1.05rem; }
    .day-summary i { width: 4px; height: 4px; border-radius: 50%; background: var(--dayora-primary); }

    .hero { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 18px 24px; border-radius: 20px; background: linear-gradient(125deg, color-mix(in srgb, var(--dayora-primary) 12%, transparent), var(--dayora-glass)); box-shadow: var(--dayora-glass-shadow); margin-bottom: 24px; }
    .hero-left { display: flex; align-items: center; gap: 14px; min-width: 0; }
    .status-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--dayora-muted); flex-shrink: 0; }
    .status-dot.active { background: #57b582; box-shadow: 0 0 0 4px #57b58225; }
    .hero h2 { margin: 0; font-size: 1.15rem; letter-spacing: -.02em; }
    .hero p { margin: 2px 0 0; color: var(--dayora-muted); font-size: .82rem; }
    .primary-cta { display: inline-flex; align-items: center; gap: 6px; padding: 9px 18px; border-radius: 12px; background: var(--dayora-primary); color: white; text-decoration: none; font-size: .85rem; font-weight: 700; flex-shrink: 0; }
    .primary-cta mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .dashboard-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
    .card { padding: 22px 24px; border-radius: 20px; box-shadow: var(--dayora-glass-shadow); min-height: 240px; }
    .card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .card-head h3 { margin: 0; font-size: 1.15rem; letter-spacing: -.02em; display: inline-flex; align-items: center; gap: 8px; }
    .card-head h3 mat-icon { font-size: 20px; width: 20px; height: 20px; color: var(--dayora-primary); }
    .card-head a { display: inline-flex; align-items: center; gap: 4px; color: var(--dayora-primary); font-size: .78rem; font-weight: 700; text-decoration: none; }
    .card-head a mat-icon { font-size: 15px; width: 15px; height: 15px; }

    .task-list, .event-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
    .task-list li, .event-list li { display: flex; align-items: center; gap: 10px; min-width: 0; font-size: .88rem; }
    .task-list li span:last-child, .event-list li span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .check { width: 14px; height: 14px; flex-shrink: 0; border: 1.5px solid var(--dayora-primary); border-radius: 50%; }
    .event-list time { min-width: 60px; color: var(--dayora-primary); font-size: .78rem; font-weight: 700; }
    .empty { padding: 32px 0; text-align: center; color: var(--dayora-muted); font-size: .85rem; }
    .empty p { margin: 0; }

    .state { display: flex; align-items: center; gap: 10px; margin: 20px 0; padding: 16px; border-radius: 16px; color: var(--dayora-muted); }
    .state mat-icon { color: var(--dayora-primary); }
    .error { color: #9b3d4b; }
    .state button { margin-left: auto; padding: 6px 12px; border: 0; border-radius: 8px; }

    @media (max-width: 720px) {
      .welcome { flex-direction: column; align-items: flex-start; }
      .day-summary { align-self: stretch; justify-content: center; }
      .hero { flex-direction: column; align-items: flex-start; }
      .primary-cta { align-self: stretch; justify-content: center; }
      .dashboard-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class TodayPage {
  readonly today = new Intl.DateTimeFormat('en-US',{day:'numeric',month:'long',year:'numeric'}).format(new Date());
  readonly tasks=signal<Task[]>([]); readonly events=signal<Event[]>([]); readonly focus=signal<Focus|null>(null); readonly loading=signal(true); readonly error=signal(''); private request=0;
  constructor(private api:Api, public workspace:WorkspaceService, private store:TaskStore, realtime:RealtimeService, destroy:DestroyRef){
    realtime.invalidated.pipe(takeUntilDestroyed(destroy)).subscribe(()=>this.load());
    effect(onCleanup=>{const id=workspace.selectedId(),request=++this.request;onCleanup(()=>this.request++);this.load(id,request);});
    void workspace.open();
  }
  greeting(){const h=new Date().getHours();return h<12?'Good morning':h<18?'Good afternoon':'Good evening'}
  workspaceName(){return this.workspace.workspaces().find(w=>w.id===this.workspace.selectedId())?.name||'No workspace selected'}
  focusWorkspaceName(){const id=this.focus()?.workspaceId;return this.workspace.workspaces().find(w=>w.id===id)?.name||'another workspace'}
  focusOutsideWorkspace(){const id=this.focus()?.workspaceId;return !!id&&id!==this.workspace.selectedId()}
  activeTasks(){return this.tasks().filter(t=>(t.status||'TODO')==='TODO'||t.status==='IN_PROGRESS')}
  private current(id:string,request:number){return this.request===request&&this.workspace.selectedId()===id}
  load(id=this.workspace.selectedId(),request=++this.request){
    this.loading.set(true);this.error.set('');this.tasks.set([]);this.events.set([]);if(!id){this.focus.set(null);this.loading.set(false);return;}
    const start=new Date();start.setHours(0,0,0,0);const end=new Date(start);end.setDate(end.getDate()+1);
    void this.store.load().then(()=>{if(!this.current(id,request))return;this.tasks.set(this.store.tasks().filter(t=>this.workspace.matches(t)));if(this.store.status().startsWith('Tasks could'))this.error.set(this.store.status());});
    forkJoin({events:this.api.events(start.toISOString(),end.toISOString(),id).pipe(catchError(()=>of([]))),focus:this.api.currentFocus().pipe(catchError(()=>of(null)))}).subscribe({next:r=>{if(!this.current(id,request))return;this.events.set(r.events.filter(e=>this.workspace.matches(e)).sort((a,b)=>Date.parse(a.startsAt)-Date.parse(b.startsAt)));this.focus.set(r.focus);this.loading.set(false)},error:()=>{if(this.current(id,request)){this.error.set('Today could not be loaded.');this.loading.set(false)}}});
  }
  focusLabel(){const s=this.focus();if(!s)return '';return s.state==='RUNNING'?'Session running':s.state==='PAUSED'?'Session paused':s.state}
  eventTime(e:Event){return new Intl.DateTimeFormat('en-US',{hour:'numeric',minute:'2-digit'}).format(new Date(e.startsAt))}
}
