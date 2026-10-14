package com.centraldocasal.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun planMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))
private fun planDate(date: String): String = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault("Sem prazo definido")
private fun planScope(plan: Record) = if (plan.isPrivate) "Pessoal · só minha conta" else "Compartilhado · nosso espaço"
private fun planSteps(plan:Record):List<Map<*,*>> = (plan.fields["steps"] as? List<*>)?.mapNotNull { it as? Map<*,*> } ?: emptyList()
private fun chaptersComplete(plan:Record):Boolean = planSteps(plan).let { it.isNotEmpty() && it.all { step -> step["done"]==true } }

@Composable
internal fun PlansExperienceScreen(vm: CentralViewModel, data: DataState, onCreate: () -> Unit, onEdit: (Record) -> Unit, onContribution: (Record) -> Unit, onStartImported: ((Record) -> Unit)? = null) {
    var filter by rememberSaveable { mutableStateOf("Todas") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAreas by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Em andamento") }
    var choosingStatus by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selected = data.records.firstOrNull { it.kind == "plan" && it.id == selectedId }
    val selectedReference=data.records.firstOrNull{it.kind=="importedPlan"&&it.id==selectedId}
    BackHandler(selected != null||selectedReference!=null) { selectedId = null }
    if(selectedReference!=null){
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={selectedId=null}){Icon(Icons.Default.ArrowBack,"Voltar aos planos")};Text("Ideia em aberto",style=MaterialTheme.typography.titleMedium)}}
            item{ImportedPlanCard(selectedReference)}
            if(onStartImported!=null&&data.records.none{it.kind=="plan"&&it.text("sourcePlanId")==selectedReference.id})item{Button(onClick={onStartImported(selectedReference)},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Text("Organizar este projeto")};Text("Vocês escolhem os próximos passos antes de acompanhar o progresso.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        };return
    }
    if (selected != null) {
        PlanDetail(selected, planProgress(selected, data.records), busy, vm, { selectedId = null }, { onEdit(selected) }, { onContribution(selected) })
        return
    }
    fun visible(record: Record) = when (filter) { "Pessoais" -> record.isPrivate; "Compartilhadas" -> !record.isPrivate; else -> true }
    val plans = data.records.filter { it.kind == "plan" && visible(it) && when(status){"Em andamento"->!chaptersComplete(it);"Etapas concluídas"->chaptersComplete(it);else->true} }
    val converted=data.records.filter { it.kind=="plan" }.map { it.text("sourcePlanId") }.toSet()
    val imported = data.records.filter { it.kind == "importedPlan" && it.id !in converted && visible(it) }
    val areas = data.records.filter { it.kind == "lifeArea" && visible(it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("Planos", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f)); OutlinedButton(onClick=onCreate, enabled=!busy, shape=RoundedCornerShape(16.dp), contentPadding=PaddingValues(horizontal=14.dp, vertical=8.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Novo plano") } } }
        item { Column(verticalArrangement=Arrangement.spacedBy(5.dp)) { Text("Qual é o próximo passo?",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold); Text("Uma ideia. Pequenos passos. Algo nosso.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { ChoiceStrip(listOf("Todas","Compartilhadas","Pessoais"),filter,{filter=it}) }
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${plans.size} planos",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f));Box{TextButton(onClick={choosingStatus=true}){Text(status);Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(18.dp))};DropdownMenu(expanded=choosingStatus,onDismissRequest={choosingStatus=false}){listOf("Em andamento","Etapas concluídas","Todos").forEach {option->DropdownMenuItem(text={Text(option)},onClick={status=option;choosingStatus=false})}}}} }
        items(plans, key = { it.id }) { plan -> PlanListCard(plan, planProgress(plan, data.records)) { selectedId = plan.id } }
        if (plans.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(status=="Etapas concluídas")"Os capítulos concluídos vão aparecer aqui"else"Um plano começa com uma ideia",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(if(status=="Etapas concluídas")"Este espaço reúne planos com todas as etapas confirmadas."else"Escolha um próximo passo e acompanhe no seu ritmo.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);if(status!="Etapas concluídas")TextButton(onClick=onCreate,enabled=!busy){Text("Criar um plano")}}
        }
        if (imported.isNotEmpty()&&status!="Etapas concluídas") item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Ideias que já estavam no papel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Referências importadas. Preferências e decisões ainda em aberto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(if(status=="Etapas concluídas")emptyList() else imported, key = { it.id }) { reference ->
            OutlinedCard(onClick={selectedId=reference.id},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Icon(Icons.Default.Star,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(reference.text("title"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("Ideia em aberto · ver referência",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.KeyboardArrowRight,"Abrir referência do projeto")} }
        }
        if (areas.isNotEmpty()) item { TextButton(onClick = { showAreas = !showAreas }) { Icon(Icons.Default.Favorite, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (showAreas) "Recolher áreas da vida" else "Explorar áreas da vida") } }
        if (showAreas) items(areas, key = { it.id }) { area ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(area.text("title"), style = MaterialTheme.typography.titleMedium); Text("${area.text("group")} · ${area.text("status")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(area.text("note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
private fun PlanListCard(plan: Record, progress: PlanProgress, onOpen: () -> Unit) {
    val steps=planSteps(plan);val completed=steps.count{it["done"]==true}
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanAccentIcon(PlanLine, "Plano")
                Column(Modifier.weight(1f)) { Text(plan.text("title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(plan.text("horizon").ifBlank { "No nosso ritmo" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Default.KeyboardArrowRight, "Abrir detalhes do plano", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress.target != null) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(planMoney(progress.registered), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text("de ${planMoney(progress.target)}", style = MaterialTheme.typography.bodySmall) }; PlanProgressBar(progress.fraction); Text("${(progress.fraction * 100).toInt()}% da meta · registros manuais", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if(steps.isNotEmpty()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Etapas",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("$completed / ${steps.size}",style=MaterialTheme.typography.labelLarge)};PlanProgressBar(completed.toFloat()/steps.size);if(completed==steps.size)Text("Todas as etapas confirmadas",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}
            else Text(plan.text("note").ifBlank{"Qual é o primeiro passo?"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            if(progress.history.isNotEmpty()&&progress.target==null)Text("${progress.history.size} avanços registrados",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(planScope(plan), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (plan.text("date").isNotBlank()) Text("Prazo · ${planDate(plan.text("date"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanProgressBar(value: Float) {
    val progress by animateFloatAsState(value, tween(if (LocalExperience.current.motion) 400 else 0), label = "Progresso registrado")
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), gapSize = 0.dp, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
}

@Composable
private fun PlanDetail(plan: Record, progress: PlanProgress, busy: Boolean, vm: CentralViewModel, onBack: () -> Unit, onEdit: () -> Unit, onContribution: () -> Unit) {
    val data by vm.repo.data.collectAsStateWithLifecycle()
    var sourceOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    val origin=data.records.firstOrNull { it.kind=="importedPlan"&&it.id==plan.text("sourcePlanId") }
    var stepTitle by rememberSaveable(plan.id) { mutableStateOf("") }
    var stepId by rememberSaveable(plan.id) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val steps = planSteps(plan)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar aos planos") }; Text("Detalhes do plano", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = onEdit, enabled = !busy) { Text("Editar") } } }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)), RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PlanAccentIcon(Icons.Default.Star, "Plano")
                Text(plan.text("title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(planScope(plan), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(progress.target!=null||progress.registered>0)planMoney(progress.registered)else if(steps.isEmpty())"O começo de algo"else"${steps.count{it["done"]==true}} de ${steps.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(if(progress.target!=null||progress.registered>0)"Valor registrado por vocês"else if(steps.isEmpty())"Escolham o primeiro capítulo"else"Etapas confirmadas por vocês", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress.target != null) { PlanProgressBar(progress.fraction); Text("${(progress.fraction * 100).toInt()}% · meta ${planMoney(progress.target)}", style = MaterialTheme.typography.labelLarge) }
                if(progress.target!=null||progress.registered>0)Text("Valores registrados manualmente.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Column { Text("Falta registrar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(progress.remaining?.let(::planMoney) ?: "Meta a definir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; Column { Text("Prazo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(planDate(plan.text("date")), style = MaterialTheme.typography.titleMedium) } } }
        item { OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { val step = plan.text("note"); Text(if (step.isNotBlank()) "Próximo passo" else "Último avanço", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(step.ifBlank { progress.history.firstOrNull()?.text("note")?.takeIf { it.isNotBlank() } ?: "Defina um próximo passo ao editar o plano." }, style = MaterialTheme.typography.bodyMedium); if (plan.text("horizon").isNotBlank()) Text(plan.text("horizon"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Button(onClick = onContribution, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(16.dp)) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Registrar um avanço") } }
        if(origin!=null) item { TextButton(onClick={sourceOpen=!sourceOpen}) { Text(if(sourceOpen) "Recolher projeto de origem" else "Consultar projeto de origem") };if(sourceOpen) ImportedPlanCard(origin) }
        item { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Capítulos deste plano", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${steps.count { it["done"] == true }} de ${steps.size} etapas confirmadas por vocês", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(steps, key = { (it["id"] as? String).orEmpty() }) { step ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PlanAccentIcon(if(step["done"]==true) Icons.Default.Check else CalendarLine)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text((step["title"] as? String).orEmpty(),style=MaterialTheme.typography.titleMedium)
                        Text(if(step["done"]==true) "Etapa confirmada" else "Um passo de cada vez",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(step["done"]==true,{done->vm.action { vm.repo.savePlanStep(plan,step["id"] as String,completed=done,expectedRevision=plan.number("stageRevision")) }},enabled=!busy)
                }
            }
        }
        item { OutlinedTextField(stepTitle, { stepTitle = it }, label = { Text("Uma próxima etapa") }, placeholder = { Text("O que vocês querem fazer?") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, shape = RoundedCornerShape(18.dp)); TextButton(onClick = { vm.action { vm.repo.savePlanStep(plan, stepId, title = stepTitle, expectedRevision = plan.number("stageRevision")); stepTitle = ""; stepId = java.util.UUID.randomUUID().toString() } }, enabled = !busy && stepTitle.isNotBlank()) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Adicionar etapa") } }
        item { Text("Histórico do que aconteceu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (progress.history.isEmpty()) item { Text("O primeiro avanço aparece aqui depois que vocês registrarem.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(progress.history, key = { it.id }) { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Default.Check, null, Modifier.padding(9.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(entry.text("note").ifBlank { "Avanço registrado" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); Text(planDate(entry.text("date")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (entry.number("amount") > 0) Text(planMoney(entry.number("amount")), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
