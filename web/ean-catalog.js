// Consulta somente por ação explícita do usuário, nunca durante cada render.
// O código GTIN não contém nome, preço ou imagem: esses dados vêm das fontes.
const TTL_FOUND = 30 * 86400000;
const TTL_MISSING = 12 * 3600000;
const text = value => typeof value === "string" ? value.trim().slice(0, 200) : "";
const validLength = value => [8,12,13,14].includes(value.length);

export function normalizeGtin(input) {
  const code = String(input ?? "").trim().replace(/[\s-]/g, "");
  if (!/^\d+$/.test(code) || !validLength(code)) return "";
  const check = Number(code.at(-1));
  let sum = 0, weight = 3;
  for (let i = code.length - 2; i >= 0; i--) {
    sum += Number(code[i]) * weight;
    weight = weight === 3 ? 1 : 3;
  }
  return (10 - sum % 10) % 10 === check ? code : "";
}

const image = value => {
  if (typeof value !== "string") return "";
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" ||
        !/(^|\.)((precomelhor\.com\.br)|(openfoodfacts\.(org|net)))$/i.test(url.hostname))
      return "";
    return url.href;
  } catch { return ""; }
};
const product = (ean, source, name, brand, amount, photo) => {
  const title = text(name);
  return title ? {ean, title, brand:text(brand), package:text(amount),
    imageUrl:image(photo), source} : null;
};
export function fromPrecoMelhor(body, ean) {
  const p = body?.product;
  if (!p || typeof p !== "object") return null;
  const reported = String(p.ean ?? p.barcode ?? "").trim();
  if (reported && reported !== ean) return null;
  return product(ean,"precomelhor",p.product_name ?? p.name,p.brand,p.quantity ?? p.package_size,p.image_url);
}
export function fromOpenFoodFacts(body, ean) {
  const p = body?.product;
  if (!p || typeof p !== "object" || body?.status === 0) return null;
  if (body.code && String(body.code) !== ean) return null;
  return product(ean,"openfoodfacts",p.product_name_pt || p.product_name,p.brands,p.quantity,p.image_front_url || p.image_url);
}

// O histórico já carregado é consultado em memória. Não cria nova leitura Firestore.
export function fromLocalRecords(records,ean) {
  const entries=Array.isArray(records)?records:[];
  for(const item of entries){
    if(!["catalogItem","catalogSku"].includes(item?.kind))continue;
    const code=normalizeGtin(item.gtin ?? item.ean ?? item.barcode ?? "");
    if(code===ean){
      const found=product(ean,"minha-base",item.title,item.brand,item.package ?? item.quantityText,"");
      if(found)return found;
    }
  }
  for(const receipt of entries){
    if(receipt?.kind!=="receipt")continue;
    const lines=receipt.invoice?.lines;
    if(!Array.isArray(lines))continue;
    for(const line of lines){
      if(normalizeGtin(line?.gtin)===ean){
        const found=product(ean,"nota-anterior",line.name,line.brand,line.quantityText,"");
        if(found)return found;
      }
    }
  }
  return null;
}
const cacheKey = ean => "cdc:ean:lookup:v1:" + ean;
function readCache(storage, ean) {
  try {
    const s=storage?.getItem(cacheKey(ean));
    if (!s || s.length > 3000) return null;
    const v=JSON.parse(s);
    return v?.v === 1 && v?.value?.ean === ean && Number.isFinite(v.expiresAt) ? v : null;
  } catch { return null; }
}
function writeCache(storage, ean, value, expiresAt) {
  try { storage?.setItem(cacheKey(ean),JSON.stringify({v:1,value,expiresAt})); } catch {}
}
async function getBody(fetchImpl, url) {
  const options={headers:{Accept:"application/json"}};
  if (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function")
    options.signal=AbortSignal.timeout(8000);
  const response=await fetchImpl(url,options);
  if (response.status === 404) return null;
  if (!response.ok) throw Error("Serviço indisponível ("+response.status+").");
  return response.json();
}

export async function lookupGtin(input,{fetchImpl=globalThis.fetch,storage=globalThis.localStorage,
    localRecords=[],now=Date.now()}={}) {
  const ean=normalizeGtin(input);
  if (!ean) throw Error("Código EAN/GTIN inválido. Confira os dígitos e tente novamente.");
  const local=fromLocalRecords(localRecords,ean);
  if(local)return {ean,status:"found",product:local,fromLocal:true,fromCache:false};
  const cached=readCache(storage,ean);
  if (cached && cached.expiresAt > now) return {...cached.value,fromCache:true};
  const sources=[
    {url:"https://www.precomelhor.com.br/api/product/"+ean,parse:fromPrecoMelhor},
    {url:"https://world.openfoodfacts.org/api/v2/product/"+ean+".json?product_type=all&fields=code,product_name,product_name_pt,brands,quantity,image_front_url,image_url",parse:fromOpenFoodFacts}
  ];
  let errors=0;
  for (const source of sources) {
    try {
      const payload=await getBody(fetchImpl,source.url);
      const found=source.parse(payload,ean);
      if (found) {
        const value={ean,status:"found",product:found};
        writeCache(storage,ean,value,now+TTL_FOUND);
        return {...value,fromCache:false};
      }
    } catch { errors++; }
  }
  if (cached?.value?.status === "found")
    return {...cached.value,fromCache:true,stale:true};
  const result={ean,status:errors ? "unavailable" : "missing",product:null};
  if (!errors) writeCache(storage,ean,result,now+TTL_MISSING);
  return {...result,fromCache:false};
}
