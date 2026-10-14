const { createHash } = require('node:crypto');
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function ensure(condition, message) { if (!condition) throw new Error(message); }
const cuts = new Set(['Alcatra / maminha','Carne moída de primeira','Costela ponta de ripa','Coxa e sobrecoxa','Coxão mole','Frango passarinho','Linguiça toscana','Patinho','Picanha bovina com osso']);
function prepare(mainText, insightsText) {
 const a=JSON.parse(mainText), b=JSON.parse(insightsText);
 for(const list of ['documentos','compras_itens','itens_base','produtos','estabelecimentos','meses','gastos_por_categoria_e_mes']) ensure(Array.isArray(a[list]),'Estrutura ausente: '+list);
 for(const list of ['documentos','compras_itens','itens_base','produtos']) ensure(new Set(a[list].map(x=>x.id)).size===a[list].length,'IDs duplicados em '+list);
 const stores=new Map(a.estabelecimentos.map(x=>[x.id,x]));
 const notes=new Map(a.documentos.map(x=>[x.id,x]));
 const products=new Map(a.produtos.map(x=>[x.id,x]));
 const bases=new Map(a.itens_base.map(x=>[x.id,x]));
 const lines=new Map(a.documentos.map(x=>[x.id,[]]));
 const observed=new Map(a.produtos.map(x=>[x.id,[]]));
 for(const x of a.compras_itens) {
   ensure(notes.has(x.nota_id) && products.has(x.produto_id),'Linha sem documento ou produto');
   ensure(x.item_base_id===null || bases.has(x.item_base_id),'Vínculo de catálogo inválido');
   lines.get(x.nota_id).push(x); observed.get(x.produto_id).push(x);
 }
 const total=a.documentos.reduce((s,x)=>{ensure(Number.isSafeInteger(x.valor_documentado_centavos)&&x.valor_documentado_centavos>=0,'Total monetário inválido');return s+x.valor_documentado_centavos},0);
 ensure(total===a.totais.valor_documentado_centavos,'Total geral divergente');
 ensure(a.gastos_por_categoria_e_mes.reduce((s,x)=>s+x.valor_estimado_centavos,0)===total,'Categorias não conciliam com o total documentado');
 const docs=[];
 const sourceHash=hash(mainText), insightsHash=hash(insightsText);
 function add(id,kind,fields) { docs.push({id:'import_2026_'+id,kind,private:false,source:'dados_compras_2026.json',sourceHash,...fields}); }
 const marketForStore=id=>/açougue/i.test(stores.get(id)?.nome||'')?'Açougue':'Mercado';
 for(const x of a.documentos) {
   ensure(stores.has(x.estabelecimento_id),'Estabelecimento ausente');
   const rows=lines.get(x.id).sort((x,y)=>x.posicao_na_nota-y.posicao_na_nota);
   ensure(x.tem_detalhamento ? rows.length===x.qtd_linhas_documento : rows.length===0 && x.qtd_linhas_documento==null,'Contagem de linhas divergente: '+x.id);
   add(x.id,'importedReceipt',{title:stores.get(x.estabelecimento_id).nome,date:x.data,market:marketForStore(x.estabelecimento_id),total:x.valor_documentado_centavos,discount:x.desconto_centavos,detailed:x.tem_detalhamento,reconciled:x.pagamento_conciliado,original:x,store:stores.get(x.estabelecimento_id),lines:rows});
 }
 for(const x of a.itens_base) {
   add(x.id,'catalogItem',{title:x.nome,category:x.categoria,market:cuts.has(x.nome)?'Açougue':'Mercado',reference:x.preco_referencia_centavos,quantityText:x.quantidade_planejada,previousState:x.estado_lista_registrado,review:x.precisa_revisao,stockConfirmed:false,original:x});
 }
 for(const x of a.produtos) {
   const history=observed.get(x.id).map(row=>({...row,date:notes.get(row.nota_id).data})).sort((x,y)=>x.date.localeCompare(y.date)||x.id.localeCompare(y.id));
   const last=history.at(-1);
   add(x.id,'catalogSku',{title:x.nome_comercial,category:x.categoria_geral,market:marketForStore(x.estabelecimento_id),store:stores.get(x.estabelecimento_id).nome,referenceText:last?.preco_unitario_reais||null,unit:last?.unidade||null,lastDate:last?.date||null,occurrences:x.numero_ocorrencias,baseId:x.item_base_id,review:x.nome_revisao_necessaria||x.grau_confirmacao_vinculo!=='confirmado',original:x,history,variation:a.variacoes_de_precos.filter(p=>p.produto_id===x.id)});
 }
 for(const x of a.meses) {
   const monthDocs=a.documentos.filter(p=>p.data.startsWith(x.mes));
   const monthTotal=monthDocs.reduce((s,p)=>s+p.valor_documentado_centavos,0);
   ensure(monthDocs.length===x.quantidade_notas,'Contagem mensal divergente');
   if(monthDocs.length) ensure(monthTotal===x.valor_documentado_centavos,'Total mensal divergente');
   add('month_'+x.mes,'documentedMonth',{month:x.mes,notes:x.quantidade_notas,total:monthDocs.length?monthTotal:null,coverageComplete:false,categories:a.gastos_por_categoria_e_mes.filter(p=>p.mes===x.mes).map(p=>({title:p.categoria,total:p.valor_estimado_centavos})),original:x});
 }
 const principal=[];
 for(const month of a.meses) {
   const ranked=a.documentos.filter(x=>x.data.startsWith(month.mes)&&/muffato/i.test(stores.get(x.estabelecimento_id).nome)).sort((x,y)=>y.valor_documentado_centavos-x.valor_documentado_centavos);
   if(ranked.length) principal.push(ranked[0]);
 }
 const lastFour=principal.slice(-4).map(x=>x.valor_documentado_centavos).sort((x,y)=>x-y);
 const forecast=lastFour.length===4?Math.round((lastFour[1]+lastFour[2])/2):null;
 add('summary','importSummary',{title:'Compras documentadas de 2026',notes:a.documentos.length,baseItems:a.itens_base.length,skuItems:a.produtos.length,lines:a.compras_itens.length,total,undetailed:a.documentos.filter(x=>!x.tem_detalhamento).length,undetailedTotal:a.totais.valor_sem_detalhamento_centavos,discount:a.totais.descontos_centavos,cutoff:a.data_limite_dos_documentos,forecast,forecastIsBudget:true,months:a.meses.filter(x=>x.quantidade_notas>0).map(x=>x.mes),sourceConflict:b.summary?.quantidade_notas!==a.documentos.length,olderInsightNotes:b.summary?.quantidade_notas,olderInsightTotal:b.summary?.valor_documentado_centavos,insightsHash,conventions:a.convencoes,qualityAlerts:b.insights?.alertas_de_qualidade||{}});
 ensure(docs.every(x=>Buffer.byteLength(JSON.stringify(x))<800000),'Documento maior que o limite de segurança');
 return {schema:1,sourceHash,insightsHash,records:docs,counts:{notes:a.documentos.length,lines:a.compras_itens.length,catalog:a.itens_base.length,sku:a.produtos.length,cutCatalog:a.itens_base.filter(x=>cuts.has(x.nome)).length,total,forecast},archive:{main:mainText,insights:insightsText}};
}
module.exports={prepare};
if(require.main===module) {
 const fs=require('node:fs'); const plan=prepare(fs.readFileSync(process.argv[2],'utf8'),fs.readFileSync(process.argv[3],'utf8'));
 fs.writeFileSync(process.argv[4],JSON.stringify(plan)); console.log(JSON.stringify({counts:plan.counts,records:plan.records.length,sourceHash:plan.sourceHash},null,2));
}
