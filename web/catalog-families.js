// Agrupamento de famílias para visualização no catálogo web.
// NÃO altera vínculos oficiais ou notas. Cada variante mantém sua própria série
// para não comparar preços de embalagens/variedades diferentes como um único item.
import {normalizeCatalogText} from "./catalog.js";
import {priceHistory,relatedSkus} from "./catalog-history.js";
import {presentationSeries} from "./product-presentation.js";
import {unmatchedHistoryCandidates,canonicalCatalogId} from "./catalog-matches.js";

const asText=v=>typeof v==="string"?v.trim():"";
const same=(a,b)=>!!a&&!!b&&canonicalCatalogId(a)===canonicalCatalogId(b);
const normalizedTitle=p=>normalizeCatalogText(p?.title);
const familyPrefix=(baseTitle,skuTitle)=>{
  if(!baseTitle||!skuTitle||baseTitle.length<3)return false;
  return skuTitle===baseTitle||skuTitle.startsWith(baseTitle+" ")||
    skuTitle.startsWith(baseTitle+"-")||skuTitle.startsWith(baseTitle+"/")||
    skuTitle.startsWith(baseTitle+"(");
};
const currentDecision=(sku,links)=>(links||[])
  .filter(l=>l.kind==="catalogLink"&&l.private!==true&&same(l.skuId||l.id,sku.id))
  .sort((a,b)=>Number(b.revision||0)-Number(a.revision||0))[0]||null;

// Cache per Firestore snapshot avoids rescanning hundreds of receipt lines per
// card and per catalog filter. Replaced automatically on the next snapshot.
let cachedRecords=null,cachedLinks=null;
const familyCache=new Map();
/** Renders broader family variants; labels origin and honors existing explicit decisions. */
export function familyVariants(base,records=[],links=[]) {
  if(!base||base.kind!=="catalogItem"||base.private===true)return [];
  if(records!==cachedRecords||links!==cachedLinks){
    cachedRecords=records;cachedLinks=links;familyCache.clear();
  }
  if(familyCache.has(base.id))return familyCache.get(base.id);
  const catalog=records.filter(r=>r.kind==="catalogSku"&&r.private!==true&&r.market===base.market);
  const bases=records.filter(r=>r.kind==="catalogItem"&&r.private!==true&&r.market===base.market);
  const official=new Set(relatedSkus(base,records,links).map(s=>s.id));
  const inferred=new Map(unmatchedHistoryCandidates(base,records,links).map(c=>[c.sku.id,c]));
  const familyName=normalizedTitle(base);
  const results=[];
  for(const sku of catalog){
    const decision=currentDecision(sku,links);
    if(decision&&(decision.status!=="confirmed"||!same(decision.baseItemId,base.id)))continue;
    const confirmed=official.has(sku.id);
    const proposed=inferred.get(sku.id);
    const title=normalizedTitle(sku);
    // Title-based inclusion is allowed in the *visual family* only when the base
    // is the uniquely identifiable most-specific prefix. For generic single-word
    // family heads (e.g. Açúcar), variants like mascavo/cristal remain visible.
    const matches=bases.filter(b=>familyPrefix(normalizedTitle(b),title));
    const maxLength=matches.reduce((n,b)=>Math.max(n,normalizedTitle(b).length),0);
    const best=matches.filter(b=>normalizedTitle(b).length===maxLength);
    const precise=best.length===1&&best[0].id===base.id;
    const isGenericHead=familyName.split(" ").length===1 &&
      bases.filter(b=>normalizedTitle(b)===familyName).length===1;
    const familyMatch=familyPrefix(familyName,title)&&(precise||isGenericHead);
    // No generic title matching when an imported ID points to a DIFFERENT base,
    // except when this is the generic family head; it is still just a family
    // preview, not an official association.
    if(!confirmed&&!proposed&&!familyMatch)continue;
    const history=priceHistory(sku,records,links);
    if(!history.length)continue;
    const status=confirmed?"confirmado":proposed?"referência importada":"família pelo nome";
    results.push({sku,history,series:presentationSeries(history),status,confirmed,needsReview:!confirmed,
      latest:history[history.length-1],evidence:proposed?.evidence||[]});
  }
  const sorted=results.sort((a,b)=>{
    if(a.confirmed!==b.confirmed)return a.confirmed?-1:1;
    if(a.history.length!==b.history.length)return b.history.length-a.history.length;
    return String(b.latest?.date||"").localeCompare(String(a.latest?.date||""))
      ||asText(a.sku.title).localeCompare(asText(b.sku.title),"pt-BR");
  });
  familyCache.set(base.id,sorted);
  return sorted;
}
export function primaryFamilySeries(base,records=[],links=[]){
  const own=priceHistory(base,records,links);
  const variants=familyVariants(base,records,links);
  if(own.length){
    // Main graph uses official associations only when available. If the official
    // series contains multiple SKUs, return one SKU's series to avoid mixing packs.
    const grouped=new Map();
    for(const point of own){
      const id=point.skuId||"base";
      if(!grouped.has(id))grouped.set(id,[]);
      grouped.get(id).push(point);
    }
    const largest=[...grouped].sort((a,b)=>b[1].length-a[1].length)[0];
    const sku=variants.find(v=>v.sku.id===largest[0])?.sku||
      records.find(r=>r.id===largest[0]);
    const pack=presentationSeries(largest[1])[0];
    return {history:pack?.points||[],label:sku?.title||base.title,confirmed:true,
      presentation:pack?.presentation,variants};
  }
  const preview=variants[0];
  const pack=preview?.series[0];
  return {history:pack?.points||[],label:preview?.sku.title||base.title,
    presentation:pack?.presentation,
    confirmed:!preview?.needsReview&&!!preview,variants};
}
