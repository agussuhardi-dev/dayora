import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { Api, Label } from '../api';
import { TaskLabelPickerComponent } from './task-label-picker.component';

describe('TaskLabelPickerComponent', () => {
  const label: Label = {id:'l1', workspaceId:'w1', name:'Work', color:'#123456'};
  let api: {online: ReturnType<typeof signal<boolean>>; taskLabels: ReturnType<typeof vi.fn>; setTaskLabels: ReturnType<typeof vi.fn>};
  let picker: TaskLabelPickerComponent;

  beforeEach(() => {
    api = {online:signal(true), taskLabels:vi.fn(), setTaskLabels:vi.fn()};
    TestBed.configureTestingModule({providers:[{provide:Api,useValue:api}]});
    picker = TestBed.runInInjectionContext(() => new TaskLabelPickerComponent(TestBed.inject(Api)));
    picker.taskId='t1'; picker.workspaceId='w1'; picker.labels=[label];
  });

  it('loads and saves assignments', async () => {
    api.taskLabels.mockReturnValue(of([label]));
    api.setTaskLabels.mockReturnValue(of([]));
    await picker.load();
    expect(picker.ready()).toBe(true);
    expect(picker.selected()).toEqual(['l1']);
    await picker.save([]);
    expect(api.setTaskLabels).toHaveBeenCalledWith('t1', []);
    expect(picker.selected()).toEqual([]);
  });

  it('blocks edits after a failed load', async () => {
    api.taskLabels.mockReturnValue(throwError(() => new Error('offline')));
    await picker.load();
    expect(picker.ready()).toBe(false);
    expect(picker.error()).toContain('Could not load');
    await picker.save([]);
    expect(api.setTaskLabels).not.toHaveBeenCalled();
  });

  it('rejects labels from another workspace', async () => {
    picker.labels=[label,{id:'other',workspaceId:'w2',name:'Other',color:'#000000'}];
    api.taskLabels.mockReturnValue(of([label]));
    await picker.load();
    await picker.save(['l1','other']);
    expect(api.setTaskLabels).not.toHaveBeenCalled();
    expect(picker.selected()).toEqual(['l1']);
  });

  it('ignores stale task responses after a workspace switch', async () => {
    const oldResponse=new Subject<Label[]>();
    api.taskLabels.mockReturnValueOnce(oldResponse).mockReturnValueOnce(of([]));
    const oldLoad=picker.load();
    picker.taskId='t2'; picker.workspaceId='w2';
    await picker.load();
    oldResponse.next([label]); oldResponse.complete();
    await oldLoad;
    expect(picker.selected()).toEqual([]);
    expect(picker.ready()).toBe(true);
  });
});
