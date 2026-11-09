import test from "node:test";
import assert from "node:assert/strict";
import {queryCatalog} from "../catalog.js";

const items=[
  {title:"Arroz Branco 5 KG",category:"Alimentos",store:"Mercado Azul"},
  {title:"Sabão Líquido 2 L",category:"Limpeza",store:"Loja Central"},
  {title:"Café Torrado 500 g",category:"Bebidas",brand:"Café Bom"}
];

test("pesquisa encontra palavras em ordem diferente",()=>{
  assert.deepEqual(queryCatalog(items,"5kg arroz branco","Todas").map(x=>x.title),["Arroz Branco 5 KG"]);
  assert.deepEqual(queryCatalog(items,"branco arroz","Todas").map(x=>x.title),["Arroz Branco 5 KG"]);
});
test("pesquisa ignora acentos e combina categoria com texto",()=>{
  assert.deepEqual(queryCatalog(items,"sabao liquido","Limpeza").map(x=>x.title),["Sabão Líquido 2 L"]);
  assert.deepEqual(queryCatalog(items,"sabao liquido","Alimentos"),[]);
});
test("pesquisa procura também marca, loja e unidade",()=>{
  assert.deepEqual(queryCatalog(items,"cafe bom","Todas").map(x=>x.title),["Café Torrado 500 g"]);
  assert.deepEqual(queryCatalog(items,"mercado branco","Todas").map(x=>x.title),["Arroz Branco 5 KG"]);
});
