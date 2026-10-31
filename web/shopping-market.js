import {marketTotals} from "./market-totals.js";
import {filterShoppingEntries,shoppingSearchBar} from "./shopping-search.js";
import {suggestedEstimate} from "./shopping-estimates.js";
import {subtotal} from "./shopping.js";
const money=c=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format((Number(c)||0)/100);
const qty=m=>(Number(m??1000)/1000).toLocaleString("pt-BR",{maximumFractionDigits:3});
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
export function quantityControls(item,marketMode=false) {
  const field=marketMode&&item.bought?"actual":"planned";
  const q=field==="actual"?(item.actualMilli??item.plannedMilli):item.plannedMilli;
  return `<div class="qty-controls" aria-label="Quantidade de ${esc(item.title)}">
    <button type="button" data-shop-action="quick-qty" data-item-id="${esc(item.id)}" data-qty-field="${field}" data-delta="-1" aria-label="Diminuir quantidade de ${esc(item.title)}">−</button>
    <span class="qty-value">${qty(q)} <small>${esc(item.unit||"")}</small></span>
    <button type="button" data-shop-action="quick-qty" data-item-id="${esc(item.id)}" data-qty-field="${field}" data-delta="1" aria-label="Aumentar quantidade de ${esc(item.title)}">+</button>
  </div>`;
}
function marketRow(e,state,priceDrafts={}) {
  const title=esc(e.title||"Produto sem nome");
  const suggested=e.estimateKnown===false?suggestedEstimate(e,state.shared,state.links||[],state.market):null;
  const ready=typeof e.unit==="string"&&e.unit!=="Escolher"&&e.unitReviewed!==false&&e.quantityReviewed!==false;
  const bought=e.bought;
  const price=Object.prototype.hasOwnProperty.call(priceDrafts,e.id)?priceDrafts[e.id]:
    (!bought||e.actual==null?"":(Number(e.actual)/100).toFixed(2).replace(".",","));
  const group=esc(e.group||e.category||"Essenciais");
  const realQty=Number(e.actualMilli??e.plannedMilli??1000);
  return `<article class="market-row ${bought?"done":""}" data-shopping-entry-id="${esc(e.id)}">
    <div class="market-row-head"><div><strong>${title}</strong><span>${group}${ready?"":" · Revisar unidade"}</span></div><div class="market-card-menu"><button type="button" class="item-btn" data-shop-action="edit" data-item-id="${esc(e.id)}">Detalhes</button><button type="button" class="item-btn danger" data-shop-action="remove-inline" data-item-id="${esc(e.id)}">Remover</button></div></div>
    <div class="market-purchase-line">${quantityControls(e,true)}
      <label class="market-price">Preço real por ${esc(e.unit||"un")} (R$)
        <input aria-label="Preço real de ${title}" data-market-price-id="${esc(e.id)}" inputmode="decimal" placeholder="Digite o preço" value="${esc(price)}" autocomplete="off">
      </label></div>
    <div class="market-row-foot"><span>${bought?`Total no carrinho: <strong>${money(subtotal(e.actual||0,realQty))}</strong>`:e.estimateKnown===false?(suggested?.cents?`Referência sugerida: ${money(suggested.cents)}/${esc(e.unit)} · confirmar`:"Sem preço de referência"):`Referência: ${money(e.estimated||0)}/${esc(e.unit)}`}</span>
      ${bought?`<div class="market-row-buttons"><button type="button" class="item-btn" data-shop-action="unbuy-inline" data-item-id="${esc(e.id)}">Retirar do carrinho</button><button type="button" class="item-btn buy" data-shop-action="price-save" data-item-id="${esc(e.id)}">Salvar preço</button></div>`:
      `<button type="button" class="primary-btn" data-shop-action="buy-inline" data-item-id="${esc(e.id)}">✓ Colocar no carrinho</button>`}
    </div>
  </article>`;
}
export function marketView(state,{section="pending",search="",priceDrafts={}}={}) {
  const entries=state.baskets[state.market]?.entries||[];
  const remaining=entries.filter(e=>!e.bought),bought=entries.filter(e=>e.bought);
  const visibleRemaining=filterShoppingEntries(remaining,search);
  const visibleBought=filterShoppingEntries(bought,search);
  const total=entries.length;
  const parsedDrafts={};
  for(const e of entries)if(Object.prototype.hasOwnProperty.call(priceDrafts,e.id)){
    const value=String(priceDrafts[e.id]).trim().replace(/^R\$\s*/,"");
    const pattern=/^\d{1,7}(?:[.,]\d{1,2})?$/;
    if(pattern.test(value)){
      const decimals=value.replace(",",".");
      parsedDrafts[e.id]=Math.round(Number(decimals)*100);
    }
  }
  const totals=marketTotals(state.baskets[state.market]||{},parsedDrafts);
  const {spent,budget}=totals;
  const active=section==="bought"?"bought":"pending";
  const progress=total?Math.round((bought.length/total)*100):0;
  return `<div class="market-quick-action"><button type="button" class="primary-btn" data-shop-action="market-add-cart">+ Produto direto no carrinho</button><span>Esqueceu de incluir algo? Registre aqui sem voltar à lista.</span></div>
  ${shoppingSearchBar({area:"market",query:search,total:active==="pending"?remaining.length:bought.length,
    shown:active==="pending"?visibleRemaining.length:visibleBought.length,
    label:"Buscar produtos da compra"})}
  <section class="market-mode-head"><div><span class="eyebrow">MODO NO MERCADO</span><h2>Resumo da compra</h2><p>Informe o preço real e toque em Colocar no carrinho para mover o produto da lista.</p></div><div class="market-spent"><span>Total no carrinho</span><strong data-market-total>${money(spent)}</strong><small data-market-remaining>${budget?"Restante do orçamento: "+money(totals.remaining):"Orçamento ainda não definido"}</small></div></section>
  <div class="market-live-preview" data-market-preview ${totals.hasUnsavedChanges||totals.previewPendingCount?"":"hidden"}>
    <span data-market-draft-summary>${totals.hasUnsavedChanges?"Prévia do carrinho (preço ainda não salvo): "+money(totals.previewSpent):""}</span>
    <span data-market-pending-summary>${totals.previewPendingCount?"Se colocar "+totals.previewPendingCount+" item(ns) digitado(s) no carrinho: "+money(totals.possibleAfterBuying):""}</span>
  </div>
  <div class="market-progress" aria-label="${progress}% dos produtos da lista no carrinho"><div class="market-progress-track"><div style="width:${progress}%"></div></div><span>${bought.length} de ${total} no carrinho</span></div>

  <nav class="market-status-tabs" aria-label="Acompanhar a compra">
    <button type="button" class="market-status-tab ${active==="pending"?"active":""}" data-shop-action="market-section-pending" aria-pressed="${active==="pending"}"><span>☐ Falta comprar</span><strong>${search.trim()?visibleRemaining.length+"/"+remaining.length:remaining.length}</strong></button>
    <button type="button" class="market-status-tab ${active==="bought"?"active":""}" data-shop-action="market-section-bought" aria-pressed="${active==="bought"}"><span>✓ No carrinho</span><strong>${search.trim()?visibleBought.length+"/"+bought.length:bought.length}</strong></button>
  </nav>
  ${active==="pending"?
  `<section class="market-focus-section"><div class="market-section-head"><h3>Falta comprar</h3><span>${remaining.length} item(ns)</span></div>
  <div class="market-list">${visibleRemaining.length?visibleRemaining.map(e=>marketRow(e,state,priceDrafts)).join(""):`<div class="empty">${remaining.length?"Nenhum produto encontrado em Falta comprar para esta pesquisa.":"Tudo o que estava na lista foi para o carrinho."} ${remaining.length?`<button type="button" class="item-btn" data-shop-action="clear-shopping-search" data-search-area="market">Limpar pesquisa</button>`:`<button type="button" class="item-btn" data-shop-action="market-section-bought">Ver meu carrinho</button>`}</div>`}</div></section>`:
  `<section class="market-focus-section"><div class="market-section-head"><h3>✓ No carrinho</h3><span>${bought.length} item(ns)</span></div>
  <div class="market-list">${visibleBought.length?visibleBought.map(e=>marketRow(e,state,priceDrafts)).join(""):`<div class="empty">${bought.length?"Nenhum produto encontrado no carrinho para esta pesquisa.":"Você ainda não colocou produtos no carrinho."} ${bought.length?`<button type="button" class="item-btn" data-shop-action="clear-shopping-search" data-search-area="market">Limpar pesquisa</button>`:`<button type="button" class="item-btn" data-shop-action="market-section-pending">Ver o que falta</button>`}</div>`}</div></section>`}
  <div class="checkout-card"><div><strong>Conferir nota fiscal e finalizar</strong><p>Anexe XML, foto ou PDF, confronte os preços com o carrinho e salve o comprovante junto à compra.</p></div>
  <button type="button" class="primary-btn" data-shop-action="close" ${bought.length?"":"disabled"}>Conferir nota e finalizar</button></div>`;
}
