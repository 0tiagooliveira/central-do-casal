import test from "node:test";
import assert from "node:assert/strict";
import { formatAppRoute, parseAppRoute } from "../navigation.js";

test("all five main tabs survive a URL round trip", () => {
  for (const tab of ["Hoje", "Compras", "Rotina", "Planos", "Nós"]) {
    const url = formatAppRoute({ tab });
    assert.equal(parseAppRoute(url).tab, tab);
  }
});

test("butcher cart deep link preserves the market and shopping mode", () => {
  const url = formatAppRoute({
    tab: "Compras", market: "Açougue",
    shopMode: "market", marketSection: "bought"
  });
  assert.equal(url, "#/compras/acougue/no-mercado/carrinho");
  assert.deepEqual(parseAppRoute(url), {
    tab: "Compras", market: "Açougue", shopMode: "market",
    catalogSource: "base", marketSection: "bought"
  });
});

test("catalog source is retained for a direct link", () => {
  const url = formatAppRoute({
    tab: "Compras", market: "Mercado", shopMode: "catalog", catalogSource: "store"
  });
  assert.equal(url, "#/compras/mercado/catalogo/por-loja");
  assert.equal(parseAppRoute(url).catalogSource, "store");
});

test("unknown paths safely fall back to Hoje", () => {
  assert.equal(parseAppRoute("#/not-a-real-module").tab, "Hoje");
  assert.equal(parseAppRoute("#/compras/desconhecido").market, "Mercado");
});
