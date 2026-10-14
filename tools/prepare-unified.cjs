const {createHash}=require('node:crypto');
const {isDeepStrictEqual}=require('node:util');
const ensure=(condition,message)=>{if(!condition)throw new Error(message);};
const hash=text=>createHash('sha256').update(text).digest('hex');
const repeats={diaria:'Diária',semanal:'Dias da semana',mensal:'Mensal',mensal_primeiro_domingo_apos_dia_8:'Primeiro domingo após dia 8',quando_necessario:'Única'};
// Preserve source records, but never copy operational secrets into cloud documents.
function redact(value,key='') {
 if(/(?:password|passwd|senha|secret|segredo|token|credential|credencial|api[_-]?key|private[_-]?key)/i.test(key))return '[redigido]';
 if(Array.isArray(value))return value.map(x=>redact(x));
 if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,redact(v,k)]));
 if(typeof value==='string'&&/^(Bearer\s+|-----BEGIN .*PRIVATE KEY)/i.test(value))return '[redigido]';
 return value;
}
function prepareUnified(unifiedText,existingPurchasesText) {
 const u=JSON.parse(unifiedText),p=JSON.parse(existingPurchasesText);
 ensure(u.compras_2026&&isDeepStrictEqual(u.compras_2026,p),'Compras divergem da base já importada; revisão obrigatória antes de importar.');
 ensure(/^\d{4}-\d{2}-\d{2}$/.test(u.gerado_em),'Data de geração ausente ou inválida');
 const lists=['rotinas','contas_planejadas','areas_da_vida','projetos_e_decisoes','relacoes_entre_registros','automacoes_propostas','pendencias_validacao'];
 for(const list of lists)ensure(Array.isArray(u[list]),'Estrutura ausente: '+list);
 const records=[],sourceHash=hash(unifiedText),ids=new Set();
 function add(id,kind,fields) {
  ensure(typeof id==='string'&&/^[A-Za-z0-9_-]+$/.test(id),'ID de origem inválido');
  const recordId='unified_2026_'+id;ensure(!ids.has(recordId),'ID duplicado: '+id);ids.add(recordId);
  records.push({id:recordId,kind,private:false,source:'central_do_casal_base_unificada_2026.json',sourceHash,...fields});
 }
 const scope=owner=>owner==='Casal'?{private:false}:['Tiago','Rhayra'].includes(owner)?{private:true,privateOwner:owner}:null;
 const bills=new Map(),recordScope=new Map();
 for(const x of u.contas_planejadas) {
  const visibility=scope(x.responsavel);ensure(visibility,'Responsável financeiro desconhecido; não presumir compartilhamento');
  ensure(x.previsto_centavos===null||Number.isSafeInteger(x.previsto_centavos)&&x.previsto_centavos>=0,'Valor previsto inválido');
  bills.set(x.id,x);
  recordScope.set(x.id,visibility);
  add(x.id,'plannedBill',{...visibility,title:x.nome,month:x.competencia,date:x.vencimento_registrado??'',amount:x.previsto_centavos,owner:x.responsavel,reconciled:false,paidConfirmed:false,status:x.status_no_registro,note:x.notas??'',original:redact(x)});
 }
 for(const x of u.rotinas) {
  ensure(repeats[x.frequencia],'Recorrência desconhecida: '+x.frequencia);
  const days=x.dias_semana_iso??[];ensure(Array.isArray(days)&&days.every(d=>Number.isInteger(d)&&d>=1&&d<=7)&&new Set(days).size===days.length,'Dias da semana inválidos');
  const recurring=x.frequencia!=='quando_necessario';
  const individual=['Tiago','Rhayra'].includes(x.responsavel);
  const financial=x.valor_previsto_centavos!=null||/R\$|\b(?:\d+[.,]\d{2})\b/.test(x.notas??'')||x.categoria==='Financeiro';
  const visibility=individual&&financial?scope(x.responsavel):{private:false};
  recordScope.set(x.id,visibility);
  add(x.id,'task',{...visibility,title:x.nome,note:x.notas??'',owner:x.responsavel??'A definir',category:x.categoria??'',date:x.proxima_data_historica??(recurring?u.gerado_em:''),repeat:repeats[x.frequencia],days,time:x.conflito_horario?'':x.horario??'',needsReview:!!x.origem_inferencia||(x.frequencia==='mensal'&&!x.proxima_data_historica),needsTimeReview:!!x.conflito_horario,skipReminder:true,sourceRecurrence:x.frequencia,completionConfirmed:false,original:redact(x)});
 }
 for(const x of u.projetos_e_decisoes) {
  const target=x.valor_alvo_max_centavos;
  ensure(target==null||Number.isSafeInteger(target)&&target>=0,'Preferência monetária inválida');
  add(x.id,'importedPlan',{title:x.nome,status:x.status??'',owner:x.responsavel??'A definir',...(target!=null?{target,targetIsPreference:true}:{}),location:x.localizacao_desejada??'',note:x.visao??x.observacao??'',pending:x.pendencias??[],original:redact(x)});
 }
 for(const x of u.areas_da_vida)add(x.id,'lifeArea',{title:x.nome,group:x.grupo??'',owner:x.responsavel??'A definir',status:x.estado_registrado??'',note:x.visao??'',original:redact(x)});
 for(const x of u.automacoes_propostas)add(x.id,'automationProposal',{title:x.nome,status:'proposta',enabled:false,executable:false,original:redact(x)});
 const known=new Set([...u.rotinas,...u.contas_planejadas,...u.projetos_e_decisoes,...u.areas_da_vida].map(x=>x.id));
 for(const x of u.relacoes_entre_registros) {
  ensure(known.has(x.origem_id)&&known.has(x.destino_id),'Relação referencia registro inexistente');
  const fromScope=recordScope.get(x.origem_id),toScope=recordScope.get(x.destino_id);
  const owners=new Set([fromScope?.privateOwner,toScope?.privateOwner].filter(Boolean));
  ensure(owners.size<=1,'Relação mistura proprietários privados; revisão obrigatória');
  const visibility=owners.size?scope([...owners][0]):{private:false};
  add('relation_'+hash(JSON.stringify(x)).slice(0,16),'dataRelationship',{...visibility,title:x.tipo,from:'unified_2026_'+x.origem_id,to:'unified_2026_'+x.destino_id,relationshipType:x.tipo,enabled:false,original:redact(x)});
 }
 ensure(u.servidor_domestico&&typeof u.servidor_domestico==='object','Configuração doméstica ausente');
 add('house_setup','houseSetup',{title:'Servidor doméstico',status:'não instalado',connected:false,original:redact(u.servidor_domestico)});
 for(const x of u.pendencias_validacao) {
  const message=x.mensagem??'';
  // A review mentioning individual bills follows their private scope as well.
  const owners=new Set(u.contas_planejadas.filter(b=>b.responsavel!=='Casal'&&(message.toLowerCase().includes(b.nome.toLowerCase())||message.toLowerCase().includes(b.responsavel.toLowerCase()))).map(b=>b.responsavel));
  if(/financiamento|fatura/i.test(message))owners.add('Tiago');
  ensure(owners.size<=1,'Revisão mistura finanças de proprietários distintos; separar texto antes de importar');
  if(owners.size)for(const owner of owners)add('review_'+x.codigo+'_'+owner,'sourceReview',{...scope(owner),title:x.codigo,severity:x.severidade,note:message,original:redact(x)});
  else add('review_'+x.codigo,'sourceReview',{title:x.codigo,severity:x.severidade,note:message,original:redact(x)});
 }
 const counts={tasks:u.rotinas.length,privateTasks:records.filter(x=>x.kind==='task'&&x.private).length,sharedTasks:records.filter(x=>x.kind==='task'&&!x.private).length,plannedBills:u.contas_planejadas.length,privateBills:u.contas_planejadas.filter(x=>x.responsavel!=='Casal').length,sharedBills:u.contas_planejadas.filter(x=>x.responsavel==='Casal').length,projects:u.projetos_e_decisoes.length,areas:u.areas_da_vida.length,relationships:u.relacoes_entre_registros.length,privateRelationships:records.filter(x=>x.kind==='dataRelationship'&&x.private).length,proposals:u.automacoes_propostas.length,sourceReviews:u.pendencias_validacao.length,purchasesGenerated:0};
 add('summary','unifiedSummary',{title:'Planejamento importado de 2026',cutoff:u.gerado_em,counts,completionConfirmed:false,continuousSync:false});
 ensure(records.every(x=>Buffer.byteLength(JSON.stringify(x))<800000),'Documento maior que o limite de segurança');
 return {schema:1,sourceHash,records,counts,archive:{}};
}
module.exports={prepareUnified};
if(require.main===module) {
 const fs=require('node:fs');const [input,previous,output]=process.argv.slice(2);
 ensure(input&&previous&&output,'Uso: node prepare-unified.cjs base-unificada.json compras-anteriores.json plano.json');
 const plan=prepareUnified(fs.readFileSync(input,'utf8'),fs.readFileSync(previous,'utf8'));
 fs.writeFileSync(output,JSON.stringify(plan));console.log(JSON.stringify({records:plan.records.length,counts:plan.counts,sourceHash:plan.sourceHash},null,2));
}
