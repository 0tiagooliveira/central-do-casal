import {normalizeCatalogText,normalizeCatalogUnit} from "./catalog.js";
import {UNITS,moneyCents,milliQty} from "./shopping.js";

const esc=value=>String(value??"").replace(/[&<>"']/g,ch=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
const norm=value=>normalizeCatalogText(value).replace(/\s+/g," ");
const decimal=milli=>(milli/1000).toLocaleString("pt-BR",{maximumFractionDigits:3});

/** Compra não planejada: pesquisa opcional no catálogo e inclusão no carrinho. */
export function showMarketAddDialog({dialog,state,notify,onSubmit}) {
  let selected=null,visible=[];
  const products=(state.shared||[])
    .filter(p=>(p.kind==="catalogItem"||p.kind==="catalogSku")&&p.private!==true&&
      (p.market===state.market||(!p.market&&state.market==="Mercado"))&&String(p.title||"").trim());
  dialog.innerHTML=[
    '<form id="market-instant-add" method="dialog">',
    '<div class="dialog-header"><h2>+ Produto direto no carrinho</h2><button type="button" class="item-btn" data-dismiss>Fechar</button></div>',
    '<p class="info-line">Pesquise um produto da base ou digite um novo. Ao salvar, ele vai para No carrinho com a quantidade e o preço pago.</p>',
    '<label>Nome do produto<input name="title" type="search" required maxlength="200" autocomplete="off" placeholder="Ex.: Amaciante 5 L ou pão" aria-controls="market-instant-suggestions"></label>',
    '<div id="market-instant-suggestions" class="market-instant-suggestions" aria-label="Sugestões do catálogo"></div>',
    '<div class="dialog-grid"><label>Quantidade comprada<input name="qty" required inputmode="decimal" value="1" autocomplete="off"></label>',
    '<label>Unidade<select name="unit" required>',
    UNITS.map(u=>'<option value="'+esc(u)+'">'+esc(u)+'</option>').join(""),
    '</select></label></div>',
    '<div class="market-inline-adjust"><button type="button" data-qty-adjust="-1">− Diminuir</button><button type="button" data-qty-adjust="1">+ Aumentar</button></div>',
    '<label>Preço real por unidade ou embalagem (R$)<input name="actual" required inputmode="decimal" autocomplete="off" placeholder="Ex.: 12,90"></label>',
    '<label>Categoria / grupo<input name="group" maxlength="60" value="Essenciais" autocomplete="off"></label>',
    '<div class="dialog-actions"><button type="submit" class="primary-btn">✓ Adicionar direto ao carrinho</button></div>',
    '<p class="info-line">Se já existir um único item pendente com mesmo nome e unidade, ele será movido ao carrinho para evitar duplicação.</p>',
    '</form>'
  ].join("");
  dialog.showModal();
  const form=dialog.querySelector("form");
  const title=form.elements.title,unit=form.elements.unit,qty=form.elements.qty;
  const suggestions=dialog.querySelector("#market-instant-suggestions");
  function showSuggestions(value){
    visible=[];
    const words=norm(value).split(" ").filter(Boolean);
    if(!words.length||norm(value).length<2){suggestions.innerHTML="";return;}
    visible=products.filter(p=>{
      const context=norm([p.title,p.category,p.store,p.group].filter(Boolean).join(" "));
      return words.every(word=>context.includes(word));
    }).sort((a,b)=>(a.kind==="catalogItem"?0:1)-(b.kind==="catalogItem"?0:1)).slice(0,7);
    suggestions.innerHTML=visible.length?
      '<span class="market-suggestions-label">Escolha da base ou continue digitando</span>'+
      visible.map((p,i)=>'<button type="button" data-pick-product="'+i+'"><strong>'+esc(p.title)+
        '</strong><small>'+esc(p.kind==="catalogItem"?"Minha base":"Por loja")+
        (p.category?" · "+esc(p.category):"")+(p.store?" · "+esc(p.store):"")+'</small></button>').join(""):
      '<p class="info-line">Produto ainda não identificado. Você pode cadastrá-lo nesta compra.</p>';
  }
  title.addEventListener("input",()=>{selected=null;showSuggestions(title.value);});
  suggestions.addEventListener("click",event=>{
    const button=event.target.closest("[data-pick-product]");
    if(!button)return;
    const product=visible[Number(button.dataset.pickProduct)];
    if(!product)return;
    selected=product;
    title.value=product.title;
    const knownUnit=normalizeCatalogUnit(product.unit);
    unit.value=UNITS.includes(knownUnit)?knownUnit:"un";
    form.elements.group.value=String(product.group||product.category||"Essenciais").slice(0,60);
    suggestions.innerHTML='<p class="market-selected-product">✓ Escolhido da base: '+esc(product.title)+'</p>';
    form.elements.actual.focus();
  });
  dialog.querySelector("[data-dismiss]").addEventListener("click",()=>dialog.close());
  dialog.querySelectorAll("[data-qty-adjust]").forEach(button=>button.addEventListener("click",()=>{
    const step=unit.value==="kg"||unit.value==="L"?500:unit.value==="g"||unit.value==="mL"?100000:1000;
    let current=1000;
    try{current=milliQty(qty.value);}catch{}
    const next=current+Number(button.dataset.qtyAdjust)*step;
    if(next<=0||next>100000000)return;
    qty.value=decimal(next);
  }));
  form.addEventListener("submit",event=>{
    event.preventDefault();
    try{
      const purchasedQty=milliQty(qty.value),actual=moneyCents(form.elements.actual.value);
      const entry={title:title.value.trim(),unit:unit.value,
        plannedMilli:purchasedQty,actualMilli:purchasedQty,
        estimated:actual,actual,bought:true,estimateKnown:true,
        unitReviewed:true,quantityReviewed:true,
        group:form.elements.group.value.trim()||"Essenciais",
        category:selected?.category||"",
        sourceBaseId:selected?.kind==="catalogItem"?selected.id:"",
        sourceSkuId:selected?.kind==="catalogSku"?selected.id:""};
      if(!entry.title)throw new Error("Informe o nome do produto.");
      onSubmit(entry);
    }catch(error){notify(error.message||"Revise quantidade e preço.",true);}
  });
  title.focus();
}
