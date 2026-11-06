// A compra ao vivo precisa apenas do documento da cesta. Catálogo e
// histórico são carregados na primeira tela que realmente os utiliza.
export function needsSharedRecords(tab,shopMode){
  return !(tab==="Compras"&&shopMode==="market");
}
export function needsCatalogLinks(tab,shopMode){
  return tab==="Compras"&&["catalog","list","review"].includes(shopMode);
}
