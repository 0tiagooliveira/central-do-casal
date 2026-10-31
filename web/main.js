import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import { getFirestore, doc, collection, onSnapshot } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";

// Configuração pública do mesmo projeto usado pelo Android; sem credenciais administrativas.
// Um app Web deve ser registrado no console Firebase antes de publicar em produção.
const firebaseConfig = {
  apiKey: "AIzaSyBS9nLXez2vs3xC86Og5BOE73qUVla_kcQ",
  authDomain: "central-do-casal.firebaseapp.com",
  projectId: "central-do-casal",
  storageBucket: "central-do-casal.firebasestorage.app",
  messagingSenderId: "1092711646864"
};
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

const tabs = [["Hoje","☀️"],["Compras","🛒"],["Rotina","✓"],["Planos","◈"],["Nós","♡"]];
const state = { tab:"Hoje", market:"Mercado", profile:null, uid:"", household:"", shared:[], personal:[], baskets:{}, error:"", sync:"Aguardando dados", loading:true };
let listeners = [];
const $ = (id) => document.getElementById(id);
const fmt = (value) => new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format((Number(value)||0)/100);
const esc = (s) => String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const normalize = (v) => String(v??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").trim().replace(/\s+/g," ").toLowerCase();
const todayDate = () => { const d=new Date(); return [d.getFullYear(),String(d.getMonth()+1).padStart(2,"0"),String(d.getDate()).padStart(2,"0")].join("-"); };
const allRecords = () => [...state.shared,...state.personal];
const records = (kind) => allRecords().filter(r=>r.kind===kind);
const card = (label,value,foot="") => `<article class="panel"><p class="panel-label">${esc(label)}</p><div class="panel-value">${esc(value)}</div><p class="panel-foot">${esc(foot)}</p></article>`;
const empty = (text) => `<div class="empty">${esc(text)}</div>`;
const row = (title, meta, right="",tag="") => `<article class="record-row"><div class="record-main"><div class="record-title">${esc(title)}</div><div class="record-meta">${esc(meta)}</div></div>${tag?`<span class="pill ${tag==="Revisar"?"warn":tag==="Feito"?"success":""}">${esc(tag)}</span>`:""}${right?`<span class="record-right">${esc(right)}</span>`:""}</article>`;
const safeName = () => state.profile?.displayName || auth.currentUser?.displayName || "Meu perfil";
const isFinished = (item) => item.done===true || item.completed===true;
const currentBasket = () => state.baskets[state.market] || {market:state.market,entries:[],budget:0};
const oneDay = (raw) => {const value=String(raw||"");return value.length>=10?value.slice(0,10):"";};
function taskForDay(t,date) {
  if(t.needsReview) return false;
  const start=oneDay(t.date);
  if(!start||start>date) return false;
  if(t.repeat==="Diária") return true;
  const a=new Date(start+"T12:00:00"), b=new Date(date+"T12:00:00");
  if(t.repeat==="Semanal") return a.getDay()===b.getDay();
  if(t.repeat==="Mensal") { const last=new Date(b.getFullYear(),b.getMonth()+1,0).getDate();return b.getDate()===Math.min(a.getDate(),last);}
  if(t.repeat==="Dias da semana") return Array.isArray(t.days)&&t.days.some(x=>Number(x)===(b.getDay()||7));
  if(t.repeat==="Primeiro domingo após dia 8") return b.getDay()===0 && b.getDate()>=9 && b.getDate()<=15;
  return start===date;
}
function renderHoje(){
  const tasks=records("task").filter(t=>taskForDay(t,todayDate()));
  const purchases=Object.values(state.baskets).reduce((n,b)=>n+(b.entries||[]).filter(e=>!e.bought).length,0);
  const plans=records("plan");
  const due=tasks.filter(t=>!records("occurrence").some(o=>o.id===t.id+":"+todayDate() && isFinished(o)));
  return `<section class="welcome"><div class="eyebrow">NOSSA ROTINA</div><h2>Olá, ${esc(safeName().split(" ")[0])} ✨</h2><p>Um resumo do que já está sincronizado com o espaço do casal. O que ficou salvo apenas no Android não aparece aqui.</p></section>
  <div class="card-grid">${card("Tarefas de hoje",String(due.length),"pendentes no dia")}${card("Lista de compras",String(purchases),"itens ainda não comprados")}${card("Nossos planos",String(plans.length),"planos cadastrados")}</div>
  <div class="split-head"><h2 class="section-heading">Para hoje</h2><span class="read-only">Visualização, sem alterações</span></div>
  <div class="row-list">${due.length?due.slice(0,12).map(t=>row(t.title||"Tarefa sem título", [t.owner,t.time,t.category].filter(Boolean).join(" · "),"","Pendente")).join(""):empty("Nenhuma tarefa pendente encontrada para hoje.")}</div>`;
}
function renderCompras(){
  const b=currentBasket();
  const entries=Array.isArray(b.entries)?b.entries:[];
  const suspicious=entries.filter(e=>!String(e.title||"").trim()||!["un","kg","g","L","mL","pacote"].includes(e.unit)||e.unitReviewed===false||e.quantityReviewed===false);
  const normalizedCount=new Map();
  for(const e of entries){const key=normalize(e.title);if(key)normalizedCount.set(key,(normalizedCount.get(key)||0)+1);}
  const duplicates=entries.filter(e=>normalizedCount.get(normalize(e.title))>1);
  const receipts=records("receipt").filter(r=>r.market===state.market).sort((a,b)=>String(b.updatedAt?.seconds||b.createdAt||"").localeCompare(String(a.updatedAt?.seconds||a.createdAt||"")));
  const options=["Mercado","Açougue"].map(x=>`<button type="button" data-market="${x}" aria-pressed="${x===state.market}">${x}</button>`).join("");
  return `<div class="split-head"><div><p class="info-line">Lista compartilhada da casa, sem alterar dados no Android.</p></div><div class="switcher">${options}</div></div>
    <div class="card-grid">${card("Orçamento",fmt(b.budget),"definido no aplicativo")}${card("Itens",String(entries.length),String(entries.filter(x=>!x.bought).length)+" pendentes")}${card("Atenção",String(suspicious.length),"itens que exigem revisão")}</div>
    ${suspicious.length?`<div class="banner warning" style="margin-top:18px">Existem ${suspicious.length} item(ns) com dados incompletos. Confira nome, unidade e quantidade no Android antes de fechar a compra.</div>`:""}
    ${duplicates.length?`<section class="panel" style="margin-top:17px"><div class="split-head"><strong>Possíveis duplicações</strong><span class="pill warn">Conferência manual</span></div><p class="info-line">Títulos iguais não significam produtos iguais. Compare unidades e origem antes de qualquer ajuste.</p>${duplicates.map(e=>row(e.title||"Sem nome",`Unidade: ${e.unit||"indefinida"} · ID: ${e.id||"sem ID"}`)).join("")}</section>`:""}
    <div class="split-head"><h2 class="section-heading">Produtos da lista</h2><span class="mini-count">${entries.length}</span></div>
    <div class="row-list">${entries.length?entries.map(e=>row(e.title||"Item sem nome",`${(Number(e.plannedMilli)||1000)/1000} ${e.unit||"sem unidade"} · ${e.group||"Sem grupo"}`,e.estimateKnown===false?"Preço a revisar":fmt(e.estimated),!String(e.title||"").trim()||!e.unit||e.unitReviewed===false||e.quantityReviewed===false?"Revisar":e.bought?"Feito":"")).join(""):empty("Nenhuma lista sincronizada para "+state.market+".")}</div>
    <div class="split-head"><h2 class="section-heading">Histórico de compras confirmadas</h2><span class="mini-count">${receipts.length}</span></div>
    <p class="info-line">Recibos registrados no Firebase. Compras antigas apenas no armazenamento do APK não podem ser recuperadas automaticamente pela versão web.</p>
    <div class="row-list">${receipts.length?receipts.slice(0,40).map(r=>row("Compra registrada",`${(r.entries||[]).filter(e=>e.bought).length} itens · ${r.market||state.market} · ID: ${r.id}`,fmt(r.total))).join(""):empty("Ainda não há compras confirmadas sincronizadas nesta categoria.")}</div>`;
}
function renderRotina(){
  const items=records("task").sort((a,b)=>String(a.date||"").localeCompare(String(b.date||"")));
  return `<section class="welcome"><div class="eyebrow">ROTINA VIVA</div><h2>Organização sem correria.</h2><p>Tarefas pessoais e compartilhadas, inclusive as recorrentes.</p></section><div class="split-head"><h2 class="section-heading">Tarefas cadastradas</h2><span class="mini-count">${items.length}</span></div><div class="row-list">${items.length?items.map(t=>row(t.title||"Tarefa sem nome", [t.owner,t.category,t.repeat,t.date,t.time].filter(Boolean).join(" · "),"",t.needsReview?"Revisar":"")).join(""):empty("Nenhuma tarefa disponível na nuvem.")}</div>`;
}
function renderPlanos(){
  const items=records("plan");
  return `<section class="welcome"><div class="eyebrow">FUTURO COMPARTILHADO</div><h2>Nossos próximos capítulos.</h2><p>Projetos e etapas que vocês já registraram.</p></section><div class="split-head"><h2 class="section-heading">Planos</h2><span class="mini-count">${items.length}</span></div><div class="row-list">${items.length?items.map(p=>row(p.title||"Plano sem título",`${Array.isArray(p.steps)?p.steps.filter(s=>s.done).length:0}/${Array.isArray(p.steps)?p.steps.length:0} etapas concluídas`,p.target?fmt(p.target):"")).join(""):empty("Nenhum plano sincronizado.")}</div>`;
}
function renderNos(){
  const moments=records("moment");
  const events=records("careEvent");
  return `<section class="welcome"><div class="eyebrow">NÓS</div><h2>O que construímos juntos. ♡</h2><p>Momentos compartilhados e os pequenos acontecimentos da vida.</p></section><div class="card-grid">${card("Momentos",String(moments.length),"registros compartilhados ou pessoais")}${card("Cuidados",String(events.length),"eventos registrados")}${card("Nosso espaço",state.household?"Conectado":"Não vinculado","Firebase")}</div><h2 class="section-heading">Memórias</h2><div class="row-list">${moments.length?moments.slice(0,30).map(m=>row(m.title||"Nosso momento",m.date||m.description||"Memória registrada")).join(""):empty("Nenhum momento disponível na nuvem.")}</div>`;
}
const views={"Hoje":renderHoje,"Compras":renderCompras,"Rotina":renderRotina,"Planos":renderPlanos,"Nós":renderNos};
function render(){
  if(!$("app")||$("app").hidden) return;
  $("page-title").textContent=state.tab;
  $("profile-name").textContent=safeName();
  $("avatar").textContent=safeName().slice(0,1).toUpperCase();
  $("sync-status").textContent=state.sync;
  const nav=tabs.map(([name,emoji])=>`<button class="nav-item" data-tab="${name}" type="button" ${name===state.tab?'aria-current="page"':""}><span class="nav-emoji" aria-hidden="true">${emoji}</span><span>${name}</span></button>`).join("");
  $("side-nav").innerHTML=nav; $("bottom-nav").innerHTML=nav;
  $("warning").hidden=!state.error && !!state.household;
  $("warning").textContent=state.error||"Nenhum espaço compartilhado associado a esta conta. A versão web só exibe dados já salvos no Firebase.";
  $("content").innerHTML=views[state.tab]();
  document.querySelectorAll("[data-tab]").forEach(b=>b.addEventListener("click",()=>{state.tab=b.dataset.tab;render();window.scrollTo(0,0)}));
  document.querySelectorAll("[data-market]").forEach(b=>b.addEventListener("click",()=>{state.market=b.dataset.market;render()}));
}
function clearListeners(){
  for(const stop of listeners) stop();
  listeners=[];
  state.shared=[];state.personal=[];state.baskets={};state.profile=null;state.household="";state.error="";
}
function watch(reference, callback, source){
  const stop=onSnapshot(reference,{includeMetadataChanges:true},snapshot=>{
    if(state.uid!==auth.currentUser?.uid) return;
    callback(snapshot);
    state.sync=snapshot.metadata.fromCache?"Exibindo cache · conferindo conexão":"Dados recebidos do Firebase";
    render();
  },error=>{state.error="Não foi possível acessar "+source+": "+(error?.code||"erro de sincronização")+". Confira regras e a conta.";state.sync="Conexão precisa de atenção";render();});
  listeners.push(stop);
}
function connectHousehold(householdId){
  while(listeners.length>2){listeners.pop()();}
  state.shared=[];state.baskets={};
  state.household=householdId;
  if(!householdId){state.sync="Sem espaço compartilhado";render();return;}
  watch(collection(db,"households",householdId,"records"),s=>{state.shared=s.docs.map(d=>({id:d.id,...d.data()}));},"registros compartilhados");
  watch(collection(db,"households",householdId,"baskets"),s=>{state.baskets=Object.fromEntries(s.docs.map(d=>[d.id,d.data()]));},"listas de compras");
}
onAuthStateChanged(auth,user=>{
  clearListeners();
  if(!user){
    state.uid=""; $("login").hidden=false; $("app").hidden=true; return;
  }
  state.uid=user.uid; state.sync="Conectando ao Firebase"; $("login").hidden=true; $("app").hidden=false;
  watch(doc(db,"users",user.uid),s=>{
    const next=s.data()||null;
    const newId=next?.householdId||"";
    const previous=state.household;
    state.profile=next;
    if(previous!==newId) connectHousehold(newId);
  },"perfil");
  watch(collection(db,"users",user.uid,"records"),s=>{state.personal=s.docs.map(d=>({id:d.id,...d.data()}));},"registros pessoais");
  render();
});
$("login-form").addEventListener("submit",async e=>{
  e.preventDefault();
  $("login-error").hidden=true;
  $("login-button").disabled=true; $("login-button").textContent="Entrando…";
  try{await signInWithEmailAndPassword(auth,$("email").value.trim(),$("password").value);}
  catch(err){$("login-error").textContent=err?.code==="auth/invalid-credential"?"E-mail ou senha inválidos.": "Falha no login ("+(err?.code||"erro desconhecido")+").";$("login-error").hidden=false;}
  finally{$("login-button").disabled=false;$("login-button").textContent="Entrar";$("password").value="";}
});
$("logout").addEventListener("click",()=>signOut(auth));
