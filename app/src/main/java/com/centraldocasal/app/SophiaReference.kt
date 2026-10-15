package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.occurs
import com.centraldocasal.app.domain.occurrenceId
import java.time.LocalDate

/** O nome Sophia representa dicas locais verificadas: sem fingir chat generativo ou dados externos. */
@Composable
internal fun SophiaReferenceScreen(
    session:SessionState,data:DataState,
    requestLocationPermission:()->Unit,
    requestNotificationsPermission:()->Unit,
    navigate:(String)->Unit
) {
    val today=LocalDate.now()
    val tasks=data.records.filter { it.kind=="task" && occurs(it,today) &&
        SophiaMomentPlanner.belongsTo(it,session.name) }
    val pending=tasks.filter { task ->
        data.records.none { it.kind=="occurrence" && it.id==occurrenceId(task.id,today) && it.flag("done") }
    }.sortedBy { it.text("time").ifBlank { "99:99" } }
    val pendingShopping=data.baskets.values.sumOf { it.entries.count { e -> !e.bought } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ReferenceHeading("Sophia","Sua assistente contextual do dia a dia")
                    Spacer(Modifier.height(9.dp))
                    Text("Como posso ajudar ${session.name.substringBefore(' ').ifBlank { "você" }} hoje?",
                        style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("Organize o que importa, sem misturar seus dados com os de outra conta.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape=RoundedCornerShape(20.dp),
                    color=MaterialTheme.colorScheme.primaryContainer) {
                    Text("✦",Modifier.padding(18.dp),
                        style=MaterialTheme.typography.headlineMedium,
                        color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { WeatherCard(requestLocationPermission,session.uid,session.name,
            data.records,requestNotificationsPermission) }
        item {
            Text("Seu dia hoje",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            ReferenceCard(Modifier.fillMaxWidth()) {
                Text("${pending.size} tarefas pendentes para você",
                    style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                pending.take(3).forEach { task ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange,null,Modifier.size(19.dp),
                            tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(task.text("title"),modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.bodyMedium)
                        Text(task.text("time"),style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick={navigate("Rotina")}) { Text("Ver minha rotina") }
            }
        }
        item {
            Text("Ações rápidas",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                SophiaShortcut("Rotina","Planejar dia",Modifier.weight(1f)) { navigate("Rotina") }
                SophiaShortcut("Compras","$pendingShopping itens",Modifier.weight(1f)) { navigate("Compras") }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                SophiaShortcut("Lembretes","Seus avisos",Modifier.weight(1f)) { navigate("Lembretes") }
                SophiaShortcut("Nós","Nossa jornada",Modifier.weight(1f)) { navigate("Nós") }
            }
        }
        item {
            ReferenceEmpty("Conversa com IA em preparação",
                "As dicas e os avisos atuais usam regras locais e previsões verificáveis. Um chat generativo só aparecerá quando tiver backend seguro, permissões e testes concluídos.")
        }
    }
}

@Composable
private fun SophiaShortcut(title:String,detail:String,modifier:Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=80.dp),
        shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("✦ $title",style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.primary)
            Text(detail,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
