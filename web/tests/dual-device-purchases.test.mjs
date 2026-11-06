import test from "node:test";
import assert from "node:assert/strict";
import {same,editItem,deleteItem,editBudget,rebaseOfflineBasket,retryIfRevisionAdvanced} from "../basket-concurrency.js";
import {isRecoverableSyncError,replayEmergency} from "../emergency-cart.js";
import {needsSharedRecords,needsCatalogLinks} from "../firestore-listener-policy.js";
import {inspectLocalBackup} from "../../scripts/audit-local-backup.mjs";
import {previewBackupAgainstServer} from "../backup-server-preview.js";
import {createFirestoreUsageTracker} from "../firestore-usage-tracker.js";

const entry=(id,other={})=>({
  id,title:id,unit:"un",plannedMilli:1000,actualMilli:1000,
  estimated:500,actual:null,bought:false,unitReviewed:true,
  quantityReviewed:true,...other
});
const a=entry("arroz"), b=entry("leite");
const basket=(entries=[a,b],other={})=>({
  market:"Mercado",generation:"geracao-123",revision:8,budget:5000,entries,...other
});

test("two devices edit different products without losing either change",()=>{
  const original=basket();
  const deviceB={...b,bought:true,actual:399};
  const remote=basket([a,deviceB],{revision:9});
  const deviceA={...a,bought:true,actual:220};
  const merged=editItem(remote.entries,a,deviceA);
  assert.deepEqual(merged,[deviceA,deviceB]);
});

test("two devices can edit distinct fields of the same product",()=>{
  const remote=entry("arroz",{group:"Alimentos"});
  const local=entry("arroz",{actual:700,bought:true});
  const merged=editItem([remote],a,local);
  assert.equal(merged[0].group,"Alimentos");
  assert.equal(merged[0].actual,700);
});

test("a conflicting change of the same field requires confirmation",()=>{
  const remote=entry("arroz",{actual:620});
  const local=entry("arroz",{actual:700});
  assert.throws(()=>editItem([remote],a,local),/outro aparelho/);
  assert.equal(remote.actual,620);
});

test("deleting a changed item fails, unrelated changes survive",()=>{
  const remote=entry("arroz",{actual:800});
  assert.throws(()=>deleteItem([remote,b],a),/outro aparelho/);
  assert.deepEqual(deleteItem([a,entry("leite",{actual:400})],a),[entry("leite",{actual:400})]);
});

test("repeated exact mutation is a no-op and does not require a new write",()=>{
  const desired=entry("arroz",{bought:true,actual:300});
  assert.equal(same(editItem([desired],a,desired),[desired]),true);
  assert.deepEqual(editBudget({budget:6000},5000,6000),{budget:6000});
  assert.throws(()=>editBudget({budget:9000},5000,6000),/outro aparelho/);
});

test("offline journal merges unrelated remote purchases and is idempotent",()=>{
  const baseline=basket();
  const local=basket([entry("arroz",{bought:true,actual:200}),b]);
  const remote=basket([a,entry("leite",{bought:true,actual:350})],{revision:12});
  const once=rebaseOfflineBasket(remote,baseline,local);
  assert.equal(once.entries[0].actual,200);
  assert.equal(once.entries[1].actual,350);
  const twice=rebaseOfflineBasket(once,baseline,local);
  assert.deepEqual(twice,once);
});

test("offline replay recognizes an already-synced addition by its stable ID",()=>{
  const initial=basket([a]);
  const extra=entry("banana");
  const draft={
    baseGeneration:initial.generation,baseline:initial,
    operations:[{type:"add",entry:extra}],
    basket:basket([a,extra])
  };
  const once=replayEmergency(initial,draft);
  const twice=replayEmergency(once,draft);
  assert.deepEqual(once.entries,twice.entries);
  assert.equal(twice.entries.length,2);
});

test("offline replay never overwrites another phone's changed budget",()=>{
  const baseline=basket();
  const local=basket([a,b],{budget:4000});
  const remote=basket([a,b],{budget:8000,revision:9});
  assert.throws(()=>rebaseOfflineBasket(remote,baseline,local),/orçamento mudou/);
});

test("offline replay rejects altered journal and wrong shopping generation",()=>{
  const initial=basket([a]);
  const changed=basket([entry("arroz",{actual:200})]);
  const draft={baseGeneration:initial.generation,baseline:initial,
    basket:changed,operations:[]};
  assert.throws(()=>replayEmergency(initial,draft),/não correspondem/);
  assert.throws(()=>replayEmergency({...initial,generation:"outra"},draft),/finalizada/);
});

test("offline fallback handles quota and connectivity, never permission denial",()=>{
  for(const code of ["resource-exhausted","firestore/unavailable","deadline-exceeded","network-request-failed"])
    assert.equal(isRecoverableSyncError({code}),true,code);
  assert.equal(isRecoverableSyncError({code:"permission-denied"}),false);
});

test("shopping basket stays live without eagerly loading the heavy collections",()=>{
  assert.equal(needsSharedRecords("Compras","market"),false);
  assert.equal(needsCatalogLinks("Compras","market"),false);
  assert.equal(needsSharedRecords("Compras","list"),true);
  assert.equal(needsCatalogLinks("Compras","catalog"),true);
  assert.equal(needsSharedRecords("Hoje","catalog"),true);
  assert.equal(needsCatalogLinks("Hoje","catalog"),false);
});

test("a long offline shopping session with repeated edits reconstructs without duplicates",()=>{
  const baseline=basket(Array.from({length:56},(_,i)=>entry("fixture-"+i)));
  const ops=[];
  const finalEntries=baseline.entries.map(e=>({...e}));
  for(let n=0;n<26;n++){
    const index=n%20;
    const updated={...finalEntries[index],actual:100+n,bought:true};
    ops.push({type:"update",id:updated.id,entry:updated});
    finalEntries[index]=updated;
  }
  for(const id of ["new-item-a","new-item-b"]){
    const added=entry(id,{bought:true,actual:250});
    ops.push({type:"add",entry:added});
    finalEntries.push(added);
  }
  const draft={baseGeneration:baseline.generation,baseline,operations:ops,
    basket:basket(finalEntries)};
  const remote=basket(baseline.entries.map(e=>e.id==="fixture-55"?{...e,estimated:900}:e),
    {revision:9});
  const result=replayEmergency(remote,draft);
  assert.equal(result.entries.length,58);
  assert.equal(result.entries.find(e=>e.id==="fixture-55").estimated,900);
  assert.equal(result.entries.find(e=>e.id==="fixture-0").actual,120);
  assert.deepEqual(replayEmergency(result,draft).entries,result.entries);
});

test("local backup auditor reproduces 56 items and 28 operations with no Firebase calls",()=>{
  const original=basket(Array.from({length:56},(_,i)=>entry("synthetic-"+i)));
  const current=structuredClone(original),ops=[];
  for(let i=0;i<26;i++){
    const edited={...current.entries[i],bought:true,actual:105+i};
    current.entries[i]=edited;
    ops.push({type:"update",id:edited.id,entry:edited});
  }
  for(const name of ["new-a","new-b"]){
    const added=entry(name,{bought:true,actual:230});
    current.entries.push(added);
    ops.push({type:"add",entry:added});
  }
  const backup={type:"central-do-casal-emergency-backup",market:"Mercado",
    baseRevision:original.revision,baseline:original,operations:ops,
    baskets:{Mercado:current}};
  const stats=inspectLocalBackup(backup);
  assert.equal(stats.baselineItems,56);
  assert.equal(stats.operations,28);
  assert.equal(stats.outputItems,58);
  assert.equal(stats.purchasedItems,28);
  assert.equal(stats.matchesExport,true);
  assert.equal(stats.idempotent,true);
  assert.equal(stats.liveFirebaseChecked,false);
  assert.equal(stats.remoteStatus,"não fornecida");
  const remote={...structuredClone(original),generation:"outra-geracao"};
  assert.equal(inspectLocalBackup(backup,remote).remoteStatus,
    "geração diferente: sincronização bloqueada");
});

test("client-side quota diagnostics only count snapshots, never write to Firebase",()=>{
  const tracker=createFirestoreUsageTracker();
  tracker.onSnapshot("listas de compras",{
    docs:[{},{}],docChanges:()=>[{},{}],
    metadata:{fromCache:false,hasPendingWrites:false}
  });
  tracker.onSnapshot("listas de compras",{
    docs:[{},{}],docChanges:()=>[{}],
    metadata:{fromCache:true,hasPendingWrites:true}
  });
  tracker.onError("listas de compras",{code:"resource-exhausted",message:"No user details saved"});
  const data=tracker.summary();
  assert.equal(data.snapshots,2);
  assert.equal(data.changes,3);
  assert.equal(data.cacheSnapshots,1);
  assert.equal(data.pendingWritesSnapshots,1);
  assert.equal(data.errors,1);
  assert.equal(data.bySource[0].lastError,"resource-exhausted");
  assert.equal(JSON.stringify(data).includes("No user details"),false);
  tracker.reset();
  assert.equal(tracker.summary().snapshots,0);
});

test("read-only server comparison preserves independent remote edits and never mutates inputs",()=>{
  const baseline=basket([a,b]);
  const edited={...a,bought:true,actual:290};
  const newEntry=entry("extra",{bought:true,actual:430});
  const operations=[{type:"update",id:a.id,entry:edited},{type:"add",entry:newEntry}];
  const file={type:"central-do-casal-emergency-backup",household:"home-simulado",
    market:"Mercado",baseRevision:baseline.revision,baseline,operations,
    baskets:{Mercado:basket([edited,b,newEntry])}};
  const state={uid:"alice",household:"home-simulado",baskets:{}};
  const remote=basket([a,{...b,estimated:720}],{revision:11});
  const original=structuredClone(remote);
  const result=previewBackupAgainstServer(file,state,remote);
  assert.equal(result.status,"merge-possible");
  assert.equal(result.added,1);
  assert.equal(result.updated,1);
  assert.equal(result.removed,0);
  assert.equal(result.readOnly,true);
  assert.equal(result.shouldRecheckAtCommit,true);
  assert.deepEqual(remote,original);
  const applied=replayEmergency(remote,{baseline,baseGeneration:baseline.generation,
    operations,basket:file.baskets.Mercado});
  const repeated=previewBackupAgainstServer(file,state,applied);
  assert.equal(repeated.status,"already-synced");
});

test("read-only comparison rejects different purchase and conflicting field",()=>{
  const baseline=basket([a,b]);
  const bought={...a,bought:true,actual:200};
  const file={type:"central-do-casal-emergency-backup",household:"home-simulado",
    market:"Mercado",baseRevision:baseline.revision,baseline,
    operations:[{type:"update",id:a.id,entry:bought}],
    baskets:{Mercado:basket([bought,b])}};
  const state={uid:"alice",household:"home-simulado",baskets:{}};
  assert.throws(()=>previewBackupAgainstServer(file,state,null),/servidor/);
  assert.throws(()=>previewBackupAgainstServer(file,state,
    basket([a,b],{generation:"nova-geracao"})),/outra compra/);
  assert.throws(()=>previewBackupAgainstServer(file,state,
    basket([{...a,actual:600},b],{revision:9})),/outro aparelho/);
  assert.throws(()=>previewBackupAgainstServer(file,
    {...state,household:"outra-casa"},baseline),/mesma casa/);
});

test("permission-denied is retried only when the same basket revision really advanced",async()=>{
  const denied=()=>Object.assign(new Error("Denied"),{code:"permission-denied"});
  let writes=0,reads=0;
  const result=await retryIfRevisionAdvanced({
    operation:async()=>{writes++;if(writes===1)throw denied();return "salvo";},
    readLatest:async()=>{reads++;return {generation:"valid",revision:10};},
    observedGeneration:"valid",observedRevision:9,maxRetries:2
  });
  assert.equal(result,"salvo");
  assert.equal(writes,2);
  assert.equal(reads,1);
});

test("real permission denial and new shopping generation never trigger retry",async()=>{
  const denied=()=>Object.assign(new Error("Permissão negada"),{code:"permission-denied"});
  for(const latest of [
    {generation:"valid",revision:9},
    {generation:"other",revision:10},
    null
  ]){
    let writes=0;
    await assert.rejects(retryIfRevisionAdvanced({
      operation:async()=>{writes++;throw denied();},
      readLatest:async()=>latest,
      observedGeneration:"valid",observedRevision:9,maxRetries:2
    }),/Permissão negada/);
    assert.equal(writes,1);
  }
});

test("concurrent revision recovery has bounded retries and does not retry unrelated errors",async()=>{
  let reads=0,attempts=0;
  await assert.rejects(retryIfRevisionAdvanced({
    operation:async()=>{attempts++;throw Object.assign(new Error("Denied"),{code:"permission-denied"});},
    readLatest:async()=>({generation:"same",revision:++reads+4}),
    observedGeneration:"same",observedRevision:4,maxRetries:2
  }),/Denied/);
  assert.equal(attempts,3);
  assert.equal(reads,2);
  let called=false;
  await assert.rejects(retryIfRevisionAdvanced({
    operation:async()=>{throw Object.assign(new Error("Network"),{code:"unavailable"});},
    readLatest:async()=>{called=true;return null;},
    observedGeneration:"same",observedRevision:4
  }),/Network/);
  assert.equal(called,false);
});
