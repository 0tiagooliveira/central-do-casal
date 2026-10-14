package com.centraldocasal.app

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CentralTheme(name: String = "", content: @Composable () -> Unit) {
    val rhayra = name.contains("Rhayra", true)
    // UX v34: the personal accent lives in icons and selected states; readable text
    // and solid actions use the darker companion token for contrast.
    val primary = if (rhayra) Color(0xFFAD005D) else Color(0xFF154DAF)
    val personalAccent = if (rhayra) Color(0xFFFF1493) else Color(0xFF1769FF)
    val personalContainer = if (rhayra) Color(0xFFFFE5F3) else Color(0xFFEDF4FF)
    val onPersonalContainer = if (rhayra) Color(0xFF7A124A) else Color(0xFF092D73)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = primary, onPrimary = Color.White,
            primaryContainer = personalContainer, onPrimaryContainer = onPersonalContainer,
            secondary = primary, secondaryContainer = personalContainer, onSecondaryContainer = onPersonalContainer,
            tertiary = personalAccent, background = if (rhayra) Color(0xFFFFFBFD) else Color(0xFFFCFCFE), onBackground = Color(0xFF111827),
            surface = Color.White, onSurface = Color(0xFF24262B), surfaceVariant = Color(0xFFF5F5F7),
            surfaceTint = Color.Transparent, surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFFAFAFB),
            surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFF5F5F7), surfaceContainerHighest = Color(0xFFF0F0F3),
            onSurfaceVariant = Color(0xFF63666E), outline = Color(0xFF858892), outlineVariant = Color(0xFFE5E5EA),
            error = Color(0xFFB42318), errorContainer = Color(0xFFFFE4E1), onErrorContainer = Color(0xFF7A1B14)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}

@Composable
internal fun PlanAccentIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String? = null) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(icon, description, Modifier.padding(10.dp).size(22.dp))
    }
}

@Composable
internal fun DayHero(name: String, date: String, completed: Int, count: Int, openRoutine: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE0E5ED))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Olá, ${name.substringBefore(' ').ifBlank { "vocês" }}.", style = MaterialTheme.typography.titleLarge)
                    Text(if (count == 0) "Um respiro para o seu dia." else "$completed de $count tarefas concluídas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = openRoutine) { Icon(Icons.Default.ArrowForward, "Abrir rotina de hoje", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
internal fun SummaryTile(title: String, value: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFD5DDE8))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(7.dp).size(18.dp), tint = MaterialTheme.colorScheme.tertiary) }
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
