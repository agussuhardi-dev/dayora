import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Api, Focus } from '../../api';
import { FocusPipService } from './focus-pip.service';

const focus:Focus={id:'focus',state:'RUNNING',mode:'POMODORO',durationSeconds:1500,activeDurationSeconds:1500,runningSince:null};
describe('Independent focus mini player',()=>{
  let service:FocusPipService;
  let completed=0;
  beforeEach(()=>{
    completed=0;
    TestBed.configureTestingModule({providers:[{provide:Api,useValue:{online:()=>true,currentFocus:()=>of(focus),focus:()=>of(focus),focusAction:()=>{completed++;return of({...focus,state:'COMPLETED'});}}}]});
    service=TestBed.inject(FocusPipService);
  });
  afterEach(()=>service.close());
  it('opens a visible route-independent fallback; never calls legacy action callback',async()=>{
    let renders=0,actions=0;
    await service.open(()=>{renders++;return {title:'Initial title',timer:'25:00',mode:'Focus',progress:0,isRunning:true};},()=>actions++);
    await Promise.resolve();
    expect(service.active()).toBe(true);expect(service.fallback()).toBe(true);
    expect(document.querySelector('iframe[title="Dayora focus mini player"]')).not.toBeNull();
    expect(renders).toBe(1);expect(actions).toBe(0);
    service.close();expect(service.active()).toBe(false);expect(document.querySelector('iframe')).toBeNull();expect(completed).toBe(0);
  });
  it('manual completion never schedules the next phase; expiry does',async()=>{
    const s=service as any;
    s.active.set(true);s.recovered=true;s.session.set(focus);
    await s.action('complete');expect(completed).toBe(1);expect(s.autoNext).toBeUndefined();
    s.session.set(focus);await s.action('complete',true);
    expect(s.autoNext.id).toBe('focus');expect(s.autoNext.at).toBeGreaterThanOrEqual(Date.now()+2900);
  });
  it('unconfirmed server errors disable actions; 401 clears title/session',async()=>{
    const s=service as any;
    s.active.set(true);s.recovered=true;s.session.set(focus);
    s.api.focusAction=()=>throwError(()=>({status:503}));
    await s.action('pause');expect(s.available()).toBe(false);expect(s.status).toContain('unconfirmed');
    s.failed({status:401});expect(service.active()).toBe(false);expect(service.session()).toBeNull();expect(s.title).toBe('Focus session');
  });
  it('deduplicates opt-in completion notifications',()=>{
    const original=Object.getOwnPropertyDescriptor(window,'Notification');
    let calls=0;
    class FakeNotification {static permission='granted';constructor(){calls++;}}
    Object.defineProperty(window,'Notification',{configurable:true,value:FakeNotification});
    try{(service as any).notifications=true;service.notifyCompletion(focus);service.notifyCompletion(focus);expect(calls).toBe(1);}
    finally{if(original)Object.defineProperty(window,'Notification',original);else delete (window as any).Notification;}
  });
});
