import {confirmCatalogAssociation} from "./catalog-reconcile.js";
import {catalogView} from "./catalog-ui.js";
import {catalogDraft} from "./catalog.js";
import {priceHistory,priceTrend} from "./catalog-history.js";
import {marketView,quantityControls} from "./shopping-market.js";
import {createShoppingActions,UNITS,moneyCents,milliQty,subtotal} from "./shopping.js";

const currency=n=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format((Number(n)||0)/100);
const quantity=n=>(Number(n||1000)/1000).toLocaleString("pt-BR",{maximumFractionDigits:3});
const inputMoney=n=>((Number(n)||0)/100).toFixed(2).replace(".",",");
const clean=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const shopPrefs={mode:"catalog",source:"base",search:"",category:"Todas",limit:40};
function entryHTML(e) {
  const name=clean(e.title||"Produto sem nome");
  const warnings=!String(e.title||"").trim()||!UNITS.includes(e.unit)||e.unitReviewed===false||e.quantityReviewed===false;
  const meta=[quantity(e.plannedMilli)+" "+(e.unit||"sem unidade"),e.group||"Essenciais"].join(" · ");
  const unitEstimate=e.estimateKnown===false?"Sem preço estimado":currency(e.estimated);
  return `<article class="shopping-item ${e.bought?"purchased":""}">
    <div class="shopping-info"><div class="shopping-title">${name} ${warnings?'<span class="pill warn">Revisar</span>':""}</div>
      <div class="record-meta">${clean(meta)}</div><div class="shopping-price">${clean(unitEstimate)} ${e.bought?`<span class="pill success">Comprado · ${currency(e.actual)}</span>`:""}</div></div>
    <div class="shopping-actions">
      ${quantityControls(e)}
      <button type="button" class="item-btn ${e.bought?"subtle":"buy"}" data-shop-action="toggle" data-item-id="${clean(e.id)}" aria-label="${e.bought?"Desmarcar":"Registrar compra de"} ${name}">${e.bought?"Desfazer":"✓ Comprei"}</button>
      <button type="button" class="item-btn" data-shop-action="edit" data-item-id="${clean(e.id)}">Editar</button>
    </div>
  </article>`;
}
export function shoppingView({state}) {
  const market=state.market;
  const b=state.baskets[market]||{market,entries:[],budget:0};
  const entries=Array.isArray(b.entries)?b.entries:[];
  const list=entries.filter(e=>!e.bought);
  const bought=entries.filter(e=>e.bought);
  const flagged=entries.filter(e=>!String(e.title||"").trim()||!UNITS.includes(e.unit)||e.unitReviewed===false||e.quantityReviewed===false);
  const subtotalExpected=entries.filter(e=>e.estimateKnown!==false&&e.unitReviewed!==false&&e.quantityReviewed!==false&&UNITS.includes(e.unit)).reduce((n,e)=>n+subtotal(e.estimated||0,e.plannedMilli||1000),0);
  const actual=bought.reduce((n,e)=>n+subtotal(Number(e.actual)||0,e.actualMilli||e.plannedMilli||1000),0);
  const receipts=state.shared.filter(r=>r.kind==="receipt"&&r.market===market).sort((a,b)=>String(b.date||"").localeCompare(String(a.date||"")));
  const options=["Mercado","Açougue"].map(x=>`<button type="button" data-market="${x}" aria-pressed="${x===market}">${x}</button>`).join("");
  const seen=new Map();for(const e of entries){const k=String(e.title||"").trim().toLowerCase();if(k)seen.set(k,(seen.get(k)||0)+1);}
  const duplicates=[...seen.entries()].filter(([,n])=>n>1).map(([name])=>name);
  const modes=[["catalog","Catálogo"],["list","Minha lista"],["market","No mercado"]].map(([id,label])=>`<button type="button" class="shop-tab ${shopPrefs.mode===id?"selected":""}" data-shop-action="view-${id}" aria-pressed="${shopPrefs.mode===id}">${label}</button>`).join("");
  const viewHeader=`<nav class="shop-mode-tabs" aria-label="Áreas de compras">${modes}</nav>`;
  if(shopPrefs.mode==="catalog")return viewHeader+catalogView(state,shopPrefs);
  if(shopPrefs.mode==="market")return viewHeader+`<div class="switcher shop-market-switch">${options}</div>`+marketView(state);
  return viewHeader+`<div class="split-head"><div><p class="info-line">Alterações sincronizadas com o Android no mesmo espaço do casal.</p></div><div class="switcher">${options}</div></div>
    <section class="shopping-summary">
      <div class="shop-summary-text"><span class="eyebrow">ORÇAMENTO DE ${market.toUpperCase()}</span><h2>${currency(b.budget)}</h2><p>Estimativa: <strong>${currency(subtotalExpected)}</strong> · Já comprado: <strong>${currency(actual)}</strong></p></div>
      <button type="button" data-shop-action="budget" class="item-btn">Alterar orçamento</button>
    </section>
    <div class="stats-bar"><span>${entries.length} produtos</span><span>${list.length} pendentes</span><span>${bought.length} comprados</span>${flagged.length?`<span>${flagged.length} para revisar</span>`:""}</div>
    ${duplicates.length?`<div class="banner warning" style="margin-top:17px">Produtos com nomes repetidos (${clean(duplicates.join(", "))}). Nenhum foi mesclado. Confira as unidades antes de editar.</div>`:""}
    <section class="shop-input-card"><div><h2>Lista de ${market.toLowerCase()}</h2><p>Adicione e marque produtos conforme compra.</p></div><button type="button" class="primary-btn" data-shop-action="add">+ Adicionar produto</button></section>
    <div class="row-list">${list.length?list.map(entryHTML).join(""):`<div class="empty">Nenhum produto pendente. Adicione o que precisa comprar.</div>`}</div>
    ${bought.length?`<div class="split-head"><h2 class="section-heading">No carrinho</h2><span class="mini-count">${bought.length}</span></div><div class="row-list">${bought.map(entryHTML).join("")}</div>`:""}
    <div class="checkout-card"><div><strong>Concluir esta compra</strong><p>Guarde o comprovante no histórico e reabra a lista com os últimos preços.</p></div><button type="button" class="primary-btn" data-shop-action="close" ${bought.length?"":"disabled"}>Finalizar compra</button></div>
    <div class="split-head"><h2 class="section-heading">Histórico de compras</h2><span class="mini-count">${receipts.length}</span></div>
    <p class="info-line">Reutilize os produtos que já compraram. Nada é consolidado automaticamente.</p>
    <div class="row-list">${receipts.length?receipts.slice(0,30).map(r=>{
      const purchased=(r.entries||[]).filter(e=>e.bought);
      return `<details class="panel receipt-detail"><summary><strong>${clean(r.date||"Compra registrada")}</strong> · ${purchased.length} produtos <span>${currency(r.total)}</span></summary><div class="receipt-items">${purchased.map(e=>`<div class="receipt-row"><span>${clean(e.title||"Sem nome")} · ${quantity(e.actualMilli||e.plannedMilli)} ${clean(e.unit)}</span><button type="button" class="item-btn" data-shop-action="repurchase" data-receipt-id="${clean(r.id)}" data-item-id="${clean(e.id)}">Repor</button></div>`).join("")||"<p>Sem produtos marcados.</p>"}</div></details>`;
    }).join(""):`<div class="empty">Ainda não há compras concluídas sincronizadas.</div>`}</div>
    <div class="backup-panel"><div><strong>Compras antigas no Android?</strong><p>Você pode importar as listas de um JSON exportado pelo aplicativo. Itens existentes são preservados e títulos repetidos não são mesclados automaticamente.</p></div><button type="button" class="item-btn" data-shop-action="import">Importar lista antiga</button></div>`;
}
export function installShoppingUI({root,db,auth,getState,render}) {
  const actions=createShoppingActions(db,auth,getState);
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
  async function run(job,description){
    if(busy)return;
    busy=true;
    dlg.querySelectorAll("button").forEach(b=>b.disabled=true);
    try{await job();dlg.close();notify(description+" · sincronizado no Firebase.");}
    catch(e){notify((e.message||"Erro na sincronização")+" Verifique a conexão e tente novamente.",true);
      dlg.querySelectorAll("button").forEach(b=>b.disabled=false);}
    finally{busy=false;}
  }
  function editor(item=null,buy=false,seed=null){
    const market=getState().market;
    const e=item||seed||{title:"",unit:"un",plannedMilli:1000,estimated:0,bought:false,group:"Essenciais",estimateKnown:true};
    const purchased=buy||e.bought;
    dlg.innerHTML=`<form id="shopping-editor" method="dialog">
    <div class="dialog-header"><h2>${item?"Editar produto":"Novo produto"}</h2><button type="button" class="item-btn" data-cancel>Fechar</button></div>
    <label>Nome do produto<input required maxlength="200" name="title" autocomplete="off" value="${clean(e.title)}" placeholder="Ex.: Arroz integral"></label>
    <div class="dialog-grid"><label>Quantidade<input required name="qty" inputmode="decimal" value="${quantity(e.plannedMilli)}"></label>
      <label>Unidade<select name="unit" required><option value="" disabled ${!UNITS.includes(e.unit)?"selected":""}>Escolher unidade</option>${UNITS.map(u=>`<option value="${u}" ${e.unit===u?"selected":""}>${u}</option>`).join("")}</select></label></div>
    <div class="dialog-grid"><label>Preço estimado (R$)<input name="estimated" inputmode="decimal" value="${e.estimateKnown===false?"":inputMoney(e.estimated)}"></label>
      <label>Grupo<input name="group" maxlength="60" value="${clean(e.group||"Essenciais")}"></label></div>
    <label class="check-line"><input type="checkbox" name="bought" ${purchased?"checked":""}> Já comprei este produto</label>
    <label class="actual-field" ${purchased?"":"hidden"}>Preço real por unidade (R$)<input name="actual" inputmode="decimal" value="${e.actual==null?"":inputMoney(e.actual)}" placeholder="Ex.: 12,90"></label>
    <div class="dialog-actions">${item?`<button class="item-btn danger" type="button" data-remove>Excluir produto</button>`:""}<button class="primary-btn" type="submit">Salvar produto</button></div>
    <div class="info-line">A mesma lista é usada no Android. Não alteramos os produtos duplicados sem sua confirmação.</div></form>`;
    dlg.showModal();
    const form=dlg.querySelector("form");
    form.elements.title.focus();
    form.elements.bought.addEventListener("change",()=>{form.querySelector(".actual-field").hidden=!form.elements.bought.checked;});
    dlg.querySelector("[data-cancel]").addEventListener("click",()=>dlg.close());
    dlg.querySelector("[data-remove]")?.addEventListener("click",()=>{if(confirm("Excluir este produto da lista atual?"))run(()=>actions.removeItem(market,item.id),"Produto excluído");});
    form.addEventListener("submit",event=>{
      event.preventDefault();
      try{
        const q=milliQty(form.elements.qty.value);
        const bought=form.elements.bought.checked;
        const actual=bought?moneyCents(form.elements.actual.value):null;
        const entry={title:form.elements.title.value.trim(),plannedMilli:q,actualMilli:q,unit:form.elements.unit.value,estimated:moneyCents(form.elements.estimated.value||"0"),actual,bought,estimateKnown:form.elements.estimated.value.trim()!=="",unitReviewed:true,quantityReviewed:true,group:form.elements.group.value.trim()||"Essenciais",...(seed?{sourceBaseId:seed.sourceBaseId,sourceSkuId:seed.sourceSkuId,category:seed.category,storageRoom:seed.storageRoom,recipient:seed.recipient,subgroup:seed.subgroup,priority:seed.priority}:{})};
        if(!entry.title)throw new Error("Informe o nome do produto.");
        run(()=>item?actions.updateItem(market,item.id,entry):actions.addItem(market,entry),"Produto salvo");
      }catch(e){notify(e.message,true);}
    });
  }
  let catalogSearchTimer;
  root.addEventListener("input",event=>{
    if(event.target.id!=="shop-catalog-search")return;
    shopPrefs.search=event.target.value;shopPrefs.limit=40;
    const cursor=event.target.selectionStart??shopPrefs.search.length;
    clearTimeout(catalogSearchTimer);
    catalogSearchTimer=setTimeout(()=>{
      render();
      const field=root.querySelector("#shop-catalog-search");
      if(field){field.focus();field.setSelectionRange(cursor,cursor);}
    },140);
  });
  root.addEventListener("change",event=>{
    if(event.target.id==="shop-catalog-category"){shopPrefs.category=event.target.value;shopPrefs.limit=40;render();}
  });
  root.addEventListener("click",event=>{
    const button=event.target.closest("[data-shop-action]");
    if(!button||busy)return;
    const s=getState(),act=button.dataset.shopAction,market=s.market;
    if(act.startsWith("view-")){shopPrefs.mode=act.slice(5);render();return;}
    if(act==="source-base"||act==="source-store"){shopPrefs.source=act==="source-base"?"base":"store";shopPrefs.category="Todas";shopPrefs.limit=40;render();return;}
    if(act==="show-more"){shopPrefs.limit+=40;render();return;}
    if(!s.household){notify("Você precisa entrar no mesmo espaço compartilhado do Android para editar.",true);return;}
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
    if(act==="choose-catalog"){
      const p=s.shared.find(p=>p.id===button.dataset.catalogId&&(p.kind==="catalogSku"||p.kind==="catalogItem"));
      if(!p){notify("Produto não encontrado na base. Atualize a página.",true);return;}
      const d=catalogDraft(p);
      const trend=priceTrend(priceHistory(p,s.shared,s.links||[]));
      if(trend.points.length){
        const recent=trend.points[trend.points.length-1];
        d.unit=recent.unit;d.unitReviewed=true;d.estimated=recent.cents;d.estimateKnown=true;
      }
      editor(null,false,d);return;
    }
    if(act==="quick-qty"&&item){
      const field=button.dataset.qtyField==="actual"?"actual":"planned";
      const step=(item.unit==="kg"||item.unit==="L")?500:(item.unit==="g"||item.unit==="mL")?100000:1000;
      const previous=Number(field==="actual"?(item.actualMilli??item.plannedMilli):item.plannedMilli);
      const next=previous+Number(button.dataset.delta)*step;
      if(!Number.isSafeInteger(next)||next<=0||next>100000000){notify("Quantidade fora do limite. Use Editar para informar uma quantidade exata.",true);return;}
      const update=field==="actual"?{...item,actualMilli:next}:{...item,plannedMilli:next,actualMilli:item.bought?(item.actualMilli??item.plannedMilli):next};
      run(()=>actions.updateItem(market,item.id,update),"Quantidade atualizada");return;
    }
    if(["buy-inline","price-save","unbuy-inline"].includes(act)&&item){
      const priceInput=[...root.querySelectorAll("[data-market-price-id]")].find(input=>input.dataset.marketPriceId===item.id);
      if(act==="unbuy-inline"){run(()=>actions.updateItem(market,item.id,{...item,bought:false,actual:null}),"Produto devolvido à lista");return;}
      if(!priceInput||!priceInput.value.trim()){notify("Informe o preço real antes de colocar o produto no carrinho.",true);priceInput?.focus();return;}
      try{
        const price=moneyCents(priceInput.value);
        run(()=>actions.updateItem(market,item.id,{...item,bought:true,actual:price}),act==="buy-inline"?"Produto colocado no carrinho":"Preço do carrinho atualizado");
      }catch(e){notify(e.message,true);priceInput.focus();}
      return;
    }
    if(act==="import"){uploader.click();return;}
    if(act==="add")editor();
    if(act==="edit"&&item)editor(item);
    if(act==="toggle"&&item){if(item.bought)run(()=>actions.updateItem(market,item.id,{...item,bought:false,actual:null}),"Compra desmarcada");else editor(item,true);}
    if(act==="budget"){
      const entry=prompt("Novo orçamento de "+market+" (R$)",inputMoney(s.baskets[market]?.budget||0));
      if(entry!==null){try{const budget=moneyCents(entry);run(()=>actions.changeBudget(market,budget),"Orçamento atualizado");}catch(e){notify(e.message,true);}}
    }
    if(act==="close"){
      const b=s.baskets[market];
      if(!b?.entries?.some(e=>e.bought))return;
      if(confirm("Finalizar compra de "+market+"? Isso salva o histórico e inicia nova lista. Não é possível desfazer automaticamente."))
        run(()=>actions.closeBasket(market),"Compra registrada no histórico");
    }
    if(act==="repurchase"){
      const entry=(s.shared.find(r=>r.id===button.dataset.receiptId&&r.kind==="receipt")?.entries||[]).find(e=>e.id===button.dataset.itemId);
      if(entry&&confirm("Adicionar "+entry.title+" de volta à lista de "+market+"?"))
        run(()=>actions.repurchase(market,button.dataset.receiptId,button.dataset.itemId),"Produto adicionado novamente");
    }
  });
}
