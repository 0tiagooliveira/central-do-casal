// Navegação por URL: mantém a tela durante refresh, links compartilhados e Voltar.
// Nunca armazena credenciais ou dados do Firebase.
const tabToSlug={"Hoje":"hoje","Compras":"compras","Rotina":"rotina","Planos":"planos","Nós":"nos"};
const slugToTab=Object.fromEntries(Object.entries(tabToSlug).map(([tab,slug])=>[slug,tab]));
const modeToSlug={catalog:"catalogo",list:"minha-lista",market:"no-mercado"};
const slugToMode=Object.fromEntries(Object.entries(modeToSlug).map(([mode,slug])=>[slug,mode]));

export function parseAppRoute(hash="") {
  const parts=String(hash||"").replace(/^#\/?/,"").split("/").filter(Boolean);
  const tab=slugToTab[parts[0]]||"Hoje";
  const market=parts[1]==="acougue"?"Açougue":"Mercado";
  const shopMode=slugToMode[parts[2]]||"catalog";
  const catalogSource=parts[3]==="por-loja"?"store":"base";
  const marketSection=parts[3]==="carrinho"?"bought":"pending";
  return {tab,market,shopMode,catalogSource,marketSection};
}
export function formatAppRoute(route) {
  const tab=tabToSlug[route.tab]||"hoje";
  if(tab!=="compras")return "#/"+tab;
  const market=route.market==="Açougue"?"acougue":"mercado";
  const mode=modeToSlug[route.shopMode]||"catalogo";
  const ending=mode==="catalogo"?(route.catalogSource==="store"?"por-loja":"minha-base"):
    mode==="no-mercado"?(route.marketSection==="bought"?"carrinho":"falta-comprar"):"";
  return "#/compras/"+market+"/"+mode+(ending?"/"+ending:"");
}
export function writeAppRoute(route,{replace=false}={}) {
  const target=formatAppRoute(route);
  if(window.location.hash===target)return;
  if(replace)window.history.replaceState(null,"",target);
  else window.history.pushState(null,"",target);
}
