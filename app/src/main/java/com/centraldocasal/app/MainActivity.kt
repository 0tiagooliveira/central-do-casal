package com.centraldocasal.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centraldocasal.app.data.*
import com.centraldocasal.app.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> androidx.lifecycle.ViewModelProvider(this)[CentralViewModel::class.java].message.value = if(allowed) "Notificações permitidas pelo Android." else "Notificações não foram permitidas. Você pode rever nas configurações do Android." }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed ->
        androidx.lifecycle.ViewModelProvider(this)[CentralViewModel::class.java].message.value = if(allowed.values.any { it }) "Localização permitida enquanto você usa o app." else "A localização não foi permitida. Nenhuma posição será compartilhada."
    }
    private val nearbyPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed ->
        androidx.lifecycle.ViewModelProvider(this)[CentralViewModel::class.java].message.value = if(allowed.values.any { it }) "Acesso a aparelhos próximos permitido." else "O acesso a aparelhos próximos não foi permitido."
    }
    private var pendingPhotoUid = ""
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val uid=pendingPhotoUid;pendingPhotoUid=""
        if(uri!=null && uid.isNotBlank() && androidx.lifecycle.ViewModelProvider(this)[CentralViewModel::class.java].repo.session.value.uid == uid) {
            runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            getSharedPreferences("experience_$uid",MODE_PRIVATE).edit().putString("photo",uri.toString()).apply()
        }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray(Charsets.UTF_8)) }
        pendingExport = ""
    }
    private var pendingExport = ""
    private var reminderLaunch by mutableStateOf(false)
    private var reminderTaskId by mutableStateOf("")
    private var reminderTaskDate by mutableStateOf("")
    private var reminderTaskUid by mutableStateOf("")

    private fun readReminderIntent(source: Intent?) {
        reminderLaunch = source?.hasExtra("taskId") == true
        reminderTaskId = source?.getStringExtra("taskId").orEmpty()
        reminderTaskDate = source?.getStringExtra("taskDate").orEmpty()
        reminderTaskUid = source?.getStringExtra("taskUid").orEmpty()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReminderIntent(intent)
    }
    override fun onResume() {
        super.onResume()
        // Depois de voltar de Permissões/Alarmes e lembretes, reavaliar os
        // alarmes locais, mas não recriar os que continuam válidos.
        Reminders.restore(this,forceRearm=false)
        SophiaMoments.restore(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        readReminderIntent(intent)
        setContent {
            val vm: CentralViewModel = viewModel()
            val profile by vm.repo.session.collectAsStateWithLifecycle()
            val experience=rememberExperience(profile.uid)
            CompositionLocalProvider(LocalExperience provides experience) {
            CentralTheme(profile.name) {
                CentralApp(vm, export = { pendingExport = vm.repo.export(); exportFile.launch("central-do-casal-${LocalDate.now()}.json") }, notify = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, location = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)) }, nearby = { if(Build.VERSION.SDK_INT>=31) nearbyPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.NEARBY_WIFI_DEVICES)) }, photo = { pendingPhotoUid=profile.uid;photoPicker.launch(arrayOf("image/*")) }, openReminder=reminderLaunch, consumeReminder={reminderLaunch=false}, reminderTaskId=reminderTaskId, reminderTaskDate=reminderTaskDate, reminderTaskUid=reminderTaskUid)
            }
            }
        }
    }
}

class CentralViewModel(app: Application) : AndroidViewModel(app) {
    val repo = CentralRepository(app)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    fun action(success: String? = null, timeoutMs: Long = 30000, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch { busy.value = true
            try { withTimeout(timeoutMs) { block() }; if (success != null) message.value = success }
            catch (e: kotlinx.coroutines.TimeoutCancellationException) { message.value = "O servidor não confirmou a tempo. Confira o estado antes de repetir; a alteração pode estar pendente." }
            catch (e: Exception) { message.value = when (e) { is IllegalArgumentException, is IllegalStateException -> e.message ?: "Confira os dados."; is com.google.firebase.auth.FirebaseAuthException -> "Não foi possível entrar: ${e.errorCode}. Confira os dados e a configuração de autenticação."; else -> "Operação não confirmada. Confira a conexão e as permissões do Firebase." } }
            finally { busy.value = false }
        }
    }
    fun save(record: Record) = action { repo.save(record); if (record.kind == "task") Reminders.schedule(getApplication(), repo.session.value.uid, record, repo.session.value.name) }
    fun complete(task: Record, day: LocalDate, completed: Boolean) = action { repo.save(Record(occurrenceId(task.id, day), "occurrence", task.isPrivate, mapOf("taskId" to task.id, "date" to day.toString(), "done" to completed))); if (completed) Reminders.cancel(getApplication(), repo.session.value.uid, task.id, day) }
    fun logout() { Reminders.cancelAll(getApplication(), repo.session.value.uid); repo.logout() }
    override fun onCleared() { repo.close() }
}

/** A reminder opened on a different signed-in account must never reveal the original task. */
internal fun reminderMatchesSession(sessionUid: String, reminderUid: String): Boolean =
    sessionUid.isNotBlank() && (reminderUid.isBlank() || sessionUid == reminderUid)

private val br = Locale.forLanguageTag("pt-BR")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
private fun money(value: Long) = NumberFormat.getCurrencyInstance(br).format(java.math.BigDecimal(value).movePointLeft(2))
private fun moneyInput(value: Long) = java.math.BigDecimal(value).movePointLeft(2).toPlainString().replace('.', ',')
private fun quantityInput(value: Long) = java.math.BigDecimal(value).movePointLeft(3).stripTrailingZeros().toPlainString().replace('.', ',')
private fun displayDate(value: String) = runCatching { LocalDate.parse(value).format(dateFormat) }.getOrDefault(value)
private fun dateInput(value: String) = LocalDate.parse(value, dateFormat).toString()

@Composable
fun CentralApp(vm: CentralViewModel, export: () -> Unit, notify: () -> Unit, location: () -> Unit = {}, nearby: () -> Unit = {}, photo: () -> Unit = {}, openReminder: Boolean = false, consumeReminder: () -> Unit = {}, reminderTaskId: String = "", reminderTaskDate: String = "", reminderTaskUid: String = "") {
    val session by vm.repo.session.collectAsStateWithLifecycle()
    val data by vm.repo.data.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Hoje") }
    var route by rememberSaveable { mutableStateOf("") }
    var requestedShoppingMarket by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedRoutineDate by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedRoutineTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    val view=LocalView.current; val experience=LocalExperience.current
    val context = LocalContext.current
    val alertPrefs = remember(session.uid) { context.getSharedPreferences("safety_alerts_${session.uid}", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(session.uid) {
        route = ""
        tab = "Hoje"
        requestedShoppingMarket = null
        requestedRoutineDate = null
        requestedRoutineTaskId = null
    }
    LaunchedEffect(session.uid,openReminder,reminderTaskId,reminderTaskDate,reminderTaskUid) {
        if(openReminder && session.uid.isNotBlank()) {
            if(reminderMatchesSession(session.uid,reminderTaskUid)) {
                route=""
                tab="Rotina"
                requestedRoutineDate=reminderTaskDate.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
                requestedRoutineTaskId=reminderTaskId.takeIf { it.isNotBlank() }
            } else {
                vm.message.value="Este lembrete pertence a outra conta. Entre no perfil correto para consultá-lo."
            }
            consumeReminder()
        }
    }
    LaunchedEffect(session.uid, session.name, data.records) {
        if(session.uid.isNotBlank()) {
            val reminderPrefs=context.getSharedPreferences("reminders_${session.uid}",android.content.Context.MODE_PRIVATE)
            if(!reminderPrefs.contains("enabled")) reminderPrefs.edit().putBoolean("enabled",true).apply()
            Reminders.migrate(context,session.uid)
            data.records.filter { it.kind=="task" }.forEach { task ->
                val completeToday=data.records.any { it.kind=="occurrence" && it.text("taskId")==task.id && it.text("date")==LocalDate.now().toString() && it.flag("done") }
                Reminders.schedule(context,session.uid,task,session.name,completedDay=LocalDate.now().takeIf { completeToday })
            }
            SophiaMoments.syncTasks(context, session.uid, session.name, data.records)
        }
    }
    LaunchedEffect(data.records, session.uid) {
        val partnerAlert = data.records.filter { it.kind == "safetyAlert" && it.text("ownerUid") != session.uid }
            .maxByOrNull { it.number("createdAt") }
        if (partnerAlert != null && alertPrefs.getString("seen", "") != partnerAlert.id) {
            alertPrefs.edit().putString("seen", partnerAlert.id).apply()
            vm.message.value = AndroidInteractions.notifySafetyAlert(context, partnerAlert.id, partnerAlert.text("ownerName", "Seu parceiro"), partnerAlert.text("mapUrl", "Localização não disponível"))
        }
    }
    BackHandler(route.isNotBlank()) { route = "" }
    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        if (session.uid.isNotBlank() && route.isNotBlank()) Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (route.isNotBlank()) IconButton(onClick = { route = "" }) { Icon(Icons.Default.ArrowBack, "Voltar") }
                else Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(HeartLine, null, Modifier.padding(11.dp).size(23.dp), tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (route.isBlank()) "Central do Casal" else route, style = MaterialTheme.typography.titleLarge)
                    if (route.isBlank()) Text(if (session.name.isBlank()) "Nosso espaço" else session.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (route.isBlank()) {
                    IconButton(onClick = { route = "Lembretes" }) { Icon(ReminderBell, "Abrir lembretes", Modifier.size(22.dp)) }
                    FilledTonalIconButton(onClick = { route = "Configurações" }, shape = CircleShape, modifier = Modifier.size(48.dp).semantics { contentDescription="Configurações e privacidade" }) { PersonalPortrait(experience.photo,session.name,size=42.dp) }
                }
            }
        }
    }, bottomBar = {
        if (session.uid.isNotBlank() && route.isBlank()) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            val icons = listOf(HomeLine, CalendarLine, BasketLine, HeartLine, Icons.Default.Settings)
            listOf("Hoje", "Rotina", "Compras", "Nós", "Mais").forEachIndexed { index, title -> NavigationBarItem(modifier = Modifier.testTag("tab-$title"), selected = title == tab, onClick = { AndroidInteractions.haptic(view,experience.haptics);tab = title; route = ""; if(title=="Compras") requestedShoppingMarket=null; if(title=="Rotina") { requestedRoutineDate=null; requestedRoutineTaskId=null } }, icon = { Icon(icons[index], null, Modifier.size(22.dp)) }, label = { Text(title, style = MaterialTheme.typography.labelSmall) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant)) }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (session.uid.isBlank()) AuthScreen(vm, busy)
            else {
                data.error?.let { error ->
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { vm.action { vm.repo.retryConnection() } }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("Tentar novamente") }
                        }
                    }
                }
                data.records.firstOrNull { it.kind == "appRelease" && it.number("minimumVersionCode") > BuildConfig.VERSION_CODE }?.let { release ->
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp)) { Text("Há uma atualização importante do aplicativo", style = MaterialTheme.typography.bodyMedium); TextButton(onClick = { route = "Configurações" }) { Text("Ver atualização") } } }
                }
                data.records.filter { it.kind == "safetyAlert" && it.text("ownerUid") != session.uid && System.currentTimeMillis()-it.number("createdAt") < 7_200_000 }.maxByOrNull { it.number("createdAt") }?.let { alert ->
                    Surface(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SOS de ${alert.text("ownerName","seu parceiro")}",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onErrorContainer);Text("Alerta recebido agora. Abra para ver localização e ligar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer) };Button(onClick={route="Segurança"}) { Text("Abrir") } } }
                }
                Crossfade(targetState = if(route.isNotBlank()) route else tab, animationSpec = tween(centralMotionDurationMillis(MotionKind.NAVIGATION)), modifier = Modifier.weight(1f), label = "Telas") { screen -> screenStates.SaveableStateProvider("${session.uid}:$screen") { when (screen) {
                    "Hoje" -> HomeExperienceScreen(vm, session, data, navigate = { route = it }, selectTab = { destination -> tab = destination; if(destination=="Compras") requestedShoppingMarket=null; if(destination=="Rotina") requestedRoutineDate=null }, requestLocationPermission = location, requestNotificationsPermission = notify)
                    "Mais" -> HomeMoreScreen { destination -> route=destination }
                    "Compras" -> ShoppingHubScreen(vm, data, session, busy, annual = { route = "Ano de compras" }, requestedMarket = requestedShoppingMarket)
                    "Ano de compras" -> AnnualPurchasesScreen(data, onSelect = { AndroidInteractions.haptic(view,experience.haptics);AndroidInteractions.clickSound(view,experience.sounds) })
                    "Rotina" -> RoutineScreen(vm, session, data, requestedDate = requestedRoutineDate,
                        requestedTaskId = requestedRoutineTaskId, onTargetOpened = { requestedRoutineTaskId = null })
                    "Lembretes" -> ReminderInbox(session,data,openRoutine={route="";requestedRoutineDate=null;tab="Rotina"},openSettings={route="Configurações"})
                    "Revisar rotinas" -> RoutineScreen(vm,session,data,reviewOnly=true)
                    "Nossa vida" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { LifeOverview(data,session,onRoutine={route="";requestedRoutineDate=null;tab="Rotina"},onPlans={route="";tab="Planos"},onMoments={route="";tab="Nós"},onShopping={route="";requestedShoppingMarket=null;tab="Compras"}) }
                    "Planos" -> PlansScreen(vm, data, onOpenLinked = { destination -> route = ""; when { destination.startsWith("Compras:") -> { requestedShoppingMarket=destination.substringAfter(":").takeIf { it in listOf("Mercado","Açougue") }; tab="Compras" }; destination.startsWith("Rotina:") -> { requestedRoutineDate=destination.substringAfter(":").takeIf { runCatching { LocalDate.parse(it) }.isSuccess }; tab="Rotina" }; else -> tab=destination } })
                    "Nós" -> TogetherScreen(vm, session, data)
                    "Finanças" -> FinanceScreen(vm, data)
                    "Saúde" -> HealthScreen(vm, data)
                    "Configurações" -> SettingsScreen(vm, session, data, export, notify, navigate = { route = it }, photo = photo)
                    "Casa" -> HomeDevicesScreen(vm, data, busy, requestNearby=nearby)
                    "Segurança" -> SafetyScreen(vm, session, data, busy, requestPermission=location)
                    "Sophia" -> SophiaReferenceScreen(session,data,location,notify) { destination ->
                        if(destination in listOf("Hoje","Rotina","Compras","Nós","Mais")) { tab=destination;route="" }
                        else route=destination
                    }
                } } }
            }
        }
    }
}

@Composable
private fun AuthScreen(vm: CentralViewModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }; var email by rememberSaveable { mutableStateOf("") }; var password by rememberSaveable { mutableStateOf("") }; var register by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp)); PlanAccentIcon(HeartLine)
        Text("Um lugar para\na vida a dois.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Organizem o dia, cuidem dos planos e guardem os bons momentos.", color=MaterialTheme.colorScheme.onSurfaceVariant)
        QuietCard(if(register) "Sua conta individual" else "Que bom ter você aqui", "Cada pessoa entra com sua própria conta.") {
        if (register) Field("Seu nome", name, { name = it })
        Field("E-mail", email, { email = it })
        OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp))
        Button(onClick = { if (register) vm.action { vm.repo.register(name, email, password) } else vm.action { vm.repo.login(email, password) } }, enabled = !busy && email.isNotBlank() && password.isNotBlank() && (!register || name.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min=52.dp), shape=RoundedCornerShape(16.dp)) { Text(if(busy) "Aguarde…" else if (register) "Criar conta" else "Entrar com e-mail") }
        TextButton(onClick = { register = !register }, enabled = !busy) { Text(if (register) "Já tenho uma conta" else "Criar minha conta individual") }
        TextButton(onClick = { vm.action("Se a conta existir, você receberá instruções por e-mail.") { vm.repo.reset(email) } }, enabled = !busy) { Text("Recuperar acesso") }
        }
        QuietCard("Começar neste aparelho", "Seus registros ficam neste celular. Você pode entrar na sua conta depois.") {
        if (!register) Field("Como quer ser chamado?", name, { name = it })
        OutlinedButton(onClick = { vm.action { vm.repo.useLocal(name) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Usar espaço local") }
        }
        Text("Central do Casal ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun taskBelongsTo(task: Record, name: String): Boolean {
    val owner=task.text("owner","Ambos")
    if(owner=="Ambos") return true
    val user=name.trim().lowercase(); val first=user.substringBefore(' ')
    val assigned=owner.trim().lowercase()
    return assigned==user || assigned.substringBefore(' ')==first
}

@Composable
private fun TodayScreen(vm: CentralViewModel, session: SessionState, data: DataState, navigate: (String) -> Unit, selectTab: (String) -> Unit) {
    val day = LocalDate.now(); val tasks = data.records.filter { it.kind == "task" && occurs(it, day) && taskBelongsTo(it,session.name) }; val pendingTasks = tasks.filterNot { done(it, day, data) }
    val horizon=java.time.LocalTime.now().plusHours(6)
    val upcomingTasks=pendingTasks.filter { task -> task.text("time").ifBlank { null }?.let { runCatching { java.time.LocalTime.parse(it) <= horizon }.getOrDefault(true) } ?: true }
        .sortedBy { it.text("time").ifBlank { "99:99" } }
    val memories = data.records.count { it.kind == "quest" && !it.flag("cancelled") && (it.fields["completed"] as? List<*>)?.distinct()?.size == 2 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DayHero(session.name, day.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", br)), tasks.count { done(it, day, data) }, tasks.size, openRoutine = { selectTab("Rotina") }) }
        item { ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SOS e localização",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onErrorContainer);Text("Compartilhe posição, veja seu parceiro e peça ajuda rapidamente.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer) };Button(onClick={navigate("Segurança")},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("Abrir") } } } }
        item { ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Nossa Casa",style=MaterialTheme.typography.titleMedium);Text("Vincule Alexa, aparelhos Wi‑Fi e Bluetooth.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Button(onClick={navigate("Casa")}) { Text("Conectar") } } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Meu dia", "${tasks.count { done(it, day, data) }} / ${tasks.size}", "tarefas para hoje", Icons.Default.DateRange, MaterialTheme.colorScheme.primary, { selectTab("Rotina") }, Modifier.weight(1f))
                SummaryTile("Mercado", "${data.baskets.values.sumOf { b -> b.entries.count { !it.bought } }}", "produtos pendentes", Icons.Default.ShoppingCart, MaterialTheme.colorScheme.primary, { selectTab("Compras") }, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Nossos projetos", "${data.records.count { it.kind in setOf("plan", "importedPlan") }}", "planos registrados", Icons.Default.Star, MaterialTheme.colorScheme.primary, { selectTab("Planos") }, Modifier.weight(1f))
                SummaryTile("Nossa jornada", "$memories", "experiências a dois", Icons.Default.Favorite, MaterialTheme.colorScheme.primary, { selectTab("Nós") }, Modifier.weight(1f))
            }
        }
        data.records.firstOrNull { it.kind == "importSummary" }?.let { summary -> item {
            Panel("Nossa história de compras", "${summary.number("notes")} notas documentadas") {
                Text((summary.fields["total"] as? Number)?.let { money(it.toLong()) } ?: "Total a confirmar", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { navigate("Ano de compras") }) { Text("Explorar o ano") }
            }
        } }
        if (!session.local && session.household.isBlank()) item { Panel("Prepare o espaço do casal", "Crie um espaço ou aceite um convite para compartilhar os registros.") { Button(onClick = { navigate("Configurações") }) { Text("Preparar espaço") } } }
        if (upcomingTasks.isNotEmpty()) item { Text("Próximas atividades", style = MaterialTheme.typography.titleMedium) }
        items(upcomingTasks.take(3), key = { it.id }) { TaskRow(it, day, data, vm) }
        if (pendingTasks.isNotEmpty() && upcomingTasks.isEmpty()) item { QuietCard("Mais tarde hoje", "As próximas tarefas com horário aparecerão quando estiverem mais próximas.") {} }
        item { Panel("Um momento só de vocês", "Uma lembrança pequena também merece ser guardada.") { TextButton(onClick = { selectTab("Nós") }) { Text("Abrir nosso espaço  →") } } }
        item { Text("Também por aqui", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Finanças", "Saúde").forEach { label -> OutlinedButton(onClick = { navigate(label) }, modifier = Modifier.weight(1f)) { Text(label) } } } }
    }
}

private fun done(task: Record, day: LocalDate, data: DataState) = data.records.any { it.kind == "occurrence" && it.id == occurrenceId(task.id, day) && it.flag("done") }
@Composable
private fun TaskRow(task: Record, day: LocalDate, data: DataState, vm: CentralViewModel, edit: (() -> Unit)? = null) {
    val completed = done(task, day, data)
    val busy by vm.busy.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec=tween(centralMotionDurationMillis(MotionKind.LAYOUT))),
        shape = RoundedCornerShape(20.dp),
        color = if (completed) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (completed) 0.dp else 2.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = completed,
                onCheckedChange = { vm.complete(task, day, it) },
                modifier = Modifier.semantics { contentDescription = "Concluir tarefa: ${task.text("title")}" },
                enabled = occurs(task, day) && !busy
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.text("title"), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(
                        task.text("time").takeUnless { it.isBlank() || task.flag("needsTimeReview") },
                        task.text("owner", "Ambos"),
                        task.text("category").takeUnless { it.isBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.flag("needsReview")) {
                    Text("Precisa de revisão", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (edit != null) TextButton(onClick = edit, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Editar")
            }
        }
    }
}

/**
 * Pure eligibility filter for the Routine tabs.
 *
 * A deep-linked task opens only in the editor: it must not switch the daily
 * agenda into an accidental all-dates mode. Shared items marked for review
 * remain visible to authorized household members.
 */
internal fun routineVisibleTasks(
    records: List<Record>,
    day: LocalDate,
    profileName: String,
    audience: String = "Tudo",
    reviewOnly: Boolean = false,
    habitsOnly: Boolean = false
): List<Record> = records.filter { task ->
    if (task.kind != "task" || !taskBelongsTo(task, profileName)) return@filter false
    if (reviewOnly) return@filter task.flag("needsReview") || task.flag("needsTimeReview")
    if (!occurs(task, day)) return@filter false
    val owner = task.text("owner", "Ambos")
    val allowed = when (audience) {
        "Para mim" -> !owner.equals("Ambos", true)
        "Compartilhadas" -> owner.equals("Ambos", true)
        else -> true
    }
    allowed && (!habitsOnly || task.text("repeat") !in setOf("", "Única"))
}

@Composable
private fun RoutineScreen(vm: CentralViewModel, session: SessionState, data: DataState, reviewOnly: Boolean = false, requestedDate: String? = null, requestedTaskId: String? = null, onTargetOpened: () -> Unit = {}) {
    var audience by rememberSaveable { mutableStateOf("Tudo") }
    var dayText by rememberSaveable { mutableStateOf(requestedDate?.takeIf { value -> runCatching { LocalDate.parse(value) }.isSuccess } ?: LocalDate.now().toString()) }
    LaunchedEffect(requestedDate) {
        requestedDate?.takeIf { value -> runCatching { LocalDate.parse(value) }.isSuccess }?.let {
            dayText = it
            audience = "Tudo"
        }
    }
    val day = LocalDate.parse(dayText)
    var editing by remember { mutableStateOf<Record?>(null) }; var creating by remember { mutableStateOf(false) }
    LaunchedEffect(requestedTaskId, data.records) {
        val taskId = requestedTaskId?.takeIf { it.isNotBlank() }
        if(taskId != null) {
            data.records.firstOrNull { it.kind == "task" && it.id == taskId && taskBelongsTo(it, session.name) }?.let { target ->
                audience = "Tudo"
                editing = target
                onTargetOpened()
            }
        }
    }
    val context=LocalContext.current
    val reminderPrefs=context.getSharedPreferences("reminders_${session.uid}",android.content.Context.MODE_PRIVATE)
    val remindersEnabled=reminderPrefs.getBoolean("enabled",true)
    val notificationAllowed=AndroidInteractions.notificationsAllowed(context)
    val configured=data.records.count { it.kind=="task" && it.text("time").isNotBlank() && !it.flag("skipReminder") && !it.flag("needsReview") && (it.text("owner","Ambos")=="Ambos" || taskBelongsTo(it,session.name)) }
    var routineSection by rememberSaveable { mutableStateOf("Hoje") }
    LaunchedEffect(routineSection) {
        if (!reviewOnly && routineSection == "Hoje") dayText = LocalDate.now().toString()
    }
    val tasks = routineVisibleTasks(
        data.records, day, session.name, audience,
        reviewOnly=reviewOnly, habitsOnly=routineSection=="Hábitos" && !reviewOnly
    )
    val completedTasks=tasks.filter { done(it,day,data) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                ReferenceHeading(if(reviewOnly)"Revisar rotinas" else "Rotina",
                    if(reviewOnly)"Confirme os dados pendentes" else "Seus compromissos, no seu ritmo",
                    Modifier.weight(1f))
                IconButton(onClick={creating=true}) { Icon(Icons.Default.Add,"Nova tarefa",tint=MaterialTheme.colorScheme.primary) }
            }
        }
        if(!reviewOnly) item {
            ReferenceTabs(listOf("Hoje","Semana","Cuidados","Hábitos"),routineSection) { routineSection=it }
        }
        if(!reviewOnly && routineSection!="Cuidados") item {
            ReferenceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(CalendarLine,null,tint=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text(day.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",br))
                        .replaceFirstChar { it.uppercase() },
                        style=MaterialTheme.typography.titleSmall,
                        modifier=Modifier.weight(1f))
                    TextButton(onClick={routineSection="Semana"}) { Text("Alterar dia") }
                }
            }
        }
        if(!reviewOnly && routineSection=="Semana") item { WeekRibbon(day,{ dayText=it.toString() }) }
        if(!reviewOnly && routineSection!="Cuidados") item {
            ChoiceStrip(listOf("Para mim","Compartilhadas","Tudo"),audience,{audience=it})
        }
        if(!reviewOnly && routineSection!="Cuidados" && (!notificationAllowed || !remindersEnabled)) item {
            Text(if(!notificationAllowed) "Notificações desativadas no Android. Confira os lembretes."
                else "Lembretes desativados neste aparelho.",style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(routineSection=="Cuidados" && !reviewOnly) {
            item {
                CareEventsPanel(vm,session,data,day)
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(routineSection=="Hábitos")"Hábitos recorrentes" else "Atividades do dia",
                        style=MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={creating=true}) {
                        Icon(Icons.Default.Add,null,Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp)); Text("Nova tarefa")
                    }
                }
                if(tasks.isNotEmpty()) {
                    Text("${completedTasks.size} de ${tasks.size} concluídas",
                        style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    ReferenceProgress(completedTasks.size.toFloat()/tasks.size)
                }
            }
            if(tasks.isEmpty()) item {
                ReferenceEmpty(if(reviewOnly)"Rotinas revisadas" else "Nenhuma tarefa neste período",
                    if(reviewOnly)"Não há confirmações pendentes."
                    else "Cadastre uma rotina ou escolha outro dia.")
            }
            listOf("Manhã","Tarde","Noite","Sem horário").forEach { period ->
                val group=tasks.filter { task ->
                    val time=runCatching { java.time.LocalTime.parse(task.text("time")) }.getOrNull()
                    when(period) {
                        "Manhã" -> time!=null && time.hour in 5..11
                        "Tarde" -> time!=null && time.hour in 12..17
                        "Noite" -> time!=null && (time.hour in 18..23 || time.hour in 0..4)
                        else -> time==null
                    }
                }.sortedWith(compareBy<Record> { it.text("time").ifBlank { "99:99" } }.thenBy { it.id })
                if(group.isNotEmpty()) {
                    item(key="routine-section-$period") {
                        Surface(shape=RoundedCornerShape(17.dp),
                            color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.48f)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(period,style=MaterialTheme.typography.titleMedium,
                                    color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,
                                    modifier=Modifier.weight(1f))
                                Text("${group.count{done(it,day,data)}} de ${group.size}",
                                    style=MaterialTheme.typography.labelMedium,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(group,key={ "routine-$period-${it.id}" }) { task ->
                        TaskRow(task,day,data,vm) { editing=task }
                    }
                }
            }
            if(!reviewOnly && routineSection=="Hoje" && tasks.isNotEmpty()) item {
                Text("Toque na atividade para editar ou marcar a conclusão.",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (creating || editing != null) RecordEditor("task", editing, vm, onDismiss = { creating = false; editing = null })
}

@Composable
internal fun ShoppingEditor(vm: CentralViewModel, basket: Basket, entry: Entry?, dismiss: () -> Unit, startBought: Boolean = false) {
    val id = remember { entry?.id ?: java.util.UUID.randomUUID().toString() }
    var advanced by rememberSaveable(id) { mutableStateOf(false) }
    val roomChoices=listOf("Cozinha", "Banheiro", "Lavanderia", "Pets", "Casa")
    var title by remember { mutableStateOf(entry?.title ?: "") }; var unit by remember { mutableStateOf(entry?.unit ?: "un") }; var storageRoom by remember { mutableStateOf(entry?.storageRoom?.takeIf { it.isNotBlank() } ?: entry?.let { suggestedStorageRoom(it.title,it.category) } ?: "Casa") }; var customRoom by remember { mutableStateOf(entry?.storageRoom?.takeIf { it.isNotBlank() && it !in roomChoices }.orEmpty()) }
    var recipient by remember { mutableStateOf(entry?.recipient?.ifBlank { "Casa" } ?: "Casa") }; var needGroup by remember { mutableStateOf(entry?.group?.ifBlank { "Essenciais" } ?: "Essenciais") }; var subgroup by remember { mutableStateOf(entry?.subgroup.orEmpty()) }; var priority by remember { mutableStateOf(entry?.priority?.ifBlank { "Normal" } ?: "Normal") }
    var planned by remember { mutableStateOf(quantityInput(entry?.plannedMilli ?: 1000)) }; var actualQty by remember { mutableStateOf(quantityInput(entry?.actualMilli ?: 1000)) }
    var estimated by remember { mutableStateOf(if(entry?.estimateKnown==true) moneyInput(entry.estimated) else "") }; var real by remember { mutableStateOf(entry?.actual?.let(::moneyInput) ?: "") }; var bought by remember { mutableStateOf(entry?.bought == true || startBought) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    EditDialog(if (entry == null) "Novo item" else "Ficha do produto", dismiss, save = { vm.action {
        require(title.isNotBlank()) { "Informe o produto." }
        require(unit in listOf("un", "kg", "g", "L", "mL", "pacote")) { "Escolha a unidade antes de salvar." }
        val savedRoom=if(storageRoom=="Outro") customRoom.trim().ifBlank { "Casa" } else storageRoom
        val item = Entry(id, title.trim(), unit, Money.quantity(planned), Money.quantity(actualQty), if(estimated.isBlank()) 0 else Money.cents(estimated), real.takeIf { it.isNotBlank() }?.let(Money::cents), bought, estimateKnown=estimated.isNotBlank(),unitReviewed=true,quantityReviewed=true,sourceBaseId=entry?.sourceBaseId.orEmpty(),sourceSkuId=entry?.sourceSkuId.orEmpty(),category=entry?.category.orEmpty(),storageRoom=savedRoom,recipient=recipient,group=needGroup,subgroup=subgroup.trim(),priority=priority,sourcePlanId=entry?.sourcePlanId.orEmpty(),sourceStepId=entry?.sourceStepId.orEmpty())
        if (bought) item.real()
        vm.repo.editBasket(basket.market, basket.revision.takeIf { vm.repo.data.value.baskets.containsKey(basket.market) }) { it.copy(entries = it.entries.filterNot { e -> e.id == id } + item) }; dismiss()
    } }, busy = busy) {
        Field("Produto", title, { title = it })
        Choice("Unidade", listOf("un", "kg", "g", "L", "mL", "pacote"), unit, { unit = it })
        Field("Quantidade", planned, { planned = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(bought, { bought = it }, modifier = Modifier.semantics { contentDescription = "Produto comprado" })
            Text("Já coloquei no carrinho", style = MaterialTheme.typography.bodyMedium)
        }
        if (bought) {
            Field("Quantidade comprada", actualQty, { actualQty = it })
            Field("Preço real por ${unit} (R$)", real, { real = it })
            if (real.isBlank()) Text("Informe o preço para registrar a compra.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (advanced) "Ocultar opções do produto" else "Mais opções do produto")
        }
        if (advanced) {
            Choice("Para quem", listOf("Casa", "Nós", "Rhayra", "Tiago", "Lucca"), recipient, { recipient = it })
            Choice("Grupo", listOf("Essenciais", "Alimentação", "Casa e limpeza", "Higiene e cuidados", "Cuidados do Lucca", "Pets", "Outro"), needGroup, { needGroup = it })
            Field("Subgrupo opcional", subgroup, { subgroup = it })
            Choice("Prioridade", listOf("Alta", "Normal", "Baixa"), priority, { priority = it })
            Choice("Onde guardar", roomChoices + "Outro", if (storageRoom in roomChoices) storageRoom else "Outro", { storageRoom = it })
            if (storageRoom == "Outro") Field("Nome do cômodo", customRoom, { customRoom = it })
            Field("Preço previsto por unidade (opcional, R$)", estimated, { estimated = it })
            if (entry != null && basket.entries.any { it.id == entry.id }) {
                TextButton(onClick = {
                    vm.action {
                        vm.repo.editBasket(basket.market, basket.revision) {
                            it.copy(entries = it.entries.filterNot { e -> e.id == id })
                        }
                        dismiss()
                    }
                }, enabled = !busy) { Text("Remover item da lista") }
            }
        }
    }
}

@Composable
private fun PlansScreen(vm: CentralViewModel, data: DataState, onOpenLinked: (String) -> Unit = {}) {
    var showAreas by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<Record?>(null) }; var contributing by remember { mutableStateOf<Record?>(null) }
    var draft by remember { mutableStateOf<Record?>(null) }
    if (creating || editing != null) { GoalEditor(vm, editing ?: draft,isNew=creating) { creating = false; editing = null;draft=null }; return }
    PlansExperienceScreen(vm, data, onCreate = { draft=null;creating = true }, onEdit = { editing = it }, onContribution = { contributing = it },onStartImported={reference -> draft=projectDraft(reference);creating=true},onOpenLinked=onOpenLinked)

    contributing?.let { plan ->
        var amount by remember { mutableStateOf("0,00") }; var note by remember { mutableStateOf("") }; val id = remember { java.util.UUID.randomUUID().toString() }
        EditDialog("Registrar progresso", { contributing = null }, save = { vm.action { require(note.isNotBlank()) { "Descreva o progresso real." }; vm.repo.save(Record(id, "contribution", plan.isPrivate, mapOf("planId" to plan.id, "note" to note.trim(), "amount" to Money.cents(amount), "date" to LocalDate.now().toString()))); contributing = null } }, busy = vm.busy.collectAsStateWithLifecycle().value) { Text(plan.text("title")); Field("O que avançou?", note, { note = it }); Field("Valor registrado (opcional, R$)", amount, { amount = it }) }
    }
}

/** A manual ledger is not a bank balance or an Open Finance connection. */
internal data class FinancialMonthSummary(val incomeCents: Long, val expenseCents: Long, val entryCount: Int) {
    val resultCents: Long get() = incomeCents - expenseCents
}

internal fun financialMonthRecords(records: List<Record>, month: java.time.YearMonth): List<Record> =
    records.filter { it.kind == "transaction" &&
        runCatching { java.time.YearMonth.from(java.time.LocalDate.parse(it.text("date"))) == month }.getOrDefault(false)
    }

internal fun financialMonthSummary(records: List<Record>): FinancialMonthSummary {
    val income = records.filter { it.text("type") == "Receita" }.sumOf { it.number("amount") }
    val expense = records.filter { it.text("type") == "Despesa" }.sumOf { it.number("amount") }
    return FinancialMonthSummary(income, expense, records.size)
}

@Composable
private fun FinanceScreen(vm: CentralViewModel, data: DataState) {
    var month by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }; val period = java.time.YearMonth.parse(month)
    var creating by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<Record?>(null) }
    val records = financialMonthRecords(data.records, period)
    val summary = financialMonthSummary(records)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Finanças", "Controle manual • valores em reais") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { month = period.minusMonths(1).toString() }) { Text("Anterior") }; Text(period.format(DateTimeFormatter.ofPattern("MM/yyyy"))); TextButton(onClick = { month = period.plusMonths(1).toString() }) { Text("Próximo") } } }
        data.records.firstOrNull { it.kind == "documentedMonth" && it.text("month") == month }?.let { imported -> item { DocumentedMonthCard(imported) } }
        val bills=data.records.filter { it.kind == "plannedBill" && it.text("month") == month }
        if(bills.isNotEmpty()) item { Text("Compromissos previstos", style = MaterialTheme.typography.titleMedium); Text("Ainda sem conciliação. Valores ausentes ficam a confirmar.", style = MaterialTheme.typography.bodySmall) }
        items(bills, key = { it.id }) { PlannedBillCard(it) }
        item { Panel("Controle manual", "${summary.entryCount} lançamento(s) registrado(s) neste mês") {
            AmountRow("Receitas", summary.incomeCents)
            AmountRow("Despesas", summary.expenseCents)
            AmountRow("Resultado dos lançamentos", summary.resultCents)
            Text("Este é um resumo do controle manual, não o saldo do banco. Notas importadas e contas previstas não geram lançamentos automaticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Button(onClick = { creating = true }) { Text("Novo lançamento") } }
        if (records.isEmpty()) item { Empty("Sem lançamentos neste mês", "Registre receitas e despesas de forma explícita.") }
        items(records.sortedByDescending { it.text("date") }, key = { it.id }) { r ->
            Card(onClick = { editing = r }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(r.text("title"), fontWeight = FontWeight.Bold); Text("${r.text("type")} • ${money(r.number("amount"))}"); Text("${displayDate(r.text("date"))} • ${r.text("category")} • ${if (r.isPrivate) "Privado" else "Compartilhado"}", style = MaterialTheme.typography.labelSmall) } }
        }
    }
    if (creating || editing != null) RecordEditor("transaction", editing, vm, onDismiss = { creating = false; editing = null })
}

@Composable
private fun TogetherScreen(vm: CentralViewModel, session: SessionState, data: DataState) {
    var section by rememberSaveable { mutableStateOf("Nossa Jornada") }; var create by remember { mutableStateOf(false) }
    var momentEdit by remember { mutableStateOf<Record?>(null) }
    var destination by rememberSaveable { mutableStateOf("Ilha das Surpresas") }; var cardIndex by rememberSaveable { mutableIntStateOf(0) }
    val cards = when (destination) {
        "Ilha das Surpresas" -> listOf("Escolher uma música para ouvir juntos", "Planejar um microencontro de 15 minutos", "Inventar um passeio que cabe no nosso dia")
        "Bosque das Conversas" -> listOf("Compartilhar uma lembrança boa desta semana", "Conversar sobre algo que queremos aprender", "Contar uma história que ainda não contamos")
        else -> listOf("Imaginar um lugar que queremos conhecer", "Desenhar uma ideia para nosso futuro", "Escolher um pequeno sonho para explorar")
    }
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("journey_${session.uid}", android.content.Context.MODE_PRIVATE)
    var level by rememberSaveable(session.uid) { mutableStateOf(prefs.getString("level", "Desligado") ?: "Desligado") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val view = LocalView.current
    val experience = LocalExperience.current
    fun touch() { AndroidInteractions.haptic(view, experience.haptics); AndroidInteractions.clickSound(view, experience.sounds) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ReferenceHeading("Nós", "Pequenas aventuras. Boas lembranças.") }
        item { ReferenceTabs(listOf("Nossa Jornada","Jogos","Conversas","Conquistas"),section) { touch();section=it } }
        item { Text(if (session.local) "Espaço pessoal neste aparelho" else "${data.members.size} participantes no espaço", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (section == "Nossa Jornada") {
            item { CoupleLandingReference(data,session,onMoments={touch();section="Conversas"},onJourney={touch();section="Jogos"},onMemories={touch();section="Conquistas"},onCreateMoment={touch();create=true}) }
        } else if (section == "Conversas") {
            item { Button(onClick = { touch(); create = true }, enabled = !busy) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Guardar um momento") } }
            val moments = data.records.filter { it.kind == "moment" }
            if (moments.isEmpty()) item { Empty("As memórias começam com vocês", "Guarde uma lembrança real, pessoal ou compartilhada.") }
            items(moments, key = { it.id }) { r -> Card(onClick={momentEdit=r},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(r.text("title"),style=MaterialTheme.typography.titleMedium);Text("${displayDate(r.text("date"))} · ${if(r.isPrivate) "Privada" else "Compartilhada"}",style=MaterialTheme.typography.bodySmall);Text(r.text("note"));Text("Editar lembrança",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) } } }
        } else if (section=="Conquistas") {
            val saved=data.records.filter { it.kind=="quest" && !it.isPrivate && !it.flag("cancelled") && coupleQuestState(it,data.members).confirmedByBoth }
            if(saved.isEmpty()) item { Empty("Baú de descobertas","As experiências confirmadas pelos dois aparecem aqui.") }
            items(saved,key={it.id}) { memory -> Panel(memory.text("title"),displayDate(memory.text("date"))) { Text(memory.text("destination"));Text("Experiência confirmada pelos dois",style=MaterialTheme.typography.labelSmall) } }
        } else {
            item { Panel("No nosso ritmo", "A jornada é opcional") { Choice("Atmosfera", listOf("Desligado", "Discreto", "Equilibrado", "Divertido"), level, { touch(); level = it; prefs.edit().putString("level", it).apply() }); Text("Lazer em dupla, sem ranking. Gastos e cuidados não dão pontos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (level == "Desligado") item { Empty("Jornada pausada", "Você pode continuar usando todo o app. Ative uma atmosfera se quiser explorar convites.") }
            else if (section == "Jogos") {
                item { Text("Escolham um mundo", style = MaterialTheme.typography.titleMedium) }
                items(listOf("Ilha das Surpresas", "Bosque das Conversas", "Observatório dos Sonhos")) { world ->
                    val active = destination == world
                    val worldIcon = when (world) { "Ilha das Surpresas" -> Icons.Default.Star; "Bosque das Conversas" -> Icons.Default.Favorite; else -> Icons.Default.DateRange }
                    val description = when (world) { "Ilha das Surpresas" -> "Um encontro, uma música, uma surpresa."; "Bosque das Conversas" -> "Histórias e conversas para compartilhar."; else -> "Ideias e sonhos para explorar juntos." }
                    Card(onClick = { touch(); destination = world; cardIndex = 0 }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PlanAccentIcon(worldIcon)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(world, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (active) Icon(Icons.Default.Check, "Mundo selecionado", Modifier.size(20.dp))
                        }
                    }
                }
                item { Panel("Uma carta para vocês", cards[cardIndex % cards.size]) {
                    TextButton(onClick = { touch(); cardIndex++ }) { Text("Explorar outra carta") }
                    Button(onClick = { touch(); vm.action { require(!session.local && data.members.size == 2) { "Conecte duas contas ao mesmo espaço para propor um convite real." }; vm.repo.save(Record(kind = "quest", fields = mapOf("title" to cards[cardIndex % cards.size], "destination" to destination, "creatorUid" to session.uid, "accepted" to listOf(session.uid), "completed" to emptyList<String>(), "cancelled" to false, "date" to LocalDate.now().toString()))) } }, enabled = !busy && !session.local && data.members.size == 2) { Text("Propor convite") }
                    if (session.local || data.members.size != 2) Text("Conectem suas duas contas para propor uma aventura.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                items(data.records.filter { it.kind == "quest" && !it.isPrivate && !it.flag("cancelled") }, key = { it.id }) { quest ->
                    val state = coupleQuestState(quest, data.members)
                    val accepted = state.accepted
                    val completed = state.completed
                    Panel(quest.text("title"), "${accepted.size}/2 aceites • ${completed.size}/2 confirmações de realização") {
                        LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) { Text("Você",style=MaterialTheme.typography.labelLarge);Text(if(session.uid in completed) "Realizado" else if(session.uid in accepted) "Aceitou" else "Aguardando",style=MaterialTheme.typography.bodySmall) }
                            Column(Modifier.weight(1f)) { Text("Parceiro",style=MaterialTheme.typography.labelLarge);Text(if(completed.any { it!=session.uid }) "Realizado" else if(accepted.any { it!=session.uid }) "Aceitou" else "Aguardando",style=MaterialTheme.typography.bodySmall) }
                        }
                        if (session.uid !in accepted) Button(onClick = { touch(); vm.action { vm.repo.questResponse(quest, "accept") } }, enabled = !busy) { Text("Topo") }
                        if (state.readyToComplete && session.uid in accepted && session.uid !in completed) Button(onClick = { touch(); vm.action { vm.repo.questResponse(quest, "complete") } }, enabled = !busy) { Text("Confirmar que aconteceu") }
                        if (!state.confirmedByBoth) TextButton(onClick = { touch(); vm.action { vm.repo.questResponse(quest, if (quest.text("creatorUid") == session.uid) "cancel" else "decline") } }, enabled = !busy) { Text("Talvez outra hora") }
                        else Text("Lembrança cooperativa guardada no Baú.")
                    }
                }
            } else {
                val quests = data.records.filter { it.kind == "quest" && !it.isPrivate && !it.flag("cancelled") && coupleQuestState(it, data.members).confirmedByBoth }
                if (quests.isEmpty()) item { Empty("Baú de descobertas", "Uma lembrança aparece quando ambos confirmam uma experiência real.") }
                items(quests, key = { it.id }) { Panel("Experiência compartilhada", it.text("destination")) { Text(it.text("title")); Text("Proposta em ${displayDate(it.text("date"))} • duas confirmações reais", style = MaterialTheme.typography.labelSmall) } }
            }
        }
    }
    if (create || momentEdit!=null) RecordEditor("moment", momentEdit, vm, onDismiss = { create = false;momentEdit=null })
}

/** Defense-in-depth: private health records never render from a shared collection. */
internal fun visibleHealthRecords(records: List<Record>): List<Record> =
    records.filter { it.kind == "health" && it.isPrivate }

@Composable
private fun HealthScreen(vm: CentralViewModel, data: DataState) {
    var create by remember { mutableStateOf(false) }; var edit by remember { mutableStateOf<Record?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Saúde pessoal", "Registros privados, visíveis somente à sua conta."); Button(onClick = { create = true }) { Text("Novo registro pessoal") } }
        val records = visibleHealthRecords(data.records)
        if (records.isEmpty()) item { Empty("Um espaço só seu", "Registre voluntariamente hábitos ou compromissos. Sophia não acessa estes dados.") }
        items(records, key = { it.id }) { r -> Card(onClick = { edit = r }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(r.text("title"), fontWeight = FontWeight.Bold); Text(r.text("note")); Text(displayDate(r.text("date"))) } } }
    }
    if (create || edit != null) RecordEditor("health", edit, vm, onDismiss = { create = false; edit = null })
}

@Composable
internal fun SettingsScreen(vm: CentralViewModel, session: SessionState, data: DataState, export: () -> Unit, notify: () -> Unit, navigate: (String) -> Unit = {}, photo: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf("Início") }
    var revealDiagnostics by rememberSaveable { mutableStateOf(false) }
    var partnerEmail by rememberSaveable { mutableStateOf("") }; var code by rememberSaveable { mutableStateOf("") }; var invite by remember { mutableStateOf("") }; var leaving by remember { mutableStateOf(false) }
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("reminders_${session.uid}", android.content.Context.MODE_PRIVATE)
    var reminders by rememberSaveable(session.uid) { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var quiet by rememberSaveable(session.uid) { mutableStateOf(prefs.getBoolean("quiet", true)) }
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val view=LocalView.current; val experience=LocalExperience.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableStateOf(false) }
    var reviews by rememberSaveable { mutableStateOf(false) }
    var haptics by rememberSaveable(session.uid) { mutableStateOf(experience.haptics) }
    var motion by rememberSaveable(session.uid) { mutableStateOf(experience.motion) }
    var sounds by rememberSaveable(session.uid) { mutableStateOf(experience.sounds) }
    var notificationAllowed by remember { mutableStateOf(AndroidInteractions.notificationsAllowed(context)) }
    var testInQuiet by rememberSaveable { mutableStateOf(false) }
    var showTaskDetailsOnLockScreen by rememberSaveable(session.uid) { mutableStateOf(prefs.getBoolean("showDetailsOnLockScreen",false)) }
    var reminderDiagnosticRefresh by remember { mutableIntStateOf(0) }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle,context) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) notificationAllowed=AndroidInteractions.notificationsAllowed(context) }
        lifecycle.lifecycle.addObserver(observer);onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session.uid) {
        if (!session.local) vm.action { vm.repo.refreshVerification() }
        if (!prefs.contains("enabled")) prefs.edit().putBoolean("enabled", true).apply()
        if (Build.VERSION.SDK_INT >= 33 && !notificationAllowed && !prefs.getBoolean("notificationAsked", false)) {
            prefs.edit().putBoolean("notificationAsked", true).apply()
            notify()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(section=="Início") item {
            SettingsReferenceLanding(
                session=session,photoUri=experience.photo,onPhoto=photo,
                onSection={section=it},onRoute=navigate
            )
        } else if(section!="Início") item {
            TextButton(onClick={section="Início"}) { Text("‹  Voltar às configurações") }
            ReferenceHeading(section)
        }
        if (section == "Aparência") item { Panel("Do seu jeito", "Pequenos detalhes que fazem diferença.") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Vibrações leves ao tocar",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium); Switch(haptics,{ haptics=it;context.getSharedPreferences("experience_${session.uid}",android.content.Context.MODE_PRIVATE).edit().putBoolean("haptics",it).apply();AndroidInteractions.haptic(view,it) },modifier=Modifier.semantics { contentDescription="Vibrações leves ao tocar" }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Animações suaves",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium); Switch(motion,{ motion=it;context.getSharedPreferences("experience_${session.uid}",android.content.Context.MODE_PRIVATE).edit().putBoolean("motion",it).apply() },modifier=Modifier.semantics { contentDescription="Animações suaves" }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Sons ao tocar",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium); Switch(sounds,{ sounds=it;context.getSharedPreferences("experience_${session.uid}",android.content.Context.MODE_PRIVATE).edit().putBoolean("sounds",it).apply();AndroidInteractions.clickSound(view,it) },modifier=Modifier.semantics { contentDescription="Sons ao tocar" }) }
            TextButton(onClick = { AndroidInteractions.clickSound(view,sounds) },enabled=sounds) { Text("Ouvir o som de teste") }
            TextButton(onClick = { AndroidInteractions.haptic(view,haptics) },enabled=haptics) { Text("Sentir a vibração de teste") }
            Text("A resposta tátil respeita a configuração do Android. Sua foto fica somente neste aparelho.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (section == "Conta e casal" && !session.local && session.household.isBlank()) {
            item { Panel("Vamos conectar vocês?", "Crie o espaço ou aceite o convite que recebeu.") { Button(onClick = { vm.action { vm.repo.createHouse() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Criar nosso espaço") }; HorizontalDivider(Modifier.padding(vertical = 6.dp)); Field("Código recebido", code, { code = it }); OutlinedButton(onClick = { vm.action { vm.repo.accept(code) } }, enabled = !busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Aceitar convite") } } }
        } else if (section == "Conta e casal" && !session.local) {
            item { Panel("Nosso espaço", when(data.members.size) { 2 -> "Vocês dois estão conectados"; 1 -> "Falta só conectar seu parceiro"; else -> "Confirmando participantes…" }) {
                if (data.members.size == 1) {
                    Field("E-mail do parceiro", partnerEmail, { partnerEmail = it })
                    Button(onClick = { vm.action("Convite criado. Compartilhe com seu parceiro.") { invite = vm.repo.invite(partnerEmail) } }, enabled = !busy && partnerEmail.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Criar convite") }
                    Text("Válido por 24 horas • exclusivo para esse e-mail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (invite.isNotBlank()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Convite pronto", style = MaterialTheme.typography.titleMedium)
                            Text("Envie o código ao parceiro. Ele entra na própria conta e toca em aceitar convite.", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(invite)); vm.message.value = "Código copiado." }) { Text("Copiar código") }
                                TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Nosso espaço na Central do Casal: $invite\nEntre com sua conta e aceite o convite em Configurações. Válido por 24 horas.") }, "Compartilhar convite")) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text("Compartilhar") }
                            }
                        }
                    }
                }
                TextButton(onClick = { leaving = true }, enabled = !busy) { Text("Sair deste espaço", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
        }
        if (section == "Lembretes") item { Panel("No seu ritmo", "Escolha quando quer ser lembrado.") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Lembretes de tarefas", style = MaterialTheme.typography.bodyMedium); Text("Para ${session.name.ifBlank { "esta conta" }}, neste aparelho", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(reminders, { reminders = it; prefs.edit().putBoolean("enabled", it).apply(); if (it) { notify(); data.records.filter { r -> r.kind == "task" }.forEach { r -> Reminders.schedule(vm.getApplication(), session.uid, r,session.name) } } else Reminders.cancelAll(vm.getApplication(), session.uid) }, modifier = Modifier.semantics { contentDescription = "Lembretes de tarefas para esta conta neste aparelho" }) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Modo Sossego", style = MaterialTheme.typography.bodyMedium); Text("Silêncio das 00:05 às 05:40", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(quiet, { enabled ->
                    quiet = enabled
                    prefs.edit().putBoolean("quiet", enabled).apply()
                    data.records.filter { record -> record.kind == "task" }.forEach { task ->
                        val completeToday = data.records.any { it.kind == "occurrence" && it.text("taskId") == task.id && it.text("date") == LocalDate.now().toString() && it.flag("done") }
                        Reminders.schedule(context, session.uid, task, session.name, completedDay = LocalDate.now().takeIf { completeToday })
                    }
                    reminderDiagnosticRefresh++
                }, modifier = Modifier.semantics { contentDescription = "Modo Sossego, das 00:05 às 05:40" }) }
        } }
        if (section == "Lembretes") item { Panel("Notificações e permissões", if(notificationAllowed) "Notificações permitidas pelo Android" else "Notificações ainda bloqueadas no Android") {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(!notificationAllowed && Build.VERSION.SDK_INT>=33) Button(onClick = notify) { Text("Permitir notificações") }
                OutlinedButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName)) }) { Text("Configurar no Android") }
            }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(testInQuiet,{testInQuiet=it},modifier=Modifier.semantics { contentDescription="Permitir somente este teste durante o Sossego" }); Text("Permitir somente este teste durante o Sossego",style=MaterialTheme.typography.bodySmall) }
            FilledTonalButton(onClick = { vm.message.value=AndroidInteractions.sendTestNotification(context,session.uid,testInQuiet);notificationAllowed=AndroidInteractions.notificationsAllowed(context);testInQuiet=false },enabled=notificationAllowed) { Text("Enviar notificação de teste") }
            OutlinedButton(onClick={ vm.message.value=Reminders.sendDetailedTest(context,session.uid,session.name) },enabled=notificationAllowed) { Text("Testar aviso com detalhes da tarefa") }
            OutlinedButton(onClick={ vm.message.value=Reminders.scheduleDeliveryTest(context,session.uid,session.name) },enabled=notificationAllowed && reminders) { Text("Testar agendamento real em 2 minutos") }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar detalhes da tarefa na tela bloqueada",style=MaterialTheme.typography.bodyMedium)
                    Text("Opcional: pode expor informações pessoais a quem estiver perto do celular. SOS permanece privado.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(showTaskDetailsOnLockScreen,{
                    showTaskDetailsOnLockScreen=it
                    prefs.edit().putBoolean("showDetailsOnLockScreen",it).apply()
                })
            }
            TextButton(onClick = { revealDiagnostics = !revealDiagnostics }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (revealDiagnostics) "Ocultar diagnóstico técnico" else "Diagnóstico técnico e permissões avançadas")
            }
            if (revealDiagnostics) {
            val diagnosis=remember(session.uid,data.records,reminderDiagnosticRefresh,notificationAllowed) {
                Reminders.diagnostics(context,session.uid)
            }
            Text("Diagnóstico: ${diagnosis.futureSchedules} aviso(s) futuro(s) programado(s) · ${diagnosis.knownTasks} tarefa(s) no agendador local.",style=MaterialTheme.typography.bodySmall)
            val checks = remember(session.uid,session.name,data.records,reminderDiagnosticRefresh) {
                data.records.filter { it.kind=="task" }.map { Reminders.inspect(context,session.uid,it,session.name) }
                    .sortedWith(compareBy<Reminders.TaskCheck> { it.plannedAt != null }.thenBy { it.title })
            }
            if (checks.isNotEmpty()) {
                val blocked = checks.count { it.plannedAt == null }
                Text("Verificação individual: ${checks.size-blocked} programada(s) · $blocked sem horário ativo neste aparelho.",
                    style=MaterialTheme.typography.labelLarge)
                checks.take(18).forEach { check ->
                    Column(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
                        Text(check.title,style=MaterialTheme.typography.bodyMedium)
                        Text(check.status,style=MaterialTheme.typography.bodySmall,
                            color=if(check.plannedAt==null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        check.plannedAt?.let { at -> Text(
                            "Agendado: " + java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
                                .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(at)),
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        ) }
                    }
                }
                if(checks.size>18) Text("Mais ${checks.size-18} tarefa(s) não exibidas. Revise a Rotina.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(blocked>0) OutlinedButton(onClick={navigate("Rotina")}) { Text("Abrir Rotina para corrigir") }
            }
            Text(when {
                !diagnosis.notificationsAllowed -> "Bloqueio: notificações desativadas no Android."
                !diagnosis.taskChannelAllowed -> "Bloqueio: canal 'Tarefas com detalhes' silenciado no Android."
                !reminders -> "Bloqueio: lembretes desligados neste perfil."
                diagnosis.futureSchedules==0 -> "Nenhum aviso futuro encontrado. Verifique o horário, responsável e a opção 'Permitir lembrete' nas tarefas."
                else -> "O aplicativo programou avisos, mas o Android ainda pode atrasar a entrega por economia de bateria."
            },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            diagnosis.nextAt?.let { at ->
                Text("Próximo programado: ${java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(at))}",style=MaterialTheme.typography.bodySmall)
            }
            diagnosis.lastDeliveredAt?.let { at ->
                Text("Última notificação enviada ao Android: ${java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(at))}",style=MaterialTheme.typography.bodySmall)
            }
            if(diagnosis.lastFailedAt!=null) Text("Houve falha recente ao agendar ou exibir um aviso. Execute o teste em dois minutos.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            diagnosis.lastDeliveryBlockedAt?.let { at ->
                Text("Entrega de aviso bloqueada pelo Android em " +
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault())
                        .format(java.time.Instant.ofEpochMilli(at)) +
                    ". Confira permissão de notificações e o canal de tarefas; próximas ocorrências recorrentes continuam agendadas.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={reminderDiagnosticRefresh++}) { Text("Atualizar diagnóstico") }
                OutlinedButton(onClick={
                    Reminders.cancelAll(context,session.uid)
                    data.records.filter { it.kind=="task" }.forEach { task ->
                        Reminders.schedule(context,session.uid,task,session.name)
                    }
                    reminderDiagnosticRefresh++
                    vm.message.value="Avisos recalculados neste aparelho. Confira o próximo horário no diagnóstico."
                },enabled=reminders && session.uid.isNotBlank()) { Text("Reagendar tarefas") }
            }
            OutlinedButton(onClick={
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,"central-tasks-v2")) }
                    .onFailure { vm.message.value="Não foi possível abrir o canal de tarefas. Confira as notificações nas configurações do Android." }
            }) { Text("Configurar canal de tarefas no Android") }
            OutlinedButton(onClick={
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+context.packageName))) }
                    .onFailure { vm.message.value="Não foi possível abrir as configurações deste aplicativo." }
            }) { Text("Ver bateria e permissões do aplicativo") }
            Text("O teste confirma a permissão. Lembretes reais mostram tarefa, horário, responsável e observação. No Samsung, confira também a economia de bateria e o Não Perturbe.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val alarmManager=remember { context.getSystemService(android.app.AlarmManager::class.java) }
            val exactAlarmAllowed=Build.VERSION.SDK_INT<31 || alarmManager?.canScheduleExactAlarms()==true
            Text(if(exactAlarmAllowed) "Avisos no horário: alarmes exatos disponíveis." else "Avisos no horário: alarme aproximado. O Android pode atrasá-los.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(Build.VERSION.SDK_INT>=31 && !exactAlarmAllowed) OutlinedButton(onClick={
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+context.packageName))) }
                    .onFailure { vm.message.value="Ative Alarmes e lembretes no acesso especial aos aplicativos." }
            }) { Text("Permitir alarmes no horário") }
            }
        } }
        if (section == "Privacidade") item { Panel("Privacidade e dados", if(session.local) "Seus registros pessoais ficam neste aparelho." else "Seus registros pessoais ficam só na sua conta.") { OutlinedButton(onClick = export, modifier = Modifier.fillMaxWidth()) { Text("Exportar meus registros") }; Text("Inclui os dados carregados neste aparelho. Não inclui registros privados do parceiro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (section == "Avançado") data.records.firstOrNull { it.kind == "appRelease" }?.let { release -> item {
            val uri=android.net.Uri.parse(release.text("url")); val trusted=uri.scheme == "https" && uri.host == "drive.google.com"
            Panel("Atualizações", if(release.number("minimumVersionCode") > BuildConfig.VERSION_CODE) "Atualização importante disponível" else if(release.number("versionCode") > BuildConfig.VERSION_CODE) "Atualização opcional disponível" else "Você está com a versão atual") {
                Text(if(session.local) "Este espaço salva somente no aparelho. A atualização do aplicativo preserva seus registros locais." else "Produtos, preços e registros atualizam pela nuvem. Uma nova instalação só é necessária quando o aplicativo mudar.", style = MaterialTheme.typography.bodySmall)
                if(trusted && release.number("versionCode") > BuildConfig.VERSION_CODE) Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) }) { Text("Abrir atualização no Drive") }
                Text("${BuildConfig.VERSION_NAME} instalada", style = MaterialTheme.typography.labelSmall)
            }
        } }
        if(section == "Avançado" && data.records.any { it.kind == "sourceReview" }) item { TextButton(onClick = { reviews = !reviews }) { Text(if(reviews) "Recolher pontos a confirmar" else "Ver pontos a confirmar dos arquivos") } }
        if(section == "Avançado" && reviews) items(data.records.filter { it.kind == "sourceReview" }, key = { it.id }) { review -> Panel("Ponto a confirmar", if(review.isPrivate) "Somente minha conta" else "No espaço do casal") { Text(review.text("note"), style = MaterialTheme.typography.bodySmall) } }
        if (section == "Privacidade") item { OutlinedButton(onClick = { navigate("Segurança") }, modifier = Modifier.fillMaxWidth()) { Text("SOS e localização") } }
        if (section == "Avançado") item { OutlinedButton(onClick = { navigate("Casa") }, modifier = Modifier.fillMaxWidth()) { Text("Nossa Casa e integrações") } }
        if (section == "Avançado") item { TextButton(onClick = { details = !details }) { Text(if(details) "Ocultar detalhes da versão" else "Sobre esta versão") }; if (details) Text("Sophia, Alexa, Avant, Poup+, Notion e Google login ainda não estão conectados. SOS e localização são experimentais e ainda precisam de validação em dois aparelhos. Lembretes podem atrasar por bateria.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (section == "Conta e casal") item { OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }; Text("Central do Casal ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Sair do espaço?") }, text = { Text("Sua conta perde acesso aos registros compartilhados. Seus registros privados permanecem na sua conta. O histórico do espaço é preservado.") }, confirmButton = { Button(onClick = { vm.action { Reminders.cancelAll(vm.getApplication(), session.uid); vm.repo.leaveHouse(); leaving = false } }) { Text("Sair do espaço") } }, dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancelar") } })
}

@Composable
private fun IntegrationScreen(route: String, data: DataState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading(route, when (route) { "Sophia" -> "Serviço de IA não conectado"; "Casa" -> "Dispositivos não conectados"; else -> "Recursos de segurança indisponíveis" })
        Text(when (route) {
            "Sophia" -> "O chat e a voz exigem backend autenticado, provedor de IA e consentimentos por ferramenta. Esta versão não envia seus dados a uma IA e não apresenta respostas simuladas."
            "Casa" -> "Quartinho, Luz da Mel e Cozinha: integração Avant NEO pendente. Alexa do quarto, Alexa da cozinha e Central Fixa: integração e testes físicos pendentes. Comandos só ficarão disponíveis depois da conexão comprovada."
            else -> "Volta Segura e SOS ainda não estão implementados ou validados em dois celulares. Nenhum alerta ou localização é enviado por esta versão. Não utilize este app como recurso de emergência."
        })
        if(route == "Casa") {
            data.records.firstOrNull { it.kind == "houseSetup" }?.let { setup -> Panel("Servidor doméstico", setup.text("status")) { Text("Configuração registrada como planejamento. Modelo do aparelho e dispositivos ainda a confirmar.", style = MaterialTheme.typography.bodySmall) } }
            data.records.filter { it.kind == "automationProposal" }.forEach { proposal -> Panel(proposal.text("title"), "Proposta • ainda desativada") { Text("Esta proposta foi preservada no planejamento. Nenhuma ação automática é executada pelo app.", style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

@Composable
private fun RecordEditor(kind: String, initial: Record?, vm: CentralViewModel, onDismiss: () -> Unit) {
    val id = remember { initial?.id ?: java.util.UUID.randomUUID().toString() }
    var title by remember { mutableStateOf(initial?.text("title") ?: "") }; var note by remember { mutableStateOf(initial?.text("note") ?: "") }
    var date by remember { mutableStateOf(initial?.text("date")?.let(::displayDate) ?: LocalDate.now().format(dateFormat)) }
    var isPrivate by remember { mutableStateOf(initial?.isPrivate ?: (kind in setOf("transaction", "health"))) }
    var owner by remember { mutableStateOf(initial?.text("owner", "Ambos") ?: "Ambos") }; var repeat by remember { mutableStateOf(initial?.text("repeat", "Única") ?: "Única") }; var time by remember { mutableStateOf(initial?.text("time") ?: "") }
    var weekdays by remember { mutableStateOf((initial?.fields?.get("days") as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() }?.toSet() ?: emptySet<Int>()) }
    var taskReminder by remember { mutableStateOf(initial?.flag("skipReminder") != true) }
    var reviewed by remember { mutableStateOf(initial?.flag("needsReview") != true) }
    var timeReviewed by remember { mutableStateOf(initial?.flag("needsTimeReview") != true) }
    var horizon by remember { mutableStateOf(initial?.text("horizon", "Curto prazo") ?: "Curto prazo") }; var amount by remember { mutableStateOf(moneyInput(initial?.number(if (kind == "plan") "target" else "amount") ?: 0)) }
    var type by remember { mutableStateOf(initial?.text("type", "Despesa") ?: "Despesa") }; var category by remember { mutableStateOf(initial?.text("category", "Outros") ?: "Outros") }
    var confirmRemove by remember { mutableStateOf(false) }; val busy by vm.busy.collectAsStateWithLifecycle()
    EditDialog(when (kind) { "task" -> "Tarefa"; "plan" -> "Plano"; "transaction" -> "Lançamento manual"; "moment" -> "Momento"; else -> "Registro pessoal" }, onDismiss, save = { vm.action {
        require(title.isNotBlank()) { "Informe um título." }; val isoDate = dateInput(date)
        if (kind == "task" && time.isNotBlank()) java.time.LocalTime.parse(time)
        if(kind == "task" && repeat == "Dias da semana") require(weekdays.isNotEmpty()) { "Selecione os dias da semana." }
        if(kind == "task" && taskReminder && time.isNotBlank()) require(timeReviewed && reviewed) { "Confirme os dados e o horário antes de habilitar lembretes." }
        val provenance = initial?.fields?.filterKeys { it in setOf("source", "sourceHash", "sourceRecurrence", "original", "importedAt", "sourcePlanId", "sourceStepId") } ?: emptyMap()
        val fields: Map<String, Any?> = provenance + mapOf("title" to title.trim(), "note" to note.trim(), "date" to isoDate) + when (kind) {
            "task" -> mapOf("owner" to owner, "category" to category, "repeat" to repeat, "time" to time, "days" to weekdays.sorted(), "skipReminder" to (!taskReminder || time.isBlank()), "needsReview" to !reviewed, "needsTimeReview" to !timeReviewed)
            "plan" -> mapOf("target" to Money.cents(amount), "horizon" to horizon)
            "transaction" -> mapOf("amount" to Money.cents(amount), "type" to type, "category" to category, "origin" to "Manual")
            else -> emptyMap()
        }
        val record = Record(id, kind, if (kind == "health") true else isPrivate, fields); vm.repo.save(record)
        if (kind == "task") Reminders.schedule(vm.getApplication(), vm.repo.session.value.uid, record, vm.repo.session.value.name)
        onDismiss()
    } }, busy = busy) {
        Field("Título", title, { title = it }); Field("Data (dd/mm/aaaa)", date, { date = it }); Field(if (kind == "plan") "Próxima ação / descrição" else "Observações", note, { note = it }, singleLine = false)
        when (kind) {
            "task" -> {
                Choice("Visibilidade", listOf("Compartilhada com nós dois", "Só para mim"), if(isPrivate) "Só para mim" else "Compartilhada com nós dois", { isPrivate = it == "Só para mim" })
                Text(if(isPrivate) "Só você verá esta tarefa." else "Os dois veem esta tarefa; o responsável define para quem ela aparece como pendência.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Choice("Responsável", listOf("Tiago", "Rhayra", "Ambos"), owner, { owner = it }); Choice("Área", listOf("Pessoal", "Casa", "Lucca", "Rotina"), category, { category = it }); Choice("Repetição", listOf("Única", "Diária", "Semanal", "Dias da semana", "Mensal", "Primeiro domingo após dia 8"), repeat, { repeat = it })
                if(repeat == "Dias da semana") FlowRow { listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEachIndexed { index, day -> FilterChip(index+1 in weekdays, { weekdays = if(index+1 in weekdays) weekdays-(index+1) else weekdays+(index+1) }, label = { Text(day) }) } }
                Field("Horário opcional (HH:mm)", time, { time = it })
                if(initial?.flag("needsReview") == true) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(reviewed, { reviewed = it }, modifier = Modifier.semantics { contentDescription = "Confirmar os dados desta rotina" }); Text("Confirmar os dados desta rotina", style = MaterialTheme.typography.bodySmall) }
                if(initial?.flag("needsTimeReview") == true) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(timeReviewed, { timeReviewed = it }, modifier = Modifier.semantics { contentDescription = "Confirmar o horário registrado" }); Text("Confirmar o horário registrado", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(taskReminder && time.isNotBlank(), { taskReminder = it }, enabled = time.isNotBlank(), modifier = Modifier.semantics { contentDescription = "Permitir lembrete desta tarefa" }); Text(if(time.isBlank()) "Informe um horário para receber aviso desta tarefa." else "Permitir lembrete desta tarefa", style = MaterialTheme.typography.bodySmall) }
            }
            "plan" -> { Choice("Horizonte", listOf("Curto prazo", "Médio prazo", "Longo prazo"), horizon, { horizon = it }); Field("Valor-alvo opcional (R$)", amount, { amount = it }) }
            "transaction" -> { Choice("Tipo", listOf("Receita", "Despesa"), type, { type = it }); Field("Valor (R$)", amount, { amount = it }); Choice("Categoria", listOf("Mercado", "Transporte", "Parcelas/dívidas", "Cuidados pessoais", "Casa", "Lucca", "Outros"), category, { category = it }) }
        }
        if (kind != "health" && kind != "task") Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isPrivate, { isPrivate = it }, modifier = Modifier.semantics { contentDescription = "Privado, somente minha conta" }, enabled = initial == null); Text("Privado • só minha conta") }
        if (initial != null) TextButton(onClick = { confirmRemove = true }, enabled = !busy) { Text("Excluir registro") }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Excluir este registro?") }, text = { Text("Esta ação remove o registro do espaço atual. Exporte os dados antes se precisar preservar uma cópia.") }, confirmButton = { Button(onClick = { vm.action { vm.repo.remove(initial!!); if (kind == "task") Reminders.cancelTask(vm.getApplication(), vm.repo.session.value.uid, id); confirmRemove = false; onDismiss() } }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } })
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(value, change, label = { Text(label, style = MaterialTheme.typography.bodySmall) }, textStyle = MaterialTheme.typography.bodyLarge, singleLine = singleLine, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, unfocusedContainerColor = MaterialTheme.colorScheme.background, focusedContainerColor = MaterialTheme.colorScheme.surface))
}
@Composable
private fun Choice(label: String, choices: List<String>, value: String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min=52.dp), shape=RoundedCornerShape(14.dp), border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Text("$label: $value", Modifier.weight(1f)); Icon(Icons.Default.KeyboardArrowDown,null) }; DropdownMenu(open, { open = false }) { choices.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); open = false }) } } }
}
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditDialog(title: String, dismiss: () -> Unit, save: () -> Unit, busy: Boolean, body: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick=dismiss,enabled=!busy) { Text("Cancelar") } }
            body()
            Button(onClick=save,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(busy) "Salvando…" else "Salvar") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable
private fun Panel(title: String, subtitle: String, body: @Composable ColumnScope.() -> Unit) {
    QuietCard(title,subtitle,content=body)
}
@Composable
private fun AmountRow(label: String, cents: Long) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(money(cents), fontWeight = FontWeight.SemiBold) } }
@Composable
private fun PageHeading(title: String, subtitle: String) { Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium) } }
@Composable
private fun Empty(title: String, subtitle: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Add, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

