// Informação de embalagem extraída de descrições fiscais e do SKU.
// Não infere volume nem quantidade ausentes. Normaliza apenas quando explícitos.
const clean=x=>String(x??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().replace(/\s+/g," ").trim();
const dec=x=>Number(String(x).replace(",","."));
const fmt=n=>Number(n).toLocaleString("pt-BR",{maximumFractionDigits:3});
const valid=n=>Number.isFinite(n)&&n>0&&n<=100000;
export function parsePresentation(title,unit="un") {
  const s=clean(title),u=clean(unit);
  const box=/\b(?:cx\.?|caixa|box)\b/.test(s)||["cx","caixa"].includes(u);
  const pack=/\b(?:pct\.?|pacote|pack|fardo)\b/.test(s)||["pct","pacote"].includes(u);
  const multipack=s.match(/\b(\d{1,3})\s*[x×]\s*(\d{1,4}(?:[.,]\d{1,3})?)\s*(ml|l|lt|lts|kg|g)\b/);
  const count=s.match(/\b(?:c\/|com\s+|caixa\s+(?:com\s+)?|cx\.?\s+(?:c\/\s*)?|pacote\s+(?:com\s+)?|pct\.?\s+(?:c\/\s*)?|pack\s+(?:com\s+)?)\s*(\d{1,3})\s*(?:un(?:idades?)?|und|unid|pcs?|pecas?|hamburgueres?|hamburguer|hamburgueres?)\b/);
  const sized=s.match(/\b(\d{1,4}(?:[.,]\d{1,3})?)\s*(ml|lts?|litros?|kg|quilos?|gramas?|gr|g)\b/);
  function dimension(value,measure) {
    if(!valid(value))return null;
    if(/^(ml)$/.test(measure))return {kind:"volume",amount:value/1000};
    if(/^(l|lt|lts|litro|litros)$/.test(measure))return {kind:"volume",amount:value};
    if(/^(g|gr|grama|gramas)$/.test(measure))return {kind:"mass",amount:value/1000};
    if(/^(kg|quilo|quilos)$/.test(measure))return {kind:"mass",amount:value};
    return null;
  }
  let countValue=null,dimensionValue=null,form=box?"Caixa":pack?"Pacote":null;
  if(multipack) {
    countValue=Number(multipack[1]);
    const each=dimension(dec(multipack[2]),multipack[3]);
    if(each)dimensionValue={kind:each.kind,amount:each.amount*countValue};
    form=form||"Multipack";
  }else{
    if(count)countValue=Number(count[1]);
    if(sized)dimensionValue=dimension(dec(sized[1]),sized[2]);
  }
  // Embalagem de n unidades tem preço por item apenas se n está explícito.
  // Peso/volume total somente quando a descrição indica o total inequivocamente.
  const units=valid(countValue)?countValue:null;
  // "Caixa 12 un 90 g" usually means 90 g *per piece*; only "12 x 90 g"
  // supports deriving total package weight.
  const eachPiece=!!(units&&sized&&!multipack);
  const amount=!eachPiece&&dimensionValue&&valid(dimensionValue.amount)?dimensionValue.amount:null;
  const dim=amount?dimensionValue.kind:null;
  const label=units?(form||"Conjunto")+" "+fmt(units)+" un":(form||"");
  const eachMeasure=eachPiece&&dimensionValue?
    "cada peça "+fmt(dimensionValue.amount)+(dimensionValue.kind==="volume"?" L":" kg"):"";
  const measureLabel=amount?(fmt(amount)+(dim==="volume"?" L":" kg")):eachMeasure;
  const display=[label,measureLabel].filter(Boolean).join(" · ")||
    (u==="un"||u==="und"?"Unidade sem tamanho informado":u==="pacote"?"Pacote sem tamanho informado":"Quantidade/embalagem não identificada");
  let key;
  if(units)key="count:"+units+":"+String(form||"embalagem");
  else if(form)key="form:"+form;
  else key="individual";
  if(dim)key+=":"+dim+":"+amount.toFixed(6);
  else key+=":dim-desconhecida";
  // A unidade fiscal é parte da identidade, pois UN, KG e L não são iguais.
  key=u+":"+key;
  return {key,display,form:form||"",unitCount:units,dimension:dim,dimensionAmount:amount,
    detail:clean(title),explicit:!!(units||amount||form),rawUnit:unit};
}
export function observationPresentation(description,skuTitle,unit){
  const direct=parsePresentation(description,unit);
  if(direct.explicit)return direct;
  const fallback=parsePresentation(skuTitle,unit);
  if(fallback.explicit)return fallback;
  return direct;
}
export function normalizedRate(point) {
  const p=point.presentation;
  if(!p||typeof point.cents!=="number"||!Number.isFinite(point.cents))return null;
  const u=clean(point.unit);
  // Registro fiscal já tem preço por L ou KG.
  if(u==="l")return {cents:point.cents,unit:"L",basis:"Preço fiscal por litro"};
  if(u==="kg")return {cents:point.cents,unit:"kg",basis:"Preço fiscal por quilo"};
  if(!["un","und","pacote","pct"].includes(u))return null;
  // Somente divide o preço do recipiente quando o volume/massa foi extraído.
  if(p.dimension&&p.dimensionAmount){
    return {cents:point.cents/p.dimensionAmount,unit:p.dimension==="volume"?"L":"kg",basis:"Preço equivalente por conteúdo declarado"};
  }
  if(p.unitCount){
    return {cents:point.cents/p.unitCount,unit:"un",basis:"Preço por peça declarada"};
  }
  return null;
}
export function presentationSeries(points=[]){
  const map=new Map();
  for(const p of points){
    const key=(p.skuId||"sem-sku")+"|"+(p.presentation?.key||"sem-embalagem")+"|"+(p.unit||"");
    if(!map.has(key))map.set(key,[]);
    map.get(key).push(p);
  }
  return [...map.entries()].map(([key,observations])=>({key,
    presentation:observations[observations.length-1]?.presentation,
    points:observations.slice().sort((a,b)=>a.date.localeCompare(b.date)||a.id.localeCompare(b.id))
  })).sort((a,b)=>b.points.length-a.points.length||
    b.points[b.points.length-1].date.localeCompare(a.points[a.points.length-1].date));
}
export function priceStatistics(points=[]){
  if(!points.length)return null;
  const prices=points.map(p=>p.cents).filter(n=>Number.isFinite(n));
  if(!prices.length)return null;
  const newest=points.at(-1),previous=points.at(-2);
  const last=newest.cents;
  const changed=previous?last-previous.cents:null;
  const pct=previous&&previous.cents>0?Math.round((changed/previous.cents)*100):null;
  return {observations:prices.length,min:Math.min(...prices),max:Math.max(...prices),
    avg:prices.reduce((a,b)=>a+b,0)/prices.length,last,previous:previous?.cents??null,
    change:changed,percent:pct,firstDate:points[0].date,lastDate:newest.date};
}
