package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowForward

import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private fun homeMoney(value: Long) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(value, 2))
private val homeInk = Color(0xFF202C43)
private val homeMuted = Color(0xFF8490A1)
private val homeOutline = Color(0xFFE6E9EF)
private val homeLocale = Locale.forLanguageTag("pt-BR")

internal fun homeGreeting(hour: Int) = when(hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

/** A tarefa de maior utilidade agora, sem misturar tarefas atribuídas ao parceiro. */
internal fun homeFocus(tasks: List<Record>, records: List<Record>, today: LocalDate, now: LocalTime): Record? {
    val done = records.filter { it.kind == "occurrence" && it.flag("done") }.map { it.id }.toSet()
    return tasks.filter { occurrenceId(it.id, today) !in done }
        .sortedWith(compareBy<Record> {
            val time = runCatching { LocalTime.parse(it.text("time")) }.getOrNull()
            when {
                time == null -> 3
                !time.isBefore(now.minusMinutes(20)) -> 0
                else -> 1
            }
        }.thenBy {
            val time = runCatching { LocalTime.parse(it.text("time")) }.getOrNull()
            if (time == null) Long.MAX_VALUE else kotlin.math.abs(java.time.Duration.between(now,time).toMinutes())
        }.thenBy { it.id })
        .firstOrNull()
}

@Composable
internal fun HomeExperienceScreen(
    vm: CentralViewModel,
    session: SessionState,
    data: DataState,
    navigate: (String) -> Unit,
    selectTab: (String) -> Unit,
    requestLocationPermission: () -> Unit = {},
    requestNotificationsPermission: () -> Unit = {}
) {
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    val palette = MaterialTheme.colorScheme
    val accent = palette.tertiary
    val bg = palette.primaryContainer.copy(alpha = 0.58f)
    val name = session.name.substringBefore(' ').ifBlank { "você" }
    val tasks = data.records.filter {
        it.kind == "task" && occurs(it,today) && SophiaMomentPlanner.belongsTo(it,session.name)
    }
    val focus = homeFocus(tasks, data.records, today, now.toLocalTime())
    val completed = tasks.count { task ->
        data.records.any { it.id == occurrenceId(task.id,today) && it.kind == "occurrence" && it.flag("done") }
    }
    val sharedTasks = tasks.filter { it.text("owner","Ambos").equals("Ambos",true) }
    val sharedDone = sharedTasks.count { task ->
        data.records.any { it.id == occurrenceId(task.id,today) && it.kind == "occurrence" && it.flag("done") }
    }
    val cares = careEventsForDay(data.records,today)
    val canRecordCare = session.local || session.household.isNotBlank()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val options = LocalExperience.current
    val view = LocalView.current
    var showRest by rememberSaveable(session.uid) { mutableStateOf(false) }
    fun touch(action: () -> Unit) {
        AndroidInteractions.haptic(view,options.haptics)
        AndroidInteractions.clickSound(view,options.sounds)
        action()
    }
    fun saveCare(kind: CareKind) {
        if (!canRecordCare || busy) return
        touch {
            val at = System.currentTimeMillis()
            vm.action("${kind.label} registrada") {
                vm.repo.save(Record(
                    id = "care-${session.uid}-${UUID.randomUUID()}",
                    kind = "careEvent",
                    isPrivate = false,
                    fields = mapOf(
                        "careType" to kind.id, "subject" to kind.subject,
                        "actorUid" to session.uid, "actorName" to session.name,
                        "occurredAt" to at, "date" to LocalDate.now().toString()
                    )
                ))
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(palette.background).testTag("home-redesign"),
        contentPadding = PaddingValues(start = 18.dp,end = 18.dp,top = 15.dp,bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE · dd 'de' MMMM",homeLocale))
                        .uppercase(homeLocale),style=paletteTypographyLabel(),color=palette.primary)
                    Text("${homeGreeting(now.hour)}, ${name}! ♡",style=MaterialTheme.typography.headlineMedium,
                        fontWeight=FontWeight.Bold,color=homeInk)
                    Text("Sua rotina, no seu tempo.",style=MaterialTheme.typography.bodyMedium,color=homeMuted)
                }
                Surface(
                    onClick = { touch { navigate("Configurações") } },
                    modifier=Modifier.size(48.dp).semantics { contentDescription = "Abrir meu perfil" },
                    shape=RoundedCornerShape(16.dp),color=palette.primaryContainer
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text(name.take(1).uppercase(homeLocale),fontSize=22.sp,fontWeight=FontWeight.Bold,color=palette.primary)
                    }
                }
                IconButton(onClick={touch{navigate("Lembretes")}},
                    modifier=Modifier.size(48.dp).semantics{contentDescription="Abrir lembretes"}) {
                    Icon(ReminderBell,null,tint=palette.primary)
                }
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(0.dp)) {
                WeatherCard(requestLocationPermission,session.uid,session.name,data.records,requestNotificationsPermission)
                TextButton(
                    onClick={touch{navigate("Sophia")}},
                    modifier=Modifier.heightIn(min=48.dp).testTag("home-open-sophia")
                ) { Text("Conversar com a Sophia  →") }
            }
        }
        item {
            HomeSection("O QUE IMPORTA AGORA", icon = "☀", accent=accent, trailing = if(now.hour<12) "Manhã" else if(now.hour<18) "Tarde" else "Noite",
                onTrailing = { touch { selectTab("Rotina") } })
            Spacer(Modifier.height(8.dp))
            Card(
                shape=RoundedCornerShape(23.dp),
                colors=CardDefaults.cardColors(containerColor=bg),
                border=BorderStroke(1.dp,palette.primaryContainer),
                modifier=Modifier.fillMaxWidth().testTag("home-focus")
            ) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=RoundedCornerShape(17.dp),color=Color.White,modifier=Modifier.size(55.dp)) {
                            Box(contentAlignment=Alignment.Center) { Text(if(focus==null) "✓" else focusHomeSymbol(focus.text("title")),fontSize=28.sp,color=accent) }
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(focus?.text("title")?.ifBlank { "Próxima atividade" } ?: "Tudo em dia por enquanto",
                                style=MaterialTheme.typography.titleLarge,color=homeInk,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(if(focus==null) "Não há tarefas pendentes para hoje."
                                else listOf(focus.text("time"),focus.text("category")).filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"Sua atividade"},
                                style=MaterialTheme.typography.bodySmall,color=homeMuted)
                        }
                    }
                    Button(
                        onClick={touch{
                            if(focus!=null) vm.complete(focus,today,true) else selectTab("Rotina")
                        }},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("home-focus-action"),
                        shape=RoundedCornerShape(14.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=palette.primary)
                    ) {
                        Icon(if(focus==null) Icons.Default.Add else Icons.Default.Check,null,Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if(focus==null) "Abrir minha rotina" else "Concluir atividade",style=MaterialTheme.typography.labelLarge)
                    }
                    Row(Modifier.fillMaxWidth().clickable { touch { selectTab("Rotina") } }.heightIn(min=48.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("$completed de ${tasks.size} atividades concluídas hoje",
                            modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=palette.primary)
                        Icon(Icons.Default.ArrowForward,null,Modifier.size(18.dp),tint=accent)
                    }
                }
            }
        }
        item {
            HomeSection("Nosso ritmo ✦",accent=accent,trailing="Sem cobrança")
            Spacer(Modifier.height(8.dp))
            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(23.dp),color=Color.White,
                border=BorderStroke(1.dp,homeOutline)) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Minha contribuição",style=MaterialTheme.typography.bodySmall,color=homeMuted)
                            Text("$completed / ${tasks.size}",style=MaterialTheme.typography.headlineMedium,
                                fontWeight=FontWeight.Bold,color=palette.primary)
                            Text("atividades de hoje",style=MaterialTheme.typography.labelSmall,color=homeMuted)
                        }
                        VerticalDivider(Modifier.height(54.dp),color=homeOutline)
                        Column(Modifier.weight(1f).padding(start=17.dp)) {
                            Text("Nossa dupla ♡",style=MaterialTheme.typography.bodySmall,color=homeMuted)
                            Text("$sharedDone / ${sharedTasks.size}",style=MaterialTheme.typography.headlineMedium,
                                fontWeight=FontWeight.Bold,color=homeInk)
                            Text("atividades compartilhadas",style=MaterialTheme.typography.labelSmall,color=homeMuted)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if(tasks.isEmpty()) 0f else completed.toFloat()/tasks.size },
                        modifier=Modifier.fillMaxWidth().height(6.dp),
                        color=accent,trackColor=palette.primaryContainer
                    )
                }
            }
        }
        item {
            HomeSection("Cuidados da família",accent=accent,trailing="Histórico",
                onTrailing={touch{selectTab("Rotina")}})
            Spacer(Modifier.height(3.dp))
            Text("Registro rápido para o Lucca e a Mel.",style=MaterialTheme.typography.bodySmall,color=homeMuted)
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = homeCareColumns(maxWidth.value, LocalDensity.current.fontScale)
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    careKinds.chunked(columns).forEach { group ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            group.forEach { kind ->
                                HomeCareTile(
                                    kind=kind,
                                    count=cares.count { it.text("careType") == kind.id },
                                    accent=accent,
                                    tint=palette.primaryContainer,
                                    enabled=canRecordCare && !busy,
                                    modifier=Modifier.weight(1f),
                                    onAdd={saveCare(kind)}
                                )
                            }
                            repeat(columns-group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick={showRest = !showRest},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                shape=RoundedCornerShape(16.dp)
            ) {
                Text(if(showRest) "Mostrar menos" else "Ver compras, planos e outros detalhes")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward,null,Modifier.size(18.dp))
            }
        }
        if(showRest) {
            item { HomePanel("Compras") {
                val pending=data.baskets.values.sumOf { basket -> basket.entries.count { !it.bought } }
                Text("$pending itens pendentes",style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={touch{selectTab("Compras")}}) { Text("Abrir minha lista") }
            } }
            item { HomePanel("Outros recursos") {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Planos","Nossa vida","Finanças","Saúde","Casa","Segurança","Configurações").forEach { destination ->
                        AssistChip(onClick={touch{navigate(destination)}},label={Text(destination)})
                    }
                }
            } }
            item { Text("Próximas atividades",style=MaterialTheme.typography.titleMedium) }
            items(tasks.filterNot { task ->
                data.records.any { it.id == occurrenceId(task.id,today) && it.kind == "occurrence" && it.flag("done") }
            }.take(8),key={it.id}) { task ->
                OutlinedCard(onClick={touch{selectTab("Rotina")}},shape=RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(task.text("title"),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        Text(task.text("time"),style=MaterialTheme.typography.labelMedium,color=homeMuted)
                    }
                }
            }
            data.baskets.values.filter { it.budget>0 && it.entries.isNotEmpty() }.forEach { basket ->
                item(key="home-budget-${basket.market}") {
                    val bought=basket.entries.filter { it.bought }
                    val known=bought.mapNotNull { e ->
                        if(e.actual!=null && e.unitReviewed && e.quantityReviewed && e.unit in Entry.allowedUnits)
                            runCatching { Money.subtotal(e.actual,e.actualMilli) }.getOrNull() else null
                    }
                    HomePanel("${basket.market} · orçamento") {
                        val remaining=basket.budget-known.sum()
                        Text(if(known.size<bought.size) "Saldo parcial" else
                            if(remaining<0) "Excedido em ${homeMoney(-remaining)}" else "Disponível: ${homeMoney(remaining)}")
                        TextButton(onClick={touch{selectTab("Compras")}}) { Text("Ver compras") }
                    }
                }
            }
        }
    }
}

private fun focusHomeSymbol(title: String): String {
    val text=title.lowercase(homeLocale)
    return when {
        "lucca" in text || "banho" in text -> "♡"
        "mel" in text || "ração" in text -> "♧"
        "comida" in text || "jantar" in text || "almoço" in text -> "♨"
        "faculdade" in text || "estudar" in text -> "✎"
        "buscar" in text || "carro" in text -> "↗"
        else -> "✓"
    }
}

@Composable
private fun paletteTypographyLabel() = MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.Bold)

@Composable
private fun HomeSection(title: String, accent: Color, trailing: String="", onTrailing: (() -> Unit)?=null, icon: String="") {
    Row(Modifier.fillMaxWidth().heightIn(min=30.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon.isNotBlank()) {
            Text(icon,fontSize=19.sp,color=accent)
            Spacer(Modifier.width(6.dp))
        }
        Text(title,modifier=Modifier.weight(1f),style=if(icon.isNotBlank()) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
            fontWeight=FontWeight.Bold,color=if(icon.isNotBlank()) accent else homeInk)
        if(trailing.isNotBlank()) {
            if(onTrailing!=null) TextButton(onClick=onTrailing,contentPadding=PaddingValues(horizontal=4.dp)) {
                Text(trailing,style=MaterialTheme.typography.labelSmall,color=homeMuted)
                Icon(Icons.Default.ArrowForward,null,Modifier.size(15.dp),tint=accent)
            } else Text(trailing,style=MaterialTheme.typography.labelSmall,color=homeMuted)
        }
    }
}

/**
 * One column for very narrow layouts / 160% fonts; two for phones;
 * four only when the available width is tablet-sized.
 * Width is the actual content area, not a hard-coded device model.
 */
internal fun homeCareColumns(widthDp: Float, fontScale: Float): Int = when {
    widthDp < 305f || fontScale >= 1.6f -> 1
    widthDp >= 720f && fontScale <= 1.15f -> 4
    else -> 2
}

@Composable
private fun HomeCareTile(
    kind: CareKind,
    count: Int,
    accent: Color,
    tint: Color,
    enabled: Boolean,
    modifier: Modifier,
    onAdd: () -> Unit
) {
    val title = when(kind.id) {
        "diaper" -> "Fralda"
        "bottle" -> "Mamadeira"
        "bath" -> "Banho"
        else -> "Mel"
    }
    // Whole tile is an accessible tap target; avoid tiny nested 40dp icon buttons.
    Surface(
        onClick=onAdd,
        enabled=enabled,
        modifier=modifier.heightIn(min=72.dp)
            .testTag("home-care-${kind.id}")
            .semantics { contentDescription="Registrar ${kind.label}; $count hoje" },
        shape=RoundedCornerShape(16.dp),
        color=Color.White,
        border=BorderStroke(1.dp,homeOutline)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(7.dp)
        ) {
            Surface(shape=RoundedCornerShape(11.dp),color=tint,modifier=Modifier.size(34.dp)) {
                Box(contentAlignment=Alignment.Center) {
                    HomeCareLineIcon(kind.id,accent)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,style=MaterialTheme.typography.labelMedium,color=homeInk,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("$count hoje",style=MaterialTheme.typography.labelSmall,color=homeMuted)
            }
            Icon(Icons.Default.Add,null,Modifier.size(20.dp),tint=accent)
        }
    }
}

@Composable
private fun HomePanel(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        border=BorderStroke(1.dp,homeOutline)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            body()
        }
    }
}



@Composable
private fun HomeCareLineIcon(type: String,color: Color) {
    Canvas(Modifier.size(25.dp)) {
        val s = size.minDimension / 24f
        val stroke = Stroke(width=1.85f*s)
        fun point(x:Float,y:Float)=Offset(x*s,y*s)
        fun ring(x:Float,y:Float,r:Float)=drawCircle(color,r*s,point(x,y),style=stroke)
        fun solid(x:Float,y:Float,r:Float)=drawCircle(color,r*s,point(x,y))
        fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(color,point(x1,y1),point(x2,y2),strokeWidth=1.85f*s)
        fun rect(x:Float,y:Float,w:Float,h:Float,r:Float=2f)=drawRoundRect(color,
            topLeft=point(x,y),size=Size(w*s,h*s),cornerRadius=CornerRadius(r*s,r*s),style=stroke)
        when(type) {
            "diaper" -> {
                ring(12f,12f,8.3f)
                solid(9f,11f,0.85f);solid(15f,11f,0.85f)
                drawArc(color,20f,140f,false,topLeft=point(8f,12f),size=Size(8f*s,5f*s),style=stroke)
                drawArc(color,192f,165f,false,topLeft=point(9f,1.9f),size=Size(6f*s,5.5f*s),style=stroke)
            }
            "bottle" -> {
                rect(9f,2f,6f,4f,1f)
                rect(7f,7f,10f,14f,2f)
                line(10f,12f,14f,12f);line(10f,16f,14f,16f)
                line(8f,7f,9f,6f);line(16f,7f,15f,6f)
            }
            "bath" -> {
                line(3f,11f,21f,11f)
                drawArc(color,0f,180f,false,topLeft=point(4f,10f),size=Size(16f*s,11f*s),style=stroke)
                line(7f,20f,7f,22f);line(17f,20f,17f,22f)
                line(8f,11f,8f,5f);line(8f,5f,11f,5f)
                drawArc(color,180f,180f,false,topLeft=point(11f,4f),size=Size(5f*s,4f*s),style=stroke)
            }
            else -> {
                ring(6f,7f,1.6f);ring(11f,4f,1.6f);ring(16f,6f,1.6f);ring(20f,10f,1.5f)
                drawOval(color,topLeft=point(7f,11f),size=Size(11f*s,9f*s),style=stroke)
            }
        }
    }
}

@Composable
internal fun HomeMoreScreen(navigate: (String) -> Unit) {
    val scheme=MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Mais da Central",style=MaterialTheme.typography.headlineMedium,
                fontWeight=FontWeight.Bold,color=homeInk)
            Text("Tudo que completa a vida de vocês.",style=MaterialTheme.typography.bodyMedium,
                color=homeMuted)
        }
        items(listOf(
            Triple("Planos","Projetos, metas e contribuições","✧"),
            Triple("Sophia","Previsão, dicas e ações personalizadas","✦"),
            Triple("Lembretes","Seus horários e alertas","◷"),
            Triple("Nossa vida","Visão geral da família","♡"),
            Triple("Finanças","Dinheiro e organização","▣"),
            Triple("Saúde","Registros pessoais","✚"),
            Triple("Casa","Dispositivos e automações","⌂"),
            Triple("Segurança","Recursos e alertas de segurança","!"),
            Triple("Configurações","Conta, aparência e privacidade","⚙")
        )) { (name,description,symbol) ->
            OutlinedCard(onClick={navigate(name)},shape=RoundedCornerShape(18.dp),
                border=BorderStroke(1.dp,homeOutline),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(color=scheme.primaryContainer,shape=RoundedCornerShape(12.dp),
                        modifier=Modifier.size(42.dp)) {
                        Box(contentAlignment=Alignment.Center) {
                            Text(symbol,color=scheme.primary,fontSize=21.sp)
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(name,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        Text(description,style=MaterialTheme.typography.bodySmall,color=homeMuted)
                    }
                    Icon(Icons.Default.ArrowForward,null,tint=scheme.tertiary)
                }
            }
        }
    }
}
