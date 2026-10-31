import {packageDetailCards,familyEquivalentComparison,money4,quantitySelector} from "./pricing-details-ui.js";
import {normalizedRate} from "./product-presentation.js";
import {primaryFamilySeries,familyVariants} from "./catalog-families.js";
import {unmatchedHistoryCandidates} from "./catalog-matches.js";
import {catalogItems,catalogCategories,queryCatalog,alreadyAdded} from "./catalog.js";
import {priceHistory,priceTrend,sparkline} from "./catalog-history.js";

const esc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const money=n=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL",minimumFractionDigits:2,maximumFractionDigits:Number.isInteger(n)?2:4}).format((Number(n)||0)/100);
const humanDate=date=>/^\d{4}-\d{2}-\d{2}$/.test(date||"")?date.slice(8,10)+"/"+date.slice(5,7)+"/"+date.slice(0,4):"Sem data";
export function catalogView(state,{source="base",search="",category="Todas",limit=40,recoverableOnly=false,quantities={}}={}){
  const catalog=catalogItems(state.shared,state.market,source);
  const categories=catalogCategories(catalog);
  const selected=categories.includes(category)?category:"Todas";
  const entries=state.baskets[state.market]?.entries||[];
  const links=state.links||[];
  const filtered=queryCatalog(catalog,search,selected);
  const recoverable=source==="base"&&recoverableOnly?filtered.filter(p=>familyVariants(p,state.shared,links).some(v=>v.needsReview)):[];
  const results=source==="base"&&recoverableOnly?recoverable:filtered;
  const visible=results.slice(0,limit);
  const categoriesMarkup=categories.map(c=>`<option value="${esc(c)}" ${selected===c?"selected":""}>${esc(c)}</option>`).join("");
  const card=product=>{
    const family=source==="base"?primaryFamilySeries(product,state.shared,links):null;
    const history=family?family.history:priceHistory(product,state.shared,links);
    const trend=priceTrend(history);
    const last=trend.points[trend.points.length-1];
    const added=alreadyAdded(product,entries);
    const reference=product.kind==="catalogItem"&&Number.isSafeInteger(product.reference)&&product.reference>0?product.reference:null;
    const lastPrice=last?money(last.cents)+"/"+esc(trend.unit):"A confirmar";
    const lastDate=last?humanDate(last.date):"Sem registro válido";
    const trendKind=trend.kind==="up"?"up":trend.kind==="down"?"down":"neutral";
    const variants=family?.variants||[];
    const familyDetails=variants.length?`<details class="family-details">
      <summary>Ver ${variants.length} variação(ões) · ${variants.reduce((n,v)=>n+v.history.length,0)} compras com preços</summary>
      ${familyEquivalentComparison(variants)}
      <div class="family-variants">
        ${variants.map(v=>`<article class="family-variant">
          <div class="variant-top"><strong>${esc(v.sku.title)}</strong>
            <small>${esc(v.sku.store||"Loja não informada")} · ${esc(v.status)}</small></div>
          ${packageDetailCards(v.history,{skuId:v.sku.id,baseId:product.id,quantities})}
          ${v.needsReview?`<button type="button" class="item-btn" data-shop-action="confirm-association" data-base-id="${esc(product.id)}" data-sku-id="${esc(v.sku.id)}">Confirmar associação à base</button>`:""}
        </article>`).join("")}
      </div>
      <p class="info-line">Os preços ficam separados por apresentação. Associação por nome é apenas uma prévia, não um vínculo gravado.</p>
    </details>`:"";
    const storeDetails=source==="store"&&history.length?`<details class="family-details">
      <summary>Ver embalagens, histórico e estatísticas</summary>${packageDetailCards(history,{skuId:product.id,quantities})}</details>`:"";
    const referenceRate=last?normalizedRate(last):null;
    const presentationLabel=(family?.presentation||last?.presentation)?.display||"";
    return `<article class="catalog-card">
      <div class="catalog-card-top"><div class="catalog-title"><strong>${esc(product.title)}</strong><span>${esc(product.category||"Sem categoria")}${product.store?" · "+esc(product.store):""}</span></div></div>
      ${!added?quantitySelector(product.id,product.unit||"un",quantities):""}
      <button type="button" class="item-btn catalog-add-button ${added?"subtle":"buy"}" data-shop-action="choose-catalog" data-catalog-id="${esc(product.id)}" ${added?"disabled":""}>${added?"✓ Na lista":"+ Adicionar com esta quantidade"}</button>
      <div class="catalog-price-row"><div><span class="eyebrow">${family&&history.length?"PREÇO DA VARIAÇÃO EM DESTAQUE":"ÚLTIMO PREÇO DOCUMENTADO"}</span><strong>${lastPrice}</strong><small>${lastDate}</small></div>
       <div class="trend-pill ${trendKind}">${trend.kind==="up"?"↗":trend.kind==="down"?"↘":"•"} ${esc(trend.label)}</div></div>
      ${history.length?`<div class="family-main-label">${family?esc(family.label)+" · "+(family.confirmed?"vínculo confirmado":"prévia por família")+" · ":""}${esc(presentationLabel)}</div>`:""}
      ${history.length?`<div class="catalog-chart">${sparkline(history)}<div class="chart-dates"><span>${trend.points.length?humanDate(trend.points[0].date):"Sem histórico"}</span><span>${trend.points.length?humanDate(trend.points[trend.points.length-1].date):""}</span></div></div>`:`<div class="catalog-no-history">Sem preços de compras associados a esta família na base atual.</div>`}
      <div class="catalog-foot">${trend.points.length} preço(s) comparável(is) ${trend.unit?"· unidade: "+esc(trend.unit):""}${trend.excluded?" · "+trend.excluded+" em outras unidades não comparadas":""}</div>
      ${referenceRate?`<div class="catalog-reference"><strong>${money4(referenceRate.cents)}/${esc(referenceRate.unit)}</strong> · ${esc(referenceRate.basis)}</div>`:""}
      ${history.length&&trend.kind==="unknown"&&trend.points.length>1?`<div class="catalog-evidence">A variação não foi calculada porque a apresentação da embalagem não está identificada.</div>`:""}
      ${reference?`<div class="catalog-reference">Preço de referência cadastrado: <strong>${money(reference)}</strong> · sem data de compra confirmada</div>`:""}
      ${!history.length&&product.previousState&&/comprad/i.test(product.previousState)?'<p class="catalog-evidence">A base registra este item como comprado antes, mas faltam notas vinculadas para construir o gráfico.</p>':""}
      ${familyDetails}
      ${storeDetails}
    </article>`;
  };
  return `<section class="catalog-browser">\n    <div class="switcher catalog-market-switch">${["Mercado","Açougue"].map(market=>`<button type="button" data-market="${market}" aria-pressed="${state.market===market}">${market}</button>`).join("")}</div>
    <div class="catalog-explainer"><strong>Escolha os produtos na sua base</strong><p>Produtos que já existem no Firebase, com histórico das notas e preços unitários quando documentados. Selecione para colocar na lista sem digitar tudo novamente.</p></div>
    <div class="catalog-subnav"><button class="item-btn ${source==="base"?"active":""}" data-shop-action="source-base" type="button" aria-pressed="${source==="base"}">Minha base (${catalogItems(state.shared,state.market,"base").length})</button><button class="item-btn ${source==="store"?"active":""}" type="button" data-shop-action="source-store" aria-pressed="${source==="store"}">Por loja (${catalogItems(state.shared,state.market,"store").length})</button></div>
    <div class="catalog-filters"><label>Buscar produto<input type="search" placeholder="Nome, marca, categoria ou loja" id="shop-catalog-search" value="${esc(search)}" autocomplete="off"></label><label>Categoria<select id="shop-catalog-category">${categoriesMarkup}</select></label></div>
    ${source==="base"?`<label class="catalog-recovery-filter"><input type="checkbox" data-catalog-recovery ${recoverableOnly?"checked":""}> Mostrar famílias com preços agrupáveis e vínculos pendentes${recoverableOnly?" ("+recoverable.length+")":""}</label>`:""}
    <div class="catalog-result-summary"><strong>${results.length} produto(s) encontrado(s)</strong><span>Exibindo ${visible.length} de ${results.length}</span></div>
    <div class="catalog-grid">${visible.length?visible.map(card).join(""):`<div class="empty">${catalog.length?"Nenhum produto corresponde aos filtros.":"Nenhum produto dessa base disponível no Firebase para este espaço."}</div>`}</div>
    ${visible.length<results.length?`<div class="catalog-more"><button class="item-btn" data-shop-action="show-more" type="button">Mostrar mais ${Math.min(40,results.length-visible.length)} produtos</button></div>`:""}
    <p class="info-line">O gráfico usa notas documentadas e compras com vínculo confirmado. Cada família mostra gráficos por embalagem e preço equivalente por litro/quilo/peça quando a nota fornece o tamanho exato. Vínculos sugeridos aparecem como prévia e só se tornam oficiais se você os confirmar.</p>
  </section>`;
}
