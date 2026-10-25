package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Base comum das referências: áreas brancas, contraste e a cor individual só nos destaques. */
@Composable
internal fun ReferenceHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,
            color=MaterialTheme.colorScheme.primary)
        if(!subtitle.isNullOrBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ReferenceTabs(items: List<String>, selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        items.forEach { item ->
            val active=item==selected
            Surface(onClick={select(item)},shape=RoundedCornerShape(30.dp),
                color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier=Modifier.heightIn(min=44.dp)) {
                Box(Modifier.padding(horizontal=16.dp,vertical=11.dp),contentAlignment=Alignment.Center) {
                    Text(item,style=MaterialTheme.typography.labelMedium,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,
                        color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines=1)
                }
            }
        }
    }
}

@Composable
internal fun ReferenceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
internal fun ReferenceEmpty(title: String, detail: String) {
    ReferenceCard(Modifier.fillMaxWidth()) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ReferenceProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=modifier.fillMaxWidth().height(7.dp),
        color=MaterialTheme.colorScheme.tertiary,trackColor=MaterialTheme.colorScheme.primaryContainer)
}
