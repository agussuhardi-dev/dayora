import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { RealtimeService } from './realtime.service';

interface PushConfig { apiKey:string; authDomain:string; projectId:string; messagingSenderId:string; appId:string; vapidKey:string }
interface PushStatus { enabled:boolean; reason:string; tokenCount:number }

@Injectable({providedIn:'root'})
export class PushService {
  readonly message = signal('Push notifications have not been checked.');
  readonly busy = signal(false);
  private token = this.savedToken();
  private unsubscribe?: () => void;
  private pending?: Promise<void>;
  private active = false;
  private readonly clientInfo=`browser=${navigator.userAgent}, platform=${navigator.platform}`;
  constructor(private http:HttpClient, private realtime:RealtimeService) {}

  private savedToken() { try { return localStorage.getItem('dayora-web-push-token') || ''; } catch { return ''; } }
  private remember(token:string) { this.token=token; try { if(token)localStorage.setItem('dayora-web-push-token',token);else localStorage.removeItem('dayora-web-push-token'); } catch { /* Memory retains the token when storage is unavailable. */ } }
  start() { this.active=true; console.info('[DayoraPush] FCM client start',this.clientInfo); return this.refresh(); }
  stop() { this.active=false; this.unsubscribe?.(); this.unsubscribe=undefined; }
  async enable() {
    if(typeof Notification==='undefined') { this.message.set('Browser notifications are unsupported.'); return; }
    try {
      const permission=await Notification.requestPermission();
      if(permission!=='granted') { this.message.set('Notifications blocked. Allow notifications in browser site settings.'); return; }
      await this.start();
    } catch { this.message.set('Notification permission could not be requested.'); }
  }
  refresh():Promise<void> {
    if(this.pending)return this.pending;
    this.pending=this.register().finally(()=>{this.pending=undefined;this.busy.set(false);});
    return this.pending;
  }
  private async register() {
    this.busy.set(true);
    try {
      const [config,status]=await Promise.all([
        firstValueFrom(this.http.get<PushConfig>('/api/push/config')),
        firstValueFrom(this.http.get<PushStatus>('/api/push/status')),
      ]);
      const keys:(keyof PushConfig)[]=['apiKey','authDomain','projectId','messagingSenderId','appId','vapidKey'];
      const missing=keys.filter(key=>typeof config[key]!=='string'||!config[key].trim());
      if(missing.length) { console.info('[DayoraPush] FCM disabled: missing',missing.join(', ')); this.message.set(`Web push disabled: missing ${missing.join(', ')}.${status.enabled?'':` Backend: ${status.reason}`}`); return; }
      if(!status.enabled) { this.message.set(`Push disabled: ${status.reason}`); return; }
      if(!isSecureContext || !('serviceWorker' in navigator) || typeof Notification==='undefined') { this.message.set('Web push requires HTTPS and a supported browser.'); return; }
      if(Notification.permission!=='granted') { this.message.set(`Push not enabled: notification permission ${Notification.permission}.`); return; }
      const sdk=await import('firebase/messaging');
      if(!await sdk.isSupported()) { this.message.set('Web push is unsupported in this browser.'); return; }
      if(!this.active)return;
      const {initializeApp,getApps}=await import('firebase/app');
      const app=getApps().find(app=>app.name==='dayora-push') || initializeApp(config,'dayora-push');
      const registration=await navigator.serviceWorker.register(`/firebase-messaging-sw.js?${new URLSearchParams(Object.fromEntries(keys.filter(k=>k!=='vapidKey').map(k=>[k,config[k]])))}`,{scope:'/firebase-cloud-messaging-push-scope'});
      const messaging=sdk.getMessaging(app);
      const token=await sdk.getToken(messaging,{vapidKey:config.vapidKey,serviceWorkerRegistration:registration});
      if(!token)throw new Error('No push token returned.');
      if(!this.active)return;
      console.info('[DayoraPush] FCM token obtained; registering with backend',this.clientInfo);
      const previous=this.token;
      // Remember before HTTP: logout must remove even a registration whose response was lost.
      this.remember(token);
      await firstValueFrom(this.http.put('/api/push/token',{token,platform:'WEB'}));
      console.info('[DayoraPush] FCM token registered with backend');
      if(previous && previous!==token)await firstValueFrom(this.http.delete('/api/push/token',{body:{token:previous}}));
      this.unsubscribe?.();
      if(this.active)this.unsubscribe=sdk.onMessage(messaging,payload=>{console.info('[DayoraPush] FCM foreground message received', {messageId:payload.messageId});this.realtime.invalidate();});
      this.message.set('Web push enabled. Background sync alerts are available; completion alerts still require Dayora to stay open.');
    } catch(error) { console.warn('[DayoraPush] FCM setup failed',error instanceof Error?error.message:'unknown error'); this.message.set(`Web push failed: ${error instanceof Error?error.message:'check connection and server configuration'}`); }
  }
  async unregister() {
    this.stop();
    await this.pending;
    const token=this.token || this.savedToken();
    if(token)await firstValueFrom(this.http.delete('/api/push/token',{body:{token}}));
    this.remember('');
  }
  async test() {
    await this.refresh();
    try {
      const registration=await navigator.serviceWorker.getRegistration('/firebase-cloud-messaging-push-scope');
      if(!registration || Notification.permission!=='granted')throw new Error('Enable web push first.');
      await registration.showNotification('Dayora',{body:'Local notification test. This does not test backend FCM delivery.',tag:'dayora-test',data:{url:'/'}});
    } catch(error) { this.message.set(error instanceof Error?error.message:'Notification test failed.'); }
  }
}
