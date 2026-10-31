import {writeAppRoute} from "./navigation.js";
import {isQuotaError,readEmergency,saveEmergency,projectedShoppingState,clearEmergency,downloadEmergency,emergencyBanner} from "./emergency-cart.js";
import {showMarketAddDialog} from "./market-add-dialog.js";
import {showInvoiceCheckout} from "./invoice-checkout.js";
import {labelInvoiceStatus} from "./invoice-reconcile.js";
import {filterShoppingEntries,shoppingSearchBar} from "./shopping-search.js";
import {suggestedEstimate,missingEstimateSummary} from "./shopping-estimates.js";
import {presentationSeries} from "./product-presentation.js";
import {confirmCatalogAssociation} from "./catalog-reconcile.js";
import {catalogView} from "./catalog-ui.js";
import {catalogDraft,catalogQuickDraft,alreadyAdded} from "./catalog.js";
import {priceHistory,priceTrend} from "./catalog-history.js";
import {marketView,quantityControls} from "./shopping-market.js";
import {createShoppingActions,UNITS,moneyCents,milliQty,subtotal} from "./shopping.js";

const currency=n=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format((Number(n)||0)/100);
const quantity=n=>(Number(n||1000)/1000).toLocaleString("pt-BR",{maximumFractionDigits:3});
const inputMoney=n=>((Number(n)||0)/100).toFixed(2).replace(".",",");
const clean=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const shopPrefs={mode:"catalog",source:"base",search:"",category:"Todas",limit:40,recoverableOnly:false,quantities:{},marketSection:"pending",listSearch:{Mercado:"",Açougue:""},marketSearch:{Mercado:"",Açougue:""}};
try{
  const remembered=JSON.parse(sessionStorage.getItem("cdc:shopping-filters")||"null");
  if(remembered&&typeof remembered==="object"){
    for(const key of ["search","category"])
      if(typeof remembered[key]==="string")shopPrefs[key]=remembered[key].slice(0,160);
    for(const key of ["listSearch","marketSearch"])
      for(const market of ["Mercado","Açougue"])
        if(typeof remembered[key]?.[market]==="string")
          shopPrefs[key][market]=remembered[key][market].slice(0,160);
  }
}catch{}
function persistShoppingFilters(){
  try{sessionStorage.setItem("cdc:shopping-filters",JSON.stringify({
    search:shopPrefs.search,category:shopPrefs.category,
    listSearch:shopPrefs.listSearch,marketSearch:shopPrefs.marketSearch
  }));}catch{}
}
export function getShoppingRoutePrefs(){
  return {shopMode:shopPrefs.mode,catalogSource:shopPrefs.source,marketSection:shopPrefs.marketSection};
}
export function restoreShoppingRoutePrefs(route){
  shopPrefs.mode=["catalog","list","market"].includes(route?.shopMode)?route.shopMode:"catalog";
  shopPrefs.source=route?.catalogSource==="store"?"store":"base";
  shopPrefs.marketSection=route?.marketSection==="bought"?"bought":"pending";
}

function entryHTML(e,state) {
  const name=clean(e.title||"Produto sem nome");
  const suggestion=e.estimateKnown===false?suggestedEstimate(e,state.shared,state.links||[],state.market):null;
  const warnings=!String(e.title||"").trim()||!UNITS.includes(e.unit)||e.unitReviewed===false||e.quantityReviewed===false;
  const meta=[quantity(e.plannedMilli)+" "+(e.unit||"sem unidade"),e.group||"Essenciais"].join(" · ");
  const unitEstimate=e.estimateKnown===false?
    (suggestion?.cents?("Referência sugerida: "+currency(suggestion.cents)):"Sem preço estimado"):
    ("Estimativa: "+currency(e.estimated));
  const priceNote=e.estimateKnown===false&&suggestion?.cents?
    '<span class="estimate-context">'+clean(suggestion.source)+(suggestion.safe?" · disponível para aplicar":" · exige confirmação")+'</span>':
    e.estimateKnown!==false&&e.unitReviewed===false?
    '<span class="estimate-context">Preço provisório. Confirme unidade e embalagem para calcular o total.</span>':"";
  return `<article class="shopping-item ${e.bought?"purchased":""}" data-shopping-entry-id="${clean(e.id)}">
    <div class="shopping-info"><div class="shopping-title">${name} ${warnings?'<span class="pill warn">Revisar</span>':""}</div>
      <div class="record-meta">${clean(meta)}</div><div class="shopping-price">${clean(unitEstimate)} ${e.bought?`<span class="pill success">Comprado · ${currency(e.actual)}</span>`:""}${priceNote}</div></div>
    <div class="shopping-actions">
      ${quantityControls(e)}
      <button type="button" class="item-btn ${e.bought?"subtle":"buy"}" data-shop-action="toggle" data-item-id="${clean(e.id)}" aria-label="${e.bought?"Desmarcar":"Registrar compra de"} ${name}">${e.bought?"Desfazer":"✓ Comprei"}</button>
      <button type="button" class="item-btn" data-shop-action="edit" data-item-id="${clean(e.id)}">Editar</button>
      <button type="button" class="item-btn danger" data-shop-action="remove-inline" data-item-id="${clean(e.id)}" aria-label="Remover ${name} da lista">Remover</button>
    </div>
  </article>`;
}
export function shoppingView({state}) {
  state=projectedShoppingState(state);
  const market=state.market;
  const options=["Mercado","Açougue"].map(x=>`<button type="button" data-market="${x}" aria-pressed="${x===market}">${x}</button>`).join("");
  const modes=[["catalog","Catálogo"],["list","Minha lista"],["market","No mercado"]].map(([id,label])=>`<button type="button" class="shop-tab ${shopPrefs.mode===id?"selected":""}" data-shop-action="view-${id}" aria-pressed="${shopPrefs.mode===id}">${label}</button>`).join("");
  const viewHeader=`<nav class="shop-mode-tabs" aria-label="Áreas de compras">${modes}</nav>`+emergencyBanner(state);
  // Importante: não analisar notas, gráficos, preços e 50+ itens a cada
  // entrada em No mercado. Tudo abaixo só é necessário em Minha lista.
  if(shopPrefs.mode==="catalog")return viewHeader+catalogView(state,shopPrefs);
  if(shopPrefs.mode==="market")return viewHeader+`<div class="switcher shop-market-switch">${options}</div>`+
    marketView(state,{section:shopPrefs.marketSection,search:shopPrefs.marketSearch[market]||""});
  const b=state.baskets[market]||{market,entries:[],budget:0};
  const entries=Array.isArray(b.entries)?b.entries:[];
  const list=entries.filter(e=>!e.bought);
  const bought=entries.filter(e=>e.bought);
  const listQuery=shopPrefs.listSearch[market]||"";
  const visibleList=filterShoppingEntries(list,listQuery);
  const visibleBought=filterShoppingEntries(bought,listQuery);
  const flagged=entries.filter(e=>!String(e.title||"").trim()||!UNITS.includes(e.unit)||e.unitReviewed===false||e.quantityReviewed===false);
  const priceIssues=missingEstimateSummary(entries,state.shared,state.links||[],market);
  const indicative=entries.filter(e=>e.estimateKnown!==false&&e.unitReviewed===false&&
    Number.isSafeInteger(e.estimated)&&Number.isSafeInteger(e.plannedMilli)&&e.plannedMilli>0)
    .reduce((n,e)=>n+subtotal(e.estimated,e.plannedMilli),0);
  const subtotalExpected=entries.filter(e=>e.estimateKnown!==false&&e.unitReviewed!==false&&e.quantityReviewed!==false&&UNITS.includes(e.unit))
    .reduce((n,e)=>n+subtotal(e.estimated||0,e.plannedMilli||1000),0);
  const actual=bought.reduce((n,e)=>n+subtotal(Number(e.actual)||0,e.actualMilli||e.plannedMilli||1000),0);
  const receipts=state.shared.filter(r=>r.kind==="receipt"&&r.market===market).sort((a,b)=>String(b.date||"").localeCompare(String(a.date||"")));
  const seen=new Map();
  for(const e of entries){
    const k=String(e.title||"").trim().toLowerCase();
    if(k)seen.set(k,(seen.get(k)||0)+1);
  }
  const duplicates=[...seen.entries()].filter(([,n])=>n>1).map(([name])=>name);
  return viewHeader+`<div class="split-head"><div><p class="info-line">Alterações sincronizadas com o Android no mesmo espaço do casal.</p></div><div class="switcher">${options}</div></div>
    ${shoppingSearchBar({area:"list",query:listQuery,total:entries.length,
      shown:visibleList.length+visibleBought.length,label:"Pesquisar na lista e no carrinho"})}
    <section class="shopping-summary">
      <div class="shop-summary-text"><span class="eyebrow">ORÇAMENTO DE ${market.toUpperCase()}</span><h2>${currency(b.budget)}</h2><p>Estimativa conferida: <strong>${currency(subtotalExpected)}</strong> · Referências com unidade a revisar: <strong>${currency(indicative)}</strong> · No carrinho: <strong>${currency(actual)}</strong></p></div>
      <button type="button" data-shop-action="budget" class="item-btn">Alterar orçamento</button>
    </section>
    <div class="stats-bar"><span>${entries.length} produtos</span><span>${list.length} pendentes</span><span>${bought.length} comprados</span>${flagged.length?`<span>${flagged.length} para revisar</span>`:""}</div>
    ${priceIssues.missing?`<section class="estimate-recovery"><div><strong>${priceIssues.missing} produto(s) sem estimativa salva</strong>
    <p>${priceIssues.usable} preço(s) recuperável(is) pela base ou pelo histórico da mesma apresentação.
    ${priceIssues.manual?priceIssues.manual+" requer(em) revisão manual.":""} Preços sugeridos não são preços atuais garantidos.</p></div>
    ${priceIssues.usable?`<button type="button" class="item-btn buy" data-shop-action="fill-estimates">Aplicar ${priceIssues.usable} referência(s)</button>`:""}</section>`:""}
    ${duplicates.length?`<div class="banner warning" style="margin-top:17px">Produtos com nomes repetidos (${clean(duplicates.join(", "))}). Nenhum foi mesclado. Confira as unidades antes de editar.</div>`:""}
    <section class="shop-input-card"><div><h2>Lista de ${market.toLowerCase()}</h2><p>Adicione e marque produtos conforme compra.</p></div><button type="button" class="primary-btn" data-shop-action="add">+ Adicionar produto</button></section>

    <div class="row-list">${visibleList.length?visibleList.map(e=>entryHTML(e,state)).join(""):`<div class="empty">${list.length?"Nenhum item pendente corresponde à pesquisa.":"Nenhum produto pendente. Adicione o que precisa comprar."} ${list.length&&listQuery.trim()?`<button type="button" class="item-btn" data-shop-action="clear-shopping-search" data-search-area="list">Limpar pesquisa</button>`:""}</div>`}</div>
    ${bought.length&&(!listQuery.trim()||visibleBought.length)?`<div class="split-head"><h2 class="section-heading">No carrinho</h2><span class="mini-count">${visibleBought.length}${listQuery.trim()?"/"+bought.length:""}</span></div><div class="row-list">${visibleBought.length?visibleBought.map(e=>entryHTML(e,state)).join(""):`<div class="empty">Nenhum produto do carrinho corresponde à pesquisa.</div>`}</div>`:""}
    <div class="checkout-card"><div><strong>Conferir e concluir esta compra</strong><p>Envie a nota, compare os valores pagos e salve o histórico com o comprovante.</p></div><button type="button" class="primary-btn" data-shop-action="close" ${bought.length?"":"disabled"}>Conferir nota e finalizar</button></div>
    <div class="split-head"><h2 class="section-heading">Histórico de compras</h2><span class="mini-count">${receipts.length}</span></div>
    <p class="info-line">Reutilize os produtos que já compraram. Nada é consolidado automaticamente.</p>
    <div class="row-list">${receipts.length?receipts.slice(0,30).map(r=>{
      const purchased=(r.entries||[]).filter(e=>e.bought);
      const invoice=r.invoice||null;
      const report=invoice?.comparison||null;
      const invoiceInfo=invoice?
        `<div class="receipt-invoice"><strong>${clean(labelInvoiceStatus(report?.status||"pending"))}</strong>
          ${invoice.issuer?`<span>Loja: ${clean(invoice.issuer)}</span>`:""}
          ${invoice.totalCents!=null?`<span>Total da nota: ${currency(invoice.totalCents)}</span>`:""}
          ${report?.differenceCents!=null?`<span>Diferença: ${currency(report.differenceCents)}</span>`:""}
          ${invoice.filename?`<button type="button" class="item-btn" data-shop-action="invoice-download" data-receipt-id="${clean(r.id)}">Baixar comprovante</button>`:""}
          ${report?.lines?.length?`<details class="receipt-invoice-lines"><summary>Ver comparação de cada produto (${report.lines.length})</summary>
          ${report.lines.map(line=>`<div class="receipt-invoice-line"><strong>${clean(line.name)}</strong>
            <span>Nota ${currency(line.noteTotalCents)}${line.cartTotalCents!=null?" · Mercado "+currency(line.cartTotalCents):""}</span>
            <small>${line.status==="matched"?"✓ Preço confere":line.status==="different"?"⚠ Divergência "+currency(line.differenceCents):"Não conferido"}</small>
          </div>`).join("")}</details>`:""}
          <small>Valores documentados pelo arquivo enviado, sem verificação fiscal externa.</small></div>`:
        '<p class="info-line">Compra sem nota fiscal vinculada.</p>';
      return `<details class="panel receipt-detail"><summary><strong>${clean(r.date||"Compra registrada")}</strong> · ${purchased.length} produtos <span>${currency(r.total)}</span></summary>
        ${invoiceInfo}
        <div class="receipt-items">${purchased.map(e=>`<div class="receipt-row"><span>${clean(e.title||"Sem nome")} · ${quantity(e.actualMilli||e.plannedMilli)} ${clean(e.unit)}</span><button type="button" class="item-btn" data-shop-action="repurchase" data-receipt-id="${clean(r.id)}" data-item-id="${clean(e.id)}">Repor</button></div>`).join("")||"<p>Sem produtos marcados.</p>"}</div></details>`;
    }).join(""):`<div class="empty">Ainda não há compras concluídas sincronizadas.</div>`}</div>
    <div class="backup-panel"><div><strong>Compras antigas no Android?</strong><p>Você pode importar as listas de um JSON exportado pelo aplicativo. Itens existentes são preservados e títulos repetidos não são mesclados automaticamente.</p></div><button type="button" class="item-btn" data-shop-action="import">Importar lista antiga</button></div>`;
}
export function installShoppingUI({root,db,auth,getState,render}) {
  const actions=createShoppingActions(db,auth,getState);
  function rememberShoppingRoute(){
    writeAppRoute({tab:"Compras",market:getState().market,...getShoppingRoutePrefs()});
  }
  const dlg=document.createElement("dialog");
  dlg.className="shop-dialog";
  document.body.append(dlg);
  const toast=document.createElement("div");toast.className="toast";toast.role="status";toast.hidden=true;document.body.append(toast);
  let toastTimeout;
  function notify(text,error=false){
    toast.textContent=text;toast.classList.toggle("bad",error);toast.hidden=false;clearTimeout(toastTimeout);
    toastTimeout=setTimeout(()=>{toast.hidden=true;},6000);
  }
  const uploader=document.createElement("input");
  uploader.type="file";uploader.accept="application/json,.json";uploader.hidden=true;document.body.append(uploader);
  uploader.addEventListener("change",async()=>{
    const file=uploader.files?.[0];uploader.value="";
    if(!file)return;
    try {
      if(file.size>10_000_000)throw new Error("Arquivo muito grande. Envie um backup JSON menor que 10 MB.");
      const data=JSON.parse(await file.text());
      if(!data?.baskets||typeof data.baskets!=="object"||Array.isArray(data.baskets))throw new Error("Arquivo não é um backup da Central do Casal com listas.");
      const markets=["Mercado","Açougue"].filter(m=>Array.isArray(data.baskets[m]?.entries)&&data.baskets[m].entries.length);
      if(!markets.length)throw new Error("O backup não contém produtos nas listas de Mercado ou Açougue.");
      const counts=markets.map(m=>m+": "+data.baskets[m].entries.length).join(", ");
      const ok=confirm("IMPORTAR LISTAS DO ANDROID?\n\n"+counts+"\n\nProdutos existentes no Firebase não serão substituídos. Itens com IDs diferentes podem ficar duplicados para você revisar. Continuar?");
      if(!ok)return;
      for(const market of markets) await actions.importBackup(market,data.baskets[market]);
      notify("Listas do backup enviadas ao Firebase. Confira as unidades e possíveis duplicações.");
    }catch(err){notify(err.message||"Falha ao importar backup.",true);}
  });
  let busy=false;
  async function run(job,description,fallback=null){
    if(busy)return;
    busy=true;
    dlg.querySelectorAll("button").forEach(b=>b.disabled=true);
    try{
      if(fallback?.market&&readEmergency(getState(),fallback.market)){
        saveEmergency(getState(),fallback.market,fallback.operation);
        if(fallback.operation.type==="addToCart"){shopPrefs.marketSection="bought";rememberShoppingRoute();}
        dlg.close();render();
        notify(description+" · salvo SOMENTE neste aparelho. Baixe o backup.");
        return;
      }
      await job();dlg.close();notify(description+" · sincronizado no Firebase.");
    }catch(e){
      if(fallback?.market&&isQuotaError(e)){
        try{
          saveEmergency(getState(),fallback.market,fallback.operation);
          if(fallback.operation.type==="addToCart"){shopPrefs.marketSection="bought";rememberShoppingRoute();}
          dlg.close();render();
          notify(description+" · limite do Firebase atingido. Salvo SOMENTE neste aparelho. Faça backup.",true);
          return;
        }catch(saveError){
          notify(saveError.message,true);
        }
      }else{
        notify(isQuotaError(e)?
          "Limite de uso do Firebase atingido. Os dados NÃO foram sincronizados. Anote o preço e use o modo local.":
          (e.message||"Erro na sincronização")+". Verifique a conexão.",true);
      }
      dlg.querySelectorAll("button").forEach(b=>b.disabled=false);
    }finally{busy=false;}
  }
  function editor(item=null,buy=false,seed=null){
    const market=getState().market;
    const e=item||seed||{title:"",unit:"un",plannedMilli:1000,estimated:0,bought:false,group:"Essenciais",estimateKnown:false};
    const purchased=buy||e.bought;
    dlg.innerHTML=`<form id="shopping-editor" method="dialog">
    <div class="dialog-header"><h2>${item?"Editar produto":"Novo produto"}</h2><button type="button" class="item-btn" data-cancel>Fechar</button></div>
    <label>Nome do produto<input required maxlength="200" name="title" autocomplete="off" value="${clean(e.title)}" placeholder="Ex.: Arroz integral"></label>
    <div class="dialog-grid"><label>Quantidade<input required name="qty" inputmode="decimal" value="${quantity(e.plannedMilli)}"></label>
      <label>Unidade<select name="unit" required><option value="" disabled ${!UNITS.includes(e.unit)?"selected":""}>Escolher unidade</option>${UNITS.map(u=>`<option value="${u}" ${e.unit===u?"selected":""}>${u}</option>`).join("")}</select></label></div>
    <div class="dialog-grid"><label>Preço estimado (R$)<input name="estimated" inputmode="decimal" value="${e.estimateKnown===false||!Number.isSafeInteger(e.estimated)||e.estimated===0?"":inputMoney(e.estimated)}" placeholder="Ex.: 12,90"></label>
      <label>Grupo<input name="group" maxlength="60" value="${clean(e.group||"Essenciais")}"></label></div>
    <label class="check-line"><input type="checkbox" name="bought" ${purchased?"checked":""}> Já comprei este produto</label>
    <label class="actual-field" ${purchased?"":"hidden"}>Preço real por unidade (R$)<input name="actual" inputmode="decimal" value="${e.actual==null||(!item&&e.actual===0)?"":inputMoney(e.actual)}" placeholder="Ex.: 12,90"></label>
    <div class="dialog-actions">${item?`<button class="item-btn danger" type="button" data-remove>Excluir produto</button>`:""}<button class="primary-btn" type="submit">Salvar produto</button></div>
    <div class="info-line">A mesma lista é usada no Android. Não alteramos os produtos duplicados sem sua confirmação.</div></form>`;
    dlg.showModal();
    const form=dlg.querySelector("form");
    form.elements.title.focus();
    form.elements.bought.addEventListener("change",()=>{form.querySelector(".actual-field").hidden=!form.elements.bought.checked;});
    dlg.querySelector("[data-cancel]").addEventListener("click",()=>dlg.close());
    dlg.querySelector("[data-remove]")?.addEventListener("click",()=>{if(confirm("Excluir este produto da lista atual?"))run(()=>actions.removeItem(market,item.id),"Produto excluído",
      {market,operation:{type:"remove",id:item.id}});});
    form.addEventListener("submit",event=>{
      event.preventDefault();
      try{
        const q=milliQty(form.elements.qty.value);
        const bought=form.elements.bought.checked;
        const actual=bought?moneyCents(form.elements.actual.value):null;
        const entry={title:form.elements.title.value.trim(),plannedMilli:q,actualMilli:q,unit:form.elements.unit.value,estimated:moneyCents(form.elements.estimated.value||"0"),actual,bought,estimateKnown:form.elements.estimated.value.trim()!=="",unitReviewed:true,quantityReviewed:true,group:form.elements.group.value.trim()||"Essenciais",...(seed?{sourceBaseId:seed.sourceBaseId,sourceSkuId:seed.sourceSkuId,category:seed.category,storageRoom:seed.storageRoom,recipient:seed.recipient,subgroup:seed.subgroup,priority:seed.priority}:{})};
        if(!entry.title)throw new Error("Informe o nome do produto.");
        const offlineEntry=item?{...item,...entry}:{...entry,id:crypto.randomUUID()};
        const operation=item?{type:"update",id:item.id,entry:offlineEntry}:{type:"add",entry:offlineEntry};
        run(()=>item?actions.updateItem(market,item.id,entry,item):actions.addItem(market,entry),
          "Produto salvo",{market,operation});
      }catch(e){notify(e.message,true);}
    });
  }
  let catalogSearchTimer,shoppingSearchTimer;
  root.addEventListener("input",event=>{
    const area=event.target.dataset?.shoppingSearch;
    if(area==="list"||area==="market"){
      const state=getState();
      const prefs=area==="list"?shopPrefs.listSearch:shopPrefs.marketSearch;
      prefs[state.market]=event.target.value;
      persistShoppingFilters();
      const caret=event.target.selectionStart??event.target.value.length;
      clearTimeout(shoppingSearchTimer);
      shoppingSearchTimer=setTimeout(()=>{
        // O campo pode estar em outra aba ou mercado após uma troca rápida.
        if(getState().market!==state.market)return;
        render();
        const field=root.querySelector('[data-shopping-search="'+area+'"]');
        if(field&&document.activeElement!==field){
          field.focus({preventScroll:true});
          try{field.setSelectionRange(caret,caret);}catch{}
        }
      },130);
      return;
    }
    if(event.target.matches("[data-shop-qty]")){
      shopPrefs.quantities[event.target.dataset.qtyKey]=event.target.value;return;
    }
    if(event.target.id!=="shop-catalog-search")return;
    shopPrefs.search=event.target.value;shopPrefs.limit=40;
    persistShoppingFilters();
    const cursor=event.target.selectionStart??shopPrefs.search.length;
    clearTimeout(catalogSearchTimer);
    catalogSearchTimer=setTimeout(()=>{
      render();
      const field=root.querySelector("#shop-catalog-search");
      if(field){field.focus();field.setSelectionRange(cursor,cursor);}
    },140);
  });
  root.addEventListener("change",event=>{
    if(event.target.id==="shop-catalog-category"){shopPrefs.category=event.target.value;shopPrefs.limit=40;persistShoppingFilters();render();}
    if(event.target.matches("[data-catalog-recovery]")){shopPrefs.recoverableOnly=event.target.checked;shopPrefs.limit=40;render();}
  });
  root.addEventListener("click",async event=>{
    const button=event.target.closest("[data-shop-action]");
    if(!button||busy)return;
    const s=projectedShoppingState(getState()),act=button.dataset.shopAction,market=s.market;
    if(act==="emergency-export"){
      try{downloadEmergency(getState(),market);notify("Backup da compra baixado neste aparelho.");}
      catch(e){notify(e.message,true);}
      return;
    }
    if(act==="emergency-sync"){
      const draft=readEmergency(getState(),market);
      if(!draft){notify("Nenhuma alteração local pendente.");render();return;}
      if(!confirm("Enviar "+draft.operations.length+" alteração(ões) locais para o Firebase? Se outro aparelho alterou o mesmo produto, a sincronização será interrompida para preservar seus dados."))return;
      if(busy)return;busy=true;
      try{
        await actions.synchronizeEmergency(market,draft);
        clearEmergency(getState(),market);render();
        notify("Alterações locais sincronizadas. Confira o carrinho atualizado.");
      }catch(e){
        notify(isQuotaError(e)?"O Firebase continua com a cota excedida. Seu backup local foi preservado. Tente sincronizar depois.":(e.message||"Não foi possível sincronizar. Seu backup está preservado."),true);
      }finally{busy=false;}
      return;
    }
    if(act==="clear-shopping-search"){
      const area=button.dataset.searchArea;
      if(area==="list"||area==="market"){
        const prefs=area==="list"?shopPrefs.listSearch:shopPrefs.marketSearch;
        prefs[market]="";
        persistShoppingFilters();
        clearTimeout(shoppingSearchTimer);
        render();
        root.querySelector('[data-shopping-search="'+area+'"]')?.focus({preventScroll:true});
      }
      return;
    }
    if(act==="market-section-pending"||act==="market-section-bought"){
      shopPrefs.marketSection=act==="market-section-bought"?"bought":"pending";
      rememberShoppingRoute();
      render();
      return;
    }
    if(act.startsWith("view-")){
      shopPrefs.mode=act.slice(5);
      rememberShoppingRoute();
      render();
      window.scrollTo({top:0,behavior:"auto"});
      return;
    }
    if(act==="source-base"||act==="source-store"){
      shopPrefs.source=act==="source-base"?"base":"store";
      shopPrefs.category="Todas";shopPrefs.limit=40;
      persistShoppingFilters();
      rememberShoppingRoute();
      render();
      return;
    }
    if(act==="catalog-qty-step"){
      const wrap=button.closest(".catalog-qty-control");
      const field=wrap?.querySelector("[data-shop-qty]");
      if(!field)return;
      const unit=wrap.dataset.qtyUnit;
      const increment=["kg","L"].includes(unit)?500:["g","mL"].includes(unit)?100000:1000;
      let previous;
      try{previous=milliQty(field.value);}catch{previous=increment;}
      const next=previous+Number(button.dataset.delta)*increment;
      if(next<=0){notify("A quantidade precisa ser maior que zero.");return;}
      if(next>100000000){notify("Quantidade acima do limite. Use uma quantidade menor.",true);return;}
      field.value=(next/1000).toLocaleString("pt-BR",{maximumFractionDigits:3});
      shopPrefs.quantities[field.dataset.qtyKey]=field.value;
      return;
    }
    if(act==="show-more"){shopPrefs.limit+=40;render();return;}
    if(!s.household){notify("Você precisa entrar no mesmo espaço compartilhado do Android para editar.",true);return;}
    if(act==="market-add-cart"){
      showMarketAddDialog({dialog:dlg,state:s,notify,
        onSubmit:entry=>run(async()=>{
          await actions.addToCart(market,entry);
          shopPrefs.marketSection="bought";
          rememberShoppingRoute();
          render();
        },"Produto registrado diretamente no carrinho",
        {market,operation:{type:"addToCart",entry}})});
      return;
    }
    const item=(s.baskets[market]?.entries||[]).find(e=>e.id===button.dataset.itemId);
    if(act==="confirm-association"){
      const base=s.shared.find(x=>x.id===button.dataset.baseId&&x.kind==="catalogItem");
      const sku=s.shared.find(x=>x.id===button.dataset.skuId&&x.kind==="catalogSku");
      if(!base||!sku){notify("Produto não encontrado. Atualize a página.",true);return;}
      const history=priceHistory(sku,s.shared,s.links||[]);
      const value=history.at(-1);
      const message="Confirme somente se for o MESMO produto, considerando embalagem e unidade.\\n\\n"
        +"Sua base: "+base.title+" ("+(base.category||"sem categoria")+")\\n"
        +"Nota / loja: "+sku.title+" ("+(sku.store||"loja não informada")+")\\n"
        +"Registros encontrados: "+history.length+(value?"\\nÚltimo: "+(value.cents/100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"})+"/"+value.unit:"")
        +"\\n\\nEsta confirmação associará os históricos ao produto na sua base, inclusive no Android. Os recibos originais serão preservados. Continuar?";
      if(confirm(message))run(()=>confirmCatalogAssociation(db,auth,getState,base.id,sku.id),"Histórico vinculado à sua base");
      return;
    }
    if(act==="add-package"){
      const sku=s.shared.find(x=>x.id===button.dataset.skuId&&x.kind==="catalogSku");
      if(!sku){notify("Esta apresentação não foi encontrada na base. Atualize a página.",true);return;}
      const packageGroup=presentationSeries(priceHistory(sku,s.shared,s.links||[]))
        .find(g=>g.key===button.dataset.packageKey);
      if(!packageGroup||!packageGroup.points.length){notify("Histórico desta embalagem não encontrado.",true);return;}
      const last=packageGroup.points.at(-1);
      const display=packageGroup.presentation?.display||"Apresentação não identificada";
      const compact=x=>String(x||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().replace(/[^a-z0-9]/g,"");
      const title=compact(sku.title).includes(compact(display))?sku.title:sku.title+" · "+display;
      if((s.baskets[market]?.entries||[]).some(e=>e.sourceSkuId===sku.id&&compact(e.title)===compact(title))){
        notify("Esta apresentação já está na lista. Ajuste a quantidade em Minha lista.");
        return;
      }
      const field=button.closest(".package-card")?.querySelector("[data-shop-qty]");
      let chosenQuantity;
      try{chosenQuantity=milliQty(field?.value||"1");}
      catch(err){notify("Quantidade inválida: "+err.message,true);field?.focus();return;}
      const draft=catalogQuickDraft(sku);
      Object.assign(draft,{title,unit:last.unit,unitReviewed:true,plannedMilli:chosenQuantity,
        actualMilli:chosenQuantity,quantityReviewed:true,
        estimated:Number.isFinite(last.cents)?Math.round(last.cents):0,
        estimateKnown:Number.isFinite(last.cents)&&last.cents>=0,
        sourceSkuId:sku.id});
      run(()=>actions.addItem(market,draft),"Apresentação adicionada à lista",
        {market,operation:{type:"add",entry:{...draft,id:crypto.randomUUID(),bought:false,actual:null}}});
      return;
    }
    if(act==="choose-catalog"){
      const p=s.shared.find(p=>p.id===button.dataset.catalogId&&(p.kind==="catalogSku"||p.kind==="catalogItem"));
      if(!p){notify("Produto não encontrado na base. Atualize a página.",true);return;}
      if(alreadyAdded(p,s.baskets[market]?.entries||[])){notify("Este produto já está na lista. Use + e − em Minha lista para ajustar a quantidade.");return;}
      const field=button.closest(".catalog-card")?.querySelector(":scope > .catalog-qty-control [data-shop-qty]");
      let chosenQuantity;
      try{chosenQuantity=milliQty(field?.value||"1");}
      catch(err){notify("Quantidade inválida: "+err.message,true);field?.focus();return;}
      const d=catalogQuickDraft(p);
      d.plannedMilli=chosenQuantity;d.actualMilli=chosenQuantity;d.quantityReviewed=true;
      const estimate=suggestedEstimate(d,s.shared,s.links||[],market);
      if(estimate?.safe){d.estimated=estimate.cents;d.estimateKnown=true;}
      run(()=>actions.addItem(market,d),"Produto adicionado à lista",
        {market,operation:{type:"add",entry:{...d,id:crypto.randomUUID(),bought:false,actual:null}}});
      return;
    }
    if(act==="fill-estimates"){
      const summary=missingEstimateSummary(s.baskets[market]?.entries||[],s.shared,s.links||[],market);
      if(!summary.usable){notify("Nenhum preço ausente com origem confiável foi encontrado.");return;}
      const preview=summary.suggestions.slice(0,8).map(x=>x.title+": "+currency(x.cents)+" · "+x.source).join("\\n");
      const message="Aplicar "+summary.usable+" preço(s) de referência à lista "+market+"?\\n\\n"+
        preview+(summary.usable>8?"\\n... e mais "+(summary.usable-8)+" produto(s).":"")+
        "\\n\\nSão referências históricas, não cotações atuais. Preços reais, quantidades e valores já informados serão preservados.";
      if(confirm(message))
        run(()=>actions.fillMissingEstimates(market,summary.suggestions),"Referências de preço recuperadas");
      return;
    }
    if(act==="remove-inline"&&item){
      const wasBought=item.bought?" (já está no carrinho)":"";
      if(confirm('Remover "'+item.title+'"'+wasBought+' da lista de '+market+'? O produto continua no catálogo e nas compras antigas.')){
        run(()=>actions.removeItem(market,item.id),"Produto removido da lista",
          {market,operation:{type:"remove",id:item.id}});
      }
      return;
    }
    if(act==="quick-qty"&&item){
      const field=button.dataset.qtyField==="actual"?"actual":"planned";
      const step=(item.unit==="kg"||item.unit==="L")?500:(item.unit==="g"||item.unit==="mL")?100000:1000;
      const previous=Number(field==="actual"?(item.actualMilli??item.plannedMilli):item.plannedMilli);
      const next=previous+Number(button.dataset.delta)*step;
      if(!Number.isSafeInteger(next)||next<=0||next>100000000){notify("Quantidade fora do limite. Use Editar para informar uma quantidade exata.",true);return;}
      const update=field==="actual"?{...item,actualMilli:next}:{...item,plannedMilli:next,actualMilli:item.bought?(item.actualMilli??item.plannedMilli):next};
      run(()=>actions.updateItem(market,item.id,update,item),"Quantidade atualizada",
        {market,operation:{type:"update",id:item.id,entry:update}});return;
    }
    if(["buy-inline","price-save","unbuy-inline"].includes(act)&&item){
      const priceInput=[...root.querySelectorAll("[data-market-price-id]")].find(input=>input.dataset.marketPriceId===item.id);
      if(act==="unbuy-inline"){const next={...item,bought:false,actual:null};
        run(()=>actions.updateItem(market,item.id,next,item),"Produto voltou para Falta comprar",
          {market,operation:{type:"update",id:item.id,entry:next}});
        return;}
      if(!priceInput||!priceInput.value.trim()){notify("Informe o preço real antes de colocar o produto no carrinho.",true);priceInput?.focus();return;}
      try{
        const price=moneyCents(priceInput.value);
        const next={...item,bought:true,actual:price};
        run(()=>actions.updateItem(market,item.id,next,item),
          act==="buy-inline"?"Produto movido de Falta comprar para No carrinho":"Preço do carrinho atualizado",
          {market,operation:{type:"update",id:item.id,entry:next}});
      }catch(e){notify(e.message,true);priceInput.focus();}
      return;
    }
    if(act==="import"){uploader.click();return;}
    if(act==="add")editor();
    if(act==="edit"&&item)editor(item);
    if(act==="toggle"&&item){if(item.bought){
      const next={...item,bought:false,actual:null};
      run(()=>actions.updateItem(market,item.id,next,item),"Compra desmarcada",
        {market,operation:{type:"update",id:item.id,entry:next}});
    }else editor(item,true);}
    if(act==="budget"){
      const entry=prompt("Novo orçamento de "+market+" (R$)",inputMoney(s.baskets[market]?.budget||0));
      if(entry!==null){try{const budget=moneyCents(entry);run(()=>actions.changeBudget(market,budget),"Orçamento atualizado");}catch(e){notify(e.message,true);}}
    }
    if(act==="close"){
      if(readEmergency(getState(),market)){
        notify("Há compras ainda não sincronizadas. Baixe o backup e use Tentar sincronizar antes de finalizar a compra.",true);
        return;
      }
      const b=s.baskets[market];
      if(!b?.entries?.some(e=>e.bought))return;
      showInvoiceCheckout({dialog:dlg,state:s,notify,onFinish:(invoice,mapping)=>
        run(()=>actions.closeBasket(market,invoice,mapping),"Compra e conferência salvas no histórico")
      });
      return;
    }
    if(act==="invoice-download"){
      const receipt=s.shared.find(r=>r.kind==="receipt"&&r.id===button.dataset.receiptId);
      const invoice=receipt?.invoice;
      if(!invoice){notify("Nenhum comprovante encontrado.",true);return;}
      const a=document.createElement("a");
      if(typeof invoice.xmlContent==="string"&&invoice.xmlContent.length){
        const blob=new Blob([invoice.xmlContent],{type:"application/xml"});
        a.href=URL.createObjectURL(blob);
      }else if(typeof invoice.fileData==="string"&&/^data:(image\/jpeg|application\/pdf);base64,/.test(invoice.fileData)){
        a.href=invoice.fileData;
      }else{notify("O arquivo original não está disponível neste registro.",true);return;}
      a.download=String(invoice.filename||"nota-fiscal").replace(/[^a-zA-Z0-9._-]/g,"_");
      document.body.append(a);a.click();a.remove();
      if(a.href.startsWith("blob:"))setTimeout(()=>URL.revokeObjectURL(a.href),1500);
      return;
    }
    if(act==="repurchase"){
      const entry=(s.shared.find(r=>r.id===button.dataset.receiptId&&r.kind==="receipt")?.entries||[]).find(e=>e.id===button.dataset.itemId);
      if(entry&&confirm("Adicionar "+entry.title+" de volta à lista de "+market+"?"))
        run(()=>actions.repurchase(market,button.dataset.receiptId,button.dataset.itemId),"Produto adicionado novamente");
    }
  });
}
