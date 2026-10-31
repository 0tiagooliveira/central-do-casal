// Revisão de nota antes de finalizar uma cesta. XML é extraído localmente;
// foto/PDF são anexados sem OCR e exigem conferência manual.
import {parseFiscalXml,parseManualInvoiceLines} from "./invoice-parser.js";
import {compareInvoice,suggestInvoiceMappings,labelInvoiceStatus} from "./invoice-reconcile.js";
import {moneyCents} from "./shopping.js";
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const brl=c=>Number.isFinite(c)?new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(c/100):"Não informado";
const fileLimit=180000; // DataURL chars. Keeps the whole Firestore receipt under 1 MiB.
async function imageAsDataUrl(file){
  if(file.size>10_000_000)throw Error("Imagem muito grande (máximo 10 MB).");
  const bitmap=await createImageBitmap(file);
  try{
    const dimension=Math.max(bitmap.width,bitmap.height);
    const canvas=document.createElement("canvas");
    const scale=Math.min(1,1500/dimension);
    canvas.width=Math.max(1,Math.round(bitmap.width*scale));
    canvas.height=Math.max(1,Math.round(bitmap.height*scale));
    const context=canvas.getContext("2d");
    context.fillStyle="#fff";context.fillRect(0,0,canvas.width,canvas.height);
    context.drawImage(bitmap,0,0,canvas.width,canvas.height);
    for(const quality of [.77,.65,.52,.4]){
      const result=canvas.toDataURL("image/jpeg",quality);
      if(result.length<=fileLimit)return result;
    }
    throw Error("A foto ficou grande demais para ser guardada com segurança no histórico. Envie um XML ou outra imagem.");
  }finally{bitmap.close();}
}
async function fileAsDataUrl(file){
  if(file.size>130000)throw Error("PDF acima do limite de 130 KB para o histórico gratuito. Use o XML ou uma foto.");
  return await new Promise((resolve,reject)=>{
    const reader=new FileReader();
    reader.onload=()=>resolve(String(reader.result||""));
    reader.onerror=()=>reject(Error("Não foi possível ler o PDF."));
    reader.readAsDataURL(file);
  });
}
async function readInvoiceFile(file){
  if(!file)throw Error("Selecione uma nota.");
  const extension=file.name.split(".").pop()?.toLowerCase();
  if(extension==="xml"||file.type==="text/xml"||file.type==="application/xml"){
    const raw=await file.text(),parsed=parseFiscalXml(raw);
    return {...parsed,filename:file.name.slice(0,120),mime:"application/xml",xmlContent:raw};
  }
  if(file.type.startsWith("image/")){
    const dataUrl=await imageAsDataUrl(file);
    return {source:"image",format:"image",issuer:"",date:"",number:"",
      totalCents:null,lines:[],parseStatus:"not-extracted",filename:file.name.slice(0,120),
      mime:"image/jpeg",fileData:dataUrl,
      note:"Foto anexada, mas itens e preços não extraídos automaticamente."};
  }
  if(extension==="pdf"||file.type==="application/pdf"){
    const dataUrl=await fileAsDataUrl(file);
    return {source:"pdf",format:"pdf",issuer:"",date:"",number:"",
      totalCents:null,lines:[],parseStatus:"not-extracted",filename:file.name.slice(0,120),
      mime:"application/pdf",fileData:dataUrl,
      note:"PDF anexado, mas itens e preços não extraídos automaticamente."};
  }
  throw Error("Formato não aceito. Use XML, PDF ou imagem.");
}
export function showInvoiceCheckout({dialog,state,notify,onFinish}) {
  const market=state.market,basket=state.baskets[market];
  const bought=(basket?.entries||[]).filter(e=>e.bought);
  if(!bought.length){notify("Coloque pelo menos um item no carrinho antes de finalizar.",true);return;}
  let invoice=null,mapping={},loading=false;
  dialog.innerHTML=[
    '<form id="invoice-close-form" method="dialog">',
    '<div class="dialog-header"><h2>Conferir nota e finalizar</h2><button type="button" class="item-btn" data-dismiss>Fechar</button></div>',
    '<p class="info-line">Compare o que anotou no mercado com os valores do comprovante antes de salvar a compra no histórico.</p>',
    '<label>Enviar nota fiscal (XML, foto ou PDF)<input type="file" id="invoice-upload" accept=".xml,application/xml,text/xml,.pdf,application/pdf,image/jpeg,image/png,image/webp,image/heic,image/heif" /></label>',
    '<p class="invoice-format-note">XML NFC-e/NF-e: lê automaticamente produtos, quantidades e valores. Foto/PDF: guarda um anexo reduzido, mas não faz leitura automática. O XML é a melhor opção para conferir cada preço.</p>',
    '<label>Total que aparece na nota (R$)<input id="invoice-total" inputmode="decimal" placeholder="Ex.: 71,82" /></label>',
    '<label>Itens da nota (opcional para foto/PDF)<textarea id="invoice-lines" rows="3" placeholder="Arroz; 2; UN; 19,90&#10;Amaciante 5L; 1; UN; 24,90"></textarea></label>',
    '<button type="button" class="item-btn" id="invoice-manual-apply">Conferir itens digitados</button>',
    '<div id="invoice-compare" class="invoice-compare" aria-live="polite"></div>',
    '<div class="dialog-actions invoice-checkout-actions"><button type="button" class="item-btn" id="invoice-without">Salvar sem nota</button>',
    '<button type="submit" class="primary-btn" id="invoice-finish">Salvar compra e conferência</button></div>',
    '<p class="info-line">Uma divergência não é ocultada: ela ficará registrada no histórico. A validade fiscal da nota não é consultada na SEFAZ.</p>',
    '</form>'
  ].join("");
  dialog.showModal();
  const form=dialog.querySelector("form");
  const upload=form.querySelector("#invoice-upload"),totalInput=form.querySelector("#invoice-total"),
    linesInput=form.querySelector("#invoice-lines"),comparison=form.querySelector("#invoice-compare"),
    finish=form.querySelector("#invoice-finish");
  const getReport=()=>compareInvoice(bought,invoice,mapping);
  const row=lines=>lines.map(line=>{
    const selected=mapping[line.id]||"";
    const options=bought.map(e=>'<option value="'+esc(e.id)+'" '+(selected===e.id?"selected":"")+'>'+
      esc(e.title)+' · '+(Number(e.actualMilli??e.plannedMilli)/1000).toLocaleString("pt-BR")+
      " "+esc(e.unit)+'</option>').join("");
    const status=getReport().lines.find(r=>r.lineId===line.id);
    const statusText=status?.status==="matched"?"✓ Confere":
      status?.status==="different"?"⚠ Divergente":"Pendente";
    return '<div class="invoice-compare-line"><div><strong>'+esc(line.name)+'</strong><small>'+
      (line.quantityMilli/1000).toLocaleString("pt-BR")+' '+esc(line.unit)+
      ' · Nota: '+brl(line.netCents)+'</small></div>'+
      '<label>Corresponde a<select data-invoice-match="'+esc(line.id)+'">'+
      '<option value="">Selecione o item do carrinho</option>'+options+
      '<option value="__skip" '+(selected==="__skip"?"selected":"")+
      '>Item não registrado no carrinho</option></select></label>'+
      '<span class="invoice-compare-status">'+statusText+
      (status?.status==="different"?' · diferença '+brl(status.differenceCents):"")+
      '</span></div>';
  }).join("");
  function draw(){
    const report=getReport();
    if(!invoice){
      comparison.innerHTML='<div class="invoice-placeholder">Carrinho: <strong>'+brl(report.cartTotalCents)+
        '</strong>. Anexe a nota para comparar ou salve explicitamente sem nota.</div>';
      return;
    }
    const pending=report.pending+report.different+report.missing.length+report.skipped;
    comparison.innerHTML='<div class="invoice-summary"><strong>'+esc(labelInvoiceStatus(report.status))+
      '</strong><span>Seu carrinho: '+brl(report.cartTotalCents)+'</span><span>Total da nota: '+
      brl(report.invoiceTotalCents)+'</span>'+
      '<span>Diferença total: '+(report.differenceCents===null?"Não calculável":brl(report.differenceCents))+
      '</span><span>'+report.matched+' linha(s) conferida(s), '+pending+' pendência(s)</span></div>'+
      (invoice.lines?.length?'<div class="invoice-compare-list">'+row(invoice.lines)+'</div>':
        '<p class="invoice-format-note">Este anexo não contém itens extraídos. Informe o total e, se desejar, as linhas digitadas para confrontar os preços. A conferência ficará pendente sem XML.</p>')+
      (report.missing.length?'<p class="invoice-warning">'+report.missing.length+
        ' produto(s) do carrinho ainda não foram associados a uma linha da nota.</p>':"");
  }
  draw();
  upload.addEventListener("change",async()=>{
    const file=upload.files?.[0];if(!file)return;
    if(loading)return;
    loading=true;finish.disabled=true;
    comparison.innerHTML='<p class="info-line">Lendo arquivo…</p>';
    try{
      invoice=await readInvoiceFile(file);
      mapping=suggestInvoiceMappings(invoice.lines,bought);
      totalInput.value=invoice.totalCents==null?"":(invoice.totalCents/100).toFixed(2).replace(".",",");
      totalInput.readOnly=invoice.source==="xml";
      linesInput.value="";
      linesInput.disabled=invoice.source==="xml";
      notify(invoice.source==="xml"?"Valores da nota extraídos. Confira as associações.":"Comprovante preparado para o histórico; leitura dos itens não disponível.");
      draw();
    }catch(err){invoice=null;mapping={};upload.value="";notify(err.message,true);draw();}
    finally{loading=false;finish.disabled=false;}
  });
  totalInput.addEventListener("input",()=>{
    if(!invoice||invoice.source==="xml")return;
    try{invoice.totalCents=totalInput.value.trim()?moneyCents(totalInput.value):null;}catch{invoice.totalCents=null;}
    draw();
  });
  form.querySelector("#invoice-manual-apply").addEventListener("click",()=>{
    if(!invoice){notify("Anexe primeiro uma nota ou comprovante.",true);return;}
    if(invoice.source==="xml"){notify("Os itens já foram extraídos do XML; revise as associações.");return;}
    try{
      invoice.lines=parseManualInvoiceLines(linesInput.value);
      invoice.parseStatus="manually-entered";
      mapping=suggestInvoiceMappings(invoice.lines,bought);
      draw();
    }catch(err){notify(err.message,true);}
  });
  comparison.addEventListener("change",event=>{
    const id=event.target.dataset.invoiceMatch;
    if(!id)return;
    mapping[id]=event.target.value;
    draw();
  });
  form.querySelector("[data-dismiss]").addEventListener("click",()=>dialog.close());
  form.querySelector("#invoice-without").addEventListener("click",()=>{
    if(confirm("Salvar a compra sem comprovante? Os preços do mercado ficarão no histórico, mas sem conferência fiscal."))onFinish(null,{});
  });
  form.addEventListener("submit",event=>{
    event.preventDefault();
    if(loading)return;
    if(!invoice){notify("Anexe a nota ou use 'Salvar sem nota'.",true);return;}
    if(invoice.source!=="xml"&&totalInput.value.trim()){
      try{invoice.totalCents=moneyCents(totalInput.value);}catch(err){notify(err.message,true);return;}
    }
    const report=getReport();
    const warning=report.status==="matched"?"Conferência completa. Salvar compra e nota?":
      "Há diferenças ou dados ainda não conferidos. Eles serão salvos como pendentes no histórico, sem alterar os preços que você registrou. Continuar?";
    if(!confirm(warning))return;
    onFinish(invoice,mapping);
  });
}
