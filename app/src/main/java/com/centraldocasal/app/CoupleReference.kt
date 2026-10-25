package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState

@Composable
internal fun CoupleLandingReference(
    data:DataState,session:SessionState,
    onJourney:()->Unit,onMoments:()->Unit,onMemories:()->Unit,onCreateMoment:()->Unit
) {
    val scheme=MaterialTheme.colorScheme
    val pending=data.records.filter { it.kind=="quest" && !it.flag("cancelled") &&
        (it.fields["completed"] as? List<*>)?.distinct()?.size != 2 }
    val moments=data.records.count { it.kind=="moment" }
    val completed=data.records.count { it.kind=="quest" &&
        (it.fields["completed"] as? List<*>)?.distinct()?.size==2 }
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Surface(shape=RoundedCornerShape(22.dp),color=scheme.primaryContainer.copy(alpha=.50f)) {
            Column(Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(scheme.primaryContainer,scheme.surfaceContainerLow)))
                .padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("✦ NOSSO PRÓXIMO MOMENTO",style=MaterialTheme.typography.labelSmall,
                    fontWeight=FontWeight.Bold,color=scheme.primary)
                Text(pending.firstOrNull()?.text("title")?.ifBlank { "Um tempo para nós dois" }
                    ?: "Um tempo para nós dois",style=MaterialTheme.typography.headlineMedium,
                    fontWeight=FontWeight.Bold)
                Text(if(pending.isNotEmpty())"Vocês já têm um convite em aberto."
                    else "Uma ideia da jornada para planejarem juntos quando puderem.",
                    style=MaterialTheme.typography.bodySmall,
                    color=scheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick=onJourney,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)) {
                        Text(if(pending.isNotEmpty())"Ver convite" else "Planejar agora")
                    }
                    OutlinedButton(onClick=onMoments,shape=RoundedCornerShape(14.dp)) {
                        Text("Lembranças")
                    }
                }
            }
        }
        Text("Nossa jornada",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            CoupleShortcut("Jogo da semana","Convites leves",onJourney,Modifier.weight(1f))
            CoupleShortcut("Linha do tempo","$moments momentos",onMoments,Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            CoupleShortcut("Cartas do casal","Descobrir juntos",onJourney,Modifier.weight(1f))
            CoupleShortcut("Conquistas","$completed lembranças",onMemories,Modifier.weight(1f))
        }
        ReferenceCard(Modifier.fillMaxWidth()) {
            Text("Uma história construída por vocês ♡",
                style=MaterialTheme.typography.titleSmall,color=scheme.primary)
            Text(if(session.local)"Conectem os dois perfis para compartilhar convites e lembranças."
                else "As experiências aparecem aqui quando vocês as registram e confirmam.",
                style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant)
            TextButton(onClick=onCreateMoment) { Text("Guardar um momento") }
        }
    }
}

@Composable
private fun CoupleShortcut(title:String,subtitle:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=92.dp),
        shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.75f))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("♡",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
            Text(subtitle,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
