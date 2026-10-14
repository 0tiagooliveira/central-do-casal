import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, setDoc, updateDoc, deleteDoc, writeBatch, runTransaction, serverTimestamp, Timestamp, arrayUnion, arrayRemove } from 'firebase/firestore';
let env;
const db = uid => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: true }).firestore();
const profile = uid => ({displayName: uid, householdId: 'home'});
const record = (uid, kind='task', privateFlag=false) => ({kind, private: privateFlag, title: 'Teste explícito', date: '2026-10-10', updatedBy: uid, updatedAt: serverTimestamp()});
before(async () => { env = await initializeTestEnvironment({projectId:'demo-central-do-casal', firestore:{rules:readFileSync('firestore/firestore.rules','utf8')}}); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const d=context.firestore();
    await setDoc(doc(d,'households/home'), {name:'Teste', creatorUid:'alice', members:['alice','bob'], inviteId:'', createdAt: Timestamp.now()});
    await setDoc(doc(d,'users/alice'),profile('alice')); await setDoc(doc(d,'users/bob'),profile('bob'));
    await setDoc(doc(d,'users/bob/records/health'), record('bob','health',true));
    await setDoc(doc(d,'households/home/records/shared'),record('alice'));
  });
});
test('household members read shared records',async()=> {await assertSucceeds(getDoc(doc(db('bob'),'households/home/records/shared')));});
test('imported history and catalog are readable only by members and immutable to clients',async()=> {
 for(const kind of ['importedReceipt','catalogItem','catalogSku','documentedMonth','importSummary']) {
   await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),`households/home/records/${kind}`),{kind,private:false,title:'Fixture de importação',total:1234}));
   const ref=doc(db('bob'),`households/home/records/${kind}`);
   await assertSucceeds(getDoc(ref)); await assertFails(getDoc(doc(db('eve'),`households/home/records/${kind}`)));
   await assertFails(updateDoc(ref,{total:0})); await assertFails(deleteDoc(ref));
 }
});
test('raw import archive is not accessible to Android clients',async()=> {
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'households/home/importArchive/main_0'),{data:'Fixture de arquivo'}));
 await assertFails(getDoc(doc(db('alice'),'households/home/importArchive/main_0')));
});
test('personal planned bills and relationships stay private and read only after import',async()=> {
 for(const kind of ['plannedBill','dataRelationship','sourceReview']) {
   await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),`users/alice/records/${kind}`),{kind,private:true,title:'Fixture pessoal',amount:1234}));
   const own=doc(db('alice'),`users/alice/records/${kind}`);
   await assertSucceeds(getDoc(own));await assertFails(getDoc(doc(db('bob'),`users/alice/records/${kind}`)));
   await assertFails(updateDoc(own,{amount:0}));await assertFails(deleteDoc(own));
 }
});
test('Android listeners can query whole private and shared collections including empty baskets',async()=> {
 const d=db('alice');
 await assertSucceeds(getDocs(collection(d,'users/alice/records')));
 await assertSucceeds(getDocs(collection(d,'households/home/records')));
 await assertSucceeds(getDocs(collection(d,'households/home/baskets')));
 await assertFails(getDocs(collection(db('eve'),'households/home/baskets')));
 await assertFails(getDocs(collection(d,'users/bob/records')));
});
test('new creator can create household atomically then read every Android subscription',async()=> {
 const d=db('carol'), b=writeBatch(d);
 b.set(doc(d,'users/carol'),{displayName:'Carol',householdId:'new-home'});
 b.set(doc(d,'households/new-home'),{name:'Nosso espaço',creatorUid:'carol',members:['carol'],inviteId:'',createdAt:serverTimestamp()});
 await assertSucceeds(b.commit());
 await assertSucceeds(getDoc(doc(d,'users/carol')));
 await assertSucceeds(getDoc(doc(d,'households/new-home')));
 await assertSucceeds(getDocs(collection(d,'users/carol/records')));
 await assertSucceeds(getDocs(collection(d,'households/new-home/records')));
 await assertSucceeds(getDocs(collection(d,'households/new-home/baskets')));
});
test('creator invitation transaction matches Android code and recipient joins without private access',async()=> {
 const d=db('carol'); let b=writeBatch(d);
 b.set(doc(d,'users/carol'),{displayName:'Carol',householdId:'couple'});
 b.set(doc(d,'households/couple'),{name:'Nosso espaço',creatorUid:'carol',members:['carol'],inviteId:'',createdAt:serverTimestamp()});
 await b.commit();
 await assertSucceeds(runTransaction(d,async tx=>{
   const home=doc(d,'households/couple'); await tx.get(home);
   tx.set(doc(d,'invites/client-code'),{householdId:'couple',ownerUid:'carol',email:'dana@example.com',expiresAt:Timestamp.fromMillis(Date.now()+86400000),usedBy:''});
   tx.update(home,{inviteId:'client-code'});
 }));
 const partner=db('dana');
 await assertSucceeds(runTransaction(partner,async tx=>{
   const invite=doc(partner,'invites/client-code'); await tx.get(invite);
   tx.update(invite,{usedBy:'dana'});
   tx.update(doc(partner,'households/couple'),{members:arrayUnion('dana')});
   tx.set(doc(partner,'users/dana'),{displayName:'Dana',householdId:'couple'});
 }));
 await assertSucceeds(getDocs(collection(partner,'households/couple/baskets')));
 await assertFails(getDocs(collection(partner,'users/carol/records')));
});
test('stranger cannot read shared records',async()=> {await assertFails(getDoc(doc(db('eve'),'households/home/records/shared')));});
test('unauthenticated caller cannot read',async()=> {await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'households/home/records/shared')));});
test('partner cannot read private health',async()=> {await assertFails(getDoc(doc(db('alice'),'users/bob/records/health')));});
test('owner can read private health',async()=> {await assertSucceeds(getDoc(doc(db('bob'),'users/bob/records/health')));});
test('stranger cannot forge household membership',async()=> {await assertFails(updateDoc(doc(db('eve'),'households/home'),{members:['alice','bob','eve']}));});
test('member cannot add third party',async()=> {await assertFails(updateDoc(doc(db('alice'),'households/home'),{members:['alice','bob','eve']}));});
test('user cannot point profile at unauthorized household',async()=> {await assertFails(setDoc(doc(db('eve'),'users/eve'),profile('eve')));});
test('member cannot put health in shared collection',async()=> {await assertFails(setDoc(doc(db('alice'),'households/home/records/health'),record('alice','health')));});
const safetyRecord = (uid,kind) => ({
  kind,private:false,ownerUid:uid,ownerName:uid,
  latitude:-25.0,longitude:-49.0,
  mapUrl:'https://maps.google.com/?q=-25.0,-49.0',
  ...(kind==='safetyAlert'?{createdAt:Date.now()}:{capturedAt:Date.now()}),
  updatedBy:uid,updatedAt:serverTimestamp()
});
test('members can share house devices and safety state while strangers and private collections remain blocked',async()=>{
 const shared=db('alice');
 await assertSucceeds(setDoc(doc(shared,'households/home/records/smartDevice'),record('alice','smartDevice')));
 for(const kind of ['safetyCheckIn','safetyAlert']) await assertSucceeds(setDoc(doc(shared,`households/home/records/${kind}`),safetyRecord('alice',kind)));
 await assertFails(setDoc(doc(db('eve'),'households/home/records/device'),{...record('eve','smartDevice'),private:false}));
 await assertFails(setDoc(doc(shared,'users/alice/records/private-device'),{...record('alice','smartDevice',true),private:true}));
 await assertFails(updateDoc(doc(shared,'households/home/records/safetyAlert'),{title:'não pode alterar alerta'}));
});
test('SOS identity belongs to authenticated writer and partner cannot rewrite a check-in',async()=>{
 const alice=db('alice'), bob=db('bob');
 await assertFails(setDoc(doc(alice,'households/home/records/forged-sos'),safetyRecord('bob','safetyAlert')));
 await assertFails(setDoc(doc(alice,'households/home/records/forged-checkin'),safetyRecord('bob','safetyCheckIn')));
 await assertFails(setDoc(doc(alice,'households/home/records/out-of-range'),{...safetyRecord('alice','safetyAlert'),latitude:100}));
 const ref=doc(alice,'households/home/records/own-checkin');
 await assertSucceeds(setDoc(ref,safetyRecord('alice','safetyCheckIn')));
 await assertSucceeds(setDoc(ref,safetyRecord('alice','safetyCheckIn')));
 await assertFails(setDoc(doc(bob,'households/home/records/own-checkin'),safetyRecord('bob','safetyCheckIn')));
 await assertFails(updateDoc(doc(bob,'households/home/records/own-checkin'),{
   ownerUid:'bob',ownerName:'bob',updatedBy:'bob',updatedAt:serverTimestamp()
 }));
 await assertFails(deleteDoc(doc(bob,'households/home/records/own-checkin')));
 await assertSucceeds(deleteDoc(ref));
 const afterDelete=await assertSucceeds(getDoc(ref));
 if(afterDelete.exists()) throw new Error('Check-in was not deleted');
});
test('live location is scoped to owner, household, valid coordinates and expiry', async () => {
 const point=(uid,lat=-25.0,lon=-49.0)=>({
   ownerUid:uid,latitude:lat,longitude:lon,capturedAt:Timestamp.now(),
   updatedAt:serverTimestamp(),
   expiresAt:Timestamp.fromMillis(Date.now()+60000),
   sessionEndsAt:Timestamp.fromMillis(Date.now()+1800000)
 });
 const ref=doc(db('alice'),'households/home/liveLocations/alice');
 await assertSucceeds(setDoc(ref,point('alice')));
 await assertSucceeds(getDoc(doc(db('bob'),'households/home/liveLocations/alice')));
 await assertFails(getDoc(doc(db('eve'),'households/home/liveLocations/alice')));
 await assertFails(setDoc(ref,point('bob')));
 await assertFails(setDoc(ref,point('alice',100,0)));
 await assertFails(setDoc(ref,{...point('alice'),expiresAt:Timestamp.fromMillis(Date.now()+86400000)}));
 // É permitido atualizar a posição na mesma sessão, sem estender o prazo.
 await assertSucceeds(updateDoc(ref,{
   latitude:-24.5,capturedAt:Timestamp.now(),
   expiresAt:Timestamp.fromMillis(Date.now()+60000),updatedAt:serverTimestamp()
 }));
 // O cliente não pode prolongar ou encurtar silenciosamente uma sessão existente.
 await assertFails(updateDoc(ref,{
   sessionEndsAt:Timestamp.fromMillis(Date.now()+25*60000),
   updatedAt:serverTimestamp()
 }));
 // Um fix mais antigo não pode substituir a última posição recebida.
 await assertFails(updateDoc(ref,{
   capturedAt:Timestamp.fromMillis(Date.now()-30000),
   updatedAt:serverTimestamp()
 }));
 // Publicar uma localização "atual" por mais de 90 s também é proibido.
 await assertFails(updateDoc(ref,{
   expiresAt:Timestamp.fromMillis(Date.now()+120000),
   updatedAt:serverTimestamp()
 }));
 await assertFails(deleteDoc(doc(db('bob'),'households/home/liveLocations/alice')));
 await assertSucceeds(deleteDoc(ref));
 await assertFails(setDoc(doc(db('bob'),'households/home/liveLocations/alice'),point('bob')));
});
test('member cannot store private flag in shared collection',async()=> {await assertFails(setDoc(doc(db('alice'),'households/home/records/private'),record('alice','task',true)));});
test('care events require genuine authorship and only the author can undo',async()=> {
 const care=(uid,type='diaper',subject='Lucca')=>({
   kind:'careEvent',private:false,careType:type,subject,actorUid:uid,actorName:uid,
   occurredAt:Date.now(),date:'2026-10-13',updatedBy:uid,updatedAt:serverTimestamp()
 });
 const ref=doc(db('alice'),'households/home/records/care-alice');
 await assertSucceeds(setDoc(ref,care('alice')));
 await assertSucceeds(getDoc(doc(db('bob'),'households/home/records/care-alice')));
 await assertFails(updateDoc(ref,{careType:'bath'}));
 await assertFails(deleteDoc(doc(db('bob'),'households/home/records/care-alice')));
 await assertSucceeds(deleteDoc(ref));
 await assertFails(setDoc(doc(db('bob'),'households/home/records/care-forged'),care('alice')));
 await assertFails(setDoc(doc(db('alice'),'households/home/records/care-invalid'),care('alice','petFood','Lucca')));
});
test('user cannot forge mutation author',async()=> {await assertFails(setDoc(doc(db('alice'),'households/home/records/forged'),record('bob')));});
test('receipt snapshots cannot be modified or deleted',async()=> {
  await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'households/home/records/receipt'),{...record('alice','receipt'),total:123});});
  await assertFails(updateDoc(doc(db('alice'),'households/home/records/receipt'),{total:0}));
  await assertFails(deleteDoc(doc(db('alice'),'households/home/records/receipt')));
});
test('contribution is append only',async()=> {const d=db('alice');await assertSucceeds(setDoc(doc(d,'households/home/records/contribution'),{...record('alice','contribution'),amount:100}));await assertFails(updateDoc(doc(d,'households/home/records/contribution'),{amount:500}));});
test('member can leave and loses access immediately',async()=> {
  const d=db('bob'); const b=writeBatch(d); b.update(doc(d,'households/home'),{members:arrayRemove('bob')});b.update(doc(d,'users/bob'),{householdId:''});await assertSucceeds(b.commit());await assertFails(getDoc(doc(d,'households/home/records/shared')));
});
test('member cannot remove partner',async()=> {await assertFails(updateDoc(doc(db('alice'),'households/home'),{members:['alice']}));});
test('basket revisions reject stale concurrent updates',async()=> {
  const d=db('alice'); const ref=doc(d,'households/home/baskets/Mercado');
  const basket={market:'Mercado',generation:'a'.repeat(32),budget:1000,revision:1,entries:[]};await assertSucceeds(setDoc(ref,basket));await assertFails(setDoc(ref,basket));await assertSucceeds(setDoc(ref,{...basket,revision:2}));
});
test('separate butcher budget is permitted and negative budget denied',async()=> {
  await assertSucceeds(setDoc(doc(db('bob'),'households/home/baskets/Açougue'),{market:'Açougue',generation:'b'.repeat(32),budget:2000,revision:1,entries:[]}));
  await assertFails(setDoc(doc(db('bob'),'households/home/baskets/Mercado'),{market:'Mercado',generation:'a'.repeat(32),budget:-1,revision:1,entries:[]}));
});
test('one user cannot accept on behalf of partner',async()=> {
 const d=db('alice');const ref=doc(d,'households/home/records/quest');await assertSucceeds(setDoc(ref,{...record('alice','quest'),creatorUid:'alice',accepted:['alice'],completed:[],cancelled:false}));await assertFails(updateDoc(ref,{accepted:['alice','bob']}));await assertSucceeds(updateDoc(doc(db('bob'),'households/home/records/quest'),{accepted:arrayUnion('bob')}));
});
test('quest requires both genuine confirmations and cannot change card',async()=> {
 const ref=doc(db('alice'),'households/home/records/quest');await assertSucceeds(setDoc(ref,{...record('alice','quest'),creatorUid:'alice',accepted:['alice'],completed:[],cancelled:false}));
 await assertFails(updateDoc(ref,{completed:['alice']})); await assertFails(updateDoc(ref,{title:'Outra carta'}));
 await assertSucceeds(updateDoc(doc(db('bob'),'households/home/records/quest'),{accepted:arrayUnion('bob')}));
 await assertFails(updateDoc(ref,{completed:['alice','bob']})); await assertSucceeds(updateDoc(ref,{completed:arrayUnion('alice')}));await assertSucceeds(updateDoc(doc(db('bob'),'households/home/records/quest'),{completed:arrayUnion('bob')}));
});
async function makeInvite(expired=false) {
  await env.withSecurityRulesDisabled(async c=>{
    const d=c.firestore();await updateDoc(doc(d,'households/home'),{members:['alice'],inviteId:'invite'});await updateDoc(doc(d,'users/bob'),{householdId:''});
    await setDoc(doc(d,'invites/invite'),{householdId:'home',ownerUid:'alice',email:'bob@example.com',usedBy:'',expiresAt:Timestamp.fromMillis(Date.now()+(expired?-60000:3600000))});
  });
}
function acceptInvite(d) {const b=writeBatch(d);b.update(doc(d,'invites/invite'),{usedBy:'bob'});b.update(doc(d,'households/home'),{members:arrayUnion('bob')});b.set(doc(d,'users/bob'),profile('bob'));return b.commit();}
test('invitation binds verified intended recipient and is atomic',async()=> {await makeInvite();const d=db('bob');await assertSucceeds(getDoc(doc(d,'invites/invite')));await assertSucceeds(acceptInvite(d));await assertSucceeds(getDoc(doc(d,'households/home/records/shared')));});
test('expired invitation denied',async()=>{await makeInvite(true);await assertFails(acceptInvite(db('bob')));});
test('wrong recipient cannot read invitation',async()=>{await makeInvite();await assertFails(getDoc(doc(db('eve'),'invites/invite')));});
test('unverified recipient cannot accept',async()=>{await makeInvite();const d=env.authenticatedContext('bob',{email:'bob@example.com',email_verified:false}).firestore();await assertFails(acceptInvite(d));});
test('claim without household update denied',async()=>{await makeInvite();await assertFails(updateDoc(doc(db('bob'),'invites/invite'),{usedBy:'bob'}));});
test('receipt creation requires matching immutable snapshot and session reset',async()=>{
 const d=db('alice'); const generation='x'.repeat(32);const entries=[{id:'item',title:'Produto',bought:true,actual:100,actualMilli:1000}];
 const basket={market:'Mercado',generation,budget:200,revision:1,entries};const ref=doc(d,'households/home/baskets/Mercado');await assertSucceeds(setDoc(ref,basket));
 const receipt={...basket,...record('alice','receipt'),total:100};await assertFails(setDoc(doc(d,`households/home/records/${generation}`),receipt));
 const b=writeBatch(d); b.set(doc(d,`households/home/records/${generation}`),receipt);b.set(ref,{...basket,generation:'y'.repeat(32),revision:2,entries:[]});await assertSucceeds(b.commit());
 await assertFails(setDoc(doc(d,`households/home/records/${generation}`),receipt));
});

async function seedCatalogLinkFixture() {
 await env.withSecurityRulesDisabled(async c=>{
  const d=c.firestore();
  await setDoc(doc(d,'households/home/records/fixture-sku'),{kind:'catalogSku',private:false,title:'SKU fixture',history:[{date:'2026-01-01',price:'1.2345'}]});
  await setDoc(doc(d,'households/home/records/fixture-base'),{kind:'catalogItem',private:false,title:'Item-base fixture'});
  await setDoc(doc(d,'households/home/records/private-base'),{kind:'catalogItem',private:true,title:'Fixture privada'});
  await setDoc(doc(d,'households/foreign'),{members:['eve'],creatorUid:'eve'});
  await setDoc(doc(d,'households/foreign/records/foreign-base'),{kind:'catalogItem',private:false,title:'Outro espaço fixture'});
 });
}
const catalogLink=(uid,baseItemId='fixture-base',status='confirmed',revision=1)=>({kind:'catalogLink',private:false,skuId:'fixture-sku',baseItemId,status,revision,updatedBy:uid,updatedAt:serverTimestamp()});

test('catalog link confirmation and correction use a separate revisioned document while preserving SKU history',async()=>{
 await seedCatalogLinkFixture();
 const alice=db('alice'),bob=db('bob');const ref=doc(alice,'households/home/catalogLinks/fixture-sku');
 await assertSucceeds(setDoc(ref,catalogLink('alice')));
 await assertSucceeds(setDoc(doc(bob,'households/home/catalogLinks/fixture-sku'),catalogLink('bob','','separate',2)));
 const snapshot=await assertSucceeds(getDoc(doc(bob,'households/home/records/fixture-sku')));
 if(snapshot.data().kind!=='catalogSku'||snapshot.data().history[0].price!=='1.2345') throw new Error('Original SKU history changed');
 await assertSucceeds(getDocs(collection(bob,'households/home/catalogLinks')));
 await assertFails(deleteDoc(ref));
});

test('catalog links reject strangers, unauthenticated users and forged authors',async()=>{
 await seedCatalogLinkFixture();
 await assertFails(setDoc(doc(db('eve'),'households/home/catalogLinks/fixture-sku'),catalogLink('eve')));
 await assertFails(getDocs(collection(db('eve'),'households/home/catalogLinks')));
 await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(),'households/home/catalogLinks/fixture-sku'),catalogLink('alice')));
 await assertFails(setDoc(doc(db('alice'),'households/home/catalogLinks/fixture-sku'),catalogLink('bob')));
});

test('catalog link references must resolve to the correct public kinds in the same household',async()=>{
 await seedCatalogLinkFixture();const d=db('alice'),ref=doc(d,'households/home/catalogLinks/fixture-sku');
 for(const base of ['missing','fixture-sku','private-base','foreign-base']) await assertFails(setDoc(ref,catalogLink('alice',base)));
 await assertFails(setDoc(doc(d,'households/home/catalogLinks/missing'),{...catalogLink('alice'),skuId:'missing'}));
 await assertFails(setDoc(doc(d,'households/home/catalogLinks/fixture-base'),{...catalogLink('alice'),skuId:'fixture-base'}));
 await assertFails(setDoc(ref,{...catalogLink('alice'),skuId:'fixture-base'}));
});

test('catalog link status controls base reference and permits no extra mutation fields',async()=>{
 await seedCatalogLinkFixture();const ref=doc(db('alice'),'households/home/catalogLinks/fixture-sku');
 for(const value of [catalogLink('alice','','confirmed'),catalogLink('alice','fixture-base','separate'),catalogLink('alice','fixture-base','suggested'),{...catalogLink('alice'),private:true},{...catalogLink('alice'),history:[]},{...catalogLink('alice'),updatedAt:Timestamp.fromMillis(0)}]) await assertFails(setDoc(ref,value));
 await assertSucceeds(setDoc(ref,catalogLink('alice','','separate')));
});

test('catalog link revisions start at one and stale updates are rejected',async()=>{
 await seedCatalogLinkFixture();const ref=doc(db('alice'),'households/home/catalogLinks/fixture-sku');
 await assertFails(setDoc(ref,catalogLink('alice','fixture-base','confirmed',2)));
 await assertSucceeds(setDoc(ref,catalogLink('alice')));
 await assertFails(setDoc(ref,catalogLink('alice','','separate',1)));
 await assertFails(setDoc(ref,catalogLink('alice','','separate',3)));
 await assertSucceeds(setDoc(ref,catalogLink('alice','','separate',2)));
});

test('catalog links cannot overwrite imported records or be stored privately',async()=>{
 await seedCatalogLinkFixture();const d=db('alice');
 await assertFails(setDoc(doc(d,'households/home/records/fixture-sku'),catalogLink('alice')));
 await assertFails(setDoc(doc(d,'households/home/records/new-link'),catalogLink('alice')));
 await assertFails(setDoc(doc(d,'users/alice/records/fixture-sku'),{...catalogLink('alice'),private:true}));
});
