import test from "node:test";
import assert from "node:assert/strict";
import {same,editItem,deleteItem,editBudget,rebaseOfflineBasket} from "../basket-concurrency.js";
import {isRecoverableSyncError,replayEmergency} from "../emergency-cart.js";
import {needsSharedRecords,needsCatalogLinks} from "../firestore-listener-policy.js";

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
