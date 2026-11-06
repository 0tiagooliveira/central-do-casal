// Prévia estritamente em memória contra um snapshot obtido explicitamente
// com getDocFromServer(). Não chama Firebase nem modifica localStorage.
import {inspectEmergencyBackup,replayEmergency} from "./emergency-cart.js";
import {same} from "./basket-concurrency.js";

const countBought=entries=>(entries||[]).filter(item=>item.bought).length;
export function previewBackupAgainstServer(file,state,remote){
  if(!remote||!Array.isArray(remote.entries)||!remote.generation)
    throw Error("Nenhuma cesta foi encontrada no servidor. Preserve o backup original.");
  // Não compara uma geração arquivada com uma compra nova.
  if(file?.baskets?.[file.market]?.generation!==remote.generation)
    throw Error("O servidor já contém outra compra. Nenhum dado será alterado.");
  const info=inspectEmergencyBackup(file,{
    ...state,baskets:{...state.baskets,[file.market]:remote}
  });
  if(!info.baseline)
    throw Error("O backup não contém uma base confiável. Exige conferência manual.");
  const draft={baseline:info.baseline,baseGeneration:info.basket.generation,
    operations:info.operations,basket:info.basket};
  const result=replayEmergency(remote,draft);
  const previous=new Map(remote.entries.map(item=>[item.id,item]));
  const next=new Map(result.entries.map(item=>[item.id,item]));
  const added=[...next.keys()].filter(id=>!previous.has(id)).length;
  const removed=[...previous.keys()].filter(id=>!next.has(id)).length;
  const updated=[...next.keys()].filter(id=>previous.has(id)&&
    !same(next.get(id),previous.get(id))).length;
  const budgetChanged=result.budget!==remote.budget;
  const unchanged=added===0&&updated===0&&removed===0&&!budgetChanged;
  return {
    status:unchanged?"already-synced":"merge-possible",
    cloudItems:remote.entries.length,cloudBought:countBought(remote.entries),
    backupItems:info.basket.entries.length,backupBought:info.purchasedCount,
    proposedItems:result.entries.length,proposedBought:countBought(result.entries),
    added,updated,removed,budgetChanged,
    // A comparação é uma prévia. A transação de envio relê o servidor.
    readOnly:true,shouldRecheckAtCommit:true
  };
}
