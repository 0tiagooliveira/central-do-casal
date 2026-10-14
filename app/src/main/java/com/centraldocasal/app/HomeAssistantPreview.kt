package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class HomeAssistantEntity(
    val id: String,
    val title: String,
    val state: String,
    val domain: String
)

/**
 * Only one opted-in, read-only Home Assistant request; no device command, no persistence,
 * no Firebase token, and never follow an HTTP redirect with a bearer credential.
 */
internal object HomeAssistantProbe {
    private val allowedDomains = setOf("light", "switch", "fan", "climate", "media_player")

    fun endpoint(raw: String): URL {
        val uri = URI(raw.trim())
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Use o endereço HTTPS do seu Home Assistant."
        }
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
            "Informe somente o endereço base HTTPS, sem usuário, caminho ou parâmetros."
        }
        require(uri.port in -1..65535 && uri.port != 0) { "Porta inválida." }
        return URI("https", null, uri.host, uri.port, "/api/states", null, null).toURL()
    }

    /** Narrow allowlist. Never expose locks, garage doors, cameras or scenes as generic switches. */
    fun commandEndpoint(raw: String, entityId: String, turnOn: Boolean): URL {
        require(Regex("^(light|switch)\\.[a-z0-9_]+$").matches(entityId)) {
            "Só luzes e tomadas identificadas podem ser controladas."
        }
        val validated = endpoint(raw).toURI()
        val service = if (turnOn) "turn_on" else "turn_off"
        val path = "/api/services/" + entityId.substringBefore('.') + "/" + service
        return URI("https", null, validated.host, validated.port, path, null, null).toURL()
    }

    /** Requires an explicit, one-off human command and keeps the credential only in RAM. */
    suspend fun command(base: String, bearer: String, entityId: String, turnOn: Boolean) = withContext(Dispatchers.IO) {
        require(bearer.length in 20..4096 && bearer.none { it == '\r' || it == '\n' }) {
            "Credencial inválida."
        }
        val conn = commandEndpoint(base, entityId, turnOn).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer " + bearer)
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(JSONObject().put("entity_id",entityId).toString())
            }
            val statusCode = conn.responseCode
            require(statusCode == 200 || statusCode == 201) {
                if (statusCode == 401 || statusCode == 403) "Acesso recusado pelo Home Assistant."
                else "O Home Assistant não confirmou o comando (HTTP " + statusCode + ")."
            }
        } finally {
            conn.disconnect()
        }
    }

    fun parseStates(json: String): List<HomeAssistantEntity> {
        val states = JSONArray(json)
        val result = ArrayList<HomeAssistantEntity>()
        for (i in 0 until states.length()) {
            val item = states.optJSONObject(i) ?: continue
            val id = item.optString("entity_id")
            val domain = id.substringBefore('.', "")
            if (domain !in allowedDomains || id.length !in 5..180) continue
            val friendly = item.optJSONObject("attributes")?.optString("friendly_name").orEmpty()
            val title = friendly.takeIf { it.isNotBlank() }?.take(90) ?: id.take(90)
            result.add(HomeAssistantEntity(id, title, item.optString("state","unknown").take(40), domain))
            if (result.size == 80) break
        }
        return result
    }

    suspend fun read(base: String, bearer: String): List<HomeAssistantEntity> = withContext(Dispatchers.IO) {
        require(bearer.length in 20..4096 && bearer.none { it == '\r' || it == '\n' }) {
            "Token inválido. Use uma credencial da sua instância."
        }
        val conn = endpoint(base).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $bearer")
            val code = conn.responseCode
            require(code == 200) {
                if (code == 401 || code == 403) "Acesso negado pelo Home Assistant." else "Home Assistant respondeu HTTP $code."
            }
            val reader = conn.inputStream.bufferedReader()
            val body = buildString {
                reader.use { input ->
                    val chunk = CharArray(4096)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        require(length + count <= 3_000_000) { "Instância grande demais para esta visualização." }
                        append(chunk, 0, count)
                    }
                }
            }
            parseStates(body)
        } finally {
            conn.disconnect()
        }
    }
}

/** A read is a snapshot, not a live subscription. Never command from stale state. */
internal fun homeAssistantObservationFresh(observedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
    observedAt > 0 && now >= observedAt && now - observedAt <= 2 * 60_000L

@Composable
internal fun HomeAssistantPreviewCard() {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") } // RAM only; never saveable or written to Firestore
    var entities by remember { mutableStateOf<List<HomeAssistantEntity>>(emptyList()) }
    var observedAt by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("Nenhuma conexão consultada neste aparelho.") }
    var loading by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<Pair<HomeAssistantEntity,Boolean>?>(null) }
    var commandCredential by remember { mutableStateOf("") }

    ElevatedCard(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Home Assistant · consulta real",style=MaterialTheme.typography.titleMedium)
            Text("Consulta real de estados e controle manual experimental de luzes/tomadas, sempre com confirmação. Outros dispositivos permanecem somente leitura.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value=baseUrl,onValueChange={baseUrl=it;entities=emptyList();observedAt=0L;status="Endereço alterado. Consulte novamente."},
                label={Text("Endereço HTTPS da sua instância")},
                placeholder={Text("https://casa.exemplo.com")},
                singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!loading
            )
            OutlinedTextField(
                value=token,onValueChange={token=it},
                label={Text("Token de acesso (não é salvo)")},
                singleLine=true,visualTransformation=PasswordVisualTransformation(),
                modifier=Modifier.fillMaxWidth(),enabled=!loading
            )
            Text("O token é usado somente nesta consulta e descartado ao concluir. Não compartilhe credenciais no chat ou GitHub. Use apenas sua própria instância HTTPS.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled=!loading && baseUrl.isNotBlank() && token.isNotBlank(),onClick={
                    loading=true
                    entities=emptyList()
                    observedAt=0L
                    val credential=token
                    token=""
                    scope.launch {
                        try {
                            entities=HomeAssistantProbe.read(baseUrl,credential)
                            observedAt=System.currentTimeMillis()
                            status="Conexão confirmada. ${entities.size} dispositivo(s) exibido(s)."
                        } catch (_:Exception) {
                            status="Consulta indisponível. Confira HTTPS, rede e autorização no Home Assistant."
                        } finally { loading=false }
                    }
                }) { Text(if(loading) "Consultando…" else "Consultar estados reais") }
                if(entities.isNotEmpty()) TextButton(onClick={entities=emptyList();observedAt=0L;status="Estados descartados da tela."},enabled=!loading) { Text("Limpar") }
            }
            Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if (observedAt > 0L) {
                Text(
                    "Consulta pontual às " + DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(observedAt)) +
                        ". Estados podem mudar; atualize antes de controlar.",
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entities.take(20).forEach { entity ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(entity.title,style=MaterialTheme.typography.bodyMedium)
                        Text(entity.id,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text(entity.state,style=MaterialTheme.typography.bodyMedium)
                        if(entity.domain in listOf("light","switch") &&
                            entity.state in listOf("on","off")) {
                            TextButton(
                                onClick={
                                    if(homeAssistantObservationFresh(observedAt)) {
                                        pendingCommand=entity to (entity.state!="on")
                                    } else {
                                        entities=emptyList()
                                        observedAt=0L
                                        status="A consulta ficou antiga. Consulte novamente antes de controlar."
                                    }
                                },
                                enabled=!loading
                            ) { Text(if(entity.state=="on") "Desligar" else "Ligar") }
                        }
                    }
                }
            }
            if(entities.size>20)Text("Mais ${entities.size-20} dispositivo(s). Exibição limitada nesta primeira versão.",style=MaterialTheme.typography.bodySmall)
        }
    }
    pendingCommand?.let { (device, turnOn) ->
        AlertDialog(
            onDismissRequest={
                if(!loading) { pendingCommand=null; commandCredential="" }
            },
            title={Text(if(turnOn) "Confirmar: ligar" else "Confirmar: desligar")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(device.title,style=MaterialTheme.typography.titleMedium)
                    Text("Entidade: " + device.id,style=MaterialTheme.typography.bodySmall)
                    Text("O comando será enviado somente após confirmar. Não controla alarmes, fechaduras nem outros dispositivos sensíveis.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        commandCredential,{commandCredential=it},
                        label={Text("Token Home Assistant para este comando")},
                        visualTransformation=PasswordVisualTransformation(),
                        singleLine=true,modifier=Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton={
                Button(
                    enabled=!loading && commandCredential.length in 20..4096,
                    onClick={
                        val credential=commandCredential
                        val base=baseUrl
                        commandCredential=""
                        pendingCommand=null
                        if (!homeAssistantObservationFresh(observedAt)) {
                            entities=emptyList()
                            observedAt=0L
                            status="Consulta antiga. Faça uma nova leitura antes de comandar."
                        } else {
                        loading=true
                        scope.launch {
                            try {
                                HomeAssistantProbe.command(base,credential,device.id,turnOn)
                                val refreshed=HomeAssistantProbe.read(base,credential)
                                entities=refreshed
                                observedAt=System.currentTimeMillis()
                                val state=refreshed.firstOrNull { it.id==device.id }?.state
                                status=if(state==(if(turnOn) "on" else "off")) {
                                    "Estado confirmado na consulta: " + device.title + " " + (if(turnOn) "ligado." else "desligado.")
                                } else {
                                    "Comando aceito pelo Home Assistant; estado ainda não confirmado. Consulte novamente."
                                }
                            } catch (_:Exception) {
                                entities=emptyList()
                                observedAt=0L
                                status="Não foi possível confirmar o comando. Consulte os estados novamente; a ação pode ter sido aplicada."
                            } finally { loading=false }
                        }
                        }
                    }
                ) { Text("Confirmar comando") }
            },
            dismissButton={
                TextButton(onClick={pendingCommand=null;commandCredential=""},enabled=!loading) { Text("Cancelar") }
            }
        )
    }
}
