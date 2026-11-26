import { Component, effect, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { firstValueFrom } from 'rxjs';
import { TaskStore } from '../task-store.service';
import { Api, Label, TaskStatus, TaskStatusCategory } from '../api';
import { WorkspaceService } from '../workspace.service';

@Component({
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatTabsModule],
  styleUrl: './planning.css',
  styles: [`
    .settings-form{grid-template-columns:minmax(140px,1fr) minmax(140px,1fr) auto auto;align-items:center}
    .settings-list{list-style:none;padding:0;display:grid;gap:16px}
    .status-row{display:grid;grid-template-columns:minmax(140px,1fr) minmax(140px,1fr) 64px;gap:12px;border-bottom:1px solid var(--dayora-glass-border);padding-bottom:16px}
    .status-actions{grid-column:1/-1;display:flex;flex-wrap:wrap;gap:8px;align-items:center}
    .status-actions select{width:auto;max-width:100%}
    .panel{margin-top:20px}fieldset{border:0;padding:0;margin:0;min-width:0}
    .count-badge{padding:4px 11px;border-radius:99px;font-size:.78rem;font-weight:700;color:var(--dayora-primary);background:color-mix(in srgb,var(--dayora-primary) 12%,transparent)}
    .label-create-box{padding:20px;border:1px solid var(--dayora-glass-border);border-radius:20px;background:var(--dayora-glass-strong);margin-bottom:24px}
    .label-form{display:grid;gap:14px}
    .label-input-group{display:grid;grid-template-columns:1fr auto;gap:16px;align-items:center}
    .color-picker-group{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
    .color-presets{display:flex;align-items:center;gap:6px}
    .preset-dot{width:24px;height:24px;border-radius:50%;border:2px solid transparent;cursor:pointer;padding:0;transition:transform .15s,box-shadow .15s}
    .preset-dot:hover{transform:scale(1.15)}
    .preset-dot.selected{border-color:var(--dayora-text);transform:scale(1.15);box-shadow:0 0 0 2px var(--dayora-bg)}
    .custom-color-btn{position:relative;display:grid;place-items:center;width:28px;height:28px;border-radius:50%;border:1px solid var(--dayora-glass-border);background:var(--dayora-glass);cursor:pointer;color:var(--dayora-muted);overflow:hidden}
    .custom-color-btn input[type="color"]{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%}
    .custom-color-btn mat-icon{font-size:16px;width:16px;height:16px}
    .label-form-actions{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
    .preview-wrap{display:flex;align-items:center;gap:10px}
    .label-preview-label{font-size:.78rem;color:var(--dayora-muted);font-weight:600}
    .add-label-btn{display:inline-flex;align-items:center;gap:6px}
    .label-pill{display:inline-flex;align-items:center;gap:7px;padding:5px 12px;border-radius:99px;font-size:.82rem;font-weight:650;color:var(--label-c);background:color-mix(in srgb,var(--label-c) 14%,var(--dayora-glass-strong));border:1px solid color-mix(in srgb,var(--label-c) 32%,transparent)}
    .pill-dot{width:8px;height:8px;border-radius:50%;background:var(--label-c);box-shadow:0 0 0 2px color-mix(in srgb,var(--label-c) 25%,transparent);flex-shrink:0}
    .pill-text{max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .labels-grid{display:flex;flex-wrap:wrap;gap:10px;margin-top:8px}
    .label-card{display:inline-flex;align-items:center;gap:4px;padding:3px 4px 3px 6px;border-radius:99px;background:var(--dayora-glass-strong);border:1px solid var(--dayora-glass-border);transition:box-shadow .2s}
    .label-card:hover{box-shadow:0 4px 16px rgba(0,0,0,.06)}
    .label-card .label-pill{border:none;background:transparent;padding:2px 4px}
    .label-delete{width:26px;height:26px;display:grid;place-items:center;padding:0;border:0;background:transparent;color:var(--dayora-muted);border-radius:50%;cursor:pointer;transition:color .15s,background .15s}
    .label-delete:hover{color:var(--dayora-danger,#b42318);background:color-mix(in srgb,var(--dayora-danger,#b42318) 12%,transparent)}
    .label-delete mat-icon{font-size:16px;width:16px;height:16px}
    .labels-empty{display:grid;place-items:center;text-align:center;padding:36px 16px;border:1px dashed var(--dayora-glass-border);border-radius:18px;color:var(--dayora-muted)}
    .labels-empty mat-icon{font-size:36px;width:36px;height:36px;margin-bottom:8px;opacity:.6}
    .labels-empty p{margin:0 0 4px;font-weight:650;font-size:.95rem;color:var(--dayora-text)}
    .labels-empty small{font-size:.8rem}
    .status-msg{margin:8px 0}
    @media(max-width:680px){.label-input-group{grid-template-columns:1fr}}
    @media(max-width:650px){.settings-form,.status-row{grid-template-columns:1fr}.status-actions{grid-column:auto}}
    @media(max-width:480px){.status-actions{flex-direction:column;align-items:stretch}.status-actions button,.status-actions select{width:100%}}
  `],
  template: `<main class="planning-page">
    <header class="heading"><div><p class="eyebrow">WORKSPACE · {{workspaceName()}}</p><h1>Workspace <em>settings.</em></h1><p class="subtitle">Manage your workflow and labels.</p></div><a mat-button routerLink="/tasks">Back to tasks</a></header>
    @if(!workspaceId()) {
      <p class="notice" role="status">This workspace is unavailable or no longer selected.</p>
      @if(workspace.selectedId()){<a mat-button [routerLink]="['/workspaces',workspace.selectedId(),'settings']">Open selected workspace settings</a>}
    } @else {
      @if(!api.online()){<p class="notice" role="status">Settings require an online connection; changes are not queued.</p>}
      <mat-tab-group animationDuration="0ms">
        <mat-tab label="Workflow"><section class="panel glass-surface" aria-labelledby="workflow-title">
          <div class="panel-heading"><h2 id="workflow-title">Task statuses</h2></div>
          <form class="task-form settings-form" (ngSubmit)="createStatus()">
            <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Status name</mat-label><input matInput name="statusName" [(ngModel)]="statusName" required maxlength="120"></mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Category</mat-label><mat-select name="statusCategory" [(ngModel)]="statusCategory"><mat-option value="UNSTARTED">Unstarted</mat-option><mat-option value="STARTED">Started</mat-option><mat-option value="COMPLETED">Completed</mat-option><mat-option value="CANCELED">Canceled</mat-option></mat-select></mat-form-field>
            <label>Color<input type="color" name="statusColor" [(ngModel)]="newStatusColor"></label>
            <button mat-flat-button class="primary" [disabled]="!statusName.trim() || !canEditStatuses()">Create status</button>
          </form>
          <p role="status">{{statusesLoading() ? 'Loading statuses…' : statusBusy() ? 'Updating statuses…' : ''}}</p>
          @if(statusError()){<p role="alert">{{statusError()}} <button mat-button type="button" (click)="loadStatuses()" [disabled]="statusesLoading() || statusBusy() || !api.online()">Retry</button></p>}
          <fieldset [disabled]="!canEditStatuses()"><legend class="sr-only">Edit workflow</legend><ul class="settings-list">
          @for(s of statuses();track s.id){<li class="status-row">
            <label>Name<input [ngModel]="s.name" (change)="renameStatus(s,$any($event.target).value)" [name]="'status-name-'+s.id" [attr.aria-label]="'Name for '+s.name" maxlength="120"></label>
            <label>Category<select [ngModel]="s.category" (change)="changeStatusCategory(s,$any($event.target).value)" [name]="'status-category-'+s.id" [attr.aria-label]="'Category for '+s.name"><option value="UNSTARTED">Unstarted</option><option value="STARTED">Started</option><option value="COMPLETED">Completed</option><option value="CANCELED">Canceled</option></select></label>
            <label>Color<input type="color" [ngModel]="s.color" (change)="changeStatusColor(s,$any($event.target).value)" [name]="'status-color-'+s.id" [attr.aria-label]="'Color for '+s.name"></label>
            <div class="status-actions"><button mat-button type="button" (click)="setDefault(s)" [disabled]="!canEditStatuses() || s.isDefault">{{s.isDefault?'Default':'Set default'}}</button><button mat-button type="button" (click)="moveStatus(s,-1)" [disabled]="!canEditStatuses() || !canMove(s,-1)" [attr.aria-label]="'Move '+s.name+' up'">Up</button><button mat-button type="button" (click)="moveStatus(s,1)" [disabled]="!canEditStatuses() || !canMove(s,1)" [attr.aria-label]="'Move '+s.name+' down'">Down</button>
            <select [ngModel]="replacements[s.id]||''" (ngModelChange)="replacements[s.id]=$event" [name]="'replacement-'+s.id" [attr.aria-label]="'Replacement for '+s.name"><option value="">No replacement</option>@for(option of statuses();track option.id){@if(option.id!==s.id){<option [value]="option.id">{{option.name}}</option>}}</select>
            <button mat-button class="danger" type="button" (click)="archiveStatus(s)" [disabled]="!canEditStatuses()" [attr.aria-label]="'Archive '+s.name">Archive</button><button mat-button class="danger" type="button" (click)="deleteStatus(s)" [disabled]="!canEditStatuses()" [attr.aria-label]="'Delete '+s.name">Delete</button></div>
          </li>}</ul>
          @if(archivedStatuses().length){<h3>Archived statuses</h3><ul class="settings-list">@for(s of archivedStatuses();track s.id){<li>{{s.name}} <button mat-button type="button" (click)="restoreStatus(s)" [disabled]="!canEditStatuses()">Restore</button></li>}</ul>}</fieldset>
        </section></mat-tab>
        <mat-tab label="Labels"><section class="panel glass-surface" aria-labelledby="labels-title">
          <div class="panel-heading">
            <div>
              <h2 id="labels-title">Workspace labels</h2>
              <p class="muted">Tag and categorize tasks by project, area, or context.</p>
            </div>
            <span class="count-badge">{{labels().length}} {{labels().length === 1 ? 'label' : 'labels'}}</span>
          </div>

          <div class="label-create-box">
            <form class="label-form" (ngSubmit)="createLabel()">
              <div class="label-input-group">
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Label name</mat-label>
                  <input matInput name="labelName" [(ngModel)]="labelName" required maxlength="120" placeholder="e.g. Design, Urgent, Feature">
                </mat-form-field>
                <div class="color-picker-group">
                  <div class="color-presets" role="group" aria-label="Color presets">
                    @for(c of presetColors;track c){
                      <button type="button" class="preset-dot" [style.background-color]="c" [class.selected]="labelColor.toLowerCase()===c.toLowerCase()" (click)="labelColor=c" [attr.aria-label]="'Select color '+c"></button>
                    }
                  </div>
                  <label class="custom-color-btn" title="Choose custom color">
                    <input type="color" name="labelColor" [(ngModel)]="labelColor" aria-label="Custom label color">
                    <mat-icon>palette</mat-icon>
                  </label>
                </div>
              </div>
              <div class="label-form-actions">
                <div class="preview-wrap">
                  <span class="label-preview-label">Preview:</span>
                  <span class="label-pill" [style.--label-c]="labelColor">
                    <span class="pill-dot"></span>
                    <span class="pill-text">{{labelName.trim() || 'Label preview'}}</span>
                  </span>
                </div>
                <button mat-flat-button class="primary add-label-btn" [disabled]="!labelName.trim() || !canEditLabels()">
                  <mat-icon>add</mat-icon> Create label
                </button>
              </div>
            </form>
          </div>

          <p role="status" class="muted status-msg">{{labelsLoading() ? 'Loading workspace labels…' : labelBusy() ? 'Updating labels…' : ''}}</p>
          @if(labelError()){<p role="alert" class="notice">{{labelError()}} <button mat-button type="button" (click)="loadLabels()" [disabled]="labelsLoading() || labelBusy() || !api.online()">Retry</button></p>}

          @if(labels().length){
            <div class="labels-grid">
              @for(label of labels();track label.id){
                <div class="label-card">
                  <span class="label-pill" [style.--label-c]="label.color">
                    <span class="pill-dot"></span>
                    <span class="pill-text">{{label.name}}</span>
                  </span>
                  <button class="label-delete" type="button" (click)="deleteLabel(label)" [disabled]="!canEditLabels()" [attr.aria-label]="'Delete label '+label.name" title="Delete label">
                    <mat-icon>close</mat-icon>
                  </button>
                </div>
              }
            </div>
          } @else if(!labelsLoading()){
            <div class="labels-empty">
              <mat-icon>label_off</mat-icon>
              <p>No labels in this workspace yet</p>
              <small>Create your first label above to organize and filter tasks.</small>
            </div>
          }
        </section></mat-tab>
      </mat-tab-group>
    }
  </main>`,
})
export class WorkspaceSettingsPage {
  readonly presetColors = ['#6d5dfc', '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#ec4899', '#8b5cf6', '#06b6d4'];
  private readonly params;
  private generation = 0;
  statusName = ''; newStatusColor = '#6d5dfc'; statusCategory: TaskStatusCategory = 'UNSTARTED';
  labelName = ''; labelColor = '#6d5dfc'; replacements: Record<string,string> = {};
  statuses = signal<TaskStatus[]>([]); archivedStatuses = signal<TaskStatus[]>([]);
  statusesLoading = signal(false); statusesReady = signal(false); statusBusy = signal(false); statusError = signal('');
  labels = signal<Label[]>([]); labelsLoading = signal(false); labelsReady = signal(false); labelBusy = signal(false); labelError = signal('');

  constructor(public store: TaskStore, public workspace: WorkspaceService, public api: Api, route: ActivatedRoute) {
    this.params = toSignal(route.paramMap, {initialValue: route.snapshot.paramMap});
    // Route IDs never authorize access or silently replace the user's workspace selection.
    effect(() => {
      this.params(); this.workspace.selectedId(); this.workspace.workspaces();
      untracked(() => {
        this.generation++; this.statusName=''; this.labelName=''; this.replacements={};
        this.statusBusy.set(false); this.labelBusy.set(false);
        void this.loadStatuses(); void this.loadLabels();
        if(this.workspaceId())void this.store.load();
      });
    });
  }
  workspaceId() {
    const id=this.params().get('id') || '';
    return id===this.workspace.selectedId() && this.workspace.workspaces().some(w=>w.id===id) ? id : '';
  }
  workspaceName(){return this.workspace.workspaces().find(w=>w.id===this.workspaceId())?.name || 'No workspace';}
  private current(id:string,generation:number){return generation===this.generation && id===this.workspaceId();}
  canEditStatuses(){return !!this.workspaceId() && this.statusesReady() && !this.statusBusy() && this.api.online();}
  canEditLabels(){return !!this.workspaceId() && this.labelsReady() && !this.labelBusy() && this.api.online();}
  private applyStatuses(statuses:TaskStatus[]) {
    const categories=['UNSTARTED','STARTED','COMPLETED','CANCELED'];
    this.statuses.set(statuses.filter(s=>!s.archived).sort((a,b)=>categories.indexOf(a.category)-categories.indexOf(b.category)||a.position-b.position||a.id.localeCompare(b.id)));
    this.archivedStatuses.set(statuses.filter(s=>s.archived).sort((a,b)=>a.position-b.position));
  }
  async loadStatuses() {
    const id=this.workspaceId(),generation=this.generation;
    this.statuses.set([]);this.archivedStatuses.set([]);this.statusesReady.set(false);this.statusesLoading.set(false);this.statusError.set('');
    if(!id)return;
    this.statusesLoading.set(true);
    try {const statuses=await firstValueFrom(this.api.taskStatuses(id));if(this.current(id,generation)){this.applyStatuses(statuses);this.statusesReady.set(true);}}
    catch {if(this.current(id,generation))this.statusError.set('Could not load statuses. Retry to refresh.');}
    finally {if(this.current(id,generation))this.statusesLoading.set(false);}
  }
  private ownsStatus(s:TaskStatus){return s.workspaceId===this.workspaceId() && [...this.statuses(),...this.archivedStatuses()].some(item=>item.id===s.id);}
  private async mutateStatus(action:(id:string)=>Promise<unknown>) {
    const id=this.workspaceId(),generation=this.generation;
    if(!this.canEditStatuses())return;
    this.statusBusy.set(true);this.statusError.set('');
    try {
      await action(id);if(!this.current(id,generation))return;
      const statuses=await firstValueFrom(this.api.taskStatuses(id));
      if(this.current(id,generation)){this.applyStatuses(statuses);this.replacements={};}
    } catch {if(this.current(id,generation)){this.statusesReady.set(false);this.statusError.set('Status could not be saved. Retry to refresh before editing.');}}
    finally {if(this.current(id,generation))this.statusBusy.set(false);}
  }
  createStatus(){const name=this.statusName.trim();if(!name||name.length>120||!/^#[0-9a-f]{6}$/i.test(this.newStatusColor)||!['UNSTARTED','STARTED','COMPLETED','CANCELED'].includes(this.statusCategory))return;void this.mutateStatus(id=>firstValueFrom(this.api.createTaskStatus(id,{name,category:this.statusCategory,color:this.newStatusColor})));}
  renameStatus(s:TaskStatus,name:string){name=name.trim();if(this.ownsStatus(s)&&name&&name.length<=120&&name!==s.name)void this.mutateStatus(id=>firstValueFrom(this.api.updateTaskStatus(id,s.id,{name})));}
  changeStatusCategory(s:TaskStatus,category:TaskStatusCategory){if(this.ownsStatus(s)&&['UNSTARTED','STARTED','COMPLETED','CANCELED'].includes(category))void this.mutateStatus(id=>firstValueFrom(this.api.updateTaskStatus(id,s.id,{category})));}
  changeStatusColor(s:TaskStatus,color:string){if(this.ownsStatus(s)&&/^#[0-9a-f]{6}$/i.test(color))void this.mutateStatus(id=>firstValueFrom(this.api.updateTaskStatus(id,s.id,{color})));}
  setDefault(s:TaskStatus){if(this.ownsStatus(s))void this.mutateStatus(id=>firstValueFrom(this.api.updateTaskStatus(id,s.id,{isDefault:true})));}
  canMove(s:TaskStatus,offset:number){const list=this.statuses(),index=list.findIndex(item=>item.id===s.id);return index>=0&&list[index+offset]?.category===s.category;}
  moveStatus(s:TaskStatus,offset:number){if(!this.ownsStatus(s)||![-1,1].includes(offset)||!this.canMove(s,offset))return;const list=[...this.statuses()],index=list.findIndex(item=>item.id===s.id);[list[index],list[index+offset]]=[list[index+offset],list[index]];void this.mutateStatus(id=>firstValueFrom(this.api.reorderTaskStatuses(id,[...list,...this.archivedStatuses()].map(item=>item.id))));}
  private statusUsed(s:TaskStatus){return this.store.tasks().some(t=>this.workspace.matches(t)&&(t.statusId||(typeof t.status==='object'?t.status.id:''))===s.id);}
  archiveStatus(s:TaskStatus){
    if(!this.ownsStatus(s)||!this.canEditStatuses())return;
    if(s.isDefault){this.statusError.set('Choose another default before archiving.');return;}
    const replacement=this.replacements[s.id];
    if((replacement&&!this.statuses().some(item=>item.id===replacement&&item.id!==s.id))||(this.statusUsed(s)&&!replacement)){this.statusError.set('Choose a replacement status before archiving.');return;}
    if(confirm(`Archive status “${s.name}”?`))void this.mutateStatus(id=>firstValueFrom(this.api.archiveTaskStatus(id,s.id,replacement)));
  }
  restoreStatus(s:TaskStatus){if(this.ownsStatus(s))void this.mutateStatus(id=>firstValueFrom(this.api.restoreTaskStatus(id,s.id)));}
  deleteStatus(s:TaskStatus){if(!this.ownsStatus(s)||!this.canEditStatuses())return;if(this.statusUsed(s)){this.statusError.set('Status is in use; archive it with a replacement.');return;}if(confirm(`Delete status “${s.name}”? This cannot be undone.`))void this.mutateStatus(id=>firstValueFrom(this.api.deleteTaskStatus(id,s.id)));}
  async loadLabels(){
    const id=this.workspaceId(),generation=this.generation;
    this.labels.set([]);this.labelsReady.set(false);this.labelsLoading.set(false);this.labelError.set('');if(!id)return;
    this.labelsLoading.set(true);
    try {const labels=await firstValueFrom(this.api.labels(id));if(this.current(id,generation)){this.labels.set(labels);this.labelsReady.set(true);}}
    catch {if(this.current(id,generation))this.labelError.set('Could not load workspace labels. Management is disabled.');}
    finally {if(this.current(id,generation))this.labelsLoading.set(false);}
  }
  async createLabel(){
    const id=this.workspaceId(),generation=this.generation,name=this.labelName.trim();
    if(!this.canEditLabels()||!name||name.length>120||!/^#[0-9a-f]{6}$/i.test(this.labelColor))return;
    this.labelBusy.set(true);this.labelError.set('');
    try {const label=await firstValueFrom(this.api.createLabel(id,{name,color:this.labelColor}));if(this.current(id,generation)){this.labels.update(labels=>[...labels,label]);this.labelName='';}}
    catch {if(this.current(id,generation))this.labelError.set('Could not create label.');}
    finally {if(this.current(id,generation))this.labelBusy.set(false);}
  }
  async deleteLabel(label:Label){
    const id=this.workspaceId(),generation=this.generation;
    if(!this.canEditLabels()||label.workspaceId!==id||!this.labels().some(item=>item.id===label.id)||!confirm(`Delete label “${label.name}”? It will be removed from every task.`))return;
    this.labelBusy.set(true);this.labelError.set('');
    try {await firstValueFrom(this.api.deleteLabel(id,label.id));if(this.current(id,generation))this.labels.update(labels=>labels.filter(item=>item.id!==label.id));}
    catch {if(this.current(id,generation))this.labelError.set('Could not delete label.');}
    finally {if(this.current(id,generation))this.labelBusy.set(false);}
  }
}
