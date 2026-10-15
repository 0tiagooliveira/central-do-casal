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
internal fun PlansExperienceScreen(vm: CentralViewModel, data: DataState, onCreate: () -> Unit, onEdit: (Record) -> Unit, onContribution: (Record) -> Unit, onStartImported: ((Record) -> Unit)? = null, onOpenLinked: (String) -> Unit = {}) {
    var filter by rememberSaveable { mutableStateOf("Todas") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAreas by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Ativos") }
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
        PlanDetail(selected, planProgress(selected, data.records), busy, vm, { selectedId = null }, { onEdit(selected) }, { onContribution(selected) }, onOpenLinked)
        return
    }
    fun visible(record: Record) = when (filter) { "Pessoais" -> record.isPrivate; "Compartilhadas" -> !record.isPrivate; else -> true }
    val plans = data.records.filter { record ->
        if(record.kind!="plan" || !visible(record)) false
        else when(status) {
            "Concluídos" -> chaptersComplete(record)
            "Próximos" -> !chaptersComplete(record) && planSteps(record).none { it["done"] == true } &&
                planProgress(record,data.records).registered==0L
            else -> !chaptersComplete(record)
        }
    }
    val converted=data.records.filter { it.kind=="plan" }.map { it.text("sourcePlanId") }.toSet()
    val imported = data.records.filter { it.kind == "importedPlan" && it.id !in converted && visible(it) }
    val areas = data.records.filter { it.kind == "lifeArea" && visible(it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { ReferenceHeading("Planos",modifier=Modifier.weight(1f)); OutlinedButton(onClick=onCreate,enabled=!busy) { Icon(Icons.Default.Add,null,Modifier.size(17.dp));Spacer(Modifier.width(4.dp));Text("Novo plano") } } }
        item { ReferenceTabs(listOf("Ativos","Próximos","Concluídos"),status) { status=it } }
        item { ReferenceTabs(listOf("Todas","Compartilhadas","Pessoais"),filter) { filter=it } }
        item { Text("${plans.size} planos em $status",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        val featured=plans.firstOrNull { planSteps(it).any { step -> step["done"] == true } } ?: plans.firstOrNull()
        if(featured!=null) item(key="featured-plan-${featured.id}") {
            FeaturedPlanReference(featured,planProgress(featured,data.records)) { selectedId=featured.id }
        }
        items(plans.filter { it.id!=featured?.id },key={it.id}) { plan ->
            PlanListCard(plan,planProgress(plan,data.records)) { selectedId=plan.id }
        }
        if (plans.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(status=="Concluídos")"Os capítulos concluídos vão aparecer aqui"else"Um plano começa com uma ideia",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(if(status=="Concluídos")"Este espaço reúne planos com todas as etapas confirmadas."else"Escolha um próximo passo e acompanhe no seu ritmo.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);if(status!="Concluídos")TextButton(onClick=onCreate,enabled=!busy){Text("Criar um plano")}}
        }
        if (imported.isNotEmpty()&&status!="Concluídos") item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Ideias que já estavam no papel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Referências importadas. Preferências e decisões ainda em aberto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(if(status=="Concluídos")emptyList() else imported, key = { it.id }) { reference ->
            OutlinedCard(onClick={selectedId=reference.id},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Icon(Icons.Default.Star,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(reference.text("title"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("Ideia em aberto · ver referência",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.KeyboardArrowRight,"Abrir referência do projeto")} }
        }
        if (areas.isNotEmpty()) item { TextButton(onClick = { showAreas = !showAreas }) { Icon(Icons.Default.Favorite, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (showAreas) "Recolher áreas da vida" else "Explorar áreas da vida") } }
        if (showAreas) items(areas, key = { it.id }) { area ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(area.text("title"), style = MaterialTheme.typography.titleMedium); Text("${area.text("group")} · ${area.text("status")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(area.text("note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
private fun FeaturedPlanReference(plan:Record,progress:PlanProgress,onOpen:()->Unit) {
    val steps=planSteps(plan)
    val completed=steps.count { it["done"]==true }
    Surface(onClick=onOpen,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),
        color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(128.dp)
                .background(Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.surfaceContainerLow))),
                contentAlignment=Alignment.CenterStart) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Surface(shape=RoundedCornerShape(40.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.9f)) {
                        Text(if(steps.isNotEmpty() && completed==steps.size)"✦ Concluído" else "✦ Em andamento",Modifier.padding(horizontal=12.dp,vertical=6.dp),
                            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    Text(plan.text("title"),style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(steps.isEmpty()) "Próximos passos a definir"
                        else "$completed de ${steps.size} etapas",
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(steps.isNotEmpty()) Text("${completed*100/steps.size}%",
                        color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                }
                ReferenceProgress(if(steps.isEmpty())0f else completed.toFloat()/steps.size)
                steps.take(4).forEach { step ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(step["done"]==true) Icons.Default.Check else Icons.Default.Star,null,
                            tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text((step["title"] as? String).orEmpty(),modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.bodyMedium,maxLines=2)
                    }
                }
                Text("Toque para ver e registrar as etapas",
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }
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

private data class PlanStepAction(val id: String, val title: String)

@Composable
private fun PlanDetail(plan: Record, progress: PlanProgress, busy: Boolean, vm: CentralViewModel, onBack: () -> Unit, onEdit: () -> Unit, onContribution: () -> Unit, onOpenLinked: (String) -> Unit) {
    val data by vm.repo.data.collectAsStateWithLifecycle()
    var shoppingStep by remember { mutableStateOf<PlanStepAction?>(null) }
    var routineStep by remember { mutableStateOf<PlanStepAction?>(null) }
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
            val currentStepId = (step["id"] as? String).orEmpty()
            val currentStepTitle = (step["title"] as? String).orEmpty()
            val linkedBasket = data.baskets.values.firstOrNull { basket -> basket.entries.any { it.sourcePlanId == plan.id && it.sourceStepId == currentStepId } }
            val linkedTask = data.records.firstOrNull { it.kind == "task" && it.text("sourcePlanId") == plan.id && it.text("sourceStepId") == currentStepId }
            val shoppingLinked = linkedBasket != null
            val routineLinked = linkedTask != null
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PlanAccentIcon(if(step["done"]==true) Icons.Default.Check else CalendarLine)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(currentStepTitle,style=MaterialTheme.typography.titleMedium)
                            Text(if(step["done"]==true) "Etapa confirmada" else "Um passo de cada vez",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(step["done"]==true,{done->vm.action { vm.repo.savePlanStep(plan,currentStepId,completed=done,expectedRevision=plan.number("stageRevision")) }},enabled=!busy)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (shoppingLinked) {
                            TextButton(onClick = { onOpenLinked("Compras:${linkedBasket?.market.orEmpty()}") }) { Icon(BasketLine,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Já está em ${linkedBasket?.market ?: "Compras"}") }
                        } else {
                            TextButton(onClick = { shoppingStep = PlanStepAction(currentStepId,currentStepTitle) }, enabled=!busy) { Icon(BasketLine,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Enviar para Compras") }
                        }
                        if (routineLinked) {
                            TextButton(onClick = { onOpenLinked("Rotina:${linkedTask?.text("date").orEmpty()}") }) { Icon(CalendarLine,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Já está na Rotina") }
                        } else {
                            TextButton(onClick = { routineStep = PlanStepAction(currentStepId,currentStepTitle) }, enabled=!busy) { Icon(CalendarLine,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Agendar na Rotina") }
                        }
                    }
                    if(shoppingLinked || routineLinked) Text("O vínculo não conclui esta etapa automaticamente.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    shoppingStep?.let { step ->
        PlanStepShoppingDialog(
            plan = plan,
            step = step,
            vm = vm,
            busy = busy,
            onDismiss = { shoppingStep = null },
            onSaved = { market ->
                shoppingStep = null
                onOpenLinked("Compras:$market")
            }
        )
    }
    routineStep?.let { step ->
        PlanStepRoutineDialog(
            plan = plan,
            step = step,
            vm = vm,
            busy = busy,
            onDismiss = { routineStep = null },
            onSaved = { date ->
                routineStep = null
                onOpenLinked("Rotina:$date")
            }
        )
    }
}

@Composable
private fun PlanStepShoppingDialog(plan: Record, step: PlanStepAction, vm: CentralViewModel, busy: Boolean, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var market by rememberSaveable(step.id) { mutableStateOf("Mercado") }
    var sharePrivate by rememberSaveable(step.id) { mutableStateOf(false) }
    val session = vm.repo.session.value
    val needsShareConfirmation = plan.isPrivate && !session.local
    AlertDialog(
        onDismissRequest = { if(!busy) onDismiss() },
        title = { Text("Enviar para Compras") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Text("A etapa vira um item para revisar. Quantidade, unidade e preço continuam a confirmar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceStrip(listOf("Mercado","Açougue"), market, { market = it })
                if(needsShareConfirmation) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(sharePrivate,{sharePrivate=it})
                        Text("Compartilhar o título desta etapa privada na lista do casal.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && (!needsShareConfirmation || sharePrivate),
                onClick = {
                    vm.action("Etapa enviada para Compras") {
                        vm.repo.linkPlanStepToBasket(plan.id,step.id,step.title,market)
                        onSaved(market)
                    }
                }
            ) { Text("Adicionar à lista") }
        },
        dismissButton = { TextButton(onClick=onDismiss,enabled=!busy) { Text("Cancelar") } }
    )
}

@Composable
private fun PlanStepRoutineDialog(plan: Record, step: PlanStepAction, vm: CentralViewModel, busy: Boolean, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var date by rememberSaveable(step.id) { mutableStateOf(LocalDate.now().format(formatter)) }
    var time by rememberSaveable(step.id) { mutableStateOf("") }
    var owner by rememberSaveable(step.id) { mutableStateOf("Ambos") }
    var category by rememberSaveable(step.id) { mutableStateOf("Rotina") }
    var visibility by rememberSaveable(step.id) { mutableStateOf(if(plan.isPrivate) "Só para mim" else "Compartilhada") }
    val parsedDate = runCatching { LocalDate.parse(date,formatter) }.getOrNull()
    val validTime = time.isBlank() || runCatching { java.time.LocalTime.parse(time) }.isSuccess
    AlertDialog(
        onDismissRequest = { if(!busy) onDismiss() },
        title = { Text("Agendar na Rotina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(step.title,style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(date,{date=it},label={Text("Data (dd/mm/aaaa)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(time,{time=it},label={Text("Horário opcional (HH:mm)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("Responsável",style=MaterialTheme.typography.labelLarge)
                ChoiceStrip(listOf("Tiago","Rhayra","Ambos"),owner,{owner=it})
                Text("Área",style=MaterialTheme.typography.labelLarge)
                ChoiceStrip(listOf("Pessoal","Casa","Lucca","Rotina"),category,{category=it})
                Text("Visibilidade",style=MaterialTheme.typography.labelLarge)
                ChoiceStrip(listOf("Compartilhada","Só para mim"),visibility,{visibility=it})
                if(plan.isPrivate && visibility=="Compartilhada") Text("Ao salvar, o título desta etapa privada ficará visível no espaço do casal.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                if(parsedDate==null) Text("Confira a data.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                if(!validTime) Text("Use horário no formato HH:mm.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                Text("Criar a tarefa não conclui a etapa do plano.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && parsedDate != null && validTime,
                onClick = {
                    val iso = requireNotNull(parsedDate).toString()
                    vm.action("Etapa agendada na Rotina") {
                        val task = vm.repo.createPlanStepTask(plan.id,step.id,step.title,iso,owner,category,time.trim(),visibility=="Só para mim")
                        Reminders.schedule(vm.getApplication(),vm.repo.session.value.uid,task)
                        onSaved(iso)
                    }
                }
            ) { Text("Criar tarefa") }
        },
        dismissButton = { TextButton(onClick=onDismiss,enabled=!busy) { Text("Cancelar") } }
    )
}
