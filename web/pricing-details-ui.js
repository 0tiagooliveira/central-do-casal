import {presentationSeries,priceStatistics,normalizedRate} from "./product-presentation.js";
import {priceTrend,sparkline} from "./catalog-history.js";
const esc=x=>String(x??"").replace(/[&<>"']/g,ch=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
export const money4=c=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL",
  minimumFractionDigits:2,maximumFractionDigits:Number.isInteger(Number(c))?2:4}).format((Number(c)||0)/100);
const date=d=>/^\d{4}-\d{2}-\d{2}$/.test(d||"")?d.slice(8,10)+"/"+d.slice(5,7)+"/"+d.slice(0,4):"Data desconhecida";
const itemPrice=p=>money4(p.cents)+"/"+esc(p.unit||"un");
export function packageDetailCards(points){
  const groups=presentationSeries(points);
  if(!groups.length)return '<div class="catalog-no-history">Não há compra com preço e data para esta apresentação.</div>';
  return '<div class="package-detail-grid">'+groups.map(g=>{
    const stats=priceStatistics(g.points),last=g.points.at(-1);
    const trend=priceTrend(g.points);
    const rate=normalizedRate(last);
    const trusted=["kg","L"].includes(last.unit)||!!(last.presentation?.dimensionAmount||last.presentation?.unitCount);
    const rows=g.points.slice(-25).reverse().map(p=>
      '<tr><td>'+esc(date(p.date))+'</td><td>'+esc(p.store||"Não informada")+'</td>'+
      '<td>'+itemPrice(p)+'</td><td>'+esc(p.quantityText||"Não informada")+' '+esc(p.unit)+'</td></tr>').join("");
    const prices=g.points.length>1?
      '<div class="package-metrics"><span>Menor <strong>'+money4(stats.min)+'</strong></span>'+
      '<span>Média <strong>'+money4(stats.avg)+'</strong></span>'+
      '<span>Maior <strong>'+money4(stats.max)+'</strong></span></div>':"";
    const fromPrevious=stats.previous===null?"Sem compra anterior":
      stats.change===0?"Mesmo preço da compra anterior":
      (stats.change>0?"↑ Alta de ":"↓ Queda de ")+money4(Math.abs(stats.change))+
      (stats.percent===null?"":" ("+(stats.percent>0?"+":"")+stats.percent+"%)")+" ante a compra anterior";
    return '<section class="package-card">'+
      '<div class="package-name"><strong>'+esc(g.presentation?.display||"Embalagem não identificada")+
      '</strong><small>'+g.points.length+' compra(s) com preço e data</small></div>'+
      '<div class="package-last"><strong>'+itemPrice(last)+'</strong><small>Última: '+esc(date(last.date))+
      ' · '+esc(last.store||"Loja não informada")+'</small></div>'+
      (rate?'<div class="package-equivalent"><strong>'+money4(rate.cents)+'/'+esc(rate.unit)+'</strong>'+
        '<small>'+esc(rate.basis)+'</small></div>': 
        '<p class="package-unresolved">Preço por litro/quilo/peça não calculável: tamanho da embalagem ausente na nota.</p>')+
      '<p class="package-previous">'+esc(fromPrevious)+'</p>'+
      (trusted?'<div class="package-spark">'+sparkline(g.points)+'</div>':
        '<div class="package-spark caution">'+sparkline(g.points)+
        '<p>Gráfico apenas dos valores registrados: apresentação incompleta, sem conclusão de alta ou queda.</p></div>')+
      prices+
      '<details class="package-receipts"><summary>Ver compras e descrições ('+g.points.length+')</summary>'+
      '<p class="package-description">Última descrição fiscal: '+esc(last.description||"Não informada")+'</p>'+
      '<div class="package-table-wrap"><table><thead><tr><th>Data</th><th>Loja</th>'+
      '<th>Preço unitário</th><th>Qtd. na nota</th></tr></thead><tbody>'+rows+'</tbody></table></div>'+
      (g.points.length>25?'<p>Exibidas as 25 últimas compras desta apresentação.</p>':"")+'</details>'+
      '</section>';
  }).join("")+'</div>';
}
export function familyEquivalentComparison(variants=[]){
  const values=[];
  for(const v of variants){
    for(const g of presentationSeries(v.history)){
      const last=g.points.at(-1),rate=last&&normalizedRate(last);
      if(rate)values.push({title:v.sku.title,package:g.presentation?.display||"",
        cents:rate.cents,unit:rate.unit,date:last.date,store:last.store});
    }
  }
  const groups=new Map();
  for(const v of values){
    if(!groups.has(v.unit))groups.set(v.unit,[]);
    groups.get(v.unit).push(v);
  }
  const sections=[...groups].map(([unit,rows])=>{
    if(rows.length<2)return "";
    rows.sort((a,b)=>a.cents-b.cents);
    return '<div class="equiv-compare"><strong>Comparação por '+esc(unit)+'</strong><p>'+
      'Últimos preços por conteúdo declarado, não por frasco/caixa. '+
      'Confira marcas e variedades antes de escolher.</p>'+
      '<div class="equiv-rows">'+rows.slice(0,12).map(r=>
        '<div><span>'+esc(r.title)+' · '+esc(r.package)+'</span>'+
        '<strong>'+money4(r.cents)+'/'+esc(unit)+'</strong></div>').join("")+'</div></div>';
  }).filter(Boolean);
  return sections.length?'<div class="family-equivalents">'+sections.join("")+'</div>':"";
}
