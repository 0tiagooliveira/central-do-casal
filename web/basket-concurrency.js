// Conciliação determinística: preservar mudanças de produtos diferentes e
// rejeitar apenas a edição concorrente do mesmo campo ou item.
const stable=v=>{
  if(Array.isArray(v))return v.map(stable);
  if(v&&typeof v==="object")return Object.fromEntries(
    Object.keys(v).sort().map(k=>[k,stable(v[k])])
  );
  return v;
};
export const same=(a,b)=>JSON.stringify(stable(a))===JSON.stringify(stable(b));
export const changedKeys=(before,after)=>[...new Set([...Object.keys(before||{}),...Object.keys(after||{})])]
  .filter(key=>key!=="id"&&!same(before?.[key],after?.[key]));
const conflict=()=>{throw Error("Este produto foi alterado no outro aparelho. Atualize a lista e confira antes de sobrescrever.");};

export function mergeItem(remote,observed,desired){
  if(!observed||!desired||observed.id!==desired.id||remote?.id!==observed.id)conflict();
  const keys=changedKeys(observed,desired);
  for(const key of keys)
    if(!same(remote[key],observed[key])&&!same(remote[key],desired[key]))conflict();
  const result={...remote};
  for(const key of keys)result[key]=desired[key];
  return result;
}
export function editItem(entries,observed,desired){
  if(!observed?.id)conflict();
  const current=entries.find(e=>e.id===observed.id);
  if(!current)conflict();
  const next=mergeItem(current,observed,desired);
  return entries.map(e=>e.id===observed.id?next:e);
}
export function deleteItem(entries,observed){
  if(!observed?.id)throw Error("Produto não localizado na lista aberta.");
  const current=entries.find(e=>e.id===observed.id);
  if(!current)return entries;
  if(!same(current,observed))conflict();
  return entries.filter(e=>e.id!==observed.id);
}
export function editBudget(remote,original,desired){
  if(!Number.isSafeInteger(desired)||desired<0)throw Error("Orçamento inválido.");
  if(remote.budget!==original&&remote.budget!==desired)
    throw Error("O orçamento foi modificado no outro aparelho. Confira antes de substituir.");
  return {...remote,budget:desired};
}
export function rebaseOfflineBasket(remote,baseline,local){
  if(remote.generation!==baseline.generation||local.generation!==baseline.generation)
    throw Error("A compra foi encerrada em outro aparelho. O backup foi preservado.");
  const before=new Map((baseline.entries||[]).map(e=>[e.id,e]));
  const desired=new Map((local.entries||[]).map(e=>[e.id,e]));
  const current=new Map((remote.entries||[]).map(e=>[e.id,e]));
  for(const [id,original] of before){
    const updated=desired.get(id),existing=current.get(id);
    if(!updated){
      if(existing&&!same(existing,original))conflict();
      current.delete(id);
    }else if(!existing){
      if(!same(original,updated))conflict();
    }else{
      current.set(id,mergeItem(existing,original,updated));
    }
  }
  for(const [id,item] of desired){
    if(before.has(id))continue;
    if(current.has(id)){if(!same(current.get(id),item))conflict();}
    else current.set(id,item);
  }
  let budget=remote.budget;
  if(!same(local.budget,baseline.budget)){
    if(!same(remote.budget,baseline.budget)&&!same(remote.budget,local.budget))
      throw Error("O orçamento mudou no outro aparelho. O backup foi preservado para revisão.");
    budget=local.budget;
  }
  return {...remote,budget,entries:[...current.values()]};
}
