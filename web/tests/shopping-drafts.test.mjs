import test, {beforeEach} from "node:test";
import assert from "node:assert/strict";
import {
  saveEmergency,readEmergency,projectedShoppingState,
  replayEmergency,reconcileEmergencyAfterSync
} from "../emergency-cart.js";

const data=new Map();
globalThis.window={localStorage:{
  getItem:key=>data.get(key)??null,
  setItem:(key,value)=>{data.set(key,String(value));},
  removeItem:key=>data.delete(key)
}};
beforeEach(()=>data.clear());

const market="Mercado";
const entry=(id,title)=>({
  id,title,unit:"un",plannedMilli:1000,actualMilli:1000,
  estimated:100,bought:false,actual:null,
  unitReviewed:true,quantityReviewed:true,estimateKnown:true
});
const initial=()=>({market,generation:"g".repeat(32),revision:1,budget:1000,entries:[]});
const state=()=>({uid:"tiago",household:"casa",market,baskets:{Mercado:initial()}});
const add=(item)=>({type:"add",entry:item});

test("editar 3 produtos não modifica a lista do Firebase antes de salvar",()=>{
  const user=state();
  for(let i=0;i<3;i++)saveEmergency(user,market,add(entry("item"+i,"Produto "+i)));
  assert.equal(user.baskets.Mercado.entries.length,0);
  assert.equal(readEmergency(user,market).operations.length,3);
  assert.equal(projectedShoppingState(user).baskets.Mercado.entries.length,3);
});

test("lista nova permanece em rascunho com revisão zero",()=>{
  const user=state();user.baskets={};
  saveEmergency(user,market,add(entry("a","Arroz")));
  const draft=readEmergency(user,market);
  assert.equal(draft.baseRevision,0);
  assert.equal(draft.baseline.entries.length,0);
  assert.equal(draft.basket.entries.length,1);
  assert.equal(user.baskets.Mercado,undefined);
  assert.equal(draft.baseGeneration,draft.basket.generation);
});

test("um lote sincronizado pode ser retirado sem afetar os dados remotos",()=>{
  const user=state();
  saveEmergency(user,market,add(entry("a","Arroz")));
  saveEmergency(user,market,add(entry("b","Feijão")));
  const draft=readEmergency(user,market);
  const merged=replayEmergency(user.baskets.Mercado,draft);
  assert.equal(merged.entries.length,2);
  const result=reconcileEmergencyAfterSync(user,market,draft,{...merged,revision:2});
  assert.deepEqual(result,{status:"synced",pending:0});
  assert.equal(readEmergency(user,market),null);
});

test("nova alteração em outra aba sobrevive ao lote já enviado",()=>{
  const user=state();
  saveEmergency(user,market,add(entry("a","Arroz")));
  const sent=readEmergency(user,market);
  saveEmergency(user,market,add(entry("b","Feijão")));
  const remote={...replayEmergency(user.baskets.Mercado,sent),revision:2};
  const result=reconcileEmergencyAfterSync(user,market,sent,remote);
  assert.equal(result.pending,1);
  const remaining=readEmergency(user,market);
  assert.equal(remaining.baseRevision,2);
  assert.equal(remaining.operations.length,1);
  assert.deepEqual(remaining.basket.entries.map(x=>x.id),["a","b"]);
});

test("edição concorrente do mesmo produto não apaga o rascunho",()=>{
  const user=state();
  user.baskets.Mercado.entries=[entry("a","Arroz")];
  saveEmergency(user,market,{type:"update",id:"a",
    entry:{...entry("a","Arroz"),plannedMilli:2000}});
  const draft=readEmergency(user,market);
  const changed={...user.baskets.Mercado,revision:2,
    entries:[{...entry("a","Arroz"),plannedMilli:3000}]};
  assert.throws(()=>replayEmergency(changed,draft),/alterado em outro aparelho/);
  assert.equal(readEmergency(user,market).operations.length,1);
});

test("falha no armazenamento local não é confundida com lista salva",()=>{
  const user=state();
  const before=window.localStorage.setItem;
  window.localStorage.setItem=()=>{throw Error("Sem espaço");};
  try{
    assert.throws(()=>saveEmergency(user,market,add(entry("a","Arroz"))),/Não foi possível proteger o rascunho/);
    assert.equal(readEmergency(user,market),null);
  }finally{window.localStorage.setItem=before;}
});
