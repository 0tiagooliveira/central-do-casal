// Rascunhos ficam apenas neste navegador e são particionados por pessoa, casa e lista.
// A sincronização com o Firebase acontece somente após o usuário confirmar o lote.
import {normalizeGtin} from "./ean-catalog.js";
const norm=x=>String(x||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .toLocaleLowerCase("pt-BR").replace(/\s+/g," ").trim();
export function eanDraftKey(state) {
  if (!state?.uid || !state?.household || !["Mercado","Açougue"].includes(state.market)) return "";
  return ["cdc:ean:drafts:v1",state.uid,state.household,state.market].map(encodeURIComponent).join(":");
}
export function readEanDrafts(storage,key) {
  if (!key) return [];
  try {
    const raw=storage?.getItem(key);
    if (!raw || raw.length > 20000) return [];
    const data=JSON.parse(raw);
    if (!Array.isArray(data) || data.length > 40) return [];
    return data.filter(e=>normalizeGtin(e?.ean)===e.ean &&
      typeof e.title==="string" && e.title.trim().length>0 && e.title.length<=200 &&
      Number.isSafeInteger(e.plannedMilli) && e.plannedMilli>0 && e.plannedMilli<=100000000)
      .map(e=>({id:"ean:"+e.ean,ean:e.ean,title:e.title.trim(),
        unit:"un",plannedMilli:e.plannedMilli}));
  } catch { return []; }
}
export function addEanDraft(storage,key,{ean,title,quantity=1}) {
  const code=normalizeGtin(ean),name=String(title||"").trim();
  if (!key || !code || !name || name.length>200 || !Number.isInteger(quantity) ||
      quantity<1 || quantity>100) throw Error("Confira código, nome e quantidade (1 a 100).");
  const drafts=readEanDrafts(storage,key);
  const existing=drafts.find(p=>p.ean===code);
  if (!existing && drafts.length>=40) throw Error("Limite de 40 produtos no rascunho. Salve o lote antes de continuar.");
  const next=existing ? drafts.map(d=>d.ean===code?
    {...d,plannedMilli:Math.min(100000000,d.plannedMilli+quantity*1000)}:d) :
    [...drafts,{id:"ean:"+code,ean:code,title:name,unit:"un",plannedMilli:quantity*1000}];
  try { storage?.setItem(key,JSON.stringify(next)); }
  catch { throw Error("Armazenamento local cheio ou bloqueado. Não foi possível guardar o rascunho."); }
  return next;
}
export function clearEanDrafts(storage,key) {
  if (key) storage?.removeItem(key);
}
export function planEanDraftBatch(drafts,existing=[]) {
  const ids=new Set(),needed=[],skipped=[];
  for (const draft of drafts) {
    const ean=normalizeGtin(draft?.ean);
    const title=String(draft?.title||"").trim();
    if (!ean || !title || title.length>200 ||
        !Number.isSafeInteger(draft.plannedMilli) || draft.plannedMilli<=0 ||
        draft.plannedMilli>100000000) throw Error("Rascunho de código de barras inválido.");
    const dup=(existing||[]).some(e=>e.ean===ean ||
      (e.unit==="un" && norm(e.title)===norm(title)));
    if (ids.has(ean) || dup) skipped.push(draft);
    else { ids.add(ean);needed.push({id:"ean:"+ean,ean,title,unit:"un",
      plannedMilli:draft.plannedMilli}); }
  }
  return {needed,skipped};
}
