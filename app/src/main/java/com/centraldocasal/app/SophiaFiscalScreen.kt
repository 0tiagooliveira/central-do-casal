package com.centraldocasal.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Money
import com.centraldocasal.app.domain.Record
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private fun fiscalMoney(cents:Long):String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(cents,2))
private fun fiscalQty(milli:Long):String =
    BigDecimal.valueOf(milli,3).stripTrailingZeros().toPlainString()
private data class EditableFiscalLine(val name:String,val quantity:String,val unit:String,
    val total:String,val linkedId:String?=null,val reviewed:Boolean=false)
private fun validFiscalLine(item:EditableFiscalLine):FiscalLine?=runCatching {
    val milli=Money.quantity(item.quantity)
    require(item.unit in setOf("un","kg","L","pacote"))
    val cents=FiscalVision.cents(item.total)?:return null
    val each=BigDecimal(cents).multiply(BigDecimal(1000)).divide(
        BigDecimal(milli),0,RoundingMode.HALF_UP).longValueExact()
    FiscalLine(item.name.trim(),milli,item.unit,cents,each,item.linkedId)
}.getOrNull()

/** Photo stays on-device; a reviewed, structured purchase is the only saved data. */
@Composable
internal fun SophiaFiscalScreen(
    session:SessionState,records:List<Record>,onBack:()->Unit,
    onSave:(FiscalDraft,(Boolean,String)->Unit)->Unit
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val currentUid by rememberUpdatedState(session.uid)
    var busy by remember{mutableStateOf(false)}
    var reading by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var merchant by remember{mutableStateOf("")}
    var cnpj by remember{mutableStateOf("")}
    var date by remember{mutableStateOf("")}
    var declared by remember{mutableStateOf<Long?>(null)}
    var diffAccepted by remember{mutableStateOf(false)}
    var dupAccepted by remember{mutableStateOf(false)}
    val rows=remember{mutableStateListOf<EditableFiscalLine>()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri:Uri? ->
        if(uri!=null&&!reading&&!busy) {
            val requestUid=currentUid
            reading=true;error="";message=""
            scope.launch {
                try {
                    val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val text=try {
                        recognizer.process(InputImage.fromFilePath(context,uri)).await().text
                    } finally { recognizer.close() }
                    if(currentUid!=requestUid) {
                        error="A conta mudou. Selecione a foto novamente."
                    } else {
                        val draft=FiscalVision.parse(text)
                        merchant=draft.merchantName
                        cnpj=draft.cnpj
                        date=draft.date
                        declared=draft.declaredTotalCents
                        rows.clear()
                        rows.addAll(draft.lines.map { line ->
                            EditableFiscalLine(line.title,fiscalQty(line.quantityMilli),
                                line.unit,BigDecimal.valueOf(line.totalCents,2).toPlainString().replace('.',','),
                                FiscalVision.exactSuggestion(line,records))
                        })
                        diffAccepted=false;dupAccepted=false
                        message=if(rows.isEmpty())
                            "Foto lida, mas nenhuma linha segura foi reconhecida. Adicione os itens manualmente."
                        else "Encontrei "+rows.size+" item(ns). Confira todos antes de salvar."
                    }
                } catch (_:Exception) {
                    error="Não consegui reconhecer esta foto. Tente uma imagem mais nítida."
                } finally { reading=false }
            }
        }
    }
    val converted=rows.map(::validFiscalLine)
    val sum=converted.filterNotNull().sumOf{it.totalCents}
    val mismatch=declared!=null&&kotlin.math.abs(sum-declared!!) > 1
    val merchantId=runCatching{FiscalVision.merchantId(merchant,cnpj)}.getOrNull()
    val known=records.any{it.kind=="photoMerchant"&&it.id==merchantId}
    val similar=merchantId!=null&&records.any {
        it.kind=="photoReceipt"&&it.text("merchantId")==merchantId &&
            it.text("date")==date&&it.number("totalCents")==sum
    }
    val canSave=merchant.trim().length in 3..100 &&
        (cnpj.isBlank()||cnpj.filter(Char::isDigit).length==14) &&
        runCatching{LocalDate.parse(date)}.isSuccess &&
        rows.isNotEmpty()&&rows.size<=120 &&
        rows.all{it.reviewed&&it.name.trim().length in 3..110} &&
        converted.all{it!=null}&&(!mismatch||diffAccepted)&&(!similar||dupAccepted)
    LazyColumn(Modifier.fillMaxSize().testTag("sophia-fiscal"),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick=onBack,enabled=!busy){Text("Voltar à Sophia")}
            Text("Notas fiscais com a Sophia",style=MaterialTheme.typography.titleLarge)
            Text("A foto é lida no aparelho. Confirme loja, produtos, unidades e preços.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={picker.launch("image/*")},enabled=!reading&&!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("fiscal-photo")) {
                Text(if(reading)"Lendo foto..." else "Selecionar foto da nota")
            }
            if(reading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            if(message.isNotBlank()) Text(message)
        }
        item {
            Text("Estabelecimento",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(merchant,{merchant=it.take(100)},Modifier.fillMaxWidth(),
                label={Text("Nome da loja")},maxLines=2)
            OutlinedTextField(cnpj,{cnpj=it.filter(Char::isDigit).take(14)},
                Modifier.fillMaxWidth(),label={Text("CNPJ opcional")},singleLine=true)
            OutlinedTextField(date,{date=it.take(10)},Modifier.fillMaxWidth(),
                label={Text("Data AAAA-MM-DD")},singleLine=true)
            if(merchantId!=null) Text(if(known)"Loja existente: compras agrupadas."
                else "Loja nova: cadastro criado ao salvar.",
                style=MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("Itens ("+rows.size+")",style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={rows.add(EditableFiscalLine("","1","un","0,00"))},
                    enabled=!busy&&rows.size<120){Text("Adicionar")}
            }
        }
        itemsIndexed(rows,key={index,_->index}) { index, row ->
            val line=validFiscalLine(row)
            val matches=line?.let{FiscalVision.match(it,records)}.orEmpty()
            val price=line?.let{FiscalVision.compare(it,row.linkedId,records)}
            OutlinedCard(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text("Item "+(index+1),style=MaterialTheme.typography.titleSmall)
                        TextButton(onClick={rows.removeAt(index)},enabled=!busy){Text("Excluir")}
                    }
                    OutlinedTextField(row.name,
                        {rows[index]=row.copy(name=it.take(110),linkedId=null,reviewed=false)},
                        Modifier.fillMaxWidth(),label={Text("Produto")},maxLines=2)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        OutlinedTextField(row.quantity,
                            {rows[index]=row.copy(quantity=it.take(12),reviewed=false)},
                            Modifier.weight(1f),label={Text("Qtd.")},singleLine=true)
                        var expanded by remember{mutableStateOf(false)}
                        Box {
                            TextButton(onClick={expanded=true}){Text(row.unit+" ▾")}
                            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                                listOf("un","kg","L","pacote").forEach { unit ->
                                    DropdownMenuItem(text={Text(unit)},onClick={
                                        rows[index]=row.copy(unit=unit,linkedId=null,reviewed=false)
                                        expanded=false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(row.total,
                            {rows[index]=row.copy(total=it.take(15),reviewed=false)},
                            Modifier.weight(1f),label={Text("Total R$")},singleLine=true)
                    }
                    if(line==null) Text("Confira quantidade e preço.",color=MaterialTheme.colorScheme.error)
                    else Text("Preço por "+row.unit+": "+fiscalMoney(line.unitPriceCents))
                    Text(if(row.linkedId==null)"Produto novo. Confirme se já existe abaixo."
                        else "Vinculado a um produto da base.",
                        style=MaterialTheme.typography.bodySmall)
                    matches.forEach { match ->
                        TextButton(onClick={
                            rows[index]=row.copy(linkedId=match.productId,reviewed=false)
                        }) {Text((if(row.linkedId==match.productId)"✓ "else"")+"Vincular: "+match.name.take(55))}
                    }
                    if(row.linkedId!=null) TextButton(onClick={
                        rows[index]=row.copy(linkedId=null,reviewed=false)
                    }){Text("Criar produto separado")}
                    price?.let {
                        Text("Última compra: "+fiscalMoney(it.previous.unitPriceCents)+" em "+
                            it.previous.store+" ("+it.previous.date+")",
                            style=MaterialTheme.typography.bodySmall)
                        Text("Diferença atual: "+fiscalMoney(it.differenceCents)+
                            " | Menor histórico: "+fiscalMoney(it.cheapest.unitPriceCents)+
                            " em "+it.cheapest.store,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Checkbox(checked=row.reviewed,onCheckedChange={
                            rows[index]=row.copy(reviewed=it)
                        })
                        Text("Conferi produto, unidade, quantidade e preço",
                            modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text("Conferência final",style=MaterialTheme.typography.titleMedium)
            Text("Soma dos itens: "+fiscalMoney(sum))
            declared?.let{Text("Total reconhecido da nota: "+fiscalMoney(it))}
            if(mismatch) {
                Text("Os totais divergem. Confira descontos e possíveis erros de leitura.",
                    color=MaterialTheme.colorScheme.error)
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Checkbox(checked=diffAccepted,onCheckedChange={diffAccepted=it})
                    Text("Conferi a diferença",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(similar){
                Text("Já existe uma compra semelhante nesta loja e data.",
                    color=MaterialTheme.colorScheme.error)
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Checkbox(checked=dupAccepted,onCheckedChange={dupAccepted=it})
                    Text("Confirmei que é outra compra",style=MaterialTheme.typography.bodySmall)
                }
            }
            Text("Registros pessoais. Foto, CPF e texto OCR bruto não são enviados.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={
                busy=true
                val confirmed=FiscalDraft(merchant.trim(),cnpj,date,declared,converted.filterNotNull())
                onSave(confirmed){success,result ->
                    busy=false
                    if(success){rows.clear();merchant="";cnpj="";declared=null;message=result}
                    else error=result
                }
            },enabled=canSave&&!busy&&!reading,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("fiscal-save")) {
                Text(if(busy)"Salvando..." else "Confirmar e salvar nota")
            }
        }
        item {
            Text("Compras por estabelecimento",style=MaterialTheme.typography.titleMedium)
            val grouped=FiscalVision.grouped(records)
            if(grouped.isEmpty()) Text("Ainda não há notas registradas por foto.")
            grouped.values.forEach { notes ->
                val last=notes.maxByOrNull{it.text("date")}?:return@forEach
                Text(FiscalVision.storeName(last)+": "+notes.size+" compra(s)",
                    style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
