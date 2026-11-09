// Fechamento seguro quando a cesta está no diário local e o Firestore
// recusa gravações. Arquiva compra e comprovante SEM afirmar sincronização.
import {readEmergency,closedKey} from "./emergency-cart.js";
import {compareInvoice} from "./invoice-reconcile.js";
const store=()=>window.localStorage;
const clone=v=>JSON.parse(JSON.stringify(v));
const fmt=c=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(c/100);
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
export function readOfflineClosure(state,market=state.market){
  if(!state?.uid||!state?.household||!["Mercado","Açougue"].includes(market))return null;
  try{
    const c=JSON.parse(store().getItem(closedKey(state,market))||"null");
    if(!c||c.version!==1||c.uid!==state.uid||c.household!==state.household||
      c.market!==market||!c.receipt||!c.receipt.generation)return null;
    return c;
  }catch{return null;}
}
export function archiveOfflinePurchase(state,market,invoice=null,mapping={}){
  const draft=readEmergency(state,market);
  if(!draft||!draft.basket?.generation)
    throw Error("Não existe uma compra offline para finalizar neste aparelho.");
  const existing=readOfflineClosure(state,market);
  if(existing&&existing.status==="pending")
    throw Error("Esta compra já foi finalizada offline. Baixe o comprovante ou tente sincronizá-la.");
  if(existing&&existing.status==="synced"&&existing.receipt.generation===draft.basket.generation)
    throw Error("Esta compra já foi sincronizada. Consulte o histórico antes de abrir outra.");
  const basket=draft.basket,entries=clone(basket.entries||[]),bought=entries.filter(e=>e.bought);
  if(!bought.length)throw Error("Não há produtos comprados neste carrinho.");
  for(const e of bought){
    if(!String(e.title||"").trim()||
       !["un","kg","g","L","mL","pacote"].includes(e.unit)||
       !Number.isSafeInteger(Number(e.actualMilli??e.plannedMilli))||
       Number(e.actualMilli??e.plannedMilli)<=0||
       !Number.isSafeInteger(e.actual)||e.actual<0)
      throw Error("Confira quantidade e preço de "+(e.title||"um produto")+" antes de finalizar.");
  }
  if(invoice){
    if(!["xml","image","pdf","pdf-transcription","manual"].includes(invoice.source)||
       !Array.isArray(invoice.lines)||invoice.lines.length>250||
       (invoice.fileData&&(!/^data:(image\/jpeg|application\/pdf);base64,/.test(invoice.fileData)||
         invoice.fileData.length>350000))||
       (invoice.xmlContent&&invoice.xmlContent.length>150000))
      throw Error("Comprovante inválido ou grande demais para guardar com a compra.");
  }
  const total=bought.reduce((n,e)=>n+Math.round(e.actual*Number(e.actualMilli??e.plannedMilli)/1000),0);
  const d=Object.fromEntries(new Intl.DateTimeFormat("en-US",{timeZone:"America/Sao_Paulo",
    year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(new Date()).map(x=>[x.type,x.value]));
  const report=compareInvoice(entries,invoice,mapping);
  const receipt={market,generation:basket.generation,revision:Number(draft.baseRevision)||0,
    budget:basket.budget||0,entries,kind:"receipt",private:false,total,
    date:d.year+"-"+d.month+"-"+d.day,updatedBy:state.uid,updatedAt:new Date().toISOString(),
    invoice:invoice?{...clone(invoice),mappings:clone(mapping),comparison:report,
      comparisonStatus:report.status,attachedAt:new Date().toISOString()}:null};
  const archive={version:1,status:"pending",uid:state.uid,household:state.household,market,
    savedAt:new Date().toISOString(),baseGeneration:draft.baseGeneration,
    baseRevision:draft.baseRevision,operationsCount:draft.operations.length,receipt};
  const serialized=JSON.stringify(archive);
  if(serialized.length>780000)
    throw Error("A compra com a nota ficou grande demais. Use um XML ou anexo menor.");
  try{
    store().setItem(closedKey(state,market),serialized);
    const verified=readOfflineClosure(state,market);
    if(!verified||verified.receipt.total!==total)
      throw Error("A confirmação da gravação local falhou.");
  }catch{
    throw Error("O aparelho não conseguiu salvar a compra finalizada. Mantenha seu backup JSON e libere espaço no navegador.");
  }
  return archive;
}
export function markOfflineClosureSynced(state,market){
  const existing=readOfflineClosure(state,market);
  if(!existing)return;
  const next={...existing,status:"synced",syncedAt:new Date().toISOString()};
  try{store().setItem(closedKey(state,market),JSON.stringify(next));}
  catch{throw Error("Compra enviada, mas o aparelho não confirmou a baixa local. Não apague seu backup; reabra a compra para verificar o histórico.");}
}
export function downloadOfflineClosure(state,market){
  const closed=readOfflineClosure(state,market);
  if(!closed)throw Error("Nenhuma compra finalizada offline encontrada.");
  const draft=readEmergency(state,market);
  const archive={type:"central-do-casal-compra-finalizada-offline",
    exportedAt:new Date().toISOString(),...clone(closed),
    backupOriginal:draft?{baseRevision:draft.baseRevision,baseGeneration:draft.baseGeneration,
      baseline:draft.baseline,operations:draft.operations,basket:draft.basket}:null};
  const blob=new Blob([JSON.stringify(archive,null,2)],{type:"application/json"});
  const url=URL.createObjectURL(blob),link=document.createElement("a");
  link.href=url;link.download="central-compra-finalizada-"+market.toLowerCase()+"-"+closed.receipt.date+".json";
  document.body.append(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1200);
}
export function offlineClosureBanner(state,showExport=true){
  const c=readOfflineClosure(state,state.market);
  if(!c||c.status!=="pending")return "";
  const r=c.receipt,number=r.entries.filter(e=>e.bought).length;
  return '<section class="offline-closed-banner" role="status">'+
    '<strong>✓ Compra finalizada neste aparelho · ainda NÃO sincronizada</strong>'+
    '<p>'+number+' produto(s) comprado(s), total '+fmt(r.total)+
      '. A compra e '+(r.invoice?"a nota anexada":"o registro sem nota")+
      ' estão guardados no histórico offline. Sua lista original foi preservada.</p>'+
    (r.invoice?.comparison?'<p>Conferência da nota: '+esc(r.invoice.comparison.status)+
      '. Diferenças não foram apagadas.</p>':"")+
    '<div class="offline-closed-actions">'+
    (showExport?'<button type="button" class="primary-btn" data-shop-action="offline-closed-export">Baixar compra finalizada</button>':"")+
    '<button type="button" class="item-btn" data-shop-action="emergency-sync">Enviar compra e nota ao Firebase</button></div>'+
    '<small>O celular da outra pessoa só terá essa compra após a sincronização. Até lá, não inicie outra compra sobre a mesma lista.</small></section>';
}
