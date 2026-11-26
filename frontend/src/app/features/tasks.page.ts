import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { TaskStore } from '../task-store.service';
import { Api, Label, Task as TaskModel, TaskStatus } from '../api';
import { firstValueFrom } from 'rxjs';
import { TaskLabelPickerComponent } from './task-label-picker.component';
import { WorkspaceService } from '../workspace.service';
import { TaskDeleteDialogComponent } from './task-delete-dialog.component';

@Component({
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatDialogModule, DragDropModule, TaskLabelPickerComponent],
  styleUrl: './planning.css',
  styles: [`
    mat-form-field{--mat-form-field-outlined-container-shape:12px;--mat-form-field-outlined-outline-color:var(--dayora-glass-border);--mat-form-field-outlined-focus-outline-color:var(--dayora-primary);background:var(--dayora-glass-strong);border-radius:12px}
    input[matInput],textarea[matInput]{box-sizing:border-box;width:100%;padding:0;border:0;border-radius:0;background:transparent;height:auto;font:inherit}
    .task-form{align-items:center}
    .sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
    .editor{margin-top:0;padding:18px 20px;border:0;border-top:1px solid var(--dayora-glass-border);border-radius:0 0 14px 14px;background:color-mix(in srgb,var(--dayora-glass-strong) 45%,transparent)}
    .editor .task-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;align-items:start}
    .task-grid .wide,.task-grid .full,.editor-labels{grid-column:1/-1}
    .editor-labels{padding-top:12px;border-top:1px solid var(--dayora-glass-border)}
    .editor-labels app-task-label-picker{margin-top:8px}
    .editor-actions{display:flex;justify-content:space-between;align-items:center;margin-top:16px}
    .editor-actions-right{display:flex;gap:8px}
    .delete-task{display:inline-flex;align-items:center;justify-content:center;width:38px;height:38px;padding:0;border:0;border-radius:10px;background:color-mix(in srgb,var(--dayora-danger,#b42318) 8%,transparent);color:var(--dayora-danger,#b42318);cursor:pointer;transition:all 160ms}
    .delete-task:hover{background:color-mix(in srgb,var(--dayora-danger,#b42318) 18%,transparent);transform:translateY(-1px)}
    .delete-task mat-icon{font-size:20px;width:20px;height:20px}
    @media(max-width:800px){.editor .task-grid{grid-template-columns:1fr 1fr}}
    @media(max-width:520px){.editor .task-grid{grid-template-columns:1fr}}

    :host .add-task-form{grid-template-columns:minmax(0,1fr) auto;gap:14px;padding:14px;border:1px solid var(--dayora-glass-border);border-radius:18px;background:color-mix(in srgb,var(--dayora-glass-strong) 78%,transparent)}
    :host .add-task-form mat-form-field{width:100%;margin:0}
    :host .add-task-form .primary{min-height:56px;padding-inline:22px;border:0;border-radius:14px;background:linear-gradient(135deg,var(--dayora-primary),var(--dayora-primary-dark));box-shadow:0 9px 18px color-mix(in srgb,var(--dayora-primary) 28%,transparent);color:#fff;white-space:nowrap}
    :host .add-task-form .primary:hover:not(:disabled){transform:translateY(-1px)}
    @media(max-width:560px){:host .add-task-form{grid-template-columns:1fr;padding:12px}:host .add-task-form .primary{width:100%}}

    .tasks-page .heading{align-items:center;margin-bottom:20px}
    .tasks-page .eyebrow{margin-bottom:7px;letter-spacing:.14em}
    .tasks-page .heading h1{font-size:clamp(1.9rem,3.5vw,2.65rem);line-height:1.12}
    .tasks-page .subtitle{margin-top:7px;font-size:.92rem}

    .view-toggle{display:inline-flex;max-width:100%;gap:3px;padding:4px;margin-bottom:16px;border:1px solid var(--dayora-glass-border);border-radius:14px;background:var(--dayora-glass-strong)}
    .view-toggle button{min-width:82px;min-height:40px;padding:0 18px;border:0;border-radius:10px;background:transparent;color:var(--dayora-muted);transition:all 180ms}
    .view-toggle button[aria-pressed="true"]{background:var(--dayora-surface);color:var(--dayora-primary-dark);box-shadow:0 2px 7px #00000014}
    .view-toggle button:focus-visible,.task-title:focus-visible{outline:2px solid var(--dayora-primary);outline-offset:2px}

    .task-title{display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow-wrap:anywhere;border:0;border-radius:5px;background:transparent;color:var(--dayora-text);font-size:.96rem;font-weight:650;line-height:1.4;transition:color 180ms}
    .task-title:hover,.task-title[aria-expanded="true"]{color:var(--dayora-primary-dark)}

    /* Board View */
    .board{display:flex;gap:16px;overflow-x:auto;scroll-snap-type:x proximity;padding:6px 2px 24px;align-items:flex-start}
    .board-column{flex:1 0 280px;min-width:280px;max-width:340px;scroll-snap-align:start;background:color-mix(in srgb,var(--dayora-glass-strong) 88%,transparent);border:1px solid var(--dayora-glass-border);border-top:3.5px solid var(--status-color);border-radius:14px;padding:14px 12px;display:flex;flex-direction:column;transition:all 180ms}
    .board-column:has(.cdk-drop-list-dragging){background:color-mix(in srgb,var(--status-color) 6%,var(--dayora-glass-strong));border-color:color-mix(in srgb,var(--status-color) 40%,var(--dayora-glass-border))}
    .board-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;padding:0 2px}
    .board-header-left{display:flex;align-items:center;gap:8px;min-width:0}
    .status-dot{width:8px;height:8px;border-radius:50%;background:var(--status-color);box-shadow:0 0 0 3px color-mix(in srgb,var(--status-color) 25%,transparent);flex-shrink:0}
    .status-title{margin:0;font-size:.88rem;font-weight:700;color:var(--dayora-text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .board-count{font-size:.72rem;font-weight:650;min-width:22px;height:20px;line-height:20px;text-align:center;padding:0 7px;border-radius:99px;background:color-mix(in srgb,var(--status-color) 14%,transparent);color:var(--dayora-text)}
    .board-cards{min-height:120px;display:flex;flex-direction:column;gap:10px;flex:1}
    .board-card{position:relative;padding:12px 14px;border:1px solid var(--dayora-glass-border);border-radius:12px;background:var(--dayora-surface);box-shadow:0 1px 3px rgba(0,0,0,0.04);cursor:grab;transition:transform 160ms ease,box-shadow 160ms ease,border-color 160ms ease}
    .board-card:hover,.board-card:focus-within{transform:translateY(-2px);border-color:color-mix(in srgb,var(--status-color) 40%,var(--dayora-glass-border));box-shadow:0 6px 16px rgba(0,0,0,0.08)}
    .board-card:active{cursor:grabbing}
    .card-header{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}
    .board-card .task-title{flex:1;text-align:left;font-size:.92rem;font-weight:600;line-height:1.38;color:var(--dayora-text)}
    .card-priority{display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:6px;flex-shrink:0}
    .card-priority mat-icon{font-size:14px;width:14px;height:14px;line-height:14px}
    .card-priority.high{background:color-mix(in srgb,#ef4444 14%,transparent);color:#ef4444}
    .card-priority.medium{background:color-mix(in srgb,#f59e0b 14%,transparent);color:#f59e0b}
    .card-priority.low{background:color-mix(in srgb,#3b82f6 14%,transparent);color:#3b82f6}
    .card-notes{margin:6px 0 0;color:var(--dayora-muted);font-size:.8rem;line-height:1.45;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:anywhere}
    .card-footer{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:10px;padding-top:8px;border-top:1px solid color-mix(in srgb,var(--dayora-glass-border) 60%,transparent);flex-wrap:wrap}
    .card-labels{display:flex;align-items:center;gap:5px;flex-wrap:wrap}
    .card-label{display:inline-flex;align-items:center;gap:4px;max-width:110px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:2px 7px;border-radius:6px;background:color-mix(in srgb,var(--label-color) 12%,transparent);color:var(--dayora-text);font-size:.72rem;font-weight:500}
    .label-dot{width:5px;height:5px;border-radius:50%;background:var(--label-color);flex-shrink:0}
    .card-label-extra{color:var(--dayora-muted);font-size:.7rem;font-weight:650;padding:2px 5px;border-radius:4px;background:color-mix(in srgb,var(--dayora-muted) 12%,transparent)}
    .card-due{display:inline-flex;align-items:center;gap:4px;font-size:.72rem;color:var(--dayora-muted);padding:2px 7px;border-radius:6px;background:color-mix(in srgb,var(--dayora-muted) 8%,transparent);margin-left:auto}
    .card-due mat-icon{font-size:13px;width:13px;height:13px;line-height:13px}
    .card-due.today{color:#d97706;background:color-mix(in srgb,#f59e0b 14%,transparent);font-weight:600}
    .card-due.overdue{color:#dc2626;background:color-mix(in srgb,#ef4444 14%,transparent);font-weight:600}
    .board-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;padding:28px 10px;border:1.5px dashed color-mix(in srgb,var(--dayora-glass-border) 80%,transparent);border-radius:12px;color:var(--dayora-muted);font-size:.8rem;margin:4px 0;background:color-mix(in srgb,var(--dayora-glass-strong) 40%,transparent)}
    .board-empty-icon{font-size:20px;width:20px;height:20px;opacity:.5}
    .cdk-drag-preview{box-sizing:border-box;border-radius:12px;background:var(--dayora-surface);border:1.5px solid var(--dayora-primary);box-shadow:0 16px 36px rgba(0,0,0,0.16);transform:rotate(1.5deg);padding:12px 14px}
    .cdk-drag-placeholder{min-height:52px;border:2px dashed color-mix(in srgb,var(--status-color) 55%,var(--dayora-glass-border));border-radius:12px;background:color-mix(in srgb,var(--status-color) 8%,transparent);opacity:.8}
    .cdk-drag-animating,.board-cards.cdk-drop-list-dragging .board-card:not(.cdk-drag-placeholder){transition:transform 180ms ease}

    /* List View */
    .filters-bar{display:flex;align-items:center;gap:6px;flex-wrap:wrap;margin:-4px 0 18px;padding:5px;border:1px solid var(--dayora-glass-border);border-radius:14px;background:var(--dayora-glass-strong)}
    .filter-chip{display:inline-flex;align-items:center;gap:6px;min-height:34px;padding:0 12px;border:0;border-radius:10px;background:transparent;color:var(--dayora-muted);font-size:.82rem;font-weight:600;cursor:pointer;transition:all 160ms ease}
    .filter-chip:hover{color:var(--dayora-text);background:color-mix(in srgb,var(--dayora-hover) 50%,transparent)}
    .filter-chip.active{background:var(--dayora-surface);color:var(--dayora-primary-dark);box-shadow:0 2px 7px rgba(0,0,0,0.08);font-weight:700}
    .filter-dot{width:7px;height:7px;border-radius:50%;background:var(--chip-color,var(--dayora-primary));flex-shrink:0}
    .filter-count{font-size:.72rem;padding:1px 6px;border-radius:99px;background:color-mix(in srgb,var(--dayora-muted) 12%,transparent);color:var(--dayora-muted)}
    .filter-chip.active .filter-count{background:color-mix(in srgb,var(--dayora-primary) 14%,transparent);color:var(--dayora-primary-dark)}

    .task-list{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:10px}
    .task-item{border:1px solid var(--dayora-glass-border);border-radius:14px;background:var(--dayora-surface);overflow:hidden;transition:all 160ms ease}
    .task-item:hover{border-color:color-mix(in srgb,var(--dayora-primary) 32%,var(--dayora-glass-border));box-shadow:0 4px 14px rgba(0,0,0,0.06);transform:translateY(-1px)}
    .task-item.expanded{border-color:var(--dayora-primary);box-shadow:0 6px 20px rgba(0,0,0,0.08)}
    
    .item-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 16px;cursor:pointer;user-select:none;transition:background 160ms}
    .item-heading:hover{background:color-mix(in srgb,var(--dayora-hover) 35%,transparent)}
    .item-main{display:flex;align-items:center;gap:10px;min-width:0;flex:1;flex-wrap:wrap}
    
    .status-pill{display:inline-flex;align-items:center;gap:6px;padding:3px 9px;border-radius:99px;background:color-mix(in srgb,var(--status-c,var(--dayora-primary)) 12%,transparent);font-size:.75rem;font-weight:650;color:var(--dayora-text);flex-shrink:0}
    .status-pill .pill-dot{width:6px;height:6px;border-radius:50%;background:var(--status-c,var(--dayora-primary))}
    .status-pill .pill-name{white-space:nowrap}
    
    .item-main .task-title{font-size:.95rem;font-weight:650;color:var(--dayora-text);border:0;background:transparent;padding:0;text-align:left;cursor:pointer;line-height:1.4}
    .item-main .task-title:hover{color:var(--dayora-primary-dark)}
    
    .item-labels{display:inline-flex;align-items:center;gap:5px;flex-wrap:wrap}
    .item-label-chip{display:inline-flex;align-items:center;gap:4px;padding:2px 7px;border-radius:6px;background:color-mix(in srgb,var(--label-c) 12%,transparent);color:var(--dayora-text);font-size:.72rem;font-weight:500}
    .item-label-chip .label-dot{width:5px;height:5px;border-radius:50%;background:var(--label-c);flex-shrink:0}
    .item-label-more{color:var(--dayora-muted);font-size:.7rem;font-weight:650;padding:2px 5px;border-radius:4px;background:color-mix(in srgb,var(--dayora-muted) 12%,transparent)}
    
    .item-meta-group{display:flex;align-items:center;gap:10px;flex-shrink:0;margin-left:auto}
    .no-due{font-size:.74rem;color:var(--dayora-muted);opacity:.7}
    .expand-btn{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;color:var(--dayora-muted);transition:all 160ms ease}
    .expand-btn mat-icon{font-size:20px;width:20px;height:20px;line-height:20px}
    .task-item:hover .expand-btn{color:var(--dayora-text)}

    /* List Empty State */
    .list-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;padding:48px 16px;text-align:center;border:1.5px dashed color-mix(in srgb,var(--dayora-glass-border) 80%,transparent);border-radius:16px;color:var(--dayora-muted);background:color-mix(in srgb,var(--dayora-glass-strong) 40%,transparent);list-style:none}
    .list-empty mat-icon{font-size:38px;width:38px;height:38px;opacity:.45}
    .list-empty p{margin:0;font-size:.95rem;font-weight:650;color:var(--dayora-text)}
    .list-empty small{font-size:.82rem;color:var(--dayora-muted)}

    @media(max-width:700px){.tasks-page .heading{align-items:flex-start}.view-toggle{display:flex;width:100%;box-sizing:border-box}.view-toggle button{flex:1;min-width:0}.item-heading{flex-wrap:wrap;gap:8px}.item-main{width:100%}.item-meta-group{width:100%;justify-content:space-between;margin-top:2px}.board-column{flex-basis:82vw;min-width:82vw}}
    @media(prefers-reduced-motion:reduce){.view-toggle button,.task-title,.board,.board-column,.board-card,.cdk-drag-animating,.board-cards.cdk-drop-list-dragging .board-card,.task-item{scroll-behavior:auto;transition:none}.board-card:hover,.board-card:focus-within,.task-item:hover{transform:none}}
  `],
  template: `<main class="planning-page tasks-page">
    <header class="heading"><div><p class="eyebrow">TASKS · {{workspaceName()}}</p><h1>Make space for <em>progress.</em></h1><p class="subtitle">One workspace. A clear next step.</p></div><p class="status" role="status">{{store.status()}}</p></header>
    @if (!workspace.selectedId()) { <p class="notice" role="status">Select a workspace to manage tasks.</p> }
    <section class="panel glass-surface" aria-labelledby="add-task-title"><div class="panel-heading"><h2 id="add-task-title">Add a task</h2><p class="muted">In {{workspaceName()}}</p></div><form class="task-form add-task-form" (ngSubmit)="add()"><mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Task title</mat-label><input matInput [(ngModel)]="title" name="title" required maxlength="240" placeholder="What needs doing?"></mat-form-field><button mat-flat-button class="primary" [disabled]="creating() || !title.trim() || !workspace.selectedId()">{{creating() ? 'Adding…' : 'Add task'}}</button></form></section>
    @if (message()) { <p class="notice" role="alert">{{message()}}</p> } @if (duplicateWarning()) { <p class="notice" role="alert">An active task titled “{{duplicateWarning()}}” already exists in this workspace. <button mat-button type="button" (click)="add(true)" [disabled]="creating()">Add anyway</button></p> }
    <section class="panel glass-surface" aria-labelledby="task-list-title"><div class="panel-heading"><h2 id="task-list-title">Your tasks</h2>@if(workspace.selectedId()){<a mat-button [routerLink]="['/workspaces',workspace.selectedId(),'settings']">Workflow & labels</a>}<p class="muted count">{{tasks().length}} of {{workspaceTasks().length}} tasks</p></div>
      <p class="muted" role="status">{{statusesLoading() ? 'Loading statuses…' : labelsLoading() ? 'Loading labels…' : statusError() || labelError()}}</p>
      <div class="view-toggle" role="group" aria-label="Task view"><button mat-button type="button" [attr.aria-pressed]="view()==='list'" (click)="setView('list')">List</button><button mat-button type="button" [attr.aria-pressed]="view()==='board'" (click)="setView('board')">Board</button></div>
      @if(view()==='board'){
        <div class="board" cdkDropListGroup aria-label="Task board">
          @for(s of statuses();track s.id){
            <section class="board-column" [style.--status-color]="s.color" [attr.aria-label]="s.name">
              <div class="board-header">
                <div class="board-header-left">
                  <span class="status-dot" aria-hidden="true"></span>
                  <h3 class="status-title">{{s.name}}</h3>
                </div>
                <span class="board-count">{{columnTasks(s.id).length}}</span>
              </div>
              <div class="board-cards" cdkDropList [cdkDropListData]="s.id" [cdkDropListSortingDisabled]="true" (cdkDropListDropped)="drop($event)">
                @for(t of columnTasks(s.id);track t.id){
                  <article class="board-card" cdkDrag [cdkDragData]="t" [cdkDragDisabled]="saving().includes(t.id)">
                    <div class="card-header">
                      <button type="button" class="task-title" (click)="openTask(t)" [attr.aria-expanded]="editing()===t.id" [attr.aria-controls]="'editor-'+t.id">{{t.title}}</button>
                      @if(t.priority && t.priority !== 'NONE'){
                        <span class="card-priority" [class]="t.priority.toLowerCase()" [attr.title]="'Priority: ' + t.priority">
                          <mat-icon>flag</mat-icon>
                        </span>
                      }
                    </div>
                    @if(t.notes){<p class="card-notes">{{t.notes}}</p>}
                    @if((taskLabels()[t.id]?.length) || t.dueDate){
                      <div class="card-footer">
                        @if(taskLabels()[t.id]?.length){
                          <div class="card-labels" aria-label="Task labels">
                            @for(label of taskLabels()[t.id].slice(0,2);track label.id){
                              <span class="card-label" [style.--label-color]="label.color">
                                <span class="label-dot"></span>
                                {{label.name}}
                              </span>
                            }
                            @if(taskLabels()[t.id].length>2){<span class="card-label-extra">+{{taskLabels()[t.id].length-2}}</span>}
                          </div>
                        }
                        @if(t.dueDate){
                          <time class="card-due" [class.overdue]="dueState(t.dueDate)==='overdue'" [class.today]="dueState(t.dueDate)==='today'" [attr.datetime]="t.dueDate">
                            <mat-icon>event</mat-icon>
                            <span>{{dueState(t.dueDate)==='today' ? 'Today' : t.dueDate}}</span>
                          </time>
                        }
                      </div>
                    }
                  </article>
                } @empty {
                  <div class="board-empty">
                    <mat-icon class="board-empty-icon">inbox</mat-icon>
                    <span>No tasks</span>
                  </div>
                }
              </div>
            </section>
          }
        </div>
      }
      @if(view()==='list'){
        <div class="filters-bar" role="group" aria-label="Filter tasks by status">
          <button type="button" class="filter-chip" [class.active]="filter === ''" (click)="filter=''">
            <span>All</span>
            <span class="filter-count">{{count('')}}</span>
          </button>
          @for(s of statuses(); track s.id){
            <button type="button" class="filter-chip" [class.active]="filter === s.id" (click)="filter=s.id" [style.--chip-color]="s.color">
              <span class="filter-dot"></span>
              <span>{{s.name}}</span>
              <span class="filter-count">{{count(s.id)}}</span>
            </button>
          }
        </div>

        <ul class="list task-list">
          @for(t of tasks();track t.id){
            @let draft = this.draft(t);
            <li class="item task-item" [class.expanded]="editing()===t.id">
              <div class="item-heading" (click)="toggleEditor(t)">
                <div class="item-main">
                  @if(t.priority && t.priority !== 'NONE'){
                    <span class="card-priority" [class]="t.priority.toLowerCase()" [attr.title]="'Priority: ' + t.priority">
                      <mat-icon>flag</mat-icon>
                    </span>
                  }
                  <span class="status-pill" [style.--status-c]="statusColor(t)">
                    <span class="pill-dot"></span>
                    <span class="pill-name">{{statusLabel(t)}}</span>
                  </span>
                  <button type="button" class="task-title" (click)="$event.stopPropagation(); toggleEditor(t)" [attr.aria-expanded]="editing()===t.id" [attr.aria-controls]="'editor-'+t.id">
                    {{t.title}}
                  </button>
                  @if(taskLabels()[t.id]?.length){
                    <div class="item-labels" aria-label="Task labels">
                      @for(label of taskLabels()[t.id].slice(0,3);track label.id){
                        <span class="item-label-chip" [style.--label-c]="label.color">
                          <span class="label-dot"></span>
                          <span>{{label.name}}</span>
                        </span>
                      }
                      @if(taskLabels()[t.id].length > 3){
                        <span class="item-label-more">+{{taskLabels()[t.id].length - 3}}</span>
                      }
                    </div>
                  }
                </div>

                <div class="item-meta-group">
                  @if(draft.dueDate){
                    <time class="card-due" [class.overdue]="dueState(draft.dueDate)==='overdue'" [class.today]="dueState(draft.dueDate)==='today'" [attr.datetime]="draft.dueDate">
                      <mat-icon>event</mat-icon>
                      <span>{{dueState(draft.dueDate)==='today' ? 'Today' : draft.dueDate}}</span>
                    </time>
                  } @else {
                    <span class="no-due">No deadline</span>
                  }
                  <span class="expand-btn" aria-hidden="true">
                    <mat-icon>{{editing()===t.id ? 'expand_less' : 'expand_more'}}</mat-icon>
                  </span>
                </div>
              </div>

              @if(editing()===t.id){
                <fieldset class="editor" [id]="'editor-'+t.id" [disabled]="saving().includes(t.id)">
                  <legend class="sr-only">Edit {{t.title}}</legend>
                  <div class="row-grid task-grid">
                    <mat-form-field class="wide" appearance="outline" subscriptSizing="dynamic">
                      <mat-label>Task title</mat-label>
                      <input matInput [(ngModel)]="draft.title" [name]="'title-'+t.id" required maxlength="240">
                    </mat-form-field>
                    <mat-form-field appearance="outline" subscriptSizing="dynamic">
                      <mat-label>Status</mat-label>
                      <mat-select [(ngModel)]="draft.statusId" [name]="'status-'+t.id">
                        @for(s of statuses();track s.id){
                          <mat-option [value]="s.id">{{s.name}}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                    <mat-form-field appearance="outline" subscriptSizing="dynamic">
                      <mat-label>Priority</mat-label>
                      <mat-select [(ngModel)]="draft.priority" [name]="'priority-'+t.id">
                        <mat-option value="NONE">None</mat-option>
                        <mat-option value="LOW">Low</mat-option>
                        <mat-option value="MEDIUM">Medium</mat-option>
                        <mat-option value="HIGH">High</mat-option>
                      </mat-select>
                    </mat-form-field>
                    <mat-form-field appearance="outline" subscriptSizing="dynamic">
                      <mat-label>Deadline</mat-label>
                      <input matInput [matDatepicker]="duePicker" [ngModel]="dueDate(draft)" (ngModelChange)="setDueDate(draft,$event)" [name]="'due-'+t.id" aria-label="Task deadline">
                      <mat-datepicker-toggle matIconSuffix [for]="duePicker" aria-label="Open deadline calendar"></mat-datepicker-toggle>
                      <mat-datepicker #duePicker></mat-datepicker>
                      <button mat-icon-button matSuffix type="button" aria-label="Clear deadline" (click)="clearDueDate(draft)" [disabled]="!draft.dueDate">
                        <span aria-hidden="true">×</span>
                      </button>
                    </mat-form-field>
                    <mat-form-field class="full" appearance="outline" subscriptSizing="dynamic">
                      <mat-label>Notes</mat-label>
                      <textarea matInput [(ngModel)]="draft.notes" [name]="'notes-'+t.id" maxlength="10000" rows="3" placeholder="Add more details, links, or checklists…"></textarea>
                    </mat-form-field>
                  </div>
                  @if(labelsReady()){
                    <div class="editor-labels">
                      <app-task-label-picker [taskId]="t.id" [workspaceId]="workspace.selectedId()" [labels]="labels()"></app-task-label-picker>
                    </div>
                  }
                  <div class="actions editor-actions">
                    <button mat-icon-button class="delete-task" type="button" (click)="remove(t)" aria-label="Delete task" title="Delete task">
                      <mat-icon>delete_outline</mat-icon>
                    </button>
                    <div class="editor-actions-right">
                      <button mat-button type="button" (click)="closeEditor(t)">Close</button>
                      <button mat-flat-button class="primary" type="button" (click)="save(t)" [disabled]="!canSave(t)">{{saving().includes(t.id) ? 'Saving…' : 'Save changes'}}</button>
                    </div>
                  </div>
                </fieldset>
              }
            </li>
          } @empty {
            <li class="empty list-empty">
              <mat-icon>checklist</mat-icon>
              <p>{{workspaceTasks().length ? 'No tasks match this status' : 'No tasks in this workspace yet'}}</p>
              <small>{{workspaceTasks().length ? 'Try another filter or create a new task above.' : 'A little room for what matters. Add your first task above.'}}</small>
            </li>
          }
        </ul>
      }
    </section></main>`,
})
export class TasksPage {
  title=''; filter=''; view=signal<'list'|'board'>(this.readView()); message=signal(''); creating=signal(false); saving=signal<string[]>([]); editing=signal(''); duplicateWarning=signal('');
  labels=signal<Label[]>([]); labelsLoading=signal(false); labelsReady=signal(false); labelError=signal(''); private labelGeneration=0;
  statuses=signal<TaskStatus[]>([]); statusesLoading=signal(false); statusesReady=signal(false); statusError=signal(''); private statusGeneration=0;
  private drafts=new Map<string,TaskModel>();
  private dueDates=new WeakMap<TaskModel,{value:string|undefined;date:Date|null}>();
  dueDate(draft:TaskModel){let cached=this.dueDates.get(draft);if(!cached||cached.value!==draft.dueDate){cached={value:draft.dueDate,date:draft.dueDate?new Date(`${draft.dueDate}T00:00:00`):null};this.dueDates.set(draft,cached);}return cached.date;}
  setDueDate(draft:TaskModel,value:Date|null){if(value&&Number.isNaN(value.getTime()))return;draft.dueDate=value?`${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`:'';}
  clearDueDate(draft:TaskModel){this.setDueDate(draft,null);}
  private moves=signal<Record<string,string>>({});
  private readView():'list'|'board'{try{return localStorage.getItem('dayora.tasks.view')==='board'?'board':'list';}catch{return 'list';}}
  setView(view:'list'|'board'){this.view.set(view);try{localStorage.setItem('dayora.tasks.view',view);}catch{/* Storage may be blocked. */}}
  taskLabels=signal<Record<string,Label[]>>({}); private labelRequests=new Set<string>();
  columnTasks(statusId:string){const tasks=this.workspaceTasks().filter(t=>(this.moves()[t.id]||this.status(t))===statusId);if(this.labelsReady())tasks.forEach(t=>this.loadTaskLabels(t));return tasks;}
  openTask(task:TaskModel){this.editing.set(task.id);this.setView('list');}
  dueState(value:string){const today=new Date();today.setHours(0,0,0,0);const due=new Date(`${value}T00:00:00`);return due.getTime()===today.getTime()?'today':due<today?'overdue':'';}
  loadTaskLabels(task:TaskModel){if(this.taskLabels()[task.id]||this.labelRequests.has(task.id))return;this.labelRequests.add(task.id);const workspaceId=this.workspace.selectedId();void firstValueFrom(this.api.taskLabels(task.id)).then(labels=>{if(workspaceId===this.workspace.selectedId()&&this.workspace.matches(task))this.taskLabels.update(current=>({...current,[task.id]:labels.filter(label=>this.labels().some(available=>available.id===label.id))}));}).catch(()=>{}).finally(()=>this.labelRequests.delete(task.id));}
  drop(event:CdkDragDrop<string,string,TaskModel>){if(event.previousContainer===event.container)return;void this.move(event.item.data,event.container.data);}
  async move(task:TaskModel,statusId:string){
    const workspaceId=this.workspace.selectedId();
    if(!workspaceId||!this.workspace.matches(task)||this.saving().includes(task.id)||this.status(task)===statusId||!this.statusesReady()||!this.statuses().some(s=>s.id===statusId&&!s.archived))return;
    this.saving.update(ids=>[...ids,task.id]);this.moves.update(m=>({...m,[task.id]:statusId}));this.message.set('');
    try{await this.store.update({...task,statusId});this.drafts.delete(task.id);if(workspaceId===this.workspace.selectedId())this.message.set('Task moved.');}
    catch{if(workspaceId===this.workspace.selectedId())this.message.set('Task could not be moved. Previous status restored.');}
    finally{this.moves.update(m=>{const next={...m};delete next[task.id];return next;});this.saving.update(ids=>ids.filter(id=>id!==task.id));}
  }
  private deletingDialog=false;
  private dialog=inject(MatDialog);
  constructor(public store:TaskStore,public workspace:WorkspaceService,public api:Api){void this.store.load();effect(()=>{this.workspace.selectedId();this.drafts.clear();this.editing.set('');this.title='';this.message.set('');void this.store.load();void this.loadLabels();void this.loadStatuses();});}
  async loadStatuses(){const id=this.workspace.selectedId(),generation=++this.statusGeneration;this.statuses.set([]);this.statusesReady.set(false);this.statusesLoading.set(false);this.statusError.set('');this.filter='';if(!id)return;this.statusesLoading.set(true);try{const statuses=await firstValueFrom(this.api.taskStatuses(id));if(generation===this.statusGeneration&&id===this.workspace.selectedId()){this.statuses.set(this.orderedStatuses(statuses));this.statusesReady.set(true);}}catch{if(generation===this.statusGeneration)this.statusError.set('Could not load statuses.');}finally{if(generation===this.statusGeneration)this.statusesLoading.set(false);}}
  private orderedStatuses(statuses:TaskStatus[]){const categories=['UNSTARTED','STARTED','COMPLETED','CANCELED'];return statuses.filter(s=>!s.archived).sort((a,b)=>categories.indexOf(a.category)-categories.indexOf(b.category)||a.position-b.position||a.id.localeCompare(b.id));}
  async loadLabels(){const id=this.workspace.selectedId(),generation=++this.labelGeneration;this.labels.set([]);this.labelsReady.set(false);this.labelsLoading.set(false);this.labelError.set('');if(!id)return;this.labelsLoading.set(true);try{const labels=await firstValueFrom(this.api.labels(id));if(generation===this.labelGeneration&&id===this.workspace.selectedId()){this.labels.set(labels);this.labelsReady.set(true);}}catch{if(generation===this.labelGeneration)this.labelError.set('Could not load labels.');}finally{if(generation===this.labelGeneration)this.labelsLoading.set(false);}}
  workspaceName(){return this.workspace.workspaces().find(w=>w.id===this.workspace.selectedId())?.name||'No workspace';} status(t:TaskModel){return t.statusId||(typeof t.status==='object'?t.status.id:'');} statusLabel(t:TaskModel){return this.statuses().find(s=>s.id===this.status(t))?.name||(typeof t.status==='object'?t.status.name:'Unavailable status');} statusColor(t:TaskModel){return this.statuses().find(s=>s.id===this.status(t))?.color;} workspaceTasks(){return this.store.tasks().filter(t=>this.workspace.selectedId()&&this.workspace.matches(t));} tasks(){const list=this.workspaceTasks().filter(t=>!this.filter||this.status(t)===this.filter);if(this.labelsReady())list.forEach(t=>this.loadTaskLabels(t));return list;} count(status:string){return this.workspaceTasks().filter(t=>!status||this.status(t)===status).length;}
  toggleEditor(task:TaskModel){this.editing.update(id=>id===task.id?'':task.id);} closeEditor(task:TaskModel){if(this.editing()===task.id)this.editing.set('');} draft(task:TaskModel){let draft=this.drafts.get(task.id);if(!draft||draft.revision!==task.revision){draft={...task,statusId:this.status(task),priority:task.priority||'NONE'};this.drafts.set(task.id,draft);}return draft;}
  private normalized(task:TaskModel){return {title:(task.title||'').trim(),statusId:task.statusId||this.status(task),priority:task.priority||'NONE',dueDate:task.dueDate||'',notes:task.notes||''};}
  validDraft(task:TaskModel){const d=this.draft(task);return !!d.title?.trim()&&d.title.trim().length<=240&&(d.notes?.length||0)<=10000&&['NONE','LOW','MEDIUM','HIGH'].includes(d.priority||'NONE')&&this.statuses().some(s=>s.id===d.statusId);}
  canSave(task:TaskModel){if(this.saving().includes(task.id)||!this.validDraft(task))return false;return JSON.stringify(this.normalized(this.draft(task)))!==JSON.stringify(this.normalized({...task,statusId:this.status(task)}));}
  async save(task:TaskModel){if(!this.canSave(task)||!this.workspace.selectedId()||!this.workspace.matches(task))return;const draft={...this.draft(task),title:this.draft(task).title.trim(),workspaceId:task.workspaceId||this.workspace.selectedId()};if(!draft.title||draft.title.length>240||(draft.notes?.length||0)>10000||!['NONE','LOW','MEDIUM','HIGH'].includes(draft.priority||'NONE')||!this.statuses().some(s=>s.id===draft.statusId)){this.message.set('Use a title up to 240 characters and notes up to 10,000 characters.');return;}await this.change(task,()=>this.store.update(draft));}
  private async change(task:TaskModel,action:()=>Promise<unknown>){const workspaceId=this.workspace.selectedId();this.saving.update(v=>[...v,task.id]);this.message.set('');try{await action();if(this.workspace.selectedId()===workspaceId)this.message.set(this.store.status());}catch{if(this.workspace.selectedId()===workspaceId)this.message.set(this.store.status()+' Your edits remain here; copy them before leaving.');}finally{this.saving.update(v=>v.filter(id=>id!==task.id));}}
  private hasActiveDuplicate(title:string){return this.workspaceTasks().some(t=>['UNSTARTED','STARTED'].includes(this.statuses().find(s=>s.id===this.status(t))?.category||'')&&t.title.trim().toLocaleLowerCase()===title.toLocaleLowerCase());}
  async add(confirmed=false){const title=this.title.trim(),id=this.workspace.selectedId();if(!title||title.length>240||!id||this.creating())return;if((!confirmed||this.duplicateWarning()!==title)&&this.hasActiveDuplicate(title)){this.duplicateWarning.set(title);return;}this.duplicateWarning.set('');const existing=new Set(this.store.tasks().map(t=>t.id));this.creating.set(true);this.message.set('');try{await this.store.create(title,id);if(this.workspace.selectedId()===id){if(this.store.tasks().some(t=>!existing.has(t.id)&&t.title===title&&this.workspace.matches(t))){if(this.title.trim()===title)this.title='';}else this.message.set(this.store.status()||'Task could not be added. Your title is retained.');}}catch{if(this.workspace.selectedId()===id)this.message.set('Task could not be added. Your title is retained.');}finally{this.creating.set(false);}}
  async remove(task:TaskModel){const workspaceId=this.workspace.selectedId();if(!workspaceId||this.deletingDialog||this.saving().includes(task.id)||!this.workspace.matches(task))return;this.deletingDialog=true;try{const confirmed=await firstValueFrom(this.dialog.open(TaskDeleteDialogComponent,{data:{title:task.title},width:'min(440px, 92vw)',maxWidth:'92vw',autoFocus:'first-tabbable',restoreFocus:true,role:'alertdialog'}).afterClosed());if(confirmed===true&&workspaceId===this.workspace.selectedId()&&this.workspace.matches(task)&&!this.saving().includes(task.id))await this.change(task,()=>this.store.remove(task));}finally{this.deletingDialog=false;}}
}
