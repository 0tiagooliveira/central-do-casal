import test from "node:test";
import assert from "node:assert/strict";
import {purchaseDateInBrazil,isValidPurchaseDate,resolvePurchaseDate} from "../purchase-date.js";

test("nota sugere a data da compra em vez da data de salvamento",()=>{
  const today=new Date("2026-11-09T21:00:00Z");
  assert.deepEqual(resolvePurchaseDate({date:"2026-11-08"},"",today),
    {date:"2026-11-08",source:"nota"});
});
test("edição explícita corrige o dia, mantendo a emissão na nota",()=>{
  assert.deepEqual(resolvePurchaseDate({date:"2026-11-09"},"2026-11-08"),
    {date:"2026-11-08",source:"manual"});
});
test("sem nota usa o horário local do Brasil",()=>{
  const instant=new Date("2026-11-09T01:00:00Z");
  assert.equal(purchaseDateInBrazil(instant),"2026-11-08");
  assert.deepEqual(resolvePurchaseDate(null,"",instant),
    {date:"2026-11-08",source:"fechamento"});
});
test("não aceita dias inexistentes nem formato inválido",()=>{
  assert.equal(isValidPurchaseDate("2026-02-30"),false);
  assert.equal(isValidPurchaseDate("2026-11-08"),true);
  assert.throws(()=>resolvePurchaseDate(null,"08/11/2026"),/Data inválida/);
});
