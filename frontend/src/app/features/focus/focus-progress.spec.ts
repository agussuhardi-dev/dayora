import { signal } from '@angular/core';
import { of } from 'rxjs';
import { FocusPage } from './focus.page';
import { Focus } from '../../api';

describe('Focus ring progress', () => {
  it('validates text durations as whole minutes', () => {
    const page=Object.create(FocusPage.prototype) as FocusPage;
    page.focusMinutes=25;page.shortBreakMinutes=5;page.longBreakMinutes=15;
    page.setDuration('focusMinutes','30');expect(page.validDurations()).toBe(true);
    for(const value of ['', '1.5', '1e2', 'abc', '0', '1441']){
      page.setDuration('focusMinutes',value);expect(page.validDurations()).toBe(false);
    }
  });
  it('continues the server-selected Pomodoro phase without reopening settings', () => {
    const page=Object.create(FocusPage.prototype) as FocusPage;
    const session:Focus={id:'focus',mode:'POMODORO',phase:'FOCUS',completedFocusCount:1,state:'COMPLETED',durationSeconds:1500,activeDurationSeconds:1500,runningSince:null};
    page.session=signal<Focus|null>(session);page.busy=signal(false);page.loading=signal(false);page.recovered=signal(true);page.notice=signal('');
    let requested='';let restored:Focus|null=null;
    const next={...session,id:'break',phase:'SHORT_BREAK' as const,state:'RUNNING'};
    Object.assign(page,{api:{online:()=>true,nextFocus:(id:string)=>{requested=id;return of(next)}},restore:(s:Focus)=>{restored=s}});
    page.next(true);expect(requested).toBe('focus');expect(restored).toBe(next);expect(page.settingsOpen).toBe(false);
    for(const count of [1,2,3,4,5,8]){
      page.session.set({...session,completedFocusCount:count});expect(page.nextLabel()).toBe(count%4===0?'Start long break':'Start short break');
    }
    page.session.set({...session,phase:'LONG_BREAK'});expect(page.nextLabel()).toBe('Start focus');
  });
  it('opens timing modal after manual Pomodoro finish without a refresh', () => {
    const page=Object.create(FocusPage.prototype) as FocusPage;
    page.session=signal<Focus|null>({id:'done',mode:'POMODORO',phase:'FOCUS',state:'COMPLETED',durationSeconds:1500,activeDurationSeconds:300,runningSince:null});
    page.busy=signal(false);page.loading=signal(false);page.recovered=signal(true);page.mode=signal<'NORMAL'|'POMODORO'>('POMODORO');
    page.focusMinutes=25;page.shortBreakMinutes=5;page.longBreakMinutes=15;
    Object.assign(page,{api:{online:()=>true},settingsOpen:false});
    page.next();expect(page.settingsOpen).toBe(true);
  });
  it('counts down, freezes paused, restores session mode, clamps at zero', () => {
    const page = Object.create(FocusPage.prototype) as FocusPage;
    page.mode = signal<'NORMAL'|'POMODORO'>('NORMAL');
    page.now = signal(750000);
    page.session = signal<Focus|null>({id:'1',mode:'POMODORO',state:'RUNNING',durationSeconds:1500,activeDurationSeconds:0,runningSince:new Date(0).toISOString()});
    expect(page.progress()).toBe(0.5);
    expect(page.display()).toBe('12:30');
    page.session.update(s=>({...s!,state:'PAUSED',activeDurationSeconds:750,runningSince:null}));
    page.now.set(1500000);
    expect(page.progress()).toBe(0.5);
    page.session.update(s=>({...s!,state:'RUNNING',runningSince:new Date(0).toISOString()}));
    expect(page.progress()).toBe(0);
  });
  it('fills normal stopwatch against its 24 hour limit', () => {
    const page = Object.create(FocusPage.prototype) as FocusPage;
    page.mode = signal<'NORMAL'|'POMODORO'>('POMODORO');
    page.now = signal(43200000);
    page.session = signal<Focus|null>({id:'2',mode:'NORMAL',state:'RUNNING',durationSeconds:86400,activeDurationSeconds:0,runningSince:new Date(0).toISOString()});
    expect(page.progress()).toBe(0.5);
    page.now.set(86400000);
    expect(page.progress()).toBe(1);
  });
});
