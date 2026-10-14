package com.centraldocasal.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditor(vm: CentralViewModel, initial: Record?, isNew: Boolean = initial==null, onDismiss: () -> Unit) {
    val id=rememberSaveable { initial?.id ?: java.util.UUID.randomUUID().toString() }
    var page by rememberSaveable { mutableStateOf(0) }
    var title by rememberSaveable { mutableStateOf(initial?.text("title").orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.text("note").orEmpty()) }
    var horizon by rememberSaveable { mutableStateOf(initial?.text("horizon")?.takeIf { it.isNotBlank() } ?: "Sem prazo definido") }
    var personal by rememberSaveable { mutableStateOf(initial?.isPrivate ?: false) }
    var amount by rememberSaveable { mutableStateOf(initial?.number("target")?.takeIf { it>0 }?.let { BigDecimal.valueOf(it,2).toPlainString().replace('.',',') }.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initial?.text("date")?.takeIf { it.isNotBlank() && runCatching { LocalDate.parse(it) }.isSuccess }.orEmpty()) }
    var financial by rememberSaveable { mutableStateOf(initial?.number("target")?.let { it>0 } ?: false) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    fun back() { if(page>0) {page--;error=null} else onDismiss() }
    BackHandler(!busy) { back() }
    fun save() {
        val target=runCatching { if(!financial || amount.isBlank()) 0L else Money.cents(amount) }.getOrElse { error=it.message;return }
        if(title.isBlank()) { error="Dê um nome para a meta.";return }
        vm.action("Meta salva") {
            val origin=if(isNew) initial?.fields?.filterKeys { it in setOf("sourcePlanId","steps","stageRevision") }.orEmpty() else emptyMap()
            val record=Record(id,"plan",personal, origin+mapOf("title" to title.trim(),"note" to note.trim(),"horizon" to horizon,"target" to target,"date" to date))
            if(isNew) vm.repo.save(record) else vm.repo.savePlanDetails(record)
            onDismiss()
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
      Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick=::back,enabled=!busy) { Icon(Icons.Default.ArrowBack,if(page==0) "Voltar aos planos" else "Etapa anterior") }; Text(if(isNew) "Criar um plano" else "Editar plano",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));Text("${page+1} / 3",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary) }
      LinearProgressIndicator(progress={ (page+1)/3f },modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp),gapSize=0.dp)
      Crossfade(targetState=page,animationSpec=tween(if(LocalExperience.current.motion)180 else 0),modifier=Modifier.weight(1f),label="Etapa do plano") { current ->
       LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer) { Icon(when(current){0->Icons.Default.Star;1->Icons.Default.Check;else->Icons.Default.Favorite},null,Modifier.padding(15.dp).size(26.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer) };Text(when(current){0->"O que queremos?";1->"Como chegar lá?";else->"Com quem vamos?"},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(when(current){0->"Uma ideia que merece um lugar nos nossos dias.";1->"Um primeiro passo. O resto pode ganhar forma depois.";else->"Escolha quem acompanha e confira como este plano começa."},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        if(current==0) {
          item { OutlinedTextField(title,{title=it;error=null},label={Text("Nome da meta")},placeholder={Text("Dê um nome ao que importa")},modifier=Modifier.fillMaxWidth(),minLines=2,shape=RoundedCornerShape(20.dp),enabled=!busy,isError=title.isBlank()&&error!=null) }
          item { Text("Uma mudança na casa, um projeto pessoal ou algo que vocês querem viver juntos.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if(current==1) {
          item { OutlinedTextField(note,{note=it},label={Text("Próximo passo")},placeholder={Text("Uma ação pequena e possível")},modifier=Modifier.fillMaxWidth(),minLines=3,shape=RoundedCornerShape(20.dp),enabled=!busy) }
          item { Text("Em que ritmo?",style=MaterialTheme.typography.titleSmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Sem prazo definido","Curto prazo","Médio prazo","Longo prazo").forEach { value -> FilterChip(horizon==value,{horizon=value},enabled=!busy,label={Text(value)}) } } }
          item { OutlinedButton(onClick={pickingDate=true},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(14.dp)) { Icon(Icons.Default.DateRange,null);Spacer(Modifier.width(8.dp));Text(if(date.isBlank()) "Escolher uma data · opcional" else "Data desejada · ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))}") };if(date.isNotBlank()) TextButton(onClick={date=""},enabled=!busy) {Text("Deixar sem data")}; Text("Uma data desejada, sem criar tarefas ou lembretes automaticamente.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
          item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text("Há uma meta em dinheiro?",style=MaterialTheme.typography.titleSmall);Text("Opcional. Etapas também mostram o caminho.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(financial,{financial=it;error=null},enabled=!busy,modifier=Modifier.semantics{contentDescription="Usar meta financeira"}) }; if(financial) {OutlinedTextField(amount,{amount=it;error=null},label={Text("Valor-alvo opcional (R$)")},modifier=Modifier.fillMaxWidth().padding(top=12.dp),singleLine=true,shape=RoundedCornerShape(18.dp),enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));Text("Uma intenção; o valor registrado só muda com avanços reais.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
        }
        if(current==2) {
          item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {GoalAudienceCard(!personal,"Compartilhada","Acompanhamos juntos no espaço do casal",false,isNew&&!busy){personal=false};GoalAudienceCard(personal,"Pessoal","Somente a sua conta pode ver",true,isNew&&!busy){personal=true};if(!isNew)Text("Este plano mantém a visibilidade escolhida na criação.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
          item { OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Assim começa",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(note.ifBlank{"Próximo passo a definir"});Text(horizon,style=MaterialTheme.typography.bodySmall);Text(if(date.isBlank())"Sem data desejada" else "Até ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))}",style=MaterialTheme.typography.bodySmall);Text(if(financial&&amount.isNotBlank())"Meta financeira: R$ $amount" else "Sem meta financeira",style=MaterialTheme.typography.bodySmall);if(initial?.text("sourcePlanId")?.isNotBlank()==true)Text("A referência original deste projeto será preservada.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)} } }
        }
        error?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
        item { Button(onClick={when(current){0->if(title.isNotBlank()){page=1;error=null};1->{val valid=runCatching{if(financial&&amount.isNotBlank())Money.cents(amount)else 0L};if(valid.isSuccess){page=2;error=null}else error=valid.exceptionOrNull()?.message};else->save()}},enabled=!busy&&title.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) { Text(if(busy) "Salvando…" else when(current){0->"Continuar";1->"Revisar plano";else->"Salvar meta"}) } }
        if(!isNew&&current==2) item { TextButton(onClick={removing=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Excluir meta",color=MaterialTheme.colorScheme.error) } }
       }
      }
    }
    if(pickingDate) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=date.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()})
        DatePickerDialog(onDismissRequest={pickingDate=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let { date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() };pickingDate=false}) { Text("Escolher data") }},dismissButton={TextButton(onClick={pickingDate=false}) { Text("Cancelar") }}) { DatePicker(picker) }
    }
    if(removing&&initial!=null) AlertDialog(onDismissRequest={removing=false},title={Text("Excluir esta meta?")},text={Text("A meta será removida. Registros de progresso anteriores permanecem preservados para exportação.")},confirmButton={TextButton(enabled=!busy,onClick={vm.action("Meta excluída") { vm.repo.remove(initial);removing=false;onDismiss() }}) { Text("Excluir",color=MaterialTheme.colorScheme.error) }},dismissButton={TextButton(onClick={removing=false}) { Text("Cancelar") }})
}

@Composable
private fun GoalAudienceCard(selected:Boolean,title:String,description:String,personal:Boolean,enabled:Boolean,onSelect:()->Unit) {
 Card(onClick=onSelect,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) { Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(if(personal)Icons.Default.Person else Icons.Default.Favorite,null);Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold);Text(description,style=MaterialTheme.typography.bodySmall)};if(selected)Icon(Icons.Default.Check,"Visibilidade selecionada")} }
}
