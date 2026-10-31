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
function marketRow(e) {
  const title=esc(e.title||"Produto sem nome");
  const ready=typeof e.unit==="string"&&e.unit!=="Escolher"&&e.unitReviewed!==false&&e.quantityReviewed!==false;
  const bought=e.bought;
  const price=e.actual==null?"":(Number(e.actual)/100).toFixed(2).replace(".",",");
  const group=esc(e.group||e.category||"Essenciais");
  const realQty=Number(e.actualMilli??e.plannedMilli??1000);
  return `<article class="market-row ${bought?"done":""}">
    <div class="market-row-head"><div><strong>${title}</strong><span>${group}${ready?"":" · Revisar unidade"}</span></div><div class="market-card-menu"><button type="button" class="item-btn" data-shop-action="edit" data-item-id="${esc(e.id)}">Detalhes</button><button type="button" class="item-btn danger" data-shop-action="remove-inline" data-item-id="${esc(e.id)}">Remover</button></div></div>
    <div class="market-purchase-line">${quantityControls(e,true)}
      <label class="market-price">Preço real por ${esc(e.unit||"un")} (R$)
        <input aria-label="Preço real de ${title}" data-market-price-id="${esc(e.id)}" inputmode="decimal" placeholder="0,00" value="${esc(price)}" autocomplete="off">
      </label></div>
    <div class="market-row-foot"><span>${bought?`Total no carrinho: <strong>${money(subtotal(e.actual||0,realQty))}</strong>`:e.estimateKnown===false?"Sem preço de referência":`Referência: ${money(e.estimated||0)}/${esc(e.unit)}`}</span>
      ${bought?`<div class="market-row-buttons"><button type="button" class="item-btn" data-shop-action="unbuy-inline" data-item-id="${esc(e.id)}">Retirar do carrinho</button><button type="button" class="item-btn buy" data-shop-action="price-save" data-item-id="${esc(e.id)}">Salvar preço</button></div>`:
      `<button type="button" class="primary-btn" data-shop-action="buy-inline" data-item-id="${esc(e.id)}">✓ Colocar no carrinho</button>`}
    </div>
  </article>`;
}
export function marketView(state) {
  const entries=state.baskets[state.market]?.entries||[];
  const remaining=entries.filter(e=>!e.bought),bought=entries.filter(e=>e.bought);
  const spent=bought.reduce((s,e)=>s+subtotal(e.actual||0,e.actualMilli??e.plannedMilli??1000),0);
  const budget=state.baskets[state.market]?.budget||0;
  return `<section class="market-mode-head"><div><span class="eyebrow">MODO NO MERCADO</span><h2>Conferência em tempo real</h2><p>Informe o preço da etiqueta e ajuste a quantidade antes de colocar o produto no carrinho. Tudo é sincronizado com o Android.</p></div><div class="market-spent"><span>Total no carrinho</span><strong>${money(spent)}</strong><small>${budget?"Restante do orçamento: "+money(budget-spent):"Orçamento ainda não definido"}</small></div></section>
  <div class="market-sections"><section><div class="market-section-head"><h3>Falta comprar</h3><span>${remaining.length} item(ns)</span></div>
    <div class="market-list">${remaining.length?remaining.map(marketRow).join(""):`<div class="empty">Tudo que estava na lista foi colocado no carrinho.</div>`}</div></section>
    <section><div class="market-section-head"><h3>✓ No carrinho</h3><span>${bought.length} item(ns)</span></div>
    <div class="market-list">${bought.length?bought.map(marketRow).join(""):`<div class="empty">Conforme colocar produtos no carrinho, eles aparecerão aqui.</div>`}</div></section></div>
  <div class="checkout-card"><div><strong>Finalizar compra</strong><p>Confira quantidades e preços antes de salvar a compra no histórico.</p></div>
  <button type="button" class="primary-btn" data-shop-action="close" ${bought.length?"":"disabled"}>Concluir e salvar</button></div>`;
}
