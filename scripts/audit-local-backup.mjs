#!/usr/bin/env node
// Auditoria estritamente local: não inicializa Firebase, não abre URLs,
// não imprime nomes, IDs de usuário/casa, produtos ou detalhes pessoais.
import {readFileSync,statSync} from "node:fs";
import {replayEmergency} from "../web/emergency-cart.js";
import {same} from "../web/basket-concurrency.js";

export function inspectLocalBackup(data,remoteOverride=null){
  if(data?.type!=="central-do-casal-emergency-backup")
    throw Error("Formato de backup de emergência não reconhecido.");
  const market=data.market;
  if(!["Mercado","Açougue"].includes(market))throw Error("Mercado inválido.");
  const baseline=data.baseline,output=data.baskets?.[market];
  if(!baseline||!output||!Array.isArray(baseline.entries)||
    !Array.isArray(output.entries)||!Array.isArray(data.operations)||
    data.operations.length>300||output.entries.length>200)
    throw Error("Backup incompleto ou fora dos limites.");
  if(baseline.generation!==output.generation||data.baseRevision!==baseline.revision)
    throw Error("Geração/revisão original não corresponde ao arquivo.");
  if(new Set(output.entries.map(e=>e.id)).size!==output.entries.length)
    throw Error("IDs repetidos no resultado do backup.");
  const draft={
    baseline,baseGeneration:baseline.generation,operations:data.operations,
    basket:output
  };
  const rebuilt=replayEmergency(baseline,draft);
  if(!same(rebuilt.entries,output.entries)||rebuilt.budget!==output.budget)
    throw Error("Operações não reconstruíram a cesta exportada.");
  const retried=replayEmergency(rebuilt,draft);
  if(!same(retried.entries,rebuilt.entries)||retried.budget!==rebuilt.budget)
    throw Error("Retentativa do diário não foi idempotente.");
  const bought=output.entries.filter(e=>e.bought);
  const cartTotalCents=bought.reduce((sum,e)=>{
    const value=Number(e.actual),milli=Number(e.actualMilli??e.plannedMilli);
    if(!Number.isSafeInteger(value)||!Number.isSafeInteger(milli))
      throw Error("Valor/quantidade de produto comprado inválido.");
    return sum+Math.round(value*milli/1000);
  },0);
  let remoteStatus="não fornecida";
  if(remoteOverride){
    if(remoteOverride.generation!==baseline.generation)
      remoteStatus="geração diferente: sincronização bloqueada";
    else{
      try{
        const next=replayEmergency(remoteOverride,draft);
        remoteStatus=same(next.entries,remoteOverride.entries)&&
          next.budget===remoteOverride.budget?
          "já sincronizado":"conciliável, com mudanças pendentes";
      }catch{
        remoteStatus="conflito: revisão manual obrigatória";
      }
    }
  }
  return {
    kind:"conferencia-local-sem-acesso-a-nuvem",
    market,baselineItems:baseline.entries.length,
    operations:data.operations.length,
    operationTypes:Object.fromEntries([...new Set(data.operations.map(o=>o.type))].map(
      type=>[type,data.operations.filter(o=>o.type===type).length])),
    outputItems:output.entries.length,purchasedItems:bought.length,
    cartTotalCents,budgetCents:output.budget,
    matchesExport:true,idempotent:true,
    liveFirebaseChecked:false,remoteStatus
  };
}
function main(){
  const paths=process.argv.slice(2);
  if(paths.length<1||paths.length>2){
    process.stderr.write("Uso: node scripts/audit-local-backup.mjs BACKUP.json [SNAPSHOT_REMOTO_LOCAL.json]\n");
    process.exitCode=2;return;
  }
  const load=path=>{
    if(statSync(path).size>2_000_000)throw Error("Arquivo excede 2MB.");
    return JSON.parse(readFileSync(path,"utf8"));
  };
  try{
    const backup=load(paths[0]),remote=paths[1]?load(paths[1]):null;
    const result=inspectLocalBackup(backup,remote);
    process.stdout.write(JSON.stringify(result,null,2)+"\n");
  }catch(error){
    process.stderr.write("Auditoria interrompida: "+error.message+"\n");
    process.exitCode=1;
  }
}
if(process.argv[1]&&process.argv[1].endsWith("/audit-local-backup.mjs"))main();
