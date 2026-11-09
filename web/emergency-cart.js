// Contingência quando o Firestore bloqueia gravações por cota/limite.
// Operações locais ficam isoladas por usuário, casa e mercado e NUNCA são
// consideradas sincronizadas até a confirmação explícita do backend.
const storage=()=>window.localStorage;
const key=(s,market)=>"cdc:emergency-v1:"+encodeURIComponent(s.uid||"")+":"+
  encodeURIComponent(s.household||"")+":"+encodeURIComponent(market);
export const closedKey=(s,market)=>"cdc:offline-closed-v1:"+
  encodeURIComponent(s.uid||"")+":"+encodeURIComponent(s.household||"")+":"+
  encodeURIComponent(market);
const allowed=["Mercado","Açougue"];
const cacheKey=(state,market)=>"cdc:last-basket-v1:"+
  encodeURIComponent(state.uid||"")+":"+encodeURIComponent(state.household||"")+":"+encodeURIComponent(market);
export function cacheBasketSnapshot(state,market,basket){
  if(!state?.uid||!state?.household||!allowed.includes(market)||!basket?.generation||
      !Array.isArray(basket.entries))return;
  try{
    storage().setItem(cacheKey(state,market),JSON.stringify({
      savedAt:new Date().toISOString(),uid:state.uid,household:state.household,market,
      basket:{market,generation:basket.generation,revision:Number(basket.revision)||0,
        entries:basket.entries,budget:basket.budget||0}
    }));
  }catch{}
}
export function readCachedBasket(state,market){
  if(!state?.uid||!state?.household||!allowed.includes(market))return null;
  try{
    const item=JSON.parse(storage().getItem(cacheKey(state,market))||"null");
    return item?.uid===state.uid&&item.household===state.household&&
      item.market===market&&item.basket?.generation&&Array.isArray(item.basket.entries)?
      item.basket:null;
  }catch{return null;}
}

// Este arquivo de emergência é exportado pelo próprio aplicativo com as
// operações pendentes. Reimportá-lo NUNCA executa gravação no Firestore.
export function inspectEmergencyBackup(file,state) {
  if(file?.type!=="central-do-casal-emergency-backup"||
    !allowed.includes(file.market)||!file.baskets||
    file.household!==state?.household||!state?.uid||!state?.household)
    throw Error("Este arquivo não pertence à mesma casa ou não é um backup de emergência da Central.");
  const market=file.market,basket=file.baskets[market],operations=file.operations;
  if(!basket||basket.market!==market||
    typeof basket.generation!=="string"||!basket.generation||
    !Array.isArray(basket.entries)||basket.entries.length>200||
    !Array.isArray(operations)||operations.length<1||operations.length>300||
    !Number.isSafeInteger(file.baseRevision)||file.baseRevision<0)
    throw Error("O arquivo não contém uma lista e um diário de alterações válidos.");
  const ids=new Set();
  const validEntry=e=>e&&typeof e.id==="string"&&e.id.length<=160&&
    typeof e.title==="string"&&e.title.length>0&&e.title.length<=200&&
    ["un","kg","g","L","mL","pacote"].includes(e.unit)&&
    Number.isSafeInteger(e.plannedMilli)&&e.plannedMilli>0&&e.plannedMilli<=100000000&&
    (!e.bought||(Number.isSafeInteger(e.actual)&&e.actual>=0&&e.actual<=1000000000))&&
    (e.actualMilli==null||(Number.isSafeInteger(e.actualMilli)&&e.actualMilli>0&&e.actualMilli<=100000000));
  for(const e of basket.entries){
    if(!validEntry(e)||ids.has(e.id))throw Error("Há produtos inválidos ou duplicados no backup.");
    ids.add(e.id);
  }
  for(const op of operations){
    if(!op||!["add","remove","update","addToCart","batchAddToCart","budget"].includes(op.type)||
      (op.entry&&!validEntry(op.entry))||
      (op.type==="update"&&(!ids.has(op.id)||!validEntry(op.entry)))||
      (op.type==="add"&&!validEntry(op.entry))||
      (op.type==="budget"&&(!Number.isSafeInteger(op.budget)||op.budget<0))||
      (op.type==="batchAddToCart"&&(!Array.isArray(op.entries)||op.entries.length>30||
        op.entries.some(e=>!validEntry(e)))))
      throw Error("O arquivo tem uma alteração inválida e não foi restaurado.");
  }
  const remote=state.baskets?.[market]||readCachedBasket(state,market);
  if(remote?.generation&&remote.generation!==basket.generation)
    throw Error("A compra no Firebase já pertence a outra lista. O arquivo foi preservado, mas não foi restaurado sobre a compra nova.");
  const purchased=basket.entries.filter(e=>e.bought);
  const total=purchased.reduce((sum,e)=>sum+Math.round(e.actual*Number(e.actualMilli??e.plannedMilli)/1000),0);
  const baseRevision=file.baseRevision;
  // Só uma cesta realmente conhecida da MESMA revisão pode ser considerada
  // a base original. Se não houver, mostramos o backup local mas nunca
  // forçamos uma sincronização que poderia apagar dados da outra pessoa.
  const storedBase=file.baseline;
  const validStoredBase=storedBase?.generation===basket.generation&&
    Number(storedBase?.revision)===baseRevision&&Array.isArray(storedBase?.entries)&&
    storedBase.entries.length<=200&&storedBase.entries.every(validEntry);
  const baseline=validStoredBase?simple(storedBase):
    remote?.generation===basket.generation&&Number(remote.revision)===baseRevision?
      simple(remote):null;
  return {market,basket,operations,baseline,baseRevision,total,
    purchasedCount:purchased.length,pendingCount:basket.entries.length-purchased.length,
    syncSafe:!!baseline,sourceUid:file.uid||"",exportedAt:file.exportedAt||""};
}
export function restoreEmergencyBackup(state,file,{replace=false}={}) {
  const info=inspectEmergencyBackup(file,state);
  if(hasOfflineClosurePending(state,info.market))
    throw Error("Esta compra está finalizada offline. Baixe o comprovante e resolva a sincronização antes de substituir a lista.");
  const existing=readEmergency(state,info.market);
  if(existing&&!replace)throw Error("Já existe uma compra local neste navegador. Baixe o backup atual antes de substituir.");
  if(existing&&replace){
    if(existing.baseGeneration!==info.basket.generation)
      throw Error("O backup existente pertence a outra compra. Ele não será substituído.");
    if(existing.operations.length>info.operations.length)
      throw Error("Este navegador tem mais alterações locais do que o arquivo escolhido. Baixe um novo backup daqui antes de tentar restaurar o arquivo antigo.");
    // Preservação adicional para recuperar o diário anterior se necessário.
    try{storage().setItem(key(state,info.market)+":anterior",JSON.stringify(existing));}
    catch{throw Error("Não foi possível proteger o backup anterior. Nenhuma alteração foi aplicada.");}
  }
  const draft={version:1,uid:state.uid,household:state.household,market:info.market,
    createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),
    baseRevision:info.baseRevision,baseGeneration:info.basket.generation,
    baseline:info.baseline,basket:{...simple(info.basket),localPending:true},
    operations:simple(info.operations),restoredFromFile:true,
    syncBlockedReason:info.syncSafe?"":"Sem cópia da cesta original na mesma revisão. Confira os dados antes de sincronizar."};
  try{
    storage().setItem(key(state,info.market),JSON.stringify(draft));
    const saved=readEmergency(state,info.market);
    if(!saved||saved.operations.length!==info.operations.length)
      throw Error("Falha na gravação local.");
  }catch{
    throw Error("O backup não coube no armazenamento deste navegador. Guarde o arquivo original.");
  }
  return info;
}

const simple=v=>JSON.parse(JSON.stringify(v));
const normalize=s=>String(s??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .toLocaleLowerCase("pt-BR").trim().replace(/\s+/g," ");
const escapeHTML=s=>String(s??"").replace(/[&<>"']/g,c=>
  ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
export function isQuotaError(error) {
  const code=String(error?.code||"").toLowerCase();
  const message=String(error?.message||error||"").toLowerCase();
  return code.includes("resource-exhausted")||code.includes("quota")||
    /quota exceeded|exceeded.*quota|quota.*exceeded|limit.*exceeded|limite de cota/.test(message);
}
export function hasOfflineClosurePending(state,market=state.market){
  if(!state?.uid||!state?.household||!allowed.includes(market))return false;
  try{
    const record=JSON.parse(storage().getItem(closedKey(state,market))||"null");
    return !!record&&record.status==="pending"&&record.uid===state.uid&&record.household===state.household;
  }catch{return false;}
}
export function readEmergency(state,market=state.market){
  if(!state?.uid||!state?.household||!allowed.includes(market))return null;
  try{
    const draft=JSON.parse(storage().getItem(key(state,market))||"null");
    if(!draft||draft.version!==1||draft.uid!==state.uid||
      draft.household!==state.household||draft.market!==market||
      !Array.isArray(draft.operations)||!draft.basket||!Array.isArray(draft.basket.entries))
      return null;
    return draft;
  }catch{return null;}
}
function applyOperation(basket,operation) {
  const entries=basket.entries||[];
  if(operation.type==="batchAddToCart"){
    if(!Array.isArray(operation.entries)||operation.entries.length<1||operation.entries.length>30)
      throw Error("Lote de compras inválido.");
    let latest=basket;
    for(const item of operation.entries){
      if(!item.title||item.unit!=="un"||!Number.isSafeInteger(item.actual)||
        !Number.isSafeInteger(item.plannedMilli)||item.plannedMilli<1)
        throw Error("Lote de compras contém item inválido.");
      // Nunca somar a compra duas vezes, inclusive se a mesma importação
      // for reexecutada ou outro dispositivo já tiver marcado o item.
      const exists=(latest.entries||[]).some(e=>e.bought&&e.unit===item.unit&&
        normalize(e.title)===normalize(item.title));
      if(exists)continue;
      latest=applyOperation(latest,{type:"addToCart",entry:{...item,id:item.id||crypto.randomUUID()}});
    }
    return latest;
  }
  if(operation.type==="budget"){
    if(!Number.isSafeInteger(operation.budget)||operation.budget<0)throw Error("Orçamento inválido.");
    return {...basket,budget:operation.budget};
  }
  if(operation.type==="update"){
    if(!entries.some(e=>e.id===operation.id))
      throw Error("Este item não está no backup local. Não foi alterado.");
    return {...basket,entries:entries.map(e=>e.id===operation.id?simple(operation.entry):e)};
  }
  if(operation.type==="add"){
    if(entries.some(e=>e.id===operation.entry.id))return basket;
    if(entries.length>=200)throw Error("Limite de 200 produtos na lista.");
    return {...basket,entries:[...entries,simple(operation.entry)]};
  }
  if(operation.type==="addToCart"){
    const entry=operation.entry;
    const same=entries.filter(e=>!e.bought&&e.unit===entry.unit&&normalize(e.title)===normalize(entry.title)&&
      (!entry.sourceSkuId||!e.sourceSkuId||e.sourceSkuId===entry.sourceSkuId)&&
      (!entry.sourceBaseId||!e.sourceBaseId||e.sourceBaseId===entry.sourceBaseId));
    if(same.length===1){
      const id=same[0].id;
      operation.type="update";
      operation.id=id;
      operation.entry={...same[0],bought:true,actual:entry.actual,
        plannedMilli:entry.plannedMilli,actualMilli:entry.plannedMilli,
        estimated:same[0].estimateKnown!==false?same[0].estimated:entry.actual,
        estimateKnown:true,unitReviewed:true,quantityReviewed:true};
      return applyOperation(basket,operation);
    }
    operation.type="add";
    operation.entry={...simple(entry),id:entry.id||crypto.randomUUID(),bought:true,
      actualMilli:entry.plannedMilli,estimateKnown:true,unitReviewed:true,quantityReviewed:true,
      estimated:entry.actual};
    return applyOperation(basket,operation);
  }
  if(operation.type==="remove")
    return {...basket,entries:entries.filter(e=>e.id!==operation.id)};
  throw Error("Ação não pode ser guardada offline.");
}
export function saveEmergency(state,market,input) {
  if(hasOfflineClosurePending(state,market))
    throw Error("Esta compra já foi finalizada offline. Baixe o comprovante e sincronize antes de editar outra vez.");
  if(!state?.uid||!state?.household||!allowed.includes(market))
    throw Error("Não foi possível identificar a conta para o backup local.");
  let draft=readEmergency(state,market);
  if(!draft){
    const baseline=state.baskets?.[market]?.generation?state.baskets[market]:readCachedBasket(state,market);
    if(!baseline||!Array.isArray(baseline.entries)||!baseline.generation)
      throw Error("Não há cópia da lista disponível para usar offline. Anote o item e o preço.");
    draft={version:1,uid:state.uid,household:state.household,market,
      createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),
      baseRevision:Number(baseline.revision)||0,baseGeneration:baseline.generation,
      baseline:simple(baseline),basket:simple(baseline),operations:[]};
  }
  const op=simple(input);
  const result=applyOperation(draft.basket,op);
  draft.basket={...result,market,revision:draft.baseRevision,localPending:true};
  draft.operations.push(op);draft.updatedAt=new Date().toISOString();
  try{
    storage().setItem(key(state,market),JSON.stringify(draft));
    if(!readEmergency(state,market))throw Error("Erro de verificação do backup.");
  }catch{
    throw Error("O navegador não conseguiu salvar o backup local. NÃO considere o item salvo. Registre o preço fora do app.");
  }
  return draft;
}
export function projectedShoppingState(state) {
  const draft=readEmergency(state,state.market);
  if(!draft){
    const remote=state.baskets?.[state.market];
    const cached=!remote?.generation?readCachedBasket(state,state.market):null;
    return cached?{...state,baskets:{...state.baskets,[state.market]:cached},
      cachedBasketOnly:true}:state;
  }
  return {...state,baskets:{...state.baskets,[state.market]:draft.basket},
    emergencyPending:draft.operations.length};
}
export function clearEmergency(state,market){
  storage().removeItem(key(state,market));
}
export function downloadEmergency(state,market){
  const draft=readEmergency(state,market);
  if(!draft)throw Error("Nenhum backup local pendente.");
  const file={type:"central-do-casal-emergency-backup",exportedAt:new Date().toISOString(),
    market,uid:state.uid,household:state.household,baseRevision:draft.baseRevision,
    baseline:draft.baseline,operations:draft.operations,baskets:{[market]:draft.basket}};
  const blob=new Blob([JSON.stringify(file,null,2)],{type:"application/json"});
  const link=document.createElement("a"),url=URL.createObjectURL(blob);
  link.href=url;link.download="central-compra-backup-"+market.toLowerCase()+"-"+new Date().toISOString().slice(0,10)+".json";
  document.body.append(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1200);
}
export function restoreBackupControl(state){
  if(!state.uid||!state.household)return "";
  return '<div class="emergency-restore-control"><button type="button" class="item-btn" data-shop-action="emergency-restore">'+
    '↥ Recuperar compra do backup (.json)</button><span>Se o carrinho ficou desatualizado, carregue seu último backup para voltar a ver os preços neste celular.</span></div>';
}
export function hasPreviousEmergency(state,market){
  try{return !!storage().getItem(key(state,market)+":anterior");}catch{return false;}
}
export function downloadPreviousEmergency(state,market) {
  const raw=storage().getItem(key(state,market)+":anterior");
  if(!raw)throw Error("Nenhuma cópia anterior está disponível.");
  const previous=JSON.parse(raw);
  const data={type:"central-do-casal-emergency-backup",exportedAt:new Date().toISOString(),
    market,uid:state.uid,household:state.household,baseRevision:previous.baseRevision,
    baseline:previous.baseline,operations:previous.operations,baskets:{[market]:previous.basket}};
  const blob=new Blob([JSON.stringify(data,null,2)],{type:"application/json"});
  const link=document.createElement("a"),url=URL.createObjectURL(blob);
  link.href=url;link.download="central-backup-anterior-"+market.toLowerCase()+".json";
  document.body.append(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1200);
}
export function emergencyBanner(state,showExports=true) {
  const draft=readEmergency(state,state.market);
  if(!draft){
    return state.cachedBasketOnly?
      '<section class="emergency-banner" role="status"><strong>⚠ Lista recuperada do próprio aparelho</strong>'+
      '<p>O Firebase ainda não confirmou esta lista. Confira os itens antes de continuar e não limpe os dados do navegador.</p></section>':"";
  }
  const spent=draft.basket.entries.filter(e=>e.bought).reduce((sum,e)=>
    sum+Math.round((Number(e.actual)||0)*Number(e.actualMilli??e.plannedMilli??1000)/1000),0);
  const fmt=n=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(n/100);
  const budget=Number(draft.basket.budget)||0;
  return '<section class="emergency-banner" role="status">'+
    '<strong>⚠ Modo local: '+draft.operations.length+' alteração(ões) NÃO sincronizada(s)'+
      (draft.restoredFromFile?" · backup restaurado":"")+'</strong>'+
    '<div class="emergency-live-totals"><span>No carrinho neste aparelho: <strong>'+fmt(spent)+'</strong></span>'+
    '<span>Restante'+(budget?" do orçamento":" (orçamento não definido)")+
    ': <strong>'+(budget?fmt(budget-spent):"não disponível")+'</strong></span></div>'+
    '<p>Os valores acima são desta compra local, independentemente da conexão. Não limpe os dados do navegador e baixe um backup antes de sair.</p>'+
    (draft.syncBlockedReason?'<p class="emergency-sync-warning">⚠ '+escapeHTML(draft.syncBlockedReason)+'</p>':"")+
    '<div class="emergency-buttons">'+
    (showExports?'<button type="button" class="item-btn" data-shop-action="emergency-export">Baixar backup</button>':"")+
    (showExports&&hasPreviousEmergency(state,state.market)?'<button type="button" class="item-btn" data-shop-action="emergency-export-previous">Baixar cópia anterior</button>':"")+
    (!hasOfflineClosurePending(state,state.market)?
      '<button type="button" class="primary-btn" data-shop-action="emergency-sync"'+
        (draft.syncBlockedReason?' disabled title="Exige conferência da versão original"':"")+'>Tentar sincronizar</button>':"")+
      '</div>'+
    '<small>A compra só ficará disponível em outros aparelhos depois da sincronização.</small></section>';
}
// Opções para reconciliar manualmente o diário local com a lista remota.
export function replayEmergency(remote,draft){
  if(!draft.baseline||!Array.isArray(draft.baseline.entries))
    throw Error("Não há cópia da revisão original para conferir as alterações. O backup local foi preservado; não foi enviado ao Firebase.");
  if(remote.generation!==draft.baseGeneration)
    throw Error("A compra foi finalizada em outro aparelho. Baixe seu backup e revise antes de sincronizar.");
  const oldById=new Map((draft.baseline.entries||[]).map(e=>[e.id,e]));
  const nowById=new Map((remote.entries||[]).map(e=>[e.id,e]));
  const changedIds=new Set(draft.operations
    .filter(o=>o.type==="update"||o.type==="remove").map(o=>o.id).filter(Boolean));
  for(const op of draft.operations.filter(o=>o.type==="batchAddToCart")){
    for(const entry of op.entries||[]){
      const baselinePending=(draft.baseline.entries||[]).filter(e=>!e.bought&&
        e.unit===entry.unit&&normalize(e.title)===normalize(entry.title));
      if(baselinePending.length===1)changedIds.add(baselinePending[0].id);
    }
  }
  for(const id of changedIds){
    const baseline=oldById.get(id),latest=nowById.get(id);
    if(JSON.stringify(baseline)!==JSON.stringify(latest))
      throw Error("O produto foi alterado em outro aparelho. Seu backup local está preservado. Confira antes de sincronizar.");
  }
  let next=simple(remote);
  for(const op of draft.operations){
    // Não mutar o backup durante replay.
    next=applyOperation(next,simple(op));
  }
  return next;
}
