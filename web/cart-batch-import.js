// Importa um pequeno lote de compras por link, com confirmação do usuário.
// O payload não identifica conta, não contém credenciais e só é aplicado no
// household autenticado atual, no diário offline do próprio navegador.
const normalize=s=>String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .toLocaleLowerCase("pt-BR").trim().replace(/\s+/g," ");
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const fmt=n=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(n/100);
export function readCartBatch(search=window.location.search){
  try{
    const raw=new URLSearchParams(search).get("compra");
    if(!raw||raw.length>2800||!/^[A-Za-z0-9_-]+$/.test(raw))return null;
    const b64=raw.replace(/-/g,"+").replace(/_/g,"/");
    const bytes=Uint8Array.from(atob(b64.padEnd(Math.ceil(b64.length/4)*4,"=")),c=>c.charCodeAt(0));
    const payload=JSON.parse(new TextDecoder().decode(bytes));
    if(payload.v!==1||!Array.isArray(payload.items)||payload.items.length<1||payload.items.length>30)return null;
    const entries=payload.items.map(([name,quantity,cents])=>{
      if(typeof name!=="string"||name.length>120||!name.trim()||
        !Number.isSafeInteger(quantity)||quantity<1||quantity>100000000||
        !Number.isSafeInteger(cents)||cents<0||cents>100000000)
        throw Error("Produto inválido.");
      return {title:name.trim(),unit:"un",plannedMilli:quantity,actualMilli:quantity,
        actual:cents,estimated:cents,bought:true,estimateKnown:true,
        unitReviewed:true,quantityReviewed:true,group:"Compras no mercado",
        recipient:"Casa",category:"",storageRoom:"",subgroup:"",priority:"Normal",
        sourceBaseId:"",sourceSkuId:"",sourcePlanId:"",sourceStepId:""};
    });
    const id=String(payload.id||"").slice(0,90);
    const totalCents=entries.reduce((sum,e)=>sum+Math.round(e.actual*e.plannedMilli/1000),0);
    return {id,entries,totalCents};
  }catch{return null;}
}
export function planCartBatch(batch,basket) {
  const bought=(basket?.entries||[]).filter(e=>e.bought);
  const pendings=(basket?.entries||[]).filter(e=>!e.bought);
  const add=[],already=[],toMove=[];
  for(const entry of batch.entries){
    const key=normalize(entry.title);
    const matchedBought=bought.filter(e=>normalize(e.title)===key&&e.unit===entry.unit);
    if(matchedBought.length){
      already.push({entry,matchedBought});
      continue;
    }
    const foundPending=pendings.filter(e=>normalize(e.title)===key&&e.unit===entry.unit);
    if(foundPending.length>1){
      already.push({entry,matchedBought:[],ambiguous:true});
      continue;
    }
    if(foundPending.length===1)toMove.push(entry);
    else add.push(entry);
  }
  return {add,toMove,already,needed:[...toMove,...add],sumCents:[...toMove,...add]
    .reduce((n,e)=>n+Math.round(e.actual*e.plannedMilli/1000),0)};
}
export function cartBatchBanner(state,batch=readCartBatch()){
  if(!batch||state.market!=="Mercado"||!state.uid||!state.household)return "";
  const b=state.baskets.Mercado||{entries:[]};
  const plan=planCartBatch(batch,b);
  return '<section class="cart-batch-banner"><strong>🛒 Compra enviada: '+batch.entries.length+
    ' produtos · '+fmt(batch.totalCents)+'</strong>'+
    '<p>Inclusão em No carrinho neste aparelho. '+plan.needed.length+
    ' item(ns) para adicionar/mover e '+plan.already.length+
    ' já comprado(s) ou que precisam de revisão. Nada será duplicado automaticamente.</p>'+
    (plan.needed.length?
      '<button type="button" class="primary-btn" data-shop-action="import-link-cart">Adicionar '+
      plan.needed.length+' item(ns) ao carrinho offline · '+fmt(plan.sumCents)+'</button>':
      '<p>Os produtos já constam no carrinho ou precisam de revisão. Nenhuma duplicação será criada.</p>')+
    '<small>Valores locais não ficam disponíveis no celular da outra pessoa até sincronizar.</small></section>';
}
export function consumeCartBatchLink(){
  const url=new URL(window.location.href);
  url.searchParams.delete("compra");
  window.history.replaceState(null,"",url.pathname+url.search+url.hash);
}
