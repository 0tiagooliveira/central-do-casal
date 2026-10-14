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
import android.bluetooth.BluetoothAdapter
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class SafetyLocation(val latitude: Double, val longitude: Double, val capturedAt: Long) {
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
        val known = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }.maxByOrNull { it.time }
        val delivered = AtomicBoolean(false)
        fun result(location: Location?) { if (delivered.compareAndSet(false, true)) callback(location?.let { SafetyLocation(it.latitude, it.longitude, it.time) } ?: known?.let { SafetyLocation(it.latitude, it.longitude, it.time) }) }
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
    val mapUrl=remember(latitude,longitude) { "https://www.openstreetmap.org/export/embed.html?bbox=${longitude-0.008},${latitude-0.006},${longitude+0.008},${latitude+0.006}&layer=mapnik&marker=$latitude,$longitude" }
    ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Mapa de $name",style=MaterialTheme.typography.titleMedium)
        Text("Atualizado em ${locationTime(capturedAt)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(factory={ viewContext -> WebView(viewContext).apply { settings.javaScriptEnabled=false; webViewClient=WebViewClient(); loadUrl(mapUrl) } },update={ it.loadUrl(mapUrl) },modifier=Modifier.fillMaxWidth().height(250.dp))
        OutlinedButton(onClick=onOpen,modifier=Modifier.fillMaxWidth()) { Text("Abrir mapa em tela cheia") }
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
    var tracking by rememberSaveable(session.uid) { mutableStateOf(prefs.getBoolean("tracking", false)) }
    val hasPermission = SafetyLocationReader.permitted(context)
    val lastPartner = data.records.filter { it.kind == "safetyCheckIn" && it.text("ownerUid") != session.uid }.maxByOrNull { it.number("capturedAt") }
    fun publish(current: SafetyLocation, success: String = "Localização atualizada para seu parceiro") {
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
    LaunchedEffect(tracking, hasPermission, session.uid) {
        while (tracking && hasPermission && isActive) {
            requestLocation { it?.let { current -> if(!busy) publish(current, "Localização em tempo real atualizada") } }
            delay(60_000)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("SOS e localização", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
            Text("Acompanhem a localização um do outro com consentimento. O modo em tempo real atualiza enquanto o app está aberto.", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth(), colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("SOS rápido",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onErrorContainer,fontWeight=FontWeight.Bold)
                    Text("Registra um alerta no espaço do casal e tenta incluir sua localização. WhatsApp e ligação ficam como ações separadas para você confirmar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                    Button(enabled=!busy && !locating,onClick={ if(!hasPermission) requestPermission() else requestLocation(::triggerSos) },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text(if(locating) "Obtendo posição…" else "Registrar SOS") }
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
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) { Switch(tracking,{ tracking=it;prefs.edit().putBoolean("tracking",it).apply() });Column { Text("Compartilhar em tempo real",style=MaterialTheme.typography.bodyMedium);Text(if(tracking) "Atualiza a cada minuto enquanto este app estiver aberto" else "Desativado",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
                location?.let { current -> Text("Posição obtida em ${locationTime(current.capturedAt)}", style=MaterialTheme.typography.bodyMedium) } ?: Text("Ainda não há posição obtida neste aparelho.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                lastPartner?.let { OutlinedButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(it.text("mapUrl")))) }},modifier=Modifier.fillMaxWidth()) { Text("Ver ${it.text("ownerName", "parceiro")} no mapa · ${locationTime(it.number("capturedAt"))}") } }
            } }
        }
        location?.let { current -> item { CoupleMapCard("sua localização",current.latitude,current.longitude,current.capturedAt) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(current.mapsUrl()))) } } } }
        lastPartner?.let { partner -> val latitude=(partner.fields["latitude"] as? Number)?.toDouble(); val longitude=(partner.fields["longitude"] as? Number)?.toDouble(); if(latitude!=null&&longitude!=null) item { CoupleMapCard(partner.text("ownerName","seu parceiro"),latitude,longitude,partner.number("capturedAt")) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(partner.text("mapUrl")))) } } } } ?: item { QuietCard("Mapa do parceiro","Quando seu parceiro tocar em “Atualizar e compartilhar agora” ou ativar o modo em tempo real, a localização aparecerá aqui no mapa.") {} }
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
                Button(enabled=!busy && !locating,onClick={ if(!hasPermission) requestPermission() else requestLocation(::triggerSos) },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text(if(locating) "Obtendo posição…" else "Registrar SOS") }
                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:190")))}) { Text("Ligar para emergência (190)") }
            } }
        }
    }
}

private fun providerPackage(provider: String): String? = when(provider) {
    "Alexa" -> "com.amazon.dee.app"
    "Google Home" -> "com.google.android.apps.chromecast.app"
    "Smart Life / Tuya" -> "com.tuya.smartlife"
    "Home Assistant" -> "io.homeassistant.companion.android"
    else -> null
}

/** Cadastro real, sem simular telemetria nem comando de automação. */
internal fun smartHomeDevices(records: List<Record>, includeArchived: Boolean, room: String = "Todos"): List<Record> =
    records.filter { it.kind == "smartDevice" }
        .filter { includeArchived || !it.flag("archived") }
        .filter { room == "Todos" || it.text("room", "Casa") == room }
        .sortedWith(compareBy({ it.text("room", "Casa") }, { it.text("title") }))

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
    var roomFilter by rememberSaveable { mutableStateOf("Todos") }
    val rooms = remember(devices) { listOf("Todos") + devices.map { it.text("room","Casa") }.distinct().sorted() }
    val visibleDevices = devices.filter { roomFilter == "Todos" || it.text("room","Casa") == roomFilter }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("Nossa Casa",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Encontre aparelhos próximos, registre onde ficam e conclua o vínculo no provedor de cada marca.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Painel da casa",style=MaterialTheme.typography.titleMedium)
                        Text("${devices.size} aparelho(s) cadastrados · ${rooms.size-1} cômodo(s)",style=MaterialTheme.typography.bodyMedium)
                        Text("Cadastro não é controle em tempo real. O estado ligado/desligado só aparecerá depois de uma integração autorizada com o provedor.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cômodos",style=MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            rooms.forEach { room -> FilterChip(selected=roomFilter==room,onClick={roomFilter=room},label={Text(room)}) }
                        }
                    }
                }
            }
            item { ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text("Conectar aparelhos",style=MaterialTheme.typography.titleMedium);Text("Bluetooth mostra apenas aparelhos que este celular já pareou. Para Alexa, conclua o Wi‑Fi e a conta Amazon no app Alexa.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(!nearbyAllowed) Button(onClick=requestNearby,modifier=Modifier.fillMaxWidth()) { Text("Permitir aparelhos próximos") } else { OutlinedButton(onClick=::refreshPaired,modifier=Modifier.fillMaxWidth()) { Text("Ver aparelhos Bluetooth pareados") };if(paired.isEmpty()) Text("Nenhum aparelho pareado encontrado. Use as configurações Bluetooth para parear primeiro.",style=MaterialTheme.typography.bodySmall) else paired.forEach { name -> OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(name,modifier=Modifier.weight(1f));TextButton(onClick={ vm.action("Aparelho Bluetooth registrado") { vm.repo.save(Record("device-${UUID.randomUUID()}","smartDevice",fields=mapOf("title" to name,"room" to "Casa","provider" to "Bluetooth","state" to "paired","bluetoothName" to name))) } },enabled=!busy) { Text("Vincular") } } } }};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}) { Text("Bluetooth") };OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))}) { Text("Wi‑Fi") } };Button(onClick={ val launch=context.packageManager.getLaunchIntentForPackage("com.amazon.dee.app");if(launch==null) vm.message.value="Instale o aplicativo Alexa para concluir o vínculo." else context.startActivity(launch) },modifier=Modifier.fillMaxWidth()) { Text("Abrir Alexa e concluir vínculo") } } } }
            item { Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()) { Text("Adicionar aparelho") } }
            if(visibleDevices.isEmpty()) item { Text(if(devices.isEmpty()) "Nenhum aparelho cadastrado. Comece pela luz, tomada, Alexa ou central que vocês usam." else "Nenhum aparelho cadastrado neste cômodo.",style=MaterialTheme.typography.bodyMedium) }
            items(visibleDevices,key={it.id}) { device ->
                ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(device.text("title"),style=MaterialTheme.typography.titleMedium)
                    Text("${device.text("deviceType","Aparelho")} · ${device.text("room","Casa")} · ${device.text("provider")}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(device.text("provider")=="Bluetooth" && device.text("state")=="paired") "Pareado ao Bluetooth; controle remoto não confirmado." else "Cadastrado; conexão e controle não confirmados.",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick={ val launch=context.packageManager.getLaunchIntentForPackage(providerPackage(device.text("provider"))); if(launch==null) vm.message.value="Instale ou abra ${device.text("provider")} neste aparelho para concluir o vínculo." else context.startActivity(launch) }) { Text("Abrir ${device.text("provider")}") }
                } }
            }
        }
    }
    if(adding) {
        var title by rememberSaveable { mutableStateOf("") }; var room by rememberSaveable { mutableStateOf("Casa") }; var provider by rememberSaveable { mutableStateOf("Home Assistant") }; var deviceType by rememberSaveable { mutableStateOf("Luz") }
        AlertDialog(onDismissRequest={adding=false},title={Text("Adicionar aparelho")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(title,{title=it},label={Text("Nome do aparelho")},singleLine=true);OutlinedTextField(room,{room=it},label={Text("Cômodo")},singleLine=true);Text("Tipo de aparelho",style=MaterialTheme.typography.labelMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Luz","Tomada","TV","Assistente","Outro").forEach{kind->FilterChip(deviceType==kind,{deviceType=kind},label={Text(kind)})}};Text("Provedor",style=MaterialTheme.typography.labelMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Home Assistant","Alexa","Google Home","Smart Life / Tuya").forEach { option -> FilterChip(provider==option,{provider=option},label={Text(option)}) } };Text("O vínculo real é concluído no aplicativo do provedor; nenhuma automação será acionada antes disso.",style=MaterialTheme.typography.bodySmall) }},confirmButton={Button(enabled=title.trim().length>=2&&!busy,onClick={vm.action("Aparelho cadastrado") { vm.repo.save(Record("device-${UUID.randomUUID()}","smartDevice",fields=mapOf("title" to title.trim(),"room" to room.trim().ifBlank { "Casa" },"provider" to provider,"deviceType" to deviceType,"state" to "pending")));adding=false }}) { Text("Cadastrar") }},dismissButton={TextButton(onClick={adding=false}) { Text("Cancelar") }})
    }
}
