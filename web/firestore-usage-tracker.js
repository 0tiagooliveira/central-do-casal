// Telemetria SOMENTE em memória, sem Firestore extra e sem dados de clientes.
// NÃO é cobrança ou quota oficial: documentChanges mede entrega ao navegador.
const toCount=value=>Number.isFinite(value)&&value>=0?Math.trunc(value):0;
export function createFirestoreUsageTracker(){
  const counts=new Map();
  const current=source=>{
    if(!counts.has(source))counts.set(source,{snapshots:0,changes:0,cacheSnapshots:0,
      pendingWritesSnapshots:0,errors:0,lastError:""});
    return counts.get(source);
  };
  return {
    onSnapshot(source,snapshot){
      const row=current(source);
      row.snapshots+=1;
      if(Array.isArray(snapshot?.docs)){
        try{row.changes+=toCount(snapshot.docChanges().length);}
        catch{row.changes+=toCount(snapshot.docs.length);}
      }else if(typeof snapshot?.exists==="function"&&snapshot.exists()){
        row.changes+=1;
      }
      if(snapshot?.metadata?.fromCache)row.cacheSnapshots+=1;
      if(snapshot?.metadata?.hasPendingWrites)row.pendingWritesSnapshots+=1;
    },
    onError(source,error){
      const row=current(source);row.errors+=1;
      // Não registrar mensagens, URLs, IDs, payloads, nem dados da família.
      row.lastError=String(error?.code||"unknown").slice(0,40);
    },
    summary(){
      const bySource=[...counts.entries()].map(([source,counts])=>({source,...counts}));
      return {
        snapshots:bySource.reduce((n,v)=>n+v.snapshots,0),
        changes:bySource.reduce((n,v)=>n+v.changes,0),
        cacheSnapshots:bySource.reduce((n,v)=>n+v.cacheSnapshots,0),
        pendingWritesSnapshots:bySource.reduce((n,v)=>n+v.pendingWritesSnapshots,0),
        errors:bySource.reduce((n,v)=>n+v.errors,0),
        bySource
      };
    },
    reset(){counts.clear();}
  };
}
