import {lookupGtin,normalizeGtin} from "./ean-catalog.js";
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
export function installEanScanner({onDraft,notify}) {
  const dialog=document.createElement("dialog");
  dialog.className="shop-dialog ean-dialog";
  dialog.innerHTML=`<div class="dialog-header"><h2>📷 Identificar produto</h2>
      <button type="button" class="item-btn" data-ean-close>Fechar</button></div>
    <p>Escaneie a embalagem ou digite o EAN. Nenhum dado será enviado ao Firebase nesta etapa.</p>
    <form data-ean-form><label>Código de barras (EAN/GTIN)
      <input required data-ean-input inputmode="numeric" autocomplete="off" placeholder="Ex.: 7896006716112"></label>
      <div class="dialog-actions"><button type="button" class="item-btn" data-ean-camera>Usar câmera</button>
      <button type="submit" class="primary-btn" data-ean-search>Buscar produto</button></div></form>
    <video class="ean-video" data-ean-video autoplay playsinline muted hidden></video>
    <p data-ean-status role="status" aria-live="polite">Preço não identificado pelo EAN.</p>
    <div data-ean-result hidden></div>
    <small>Fonte: <a href="https://precomelhor.com.br/api-publica" target="_blank" rel="noopener noreferrer">PreçoMelhor</a>
      e <a href="https://world.openfoodfacts.org" target="_blank" rel="noopener noreferrer">Open Food Facts</a>.
      Preços externos não são usados como preço pago.</small>`;
  document.body.append(dialog);
  const field=dialog.querySelector("[data-ean-input]");
  const video=dialog.querySelector("[data-ean-video]");
  const status=dialog.querySelector("[data-ean-status]");
  const result=dialog.querySelector("[data-ean-result]");
  const searchButton=dialog.querySelector("[data-ean-search]");
  let stream=null,timer=null,requestToken=0,busy=false,active=false;
  function stopCamera() {
    active=false;
    if (timer!==null) clearTimeout(timer);
    timer=null;
    if (stream) stream.getTracks().forEach(track=>track.stop());
    stream=null;video.srcObject=null;video.hidden=true;
  }
  async function search() {
    if (busy)return;
    stopCamera();
    const ean=normalizeGtin(field.value);
    result.hidden=true;result.replaceChildren();
    if (!ean) {status.textContent="Código inválido. Digite um GTIN com dígito verificador correto.";return;}
    const token=++requestToken;
    busy=true;searchButton.disabled=true;status.textContent="Consultando catálogo local e bases públicas…";
    let record;
    try{record=await lookupGtin(ean);}
    catch(error){status.textContent=error.message||"Falha na consulta.";return;}
    finally{busy=false;searchButton.disabled=false;}
    if (!dialog.open || token!==requestToken)return;
    let name=record.product?.title||"";
    const source=record.product?.source||"manual";
    const hint=record.status==="found"?
      "Confira a descrição e a embalagem antes de adicionar.":
      record.status==="missing"?"Produto não encontrado. Informe a descrição manualmente.":
      "Não foi possível consultar as fontes. Digite a descrição e guarde o EAN.";
    status.textContent=(record.fromCache?"Resultado do cache. ":"")+hint;
    const photo=record.product?.imageUrl ?
      `<img class="ean-photo" src="${esc(record.product.imageUrl)}" alt="Foto do produto" loading="lazy" referrerpolicy="no-referrer">`:"";
    const brand=record.product?.brand?`<small>Marca: ${esc(record.product.brand)}</small>`:"";
    const amount=record.product?.package?`<small>Embalagem: ${esc(record.product.package)}</small>`:"";
    result.innerHTML=`<div class="ean-product">${photo}<div><strong>EAN: ${esc(ean)}</strong>${brand}${amount}
      <small>Origem: ${source==="precomelhor"?"PreçoMelhor":source==="openfoodfacts"?"Open Food Facts":"Informação manual"}</small></div></div>
      <form data-ean-draft><label>Nome do produto<input name="name" maxlength="200" required
        value="${esc(name)}" placeholder="Informe nome, marca e tamanho"></label>
        <label>Quantidade de embalagens<input type="number" name="quantity" min="1" max="100" step="1" value="1" required></label>
        <p class="info-line">Sem preço confirmado. O produto ficará num rascunho local até você salvar o lote.</p>
        <div class="dialog-actions"><button class="primary-btn" type="submit">Adicionar ao rascunho</button></div></form>`;
    result.hidden=false;
    result.querySelector("[data-ean-draft]").addEventListener("submit",ev=>{
      ev.preventDefault();
      try{
        const form=ev.currentTarget,qty=Number(form.elements.quantity.value);
        const title=form.elements.name.value.trim();
        onDraft({ean,title,source,quantity:qty});
        dialog.close();
      }catch(e){notify(e.message||"Não foi possível salvar o rascunho.",true);}
    });
  }
  async function camera() {
    stopCamera();
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia ||
        typeof window.BarcodeDetector!=="function") {
      status.textContent="Leitura por câmera indisponível neste navegador. Digite o EAN manualmente.";return;
    }
    try {
      const supported=await window.BarcodeDetector.getSupportedFormats();
      const formats=["ean_13","ean_8","upc_a","itf"].filter(f=>supported.includes(f));
      if (!formats.length)throw Error("Este navegador não reconhece códigos EAN.");
      const detector=new window.BarcodeDetector({formats});
      const incoming=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:"environment"}}});
      if(!dialog.open){incoming.getTracks().forEach(t=>t.stop());return;}
      stream=incoming;active=true;video.hidden=false;video.srcObject=stream;
      await video.play();
      status.textContent="Aponte para o código da embalagem. A câmera não grava imagens.";
      async function tick() {
        if(!active||!dialog.open)return;
        try{
          if(video.readyState>=2){
            const codes=await detector.detect(video);
            const found=codes.map(x=>normalizeGtin(x.rawValue)).find(Boolean);
            if(found){field.value=found;await search();return;}
          }
        }catch{}
        if(active)timer=setTimeout(tick,260);
      }
      timer=setTimeout(tick,260);
    }catch(e){stopCamera();status.textContent="Câmera não iniciada. Autorize o acesso no Chrome ou digite o EAN.";}
  }
  dialog.querySelector("[data-ean-close]").addEventListener("click",()=>dialog.close());
  dialog.querySelector("[data-ean-camera]").addEventListener("click",camera);
  dialog.querySelector("[data-ean-form]").addEventListener("submit",event=>{event.preventDefault();search();});
  dialog.addEventListener("close",()=>{requestToken++;stopCamera();});
  dialog.addEventListener("cancel",stopCamera);
  document.addEventListener("visibilitychange",()=>{if(document.hidden)stopCamera();});
  return {open(){
    if(dialog.open)return;
    result.replaceChildren();result.hidden=true;field.value="";
    status.textContent="Escaneie ou digite o EAN para começar.";
    dialog.showModal();field.focus();
  },close(){if(dialog.open)dialog.close();}};
}
