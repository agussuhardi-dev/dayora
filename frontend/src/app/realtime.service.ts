import { Injectable, Injector, signal } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, firstValueFrom, Subject, throwError } from 'rxjs';
import { Api } from './api';

export interface InvalidateEvent { type: 'INVALIDATE'; cursor: number }

@Injectable({providedIn:'root'})
export class RealtimeService {
  private readonly events = new Subject<InvalidateEvent>();
  readonly invalidated = this.events.asObservable();
  readonly connected = signal(false);
  private socket?: WebSocket;
  private account?: string;
  private stopped = true;
  private generation = 0;
  private retry = 0;
  private timer?: ReturnType<typeof setTimeout>;
  private cursor = -1;
  private readonly online = () => {
    if (this.stopped) return;
    if (this.timer) clearTimeout(this.timer);
    this.timer = undefined;
    const ws = this.socket;
    if (ws) {
      this.socket = undefined; this.connected.set(false);
      ws.onopen = ws.onmessage = ws.onerror = ws.onclose = null;
      ws.close();
    }
    this.connect();
  };

  constructor(private api: Api) { window.addEventListener('online', this.online); }

  async start() {
    this.stop();
    const generation = this.generation;
    try {
      const {id} = await firstValueFrom(this.api.me());
      if (generation !== this.generation) return;
      this.account = id;
      try {
        const saved = localStorage.getItem(`dayora-realtime:${id}`);
        const cursor = saved === null ? -1 : Number(saved);
        this.cursor = Number.isSafeInteger(cursor) && cursor >= 0 ? cursor : -1;
      } catch { this.cursor = -1; }
      if (generation !== this.generation) return;
      this.stopped = false;
      this.connect();
    } catch { this.stop(); }
  }

  stop() {
    this.generation++; this.stopped = true; this.connected.set(false); this.retry = 0;
    if (this.timer) clearTimeout(this.timer);
    const socket = this.socket; this.socket = undefined;
    socket?.close();
  }

  private schedule() {
    if (this.stopped) return;
    const base = Math.min(25000, 500 * 2 ** Math.min(this.retry++, 6));
    this.timer = setTimeout(() => this.connect(), base * (.8 + Math.random() * .4));
  }

  private connect() {
    if (this.stopped || this.socket || typeof WebSocket === 'undefined') return;
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    try {
      const ws = this.socket = new WebSocket(`${scheme}://${location.host}/ws/events`);
      ws.onopen = () => {
        if (this.socket !== ws) return;
        this.retry = 0; this.connected.set(true);
        this.events.next({type:'INVALIDATE', cursor:this.cursor});
      };
      ws.onmessage = e => { if (this.socket === ws) this.message(e.data); };
      ws.onerror = () => ws.close();
      ws.onclose = () => {
        if (this.socket !== ws) return;
        this.socket = undefined; this.connected.set(false); this.schedule();
      };
    } catch { this.socket = undefined; this.schedule(); }
  }

  invalidate() { this.events.next({type:'INVALIDATE', cursor:this.cursor}); }

  private message(raw: unknown) {
    if (typeof raw !== 'string' || raw.length > 256) return;
    try {
      const value = JSON.parse(raw) as Partial<InvalidateEvent> | null;
      if (!value || value.type !== 'INVALIDATE' || typeof value.cursor !== 'number' || !Number.isSafeInteger(value.cursor) || value.cursor < 0 || value.cursor <= this.cursor) return;
      this.cursor = value.cursor;
      try { localStorage.setItem(`dayora-realtime:${this.account}`, String(this.cursor)); } catch { /* Storage may be disabled. */ }
      this.events.next({type:'INVALIDATE', cursor:this.cursor});
    } catch { /* Invalid socket payloads are ignored. */ }
  }
}

// Resolve only on a failed response: Api never depends on realtime.
export const realtimeAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  return next(req).pipe(catchError((error:HttpErrorResponse) => {
    if (error.status === 401) injector.get(RealtimeService).stop();
    return throwError(() => error);
  }));
};
