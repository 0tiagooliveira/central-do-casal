// Nota fiscal brasileira (NFC-e / NF-e) em XML. Sem rede, sem OCR e sem
// consulta à SEFAZ: identifica apenas o que consta no arquivo enviado.
const children=(node,name)=>Array.from(node?.children||[]).filter(n=>n.localName===name);
const first=(node,name)=>children(node,name)[0]||null;
const txt=(node,name)=>first(node,name)?.textContent?.trim()||"";
const number=v=>/^\d+(?:\.\d{1,5})?$/.test(String(v||"").trim())?Number(v):null;
const cents=v=>{const n=number(v);return n===null?null:Math.round(n*100);};
const ymd=v=>/^\d{4}-\d{2}-\d{2}/.test(v)?v.slice(0,10):"";
export function parseFiscalXml(xml) {
  if(typeof xml!=="string"||xml.length>150000||xml.length<50)throw new Error("XML vazio ou grande demais (máximo 150 KB).");
  if(/<!DOCTYPE|<!ENTITY/i.test(xml))throw new Error("XML com entidades externas não é aceito.");
  const doc=new DOMParser().parseFromString(xml,"application/xml");
  if(doc.getElementsByTagName("parsererror").length)throw new Error("O arquivo não é um XML válido.");
  const info=Array.from(doc.getElementsByTagName("*")).find(x=>x.localName==="infNFe");
  if(!info)throw new Error("Este XML não contém uma NFC-e/NF-e com produtos (infNFe).");
  const items=children(info,"det").map((det,index)=>{
    const product=first(det,"prod"),name=txt(product,"xProd");
    const quantity=number(txt(product,"qCom")),unit=txt(product,"uCom").toUpperCase().trim();
    const gross=cents(txt(product,"vProd")),discount=cents(txt(product,"vDesc"))||0,
      extras=cents(txt(product,"vOutro"))||0;
    const net=gross===null?null:gross-discount+extras;
    if(!name||!quantity||quantity<=0||!unit||gross===null||gross<0||net<0)
      throw new Error("A nota contém uma linha sem preço, quantidade ou unidade válidos.");
    const code=txt(product,"cProd"),gtin=txt(product,"cEAN");
    return {id:String(det.getAttribute("nItem")||index+1),name:name.slice(0,200),
      quantityMilli:Math.round(quantity*1000),unit:unit.slice(0,16),
      unitCents:Math.round(gross/quantity),grossCents:gross,discountCents:discount,
      extrasCents:extras,netCents:net,code:code.slice(0,50),gtin:gtin.slice(0,40)};
  });
  if(!items.length||items.length>250)throw new Error("A nota não contém uma quantidade válida de produtos.");
  const ide=first(info,"ide"),emit=first(info,"emit"),totals=first(info,"total"),
    icms=first(totals,"ICMSTot");
  const total=cents(txt(icms,"vNF"));
  if(total===null||total<0)throw new Error("O XML não informa o valor total da nota (vNF).");
  const id=String(info.getAttribute("Id")||"").replace(/^NFe/,"");
  const accessKey=/^\d{44}$/.test(id)?id:"";
  const issued=ymd(txt(ide,"dhEmi")||txt(ide,"dEmi"));
  const numberNF=txt(ide,"nNF").slice(0,30);
  return {source:"xml",format:"nfce-nfe-xml",issuer:txt(emit,"xNome").slice(0,140),
    taxId:txt(emit,"CNPJ").slice(0,14),date:issued,number:numberNF,
    accessKey,totalCents:total,lines:items,parseStatus:"extracted",
    note:"Valores extraídos do XML enviado; autenticidade fiscal não verificada na SEFAZ."};
}
export function parseManualInvoiceLines(input) {
  const raw=String(input||"").trim();
  if(!raw)return [];
  const rows=raw.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
  if(rows.length>200)throw new Error("Limite de 200 linhas de nota digitadas.");
  return rows.map((row,index)=>{
    const parts=row.split(";").map(x=>x.trim());
    if(parts.length!==4)throw new Error("Linha "+(index+1)+": use Nome; quantidade; unidade; preço unitário.");
    const [name,qty,unit,money]=parts;
    const quantity=Number(qty.replace(",","."));
    if(!name||!Number.isFinite(quantity)||quantity<=0||!unit)throw new Error("Confira os dados na linha "+(index+1)+".");
    const cleaned=money.replace(/^R\$\s*/,"").replace(/\s/g,"");
    const priceText=cleaned.includes(",")?cleaned.replace(/\./g,"").replace(",","."):cleaned;
    const price=Number(priceText);
    if(!Number.isFinite(price)||price<0||price>1000000)throw new Error("Preço inválido na linha "+(index+1)+".");
    const unitCents=Math.round(price*100),quantityMilli=Math.round(quantity*1000);
    return {id:String(index+1),name:name.slice(0,200),quantityMilli,
      unit:unit.slice(0,16).toUpperCase(),unitCents,
      grossCents:Math.round(unitCents*quantityMilli/1000),
      discountCents:0,extrasCents:0,
      netCents:Math.round(unitCents*quantityMilli/1000),code:"",gtin:""};
  });
}
