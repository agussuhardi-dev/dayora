/* Separate push scope; Angular's ngsw-worker.js keeps control of application pages. */
self.addEventListener('notificationclick', event => {
  event.stopImmediatePropagation();
  event.notification.close();
  console.info('[DayoraPush] Background notification clicked');
  event.waitUntil((async () => {
    // Never navigate to a URL supplied by a push payload.
    const url = new URL('/', self.location.origin).href;
    const windows = await self.clients.matchAll({type:'window', includeUncontrolled:true});
    const client = windows.find(client => new URL(client.url).origin === self.location.origin);
    if (client) { await client.navigate(url); await client.focus(); }
    else await self.clients.openWindow(url);
  })());
});
importScripts('https://www.gstatic.com/firebasejs/12.19.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/12.19.0/firebase-messaging-compat.js');
const params = new URL(self.location.href).searchParams;
const config = Object.fromEntries(['apiKey','authDomain','projectId','messagingSenderId','appId'].map(key => [key, params.get(key) || '']));
if (Object.values(config).every(Boolean)) {
  firebase.initializeApp(config);
  firebase.messaging().onBackgroundMessage(payload => {
    console.info('[DayoraPush] FCM background message received', {messageId:payload.messageId, browser:self.navigator.userAgent});
    // FCM displays notification payloads itself; only data-only messages need a notification.
    if (!payload.notification) return self.registration.showNotification('Dayora', {
      body:'Your Dayora data changed. Open to refresh.', tag:'dayora-sync', data:{url:'/'},
    });
  });
}
