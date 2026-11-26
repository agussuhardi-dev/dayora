import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { Api, Focus } from '../../api';

export interface PipRenderData { title:string; timer:string; mode:string; progress:number; isRunning:boolean }
type Action = 'pause'|'resume'|'complete';
const icon = (name:'play_arrow'|'pause'|'check') => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${name==='check'?'M5 12l4 4L19 6':name==='pause'?'M8 5v14M16 5v14':'M8 5l11 7-11 7z'}"/></svg>`;

@Injectable({providedIn:'root'})
export class FocusPipService {
  readonly fallback = signal(false);
  readonly active = signal(false);
  readonly session = signal<Focus|null>(null);
  private api = inject(Api);
  private win?:Window;
  private card?:Document;
  private fallbackFrame?:HTMLIFrameElement;
  private video?:HTMLVideoElement;
  private stream?:MediaStream;
  private canvas?:HTMLCanvasElement;
  private interval?:ReturnType<typeof setInterval>;
  private generation = 0;
  private pending = false;
  private recovered = false;
  private status = '';
  private title = 'Focus session';
  private titleTask?:string|null;
  private confirmation?:string;
  private autoNext?:{id:string;at:number};
  private attempted?:string;
  private lastPoll = 0;
  private notifications = typeof Notification !== 'undefined' && Notification.permission === 'granted';
  private notified = new Set<string>();

  // The legacy callbacks are sampled once only; navigation must not own this player.
  async open(initial:()=>PipRenderData, _onAction?:(action:Action)=>void):Promise<boolean> {
    this.close();
    this.title = initial().title || 'Focus session';
    const generation = this.generation;
    this.active.set(true);
    const pip = (window as Window & {documentPictureInPicture?:{requestWindow:(options:{width:number;height:number})=>Promise<Window>}}).documentPictureInPicture;
    if (pip) {
      try {
        const w:Window = await pip.requestWindow({width:340,height:270});
        if (generation!==this.generation) { w.close(); return false; }
        this.win = w;
        this.mount(w.document);
        w.addEventListener('pagehide',()=>{if(this.win===w)this.close();},{once:true});
      } catch { /* Native video PiP or an independent in-page card below. */ }
    }
    if (generation!==this.generation) return false;
    if (!this.win) {
      try { await this.openVideo(generation); } catch { this.cleanVideo(); }
      if (generation!==this.generation) return false;
      if (!this.video) this.openFallback();
    }
    this.interval = setInterval(()=>this.tick(),250);
    window.addEventListener('online',this.onOnline);
    void this.recover();
    return !this.fallback();
  }

  private onOnline = () => { this.lastPoll=0; this.tick(); };

  private openFallback() {
    const frame = document.createElement('iframe');
    frame.title = 'Dayora focus mini player';
    frame.style.cssText = 'position:fixed;right:16px;bottom:16px;width:min(340px,calc(100vw - 32px));height:290px;border:1px solid #c4b5fd30;border-radius:22px;z-index:2147483647;box-shadow:0 20px 60px #02061780,0 0 24px #a78bfa12;background:#101426;color-scheme:dark';
    document.body.append(frame);
    this.fallbackFrame = frame;
    this.fallback.set(true);
    this.mount(frame.contentDocument!);
  }

  private mount(d:Document) {
    this.card=d;
    d.title='Dayora Focus';
    d.head.innerHTML=`<meta charset="utf-8"><style>
      *{box-sizing:border-box}html{color-scheme:dark}body{margin:0;min-height:100vh;padding:14px;background:radial-gradient(circle at 90% 0%,#43306d 0%,transparent 48%),linear-gradient(145deg,#10182d,#151127 85%);color:#f2efff;font:13px system-ui}main{display:grid;gap:9px}
      .top,.actions{display:flex;align-items:center;gap:8px}.title{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:650;letter-spacing:.01em}.phase{flex:none;padding:5px 9px;border:1px solid #a78bfa55;border-radius:999px;background:#a78bfa1c;color:#d6c5ff;font-size:11px;font-weight:600}
      .timer{text-align:center;padding:7px 10px 9px;border:1px solid #ffffff16;border-radius:15px;background:#ffffff0c;box-shadow:inset 0 1px #ffffff12;font-size:46px;font-weight:700;letter-spacing:-.04em;font-variant-numeric:tabular-nums;line-height:1.15;text-shadow:0 2px 20px #a78bfa45}.track{height:6px;background:#ffffff20;border-radius:8px;overflow:hidden}.bar{height:100%;background:linear-gradient(90deg,#a78bfa,#67e8f9);border-radius:8px;box-shadow:0 0 12px #67e8f988}
      button{border:1px solid #ffffff26;border-radius:11px;padding:8px;background:#ffffff12;color:#e9e3ff;cursor:pointer;font:inherit;transition:background .15s,border-color .15s}button:hover:not(:disabled){background:#ffffff25;border-color:#a78bfa88}button.primary{background:linear-gradient(135deg,#8465d9,#668bd9);border-color:#b8a6ff88;color:white;box-shadow:0 4px 16px #8067df50}button:disabled{opacity:.45;cursor:default}button:focus-visible{outline:2px solid #67e8f9;outline-offset:2px}
      svg{display:block;width:19px;height:19px;fill:none;stroke:currentColor;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round}.actions{justify-content:center}.actions button,#close{display:grid;place-items:center;width:37px;height:37px;padding:0;flex:none}#close{width:28px;height:28px;border-radius:9px;color:#bdb5d4}#close svg{width:16px;height:16px}#notify.enabled{color:#67e8f9;border-color:#67e8f955}#status{font-size:11px;min-height:15px;margin:0;color:#a8e6f3}#confirmation{padding:8px;border:1px solid #a78bfa44;background:#a78bfa1c;border-radius:10px}#confirmation[hidden]{display:none}small{font-size:10px;color:#aaa4c2}
    </style>`;
    d.body.innerHTML=`<main><div class="top"><span id="title" class="title"></span><span id="phase" class="phase"></span><button id="close" type="button" title="Close mini player; session continues" aria-label="Close mini player; session continues"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5l14 14M19 5L5 19"/></svg></button></div>
      <div id="timer" class="timer">--:--</div><div class="track" role="progressbar" aria-label="Session progress" aria-valuemin="0" aria-valuemax="100"><div id="bar" class="bar"></div></div>
      <div class="actions"><button id="toggle" class="primary" type="button"></button><button id="finish" type="button" title="Finish session" aria-label="Finish session">${icon('check')}</button><button id="retry" type="button" title="Refresh" aria-label="Refresh"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 11a8 8 0 1 0-2 6M20 4v7h-7"/></svg></button><button id="notify" type="button" title="Enable notifications" aria-label="Enable notifications"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 9h18c0-1-3-2-3-9M10 21h4"/></svg></button></div>
      <div id="confirmation" role="group" aria-label="Confirm finish session" hidden>Finish session? Automatic cycling will stop. <button id="cancel" type="button">Cancel</button> <button id="confirm" type="button" title="Confirm finish" aria-label="Confirm finish">${icon('check')}</button></div>
      <p id="status" role="status" aria-live="polite"></p><small>Keep Dayora open. Notifications cannot run after the tab closes.</small></main>`;
    d.getElementById('close')!.onclick=()=>this.close();
    d.getElementById('toggle')!.onclick=()=>{const f=this.session();if(f?.state==='COMPLETED')void this.next();else void this.action(f?.state==='RUNNING'?'pause':'resume');};
    d.getElementById('finish')!.onclick=()=>{if(!this.available())return;this.confirmation=this.session()!.id;this.render();d.getElementById('cancel')!.focus();};
    d.getElementById('cancel')!.onclick=()=>{this.confirmation=undefined;this.render();d.getElementById('finish')!.focus();};
    d.getElementById('confirm')!.onclick=()=>{if(this.confirmation!==this.session()?.id)return;this.confirmation=undefined;void this.action('complete');};
    d.addEventListener('keydown',e=>{if(e.key==='Escape'){this.confirmation=undefined;this.render();}});
    d.getElementById('retry')!.onclick=()=>void this.recover();
    d.getElementById('notify')!.onclick=()=>this.enableNotifications();
    this.render();
  }

  private elapsed(f:Focus) {
    const running=f.state==='RUNNING'&&f.runningSince ? Math.max(0,(Date.now()-Date.parse(f.runningSince))/1000) : 0;
    return Math.max(0,Math.floor(f.activeDurationSeconds+(Number.isFinite(running)?running:0)));
  }
  private view():PipRenderData {
    const f=this.session();
    if(!f)return {title:this.title,timer:'--:--',mode:'Focus',progress:0,isRunning:false};
    const elapsed=this.elapsed(f), seconds=f.mode==='NORMAL'?elapsed:Math.max(0,f.durationSeconds-elapsed);
    const timer=`${f.mode==='NORMAL'?String(Math.floor(seconds/3600)).padStart(2,'0')+':':''}${String(Math.floor(seconds/60)%(f.mode==='NORMAL'?60:Infinity)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;
    return {title:this.title,timer,mode:f.mode==='NORMAL'?'Count up':f.phase==='SHORT_BREAK'?'Short break':f.phase==='LONG_BREAK'?'Long break':'Focus',progress:f.durationSeconds>0?Math.min(100,elapsed/f.durationSeconds*100):0,isRunning:f.state==='RUNNING'};
  }
  private available(){return this.active()&&this.recovered&&!this.pending&&this.api.online()&&!!this.session();}
  private render() {
    const d=this.card,v=this.view(),f=this.session();
    if(d){
      d.getElementById('title')!.textContent=v.title;d.getElementById('title')!.title=v.title;
      d.getElementById('phase')!.textContent=v.mode;
      d.getElementById('timer')!.textContent=v.timer;
      d.getElementById('bar')!.style.width=`${v.progress}%`;
      d.querySelector('[role="progressbar"]')!.setAttribute('aria-valuenow',String(Math.round(v.progress)));
      const toggle=d.getElementById('toggle') as HTMLButtonElement;
      const label=f?.state==='COMPLETED'?'Start next phase':v.isRunning?'Pause session':'Resume session';
      toggle.innerHTML=icon(v.isRunning?'pause':'play_arrow');toggle.title=label;toggle.setAttribute('aria-label',label);
      toggle.disabled=!this.available()||!(f?.state==='RUNNING'||f?.state==='PAUSED'||(f?.state==='COMPLETED'&&f.mode==='POMODORO'));
      (d.getElementById('finish') as HTMLButtonElement).disabled=!this.available()||!['RUNNING','PAUSED'].includes(f?.state??'');
      (d.getElementById('confirm') as HTMLButtonElement).disabled=!this.available();
      (d.getElementById('retry') as HTMLButtonElement).disabled=this.pending||!this.api.online();
      d.getElementById('confirmation')!.hidden=!this.confirmation;
      d.getElementById('status')!.textContent=!this.api.online()?'Offline. Server state unconfirmed.':this.pending?'Checking server…':this.status;
      const notify=d.getElementById('notify')!,notificationLabel=this.notifications?'Notifications enabled':'Enable notifications';
      notify.title=notificationLabel;notify.setAttribute('aria-label',notificationLabel);notify.classList.toggle('enabled',this.notifications);
    }
    this.drawVideo(v);
  }

  private accept(f:Focus|null) {
    if(this.session()?.id!==f?.id){this.confirmation=undefined;this.attempted=undefined;this.autoNext=undefined;}
    this.session.set(f);
    if(this.titleTask!==f?.taskId){
      const previous=this.titleTask;this.titleTask=f?.taskId;
      if(previous!==undefined||!f?.taskId)this.title=f?.taskId?'Linked task':'Focus session';
      if(f?.taskId){
        const id=f.taskId,generation=this.generation;
        this.api.tasks().pipe(timeout(10000)).subscribe({next:r=>{if(generation!==this.generation||this.titleTask!==id)return;this.title=r.content.find(t=>t.id===id)?.title||'Linked task';this.render();},error:e=>{if(generation===this.generation&&e.status===401)this.close();}});
      }
    }
    this.recovered=true;
  }

  private async recover() {
    if(this.pending||!this.active()||!this.api.online())return;
    const generation=this.generation,known=this.session();this.pending=true;this.lastPoll=Date.now();this.render();
    try {
      const current=await firstValueFrom(this.api.currentFocus().pipe(timeout(10000)));
      if(generation!==this.generation)return;
      const f=current??(known?await firstValueFrom(this.api.focus(known.id).pipe(timeout(10000))):null);
      if(generation!==this.generation)return;
      this.accept(f);this.status=f?.state==='COMPLETED'?'Session finished.':f?'Synced with server.':'No active session.';
    } catch(e) {if(generation===this.generation)this.failed(e);}
    finally {if(generation===this.generation){this.pending=false;this.render();}}
  }
  private failed(error:unknown) {
    if((error as {status?:number})?.status===401){this.close();return;}
    this.recovered=false;this.status='Server state unconfirmed. Refresh or wait for recovery.';
  }

  private async action(action:Action,automatic=false) {
    const f=this.session();
    if(!f||!this.available()||!['RUNNING','PAUSED'].includes(f.state)||(action==='pause'&&f.state!=='RUNNING')||(action==='resume'&&f.state!=='PAUSED'))return;
    const generation=this.generation;
    this.pending=true;this.autoNext=undefined;this.render();
    try {
      const updated=await firstValueFrom(this.api.focusAction(f.id,action).pipe(timeout(10000)));
      if(generation!==this.generation)return;
      this.accept(updated);this.status=action==='complete'?'Session finished.':action==='pause'?'Session paused.':'Session resumed.';
      if(action==='complete'){
        if(automatic&&updated.mode==='POMODORO')this.autoNext={id:updated.id,at:Date.now()+3000};
        this.notify();
      }
    } catch(e) {if(generation===this.generation)this.failed(e);}
    finally {if(generation===this.generation){this.pending=false;this.render();}}
  }
  private async next() {
    const f=this.session();if(!f||!this.available()||f.state!=='COMPLETED'||f.mode!=='POMODORO')return;
    const generation=this.generation;this.pending=true;this.autoNext=undefined;this.render();
    try {
      // Re-read before transitioning: another client may already have advanced.
      const current=await firstValueFrom(this.api.currentFocus().pipe(timeout(10000)));
      if(generation!==this.generation)return;
      const updated=current?.id===f.id&&current.state==='COMPLETED'
         ? await firstValueFrom(this.api.nextFocus(f.id).pipe(timeout(10000)))
         : current;
      if(generation!==this.generation)return;
      this.accept(updated);this.status='Next phase started.';
    } catch(e){if(generation===this.generation)this.failed(e);}
    finally{if(generation===this.generation){this.pending=false;this.render();}}
  }
  private tick() {
    if(!this.active())return;
    this.render();
    if(this.pending)return;
    if(!this.recovered||Date.now()-this.lastPoll>=10000){void this.recover();return;}
    const f=this.session();
    if(this.autoNext&&this.autoNext.id===f?.id&&Date.now()>=this.autoNext.at){void this.next();return;}
    if(f?.state==='RUNNING'&&this.elapsed(f)>=f.durationSeconds&&this.attempted!==f.id&&this.available()){
      this.attempted=f.id;void this.action('complete',true);
    }
  }
  enableNotifications():void { void this.requestNotifications(); }
  private async requestNotifications() {
    const generation=this.generation;
    if(!('Notification' in window)){this.status='Browser notifications unavailable.';this.render();return;}
    try {
      const permission=await Notification.requestPermission();
      if(generation!==this.generation)return;
      this.notifications=permission==='granted';this.status=this.notifications?'Notifications enabled while Dayora stays open.':'Notifications not enabled.';
    } catch {if(generation===this.generation)this.status='Notifications unavailable.';}
    this.render();
  }
  notifyCompletion(f:Focus):void {
    if(this.notified.has(f.id)||!this.notifications||!('Notification' in window)||Notification.permission!=='granted')return;
    this.notified.add(f.id);
    try{new Notification('Dayora · Session finished',{body:this.title,tag:`dayora-focus-${f.id}`});}catch{/* Browser may disallow desktop delivery. */}
  }
  private notify(){const f=this.session();if(f)this.notifyCompletion(f);}

  private async openVideo(generation:number) {
    if(!document.pictureInPictureEnabled)return;
    this.canvas=document.createElement('canvas');this.canvas.width=512;this.canvas.height=300;
    this.drawVideo(this.view());
    this.stream=this.canvas.captureStream(4);
    const video=document.createElement('video');this.video=video;video.muted=true;video.playsInline=true;video.srcObject=this.stream;
    video.style.cssText='position:fixed;width:1px;height:1px;opacity:0;pointer-events:none';document.body.append(video);
    await video.play();if(generation!==this.generation)return;
    await video.requestPictureInPicture();
    if(generation!==this.generation){if(document.pictureInPictureElement===video)void document.exitPictureInPicture().catch(()=>{});return;}
    video.addEventListener('leavepictureinpicture',()=>{if(this.video===video)this.close();},{once:true});
    // Video PiP has no custom clickable controls. Keep its accessible control card in-page.
    this.openFallback();
  }
  private drawVideo(v:PipRenderData) {
    const c=this.canvas?.getContext('2d');if(!c)return;
    const background=c.createLinearGradient(0,0,512,300);background.addColorStop(0,'#10182d');background.addColorStop(1,'#171129');c.fillStyle=background;c.fillRect(0,0,512,300);c.fillStyle='#c4b5fd';c.font='18px system-ui';c.fillText(v.mode,28,36);
    c.fillStyle='#f2efff';c.font='20px system-ui';c.fillText(v.title.slice(0,36),28,75);c.font='bold 64px system-ui';c.fillText(v.timer,28,160);
    c.fillStyle='#ffffff25';c.fillRect(28,190,456,6);const progress=c.createLinearGradient(28,0,484,0);progress.addColorStop(0,'#a78bfa');progress.addColorStop(1,'#67e8f9');c.fillStyle=progress;c.fillRect(28,190,456*v.progress/100,6);
    c.fillStyle='#a8e6f3';c.font='14px system-ui';c.fillText(this.pending?'Checking server…':!this.recovered?'Server state unconfirmed':this.status.slice(0,55),28,234);
    c.fillText('Controls and notifications in Dayora',28,270);
  }
  private cleanVideo() {
    const video=this.video;this.video=undefined;
    if(video&&document.pictureInPictureElement===video)void document.exitPictureInPicture().catch(()=>{});
    video?.pause();if(video)video.srcObject=null;video?.remove();
    this.stream?.getTracks().forEach(t=>t.stop());this.stream=undefined;this.canvas=undefined;
  }
  close() {
    this.generation++;this.active.set(false);clearInterval(this.interval);this.interval=undefined;
    window.removeEventListener('online',this.onOnline);
    const w=this.win;this.win=undefined;w?.close();
    this.fallbackFrame?.remove();this.fallbackFrame=undefined;this.card=undefined;this.cleanVideo();
    this.fallback.set(false);this.session.set(null);this.title='Focus session';this.titleTask=undefined;
    this.pending=false;this.recovered=false;this.confirmation=undefined;this.autoNext=undefined;this.attempted=undefined;this.status='';this.notifications=false;this.notified.clear();
  }
}
