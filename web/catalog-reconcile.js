import {doc,runTransaction,serverTimestamp} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";

/** User-confirmed link only. This never rewrites imported receipts or historical prices. */
export async function confirmCatalogAssociation(db,auth,getState,baseId,skuId){
  const state=getState(),uid=auth.currentUser?.uid;
  if(!uid||state.uid!==uid||!state.household)throw new Error("Entre no espaço compartilhado para associar produtos.");
  const base=state.shared.find(p=>p.id===baseId&&p.kind==="catalogItem"&&p.private!==true);
  const sku=state.shared.find(p=>p.id===skuId&&p.kind==="catalogSku"&&p.private!==true);
  if(!base||!sku)throw new Error("Item da base ou produto da nota não está mais disponível.");
  if(base.market!==sku.market)throw new Error("Os produtos precisam pertencer à mesma lista.");
  const existing=(state.links||[]).find(l=>l.id===skuId&&l.kind==="catalogLink");
  if(existing?.status==="confirmed"&&existing.baseItemId===baseId)return;
  if(existing)throw new Error("Este produto já tem uma decisão de associação. Revise-a no aplicativo Android antes de substituir.");
  const house=["households",state.household];
  const baseRef=doc(db,...house,"records",baseId);
  const skuRef=doc(db,...house,"records",skuId);
  const linkRef=doc(db,...house,"catalogLinks",skuId);
  await runTransaction(db,async tx=>{
    const baseSnap=await tx.get(baseRef);
    const skuSnap=await tx.get(skuRef);
    const linkSnap=await tx.get(linkRef);
    if(!baseSnap.exists()||baseSnap.data().kind!=="catalogItem"||baseSnap.data().private!==false)
      throw new Error("Item-base não encontrado neste espaço.");
    if(!skuSnap.exists()||skuSnap.data().kind!=="catalogSku"||skuSnap.data().private!==false)
      throw new Error("SKU da nota não encontrado neste espaço.");
    if(linkSnap.exists())throw new Error("A associação mudou em outro aparelho. Atualize a lista.");
    tx.set(linkRef,{kind:"catalogLink",private:false,skuId,baseItemId:baseId,status:"confirmed",
      revision:1,updatedBy:uid,updatedAt:serverTimestamp()});
  });
}
