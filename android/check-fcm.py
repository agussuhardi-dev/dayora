"""Run: python3 android/check-fcm.py (source/config regression checks; no device required)."""
from pathlib import Path

root = Path(__file__).resolve().parent
source = root / 'app/src/main/java/app/dayora/android'
gradle = (root / 'app/build.gradle.kts').read_text()
assert not (root / 'app/google-services.json').exists()
assert 'com.google.gms.google-services' not in gradle
for key in ('FIREBASE_PROJECT_ID', 'FIREBASE_MESSAGING_SENDER_ID', 'FIREBASE_APP_ID', 'FIREBASE_API_KEY'):
    assert key in gradle
    assert key + '=' in (root / 'local.properties.example').read_text()
data = (source / 'Data.kt').read_text()
sync = data.split('suspend fun sync()=')[1]
assert sync.index('csrf()') < sync.index('registerPushToken()')
assert 'runCatching{registerPushToken()}.onFailure' in sync
assert 'cachePushToken(token)' in (source / 'DayoraMessagingService.kt').read_text()
assert 'notificationPermission.launch' in (source / 'FocusScreen.kt').read_text()
assert 'r.pushStatus.collectAsState()' in (source / 'ExtraScreens.kt').read_text()
print('FCM source/config checks passed (not a delivery test).')
