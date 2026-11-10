import test from "node:test";
import assert from "node:assert/strict";
import {normalizeGtin,lookupGtin,fromOpenFoodFacts,fromPrecoMelhor} from "../web/ean-catalog.js";
import {eanDraftKey,readEanDrafts,addEanDraft,clearEanDrafts,planEanDraftBatch} from "../web/ean-drafts.js";

const storage=()=>{const values=new Map();return {
  getItem:key=>values.get(key)??null,
  setItem:(key,v)=>values.set(key,v),
  removeItem:key=>values.delete(key)
}};
const ean="7896006716112";
test("GTIN: aceita dígito de verificação válido e rejeita códigos internos",()=>{
  assert.equal(normalizeGtin("7896 0067 1611-2"),ean);
  assert.equal(normalizeGtin("17246"),"");
  assert.equal(normalizeGtin("7896006716113"),"");
  assert.equal(normalizeGtin("7896006716112<script>"),"");
});
test("PreçoMelhor: extrai produto sem assumir preço ou transformar código interno em EAN",()=>{
  const p=fromPrecoMelhor({product:{ean,product_name:"Arroz Camil 1kg",brand:"Camil",image_url:"http://unsafe.test/a.jpg"}},ean);
  assert.equal(p.title,"Arroz Camil 1kg");
  assert.equal(p.imageUrl,"");
  assert.equal(p.price,undefined);
  assert.equal(fromPrecoMelhor({product:{ean:"123",product_name:"Falso"}},ean),null);
});
test("Open Food Facts: respeita status e preserva origem",()=>{
  assert.equal(fromOpenFoodFacts({status:0,product:{product_name:"X"}},ean),null);
  assert.equal(fromOpenFoodFacts({status:1,code:ean,product:{product_name_pt:"Leite integral"}},ean).source,"openfoodfacts");
});
test("consultas: cache elimina requisições repetidas e nenhum dado é salvo no Firebase",async()=>{
  const cache=storage();let requests=0;
  const fake=async()=>{requests++;return {ok:true,status:200,json:async()=>({product:{ean,product_name:"Arroz 1kg"}})};};
  const first=await lookupGtin(ean,{storage:cache,fetchImpl:fake,now:10000});
  const second=await lookupGtin(ean,{storage:cache,fetchImpl:fake,now:20000});
  assert.equal(first.status,"found");
  assert.equal(second.fromCache,true);
  assert.equal(requests,1);
});
test("consulta usa Open Food Facts somente depois de resposta vazia do PreçoMelhor",async()=>{
  let calls=0;
  const fetchImpl=async()=>{calls++;return {ok:true,status:200,json:async()=>calls===1?{}:
    {status:1,code:ean,product:{product_name:"Chocolate",brands:"Marca"}}};};
  const result=await lookupGtin(ean,{storage:storage(),fetchImpl});
  assert.equal(result.product.source,"openfoodfacts");
  assert.equal(calls,2);
});
test("falha de rede mantém cadastro manual possível e nunca inventa nome ou preço",async()=>{
  const result=await lookupGtin(ean,{storage:storage(),fetchImpl:async()=>{throw Error("Offline");}});
  assert.equal(result.status,"unavailable");
  assert.equal(result.product,null);
});
test("rascunhos são isolados entre casas, contam quantidades e não sincronizam sem confirmação",()=>{
  const cache=storage();
  const key=eanDraftKey({uid:"pessoa",household:"casa-A",market:"Mercado"});
  const other=eanDraftKey({uid:"pessoa",household:"casa-B",market:"Mercado"});
  addEanDraft(cache,key,{ean,title:"Arroz 1 kg",quantity:1});
  addEanDraft(cache,key,{ean,title:"Arroz 1 kg",quantity:2});
  assert.equal(readEanDrafts(cache,key)[0].plannedMilli,3000);
  assert.deepEqual(readEanDrafts(cache,other),[]);
  clearEanDrafts(cache,key);
  assert.deepEqual(readEanDrafts(cache,key),[]);
});
test("lote ignora EAN e nome já existentes, sem inserir duplicatas",()=>{
  const drafts=[{ean,title:"Arroz 1 kg",plannedMilli:2000}];
  assert.equal(planEanDraftBatch(drafts,[{ean,title:"Outro",unit:"un"}]).needed.length,0);
  assert.equal(planEanDraftBatch(drafts,[{title:"ARROZ 1 KG",unit:"un"}]).needed.length,0);
  assert.equal(planEanDraftBatch(drafts,[]).needed[0].id,"ean:"+ean);
});
