// Evidências para recuperar vínculos não confirmados entre catálogo e notas.
// Nunca une produtos apenas por nomes iguais nem altera compras importadas.
import {normalizeCatalogText} from "./catalog.js";
import {priceHistory,relatedSkus} from "./catalog-history.js";

export const canonicalCatalogId=id=>!id?"":String(id).startsWith("import_2026_")?String(id):"import_2026_"+String(id);
const equalId=(a,b)=>!!a&&!!b&&canonicalCatalogId(a)===canonicalCatalogId(b);
const text=v=>typeof v==="string"?v.trim():"";
function explicitLink(sku,links){
  return (links||[]).filter(link=>link.kind==="catalogLink"&&link.private!==true&&equalId(link.skuId||link.id,sku.id))
    .sort((a,b)=>Number(b.revision||0)-Number(a.revision||0))[0]||null;
}
export function unmatchedHistoryCandidates(base,records=[],links=[]) {
  if(!base||base.kind!=="catalogItem"||base.private===true)return [];
  const connected=new Set(relatedSkus(base,records,links).map(x=>x.id));
  const skus=records.filter(x=>x.kind==="catalogSku"&&x.private!==true&&x.market===base.market);
  const baseCandidates=records.filter(x=>x.kind==="catalogItem"&&x.private!==true&&normalizeCatalogText(x.title)===normalizeCatalogText(base.title));
  const nameUnique=baseCandidates.length===1;
  const potential=[];
  const bySku=new Map();
  function add(sku,source){
    if(!sku||connected.has(sku.id))return;
    const link=explicitLink(sku,links);
    // A previous "separate" or "confirmed to another base" decision wins over suggestions.
    if(link)return;
    const entry=bySku.get(sku.id)||{sku,evidence:[],history:[]};
    if(!entry.evidence.includes(source))entry.evidence.push(source);
    bySku.set(sku.id,entry);
  }
  for(const sku of skus) {
    if(equalId(sku.baseId,base.id))add(sku,"Vínculo informado no arquivo original, ainda não confirmado");
  }
  const skuById=new Map(skus.map(s=>[canonicalCatalogId(s.id),s]));
  for(const r of records) {
    if(r.kind==="matchSuggestion"&&Array.isArray(r.candidates)&&r.candidates.some(c=>equalId(c.baseItemId,base.id))){
      const sku=skuById.get(canonicalCatalogId(r.skuId));
      if(sku)add(sku,"Sugestão de associação importada, aguardando revisão");
    }
    if(r.kind==="importedReceipt"&&Array.isArray(r.lines)){
      for(const line of r.lines) {
        if(!line||!equalId(line.item_base_id,base.id))continue;
        const sku=skuById.get(canonicalCatalogId(line.produto_id));
        if(sku)add(sku,"Item-base identificado em linha de nota fiscal");
      }
    }
  }
  if(nameUnique){
    for(const sku of skus) {
      if(normalizeCatalogText(sku.title)===normalizeCatalogText(base.title))
        add(sku,"Mesmo nome; confirmar embalagem e descrição");
    }
  }
  for(const entry of bySku.values()){
    entry.history=priceHistory(entry.sku,records,links);
    entry.latest=entry.history.at(-1)||null;
    if(entry.history.length>0)potential.push(entry);
  }
  return potential.sort((a,b)=>b.history.length-a.history.length||a.sku.title.localeCompare(b.sku.title,"pt-BR"));
}
export function catalogCoverage(records=[],links=[],market="Mercado"){
  const items=records.filter(r=>r.kind==="catalogItem"&&r.private!==true&&r.market===market);
  let withHistory=0,withCandidates=0,withoutPrice=0,totalCandidates=0;
  for(const item of items){
    if(priceHistory(item,records,links).length){withHistory++;continue;}
    const c=unmatchedHistoryCandidates(item,records,links);
    if(c.length){withCandidates++;totalCandidates+=c.length;}
    else withoutPrice++;
  }
  return {catalog:items.length,withHistory,withCandidates,withoutPrice,totalCandidates};
}
