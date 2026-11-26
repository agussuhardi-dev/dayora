import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { Api, Label } from '../api';
import { TaskStore } from '../task-store.service';
import { WorkspaceService } from '../workspace.service';
import { WorkspaceSettingsPage } from './workspace-settings.page';

describe('WorkspaceSettingsPage', () => {
  it('rejects mismatched routes and ignores stale label responses after a workspace switch', async () => {
    const selectedId=signal('w1');
    const workspace={selectedId,workspaces:signal([{id:'w1',name:'One'},{id:'w2',name:'Two'}]),matches:()=>true};
    const pending=new Subject<Label[]>();
    const api={online:signal(true),labels:vi.fn(()=>pending),taskStatuses:()=>of([])};
    const params=convertToParamMap({id:'w1'});
    TestBed.configureTestingModule({imports:[WorkspaceSettingsPage],providers:[provideRouter([]),
      {provide:WorkspaceService,useValue:workspace},{provide:Api,useValue:api},
      {provide:TaskStore,useValue:{load:vi.fn(),tasks:signal([])}},
      {provide:ActivatedRoute,useValue:{paramMap:of(params),snapshot:{paramMap:params}}}]});
    const fixture=TestBed.createComponent(WorkspaceSettingsPage);
    fixture.detectChanges();
    expect(fixture.componentInstance.workspaceId()).toBe('w1');
    selectedId.set('w2');
    fixture.detectChanges();
    expect(fixture.componentInstance.workspaceId()).toBe('');
    pending.next([{id:'old',workspaceId:'w1',name:'Old',color:'#000000'}]);
    pending.complete();
    await fixture.whenStable();
    expect(fixture.componentInstance.labels()).toEqual([]);
    expect(api.labels).toHaveBeenCalledTimes(1);
  });
});
