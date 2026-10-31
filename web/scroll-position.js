// Mantém a posição visual entre refresh e atualizações do Firestore.
// Guarda apenas um identificador de card e posição na sessão desta aba.
const key=hash=>"cdc-scroll:"+String(hash||"#/hoje");
export function captureViewPosition(root,view=window) {
  const y=Math.max(0,Number(view.scrollY)||0);
  const height=Number(view.innerHeight)||800;
  let anchor="",offset=0;
  const nodes=root?.querySelectorAll?.("[data-shopping-entry-id]")||[];
  for(const node of nodes){
    const rect=node.getBoundingClientRect();
    if(rect.bottom>75&&rect.top<height-75){
      anchor=node.dataset.shoppingEntryId||"";
      offset=Math.round(rect.top);
      break;
    }
  }
  return {y,anchor,offset};
}
export function saveViewPosition(hash,position,store=sessionStorage){
  if(!position||!Number.isFinite(position.y))return;
  try{store.setItem(key(hash),JSON.stringify(position));}catch{}
}
export function loadViewPosition(hash,store=sessionStorage){
  try{
    const saved=JSON.parse(store.getItem(key(hash))||"null");
    // Migração: versões anteriores armazenavam só o número de pixels.
    if(Number.isFinite(saved)&&saved>=0)return {y:saved,anchor:"",offset:0};
    if(saved&&Number.isFinite(saved.y)&&saved.y>=0)
      return {y:saved.y,anchor:typeof saved.anchor==="string"?saved.anchor:"",
        offset:Number.isFinite(saved.offset)?saved.offset:0};
  }catch{}
  return null;
}
export function restoreViewPosition(position,root,view=window){
  if(!position)return false;
  let y=position.y;
  if(position.anchor){
    const nodes=root?.querySelectorAll?.("[data-shopping-entry-id]")||[];
    for(const node of nodes){
      if(node.dataset.shoppingEntryId===position.anchor){
        y=(Number(view.scrollY)||0)+node.getBoundingClientRect().top-position.offset;
        break;
      }
    }
  }
  view.scrollTo({top:Math.max(0,Math.round(y)),behavior:"auto"});
  return true;
}
