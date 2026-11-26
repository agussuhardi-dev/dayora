import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { PushService } from './push.service';
import { RealtimeService } from './realtime.service';

describe('PushService', () => {
  afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });
  it('activates registration after first permission grant', async () => {
    vi.stubGlobal('Notification', { requestPermission: async () => 'granted' });
    const service = new PushService({} as HttpClient, {} as RealtimeService);
    const start = vi.spyOn(service, 'start').mockResolvedValue();
    await service.enable();
    expect(start).toHaveBeenCalledOnce();
  });
  it('reports missing config without attempting Firebase registration', async () => {
    const http = { get: (url:string) => of(url.endsWith('/status') ? {enabled:true,reason:'ready',tokenCount:0} : {}) };
    const service = new PushService(http as HttpClient, {} as RealtimeService);
    await service.start();
    expect(service.message()).toContain('Web push disabled: missing');
    expect(service.busy()).toBe(false);
  });
});
