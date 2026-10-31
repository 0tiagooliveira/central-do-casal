// Catálogo existente no Firebase: os mesmos registros catalogItem/catalogSku do Android.
// Este módulo apenas deriva a visualização e um rascunho, sem escrever no catálogo.
export const normalizeCatalogText = value => String(value??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLocaleLowerCase("pt-BR").trim();
const asText = value => typeof value === "string" ? value.trim() : "";
const allowedUnits = ["un","kg","g","L","mL","pacote"];
export const normalizeCatalogUnit = value => {
  const key = asText(value).toUpperCase();
  return ({UN:"un",UND:"un",UNIDADE:"un",UNID:"un",KG:"kg",G:"g",L:"L",ML:"mL",PACOTE:"pacote",PCT:"pacote"})[key] || "Escolher";
};
export function catalogItems(records,market,source="base") {
  const kind=source==="store"?"catalogSku":"catalogItem";
  return (Array.isArray(records)?records:[])
    .filter(item=>item.kind===kind && item.private!==true && (item.market===market || (!item.market && market==="Mercado")))
    .filter(item=>asText(item.title).length)
    .sort((a,b)=>a.title.localeCompare(b.title,"pt-BR",{sensitivity:"base"}));
}
export function catalogCategories(items) {
  const cats=new Set(items.map(x=>asText(x.category)||"Sem categoria"));
  return ["Todas",...[...cats].sort((a,b)=>a.localeCompare(b,"pt-BR"))];
}
export function queryCatalog(items,query,category) {
  const needle=normalizeCatalogText(query);
  return items.filter(p=>
    (category==="Todas"||(asText(p.category)||"Sem categoria")===category)
    && (!needle || normalizeCatalogText([p.title,p.category,p.subgroup,p.store,p.group].filter(Boolean).join(" ")).includes(needle)));
}
export function catalogPrice(record) {
  // Na base, reference já representa centavos; SKU usa valor decimal textual.
  if(record.kind==="catalogItem" && Number.isSafeInteger(record.reference) && record.reference>=0)
    return {known:true,cents:record.reference};
  const v=asText(record.referenceText);
  if(v) {
    const raw=v.replace(/R\$/gi,"").replace(/\s/g,"");
    const normalized=raw.includes(",")?raw.replace(/\./g,"").replace(",","."):raw;
    if(/^\d+(\.\d{1,2})?$/.test(normalized)){
      const [whole,decimal=""]=normalized.split(".");
      const cents=Number(whole)*100+Number(decimal.padEnd(2,"0"));
      if(Number.isSafeInteger(cents)&&cents<=1000000000)return {known:true,cents};
    }
  }
  return {known:false,cents:0};
}
export function catalogDraft(product) {
  const price=catalogPrice(product);
  const unit=normalizeCatalogUnit(product.unit);
  const qtyText=asText(product.quantityText);
  let quantity=1000,quantityKnown=false;
  if(qtyText) {
    const clean=qtyText.replace(",",".");
    if(/^\d+(\.\d{1,3})?$/.test(clean)) {
      const [whole,dec=""]=clean.split(".");
      const milli=Number(whole)*1000+Number(dec.padEnd(3,"0"));
      if(Number.isSafeInteger(milli)&&milli>0&&milli<=100000000){quantity=milli;quantityKnown=true;}
    }
  }
  return {title:asText(product.title),unit,plannedMilli:quantity,actualMilli:quantity,
    estimated:price.cents,actual:null,bought:false,estimateKnown:price.known,
    unitReviewed:unit!=="Escolher",quantityReviewed:quantityKnown,
    sourceBaseId:product.kind==="catalogItem"?product.id:"",
    sourceSkuId:product.kind==="catalogSku"?product.id:"",
    category:asText(product.category),storageRoom:asText(product.storageRoom),
    recipient:asText(product.recipient)||"Casa",
    group:asText(product.group)||"Essenciais",
    subgroup:asText(product.subgroup),priority:asText(product.priority)||"Normal",
    sourcePlanId:"",sourceStepId:""};
}
export function alreadyAdded(product,entries) {
  const key=product.kind==="catalogItem"?"sourceBaseId":"sourceSkuId";
  return (Array.isArray(entries)?entries:[]).some(e=>e[key]===product.id && !!product.id);
}

// Adição expressa: cria só um item de planejamento. Unidade, quantidade ou preço
// ausentes permanecem sinalizados para revisão na lista, sem inventar histórico.
export function catalogQuickDraft(product) {
  const draft=catalogDraft(product);
  if(!allowedUnits.includes(draft.unit)) {
    draft.unit="un";
    draft.unitReviewed=false; // unidade sugerida como placeholder
    draft.estimated=0;
    draft.estimateKnown=false;
  }
  if(!draft.quantityReviewed) {
    draft.plannedMilli=1000;
    draft.actualMilli=1000;
    draft.quantityReviewed=false;
  }
  // A referência de item-base é um preço anterior, mas não tem unidade
  // fiscal garantida; não vira subtotal automaticamente.
  if(product.kind==="catalogItem"&&!asText(product.unit)) {
    draft.estimated=0;
    draft.estimateKnown=false;
  }
  return draft;
}
