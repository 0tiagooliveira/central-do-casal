import { readFileSync } from 'node:fs';
import { before, beforeEach, after, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';
import { ref, uploadBytes, getMetadata, deleteObject } from 'firebase/storage';

let env;
const account = uid => env.authenticatedContext(uid,{email:uid+'@example.com',email_verified:true}).storage();
const fixture = new Uint8Array([255,216,255,217]);
const image = {contentType:'image/jpeg'};

before(async () => {
  env = await initializeTestEnvironment({
    projectId:'demo-central-do-casal',
    firestore:{rules:readFileSync('firestore/firestore.rules','utf8')},
    storage:{rules:readFileSync('storage/storage.rules','utf8')}
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db=ctx.firestore();
    await setDoc(doc(db,'households/home'),{members:['alice','bob'],name:'Casal',creatorUid:'alice',inviteId:''});
    await setDoc(doc(db,'households/home/records/sushi'),{kind:'moment',private:false,title:'Noite do sushi'});
    await setDoc(doc(db,'users/alice/records/private-memory'),{kind:'moment',private:true,title:'Memória privada'});
  });
});

test('only household members can view shared photo', async () => {
  const path='households/home/moments/sushi/alice-test1.jpg';
  await assertSucceeds(uploadBytes(ref(account('alice'),path),fixture,image));
  await assertSucceeds(getMetadata(ref(account('bob'),path)));
  await assertFails(getMetadata(ref(account('eve'),path)));
});

test('a partner cannot impersonate upload author or upload other content type', async () => {
  const root='households/home/moments/sushi/';
  await assertFails(uploadBytes(ref(account('bob'),root+'alice-forged.jpg'),fixture,image));
  await assertFails(uploadBytes(ref(account('alice'),root+'alice-not-image.jpg'),fixture,{contentType:'text/plain'}));
  await assertFails(uploadBytes(ref(account('eve'),root+'eve-unauthorized.jpg'),fixture,image));
});

test('private photo is readable only by its owner', async () => {
  const path='users/alice/moments/private-memory/alice-test2.jpg';
  await assertSucceeds(uploadBytes(ref(account('alice'),path),fixture,image));
  await assertSucceeds(getMetadata(ref(account('alice'),path)));
  await assertFails(getMetadata(ref(account('bob'),path)));
});

test('photo cannot be uploaded without an existing moment record', async () => {
  await assertFails(uploadBytes(ref(account('alice'),'households/home/moments/absent/alice-test3.jpg'),fixture,image));
});

test('a household partner cannot delete another authors photo', async () => {
  const path='households/home/moments/sushi/alice-test4.jpg';
  await assertSucceeds(uploadBytes(ref(account('alice'),path),fixture,image));
  await assertFails(deleteObject(ref(account('bob'),path)));
  await assertSucceeds(deleteObject(ref(account('alice'),path)));
});
