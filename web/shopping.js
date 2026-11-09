import {applyMissingEstimates} from "./shopping-estimates.js";
import {resolvePurchaseDate} from "./purchase-date.js";
import {replayEmergency} from "./emergency-cart.js";
import {compareInvoice} from "./invoice-reconcile.js";
import {doc,runTransaction,serverTimestamp} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";

export const UNITS=["un","kg","g","L","mL","pacote"];
export function moneyCents(value) {
  const s=String(value).trim().replace(/R\$/g,"").replace(/\s/g,"");
  const normalized=s.includes(",")?s.replace(/\./g,"").replace(",","."):s;
  if(!/^\d+(\.\d{1,2})?$/.test(normalized))throw new Error("Preço inválido. Use até duas casas decimais, por exemplo 12,50.");
  const [whole,dec=""]=normalized.split(".");
  const cents=Number(whole)*100+Number(dec.padEnd(2,"0"));
  if(!Number.isSafeInteger(cents)||cents>1000000000)throw new Error("Preço fora do limite.");
  return cents;
}
export function milliQty(value) {
  const s=String(value).trim().replace(",",".");
  if(!/^\d+(\.\d{1,3})?$/.test(s)) throw new Error("Quantidade inválida. Use até três casas decimais.");
  const [whole,dec=""]=s.split(".");
  const milli=Number(whole)*1000+Number(dec.padEnd(3,"0"));
  if(!Number.isSafeInteger(milli)||milli<=0||milli>100000000)throw new Error("Quantidade deve ser positiva e até 100.000.");
  return milli;
}
export const subtotal=(c,m)=>Math.floor((Number(c)*Number(m)+500)/1000);
const freshBasket=market=>({market,generation:crypto.randomUUID(),budget:0,revision:0,entries:[]});
function checkEntry(entry) {
  const title=String(entry.title||"").trim();
  if(!title||title.length>200)throw new Error("Informe o nome do produto (até 200 caracteres).");
  if(!UNITS.includes(entry.unit))throw new Error("Escolha uma unidade válida.");
  if(!Number.isInteger(entry.plannedMilli)||entry.plannedMilli<=0)throw new Error("Informe uma quantidade válida.");
  if(entry.estimated<0||!Number.isSafeInteger(entry.estimated))throw new Error("Preço estimado inválido.");
  if(entry.bought&&(!Number.isSafeInteger(entry.actual)||entry.actual<0))throw new Error("Informe o preço real unitário do produto comprado.");
}
function assertSession(state,auth) {
  if(!auth.currentUser||state.uid!==auth.currentUser.uid||!state.household)throw new Error("Entre em seu espaço compartilhado para editar. Dados locais do Android não são sincronizados automaticamente.");
}
export function createShoppingActions(db,auth,getState) {
  const basketRef=(state,market)=>doc(db,"households",state.household,"baskets",market);
  async function modify(market,updater) {
    const state=getState();assertSession(state,auth);
    if(!["Mercado","Açougue"].includes(market))throw new Error("Lista inválida.");
    const expected=Number(state.baskets[market]?.revision||0);
    await runTransaction(db,async tx=>{
      const ref=basketRef(state,market),snap=await tx.get(ref);
      const old=snap.exists()?snap.data():freshBasket(market);
      if(Number(old.revision||0)!==expected)throw new Error("A lista mudou em outro aparelho. Confira os dados atualizados e tente novamente.");
      const next=updater(old);
      if(next.entries.length>200)throw new Error("Limite de 200 itens por lista.");
      tx.set(ref,{...next,revision:Number(old.revision||0)+1});
    });
  }
  const addItem=(market,entry)=>modify(market,old=>{
    checkEntry(entry);
    return {...old,entries:[...(old.entries||[]),{id:crypto.randomUUID(),title:entry.title.trim(),unit:entry.unit,plannedMilli:entry.plannedMilli,actualMilli:entry.plannedMilli,estimated:entry.estimated,actual:entry.bought?entry.actual:null,bought:entry.bought,estimateKnown:entry.estimateKnown!==false,unitReviewed:entry.unitReviewed!==false,quantityReviewed:entry.quantityReviewed!==false,sourceBaseId:entry.sourceBaseId||"",sourceSkuId:entry.sourceSkuId||"",category:entry.category||"",storageRoom:entry.storageRoom||"",recipient:entry.recipient||"Casa",group:entry.group||"Essenciais",subgroup:entry.subgroup||"",priority:entry.priority||"Normal",sourcePlanId:entry.sourcePlanId||"",sourceStepId:entry.sourceStepId||""}]};
  });
  /** Compra imprevista no supermercado: entra em No carrinho em uma transação.
   * Se já existe exatamente um pendente do mesmo item/variação e unidade,
   * move-o em vez de duplicar. Não altera notas ou catálogo.
   */
  const addToCart=(market,entry)=>modify(market,old=>{
    const purchased={...entry,bought:true,actualMilli:entry.plannedMilli,
      estimated:entry.estimated??entry.actual,
      actual:entry.actual,estimateKnown:true,
      unitReviewed:true,quantityReviewed:true};
    checkEntry(purchased);
    const normalized=str=>String(str||"").normalize("NFD")
      .replace(/[\u0300-\u036f]/g,"").trim().toLowerCase().replace(/\s+/g," ");
    const entries=old.entries||[];
    const same=entries.filter(e=>!e.bought&&e.unit===purchased.unit&&
      normalized(e.title)===normalized(purchased.title)&&
      (!purchased.sourceSkuId||!e.sourceSkuId||e.sourceSkuId===purchased.sourceSkuId)&&
      (!purchased.sourceBaseId||!e.sourceBaseId||e.sourceBaseId===purchased.sourceBaseId));
    if(same.length===1){
      return {...old,entries:entries.map(e=>e.id!==same[0].id?e:{
        ...e,bought:true,actual:purchased.actual,actualMilli:purchased.plannedMilli,
        plannedMilli:purchased.plannedMilli,estimated:e.estimateKnown!==false?e.estimated:purchased.actual,
        estimateKnown:true,unitReviewed:true,quantityReviewed:true,
        sourceBaseId:e.sourceBaseId||purchased.sourceBaseId||"",
        sourceSkuId:e.sourceSkuId||purchased.sourceSkuId||""
      })};
    }
    const record={id:crypto.randomUUID(),title:purchased.title.trim(),unit:purchased.unit,
      plannedMilli:purchased.plannedMilli,actualMilli:purchased.plannedMilli,
      estimated:purchased.actual,actual:purchased.actual,bought:true,
      estimateKnown:true,unitReviewed:true,quantityReviewed:true,
      sourceBaseId:purchased.sourceBaseId||"",sourceSkuId:purchased.sourceSkuId||"",
      category:purchased.category||"",storageRoom:purchased.storageRoom||"",
      recipient:purchased.recipient||"Casa",group:purchased.group||"Essenciais",
      subgroup:purchased.subgroup||"",priority:purchased.priority||"Normal",
      sourcePlanId:"",sourceStepId:""};
    return {...old,entries:[...entries,record]};
  });
  const updateItem=(market,id,entry)=>modify(market,old=>{
    const entries=old.entries||[];
    const original=entries.find(e=>e.id===id);
    if(!original)throw new Error("Produto não encontrado. Atualize a lista.");
    const next={...original,...entry,title:entry.title.trim(),actualMilli:entry.bought?(entry.actualMilli??entry.plannedMilli):(entry.actualMilli??original.actualMilli??entry.plannedMilli),actual:entry.bought?entry.actual:null};
    checkEntry(next);
    return {...old,entries:entries.map(e=>e.id===id?next:e)};
  });
  const removeItem=(market,id)=>modify(market,old=>{
    if(!(old.entries||[]).some(e=>e.id===id))throw new Error("Item não encontrado.");
    return {...old,entries:old.entries.filter(e=>e.id!==id)};
  });
  // Aceita apenas sugestões derivadas de registros atuais; o usuário confirma
  // o lote antes da transação. Preços manuais ou itens comprados não são tocados.
  const fillMissingEstimates=(market,proposals)=>modify(market,old=>({
    ...old,entries:applyMissingEstimates(old.entries||[],proposals)
  }));
  async function synchronizeEmergency(market,draft) {
    const state=getState();assertSession(state,auth);
    if(!draft||draft.market!==market||draft.uid!==state.uid||
      draft.household!==state.household||!Array.isArray(draft.operations)||
      draft.operations.length===0||draft.operations.length>300)
      throw Error("Backup local inválido para sincronização.");
    return runTransaction(db,async tx=>{
      const ref=basketRef(state,market),snap=await tx.get(ref);
      const isFirstSave=!snap.exists();
      if(isFirstSave&&Number(draft.baseRevision)!==0)
        throw Error("A lista original sumiu do Firebase. O rascunho foi preservado.");
      const remote=isFirstSave?draft.baseline:snap.data();
      if(!remote||remote.generation!==draft.baseGeneration)
        throw Error("Há uma lista diferente neste mercado. Seu rascunho foi preservado para conciliação.");
      const merged=replayEmergency(remote,draft);
      if(!Array.isArray(merged.entries)||merged.entries.length>200)
        throw Error("A lista ficou acima de 200 itens. Revise antes de sincronizar.");
      const saved={market,generation:remote.generation,budget:merged.budget,
        entries:merged.entries,revision:Number(remote.revision||0)+1};
      tx.set(ref,saved);
      return {revision:saved.revision,generation:saved.generation,basket:saved};
    });
  }
  async function publishOfflinePurchase(market,draft,closed) {
    const state=getState();assertSession(state,auth);
    if(!draft||!closed||closed.status!=="pending"||
      draft.uid!==state.uid||closed.uid!==state.uid||
      draft.household!==state.household||closed.household!==state.household||
      draft.market!==market||closed.market!==market||
      draft.basket?.generation!==closed.receipt?.generation||
      !["Mercado","Açougue"].includes(market))
      throw Error("Compra offline incompatível com a sessão. O histórico local foi preservado.");
    if(!draft.baseline?.entries||!Array.isArray(draft.operations))
      throw Error("Este backup não tem a revisão original para sincronização segura. O histórico offline foi preservado.");
    const ref=basketRef(state,market);
    const receiptRef=doc(db,"households",state.household,"records",closed.receipt.generation);
    // Quando a lista nunca foi enviada, criar primeiro SOMENTE a base vazia
    // da mesma geração. As regras do recibo exigem uma cesta anterior.
    if(Number(draft.baseRevision)===0){
      await runTransaction(db,async tx=>{
        const first=await tx.get(ref);
        if(first.exists())return;
        const base=draft.baseline;
        if(!base||base.generation!==draft.baseGeneration||
           !Array.isArray(base.entries)||base.entries.length>200)
          throw Error("A lista original do rascunho não pode ser restaurada com segurança.");
        tx.set(ref,{market,generation:base.generation,
          budget:Number(base.budget)||0,entries:base.entries,revision:1});
      });
    }
    const sorted=entries=>JSON.stringify((entries||[]).map(e=>[
      e.id,e.title,e.bought,e.unit,e.plannedMilli,e.actualMilli,e.actual,
      e.estimated,e.estimateKnown,e.unitReviewed,e.quantityReviewed
    ]).sort((a,b)=>String(a[0]).localeCompare(String(b[0]))));
    return runTransaction(db,async tx=>{
      const previous=await tx.get(receiptRef);
      if(previous.exists()){
        const data=previous.data();
        if(data.updatedBy===state.uid&&data.generation===closed.receipt.generation&&
           data.total===closed.receipt.total&&
           sorted(data.entries)===sorted(closed.receipt.entries))
          return {alreadySaved:true};
        throw Error("Já existe outra compra nesta geração no Firebase. A compra offline foi preservada para revisão.");
      }
      const snap=await tx.get(ref);
      if(!snap.exists())throw Error("Lista não encontrada no Firebase; histórico offline preservado.");
      const old=snap.data();
      if(old.generation!==closed.receipt.generation)
        throw Error("A lista foi encerrada em outro aparelho. O histórico offline permanece salvo e precisa de revisão.");
      const replayed=replayEmergency(old,draft);
      if(sorted(replayed.entries)!==sorted(closed.receipt.entries)||
         replayed.budget!==closed.receipt.budget)
        throw Error("Há diferenças entre a lista de outro aparelho e a compra finalizada offline. Não substituí nenhum dado. Baixe o histórico e revise antes de sincronizar.");
      const receipt={market,generation:old.generation,budget:old.budget,revision:old.revision,
        entries:replayed.entries,kind:"receipt",private:false,total:closed.receipt.total,
        date:closed.receipt.date,purchaseDateSource:closed.receipt.purchaseDateSource||"fechamento",
        recordedAt:closed.receipt.recordedAt||new Date().toISOString(),
        updatedBy:state.uid,updatedAt:serverTimestamp(),
        invoice:closed.receipt.invoice||null};
      if(JSON.stringify({...receipt,updatedAt:null}).length>790000)
        throw Error("O comprovante é grande demais para o Firebase. O histórico offline continua salvo.");
      tx.set(receiptRef,receipt);
      const nextEntries=replayed.entries.map(e=>e.bought?
        {...e,estimated:e.actual,actual:null,bought:false,
          estimateKnown:true,unitReviewed:true,quantityReviewed:true}:e);
      const fresh={market,budget:old.budget,revision:Number(old.revision)+1,
        generation:crypto.randomUUID(),entries:nextEntries};
      tx.set(ref,fresh);
      return {saved:true,basket:fresh};
    });
  }
  const changeBudget=(market,budget)=>modify(market,old=>({...old,budget}));
  const repurchase=(market,receiptId,itemId)=>modify(market,old=>{
    const source=getState().shared.find(r=>r.kind==="receipt"&&r.id===receiptId&&r.market===market);
    const e=source?.entries?.find(e=>e.id===itemId&&e.bought&&String(e.title||"").trim());
    if(!e)throw new Error("Compra antiga não encontrada na nuvem.");
    const estimate=e.actual!=null?e.actual:e.estimated;
    const next={...e,id:crypto.randomUUID(),bought:false,actual:null,actualMilli:e.plannedMilli,estimated:estimate,estimateKnown:estimate!=null,unitReviewed:UNITS.includes(e.unit),quantityReviewed:Number(e.plannedMilli)>0};
    return {...old,entries:[...(old.entries||[]),next]};
  });
  /** Merge a user-exported Android shopping basket, never replacing cloud entries or IDs. */
  const importBackup=(market,backup)=>modify(market,old=>{
    const incoming=Array.isArray(backup?.entries)?backup.entries:[];
    if(incoming.length>200)throw new Error("Backup ultrapassa 200 itens. Revise o arquivo.");
    const ids=new Set((old.entries||[]).map(e=>e.id));
    const additions=incoming.filter(e=>e&&typeof e.id==="string"&&e.id.length>0&&!ids.has(e.id));
    if(additions.some(e=>typeof e.title!=="string"||typeof e.unit!=="string"))
      throw new Error("Backup possui produtos incompletos ou incompatíveis.");
    if((old.entries||[]).length+additions.length>200)
      throw new Error("Total acima de 200 produtos. Revise a lista antes de importar.");
    const budget=old.budget===0&&Number.isSafeInteger(backup.budget)&&backup.budget>=0?backup.budget:old.budget;
    return {...old,budget,entries:[...(old.entries||[]),...additions]};
  });
  async function closeBasket(market,invoice=null,mapping={},purchaseDate="") {
    if(invoice){
      if(!["xml","image","pdf","pdf-transcription"].includes(invoice.source))throw new Error("Formato de nota não reconhecido.");
      if(!Array.isArray(invoice.lines)||invoice.lines.length>250)throw new Error("Nota com itens demais.");
      if(invoice.fileData&&(!/^data:(image\/jpeg|application\/pdf);base64,/.test(invoice.fileData)||
        invoice.fileData.length>350000))throw new Error("Comprovante acima do limite de tamanho.");
      if(invoice.xmlContent&&typeof invoice.xmlContent==="string"&&invoice.xmlContent.length>150000)
        throw new Error("XML acima do limite.");
    }
    const state=getState();assertSession(state,auth);
    const visible=state.baskets[market];
    if(!visible||!visible.generation)throw new Error("Lista não encontrada.");
    const expected=Number(visible.revision||0);
    const ref=basketRef(state,market);
    const receiptRef=doc(db,"households",state.household,"records",visible.generation);
    await runTransaction(db,async tx=>{
      const existing=await tx.get(receiptRef);
      if(existing.exists())return;
      const snap=await tx.get(ref);
      if(!snap.exists())throw new Error("Lista não encontrada.");
      const b=snap.data();
      if(b.generation!==visible.generation||Number(b.revision)!==expected)throw new Error("A lista mudou. Confira a revisão antes de finalizar.");
      const entries=b.entries||[];
      const bought=entries.filter(e=>e.bought);
      if(!bought.length)throw new Error("Marque pelo menos um produto comprado.");
      for(const e of bought){
        if(!String(e.title||"").trim()||!UNITS.includes(e.unit)||e.unitReviewed!==true||e.quantityReviewed!==true||Number(e.plannedMilli)<=0||Number(e.actualMilli)<=0||!Number.isSafeInteger(e.actual)||e.actual<0)
          throw new Error("Revise nome, quantidade, unidade e preço real de "+(e.title||"um produto")+".");
      }
      const total=bought.reduce((n,e)=>n+subtotal(e.actual,e.actualMilli),0);
      const chosen=resolvePurchaseDate(invoice,purchaseDate);
      const report=compareInvoice(entries,invoice,mapping);
      const receipt={...b,kind:"receipt",private:false,total,
        date:chosen.date,purchaseDateSource:chosen.source,recordedAt:new Date().toISOString(),
        updatedBy:state.uid,updatedAt:serverTimestamp(),
        invoice:invoice?{...invoice,comparison:report,mappings:mapping,
          attachedAt:new Date().toISOString(),comparisonStatus:report.status}:null};
      // Firestore documents must remain under 1 MiB, including cart + attachment.
      // Lower cap leaves margin for indexes, field-name and Firestore overhead.
      if(JSON.stringify({...receipt,updatedAt:null}).length>790000)
        throw new Error("A compra e o comprovante juntos ultrapassam o limite de armazenamento. Use o XML ou outra imagem menor.");
      tx.set(receiptRef,receipt);
      const nextEntries=entries.map(e=>e.bought?{...e,estimated:e.actual,actual:null,bought:false,estimateKnown:true,unitReviewed:true,quantityReviewed:true}:e);
      tx.set(ref,{market,budget:b.budget,revision:Number(b.revision)+1,generation:crypto.randomUUID(),entries:nextEntries});
    });
  }
  return {addItem,addToCart,updateItem,removeItem,changeBudget,fillMissingEstimates,repurchase,closeBasket,importBackup,synchronizeEmergency,publishOfflinePurchase};
}
