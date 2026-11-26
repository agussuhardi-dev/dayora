import { of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { realtimeAuthInterceptor } from './realtime.service';
import { Api } from './api';
import { RealtimeService } from './realtime.service';

class FakeSocket {
  static instances: FakeSocket[] = [];
  onopen?: () => void; onclose?: () => void; onerror?: () => void;
  onmessage?: (event: {data: unknown}) => void;
  url: string; closed = false;
  constructor(url: string) { this.url = url; FakeSocket.instances.push(this); }
  close() { this.closed = true; this.onclose?.(); }
  message(data: unknown) { this.onmessage?.({data}); }
  open() { this.onopen?.(); }
}

describe('RealtimeService', () => {
  const api = {me: () => of({id: 'acct-1'})} as unknown as Api;
  let service: RealtimeService;

  beforeEach(() => {
    FakeSocket.instances = [];
    vi.useFakeTimers();
    vi.spyOn(Math, 'random').mockReturnValue(.5);
    vi.stubGlobal('WebSocket', FakeSocket);
    localStorage.clear();
    service = new RealtimeService(api);
  });
  afterEach(() => { service.stop(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

  it('connects same-origin without token and rejects malformed or oversized payloads', async () => {
    const events: number[] = []; service.invalidated.subscribe(e => events.push(e.cursor));
    await service.start();
    const socket = FakeSocket.instances[0];
    expect(socket.url).toMatch(/^ws:\/\/.*\/ws\/events$/);
    expect(socket.url).not.toContain('?');
    socket.open();
    expect(events).toEqual([-1]);
    for (const invalid of [null, [], {}, {type:'OTHER',cursor:4}, {type:'INVALIDATE',cursor:'4'}, {type:'INVALIDATE',cursor:1.5}, {type:'INVALIDATE',cursor:Number.MAX_SAFE_INTEGER+1}]) socket.message(JSON.stringify(invalid));
    socket.message(JSON.stringify({type:'INVALIDATE',cursor:10,padding:'x'.repeat(256)}));
    socket.message(new ArrayBuffer(8));
    socket.message(JSON.stringify({type: 'INVALIDATE', cursor: 3}));
    socket.message(JSON.stringify({type: 'INVALIDATE', cursor: 3}));
    socket.message(JSON.stringify({type: 'INVALIDATE', cursor: -1}));
    socket.message('x'.repeat(257));
    socket.message('{bad');
    expect(events).toEqual([-1, 3]);
  });

  it('persists cursor per account and deduplicates persisted events', async () => {
    localStorage.setItem('dayora-realtime:acct-1', '8');
    const events: number[] = []; service.invalidated.subscribe(e => events.push(e.cursor));
    await service.start();
    FakeSocket.instances[0].message(JSON.stringify({type: 'INVALIDATE', cursor: 8}));
    FakeSocket.instances[0].message(JSON.stringify({type: 'INVALIDATE', cursor: 9}));
    expect(events).toEqual([9]);
    expect(localStorage.getItem('dayora-realtime:acct-1')).toBe('9');
  });

  it('stops the socket on HTTP 401', async () => {
    await service.start();
    TestBed.configureTestingModule({providers: [{provide: RealtimeService, useValue: service}]});
    TestBed.runInInjectionContext(() => realtimeAuthInterceptor(
      new HttpRequest('GET', '/api/tasks'),
      () => throwError(() => new HttpErrorResponse({status:401})),
    ).subscribe({error: () => {}}));
    expect(FakeSocket.instances[0].closed).toBe(true);
    vi.advanceTimersByTime(60000);
    expect(FakeSocket.instances).toHaveLength(1);
  });

  it('reconnects with bounded backoff, then stop prevents reconnect', async () => {
    await service.start();
    let socket = FakeSocket.instances[0]; socket.open(); socket.close();
    vi.advanceTimersByTime(499); expect(FakeSocket.instances).toHaveLength(1);
    vi.advanceTimersByTime(2); expect(FakeSocket.instances).toHaveLength(2);
    for (const delay of [1000,2000,4000,8000,16000,25000,25000]) {
      const count = FakeSocket.instances.length;
      FakeSocket.instances.at(-1)!.close();
      vi.advanceTimersByTime(delay-1);
      expect(FakeSocket.instances).toHaveLength(count);
      vi.advanceTimersByTime(1);
      expect(FakeSocket.instances).toHaveLength(count+1);
    }
    const count = FakeSocket.instances.length;
    socket = FakeSocket.instances.at(-1)!;
    socket.close(); service.stop(); socket.close();
    vi.advanceTimersByTime(60000);
    expect(FakeSocket.instances).toHaveLength(count);
  });
});
