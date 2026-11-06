import {shoppingView,installShoppingUI,getShoppingRoutePrefs,restoreShoppingRoutePrefs} from "./shopping-ui.js";
import {parseAppRoute,writeAppRoute} from "./navigation.js";
import {captureViewPosition,saveViewPosition,loadViewPosition,restoreViewPosition} from "./scroll-position.js";
import {isQuotaError,cacheBasketSnapshot} from "./emergency-cart.js";
import {needsSharedRecords,needsCatalogLinks} from "./firestore-listener-policy.js";
import {createFirestoreUsageTracker} from "./firestore-usage-tracker.js";
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, setPersistence, browserLocalPersistence, browserSessionPersistence } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
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
// O Chrome também tenta restaurar pixels automaticamente. Nossa restauração
// por card só funciona de forma consistente se uma estratégia controlar a tela.
if("scrollRestoration" in window.history)window.history.scrollRestoration="manual";
const initialHash=(()=>{
  if(window.location.hash)return window.location.hash;
  try{return sessionStorage.getItem("cdc:last-route")||"";}catch{return "";}
})();
if(!window.location.hash&&initialHash)window.history.replaceState(null,"",initialHash);
const initialRoute=parseAppRoute(initialHash);
restoreShoppingRoutePrefs(initialRoute);
const usageTracker=createFirestoreUsageTracker();
const state = { firestoreUsage:usageTracker.summary(),tab:initialRoute.tab, market:initialRoute.market, profile:null, uid:"", household:"", shared:[], personal:[], links:[], baskets:{}, error:"", sync:"Aguardando dados", loading:true };
function saveHouseholdHint(uid,hid){
  if(!uid||!hid)return;
  try{localStorage.setItem("cdc:household-hint:"+uid,hid);}catch{}
}
function readHouseholdHint(uid){
  try{
    const value=localStorage.getItem("cdc:household-hint:"+uid)||"";
    return /^[^/]{1,140}$/.test(value)?value:"";
  }catch{return "";}
}
const initialPosition=loadViewPosition(window.location.hash);
const startingHash=window.location.hash;
let scrollRestored=false,restoreScheduled=false,lastRenderedHash="",pendingScrollSave=null;
let firstHouseData={shared:false,baskets:false};
const routeState=()=>({tab:state.tab,market:state.market,...getShoppingRoutePrefs()});
function saveScroll(){
  if(!scrollRestored)return;
  saveViewPosition(window.location.hash,captureViewPosition(document.getElementById("content")));
}
function rememberRoute(){saveScroll();writeAppRoute(routeState());}
function restoreScrollAfterRefresh(){
  if(scrollRestored||restoreScheduled||!state.uid||!state.household)return;
  const shoppingArea=getShoppingRoutePrefs().shopMode;
  const ready=state.tab==="Compras"&&shoppingArea!=="catalog"?
    firstHouseData.baskets:firstHouseData.shared&&firstHouseData.baskets;
  if(!ready)return;
  restoreScheduled=true;
  requestAnimationFrame(()=>requestAnimationFrame(()=>{
    if(window.location.hash===startingHash)
      restoreViewPosition(initialPosition,document.getElementById("content"));
    scrollRestored=true;restoreScheduled=false;
  }));
}
let listeners = [];
let houseListeners = [];
let sharedSubscribed=false,linksSubscribed=false;
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
function renderCompras(){ return shoppingView({state}); }
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
let renderQueued=false;
function scheduleRender(){
  if(renderQueued)return;
  renderQueued=true;
  requestAnimationFrame(()=>{renderQueued=false;render();});
}
function render(){
  if(!$("app")||$("app").hidden) return;
  ensureHeavyData();
  const currentHash=window.location.hash;
  const preserved=(scrollRestored&&currentHash===lastRenderedHash&&state.tab==="Compras")?
    captureViewPosition($("content")):null;
  lastRenderedHash=currentHash;
  $("page-title").textContent=state.tab;
  $("profile-name").textContent=safeName();
  $("avatar").textContent=safeName().slice(0,1).toUpperCase();
  $("sync-status").textContent=state.sync;
  const nav=tabs.map(([name,emoji])=>`<button class="nav-item" data-tab="${name}" type="button" ${name===state.tab?'aria-current="page"':""}><span class="nav-emoji" aria-hidden="true">${emoji}</span><span>${name}</span></button>`).join("");
  $("side-nav").innerHTML=nav; $("bottom-nav").innerHTML=nav;
  $("warning").hidden=!state.error && !!state.household;
  $("warning").textContent=state.error||"Nenhum espaço compartilhado associado a esta conta. A versão web só exibe dados já salvos no Firebase.";
  $("content").innerHTML=views[state.tab]();
  if(preserved)requestAnimationFrame(()=>{
    if(window.location.hash===currentHash)restoreViewPosition(preserved,$("content"));
  });
  document.querySelectorAll("[data-tab]").forEach(b=>b.addEventListener("click",()=>{
    state.tab=b.dataset.tab;
    rememberRoute();
    render();
    window.scrollTo(0,0);
  }));
  document.querySelectorAll("[data-market]").forEach(b=>b.addEventListener("click",()=>{
    state.market=b.dataset.market;
    rememberRoute();
    render();
  }));
  restoreScrollAfterRefresh();
}
function clearListeners(){
  for(const stop of [...listeners,...houseListeners]) stop();
  listeners=[];houseListeners=[];
  state.shared=[];state.personal=[];state.links=[];state.baskets={};state.profile=null;state.household="";state.error="";
  sharedSubscribed=false;linksSubscribed=false;
  usageTracker.reset();state.firestoreUsage=usageTracker.summary();
}
function watch(reference, callback, source, group="global", onError=null){
  // Sem includeMetadataChanges: mudanças só de metadados não precisam
  // reconstruir todos os cards. Agrupa os snapshots em um único frame.
  const stop=onSnapshot(reference,snapshot=>{
    if(state.uid!==auth.currentUser?.uid)return;
    callback(snapshot);
    usageTracker.onSnapshot(source,snapshot);state.firestoreUsage=usageTracker.summary();
    state.sync=snapshot.metadata.hasPendingWrites?"Alterações locais · aguardando confirmação":
      snapshot.metadata.fromCache?"Exibindo cache · conferindo conexão":"Dados recebidos do Firebase";
    scheduleRender();
  },error=>{
    usageTracker.onError(source,error);state.firestoreUsage=usageTracker.summary();
    state.error="Não foi possível acessar "+source+": "+(error?.code||"erro de sincronização")+". Confira regras e a conta.";
    state.sync="Conexão precisa de atenção";
    if(typeof onError==="function")onError(error);
    scheduleRender();
  });
  if(group==="house")houseListeners.push(stop);else listeners.push(stop);
}
function ensureHeavyData(){
  if(!state.household)return;
  const mode=getShoppingRoutePrefs().shopMode;
  if(needsSharedRecords(state.tab,mode)&&!sharedSubscribed){
    sharedSubscribed=true;
    watch(collection(db,"households",state.household,"records"),s=>{
      state.shared=s.docs.map(d=>({id:d.id,...d.data()}));firstHouseData.shared=true;
    },"registros compartilhados","house");
  }
  if(needsCatalogLinks(state.tab,mode)&&!linksSubscribed){
    linksSubscribed=true;
    watch(collection(db,"households",state.household,"catalogLinks"),s=>{
      state.links=s.docs.map(d=>({id:d.id,...d.data()}));
    },"vínculos do catálogo","house");
  }
}
function connectHousehold(householdId){
  for(const stop of houseListeners)stop();
  houseListeners=[];
  state.shared=[];state.links=[];state.baskets={};
  sharedSubscribed=false;linksSubscribed=false;
  firstHouseData={shared:false,baskets:false};
  state.household=householdId;
  if(!householdId){state.sync="Sem espaço compartilhado";render();return;}
  // A cesta tem poucos documentos, enquanto o catálogo contém centenas.
  // Assinar a cesta primeiro entrega a lista mais cedo ao modo mercado.
  watch(collection(db,"households",householdId,"baskets"),s=>{
    state.baskets=Object.fromEntries(s.docs.map(d=>[d.id,d.data()]));
    for(const [market,basket] of Object.entries(state.baskets))
      cacheBasketSnapshot(state,market,basket);
    firstHouseData.baskets=true;
  },"listas de compras","house");
  ensureHeavyData();
}
installShoppingUI({root:$("content"),db,auth,getState:()=>state,render});

// getAuth já recupera a persistência local ao iniciar no navegador.
// Não mover a sessão entre storages em todo refresh: isso atrasava o login.
onAuthStateChanged(auth,user=>{
  clearListeners();
  $("session-loading").hidden=true;
  if(!user){
    state.uid=""; $("login").hidden=false; $("app").hidden=true;

    return;
  }
  state.uid=user.uid;
  state.sync="Conectando ao Firebase";
  $("login").hidden=true; $("app").hidden=false;
  watch(doc(db,"users",user.uid),s=>{
    const next=s.data()||null;
    const newId=next?.householdId||"";
    const previous=state.household;
    state.profile=next;
    if(newId)saveHouseholdHint(user.uid,newId);
    if(previous!==newId) connectHousehold(newId);
  },"perfil","global",error=>{
    // Recuperação LOCAL apenas após login autenticado. Não ignora regras
    // de acesso ao Firebase; oferece apenas dados já salvos neste aparelho.
    if(!isQuotaError(error)||state.household)return;
    const cached=readHouseholdHint(user.uid);
    if(cached){
      state.profile={displayName:"Meu espaço local",householdId:cached};
      connectHousehold(cached);
      state.sync="Modo local disponível · Firestore com limite atingido";
    }
  });
  watch(collection(db,"users",user.uid,"records"),s=>{state.personal=s.docs.map(d=>({id:d.id,...d.data()}));},"registros pessoais");
  render();
},error=>{
  $("session-loading").hidden=true;
  $("app").hidden=true;
  $("login").hidden=false;
  $("login-error").textContent="Não foi possível restaurar a sessão ("+(error?.code||"erro de autenticação")+").";
  $("login-error").hidden=false;
});
window.addEventListener("pagehide",saveScroll);
window.addEventListener("visibilitychange",()=>{
  if(document.visibilityState==="hidden")saveScroll();
});
window.addEventListener("scroll",()=>{
  if(!scrollRestored)return;
  if(pendingScrollSave!==null)clearTimeout(pendingScrollSave);
  pendingScrollSave=setTimeout(()=>{pendingScrollSave=null;saveScroll();},160);
},{passive:true});
function applyRouteFromUrl(){
  const route=parseAppRoute(window.location.hash);
  try{sessionStorage.setItem("cdc:last-route",window.location.hash);}catch{}
  state.tab=route.tab;
  state.market=route.market;
  restoreShoppingRoutePrefs(route);
  render();
}
window.addEventListener("popstate",applyRouteFromUrl);
window.addEventListener("hashchange",applyRouteFromUrl);
$("login-form").addEventListener("submit",async e=>{
  e.preventDefault();
  $("login-error").hidden=true;
  $("login-button").disabled=true; $("login-button").textContent="Entrando…";
  try{
    // Alterar persistência apenas quando a pessoa faz login, nunca no refresh.
    try{await setPersistence(auth,browserLocalPersistence);}
    catch{await setPersistence(auth,browserSessionPersistence);}
    await signInWithEmailAndPassword(auth,$("email").value.trim(),$("password").value);
  }
  catch(err){$("login-error").textContent=err?.code==="auth/invalid-credential"?"E-mail ou senha inválidos.": "Falha no login ("+(err?.code||"erro desconhecido")+").";$("login-error").hidden=false;}
  finally{$("login-button").disabled=false;$("login-button").textContent="Entrar";$("password").value="";}
});
$("logout").addEventListener("click",()=>signOut(auth));
