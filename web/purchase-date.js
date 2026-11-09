// Separar o dia da compra do instante em que o Firebase recebeu o registro.
export function purchaseDateInBrazil(now=new Date()){
  const bits=Object.fromEntries(new Intl.DateTimeFormat("en-US",{
    timeZone:"America/Sao_Paulo",year:"numeric",month:"2-digit",day:"2-digit"
  }).formatToParts(now).map(x=>[x.type,x.value]));
  return bits.year+"-"+bits.month+"-"+bits.day;
}
export function isValidPurchaseDate(value){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(String(value||"")))return false;
  const d=new Date(value+"T12:00:00Z");
  return !Number.isNaN(d.getTime())&&d.toISOString().slice(0,10)===value;
}
export function resolvePurchaseDate(invoice,selected="",now=new Date()){
  const fiscalDate=isValidPurchaseDate(invoice?.date)?invoice.date:"";
  const explicit=String(selected||"").trim();
  if(explicit&&!isValidPurchaseDate(explicit))
    throw Error("Data inválida. Confira o dia em que a compra aconteceu.");
  const date=explicit||fiscalDate||purchaseDateInBrazil(now);
  return {date,source:fiscalDate&&date===fiscalDate?"nota":
    explicit?"manual":"fechamento"};
}
