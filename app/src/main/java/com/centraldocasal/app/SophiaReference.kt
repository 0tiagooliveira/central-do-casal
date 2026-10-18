package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import java.time.LocalDateTime

/** No chat history is sent to a third party or persisted. Switching account resets it. */
internal data class SophiaChatTurn(val fromSophia: Boolean, val message: String, val evidence: String = "")

/**
 * The central cockpit: read-only coordination + explicitly confirmed write tools.
 * There is no LLM or wake word in this version. Never label draft results as success.
 */
@Composable
internal fun SophiaReferenceScreen(
    session: SessionState,
    data: DataState,
    requestLocationPermission: () -> Unit,
    requestNotificationsPermission: () -> Unit,
    onOperation: (SophiaOperation, (Boolean, String) -> Unit) -> Unit,
    navigate: (String) -> Unit
) {
    val history = remember(session.uid) { mutableStateListOf<SophiaChatTurn>() }
    var pending by remember(session.uid) { mutableStateOf<SophiaOperation?>(null) }
    var input by remember(session.uid) { mutableStateOf("") }
    var executing by remember(session.uid) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun ask(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || executing) return
        history.add(SophiaChatTurn(false, prompt))
        val decision = SophiaBrain.decide(prompt, session, data, LocalDateTime.now())
        history.add(SophiaChatTurn(true, decision.message, decision.evidence))
        pending = decision.proposedOperation
        input = ""
        if (history.size > 24) repeat(history.size - 24) { history.removeAt(0) }
    }
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.scrollToItem(
            (1 + history.size).coerceAtMost((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        )
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("sophia-conversation"),
            state = listState,
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ReferenceHeading("Sophia", "Central de decisões da família")
                Text("O que vamos resolver agora?", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("Consulto dados da Central e preparo ações para sua confirmação. " +
                    "Nesta fase, os comandos funcionam localmente, sem IA generativa ou voz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (history.isEmpty()) item {
                ReferenceCard(Modifier.fillMaxWidth()) {
                    Text("Experimente perguntar", style = MaterialTheme.typography.titleMedium)
                    listOf("Como está meu dia?", "O que falta nas compras?", "Como está nossa casa?")
                        .forEach { sample ->
                            TextButton(onClick = { ask(sample) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(sample)
                            }
                        }
                    Text("Para mudar algo: «Crie uma tarefa estudar amanhã às 19:00».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(history.size) { index ->
                val turn = history[index]
                Surface(
                    color = if (turn.fromSophia) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (turn.fromSophia) "Sophia" else "Você",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium)
                        Text(turn.message, style = MaterialTheme.typography.bodyMedium)
                        if (turn.evidence.isNotBlank()) Text(turn.evidence,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            pending?.let { proposal ->
                item {
                    ReferenceCard(Modifier.fillMaxWidth().testTag("sophia-approval")) {
                        Text("Confirma esta alteração?", style = MaterialTheme.typography.titleSmall)
                        Text("Nada será gravado até você confirmar.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    pending = null
                                    executing = true
                                    onOperation(proposal) { _, message ->
                                        history.add(SophiaChatTurn(true, message,
                                            "Resultado retornado pelo aplicativo"))
                                        executing = false
                                    }
                                },
                                enabled = !executing,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("sophia-confirm")
                            ) { Text("Confirmar") }
                            OutlinedButton(
                                onClick = {
                                    pending = null
                                    history.add(SophiaChatTurn(true, "Pedido cancelado. Nada foi alterado."))
                                },
                                enabled = !executing,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("Cancelar") }
                        }
                    }
                }
            }
            if (executing) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Aguardando confirmação do aplicativo...",
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                WeatherCard(requestLocationPermission, session.uid, session.name,
                    data.records, requestNotificationsPermission)
            }
            item {
                Text("Abrir outras áreas", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SophiaShortcut("Rotina", Modifier.weight(1f)) { navigate("Rotina") }
                    SophiaShortcut("Compras", Modifier.weight(1f)) { navigate("Compras") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SophiaShortcut("Planos", Modifier.weight(1f)) { navigate("Planos") }
                    SophiaShortcut("Casa", Modifier.weight(1f)) { navigate("Casa") }
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    input, { input = it.take(280) },
                    modifier = Modifier.weight(1f).testTag("sophia-input"),
                    label = { Text("Pedir à Sophia") },
                    placeholder = { Text("Como está meu dia?") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask(input) }),
                    enabled = !executing
                )
                Button(
                    onClick = { ask(input) },
                    enabled = input.isNotBlank() && !executing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("sophia-send")
                ) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun SophiaShortcut(title: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
