// Sugestões conservadoras: só reaproveita dados com origem identificada.
// Nunca altera automaticamente valores que a pessoa já digitou.
import {catalogPrice,normalizeCatalogText} from "./catalog.js";
import {priceHistory} from "./catalog-history.js";
import {presentationSeries} from "./product-presentation.js";

const exactId=(a,b)=>!!a&&!!b&&(String(a)===String(b)||
  String(a).replace(/^import_2026_/,"")===String(b).replace(/^import_2026_/,""));
const finiteCents=n=>Number.isFinite(n)&&n>0&&n<=1000000000?Math.round(n):null;
const isPending=e=>e&&e.bought!==true&&(e.estimateKnown===false||e.estimateKnown==null)&&
  !(Number.isSafeInteger(e.estimated)&&e.estimated>0);
function fromSku(entry,sku,records,links){
  const history=priceHistory(sku,records,links);
  const groups=presentationSeries(history);
  let group=null;
  if(groups.length===1)group=groups[0];
  else if(groups.length>1){
    // Em um SKU com frascos/caixas diferentes, só aproveita uma apresentação
    // quando o título do item identifica explicitamente a mesma embalagem.
    const label=normalizeCatalogText(entry.title||"");
    const specific=groups.filter(g=>g.presentation?.explicit&&
      normalizeCatalogText(g.presentation.display).length>=3&&
      label.includes(normalizeCatalogText(g.presentation.display)));
    if(specific.length===1)group=specific[0];
  }
  if(group){
    const recent=group.points.at(-1);
    const cents=finiteCents(recent?.cents);
    if(cents&&recent.unit===entry.unit)
      return {cents,source:"Último preço documentado: "+recent.date,kind:"sku-history",safe:true};
  }
  // Preço de referência do SKU sem histórico de embalagem inequívoco não é
  // suficiente para preencher produto com múltiplas apresentações.
  if(groups.length)return null;
  const reference=catalogPrice(sku);
  if(reference.known&&reference.cents>0&&sku.unit&&entry.unit===String(sku.unit))
    return {cents:reference.cents,source:"Preço de referência por loja",kind:"sku-reference",safe:true};
  return null;
}
export function suggestedEstimate(entry,records=[],links=[],market="Mercado"){
  if(!entry)return null;
  const srcSku=(records||[]).find(r=>r.kind==="catalogSku"&&r.private!==true&&
    r.market===market&&exactId(r.id,entry.sourceSkuId));
  if(srcSku){
    // Se há SKU explícito, jamais substituir por uma média da família.
    return fromSku(entry,srcSku,records,links);
  }
  const srcBase=(records||[]).find(r=>r.kind==="catalogItem"&&r.private!==true&&
    r.market===market&&exactId(r.id,entry.sourceBaseId));
  if(srcBase){
    const reference=catalogPrice(srcBase);
    if(reference.known&&reference.cents>0)
      return {cents:reference.cents,source:"Preço de referência da base, sem embalagem confirmada",
        kind:"base-reference",safe:true};
    return null;
  }
  // Produtos cadastrados manualmente, sem ID de origem: exibir apenas
  // sugestão por nome único, sem completar a lista automaticamente.
  if(entry.sourceBaseId||entry.sourceSkuId)return null;
  const name=normalizeCatalogText(entry.title);
  if(!name)return null;
  const matching=(records||[]).filter(r=>r.kind==="catalogItem"&&r.private!==true&&
    r.market===market&&normalizeCatalogText(r.title)===name);
  if(matching.length!==1)return null;
  const reference=catalogPrice(matching[0]);
  return reference.known&&reference.cents>0?
    {cents:reference.cents,source:"Produto homônimo da base: confirmar antes de usar",kind:"name-only",safe:false}:null;
}
export function missingEstimateSummary(entries=[],records=[],links=[],market="Mercado"){
  let missing=0,usable=0,manual=0;
  const suggestions=[];
  for(const e of entries){
    if(!isPending(e))continue;
    missing++;
    const estimate=suggestedEstimate(e,records,links,market);
    if(estimate?.safe){
      usable++;suggestions.push({id:e.id,title:e.title,cents:estimate.cents,
        source:estimate.source,unitReviewed:e.unitReviewed!==false});
    }else manual++;
  }
  return {missing,usable,manual,suggestions};
}
export function applyMissingEstimates(entries=[],proposals=[]){
  const proposalsById=new Map(proposals.filter(x=>x&&typeof x.id==="string"&&
    Number.isSafeInteger(x.cents)&&x.cents>0).map(x=>[x.id,x]));
  return entries.map(e=>{
    if(!isPending(e)||!proposalsById.has(e.id))return e;
    const proposal=proposalsById.get(e.id);
    // Mantém unitReviewed/quantityReviewed e não mexe em quantidade ou preço real.
    return {...e,estimated:proposal.cents,estimateKnown:true};
  });
}
