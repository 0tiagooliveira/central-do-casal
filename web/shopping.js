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
  async function closeBasket(market) {
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
      const dateParts=Object.fromEntries(new Intl.DateTimeFormat("en-US",{timeZone:"America/Sao_Paulo",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(new Date()).map(x=>[x.type,x.value]));
      const date=dateParts.year+"-"+dateParts.month+"-"+dateParts.day;
      const receipt={...b,kind:"receipt",private:false,total,date,updatedBy:state.uid,updatedAt:serverTimestamp()};
      tx.set(receiptRef,receipt);
      const nextEntries=entries.map(e=>e.bought?{...e,estimated:e.actual,actual:null,bought:false,estimateKnown:true,unitReviewed:true,quantityReviewed:true}:e);
      tx.set(ref,{market,budget:b.budget,revision:Number(b.revision)+1,generation:crypto.randomUUID(),entries:nextEntries});
    });
  }
  return {addItem,updateItem,removeItem,changeBudget,repurchase,closeBasket,importBackup};
}
