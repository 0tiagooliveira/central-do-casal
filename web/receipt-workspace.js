import {parseFiscalXml,parseManualInvoiceLines} from "./invoice-parser.js";
import {projectedShoppingState} from "./emergency-cart.js";

// Tudo roda no aparelho. Não publica nem modifica o carrinho no Firebase.
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const money=n=>Number.isFinite(n)?new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(n/100):"Aguardando";
const qty=n=>(Number(n||0)/1000).toLocaleString("pt-BR",{maximumFractionDigits:3});
const norm=s=>String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().replace(/[^a-z0-9]+/g," ").trim();
const totalEntry=e=>Math.round((Number(e.actual)||0)*Number(e.actualMilli??e.plannedMilli??1000)/1000);
const state={backup:null,invoice:null,map:{},pending:{},backupName:"",invoiceName:"",message:"",error:false,reviewSection:"pending"};

function bought(shop){
 const entries=state.backup||projectedShoppingState(shop).baskets?.[shop.market]?.entries||[];
 return entries.filter(e=>e.bought&&Number.isFinite(Number(e.actual)));
}
function suggest(entries,invoice){
 const suggestions={},pending={};
 for(const line of invoice.lines||[]){
   const candidates=entries.filter(e=>norm(e.title)===norm(line.name)&&
     (["UN","UND","UNID"].includes(line.unit?.toUpperCase())?e.unit==="un":true));
   if(candidates.length===1)suggestions[String(line.id)]=candidates[0].id;
   else if(line.suggestedCartTitle){
     const hint=entries.filter(e=>norm(e.title)===norm(line.suggestedCartTitle));
     if(hint.length===1){suggestions[String(line.id)]=hint[0].id;pending[String(line.id)]=true;}
   }
 }
 return {suggestions,pending};
}
function analyze(shop){
 const entries=bought(shop),note=state.invoice;
 const cartTotal=entries.reduce((a,e)=>a+totalEntry(e),0);
 if(!note)return {entries,cartTotal,rows:[],groups:[],noteTotal:null,
   noteGross:null,discount:0,difference:null,issues:0,unmappedCart:[]};
 const groups=entries.map(e=>({entry:e,lines:[],cartTotal:totalEntry(e)}));
 const byId=new Map(groups.map(g=>[g.entry.id,g]));
 const rows=(note.lines||[]).map(line=>{
   const group=byId.get(state.map[String(line.id)]||"")||null;
   if(group)group.lines.push(line);
   return {line,group};
 });
 for(const group of groups){
   group.noteTotal=group.lines.reduce((s,l)=>s+l.netCents,0);
   group.noteQty=group.lines.reduce((s,l)=>s+l.quantityMilli,0);
   group.difference=group.noteTotal-group.cartTotal;
   group.qtyWrong=group.lines.length>0&&group.noteQty!==Number(group.entry.actualMilli??group.entry.plannedMilli??1000);
   group.unitWrong=group.lines.length>0&&group.lines.some(l=>
     ["UN","UND","UNID"].includes(l.unit.toUpperCase())?group.entry.unit!=="un":
       l.unit.toLowerCase()!==String(group.entry.unit).toLowerCase());
   group.hasSuggestion=group.lines.some(l=>state.pending[String(l.id)]);
   group.warning=group.lines.length===0||Math.abs(group.difference)>1||group.qtyWrong||group.unitWrong||group.hasSuggestion;
 }
 const unmappedCart=groups.filter(g=>!g.lines.length);
 return {entries,cartTotal,rows,groups,noteTotal:note.totalCents,
   noteGross:note.grossTotalCents??(note.lines||[]).reduce((s,l)=>s+l.netCents,0),
   discount:note.generalDiscountCents??0,difference:note.totalCents-cartTotal,
   issues:rows.filter(r=>!r.group).length+groups.filter(g=>g.warning).length,unmappedCart};
}
export function receiptWorkspaceView(shop){
 const r=analyze(shop),note=state.invoice;
 const metric=(title,value,sub)=>'<div class="review-metric"><span>'+esc(title)+
   '</span><strong>'+money(value)+'</strong><small>'+esc(sub)+'</small></div>';
 // Associar uma linha a tira IMEDIATAMENTE da fila de "Pendentes".
 // Diferenças de preço/quantidade permanecem na aba dedicada, sem apagar nada.
 const pendingRows=r.rows.filter(({line,group})=>!group||!!state.pending[String(line.id)]);
 const differentRows=r.rows.filter(({line,group})=>group&&!state.pending[String(line.id)]&&group.warning);
 const associatedRows=r.rows.filter(({line,group})=>group&&!state.pending[String(line.id)]);
 const completeRows=associatedRows.filter(({group})=>!group.warning);
 const sections={pending:pendingRows,different:differentRows,associated:associatedRows,all:r.rows};
 const visibleRows=sections[state.reviewSection]||pendingRows;
 const items=note?visibleRows.map(({line,group})=>{
   const current=state.map[String(line.id)]||"";
   const options=r.entries.map(e=>'<option value="'+esc(e.id)+'"'+(current===e.id?" selected":"")+'>'+
     esc(e.title)+' · '+qty(e.actualMilli??e.plannedMilli)+' '+esc(e.unit)+'</option>').join("");
   const status=!group?"⚠ Sem associação":state.pending[String(line.id)]?"⚠ Associação sugerida: confirme":group.qtyWrong||group.unitWrong?"⚠ Quantidade/unidade diferente":
     Math.abs(group.difference)>1?"⚠ Preço diferente":"✓ Valores conferem";
   return '<article class="review-line"><div><strong>'+esc(line.name)+'</strong>'+
     '<small>'+qty(line.quantityMilli)+' '+esc(line.unit)+' × '+money(line.unitCents)+' = '+money(line.netCents)+'</small></div>'+
     '<label>Produto correspondente no carrinho<select data-review-link="'+esc(line.id)+'">'+
     '<option value="">Selecione ou mantenha sem associação</option>'+options+'</select></label>'+
     (state.pending[String(line.id)]?'<button type="button" class="item-btn" data-review-action="approve" data-review-id="'+esc(line.id)+'">Confirmar associação sugerida</button>':"")+
     '<div class="review-line-status '+(group?.warning||!group?"review-alert":"")+'">'+status+
     (group?'<small>Carrinho '+money(group.cartTotal)+' · Nota associada '+money(group.noteTotal)+
       ' · Diferença '+money(group.difference)+'</small>':"")+'</div></article>';
 }).join(""):"";
 return '<section class="review-workspace">'+
   '<div class="review-heading"><span class="eyebrow">CONFERÊNCIA</span>'+
   '<h2>Nota fiscal × compras</h2><p>Envie seu backup e os itens da nota. Aqui você vê os preços divergentes e os produtos sem correspondência, mesmo sem Firebase.</p></div>'+
   '<div class="review-upload-grid">'+
   '<label class="review-upload"><strong>1. Backup do carrinho (.json)</strong>'+
   '<small>'+(state.backup?"Carregado: "+esc(state.backupName):"Ou use a lista aberta neste aparelho")+'</small>'+
   '<input type="file" accept=".json,application/json" data-review-file="backup"></label>'+
   '<label class="review-upload"><strong>2. Itens da nota (.xml)</strong>'+
   '<small>'+(note?"Carregado: "+esc(state.invoiceName):"Selecione o XML de conferência que preparei do PDF")+'</small>'+
   '<input type="file" accept=".xml,application/xml,text/xml" data-review-file="invoice"></label></div>'+
   '<details class="review-manual"><summary>Tenho só o PDF: quero colar os itens da nota</summary>'+
   '<p>Cole uma linha por produto: <code>Descrição; quantidade; UN; preço unitário</code>.</p>'+
   '<label>Total pago na nota (R$)<input data-review-total inputmode="decimal" placeholder="611,30"></label>'+
   '<label>Produtos<textarea data-review-lines rows="5" placeholder="Óleo; 1; UN; 6,99"></textarea></label>'+
   '<button type="button" class="item-btn" data-review-action="manual">Conferir itens colados</button></details>'+
   (state.message?'<p class="review-notice'+(state.error?" review-error":"")+'" role="status">'+esc(state.message)+'</p>':"")+
   '<div class="review-metrics">'+metric("Seu carrinho",r.cartTotal,r.entries.length+" itens marcados")+
   metric("Subtotal da nota",r.noteGross,"Antes dos descontos")+
   metric("Desconto geral",r.discount,"Não rateado por produto")+
   metric("Total pago na nota",r.noteTotal,note?note.lines.length+" linhas":"Envie XML")+
   metric("Diferença (nota - carrinho)",r.difference,note?r.issues+" pendências":"Aguardando nota")+'</div>'+
   (note?.parseStatus==="transcribed-from-pdf"?
     '<p class="review-notice">XML transcrito do PDF: <strong>não é a nota fiscal eletrônica original</strong>. Confirme os vínculos de produtos abreviados.</p>':"")+
   (note?'<div class="review-controls"><h3>Conferir os produtos</h3></div>'+
     '<nav class="review-section-tabs" aria-label="Situação da conferência">'+
     '<button type="button" class="review-section-tab'+(state.reviewSection==="pending"?" active":"")+'" data-review-section="pending">Pendentes ('+pendingRows.length+')</button>'+
     '<button type="button" class="review-section-tab'+(state.reviewSection==="different"?" active":"")+'" data-review-section="different">Diferenças ('+differentRows.length+')</button>'+
     '<button type="button" class="review-section-tab'+(state.reviewSection==="associated"?" active":"")+'" data-review-section="associated">Associados ('+associatedRows.length+')</button>'+
     '<button type="button" class="review-section-tab'+(state.reviewSection==="all"?" active":"")+'" data-review-section="all">Todos ('+r.rows.length+')</button>'+
     '</nav><p class="info-line review-filter-help">'+
     (state.reviewSection==="pending"?"Ao associar um item, ele sai desta lista. Se houver diferença de preço, aparecerá em Diferenças.":
       state.reviewSection==="different"?"Produtos associados que ainda têm diferenças de preço, quantidade ou unidade.":
       state.reviewSection==="associated"?"Produtos já associados, incluindo os que ainda têm diferenças para conferir.":
       "Todas as linhas da nota, inclusive as já associadas.")+
     '</p>'+

     '<div class="review-lines">'+(items||'<p class="review-empty">Nenhum produto nesta seção.</p>')+'</div>'+
     (r.unmappedCart.length?'<section class="review-missing"><h3>Produtos do carrinho sem vínculo na nota</h3>'+
       r.unmappedCart.map(g=>'<p>'+esc(g.entry.title)+' · '+money(g.cartTotal)+'</p>').join("")+'</section>':"")+
     '<div class="review-actions"><button type="button" class="primary-btn" data-review-action="download">Baixar relatório</button>'+
     '<button type="button" class="item-btn" data-review-action="save">Salvar conferência neste aparelho</button></div>'+
     '<p class="info-line">Não modifica automaticamente valores, histórico nem notas no Firebase.</p>':
     '<p class="info-line">Selecione o XML preparado a partir do PDF para conferir os 38 produtos sem digitar tudo.</p>')+
   '</section>';
}
function exportReport(shop){
 const r=analyze(shop),n=state.invoice;
 return {type:"central-casal-conferencia-v1",generatedAt:new Date().toISOString(),
   fiscalXmlOriginal:n?.source==="xml"&&n?.parseStatus==="extracted",
   note:{issuer:n?.issuer||"",date:n?.date||"",number:n?.number||"",
     grossCents:r.noteGross,discountCents:r.discount,paidCents:r.noteTotal},
   cartTotalCents:r.cartTotal,differenceCents:r.difference,issues:r.issues,
   comparison:r.groups.map(g=>({name:g.entry.title,cartCents:g.cartTotal,
     fiscalLines:g.lines.map(l=>({name:l.name,totalCents:l.netCents,quantityMilli:l.quantityMilli})),
     fiscalCents:g.lines.length?g.noteTotal:null,differenceCents:g.lines.length?g.difference:null,
     status:!g.lines.length?"not-matched":g.hasSuggestion?"suggested-pending":g.warning?"different":"matched"})),
   unlinkedNote:r.rows.filter(x=>!x.group).map(x=>({name:x.line.name,
     totalCents:x.line.netCents,quantityMilli:x.line.quantityMilli}))};
}
export function installReceiptWorkspace({root,getState,render,notify}){
 root.addEventListener("change",async event=>{
   const el=event.target;
   if(el.dataset.reviewFile){
     const file=el.files?.[0];if(!file)return;
     try{
       if(file.size>750000)throw Error("Arquivo acima de 750 KB.");
       if(el.dataset.reviewFile==="backup"){
         const obj=JSON.parse(await file.text());
         const entries=obj.baskets?.Mercado?.entries||obj.baskets?.["Açougue"]?.entries||
           obj.basket?.entries;
         if(!Array.isArray(entries))throw Error("Este arquivo não contém a cesta de compras.");
         state.backup=entries;state.backupName=file.name;
         if(state.invoice){const p=suggest(bought(getState()),state.invoice);state.map=p.suggestions;state.pending=p.pending;}
         state.message="Backup carregado. Nenhum valor foi alterado.";state.error=false;
       }else{
         state.invoice=parseFiscalXml(await file.text());state.invoiceName=file.name;
         {const p=suggest(bought(getState()),state.invoice);state.map=p.suggestions;state.pending=p.pending;}
         state.message=state.invoice.parseStatus==="transcribed-from-pdf"?
           "Os produtos do PDF foram carregados. Confirme as associações.":
           "Itens da nota carregados para conferência.";
         state.error=false;
       }
     }catch(e){state.message=e.message||"Falha ao ler arquivo.";state.error=true;}
     render();return;
   }
   if(el.dataset.reviewLink!==undefined){state.map[String(el.dataset.reviewLink)]=el.value;delete state.pending[String(el.dataset.reviewLink)];render();return;}
   if(el.dataset.reviewSection!==undefined){state.reviewSection=el.dataset.reviewSection;render();}
 });
 root.addEventListener("click",event=>{
   const btn=event.target.closest("[data-review-action]");
   if(!btn)return;
   const type=btn.dataset.reviewAction;
   if(type==="manual"){
     try{
       const lines=parseManualInvoiceLines(root.querySelector("[data-review-lines]")?.value||"");
       const raw=root.querySelector("[data-review-total]")?.value||"";
       const value=Number(raw.trim().replace(/\./g,"").replace(",","."));
       if(!lines.length||!raw.trim()||!Number.isFinite(value)||value<0)
         throw Error("Informe os itens e o valor pago na nota.");
       state.invoice={source:"manual",parseStatus:"manually-entered",
         totalCents:Math.round(value*100),generalDiscountCents:0,lines};
       state.invoiceName="Itens colados";state.map=suggest(bought(getState()),state.invoice);
       state.message="Itens digitados carregados.";state.error=false;
     }catch(e){state.message=e.message;state.error=true;}
     render();return;
   }
   if(!state.invoice)return;
   if(type==="approve"){
     const id=btn.dataset.reviewId;
     if(state.map[id]){delete state.pending[id];state.message="Associação confirmada.";state.error=false;render();}
     return;
   }
   if(type==="save"){
     try{
       localStorage.setItem("cdc:last-invoice-review",JSON.stringify(exportReport(getState())));
       state.message="Conferência salva somente neste aparelho. Baixe também o relatório.";
       state.error=false;
     }catch(e){state.message="Sem espaço no navegador. Baixe o relatório.";state.error=true;}
     render();return;
   }
   if(type==="download"){
     const blob=new Blob([JSON.stringify(exportReport(getState()),null,2)],{type:"application/json"});
     const url=URL.createObjectURL(blob),a=document.createElement("a");
     a.href=url;a.download="conferencia-compra-"+new Date().toISOString().slice(0,10)+".json";
     document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
     notify?.("Relatório baixado. Não foi enviado ao Firebase.");
   }
 });
}
