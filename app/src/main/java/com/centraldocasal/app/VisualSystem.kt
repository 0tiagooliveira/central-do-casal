package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val ReminderBell = ImageVector.Builder("Lembretes",24.dp,24.dp,24f,24f).apply {
    path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f) {
        moveTo(5f,17f); lineTo(19f,17f); lineTo(17f,14f); lineTo(17f,9f)
        curveTo(17f,2f,7f,2f,7f,9f); lineTo(7f,14f); close()
        moveTo(10f,20f); curveTo(11f,22f,13f,22f,14f,20f)
    }
}.build()

private fun lineIcon(name: String, drawing: PathBuilder.() -> Unit) = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
    path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathBuilder=drawing)
}.build()
internal val HomeLine = lineIcon("Hoje") { moveTo(3f,10f);lineTo(12f,3f);lineTo(21f,10f);moveTo(5f,9f);lineTo(5f,21f);lineTo(10f,21f);lineTo(10f,14f);lineTo(14f,14f);lineTo(14f,21f);lineTo(19f,21f);lineTo(19f,9f) }
internal val BasketLine = lineIcon("Compras") { moveTo(3f,9f);lineTo(21f,9f);lineTo(18f,21f);lineTo(6f,21f);close();moveTo(7f,9f);lineTo(10f,3f);moveTo(17f,9f);lineTo(14f,3f);moveTo(9f,13f);lineTo(10f,18f);moveTo(15f,13f);lineTo(14f,18f) }
internal val CalendarLine = lineIcon("Rotina") { moveTo(4f,5f);lineTo(20f,5f);lineTo(20f,21f);lineTo(4f,21f);close();moveTo(8f,3f);lineTo(8f,7f);moveTo(16f,3f);lineTo(16f,7f);moveTo(4f,10f);lineTo(20f,10f);moveTo(8f,15f);lineTo(11f,18f);lineTo(16f,13f) }
internal val PlanLine = lineIcon("Planos") { moveTo(5f,22f);lineTo(5f,3f);curveTo(10f,0f,14f,7f,20f,3f);lineTo(20f,14f);curveTo(14f,18f,10f,11f,5f,14f) }
internal val HeartLine = lineIcon("Nós") { moveTo(12f,21f);curveTo(9f,18f,2f,13f,2f,8f);curveTo(2f,2f,9f,1f,12f,6f);curveTo(15f,1f,22f,2f,22f,8f);curveTo(22f,13f,15f,18f,12f,21f);close() }

/** Wraps at large font scales instead of squeezing selectable labels. */
@Composable
internal fun ChoiceStrip(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            FilterChip(selected=option==selected,onClick={onSelect(option)},label={Text(option)},
                shape=RoundedCornerShape(14.dp),
                border=BorderStroke(1.dp,if(option==selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant),
                colors=FilterChipDefaults.filterChipColors(containerColor=MaterialTheme.colorScheme.surface,
                    selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer))
        }
    }
}

@Composable
internal fun QuietCard(title: String, subtitle: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(!subtitle.isNullOrBlank()) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
