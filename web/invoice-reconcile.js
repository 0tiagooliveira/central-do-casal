// Compara valores observados no mercado com linhas fiscais. Sem arredondar
// quantidades silenciosamente, sem casar por nome parecido e sem dizer que
// foto/PDF foram lidos automaticamente.
import {normalizeCatalogText,normalizeCatalogUnit} from "./catalog.js";
const norm=v=>normalizeCatalogText(v).replace(/\s+/g," ");
const cents=(price,qtyMilli)=>Math.round(Number(price||0)*Number(qtyMilli||0)/1000);
const fiscalUnit=u=>normalizeCatalogUnit(u)==="Escolher"?"":normalizeCatalogUnit(u);
export function suggestInvoiceMappings(lines=[],entries=[]) {
  const result={};const used=new Set();
  for(const line of lines){
    const matches=entries.filter(e=>e.bought&&
      norm(e.title)===norm(line.name)&&fiscalUnit(line.unit)===e.unit&&
      !used.has(e.id));
    if(matches.length===1){result[line.id]=matches[0].id;used.add(matches[0].id);}
  }
  return result;
}
export function compareInvoice(entries=[],invoice=null,mapping={}) {
  const bought=entries.filter(e=>e.bought);
  const inCartTotal=bought.reduce((n,e)=>n+cents(e.actual,e.actualMilli??e.plannedMilli),0);
  if(!invoice)return {status:"no-invoice",cartTotalCents:inCartTotal,invoiceTotalCents:null,
    differenceCents:null,matched:0,different:0,pending:0,skipped:0,missing:[],
    lines:[],totalMatches:false};
  const total=Number.isSafeInteger(invoice.totalCents)?invoice.totalCents:null;
  const hasLines=Array.isArray(invoice.lines)&&invoice.lines.length>0;
  const used=new Set();let matched=0,different=0,pending=0,skipped=0;
  const lines=(hasLines?invoice.lines:[]).map(line=>{
    const dest=mapping[line.id]||"";
    if(dest==="__skip"){skipped++;return {lineId:line.id,name:line.name,entryId:"",
      unit:line.unit,quantityMilli:line.quantityMilli,noteTotalCents:line.netCents,
      cartTotalCents:null,differenceCents:null,status:"ignored"};}
    const entry=bought.find(x=>x.id===dest);
    if(!entry||used.has(dest)){pending++;return {lineId:line.id,name:line.name,entryId:"",
      unit:line.unit,quantityMilli:line.quantityMilli,noteTotalCents:line.netCents,
      cartTotalCents:null,differenceCents:null,status:"unmatched"};}
    used.add(dest);
    const cartTotal=cents(entry.actual,entry.actualMilli??entry.plannedMilli),
      lineTotal=Number(line.netCents),
      delta=cartTotal-lineTotal;
    const unitMatch=fiscalUnit(line.unit)===entry.unit,
      quantityMatch=Math.abs(Number(line.quantityMilli)-Number(entry.actualMilli??entry.plannedMilli))<=1;
    const okay=unitMatch&&quantityMatch&&Math.abs(delta)<=1;
    if(okay)matched++;else different++;
    return {lineId:line.id,name:line.name,entryId:entry.id,
      unit:line.unit,quantityMilli:line.quantityMilli,
      noteTotalCents:lineTotal,cartTotalCents:cartTotal,differenceCents:delta,
      cartUnit:entry.unit,cartQuantityMilli:entry.actualMilli??entry.plannedMilli,
      status:okay?"matched":"different"};
  });
  const missing=bought.filter(e=>!used.has(e.id)).map(e=>({id:e.id,title:e.title,
    totalCents:cents(e.actual,e.actualMilli??e.plannedMilli)}));
  const differenceCents=total===null?null:inCartTotal-total;
  const totalMatches=differenceCents!==null&&Math.abs(differenceCents)<=1;
  const fullyChecked=hasLines&&matched===lines.length&&missing.length===0&&totalMatches&&
    invoice.parseStatus==="extracted";
  let status="pending";
  if(fullyChecked)status="matched";
  else if((different>0||!totalMatches&&total!==null)&&hasLines)status="differences";
  else if(hasLines&&pending>0||missing.length>0)status="pending";
  return {status,cartTotalCents:inCartTotal,invoiceTotalCents:total,differenceCents,
    totalMatches,matched,different,pending,skipped,missing,lines,
    parsedLines:hasLines,source:invoice.source||"manual"};
}
export function labelInvoiceStatus(status) {
  return ({matched:"Preços conferidos com o XML",differences:"Divergências encontradas",
    pending:"Conferência pendente", "no-invoice":"Sem nota anexada"})[status]||"Conferência pendente";
}
