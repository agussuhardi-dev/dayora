import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { RealtimeService } from './realtime.service';

export interface UserSettings { schemaVersion:number; pomodoro:{focusMinutes:number;shortBreakMinutes:number;longBreakMinutes:number;cyclesBeforeLongBreak?:number;mode:string;[key:string]:unknown}; sound:{enabled:boolean;breakTicking:boolean;[key:string]:unknown}; appearance:{theme:string;accent:string;[key:string]:unknown}; [key:string]:unknown }
type Patch={section:'pomodoro'|'sound'|'appearance';values:Record<string,unknown>};
function apply(base:UserSettings,patch:Patch):UserSettings{return {...base,[patch.section]:{...base[patch.section],...patch.values}}}
@Injectable({providedIn:'root'}) export class SettingsService {
  readonly settings=signal<UserSettings|null>(null); readonly error=signal(''); readonly saving=signal(false);
  private revision=0; private server:UserSettings|null=null; private queue:Patch[]=[]; private busy=false; private invalidated=false; private generation=0;
  constructor(private http:HttpClient,realtime:RealtimeService){realtime.invalidated.subscribe(()=>this.reload());}
  reset(){this.generation++;this.server=null;this.queue=[];this.settings.set(null);this.error.set('');this.busy=false;this.saving.set(false);this.invalidated=false;}
  async reload(){
    if(this.busy||this.queue.length){this.invalidated=true;return}
    this.busy=true;const generation=this.generation;
    try{const r=await firstValueFrom(this.http.get<{revision:number;settings:UserSettings}>('/api/settings'));if(generation!==this.generation)return;this.accept(r);this.error.set('')}
    catch{if(generation===this.generation)this.error.set('Settings could not be loaded. Retry when online.')}
    finally{if(generation===this.generation){this.busy=false;if(this.queue.length)void this.flush();else if(this.invalidated){this.invalidated=false;void this.reload()}}}
  }
  update(section:Patch['section'],values:Record<string,unknown>){const current=this.settings();if(!current){this.error.set('Settings are not loaded; changes were not saved.');return}const patch={section,values};this.queue.push(patch);this.settings.set(apply(current,patch));if(!this.error())void this.flush()}
  async retry(){
    if(this.busy)return;
    // ponytail: unsaved edits live in memory; a durable offline outbox is intentionally unsupported.
    this.busy=true;const generation=this.generation;
    try{const r=await firstValueFrom(this.http.get<{revision:number;settings:UserSettings}>('/api/settings'));if(generation!==this.generation)return;this.accept(r);this.error.set('')}
    catch{if(generation===this.generation)this.error.set('Settings retry failed. Unsaved edits remain on screen.')}
    finally{if(generation===this.generation){this.busy=false;if(!this.error())void this.flush()}}
  }
  private accept(r:{revision:number;settings:UserSettings}){
    const s=r.settings;
    if(s?.pomodoro&&s.pomodoro.cyclesBeforeLongBreak==null)s.pomodoro.cyclesBeforeLongBreak=4;
    if(!Number.isSafeInteger(r.revision)||!s||typeof s!=='object'||!s.pomodoro||!s.sound||!s.appearance||!['focusMinutes','shortBreakMinutes','longBreakMinutes'].every(k=>Number.isInteger(s.pomodoro[k])&&Number(s.pomodoro[k])>=1&&Number(s.pomodoro[k])<=1440)||!Number.isInteger(s.pomodoro.cyclesBeforeLongBreak)||Number(s.pomodoro.cyclesBeforeLongBreak)<1||Number(s.pomodoro.cyclesBeforeLongBreak)>12||!['NORMAL','POMODORO'].includes(s.pomodoro.mode)||typeof s.sound.enabled!=='boolean'||typeof s.sound.breakTicking!=='boolean'||!['system','light','dark'].includes(s.appearance.theme)||!/^#[0-9a-f]{6}$/i.test(s.appearance.accent))throw new Error('Invalid settings response');
    this.revision=r.revision;this.server=s;this.settings.set(this.queue.reduce(apply,s));
  }
  private async flush(){
    if(this.busy||this.error()||!this.server)return;
    this.busy=true;this.saving.set(true);const generation=this.generation;
    try{while(this.queue.length){const settings=apply(this.server!,this.queue[0]);const r=await firstValueFrom(this.http.put<{revision:number;settings:UserSettings}>('/api/settings',{revision:this.revision,settings}));if(generation!==this.generation)return;this.accept(r);this.queue.shift();this.settings.set(this.queue.reduce(apply,this.server!))}}
    catch(e){if(generation===this.generation)this.error.set((e as {status?:number}).status===409?'Settings conflict. Your edits are unsaved. Retry applies them over the latest settings.':'Settings could not be saved. Your edits are unsaved; retry when online.')}
    finally{if(generation===this.generation){this.busy=false;this.saving.set(false);if(!this.error()&&this.invalidated){this.invalidated=false;void this.reload()}}}
  }
}
