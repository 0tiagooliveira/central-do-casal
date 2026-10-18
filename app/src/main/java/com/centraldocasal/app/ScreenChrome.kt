package com.centraldocasal.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact shared heading; actions remain owned by the destination screen. */
@Composable
internal fun SectionHero(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    onActionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp).animateContentSize(tween(centralMotionDurationMillis(MotionKind.LAYOUT))), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(7.dp).size(20.dp), tint = MaterialTheme.colorScheme.tertiary) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (LocalDensity.current.fontScale > 1.15f) 3 else 2)
                }
            }
            if (!onActionLabel.isNullOrBlank() && onAction != null) {
                FilledTonalButton(onClick = onAction) { Text(onActionLabel) }
            }
    }
}

/** Selecting a date never changes task completion or synthesizes agenda data. */
@Composable
internal fun WeekRibbon(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val locale = Locale.forLanguageTag("pt-BR")
    val start = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val end = start.plusDays(6)
    val today = LocalDate.now()
    val shortDate = DateTimeFormatter.ofPattern("dd MMM", locale)
    val fullDate = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' uuuu", locale)
    val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    val weekDescription = "Semana de ${start.format(fullDate)} até ${end.format(fullDate)}"
    val enlarged = LocalDensity.current.fontScale > 1.15f
    val dateButton: @Composable (Int, Modifier) -> Unit = { index, modifier ->
        val date = start.plusDays(index.toLong())
        OutlinedButton(onClick = { onSelect(date) }, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "${date.format(fullDate)}${if (date == today) ", hoje" else ""}${if (date == selected) ", selecionado" else ""}" }, contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (date == selected) Color.White else MaterialTheme.colorScheme.onSurface)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (date == today) "Hoje" else date.format(weekday).trimEnd('.').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) { Icon(Icons.Default.KeyboardArrowLeft, "Semana anterior") }
                Text("${start.format(shortDate)} — ${end.format(shortDate)} · ${end.year}", modifier = Modifier.weight(1f).semantics { contentDescription = weekDescription }, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) { Icon(Icons.Default.KeyboardArrowRight, "Próxima semana") }
            }
            if (enlarged) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { (0..6).forEach { dateButton(it, Modifier.widthIn(min = 60.dp)) } }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { (0..6).forEach { dateButton(it, Modifier.weight(1f)) } }
            if (selected != today) TextButton(onClick = { onSelect(today) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Voltar para hoje") }
    }
}
