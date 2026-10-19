package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.effectiveCatalogBaseId
import com.centraldocasal.app.domain.productPurchases
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Conservative NFC-e extraction. All values must be reviewed before saving. */
internal data class FiscalLine(val title:String, val quantityMilli:Long, val unit:String,
    val totalCents:Long, val unitPriceCents:Long, val productId:String?=null)
internal data class FiscalDraft(val merchantName:String="",val cnpj:String="",val date:String="",
    val declaredTotalCents:Long?=null,val lines:List<FiscalLine> = emptyList(),val scannedLines:Int=0)
internal data class FiscalMatch(val productId:String,val name:String,val confidence:Double)
internal data class FiscalPrice(val date:String,val store:String,val unitPriceCents:Long)
internal data class FiscalComparison(val previous:FiscalPrice,val cheapest:FiscalPrice,val differenceCents:Long)

internal object FiscalVision {
    private val pt = Locale.forLanguageTag("pt-BR")
    private val cnpj = Regex("""\b\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}\b""")
    private val date = Regex("""\b(\d{2})/(\d{2})/(\d{4})\b""")
    // OCR is unreliable: only rows with explicit quantity, unit and price qualify.
    private val row = Regex(
        """^\s*(?:\d{1,4}\s+)?(.{3,110}?)\s+(\d+(?:[.,]\d{1,3})?)\s*(UN|UND|UNID|KG|G|L|ML|PCT|PACOTE)\s*(?:[Xx×]\s*(?:R\$\s*)?\d+[.,]\d{2}\s+)?(?:R\$\s*)?([\d.]+,\d{2})\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val totalRegex=Regex("""(?:VALOR\s+TOTAL|TOTAL\s+A\s+PAGAR|TOTAL\s+DA\s+NOTA|TOTAL\s+R\$)\s*[:R$\s]*([\d.]+,\d{2})""",RegexOption.IGNORE_CASE)

    fun key(raw:String)=Normalizer.normalize(raw.uppercase(pt),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^A-Z0-9]+")," ").trim()
        .replace(Regex("\\s+")," ")

    fun merchantId(name:String,taxId:String):String {
        val tax=taxId.filter(Char::isDigit)
        val nameKey=key(name)
        require(tax.length==14 || nameKey.length>=3)
        val basis=if(tax.length==14) "CNPJ:$tax" else "NOME:$nameKey"
        val sha=MessageDigest.getInstance("SHA-256").digest(basis.toByteArray())
        return "merchant-"+sha.take(12).joinToString(""){"%02x".format(it)}
    }
    fun cents(raw:String):Long?=runCatching {
        BigDecimal(raw.replace("R$","").replace(" ","").replace(".","").replace(',','.'))
            .movePointRight(2).longValueExact().takeIf { it in 0..100_000_000 }
    }.getOrNull()
    private fun qtyMilli(q:String,u:String):Pair<Long,String>?=runCatching {
        val unit=when(u.uppercase(pt)){
            "KG","G"->"kg";"L","ML"->"L";"PCT","PACOTE"->"pacote";else->"un"
        }
        val amount=BigDecimal(q.replace(',','.')).let {
            if(u.uppercase(pt) in setOf("G","ML")) it.divide(BigDecimal(1000)) else it
        }
        val milli=amount.movePointRight(3).setScale(0,RoundingMode.UNNECESSARY).longValueExact()
        (milli to unit).takeIf{milli in 1..100_000_000}
    }.getOrNull()

    fun parse(text:String):FiscalDraft {
        val lines=text.lines().map{it.replace(Regex("\\s+")," ").trim()}
            .filter(String::isNotBlank).take(400)
        val taxIndex=lines.indexOfFirst{cnpj.containsMatchIn(it)}
        val tax=lines.getOrNull(taxIndex)?.let{cnpj.find(it)?.value?.filter(Char::isDigit)}.orEmpty()
        val merchant=lines.take(if(taxIndex>0) taxIndex.coerceAtMost(18) else 12).firstOrNull {
            val name=key(it)
            name.length in 4..100 && name.any(Char::isLetter) &&
                listOf("DANFE","DOCUMENTO","CUPOM","CNPJ","CPF","CHAVE","CONSUMIDOR",
                    "NFC E","NOTA FISCAL","SECRETARIA").none(name::startsWith) &&
                !date.containsMatchIn(it)
        }.orEmpty()
        val iso=lines.asSequence().mapNotNull{date.find(it)}.mapNotNull {
            runCatching {
                LocalDate.parse("${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}",
                    DateTimeFormatter.ofPattern("dd/MM/uuuu")).toString()
            }.getOrNull()
        }.firstOrNull().orEmpty()
        val total=lines.asSequence().mapNotNull{totalRegex.find(it)?.groupValues?.get(1)}
            .mapNotNull(::cents).firstOrNull()
        val products=lines.mapNotNull { line ->
            if(Regex("""(?i)\b(TOTAL|TROCO|DESCONTO|TRIBUTOS|SUBTOTAL|PAGAMENTO)\b""")
                .containsMatchIn(line))return@mapNotNull null
            val m=row.matchEntire(line)?:return@mapNotNull null
            val title=m.groupValues[1].replace(Regex("""^\d{8,14}\s+"""),"").trim()
            if(key(title).length<3)return@mapNotNull null
            val (qty,unit)=qtyMilli(m.groupValues[2],m.groupValues[3])
                ?:return@mapNotNull null
            val net=cents(m.groupValues[4])?:return@mapNotNull null
            val unitPrice=runCatching {
                BigDecimal(net).multiply(BigDecimal(1000)).divide(BigDecimal(qty),0,RoundingMode.HALF_UP)
                    .longValueExact()
            }.getOrNull()?:return@mapNotNull null
            FiscalLine(title,qty,unit,net,unitPrice)
        }.take(120)
        return FiscalDraft(merchant,tax,iso,total,products,lines.size)
    }

    private fun similarity(a:String,b:String):Double {
        val x=key(a); val y=key(b)
        if(x.isEmpty()||y.isEmpty())return 0.0
        if(x==y)return 1.0
        if(Regex("\\d+").findAll(x).map{it.value}.toList()!=
            Regex("\\d+").findAll(y).map{it.value}.toList())return 0.0
        val l=x.split(" ").toSet();val r=y.split(" ").toSet()
        return l.intersect(r).size.toDouble()/l.union(r).size
    }
    private fun canonical(r:Record, records:List<Record>):String =
        if(r.kind=="catalogSku")effectiveCatalogBaseId(r,records)
            ?.let{ id->records.firstOrNull{it.id==id&&it.kind=="catalogItem"}?.id }?:r.id
        else r.id

    fun match(line:FiscalLine,records:List<Record>):List<FiscalMatch> =
        records.filter {
            (it.kind in setOf("catalogItem","catalogSku") && !it.isPrivate) ||
                it.kind=="photoProduct"
        }.mapNotNull { r ->
            if(r.text("unit").isNotBlank() && r.text("unit")!=line.unit)return@mapNotNull null
            val score=maxOf(similarity(line.title,r.text("title")),
                similarity(line.title,r.text("displayName")))
            if(score<0.6)return@mapNotNull null
            FiscalMatch(canonical(r,records),r.text("displayName").ifBlank{r.text("title")},score)
        }.distinctBy{it.productId}
            .sortedWith(compareByDescending<FiscalMatch>{it.confidence}.thenBy{it.name})
            .take(5)

    /** Suggest but don't silently link: the user must approve each detected product. */
    fun exactSuggestion(line:FiscalLine,records:List<Record>):String?=
        match(line,records).filter{it.confidence==1.0}.singleOrNull()?.productId

    fun history(productId:String,unit:String,records:List<Record>):List<FiscalPrice> {
        val prices=mutableListOf<FiscalPrice>()
        records.filter{it.kind=="photoReceipt"}.forEach { receipt ->
            @Suppress("UNCHECKED_CAST")
            val rows=receipt.fields["entries"] as? List<Map<String,Any?>> ?: emptyList()
            rows.filter{it["productId"]==productId && it["unit"]==unit}.forEach { entry ->
                val price=(entry["unitPriceCents"] as? Number)?.toLong()
                if(price!=null && price>=0)prices+=FiscalPrice(
                    receipt.text("date"),receipt.text("merchantName"),price)
            }
        }
        val product=records.firstOrNull{it.id==productId&&it.kind in setOf("catalogItem","catalogSku")}
        product?.let { p ->
            productPurchases(p,records).filter{it.unit.equals(unit,true)}.forEach{purchase->
                val amount=runCatching{BigDecimal(purchase.unitPriceText.replace(',','.'))
                    .movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()}.getOrNull()
                if(amount!=null && amount>=0)prices+=FiscalPrice(
                    purchase.date,purchase.store,amount)
            }
        }
        return prices.sortedWith(compareByDescending<FiscalPrice>{it.date}.thenBy{it.store})
    }
    fun compare(line:FiscalLine,productId:String?,records:List<Record>):FiscalComparison? {
        if(productId.isNullOrBlank())return null
        val past=history(productId,line.unit,records)
        if(past.isEmpty())return null
        return FiscalComparison(past.first(),past.minBy{it.unitPriceCents},
            line.unitPriceCents-past.first().unitPriceCents)
    }
    fun grouped(records:List<Record>):Map<String,List<Record>> =
        records.filter{it.kind=="photoReceipt"}.groupBy{it.text("merchantId")}
}
