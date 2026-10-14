package com.centraldocasal.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.os.Handler
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.bluetooth.BluetoothAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal data class SafetyLocation(val latitude: Double, val longitude: Double, val capturedAt: Long) {
    fun isFresh(now: Long): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        now - capturedAt in 0L..90_000L
    fun mapsUrl() = "https://maps.google.com/?q=$latitude,$longitude"
}

internal object SafetyLocationReader {
    fun permitted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun request(context: Context, callback: (SafetyLocation?) -> Unit) {
        if (!permitted(context)) { callback(null); return }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val now = System.currentTimeMillis()
        val known = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { SafetyLocation(it.latitude, it.longitude, it.time).isFresh(now) }
            .maxByOrNull { it.time }
        val delivered = AtomicBoolean(false)
        fun result(location: Location?) {
            if (delivered.compareAndSet(false, true)) {
                val timestamp = System.currentTimeMillis()
                val candidate = location?.let { SafetyLocation(it.latitude, it.longitude, it.time) }
                    ?.takeIf { it.isFresh(timestamp) }
                    ?: known?.let { SafetyLocation(it.latitude, it.longitude, it.time) }
                        ?.takeIf { it.isFresh(timestamp) }
                callback(candidate)
            }
        }
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) { result(known); return }
        runCatching {
            manager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) = result(location)
            }, Looper.getMainLooper())
        }.onFailure { result(known) }
        Handler(Looper.getMainLooper()).postDelayed({ result(known) }, 15_000)
    }
}

private fun locationTime(value: Long) = runCatching { DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value)) }.getOrDefault("agora")

@Composable
private fun CoupleMapCard(name: String, latitude: Double, longitude: Double, capturedAt: Long, onOpen: () -> Unit) {
    ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Localização de $name",style=MaterialTheme.typography.titleMedium)
        Text("Coordenadas: ${"%.5f".format(java.util.Locale.US, latitude)}, ${"%.5f".format(java.util.Locale.US, longitude)}",style=MaterialTheme.typography.bodySmall)
        Text("Atualizada em ${locationTime(capturedAt)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("O mapa externo só recebe a localização quando você escolher abri-lo.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=onOpen,modifier=Modifier.fillMaxWidth()) { Text("Abrir mapa") }
    } }
}

@Composable
internal fun SafetyScreen(vm: CentralViewModel, session: SessionState, data: DataState, busy: Boolean, requestPermission: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(session.uid) { context.getSharedPreferences("safety_${session.uid}", Context.MODE_PRIVATE) }
    // Telefone é configurado voluntariamente neste aparelho; nunca é embutido no código-fonte.
    var phone by rememberSaveable(session.uid) { mutableStateOf(prefs.getString("partnerPhone", "").orEmpty()) }
    var location by remember { mutableStateOf<SafetyLocation?>(null) }
    var locating by remember { mutableStateOf(false) }
    var sharingRequested by remember(session.uid) { mutableStateOf(false) }
    var confirmingSosLive by remember(session.uid) { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.uid) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            clock = System.currentTimeMillis()
        }
    }
    val myLive = data.liveLocations.firstOrNull { it.uid == session.uid && it.isCurrent(clock) }
    val partnerLive = data.liveLocations.firstOrNull { it.uid != session.uid && it.isCurrent(clock) }
    val canShare = !session.local && session.household.isNotBlank() && data.members.size == 2
    fun stopSharing() {
        context.startService(Intent(context, LiveLocationService::class.java).setAction(LiveLocationService.ACTION_STOP))
        sharingRequested = false
    }
    fun startSharing() {
        if (!canShare || !SafetyLocationReader.permitted(context)) {
            vm.message.value = "Conecte as duas contas e permita localização antes de compartilhar."
            return
        }
        val intent = Intent(context, LiveLocationService::class.java)
            .setAction(LiveLocationService.ACTION_START)
            .putExtra(LiveLocationService.EXTRA_UID, session.uid)
            .putExtra(LiveLocationService.EXTRA_HOUSEHOLD, session.household)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onSuccess { sharingRequested = true; vm.message.value = "Iniciando sessão de até 30 minutos. Confirme o aviso persistente do Android." }
            .onFailure { vm.message.value = "O Android não permitiu iniciar o compartilhamento. Abra o app e confira as permissões." }
    }
    val hasPermission = SafetyLocationReader.permitted(context)
    val lastPartner = data.records.filter { it.kind == "safetyCheckIn" && it.text("ownerUid") != session.uid }.maxByOrNull { it.number("capturedAt") }
    fun publish(current: SafetyLocation, success: String = "Localização atualizada para seu parceiro") {
        if (!current.isFresh(System.currentTimeMillis())) {
            vm.message.value = "Posição antiga. Obtenha a localização novamente antes de compartilhar."
            return
        }
        vm.action(success) {
            require(!session.local && data.members.size==2) { "Conecte as duas contas antes de compartilhar uma localização." }
            vm.repo.save(Record("checkin-${session.uid}","safetyCheckIn",fields=mapOf("ownerUid" to session.uid,"ownerName" to session.name,"latitude" to current.latitude,"longitude" to current.longitude,"capturedAt" to current.capturedAt,"mapUrl" to current.mapsUrl())))
        }
    }
    fun requestLocation(then: (SafetyLocation?) -> Unit = {}) {
        locating=true
        SafetyLocationReader.request(context) { found -> locating=false; location=found; if(found==null) vm.message.value="Não foi possível obter uma posição. Ative GPS ou rede e tente novamente."; then(found) }
    }
    fun callPartner() {
        val number=phone.filter { it.isDigit() || it=='+' }
        if(number.length<8) { vm.message.value="Informe e salve o telefone do parceiro para a ligação rápida."; return }
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$number"))) }.onFailure { vm.message.value="Não foi possível abrir o discador neste aparelho." }
    }
    fun messagePartner(current: SafetyLocation?) {
        val number=phone.filter { it.isDigit() }
        if(number.length<8) { vm.message.value="Informe e salve o telefone do parceiro para usar o WhatsApp."; return }
        val international=if(number.startsWith("55")) number else "55$number"
        val body="SOS: preciso de ajuda. ${current?.mapsUrl() ?: "Não foi possível obter o mapa agora."}"
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$international?text=${Uri.encode(body)}"))
        runCatching { context.startActivity(intent) }.onFailure { vm.message.value="Não foi possível abrir o WhatsApp neste aparelho." }
    }
    fun triggerSos(current: SafetyLocation?) {
        vm.action("SOS registrado no espaço do casal", timeoutMs=45_000) {
            require(!session.local && data.members.size==2) { "Conecte as duas contas antes de enviar um SOS." }
            if(current!=null) vm.repo.save(Record("checkin-${session.uid}","safetyCheckIn",fields=mapOf("ownerUid" to session.uid,"ownerName" to session.name,"latitude" to current.latitude,"longitude" to current.longitude,"capturedAt" to current.capturedAt,"mapUrl" to current.mapsUrl())))
            vm.repo.save(Record("sos-${session.uid}-${UUID.randomUUID()}","safetyAlert",fields=mapOf("ownerUid" to session.uid,"ownerName" to session.name,"createdAt" to System.currentTimeMillis(),"mapUrl" to (current?.mapsUrl().orEmpty()),"latitude" to (current?.latitude ?: 0.0),"longitude" to (current?.longitude ?: 0.0))))
        }
    }
    if(confirmingSosLive) AlertDialog(
        onDismissRequest={confirmingSosLive=false},
        title={Text("Compartilhar localização ao vivo?")},
        text={Text("Ao confirmar, a Central registra o SOS e tenta atualizar sua localização para o outro participante do casal por até 30 minutos. Um aviso do Android ficará visível, com botão Parar. Rede, GPS e bateria podem interromper o envio. Não substitui ligação para serviços de emergência.")},
        confirmButton={Button(onClick={
            confirmingSosLive=false
            startSharing()
            triggerSos(location?.takeIf { current -> current.isFresh(System.currentTimeMillis()) })
        }) { Text("Confirmar SOS e compartilhar") }},
        dismissButton={TextButton(onClick={confirmingSosLive=false}) { Text("Cancelar") }}
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("SOS e localização", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
            Text("Acompanhem a localização um do outro com consentimento. Uma sessão explícita atualiza enquanto o Android mantém o serviço ativo, inclusive com a tela fechada. Pode ser interrompida pelo sistema.", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth(), colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("SOS rápido",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onErrorContainer,fontWeight=FontWeight.Bold)
                    Text("Registra o alerta imediatamente, sem esperar pelo GPS. Inclui posição recente apenas se já tiver sido obtida. Não substitui serviços de emergência.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                    Button(enabled=!busy,onClick={ triggerSos(location?.takeIf { current -> current.isFresh(System.currentTimeMillis()) }) },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("Registrar SOS agora") }
                    OutlinedButton(enabled=!busy && canShare,onClick={
                        if(!hasPermission) requestPermission() else confirmingSosLive=true
                    },modifier=Modifier.fillMaxWidth()) { Text(if(hasPermission) "SOS + localização ao vivo por 30 min" else "Permitir localização para SOS ao vivo") }
                    Text("A localização ao vivo só começa após confirmação. O SOS simples não depende de GPS nem inicia rastreamento.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                    Text("Configure ou altere o telefone do parceiro abaixo.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        item {
            ElevatedCard(shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Minha localização", style=MaterialTheme.typography.titleMedium)
                if (!hasPermission) Button(onClick=requestPermission, modifier=Modifier.fillMaxWidth()) { Text("Permitir localização enquanto uso o app") }
                else {
                    Button(enabled=!locating && !busy,onClick={requestLocation { it?.let { publish(it) } }}, modifier=Modifier.fillMaxWidth()) { Text(if(locating) "Obtendo localização…" else "Atualizar e compartilhar agora") }
                    Text("Compartilhamento temporário",style=MaterialTheme.typography.titleMedium)
                    Text("A cada ~15 segundos enquanto o Android permitir. Duração máxima: 30 minutos. Há uma notificação com botão Parar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if (myLive != null) Text("Sessão ativa · última posição ${locationTime(myLive.capturedAt)}",style=MaterialTheme.typography.labelMedium)
                    else if (sharingRequested) Text("Aguardando primeira posição. Confira a notificação do Android.",style=MaterialTheme.typography.labelMedium)
                    if(myLive != null || sharingRequested) Button(onClick=::stopSharing,modifier=Modifier.fillMaxWidth()) { Text("Parar compartilhamento") }
                    else Button(onClick=::startSharing,enabled=canShare,modifier=Modifier.fillMaxWidth()) { Text("Compartilhar por até 30 minutos") }
                    if(!canShare) Text("Este recurso precisa de duas contas no mesmo espaço do casal.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(!AndroidInteractions.notificationsAllowed(context)) Text("Ative as notificações do Android para acompanhar o aviso persistente do serviço.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                }
                location?.let { current -> Text("Posição obtida em ${locationTime(current.capturedAt)}", style=MaterialTheme.typography.bodyMedium) } ?: Text("Ainda não há posição obtida neste aparelho.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                lastPartner?.let { Text("Última localização manual recebida em ${locationTime(it.number("capturedAt"))}. Não representa rastreamento ativo.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
        }
        location?.let { current -> item { CoupleMapCard("sua localização",current.latitude,current.longitude,current.capturedAt) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(current.mapsUrl()))) } } } }
        partnerLive?.let { partner -> item {
            CoupleMapCard("parceiro",partner.latitude,partner.longitude,partner.capturedAt) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://maps.google.com/?q=${partner.latitude},${partner.longitude}"))) }
            }
        } } ?: item { QuietCard("Posição em tempo real indisponível","O parceiro precisa iniciar uma sessão válida no próprio aparelho. Posições antigas ou vindas apenas do cache não aparecem como atuais.") {} }
        item {
            ElevatedCard(shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth(), colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("SOS para seu parceiro", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.onErrorContainer)
                Text("Um SOS grava o alerta no espaço compartilhado e tenta incluir a localização. WhatsApp e ligação só abrem quando você escolher uma dessas ações.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onErrorContainer)
                OutlinedTextField(phone,{ phone=it },label={Text("Telefone do parceiro")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={prefs.edit().putString("partnerPhone",phone.trim()).apply();vm.message.value="Telefone do parceiro salvo neste aparelho."}) { Text("Salvar") }
                    OutlinedButton(enabled=phone.filter(Char::isDigit).length>=8,onClick={messagePartner(location)}) { Text("WhatsApp") }
                    OutlinedButton(enabled=phone.filter(Char::isDigit).length>=8,onClick=::callPartner) { Text("Ligar") }
                }
                Button(enabled=!busy,onClick={ triggerSos(location?.takeIf { current -> System.currentTimeMillis() - current.capturedAt in 0L..90_000L }) },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("Registrar SOS agora") }
                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:190")))}) { Text("Ligar para emergência (190)") }
            } }
        }
    }
}

private fun providerPackage(provider: String) = when(provider) {
    "Alexa" -> "com.amazon.dee.app"
    "Google Home" -> "com.google.android.apps.chromecast.app"
    "Smart Life / Tuya" -> "com.tuya.smartlife"
    else -> "io.homeassistant.companion.android"
}

@Composable
internal fun HomeDevicesScreen(vm: CentralViewModel, data: DataState, busy: Boolean, requestNearby: () -> Unit = {}) {
    val context=LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var paired by remember { mutableStateOf<List<String>>(emptyList()) }
    val nearbyAllowed=Build.VERSION.SDK_INT<31 || ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    fun refreshPaired() {
        paired=if(!nearbyAllowed) emptyList() else runCatching { BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }?.sorted().orEmpty() }.getOrDefault(emptyList())
    }
    val devices=data.records.filter { it.kind=="smartDevice" }.sortedBy { it.text("room")+it.text("title") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("Nossa Casa",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Encontre aparelhos próximos, registre onde ficam e conclua o vínculo no provedor de cada marca.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text("Conectar aparelhos",style=MaterialTheme.typography.titleMedium);Text("Bluetooth mostra apenas aparelhos que este celular já pareou. Para Alexa, conclua o Wi‑Fi e a conta Amazon no app Alexa.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(!nearbyAllowed) Button(onClick=requestNearby,modifier=Modifier.fillMaxWidth()) { Text("Permitir aparelhos próximos") } else { OutlinedButton(onClick=::refreshPaired,modifier=Modifier.fillMaxWidth()) { Text("Ver aparelhos Bluetooth pareados") };if(paired.isEmpty()) Text("Nenhum aparelho pareado encontrado. Use as configurações Bluetooth para parear primeiro.",style=MaterialTheme.typography.bodySmall) else paired.forEach { name -> OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(name,modifier=Modifier.weight(1f));TextButton(onClick={ vm.action("Aparelho Bluetooth registrado") { vm.repo.save(Record("device-${UUID.randomUUID()}","smartDevice",fields=mapOf("title" to name,"room" to "Casa","provider" to "Bluetooth","state" to "paired","bluetoothName" to name))) } },enabled=!busy) { Text("Vincular") } } } }};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}) { Text("Bluetooth") };OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))}) { Text("Wi‑Fi") } };Button(onClick={ val launch=context.packageManager.getLaunchIntentForPackage("com.amazon.dee.app");if(launch==null) vm.message.value="Instale o aplicativo Alexa para concluir o vínculo." else context.startActivity(launch) },modifier=Modifier.fillMaxWidth()) { Text("Abrir Alexa e concluir vínculo") } } } }
            item { Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()) { Text("Adicionar aparelho") } }
            if(devices.isEmpty()) item { Text("Nenhum aparelho cadastrado. Comece pela luz, tomada, Alexa ou central que vocês usam.",style=MaterialTheme.typography.bodyMedium) }
            items(devices,key={it.id}) { device ->
                ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(device.text("title"),style=MaterialTheme.typography.titleMedium);Text("${device.text("room")} · ${device.text("provider")}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Aguardando vínculo no provedor",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick={ val launch=context.packageManager.getLaunchIntentForPackage(providerPackage(device.text("provider"))); if(launch==null) vm.message.value="Instale ou abra ${device.text("provider")} neste aparelho para concluir o vínculo." else context.startActivity(launch) }) { Text("Abrir ${device.text("provider")}") }
                } }
            }
        }
    }
    if(adding) {
        var title by rememberSaveable { mutableStateOf("") }; var room by rememberSaveable { mutableStateOf("Casa") }; var provider by rememberSaveable { mutableStateOf("Home Assistant") }
        AlertDialog(onDismissRequest={adding=false},title={Text("Adicionar aparelho")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(title,{title=it},label={Text("Nome do aparelho")},singleLine=true);OutlinedTextField(room,{room=it},label={Text("Cômodo")},singleLine=true);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Home Assistant","Alexa","Google Home","Smart Life / Tuya").forEach { option -> FilterChip(provider==option,{provider=option},label={Text(option)}) } };Text("O vínculo real é concluído no aplicativo do provedor; nenhuma automação será acionada antes disso.",style=MaterialTheme.typography.bodySmall) }},confirmButton={Button(enabled=title.trim().length>=2&&!busy,onClick={vm.action("Aparelho cadastrado") { vm.repo.save(Record("device-${UUID.randomUUID()}","smartDevice",fields=mapOf("title" to title.trim(),"room" to room.trim().ifBlank { "Casa" },"provider" to provider,"state" to "pending")));adding=false }}) { Text("Cadastrar") }},dismissButton={TextButton(onClick={adding=false}) { Text("Cancelar") }})
    }
}
