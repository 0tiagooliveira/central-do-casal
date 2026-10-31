// Série temporal de preços de compras documentadas para a ficha do produto.
// Reproduz as regras conservadoras de associação do CatalogHistory.kt no Android.
import {normalizeCatalogUnit} from "./catalog.js";
import {observationPresentation,normalizedRate} from "./product-presentation.js";

const catalogId=id=>!id||String(id).startsWith("import_2026_")?String(id||""):"import_2026_"+id;
const asText=v=>typeof v==="string"?v.trim():"";
const parseCents=value=>{
  let v=asText(value).replace(/R\$/gi,"").replace(/\s/g,"");
  if(v.includes(","))v=v.replace(/\./g,"").replace(",",".");
  if(!/^\d+(\.\d{1,4})?$/.test(v))return null;
  const [whole,dec=""]=v.split(".");
  // Preserve fiscal precision: imported unit prices may carry 3-4 decimal places.
  const raw10000=Number(whole)*10000+Number(dec.padEnd(4,"0"));
  const cents=raw10000/100;
  return Number.isSafeInteger(raw10000)&&cents>=0&&cents<=1000000000?cents:null;
};
const priceBRL=value=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL",minimumFractionDigits:2,maximumFractionDigits:Number.isInteger(value)?2:4}).format(value/100);
const safe=s=>String(s??"").replace(/[&<>"']/g,ch=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
const validDate=d=>/^\d{4}-\d{2}-\d{2}$/.test(d||"");
const linkFor=(sku,links)=>{
  const found=(links||[]).filter(l=>l.kind==="catalogLink"&&l.private!==true&&catalogId(l.skuId||l.id)===catalogId(sku.id));
  found.sort((a,b)=>(Number(b.revision)||0)-(Number(a.revision)||0));
  return found[0];
};
export function relatedSkus(base,records,links) {
  if(!base||base.kind!=="catalogItem")return [];
  return records.filter(sku=>{
    if(sku.kind!=="catalogSku"||sku.private===true)return false;
    const explicit=linkFor(sku,links);
    if(explicit)return explicit.status==="confirmed"&&catalogId(explicit.baseItemId)===catalogId(base.id);
    if(sku.review!==false && sku.original?.grau_confirmacao_vinculo!=="confirmado")return false;
    return catalogId(sku.baseId)===catalogId(base.id);
  });
}
export function priceHistory(product,records=[],links=[]) {
  if(!product||product.private===true||!["catalogItem","catalogSku"].includes(product.kind))return [];
  const skus=product.kind==="catalogSku"?[product]:relatedSkus(product,records,links);
  const relatedIds=new Set(skus.map(s=>catalogId(s.id)));
  const seen=new Set(),result=[];
  function push(id,date,unit,price,store,skuId,detail={}){
    const c=typeof price==="number"?price:parseCents(price);
    const u=normalizeCatalogUnit(unit);
    if(!validDate(date)||c==null||u==="Escolher"||seen.has(id))return;
    const sku=skus.find(x=>catalogId(x.id)===catalogId(skuId));
    const description=detail.description||sku?.title||"";
    const presentation=observationPresentation(description,sku?.title||"",unit);
    const point={id,date,unit:u,cents:c,store:store||"",skuId:skuId||"",
      description,presentation,quantityText:String(detail.quantityText??""),
      receiptId:String(detail.receiptId??""),netCents:Number.isInteger(detail.netCents)?detail.netCents:null};
    point.rate=normalizedRate(point);
    seen.add(id);result.push(point);
  }
  for(const sku of skus) {
    const hist=Array.isArray(sku.history)?sku.history:[];
    for(let i=0;i<hist.length;i++){
      const row=hist[i];if(!row||typeof row!=="object")continue;
      push("fiscal:"+sku.id+":"+(row.id||row.nota_id+":"+(row.posicao_na_nota||i)),row.date,row.unidade,row.preco_unitario_reais,sku.store,sku.id,
        {description:row.descricao_original||row.descricao_padronizada||sku.title,
         quantityText:row.quantidade,receiptId:row.nota_id,
         netCents:row.valor_liquido_estimado_rateado_centavos});
    }
  }
  // Recupera observações diretamente das linhas das notas importadas quando
  // o campo catalogSku.history estiver ausente ou incompleto.
  for(const receipt of records){
    if(receipt.kind!=="importedReceipt"||receipt.private===true||!Array.isArray(receipt.lines))continue;
    for(let i=0;i<receipt.lines.length;i++){
      const line=receipt.lines[i];
      if(!line||typeof line!=="object")continue;
      const sku=skus.find(s=>catalogId(s.id)===catalogId(line.produto_id));
      if(!sku)continue;
      const lineId=line.id||String(line.nota_id||receipt.id)+":"+(line.posicao_na_nota||i);
      push("fiscal:"+sku.id+":"+lineId,receipt.date,line.unidade,line.preco_unitario_reais,sku.store||receipt.title,sku.id,
        {description:line.descricao_original||line.descricao_padronizada||sku.title,
         quantityText:line.quantidade,receiptId:line.nota_id||receipt.id,
         netCents:line.valor_liquido_estimado_rateado_centavos});
    }
  }
  for(const receipt of records){
    if(receipt.kind!=="receipt"||receipt.private===true||!Array.isArray(receipt.entries))continue;
    for(let i=0;i<receipt.entries.length;i++){
      const e=receipt.entries[i];
      if(!e||e.bought!==true||!Number.isSafeInteger(e.actual)||e.actual<0)continue;
      const sourceSku=catalogId(e.sourceSkuId),sourceBase=catalogId(e.sourceBaseId);
      let match=false;
      if(product.kind==="catalogSku")match=sourceSku!==""&&sourceSku===catalogId(product.id);
      else if(sourceSku){
        const sku=records.find(r=>r.kind==="catalogSku"&&catalogId(r.id)===sourceSku&&r.private!==true);
        const explicit=linkFor({id:sourceSku},links);
        if(sku)match=relatedIds.has(sourceSku);
        else if(explicit)match=explicit.status==="confirmed"&&catalogId(explicit.baseItemId)===catalogId(product.id);
        else match=sourceBase===catalogId(product.id);
      } else match=sourceBase!==""&&sourceBase===catalogId(product.id);
      if(match)push("receipt:"+receipt.id+":"+(e.id||i),receipt.date,e.unit,e.actual,receipt.market,e.sourceSkuId,
        {description:e.title,quantityText:String(Number(e.actualMilli||0)/1000),receiptId:receipt.id});
    }
  }
  return result.sort((a,b)=>a.date.localeCompare(b.date)||a.id.localeCompare(b.id));
}
export function comparableHistory(history) {
  if(!history.length)return {points:[],excluded:0,unit:""};
  // Preço por un não permite comparar frascos de 1/1,5/5 L ou unidade/caixa.
  // Cada apresentação comprovada é uma curva separada.
  const latest=history[history.length-1],unit=latest.unit;
  const key=latest.presentation?.key||"sem-apresentacao";
  const points=history.filter(p=>p.unit===unit&&(p.presentation?.key||"sem-apresentacao")===key);
  return {points,excluded:history.length-points.length,unit};
}
export function priceTrend(history) {
  const {points,unit,excluded}=comparableHistory(history);
  if(!points.length)return {label:"Sem preços documentados",kind:"unknown",points,unit,excluded};
  if(points.length===1)return {label:"1 preço documentado",kind:"unknown",points,unit,excluded};
  if(["un","pacote"].includes(unit)&&points.some(p=>
    !p.presentation?.dimensionAmount&&!p.presentation?.unitCount)){
    return {label:"Embalagem sem medida · variação não confiável",kind:"unknown",points,unit,excluded};
  }
  const first=points[0].cents,last=points[points.length-1].cents;
  const delta=last-first;
  const pct=first>0?Math.round((delta/first)*100):null;
  const kind=delta>0?"up":delta<0?"down":"equal";
  const pctText=pct==null?"":` (${pct>0?"+":""}${pct}%)`;
  const label=kind==="up"?"Subiu "+priceBRL(delta)+pctText+" desde a 1ª compra":kind==="down"?"Caiu "+priceBRL(-delta)+pctText+" desde a 1ª compra":"Preço estável desde a 1ª compra";
  return {label,kind,points,unit,excluded};
}
export function sparkline(history) {
  const trend=priceTrend(history),points=trend.points;
  if(!points.length)return '<div class="chart-empty">Sem valores históricos comparáveis</div>';
  const width=240,height=67,padX=8,padY=8,values=points.map(x=>x.cents),low=Math.min(...values),high=Math.max(...values);
  const den=Math.max(1,high-low);
  const positions=points.map((p,i)=>({x:points.length===1?width/2:padX+i*(width-padX*2)/(points.length-1),y:height-padY-(p.cents-low)*(height-2*padY)/den}));
  const coords=positions.map(p=>p.x.toFixed(2)+","+p.y.toFixed(2)).join(" ");
  const stroke=trend.kind==="up"?"#c75a55":trend.kind==="down"?"#188a62":"#437fc5";
  const dots=positions.map((p,i)=>`<circle cx="${p.x}" cy="${p.y}" r="2.4" fill="${stroke}"><title>${safe(points[i].date)} · ${safe(points[i].store)} · ${safe(points[i].presentation?.display||"Apresentação não identificada")}: ${priceBRL(points[i].cents)}/${safe(trend.unit)}</title></circle>`).join("");
  const first=points[0].date,last=points[points.length-1].date;
  return `<svg class="mini-price-chart" viewBox="0 0 240 67" role="img" aria-label="${safe(trend.label)}, de ${safe(first)} até ${safe(last)}, valores por ${safe(trend.unit)}">
    <line x1="8" y1="59" x2="232" y2="59" stroke="#e5eaf2" stroke-width="1"/>
    ${points.length>1?`<polyline points="${coords}" fill="none" stroke="${stroke}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>`:""}
    ${dots}</svg>`;
}
