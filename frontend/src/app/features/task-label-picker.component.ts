import { Component, Input, OnChanges, OnDestroy, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { Api, Label } from '../api';

@Component({
  selector: 'app-task-label-picker',
  imports: [MatButtonModule, MatFormFieldModule, MatSelectModule],
  styles: [`:host{display:block;margin-top:12px}mat-form-field{width:100%;--mat-form-field-outlined-container-shape:12px}p{margin:6px 0;color:var(--dayora-muted);font-size:.85rem}.swatch{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:8px}`],
  template: `<mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Labels (optional)</mat-label><mat-select multiple [value]="selected()" (selectionChange)="save($event.value)" [disabled]="!ready() || loading() || saving() || !api.online()">@for(label of labels;track label.id){<mat-option [value]="label.id"><span class="swatch" [style.background-color]="label.color" aria-hidden="true"></span>{{label.name}}</mat-option>}</mat-select></mat-form-field>
    <p role="status">{{loading() ? 'Loading labels…' : saving() ? 'Saving labels…' : !api.online() ? 'Labels require an online connection; changes are not queued.' : 'Labels save automatically, separately from task changes.'}}</p>
    @if(error()){<p role="alert">{{error()}} <button mat-button type="button" (click)="load()" [disabled]="loading() || saving() || !api.online()">Retry</button></p>}`,
})
export class TaskLabelPickerComponent implements OnChanges, OnDestroy {
  @Input({required:true}) taskId = '';
  @Input({required:true}) workspaceId = '';
  @Input({required:true}) labels: Label[] = [];
  selected = signal<string[]>([]); ready = signal(false); loading = signal(false); saving = signal(false); error = signal('');
  private generation = 0;
  constructor(public api:Api) {}
  ngOnChanges(){void this.load();}
  ngOnDestroy(){this.generation++;}
  async load(){
    const generation=++this.generation;
    this.ready.set(false); this.loading.set(true); this.saving.set(false); this.error.set('');
    try {
      const labels=await firstValueFrom(this.api.taskLabels(this.taskId));
      if(generation!==this.generation)return;
      this.selected.set(labels.map(label=>label.id)); this.ready.set(true);
    } catch {if(generation===this.generation)this.error.set('Could not load task labels. Editing is disabled to protect existing assignments.');}
    finally {if(generation===this.generation)this.loading.set(false);}
  }
  async save(ids:string[]){
    if(!this.ready() || this.loading() || this.saving() || !this.api.online())return;
    if(ids.some(id=>!this.labels.some(label=>label.id===id && label.workspaceId===this.workspaceId)))return;
    const generation=this.generation;
    this.saving.set(true); this.error.set('');
    try {
      const labels=await firstValueFrom(this.api.setTaskLabels(this.taskId,ids));
      if(generation===this.generation)this.selected.set(labels.map(label=>label.id));
    } catch {if(generation===this.generation){this.ready.set(false);this.error.set('Could not save labels. Retry to reload current assignments before editing.');}}
    finally {if(generation===this.generation)this.saving.set(false);}
  }
}
