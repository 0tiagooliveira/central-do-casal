package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
                    modifier=Modifier.size(40.dp).semantics{contentDescription="Abrir lembretes"}) {
                    Icon(Icons.Default.Notifications,null,tint=palette.primary)
                }
            }
        }
        item { WeatherCard(requestLocationPermission,session.uid,session.name,data.records,requestNotificationsPermission) }
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
                        modifier=Modifier.fillMaxWidth().heightIn(min=47.dp).testTag("home-focus-action"),
                        shape=RoundedCornerShape(14.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=palette.primary)
                    ) {
                        Icon(if(focus==null) Icons.Default.Add else Icons.Default.Check,null,Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if(focus==null) "Abrir minha rotina" else "Registrar atividade",style=MaterialTheme.typography.labelLarge)
                    }
                    Row(Modifier.fillMaxWidth().clickable { touch { selectTab("Rotina") } }.heightIn(min=34.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("$completed de ${tasks.size} atividades concluídas hoje",
                            modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=palette.primary)
                        Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp),tint=accent)
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
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()) {
                careKinds.forEach { kind ->
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
                Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp))
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
        Text(title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,
            fontWeight=FontWeight.Bold,color=homeInk)
        if(trailing.isNotBlank()) {
            if(onTrailing!=null) TextButton(onClick=onTrailing,contentPadding=PaddingValues(horizontal=4.dp)) {
                Text(trailing,style=MaterialTheme.typography.labelSmall,color=homeMuted)
                Icon(Icons.Default.ChevronRight,null,Modifier.size(15.dp),tint=accent)
            } else Text(trailing,style=MaterialTheme.typography.labelSmall,color=homeMuted)
        }
    }
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
    val (symbol,title)=when(kind.id) {
        "diaper" -> "◡" to "Fralda"
        "bottle" -> "♧" to "Mamadeira"
        "bath" -> "♨" to "Banho"
        else -> "♧" to "Mel"
    }
    Surface(modifier,shape=RoundedCornerShape(16.dp),color=Color.White,
        border=BorderStroke(1.dp,homeOutline)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=3.dp,vertical=9.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Surface(shape=RoundedCornerShape(11.dp),color=tint,modifier=Modifier.size(36.dp)) {
                Box(contentAlignment=Alignment.Center) {
                    Text(symbol,fontSize=24.sp,color=accent)
                }
            }
            Text(title,style=MaterialTheme.typography.labelSmall,color=homeInk,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("$count hoje",style=MaterialTheme.typography.labelSmall,color=homeMuted)
            IconButton(onClick=onAdd,enabled=enabled,modifier=Modifier.size(40.dp).testTag("home-care-${kind.id}")) {
                Icon(Icons.Default.Add,"Registrar ${kind.label}",tint=accent,modifier=Modifier.size(21.dp))
            }
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
