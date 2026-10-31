// Contadores rápidos, independentes do Firebase: sempre usam a cesta exibida
// (inclusive alterações salvas SOMENTE no aparelho).
export const valueOfEntry=(entry,cents=entry.actual)=>{
  const quantity=Number(entry.actualMilli??entry.plannedMilli??1000);
  const price=Number(cents);
  return Number.isFinite(price)&&Number.isFinite(quantity)?
    Math.round(price*quantity/1000):0;
};
export function marketTotals(basket={},drafts={}) {
  const entries=Array.isArray(basket.entries)?basket.entries:[];
  let spent=0,previewSpent=0,possiblePending=0,previewPendingCount=0,
    hasUnsavedChanges=false;
  for(const e of entries){
    if(e.bought){
      const actual=valueOfEntry(e);
      spent+=actual;
      if(Object.prototype.hasOwnProperty.call(drafts,e.id)&&
        Number.isSafeInteger(drafts[e.id])){
        const preview=valueOfEntry(e,drafts[e.id]);
        previewSpent+=preview;
        hasUnsavedChanges ||= preview!==actual;
      }else previewSpent+=actual;
    }else if(Object.prototype.hasOwnProperty.call(drafts,e.id)&&
      Number.isSafeInteger(drafts[e.id])){
      possiblePending+=valueOfEntry(e,drafts[e.id]);previewPendingCount++;
    }
  }
  const budget=Number(basket.budget)||0;
  return {spent,previewSpent,budget,remaining:budget-spent,
    previewRemaining:budget-previewSpent,possiblePending,
    previewPendingCount,hasUnsavedChanges,
    possibleAfterBuying:previewSpent+possiblePending};
}
