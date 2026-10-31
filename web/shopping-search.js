// Filtro local, instantâneo, sem consultar/alterar Firestore.
// Busca sem acentos e por mais de uma palavra, independente da ordem digitada.
export const normalizeShoppingSearch=value=>String(value??"")
  .normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .toLocaleLowerCase("pt-BR").replace(/\s+/g," ").trim();

export function filterShoppingEntries(entries=[],search="") {
  const tokens=normalizeShoppingSearch(search).split(" ").filter(Boolean);
  if(!tokens.length)return entries;
  return entries.filter(entry=>{
    const haystack=normalizeShoppingSearch([
      entry.title,entry.group,entry.category,entry.subgroup,
      entry.storageRoom,entry.recipient,entry.unit
    ].filter(Boolean).join(" "));
    return tokens.every(token=>haystack.includes(token));
  });
}
const escapeHTML=value=>String(value??"").replace(/[&<>"']/g,char=>
  ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char]));
/** Reusable search for shopping list + in-store mode. */
export function shoppingSearchBar({area,query,total,shown,label}) {
  const active=normalizeShoppingSearch(query).length>0;
  const results=active?`${shown} de ${total} produto(s) encontrado(s)`:
    `${total} produto(s) disponível(is)`;
  return `<section class="shopping-search-box" role="search" aria-label="${escapeHTML(label)}">
    <label for="shopping-search-${escapeHTML(area)}"><span aria-hidden="true">⌕</span> ${escapeHTML(label)}</label>
    <div class="shopping-search-field">
      <input id="shopping-search-${escapeHTML(area)}" data-shopping-search="${escapeHTML(area)}"
        type="search" inputmode="search" enterkeyhint="search" autocomplete="off"
        placeholder="Buscar arroz, amaciante 5 L, higiene..." value="${escapeHTML(query)}">
      ${active?`<button type="button" data-shop-action="clear-shopping-search" data-search-area="${escapeHTML(area)}"
        aria-label="Limpar pesquisa">Limpar</button>`:""}
    </div>
    <div class="shopping-search-result" aria-live="polite">${results}</div>
  </section>`;
}
