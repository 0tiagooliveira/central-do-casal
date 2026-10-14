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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

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

@Composable
internal fun HomeAssistantPreviewCard() {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") } // RAM only; never saveable or written to Firestore
    var entities by remember { mutableStateOf<List<HomeAssistantEntity>>(emptyList()) }
    var status by remember { mutableStateOf("Nenhuma conexão consultada neste aparelho.") }
    var loading by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Home Assistant · consulta real",style=MaterialTheme.typography.titleMedium)
            Text("Leitura experimental dos aparelhos já conectados ao seu Home Assistant. Não liga, desliga nem altera dispositivos.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value=baseUrl,onValueChange={baseUrl=it},
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
                    val credential=token
                    token=""
                    scope.launch {
                        try {
                            entities=HomeAssistantProbe.read(baseUrl,credential)
                            status="Conexão confirmada. ${entities.size} dispositivo(s) exibido(s)."
                        } catch (_:Exception) {
                            status="Consulta indisponível. Confira HTTPS, rede e autorização no Home Assistant."
                        } finally { loading=false }
                    }
                }) { Text(if(loading) "Consultando…" else "Consultar estados reais") }
                if(entities.isNotEmpty()) TextButton(onClick={entities=emptyList();status="Estados descartados da tela."},enabled=!loading) { Text("Limpar") }
            }
            Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            entities.take(20).forEach { entity ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(entity.title,style=MaterialTheme.typography.bodyMedium)
                        Text(entity.id,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(entity.state,style=MaterialTheme.typography.bodyMedium)
                }
            }
            if(entities.size>20)Text("Mais ${entities.size-20} dispositivo(s). Exibição limitada nesta primeira versão.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
