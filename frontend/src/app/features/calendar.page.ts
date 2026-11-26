import { Component, computed, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TaskStore } from '../task-store.service';
import { WorkspaceService } from '../workspace.service';

export interface PlannerEvent {
  id: string;
  workspaceId: string;
  title: string;
  date: string; // YYYY-MM-DD
  startTime: string; // HH:mm
  endTime: string; // HH:mm
  color: string;
  taskId?: string;
  notes?: string;
}

const PRESET_COLORS = [
  '#6366f1', // Indigo
  '#06b6d4', // Cyan
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#ec4899', // Pink
  '#8b5cf6', // Violet
];

@Component({
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  styleUrl: './planning.css',
  styles: [`
    .cal-shell{display:flex;flex-direction:column;gap:18px}
    .cal-topbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:12px 18px;border-radius:18px}
    .cal-nav-group{display:flex;align-items:center;gap:10px}
    .cal-title{margin:0;font-size:1.35rem;font-weight:750;letter-spacing:-.02em;color:var(--dayora-text);min-width:190px}
    .btn-today{min-height:36px;padding:0 14px;border-radius:10px;border:1px solid var(--dayora-glass-border);background:var(--dayora-surface);color:var(--dayora-text);font-weight:600;font-size:.84rem;cursor:pointer;transition:all 160ms}
    .btn-today:hover{background:var(--dayora-hover)}
    .nav-arrow{display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;border-radius:10px;border:1px solid var(--dayora-glass-border);background:var(--dayora-surface);color:var(--dayora-text);cursor:pointer;transition:all 160ms}
    .nav-arrow:hover{background:var(--dayora-hover)}
    .nav-arrow mat-icon{font-size:20px;width:20px;height:20px;line-height:20px}

    .cal-actions{display:flex;align-items:center;gap:10px}
    .view-switcher{display:inline-flex;padding:3px;border-radius:12px;background:var(--dayora-glass-strong);border:1px solid var(--dayora-glass-border);gap:2px}
    .view-btn{min-height:32px;padding:0 14px;border:0;border-radius:9px;background:transparent;color:var(--dayora-muted);font-size:.82rem;font-weight:600;cursor:pointer;transition:all 160ms}
    .view-btn.active{background:var(--dayora-surface);color:var(--dayora-primary-dark);font-weight:700;box-shadow:0 2px 6px rgba(0,0,0,0.07)}
    .btn-create{display:inline-flex;align-items:center;gap:6px;min-height:38px;padding:0 16px;border-radius:12px;border:0;background:linear-gradient(135deg,var(--dayora-primary),var(--dayora-primary-dark));color:#fff;font-size:.85rem;font-weight:650;cursor:pointer;box-shadow:0 4px 12px color-mix(in srgb,var(--dayora-primary) 30%,transparent);transition:all 160ms}
    .btn-create:hover{transform:translateY(-1px);box-shadow:0 6px 16px color-mix(in srgb,var(--dayora-primary) 40%,transparent)}
    .btn-create mat-icon{font-size:18px;width:18px;height:18px;line-height:18px}

    /* Layout Split */
    .cal-layout{display:grid;grid-template-columns:250px minmax(0,1fr);gap:18px;align-items:start}
    @media(max-width:980px){.cal-layout{grid-template-columns:1fr}}

    /* Sidebar Tray */
    .cal-sidebar{padding:18px;border-radius:20px;display:flex;flex-direction:column;gap:18px}
    .side-section-title{margin:0 0 10px;font-size:.78rem;font-weight:800;letter-spacing:.08em;color:var(--dayora-primary);text-transform:uppercase}
    .task-tray-list{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:8px;max-height:340px;overflow-y:auto}
    .task-tray-item{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:9px 12px;border-radius:10px;background:var(--dayora-surface);border:1px solid var(--dayora-glass-border);font-size:.82rem;font-weight:600;color:var(--dayora-text);cursor:pointer;transition:all 160ms}
    .task-tray-item:hover{border-color:var(--dayora-primary);background:color-mix(in srgb,var(--dayora-primary) 6%,var(--dayora-surface));transform:translateX(2px)}
    .tray-schedule-icon{font-size:16px;width:16px;height:16px;color:var(--dayora-muted);opacity:0;transition:opacity 160ms}
    .task-tray-item:hover .tray-schedule-icon{opacity:1;color:var(--dayora-primary)}
    .tray-empty{font-size:.8rem;color:var(--dayora-muted);text-align:center;padding:18px 8px;margin:0}

    /* Month View */
    .month-container{border-radius:20px;padding:16px;overflow:hidden}
    .month-header-row{display:grid;grid-template-columns:repeat(7,1fr);gap:6px;margin-bottom:8px;text-align:center}
    .weekday-label{font-size:.75rem;font-weight:750;letter-spacing:.04em;color:var(--dayora-muted);padding:4px 0}
    .month-grid{display:grid;grid-template-columns:repeat(7,1fr);gap:6px}
    .month-cell{min-height:104px;padding:8px;border-radius:12px;background:var(--dayora-surface);border:1px solid var(--dayora-glass-border);display:flex;flex-direction:column;gap:4px;cursor:pointer;transition:border-color 160ms,background 160ms}
    .month-cell:hover{border-color:color-mix(in srgb,var(--dayora-primary) 40%,var(--dayora-glass-border));background:color-mix(in srgb,var(--dayora-primary) 3%,var(--dayora-surface))}
    .month-cell.outside{opacity:.35;background:transparent}
    .month-cell.is-today{border-color:var(--dayora-primary);background:color-mix(in srgb,var(--dayora-primary) 6%,var(--dayora-surface))}
    .cell-top{display:flex;align-items:center;justify-content:space-between;margin-bottom:2px}
    .cell-num{display:inline-flex;align-items:center;justify-content:center;font-size:.82rem;font-weight:700;color:var(--dayora-text);width:24px;height:24px;border-radius:50%}
    .month-cell.is-today .cell-num{background:var(--dayora-primary);color:#fff}
    .cell-events{display:flex;flex-direction:column;gap:3px;overflow:hidden;flex:1}
    .month-event-chip{display:flex;align-items:center;gap:5px;padding:2px 6px;border-radius:6px;font-size:.72rem;font-weight:600;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;cursor:pointer;transition:transform 140ms,filter 140ms}
    .month-event-chip:hover{filter:brightness(1.1);transform:scale(1.02)}
    .chip-time{font-size:.68rem;opacity:.9;font-weight:500}
    .chip-title{overflow:hidden;text-overflow:ellipsis}

    /* Week View */
    .week-container{border-radius:20px;padding:16px;overflow-x:auto}
    .week-header-row{display:grid;grid-template-columns:56px repeat(7,minmax(120px,1fr));gap:6px;border-bottom:1px solid var(--dayora-glass-border);padding-bottom:10px;margin-bottom:8px}
    .week-col-head{text-align:center;padding:4px 0}
    .week-day-name{font-size:.72rem;font-weight:700;color:var(--dayora-muted);text-transform:uppercase}
    .week-day-num{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;font-size:.92rem;font-weight:750;margin-top:2px;color:var(--dayora-text)}
    .week-col-head.today .week-day-num{background:var(--dayora-primary);color:#fff}
    .week-grid-body{position:relative;display:grid;grid-template-columns:56px repeat(7,minmax(120px,1fr));gap:6px;min-height:580px}
    .time-gutter{display:flex;flex-direction:column;justify-content:space-between;padding-top:2px}
    .gutter-slot{height:46px;font-size:.7rem;font-weight:650;color:var(--dayora-muted);text-align:right;padding-right:10px}
    .week-day-col{position:relative;border-radius:10px;background:color-mix(in srgb,var(--dayora-glass-strong) 40%,transparent);border:1px solid var(--dayora-glass-border);min-height:580px;display:flex;flex-direction:column}
    .week-day-col.today{border-color:color-mix(in srgb,var(--dayora-primary) 35%,var(--dayora-glass-border));background:color-mix(in srgb,var(--dayora-primary) 3%,var(--dayora-glass-strong))}
    .hour-guide{height:46px;border-bottom:1px dashed color-mix(in srgb,var(--dayora-glass-border) 60%,transparent);box-sizing:border-box;cursor:pointer}
    .hour-guide:hover{background:color-mix(in srgb,var(--dayora-primary) 5%,transparent)}
    .week-event-card{position:absolute;left:4px;right:4px;border-radius:8px;padding:6px 8px;color:#fff;overflow:hidden;box-shadow:0 3px 10px rgba(0,0,0,0.12);cursor:pointer;z-index:2;display:flex;flex-direction:column;gap:2px;transition:transform 140ms,filter 140ms}
    .week-event-card:hover{filter:brightness(1.08);transform:translateY(-1px);z-index:4}
    .we-time{font-size:.68rem;opacity:.9;font-weight:600}
    .we-title{font-size:.78rem;font-weight:700;line-height:1.2;overflow:hidden;text-overflow:ellipsis}
    .we-task{font-size:.66rem;opacity:.85;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}

    /* Modal / Dialog */
    .modal-backdrop{position:fixed;inset:0;background:rgba(0,0,0,0.45);backdrop-filter:blur(4px);z-index:999;display:flex;align-items:center;justify-content:center;padding:16px}
    .modal-sheet{width:100%;max-width:440px;border-radius:20px;padding:24px;border:1px solid var(--dayora-glass-border);box-shadow:0 24px 48px rgba(0,0,0,0.22);display:flex;flex-direction:column;gap:16px}
    .modal-head{display:flex;align-items:center;justify-content:space-between}
    .modal-head h3{margin:0;font-size:1.15rem;font-weight:750;letter-spacing:-.02em;color:var(--dayora-text)}
    .modal-close{border:0;background:transparent;color:var(--dayora-muted);cursor:pointer;border-radius:8px;width:32px;height:32px;display:grid;place-items:center}
    .modal-close:hover{background:var(--dayora-hover);color:var(--dayora-text)}
    .color-picker-row{display:flex;align-items:center;gap:10px}
    .color-chip{width:26px;height:26px;border-radius:50%;border:2px solid transparent;cursor:pointer;transition:transform 140ms}
    .color-chip:hover{transform:scale(1.15)}
    .color-chip.selected{border-color:#fff;box-shadow:0 0 0 2px var(--dayora-primary)}
    .modal-actions{display:flex;align-items:center;justify-content:space-between;margin-top:8px}
    .btn-delete{border:0;background:color-mix(in srgb,var(--dayora-danger,#b42318) 10%,transparent);color:var(--dayora-danger,#b42318);border-radius:10px;height:38px;padding:0 14px;font-weight:650;font-size:.82rem;cursor:pointer}
    .btn-delete:hover{background:color-mix(in srgb,var(--dayora-danger,#b42318) 20%,transparent)}
    .modal-actions-right{display:flex;gap:8px;margin-left:auto}

    @media(max-width:700px){
      .cal-topbar{flex-direction:column;align-items:stretch}
      .cal-actions{justify-content:space-between}
      .month-cell{min-height:74px;padding:4px}
      .month-event-chip .chip-time{display:none}
    }
  `],
  template: `
  <main class="planning-page cal-page">
    <header class="heading">
      <div>
        <p class="eyebrow">PLANNER · {{workspaceName()}}</p>
        <h1>Shape your <em>schedule.</em></h1>
        <p class="subtitle">Timeboxing, calendar scheduling, and calm commitments.</p>
      </div>
    </header>

    <div class="cal-shell">
      <!-- Topbar Navigator -->
      <section class="cal-topbar glass-surface">
        <div class="cal-nav-group">
          <button type="button" class="btn-today" (click)="goToday()">Today</button>
          <button type="button" class="nav-arrow" (click)="prev()" aria-label="Previous period"><mat-icon>chevron_left</mat-icon></button>
          <button type="button" class="nav-arrow" (click)="next()" aria-label="Next period"><mat-icon>chevron_right</mat-icon></button>
          <h2 class="cal-title">{{periodTitle()}}</h2>
        </div>

        <div class="cal-actions">
          <div class="view-switcher" role="group" aria-label="Calendar view switcher">
            <button type="button" class="view-btn" [class.active]="viewMode()==='month'" (click)="viewMode.set('month')">Month</button>
            <button type="button" class="view-btn" [class.active]="viewMode()==='week'" (click)="viewMode.set('week')">Week</button>
          </div>
          <button type="button" class="btn-create" (click)="openCreateModal()">
            <mat-icon>add</mat-icon>
            <span>New Event</span>
          </button>
        </div>
      </section>

      <!-- Calendar Layout Split -->
      <div class="cal-layout">
        <!-- Sidebar Tray: Unscheduled Tasks -->
        <aside class="cal-sidebar glass-surface">
          <div>
            <h3 class="side-section-title">Planner Backlog</h3>
            <p class="muted" style="font-size:0.78rem;margin:0 0 12px">Click any task to schedule it directly into your calendar.</p>
            <ul class="task-tray-list">
              @for(t of unscheduledTasks();track t.id){
                <li class="task-tray-item" (click)="scheduleTask(t)">
                  <span>{{t.title}}</span>
                  <mat-icon class="tray-schedule-icon">event_available</mat-icon>
                </li>
              } @empty {
                <li class="tray-empty">All workspace tasks are scheduled or completed!</li>
              }
            </ul>
          </div>
        </aside>

        <!-- Main View (Month or Week) -->
        <section class="cal-main">
          @if(viewMode()==='month'){
            <div class="month-container glass-surface">
              <div class="month-header-row">
                @for(day of ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];track day){
                  <div class="weekday-label">{{day}}</div>
                }
              </div>

              <div class="month-grid">
                @for(cell of monthCells();track cell.dateStr){
                  <div class="month-cell" [class.outside]="!cell.inCurrentMonth" [class.is-today]="cell.isToday" (click)="openCreateModal(cell.dateStr)">
                    <div class="cell-top">
                      <span class="cell-num">{{cell.dayNum}}</span>
                    </div>
                    <div class="cell-events">
                      @for(ev of cell.events;track ev.id){
                        <div class="month-event-chip" [style.background]="ev.color" (click)="$event.stopPropagation(); openEditModal(ev)">
                          <span class="chip-time">{{ev.startTime}}</span>
                          <span class="chip-title">{{ev.title}}</span>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            </div>
          } @else {
            <div class="week-container glass-surface">
              <div class="week-header-row">
                <div></div>
                @for(d of weekDays();track d.dateStr){
                  <div class="week-col-head" [class.today]="d.isToday">
                    <div class="week-day-name">{{d.dayName}}</div>
                    <div class="week-day-num">{{d.dayNum}}</div>
                  </div>
                }
              </div>

              <div class="week-grid-body">
                <div class="time-gutter">
                  @for(h of hours;track h){
                    <div class="gutter-slot">{{h}}:00</div>
                  }
                </div>

                @for(d of weekDays();track d.dateStr){
                  <div class="week-day-col" [class.today]="d.isToday">
                    @for(h of hours;track h){
                      <div class="hour-guide" (click)="openCreateModal(d.dateStr, h + ':00')" [attr.title]="'Schedule at ' + h + ':00'"></div>
                    }

                    @for(ev of eventsOnDate(d.dateStr);track ev.id){
                      <div class="week-event-card" [style.background]="ev.color" [style.top.px]="eventTop(ev.startTime)" [style.height.px]="eventHeight(ev.startTime, ev.endTime)" (click)="$event.stopPropagation(); openEditModal(ev)">
                        <span class="we-time">{{ev.startTime}} – {{ev.endTime}}</span>
                        <span class="we-title">{{ev.title}}</span>
                        @if(ev.taskId && taskName(ev.taskId)){
                          <span class="we-task">📌 {{taskName(ev.taskId)}}</span>
                        }
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          }
        </section>
      </div>
    </div>

    <!-- Event Create / Edit Modal -->
    @if(modalOpen()){
      <div class="modal-backdrop" (click)="closeModal()">
        <div class="modal-sheet glass-surface" (click)="$event.stopPropagation()">
          <div class="modal-head">
            <h3>{{editingId() ? 'Edit Event' : 'New Event'}}</h3>
            <button type="button" class="modal-close" (click)="closeModal()" aria-label="Close modal">
              <mat-icon>close</mat-icon>
            </button>
          </div>

          <form (ngSubmit)="saveModal()">
            <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
              <mat-label>Event title</mat-label>
              <input matInput [(ngModel)]="modalTitle" name="title" required placeholder="Meeting, Deep Work, Review…">
            </mat-form-field>

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Start time</mat-label>
                <input matInput type="time" [(ngModel)]="modalStart" name="start" required>
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>End time</mat-label>
                <input matInput type="time" [(ngModel)]="modalEnd" name="end" required>
              </mat-form-field>
            </div>

            <div style="margin-top:12px">
              <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
                <mat-label>Link to task (optional)</mat-label>
                <mat-select [(ngModel)]="modalTaskId" name="taskId">
                  <mat-option value="">None</mat-option>
                  @for(t of availableTasks();track t.id){
                    <mat-option [value]="t.id">{{t.title}}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </div>

            <div style="margin-top:12px">
              <label style="font-size:0.75rem;font-weight:700;color:var(--dayora-muted);display:block;margin-bottom:6px">Theme color</label>
              <div class="color-picker-row">
                @for(c of presetColors;track c){
                  <button type="button" class="color-chip" [style.background]="c" [class.selected]="modalColor===c" (click)="modalColor=c" [attr.aria-label]="'Color ' + c"></button>
                }
              </div>
            </div>

            <div class="modal-actions">
              @if(editingId()){
                <button type="button" class="btn-delete" (click)="deleteEvent(editingId()!)">Delete</button>
              }
              <div class="modal-actions-right">
                <button mat-button type="button" (click)="closeModal()">Cancel</button>
                <button mat-flat-button class="primary" type="submit" [disabled]="!modalTitle.trim()">Save Event</button>
              </div>
            </div>
          </form>
        </div>
      </div>
    }
  </main>
  `
})
export class CalendarPage {
  viewMode = signal<'month' | 'week'>('month');
  cursorDate = signal<Date>(new Date());
  events = signal<PlannerEvent[]>([]);

  // Modal State
  modalOpen = signal(false);
  editingId = signal<string | null>(null);
  modalTitle = '';
  modalDate = '';
  modalStart = '09:00';
  modalEnd = '10:00';
  modalColor = PRESET_COLORS[0];
  modalTaskId = '';

  presetColors = PRESET_COLORS;
  hours = Array.from({ length: 13 }, (_, i) => i + 8); // 8:00 to 20:00

  private storageKey = computed(() => `dayora_planner_events_${this.workspace.selectedId() || 'default'}`);

  constructor(public workspace: WorkspaceService, public store: TaskStore) {
    void this.store.load();

    effect(() => {
      const key = this.storageKey();
      const raw = localStorage.getItem(key);
      if (raw) {
        try {
          this.events.set(JSON.parse(raw));
        } catch {
          this.events.set([]);
        }
      } else {
        // Seed default sample events for demonstration
        const todayStr = this.formatDate(new Date());
        const sample: PlannerEvent[] = [
          {
            id: 'ev-1',
            workspaceId: this.workspace.selectedId(),
            title: 'Weekly Sprint Kickoff',
            date: todayStr,
            startTime: '09:00',
            endTime: '10:00',
            color: '#6366f1'
          },
          {
            id: 'ev-2',
            workspaceId: this.workspace.selectedId(),
            title: 'Deep Work & Feature Planning',
            date: todayStr,
            startTime: '13:00',
            endTime: '15:00',
            color: '#10b981'
          }
        ];
        this.events.set(sample);
        localStorage.setItem(key, JSON.stringify(sample));
      }
    });
  }

  workspaceName() {
    return this.workspace.workspaces().find(w => w.id === this.workspace.selectedId())?.name || 'Workspace';
  }

  availableTasks() {
    return this.store.tasks().filter(t => this.workspace.matches(t) && !t.completedAt);
  }

  unscheduledTasks() {
    const scheduledIds = new Set(this.events().map(e => e.taskId).filter(Boolean));
    return this.availableTasks().filter(t => !scheduledIds.has(t.id));
  }

  taskName(id: string) {
    return this.store.tasks().find(t => t.id === id)?.title || '';
  }

  // Navigation
  goToday() {
    this.cursorDate.set(new Date());
  }

  prev() {
    const d = new Date(this.cursorDate());
    if (this.viewMode() === 'month') {
      d.setMonth(d.getMonth() - 1);
    } else {
      d.setDate(d.getDate() - 7);
    }
    this.cursorDate.set(d);
  }

  next() {
    const d = new Date(this.cursorDate());
    if (this.viewMode() === 'month') {
      d.setMonth(d.getMonth() + 1);
    } else {
      d.setDate(d.getDate() + 7);
    }
    this.cursorDate.set(d);
  }

  periodTitle() {
    const d = this.cursorDate();
    if (this.viewMode() === 'month') {
      return d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
    }
    const days = this.weekDays();
    if (days.length === 0) return '';
    const first = days[0].date;
    const last = days[6].date;
    if (first.getMonth() === last.getMonth()) {
      return `${first.toLocaleDateString('en-US', { month: 'short' })} ${first.getDate()} – ${last.getDate()}, ${first.getFullYear()}`;
    }
    return `${first.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} – ${last.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`;
  }

  // Month View Calculations
  monthCells = computed(() => {
    const cursor = this.cursorDate();
    const year = cursor.getFullYear();
    const month = cursor.getMonth();
    const firstDayOfMonth = new Date(year, month, 1);
    const lastDayOfMonth = new Date(year, month + 1, 0);

    // Monday as 0, Sunday as 6
    let startDay = firstDayOfMonth.getDay() - 1;
    if (startDay < 0) startDay = 6;

    const cells: { dateStr: string; dayNum: number; inCurrentMonth: boolean; isToday: boolean; events: PlannerEvent[] }[] = [];
    const todayStr = this.formatDate(new Date());

    // Previous month filler days
    const prevMonthLastDate = new Date(year, month, 0).getDate();
    for (let i = startDay - 1; i >= 0; i--) {
      const d = new Date(year, month - 1, prevMonthLastDate - i);
      const dateStr = this.formatDate(d);
      cells.push({
        dateStr,
        dayNum: d.getDate(),
        inCurrentMonth: false,
        isToday: dateStr === todayStr,
        events: this.eventsOnDate(dateStr)
      });
    }

    // Current month days
    for (let d = 1; d <= lastDayOfMonth.getDate(); d++) {
      const date = new Date(year, month, d);
      const dateStr = this.formatDate(date);
      cells.push({
        dateStr,
        dayNum: d,
        inCurrentMonth: true,
        isToday: dateStr === todayStr,
        events: this.eventsOnDate(dateStr)
      });
    }

    // Next month filler days to complete 35 or 42 grid cells
    const remaining = (7 - (cells.length % 7)) % 7;
    for (let d = 1; d <= remaining; d++) {
      const date = new Date(year, month + 1, d);
      const dateStr = this.formatDate(date);
      cells.push({
        dateStr,
        dayNum: d,
        inCurrentMonth: false,
        isToday: dateStr === todayStr,
        events: this.eventsOnDate(dateStr)
      });
    }

    return cells;
  });

  // Week View Calculations
  weekDays = computed(() => {
    const cursor = new Date(this.cursorDate());
    let day = cursor.getDay() - 1;
    if (day < 0) day = 6;
    const monday = new Date(cursor);
    monday.setDate(cursor.getDate() - day);

    const todayStr = this.formatDate(new Date());
    const days: { date: Date; dateStr: string; dayName: string; dayNum: number; isToday: boolean }[] = [];

    for (let i = 0; i < 7; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      const dateStr = this.formatDate(d);
      days.push({
        date: d,
        dateStr,
        dayName: d.toLocaleDateString('en-US', { weekday: 'short' }),
        dayNum: d.getDate(),
        isToday: dateStr === todayStr
      });
    }
    return days;
  });

  eventsOnDate(dateStr: string) {
    return this.events().filter(e => e.date === dateStr);
  }

  // Positioning for Week View (hours start at 8:00 AM, slot height is 46px)
  eventTop(startTime: string): number {
    const [h, m] = startTime.split(':').map(Number);
    const startOffsetHours = (h - 8) + (m / 60);
    return Math.max(0, startOffsetHours * 46);
  }

  eventHeight(startTime: string, endTime: string): number {
    const [sh, sm] = startTime.split(':').map(Number);
    const [eh, em] = endTime.split(':').map(Number);
    const durationHours = Math.max(0.5, (eh + em / 60) - (sh + sm / 60));
    return Math.max(26, durationHours * 46);
  }

  // Modal Actions
  openCreateModal(dateStr?: string, startTime = '09:00') {
    this.editingId.set(null);
    this.modalTitle = '';
    this.modalDate = dateStr || this.formatDate(this.cursorDate());
    this.modalStart = startTime;
    const [h, m] = startTime.split(':').map(Number);
    const endH = String(Math.min(23, h + 1)).padStart(2, '0');
    this.modalEnd = `${endH}:${String(m).padStart(2, '0')}`;
    this.modalColor = PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)];
    this.modalTaskId = '';
    this.modalOpen.set(true);
  }

  openEditModal(ev: PlannerEvent) {
    this.editingId.set(ev.id);
    this.modalTitle = ev.title;
    this.modalDate = ev.date;
    this.modalStart = ev.startTime;
    this.modalEnd = ev.endTime;
    this.modalColor = ev.color;
    this.modalTaskId = ev.taskId || '';
    this.modalOpen.set(true);
  }

  scheduleTask(task: { id: string; title: string }) {
    this.openCreateModal(this.formatDate(this.cursorDate()));
    this.modalTitle = task.title;
    this.modalTaskId = task.id;
  }

  closeModal() {
    this.modalOpen.set(false);
  }

  saveModal() {
    if (!this.modalTitle.trim()) return;

    const current = [...this.events()];
    const eventId = this.editingId();

    if (eventId) {
      const idx = current.findIndex(e => e.id === eventId);
      if (idx !== -1) {
        current[idx] = {
          ...current[idx],
          title: this.modalTitle.trim(),
          date: this.modalDate,
          startTime: this.modalStart,
          endTime: this.modalEnd,
          color: this.modalColor,
          taskId: this.modalTaskId || undefined
        };
      }
    } else {
      current.push({
        id: 'ev_' + Date.now(),
        workspaceId: this.workspace.selectedId(),
        title: this.modalTitle.trim(),
        date: this.modalDate,
        startTime: this.modalStart,
        endTime: this.modalEnd,
        color: this.modalColor,
        taskId: this.modalTaskId || undefined
      });
    }

    this.events.set(current);
    localStorage.setItem(this.storageKey(), JSON.stringify(current));
    this.modalOpen.set(false);
  }

  deleteEvent(id: string) {
    const next = this.events().filter(e => e.id !== id);
    this.events.set(next);
    localStorage.setItem(this.storageKey(), JSON.stringify(next));
    this.modalOpen.set(false);
  }

  private formatDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
