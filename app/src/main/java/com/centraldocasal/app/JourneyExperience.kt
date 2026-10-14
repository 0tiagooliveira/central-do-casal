package com.centraldocasal.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import java.time.LocalDate

private val journeyWorlds = listOf("Ilha das Surpresas", "Bosque das Conversas", "Observatório dos Sonhos")
private val journeyCards = listOf(
    listOf("Escolher uma música para ouvir juntos", "Planejar um microencontro de 15 minutos", "Inventar um passeio que cabe no nosso dia"),
    listOf("Compartilhar uma lembrança boa desta semana", "Conversar sobre algo que queremos aprender", "Contar uma história que ainda não contamos"),
    listOf("Imaginar um lugar que queremos conhecer", "Desenhar uma ideia para nosso futuro", "Escolher um pequeno sonho para explorar")
)

/** Standalone screen: do not place inside another vertically scrolling item. */
@Composable
internal fun JourneyExperience(vm: CentralViewModel, session: SessionState, data: DataState, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember(session.uid) { context.getSharedPreferences("journey_${session.uid}", android.content.Context.MODE_PRIVATE) }
    var level by rememberSaveable(session.uid) { mutableStateOf(prefs.getString("level", "Desligado") ?: "Desligado") }
    var world by rememberSaveable { mutableIntStateOf(0) }
    var card by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val view = LocalView.current
    val options = LocalExperience.current
    val active = level != "Desligado"
    val canInvite = !session.local && data.members.distinct().size == 2
    fun touch() { AndroidInteractions.haptic(view, options.haptics); AndroidInteractions.clickSound(view, options.sounds) }
    fun setLevel(value: String) { touch(); level = value; prefs.edit().putString("level", value).apply() }
    val quests = data.records.filter { it.kind == "quest" && !it.isPrivate && !it.flag("cancelled") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { touch(); onBack() }) { Icon(Icons.Default.ArrowBack, "Voltar ao nosso espaço") }
            Column(Modifier.weight(1f)) { Text("Nossa jornada", style = MaterialTheme.typography.titleLarge); Text("Escolham uma pequena aventura.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Desligado", "Discreto", "Equilibrado", "Divertido").forEach { value -> FilterChip(level == value, { setLevel(value) }, label = { Text(value) }) }
        }
        if (active) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                journeyWorlds.forEachIndexed { index, name ->
                    Card(onClick = { touch(); world = index; card = 0; revealed = false }, modifier = Modifier.weight(1f).testTag("journey-world-$index").semantics { contentDescription = "$name${if (world == index) ", selecionado" else ""}" }, shape = RoundedCornerShape(16.dp), border = BorderStroke(if (world == index) 2.dp else 1.dp, if (world == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        WorldArt(index, Modifier.fillMaxWidth().height(62.dp))
                        Text(listOf("Ilhas", "Bosque", "Observatório")[index], Modifier.padding(horizontal = 7.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            AnimatedContent(world, transitionSpec = { fadeIn(tween(if (options.motion) 260 else 0)) togetherWith fadeOut(tween(if (options.motion) 160 else 0)) }, label = "Mundo da jornada") { selectedWorld ->
                Box(Modifier.fillMaxWidth().height(if (level == "Discreto") 140.dp else 194.dp).clip(RoundedCornerShape(22.dp))) {
                    WorldArt(selectedWorld, Modifier.fillMaxSize())
                    Surface(Modifier.align(Alignment.BottomStart).padding(14.dp), shape = RoundedCornerShape(12.dp), color = Color(0xE6111827), contentColor = Color.White) { Text(journeyWorlds[selectedWorld], Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
                }
            }
            AnimatedContent(targetState = "$world:$card:$revealed", transitionSpec = { fadeIn(tween(if (options.motion) 220 else 0)) togetherWith fadeOut(tween(if (options.motion) 120 else 0)) }, label = "Carta de aventura") { state ->
                val parts = state.split(':')
                val cardWorld = parts[0].toInt()
                val cardIndex = parts[1].toInt()
                val isRevealed = parts[2].toBoolean()
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Text(if (isRevealed) "Uma ideia para vocês" else "Uma carta espera por vocês", style = MaterialTheme.typography.labelLarge) }
                        Text(if (isRevealed) journeyCards[cardWorld][cardIndex % 3] else "Um convite simples para sair do automático.", style = MaterialTheme.typography.titleLarge)
                        if (!isRevealed) Button(onClick = { touch(); revealed = true }, enabled = world == cardWorld && card == cardIndex, modifier = Modifier.fillMaxWidth()) { Text("Revelar a carta") }
                        else {
                            Button(onClick = { touch(); vm.action("Convite proposto") {
                                require(canInvite) { "Conectem duas contas para propor a aventura." }
                                vm.repo.save(Record(kind = "quest", fields = mapOf("title" to journeyCards[cardWorld][cardIndex % 3], "destination" to journeyWorlds[cardWorld], "creatorUid" to session.uid, "accepted" to listOf(session.uid), "completed" to emptyList<String>(), "cancelled" to false, "date" to LocalDate.now().toString())))
                            } }, enabled = !busy && canInvite && world == cardWorld && card == cardIndex, modifier = Modifier.fillMaxWidth()) { Text("Propor esta aventura") }
                            TextButton(onClick = { touch(); card = (card + 1) % 3; revealed = false }, modifier = Modifier.fillMaxWidth()) { Text("Escolher outra carta") }
                            if (!canInvite) Text("Conectem suas contas para compartilhar o convite.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Uma pausa também faz parte", style = MaterialTheme.typography.titleMedium)
                Text("As lembranças continuam guardadas. Entrem quando quiserem explorar uma ideia juntos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { setLevel("Equilibrado") }) { Text("Entrar na jornada") }
            }
        }
        if (quests.isNotEmpty()) Text("As aventuras de vocês", style = MaterialTheme.typography.titleMedium)
        quests.forEach { quest ->
            val accepted = (quest.fields["accepted"] as? List<*>)?.filterIsInstance<String>()?.distinct().orEmpty()
            val completed = (quest.fields["completed"] as? List<*>)?.filterIsInstance<String>()?.distinct().orEmpty()
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(quest.text("title"), style = MaterialTheme.typography.titleMedium)
                    Text(quest.text("destination"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Você" to session.uid, "Parceiro" to data.members.firstOrNull { it != session.uid }).forEach { (name, uid) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(name, style = MaterialTheme.typography.labelLarge); Text(if (uid == null) "Conta não conectada" else if (uid in completed) "Confirmou a experiência" else if (uid in accepted) "Aceitou o convite" else "Ainda não respondeu", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (completed.size == 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary); Text("Lembrança confirmada pelos dois", style = MaterialTheme.typography.labelLarge) }
                    else if (active && canInvite) {
                        if (session.uid !in accepted) Button(onClick = { touch(); vm.action { vm.repo.questResponse(quest, "accept") } }, enabled = !busy) { Text("Topo esta aventura") }
                        if (accepted.size == 2 && session.uid !in completed) Button(onClick = { touch(); vm.action { vm.repo.questResponse(quest, "complete") } }, enabled = !busy) { Text("Confirmar que aconteceu") }
                        TextButton(onClick = { touch(); vm.action { vm.repo.questResponse(quest, if (quest.text("creatorUid") == session.uid) "cancel" else "decline") } }, enabled = !busy) { Text("Talvez outra hora") }
                    }
                }
            }
        }
        TextButton(onClick = { touch(); help = !help }) { Text(if (help) "Fechar explicação" else "Como funciona?") }
        if (help) Text("Vocês escolhem uma ideia, cada um aceita e depois confirma o que aconteceu. Sem ranking ou punição por adiar. Gastos e cuidados não dão pontos. Som, vibração e movimento seguem suas preferências e as configurações do Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorldArt(world: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (world) {
            0 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF063653), Color(0xFF078D97))))
                drawCircle(Color(0xFFFFC45C), h * .19f, Offset(w * .74f, h * .25f))
                drawOval(Color(0xFF25B8B4), Offset(-w * .1f, h * .63f), Size(w * 1.2f, h * .6f))
                drawOval(Color(0xFFEFBC6F), Offset(w * .18f, h * .72f), Size(w * .57f, h * .3f))
                drawLine(Color(0xFF553A2A), Offset(w * .44f, h * .8f), Offset(w * .49f, h * .35f), h * .035f, StrokeCap.Round)
                val palm = Offset(w * .49f, h * .35f)
                listOf(Offset(w * .25f, h * .32f), Offset(w * .37f, h * .14f), Offset(w * .61f, h * .16f), Offset(w * .69f, h * .36f)).forEach { drawLine(Color(0xFF39D18D), palm, it, h * .07f, StrokeCap.Round) }
                drawLine(Color.White.copy(alpha = .55f), Offset(w * .08f, h * .68f), Offset(w * .26f, h * .68f), h * .015f, StrokeCap.Round)
            }
            1 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF112B30), Color(0xFF246549))))
                drawCircle(Color(0xFFFFCE74), h * .14f, Offset(w * .73f, h * .25f))
                for (index in 0..4) {
                    val x = w * (.09f + .2f * index)
                    val top = h * (.14f + .07f * (index % 2))
                    drawLine(Color(0xFF493B32), Offset(x, h), Offset(x, top + h * .24f), h * .04f)
                    drawCircle(if (index % 2 == 0) Color(0xFF2A9B68) else Color(0xFF167A5A), h * .22f, Offset(x, top + h * .15f))
                }
                val trail = Path().apply { moveTo(w * .36f, h); cubicTo(w * .77f, h * .83f, w * .27f, h * .7f, w * .53f, h * .51f); lineTo(w * .62f, h * .51f); cubicTo(w * .36f, h * .75f, w * .92f, h * .81f, w * .68f, h); close() }
                drawPath(trail, Color(0xFFE1BA73))
                listOf(Offset(w * .22f, h * .59f), Offset(w * .77f, h * .51f), Offset(w * .62f, h * .68f)).forEach { drawCircle(Color(0xFFFFE599), h * .012f, it) }
            }
            else -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF14192F), Color(0xFF41316F))))
                val stars = listOf(.1f to .2f, .23f to .42f, .37f to .17f, .57f to .29f, .74f to .15f, .89f to .37f)
                stars.forEachIndexed { index, (x, y) -> drawCircle(Color(0xFFE6E7FF), h * (if (index % 2 == 0) .012f else .008f), Offset(w * x, h * y)) }
                drawCircle(Color(0xFFFFD18D), h * .13f, Offset(w * .79f, h * .24f))
                drawOval(Color(0xFF24284F), Offset(-w * .1f, h * .77f), Size(w * 1.3f, h * .45f))
                drawLine(Color(0xFF9CAAE8), Offset(w * .49f, h * .62f), Offset(w * .38f, h * .95f), h * .02f)
                drawLine(Color(0xFF9CAAE8), Offset(w * .49f, h * .62f), Offset(w * .61f, h * .95f), h * .02f)
                drawLine(Color(0xFFB6C7FF), Offset(w * .38f, h * .58f), Offset(w * .63f, h * .4f), h * .11f, StrokeCap.Round)
                drawCircle(Color(0xFF6AAFFF), h * .055f, Offset(w * .63f, h * .4f))
            }
        }
    }
}
