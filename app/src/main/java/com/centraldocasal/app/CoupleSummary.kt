package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun memoryDate(record: Record): String = runCatching { LocalDate.parse(record.text("date")).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) }.getOrDefault("Data a confirmar")

@Composable
internal fun CoupleSummary(data: DataState, session: SessionState, onMoments: () -> Unit, onJourney: () -> Unit, onCreateMoment: () -> Unit) {
    val moments = data.records.filter { it.kind == "moment" && !it.isPrivate }
    val experiences = data.records.filter { it.kind == "quest" && !it.isPrivate && !it.flag("cancelled") && (it.fields["completed"] as? List<*>)?.distinct()?.size == 2 }
    val memories = (moments + experiences).distinctBy { it.id }.sortedByDescending { it.text("date") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = memories.firstOrNull { it.id == selectedId }
    val view = LocalView.current
    val options = LocalExperience.current
    fun act(action: () -> Unit) { AndroidInteractions.haptic(view, options.haptics); AndroidInteractions.clickSound(view, options.sounds); action() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nossa parceria",style=MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("Nosso álbum", moments.size.toString(), if (session.local) "momentos neste aparelho" else "momentos compartilhados", Icons.Default.Favorite, MaterialTheme.colorScheme.primary, { act(onMoments) }, Modifier.weight(1f))
            SummaryTile("Fazer algo juntos", experiences.size.toString(), "experiências confirmadas", Icons.Default.Star, MaterialTheme.colorScheme.primary, { act(onJourney) }, Modifier.weight(1f))
        }
        OutlinedButton(onClick = { act(onCreateMoment) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Guardar um registro") }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nossa constelação", style = MaterialTheme.typography.titleMedium)
                if (memories.isEmpty()) {
                    Text("Cada lembrança compartilhada poderá aparecer aqui. As aventuras entram depois das duas confirmações.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { act(onCreateMoment) }, contentPadding = PaddingValues(0.dp)) { Text("Guardar a primeira lembrança") }
                } else {
                    Text("Toque em uma estrela para revisitar uma lembrança.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val visible = memories.take(12)
                    val accent = MaterialTheme.colorScheme.primary
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth < 280.dp) 3 else 4
                        val rows = (visible.size + columns - 1) / columns
                        val cellWidth = maxWidth / columns
                        Box(Modifier.fillMaxWidth().height((rows * 66).dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val centers = visible.indices.map { index -> Offset((index % columns + .5f) * size.width / columns, (index / columns * 66 + 28 + if (index % 2 == 0) 0 else 8).dp.toPx()) }
                                centers.zipWithNext().forEach { (from, to) -> drawLine(Color(0xFFD5DDE8), from, to, strokeWidth = 1.dp.toPx()) }
                            }
                            visible.forEachIndexed { index, memory ->
                                val isSelected = selectedId == memory.id
                                IconButton(onClick = { act { selectedId = memory.id } }, modifier = Modifier.offset(x = cellWidth * (index % columns) + (cellWidth - 48.dp) / 2, y = (index / columns * 66 + 4 + if (index % 2 == 0) 0 else 8).dp).size(48.dp).testTag("constellation-star-${memory.id}").semantics { contentDescription = "${memory.text("title", "Lembrança")}, ${memoryDate(memory)}${if (isSelected) ", selecionada" else ""}" }) {
                                    Icon(Icons.Default.Star, null, Modifier.size(if (isSelected) 32.dp else 25.dp), tint = if (isSelected) accent else Color(0xFF52647F))
                                }
                            }
                        }
                    }
                    if (selected != null) {
                        HorizontalDivider(color = Color(0xFFE0E5ED))
                        Text(selected.text("title", "Lembrança"), style = MaterialTheme.typography.titleMedium)
                        Text("Registro: ${memoryDate(selected)} · ${if (selected.kind == "quest") "duas confirmações" else if (session.local) "neste aparelho" else "momento compartilhado"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selected.text("note").isNotBlank()) Text(selected.text("note"), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { act(if (selected.kind == "quest") onJourney else onMoments) }, contentPadding = PaddingValues(0.dp)) { Text("Abrir ${if (selected.kind == "quest") "jornada" else "álbum"}") }
                    }
                    if (memories.size > visible.size) TextButton(onClick = { act(onMoments) }, contentPadding = PaddingValues(0.dp)) { Text("Ver nosso álbum") }
                }
            }
        }
    }
}
