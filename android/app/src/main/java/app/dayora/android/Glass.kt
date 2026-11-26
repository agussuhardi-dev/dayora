package app.dayora.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val LocalSolidSurfaces = compositionLocalOf { false }

// ponytail: translucent gradients, not backdrop blur; keep content sharp on every supported API.
@Composable fun Modifier.dayoraGlass(hero: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val solid = LocalSolidSurfaces.current
    val shape = RoundedCornerShape(26.dp)
    val tint = if (hero) colors.primaryContainer else colors.surface
    return shadow(if (solid) 0.dp else 4.dp, shape)
        .clip(shape)
        .background(Brush.linearGradient(listOf(tint.copy(alpha = if (solid) 1f else .96f), colors.surface.copy(alpha = if (solid) 1f else .82f))))
        .border(1.dp, colors.outlineVariant, shape)
}

@Composable fun GlassCard(modifier: Modifier = Modifier, hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.dayoraGlass(hero), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassCard(Modifier.fillMaxWidth().padding(24.dp), hero = true, content = content)
    }
}

