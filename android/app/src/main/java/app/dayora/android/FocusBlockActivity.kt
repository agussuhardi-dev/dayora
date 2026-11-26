package app.dayora.android

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

class FocusBlockActivity : ComponentActivity() {
    private var blockedPackage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getSharedPreferences("visual-accessibility", 0).getBoolean("keepScreenOn", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        blockedPackage = intent.getStringExtra("blockedPackage").orEmpty()
        setContent { DayoraTheme { BlockedScreen() } }
    }

    @Composable private fun BlockedScreen() {
        var remaining by remember { mutableLongStateOf(focusRemaining(this)) }
        LaunchedEffect(Unit) {
            while (activeFocus(this@FocusBlockActivity)) { remaining = focusRemaining(this@FocusBlockActivity); delay(1000) }
            finish()
        }
        val app = remember(blockedPackage) { runCatching { packageManager.getApplicationInfo(blockedPackage, 0) }.getOrNull() }
        val appName = remember(app) { app?.let { packageManager.getApplicationLabel(it).toString() } ?: "This app" }
        val icon = remember(app) { app?.loadIcon(packageManager) }
        val seconds = (remaining + 999) / 1000
        BackHandler { goHome() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, "Blocked", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(24.dp))
                Text("Stay focused", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(24.dp))
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        AppIcon(icon)
                        Spacer(Modifier.width(14.dp))
                        Column { Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Blocked during focus", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Spacer(Modifier.height(30.dp))
                Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Text("remaining", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(34.dp))
                Button(onClick = { startActivity(Intent(this@FocusBlockActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Back to Dayora") }
                TextButton(onClick = { goHome() }) { Text("Home screen") }
            }
        }
    }

    @Composable private fun AppIcon(icon: Drawable?) {
        val density = LocalDensity.current
        val painter = remember(icon, density) { icon?.let { BitmapPainter(it.toBitmap((48 * density.density).toInt(), (48 * density.density).toInt()).asImageBitmap()) } }
        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = Color.Transparent) {
            if (painter != null) Icon(painter, null, Modifier.padding(2.dp), tint = Color.Unspecified) else Icon(Icons.Filled.Lock, null, Modifier.padding(12.dp))
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); blockedPackage = intent.getStringExtra("blockedPackage").orEmpty() }
    private fun goHome() { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
}
