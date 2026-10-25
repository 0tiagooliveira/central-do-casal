package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.SessionState

@Composable
internal fun SettingsReferenceLanding(
    session:SessionState,photoUri:String,onPhoto:()->Unit,
    onSection:(String)->Unit,onRoute:(String)->Unit
) {
    val scheme=MaterialTheme.colorScheme
    val firstName=session.name.substringBefore(' ').ifBlank{"Você"}
    val themeLabel=if(session.name.contains("Rhayra",true)) "Rosa Barbie (Rhayra)" else "Azul (Tiago)"
    Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
        ReferenceHeading("Configurações","Personalize sua experiência na Central do Casal")
        Surface(shape=RoundedCornerShape(22.dp),color=scheme.primaryContainer.copy(alpha=.58f),
            onClick={onSection("Conta e casal")}) {
            Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                PersonalPortrait(photoUri,session.name,size=62.dp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("PERFIL DO CASAL",style=MaterialTheme.typography.labelSmall,
                        color=scheme.primary,fontWeight=FontWeight.Bold)
                    Text(firstName,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(if(session.local)"Espaço local neste aparelho"
                        else "Sua conta e seu espaço compartilhado",
                        style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant)
                }
                Icon(Icons.Default.KeyboardArrowRight,null,tint=scheme.primary)
            }
        }
        TextButton(onClick=onPhoto) { Text("Escolher foto do meu perfil") }
        Surface(onClick={onSection("Aparência")},shape=RoundedCornerShape(20.dp),
            color=scheme.primaryContainer.copy(alpha=.40f)) {
            Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("✦ Nossa experiência",style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold,color=scheme.primary)
                Text("Seu tema: $themeLabel. Ajuste sons, vibrações e animações.",
                    style=MaterialTheme.typography.bodySmall)
                Text("Personalizar ›",style=MaterialTheme.typography.labelMedium,
                    color=scheme.primary)
            }
        }
        listOf(
            Triple("Conta e privacidade","Seus dados, acesso e proteção","Privacidade"),
            Triple("Notificações","Configure quais avisos receber","Lembretes"),
            Triple("Vibração e sons","Ajuste o retorno do aplicativo","Aparência"),
            Triple("Tema do app",themeLabel,"Aparência"),
            Triple("Segurança e SOS","Contatos e recursos de segurança","route:Segurança"),
            Triple("Casa inteligente","Aparelhos, cômodos e conexões autorizadas","route:Casa"),
            Triple("Permissões","Localização, notificações e privacidade","Privacidade"),
            Triple("Integração com a Sophia","Dicas locais · IA generativa em preparação","route:Sophia"),
            Triple("Dados e backup","Exportação e armazenamento de dados","Privacidade"),
            Triple("Avançado","Diagnósticos e integrações","Avançado")
        ).forEach { (label,subtitle,destination) ->
            Surface(onClick={
                if(destination.startsWith("route:"))onRoute(destination.substringAfter(':'))
                else onSection(destination)
            },shape=RoundedCornerShape(17.dp),color=scheme.surface,
                border=BorderStroke(1.dp,scheme.outlineVariant.copy(alpha=.7f))) {
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=14.dp,vertical=11.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                    Surface(shape=RoundedCornerShape(12.dp),color=scheme.primaryContainer,
                        modifier=Modifier.size(40.dp)) {
                        Box(contentAlignment=Alignment.Center) {
                            val symbol=when(label) {
                                "Casa inteligente" -> HomeLine
                                "Notificações" -> ReminderBell
                                "Segurança e SOS" -> HeartLine
                                "Integração com a Sophia" -> Icons.Default.Star
                                "Conta e privacidade","Permissões" -> Icons.Default.Person
                                "Dados e backup" -> Icons.Default.Info
                                else -> Icons.Default.Settings
                            }
                            Icon(symbol,null,tint=scheme.primary,modifier=Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        Text(subtitle,style=MaterialTheme.typography.bodySmall,
                            color=scheme.onSurfaceVariant,maxLines=2)
                    }
                    Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp),
                        tint=scheme.primary)
                }
            }
        }
    }
}
