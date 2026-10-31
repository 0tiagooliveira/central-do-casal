import {unmatchedHistoryCandidates} from "./catalog-matches.js";
import {catalogItems,catalogCategories,queryCatalog,alreadyAdded} from "./catalog.js";
import {priceHistory,priceTrend,sparkline} from "./catalog-history.js";

const esc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const money=n=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format((Number(n)||0)/100);
const humanDate=date=>/^\d{4}-\d{2}-\d{2}$/.test(date||"")?date.slice(8,10)+"/"+date.slice(5,7)+"/"+date.slice(0,4):"Sem data";
export function catalogView(state,{source="base",search="",category="Todas",limit=40}={}){
  const catalog=catalogItems(state.shared,state.market,source);
  const categories=catalogCategories(catalog);
  const selected=categories.includes(category)?category:"Todas";
  const results=queryCatalog(catalog,search,selected);
  const visible=results.slice(0,limit);
  const entries=state.baskets[state.market]?.entries||[];
  const links=state.links||[];
  const categoriesMarkup=categories.map(c=>`<option value="${esc(c)}" ${selected===c?"selected":""}>${esc(c)}</option>`).join("");
  const card=product=>{
    const history=priceHistory(product,state.shared,links);
    const trend=priceTrend(history);
    const last=trend.points[trend.points.length-1];
    const added=alreadyAdded(product,entries);
    const candidates=source==="base"?unmatchedHistoryCandidates(product,state.shared,links):[];
    const reference=product.kind==="catalogItem"&&Number.isSafeInteger(product.reference)&&product.reference>0?product.reference:null;
    const lastPrice=last?money(last.cents)+"/"+esc(trend.unit):"A confirmar";
    const lastDate=last?humanDate(last.date):"Sem registro válido";
    const trendKind=trend.kind==="up"?"up":trend.kind==="down"?"down":"neutral";
    const candidateCards=candidates.length?`<details class="price-candidates"><summary>Encontrados ${candidates.length} histórico(s) possíveis · revisar</summary>
      <div class="price-candidate-list">${candidates.slice(0,8).map(({sku,evidence,history:observations,latest})=>`
        <div class="price-candidate">
          <div><strong>${esc(sku.title)}</strong><small>${esc(sku.store||"Loja não informada")} · ${observations.length} preço(s) · ${latest?money(latest.cents)+"/"+esc(latest.unit)+" em "+humanDate(latest.date):"Preço sem data"}</small>
          <small>${esc(evidence.join(" · "))}</small></div>
          <button type="button" class="item-btn buy" data-shop-action="confirm-association" data-base-id="${esc(product.id)}" data-sku-id="${esc(sku.id)}">Confirmar mesmo produto</button>
        </div>`).join("")}
      </div>${candidates.length>8?'<p class="info-line">Exibidos os oito primeiros. Outros candidatos permanecem pendentes.</p>':""}</details>`:"";
    return `<article class="catalog-card">
      <div class="catalog-card-top"><div class="catalog-title"><strong>${esc(product.title)}</strong><span>${esc(product.category||"Sem categoria")}${product.store?" · "+esc(product.store):""}</span></div>
      <button type="button" class="item-btn ${added?"subtle":"buy"}" data-shop-action="choose-catalog" data-catalog-id="${esc(product.id)}" ${added?"disabled":""}>${added?"✓ Na lista":"+ Adicionar"}</button></div>
      <div class="catalog-price-row"><div><span class="eyebrow">ÚLTIMO PREÇO DOCUMENTADO</span><strong>${lastPrice}</strong><small>${lastDate}</small></div>
       <div class="trend-pill ${trendKind}">${trend.kind==="up"?"↗":trend.kind==="down"?"↘":"•"} ${esc(trend.label)}</div></div>
      <div class="catalog-chart">${sparkline(history)}<div class="chart-dates"><span>${trend.points.length?humanDate(trend.points[0].date):"Sem histórico"}</span><span>${trend.points.length?humanDate(trend.points[trend.points.length-1].date):""}</span></div></div>
      <div class="catalog-foot">${trend.points.length} preço(s) comparável(is) ${trend.unit?"· unidade: "+esc(trend.unit):""}${trend.excluded?" · "+trend.excluded+" em outras unidades não comparadas":""}</div>
      ${reference?`<div class="catalog-reference">Preço de referência cadastrado: <strong>${money(reference)}</strong> · sem data de compra confirmada</div>`:""}
      ${!history.length&&product.previousState&&/comprad/i.test(product.previousState)?'<p class="catalog-evidence">A base registra este item como comprado antes, mas faltam notas vinculadas para construir o gráfico.</p>':""}
      ${candidateCards}
    </article>`;
  };
  return `<section class="catalog-browser">
    <div class="catalog-explainer"><strong>Escolha os produtos na sua base</strong><p>Produtos que já existem no Firebase, com histórico das notas e preços unitários quando documentados. Selecione para colocar na lista sem digitar tudo novamente.</p></div>
    <div class="catalog-subnav"><button class="item-btn ${source==="base"?"active":""}" data-shop-action="source-base" type="button" aria-pressed="${source==="base"}">Minha base (${catalogItems(state.shared,state.market,"base").length})</button><button class="item-btn ${source==="store"?"active":""}" type="button" data-shop-action="source-store" aria-pressed="${source==="store"}">Por loja (${catalogItems(state.shared,state.market,"store").length})</button></div>
    <div class="catalog-filters"><label>Buscar produto<input type="search" placeholder="Nome, marca, categoria ou loja" id="shop-catalog-search" value="${esc(search)}" autocomplete="off"></label><label>Categoria<select id="shop-catalog-category">${categoriesMarkup}</select></label></div>
    <div class="catalog-result-summary"><strong>${results.length} produto(s) encontrado(s)</strong><span>Exibindo ${visible.length} de ${results.length}</span></div>
    <div class="catalog-grid">${visible.length?visible.map(card).join(""):`<div class="empty">${catalog.length?"Nenhum produto corresponde aos filtros.":"Nenhum produto dessa base disponível no Firebase para este espaço."}</div>`}</div>
    ${visible.length<results.length?`<div class="catalog-more"><button class="item-btn" data-shop-action="show-more" type="button">Mostrar mais ${Math.min(40,results.length-visible.length)} produtos</button></div>`:""}
    <p class="info-line">O gráfico usa notas documentadas e compras com vínculo confirmado. Quando houver um histórico possível, revise e confirme o produto correto para recuperar os preços; valores de referência sem data não entram na tendência.</p>
  </section>`;
}
