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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class SafetyLocation(val latitude: Double, val longitude: Double, val capturedAt: Long) {
    fun mapsUrl() = "https://maps.google.com/?q=$latitude,$longitude"
}

/** A location is optional, but if included in SOS it must be physically plausible and fresh. */
/** Do not present delayed Firestore replay as a new, urgent SOS notification. */
internal fun freshPartnerSosAlert(
    record: Record,
    currentUid: String,
    now: Long = System.currentTimeMillis(),
    maxAgeMillis: Long = 15 * 60_000L
): Boolean {
    if (currentUid.isBlank() || record.kind != "safetyAlert") return false
    val owner = record.text("ownerUid")
    val whenCreated = record.number("createdAt")
    return owner.isNotBlank() && owner != currentUid && whenCreated > 0 &&
        now - whenCreated in 0L..maxAgeMillis
}

internal fun recentSosLocation(location: SafetyLocation?, now: Long = System.currentTimeMillis()): SafetyLocation? =
    location?.takeIf {
        it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
            it.capturedAt > 0L && now - it.capturedAt in 0L..90_000L
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

/** No background tracking: opt-in is valid only while this screen is active, at most one hour. */
internal fun activeForegroundShareSession(
    enabled: Boolean,
    startedElapsed: Long,
    locationAllowed: Boolean,
    paired: Boolean,
    nowElapsed: Long
): Boolean = enabled && startedElapsed > 0L && locationAllowed && paired &&
    nowElapsed - startedElapsed in 0L until (60L * 60_000L)

private fun locationTime(value: Long) = runCatching { DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value)) }.getOrDefault("agora")

@Composable
private fun CoupleMapCard(name: String, latitude: Double, longitude: Double, capturedAt: Long, onOpen: () -> Unit) {
    ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Mapa de $name",style=MaterialTheme.typography.titleMedium)
        Text("Atualizado em ${locationTime(capturedAt)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Por privacidade, o mapa externo só recebe as coordenadas quando você escolhe abri-lo.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    // Nunca restaurar automaticamente compartilhamento de posição ao voltar à tela.
    var tracking by remember(session.uid) { mutableStateOf(false) }
    var trackingStartedElapsed by remember(session.uid) { mutableLongStateOf(0L) }
    val hasPermission = SafetyLocationReader.permitted(context)
    val paired = !session.local && data.members.size == 2
    DisposableEffect(session.uid) {
        // Limpa o consentimento legado persistido, que não tinha validade temporal.
        prefs.edit().remove("tracking").apply()
        onDispose {
            tracking = false
            trackingStartedElapsed = 0L
        }
    }
    val lastPartner = data.records.filter { it.kind == "safetyCheckIn" && it.text("ownerUid") != session.uid }
        .maxByOrNull { it.number("capturedAt") }?.takeIf { point ->
            recentSosLocation(SafetyLocation(
                (point.fields["latitude"] as? Number)?.toDouble() ?: Double.NaN,
                (point.fields["longitude"] as? Number)?.toDouble() ?: Double.NaN,
                point.number("capturedAt")
            )) != null
        }
    fun publish(current: SafetyLocation, success: String = "Localização atualizada para seu parceiro") {
        val fresh = recentSosLocation(current)
        if(fresh==null) {
            vm.message.value="Esta posição está antiga ou inválida. Ative GPS/rede e tente novamente."
            return
        }
        vm.action(success) {
            require(!session.local && data.members.size==2) { "Conecte as duas contas antes de compartilhar uma localização." }
            vm.repo.save(Record("checkin-${session.uid}","safetyCheckIn",fields=mapOf(
                "ownerUid" to session.uid,"ownerName" to session.name,
                "latitude" to fresh.latitude,"longitude" to fresh.longitude,
                "capturedAt" to fresh.capturedAt,"mapUrl" to fresh.mapsUrl()
            )))
        }
    }
    fun requestLocation(then: (SafetyLocation?) -> Unit = {}) {
        locating=true
        SafetyLocationReader.request(context) { found ->
            locating=false
            val fresh=recentSosLocation(found)
            location=fresh
            if(fresh==null) vm.message.value="Não há localização recente. Ative GPS ou rede e tente novamente."
            then(fresh)
        }
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
        val fresh = recentSosLocation(current)
        vm.action("SOS registrado no espaço. Entrega ao outro celular não confirmada.", timeoutMs=45_000) {
            require(!session.local && data.members.size==2) { "Conecte as duas contas antes de registrar um SOS." }
            // The alert is written first; neither permission nor GPS/network location blocks it.
            vm.repo.save(Record("sos-${session.uid}-${UUID.randomUUID()}","safetyAlert",fields=mapOf(
                "ownerUid" to session.uid,"ownerName" to session.name,"createdAt" to System.currentTimeMillis(),
                "mapUrl" to fresh?.mapsUrl().orEmpty(),
                "latitude" to (fresh?.latitude ?: 0.0),"longitude" to (fresh?.longitude ?: 0.0)
            )))
            if(fresh!=null) vm.repo.save(Record("checkin-${session.uid}","safetyCheckIn",fields=mapOf(
                "ownerUid" to session.uid,"ownerName" to session.name,
                "latitude" to fresh.latitude,"longitude" to fresh.longitude,
                "capturedAt" to fresh.capturedAt,"mapUrl" to fresh.mapsUrl()
            )))
        }
    }
    LaunchedEffect(tracking, trackingStartedElapsed, hasPermission, paired, session.uid) {
        while (isActive && tracking) {
            if (!activeForegroundShareSession(
                    tracking, trackingStartedElapsed, hasPermission, paired,
                    android.os.SystemClock.elapsedRealtime()
                )) {
                tracking = false
                trackingStartedElapsed = 0L
                break
            }
            requestLocation { fresh ->
                // O GPS pode responder depois que a tela foi fechada ou o usuário desligou.
                if (activeForegroundShareSession(
                        tracking, trackingStartedElapsed, hasPermission, paired,
                        android.os.SystemClock.elapsedRealtime()
                    ) && !busy
                ) fresh?.let { publish(it, "Localização compartilhada nesta sessão") }
            }
            delay(60_000L)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("SOS e localização", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
            Text("Acompanhem a localização um do outro com consentimento. Esta versão atualiza a localização enquanto a tela de Segurança está aberta, com consentimento explícito. Não monitora em segundo plano.", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth(), colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("SOS rápido",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onErrorContainer,fontWeight=FontWeight.Bold)
                    Text("Registra imediatamente o alerta; só inclui localização recente já obtida. O parceiro não receberá aviso garantido com o app fechado. Para emergências, utilize ligação direta.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                    Button(enabled=!busy,onClick={ triggerSos(location) },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("Registrar SOS sem esperar GPS") }
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
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Switch(
                            checked = tracking,
                            onCheckedChange = { enabled ->
                                tracking = enabled
                                trackingStartedElapsed = if (enabled) android.os.SystemClock.elapsedRealtime() else 0L
                            },
                            enabled = paired
                        )
                        Column {
                            Text("Compartilhar nesta tela",style=MaterialTheme.typography.bodyMedium)
                            Text(
                                if (tracking) "A cada minuto enquanto esta tela estiver aberta. Encerra em até 60 minutos."
                                else if (!paired) "Conecte as duas contas para compartilhar."
                                else "Desativado. Nunca reinicia sozinho e não acompanha em segundo plano.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                Text("O alerta é registrado sem esperar GPS. A entrega ao parceiro não é garantida se o app dele estiver fechado. WhatsApp e ligação são ações explícitas.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onErrorContainer)
                OutlinedTextField(phone,{ phone=it },label={Text("Telefone do parceiro")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={prefs.edit().putString("partnerPhone",phone.trim()).apply();vm.message.value="Telefone do parceiro salvo neste aparelho."}) { Text("Salvar") }
                    OutlinedButton(enabled=phone.filter(Char::isDigit).length>=8,onClick={messagePartner(location)}) { Text("WhatsApp") }
                    OutlinedButton(enabled=phone.filter(Char::isDigit).length>=8,onClick=::callPartner) { Text("Ligar") }
                }
                Button(enabled=!busy,onClick={ triggerSos(location) },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("Registrar SOS sem esperar GPS") }
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

/** Evita registros repetidos por NOME no inventário; nome Bluetooth não comprova identidade única. */
internal fun bluetoothNameRegistered(records: List<Record>, name: String): Boolean {
    val normalized = name.trim()
    return normalized.isNotEmpty() && records.any { device ->
        device.kind == "smartDevice" && device.text("provider") == "Bluetooth" &&
            device.text("bluetoothName", device.text("title")).trim().equals(normalized, ignoreCase = true)
    }
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
    var editingDevice by remember { mutableStateOf<Record?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var paired by remember { mutableStateOf<List<String>>(emptyList()) }
    val nearbyAllowed=Build.VERSION.SDK_INT<31 || ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    fun refreshPaired() {
        paired=if(!nearbyAllowed) emptyList() else runCatching { BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }?.sorted().orEmpty() }.getOrDefault(emptyList())
    }
    val devices = smartHomeDevices(data.records, showArchived)
    val activeCount = smartHomeDevices(data.records, false).size
    var roomFilter by rememberSaveable { mutableStateOf("Todos") }
    val rooms = remember(devices) { listOf("Todos") + devices.map { it.text("room","Casa") }.distinct().sorted() }
    val visibleDevices = smartHomeDevices(data.records, showArchived, roomFilter)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("Nossa Casa",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Encontre aparelhos próximos, registre onde ficam e conclua o vínculo no provedor de cada marca.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Painel da casa",style=MaterialTheme.typography.titleMedium)
                        Text("$activeCount aparelho(s) ativos · ${rooms.size-1} cômodo(s) exibidos",style=MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked=showArchived,onCheckedChange={showArchived=it;roomFilter="Todos"})
                            Text("Mostrar aparelhos arquivados",style=MaterialTheme.typography.bodySmall)
                        }
                        Text("Cadastro não é controle em tempo real. O estado ligado/desligado só aparecerá depois de uma integração autorizada com o provedor.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cômodos",style=MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            rooms.forEach { room -> FilterChip(selected=roomFilter==room,onClick={roomFilter=room},label={Text(room)}) }
                        }
                    }
                }
            }
            item { HomeAssistantPreviewCard() }
            item { ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text("Conectar aparelhos",style=MaterialTheme.typography.titleMedium);Text("Bluetooth mostra aparelhos já pareados neste celular. Nomes iguais não identificam aparelhos diferentes com segurança. Para Alexa, conclua o Wi‑Fi e a conta Amazon no app Alexa.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(!nearbyAllowed) Button(onClick=requestNearby,modifier=Modifier.fillMaxWidth()) { Text("Permitir aparelhos próximos") } else { OutlinedButton(onClick=::refreshPaired,modifier=Modifier.fillMaxWidth()) { Text("Ver aparelhos Bluetooth pareados") };if(paired.isEmpty()) Text("Nenhum aparelho pareado encontrado. Use as configurações Bluetooth para parear primeiro.",style=MaterialTheme.typography.bodySmall) else paired.forEach { name -> OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(name,modifier=Modifier.weight(1f));TextButton(
    onClick={
        vm.action("Aparelho Bluetooth registrado") {
            require(!bluetoothNameRegistered(vm.repo.data.value.records, name)) {
                "Este nome Bluetooth já está cadastrado. Veja a ficha existente."
            }
            vm.repo.save(Record("device-${UUID.randomUUID()}","smartDevice",
                fields=mapOf("title" to name,"room" to "Casa","provider" to "Bluetooth","state" to "paired","bluetoothName" to name)))
        }
    },
    enabled=!busy && !bluetoothNameRegistered(data.records, name)
) { Text(if(bluetoothNameRegistered(data.records, name)) "Já cadastrado" else "Cadastrar") } } } }};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}) { Text("Bluetooth") };OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))}) { Text("Wi‑Fi") } };Button(onClick={ val launch=context.packageManager.getLaunchIntentForPackage("com.amazon.dee.app");if(launch==null) vm.message.value="Instale o aplicativo Alexa para concluir o vínculo." else context.startActivity(launch) },modifier=Modifier.fillMaxWidth()) { Text("Abrir Alexa e concluir vínculo") } } } }
            item { Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()) { Text("Adicionar aparelho") } }
            if(visibleDevices.isEmpty()) item { Text(if(devices.isEmpty()) "Nenhum aparelho cadastrado. Comece pela luz, tomada, Alexa ou central que vocês usam." else "Nenhum aparelho cadastrado neste cômodo.",style=MaterialTheme.typography.bodyMedium) }
            items(visibleDevices,key={it.id}) { device ->
                ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(device.text("title"),style=MaterialTheme.typography.titleMedium)
                    Text("${device.text("deviceType","Aparelho")} · ${device.text("room","Casa")} · ${device.text("provider")}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when {
                        device.flag("archived") -> "Arquivado; não é controlado pela Central."
                        device.text("provider")=="Bluetooth" && device.text("state")=="paired" -> "Pareado ao Bluetooth; controle remoto não confirmado."
                        else -> "Cadastrado; conexão e controle não confirmados."
                    },style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val provider = device.text("provider")
                    if (provider == "Bluetooth") {
                        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}) { Text("Abrir Bluetooth") }
                    } else if (providerPackage(provider) != null) {
                        OutlinedButton(onClick={
                            val packageName = providerPackage(provider)
                            val launch = packageName?.let(context.packageManager::getLaunchIntentForPackage)
                            if (launch == null) vm.message.value="Instale ou abra $provider neste aparelho para concluir o vínculo."
                            else runCatching { context.startActivity(launch) }.onFailure { vm.message.value="Não foi possível abrir $provider neste aparelho." }
                        }) { Text("Abrir $provider") }
                    } else {
                        Text("Provedor não integrado. Verifique o aplicativo oficial do aparelho.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={editingDevice=device},enabled=!busy) { Text("Editar") }
                        TextButton(onClick={
                            val archived = !device.flag("archived")
                            vm.action(if(archived) "Aparelho arquivado" else "Aparelho restaurado") {
                                vm.repo.save(device.copy(fields=device.fields + ("archived" to archived)))
                            }
                        },enabled=!busy) { Text(if(device.flag("archived")) "Restaurar" else "Arquivar") }
                    }
                } }
            }
        }
    }
    editingDevice?.let { device ->
        var editTitle by remember(device.id) { mutableStateOf(device.text("title")) }
        var editRoom by remember(device.id) { mutableStateOf(device.text("room", "Casa")) }
        var editType by remember(device.id) { mutableStateOf(device.text("deviceType", "Outro")) }
        var editProvider by remember(device.id) { mutableStateOf(device.text("provider")) }
        AlertDialog(
            onDismissRequest={editingDevice=null},
            title={Text("Editar aparelho")},
            text={
                Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editTitle,{editTitle=it},label={Text("Nome")},singleLine=true)
                    OutlinedTextField(editRoom,{editRoom=it},label={Text("Cômodo")},singleLine=true)
                    Text("Tipo",style=MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("Luz","Tomada","TV","Assistente","Outro").forEach { kind ->
                            FilterChip(selected=editType==kind,onClick={editType=kind},label={Text(kind)})
                        }
                    }
                    Text("Provedor",style=MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("Home Assistant","Alexa","Google Home","Smart Life / Tuya","Bluetooth").forEach { kind ->
                            FilterChip(selected=editProvider==kind,onClick={editProvider=kind},label={Text(kind)})
                        }
                    }
                    Text("Trocar o provedor não conecta o dispositivo automaticamente. Será necessário confirmar no aplicativo oficial.",style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={
                Button(
                    enabled=!busy && editTitle.trim().length in 2..100 && editRoom.trim().length in 1..60,
                    onClick={
                        val changedProvider = editProvider != device.text("provider")
                        val changes = mapOf(
                            "title" to editTitle.trim(),
                            "room" to editRoom.trim(),
                            "deviceType" to editType,
                            "provider" to editProvider
                        ) + if(changedProvider) mapOf("state" to "pending") else emptyMap()
                        vm.action("Aparelho atualizado") {
                            vm.repo.save(device.copy(fields=device.fields + changes))
                        }
                        editingDevice=null
                    }
                ) { Text("Salvar") }
            },
            dismissButton={TextButton(onClick={editingDevice=null}) { Text("Cancelar") }}
        )
    }
    if(adding) {
        var title by rememberSaveable { mutableStateOf("") }; var room by rememberSaveable { mutableStateOf("Casa") }; var provider by rememberSaveable { mutableStateOf("Home Assistant") }; var deviceType by rememberSaveable { mutableStateOf("Luz") }
        AlertDialog(onDismissRequest={adding=false},title={Text("Adicionar aparelho")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(title,{title=it},label={Text("Nome do aparelho")},singleLine=true);OutlinedTextField(room,{room=it},label={Text("Cômodo")},singleLine=true);Text("Tipo de aparelho",style=MaterialTheme.typography.labelMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Luz","Tomada","TV","Assistente","Outro").forEach{kind->FilterChip(deviceType==kind,{deviceType=kind},label={Text(kind)})}};Text("Provedor",style=MaterialTheme.typography.labelMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Home Assistant","Alexa","Google Home","Smart Life / Tuya").forEach { option -> FilterChip(provider==option,{provider=option},label={Text(option)}) } };Text("O vínculo real é concluído no aplicativo do provedor; nenhuma automação será acionada antes disso.",style=MaterialTheme.typography.bodySmall) }},confirmButton={Button(enabled=title.trim().length>=2&&!busy,onClick={vm.action("Aparelho cadastrado") { vm.repo.save(Record("device-${UUID.randomUUID()}","smartDevice",fields=mapOf("title" to title.trim(),"room" to room.trim().ifBlank { "Casa" },"provider" to provider,"deviceType" to deviceType,"state" to "pending")));adding=false }}) { Text("Cadastrar") }},dismissButton={TextButton(onClick={adding=false}) { Text("Cancelar") }})
    }
}
