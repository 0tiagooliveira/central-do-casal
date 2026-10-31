// Conciliação por campo: duas pessoas podem mudar produtos distintos
// (ou campos distintos do mesmo produto) sem sobrescrever uma à outra.
// Se as duas mudarem o MESMO campo, para e pede revisão em vez de perder dados.
const read=(obj,key)=>Object.prototype.hasOwnProperty.call(obj||{},key)?obj[key]:undefined;
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
export function changedEntryFields(baseline,desired){
  const fields={};
  for(const [key,value] of Object.entries(desired||{})){
    if(key==="id")continue;
    if(!equal(value,read(baseline,key)))fields[key]=value;
  }
  return fields;
}
export function applyConcurrentEntryEdit(latest,baseline,desired){
  if(!latest||!baseline||latest.id!==baseline.id)
    throw new Error("Este produto foi removido ou alterado. Atualize antes de tentar novamente.");
  const patch=changedEntryFields(baseline,desired);
  for(const [field,value] of Object.entries(patch)){
    const from=read(baseline,field),now=read(latest,field);
    if(!equal(now,from)&&!equal(now,value))
      throw new Error("Vocês alteraram "+field+" do mesmo produto simultaneamente. Confira o valor atualizado antes de salvar.");
  }
  return {...latest,...patch};
}
